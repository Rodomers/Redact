package com.rds.mews.core

import android.util.Log
import com.rds.mews.localcore.Message
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.io.Closeable
import kotlin.math.pow
import kotlinx.coroutines.flow.first

class LLMClient(
    val apiKey: String = "",
    val MODEL: String = MewsRepository.defaultModel.apiModelName,
    private val URL_TEMPLATE: String = "https://generativelanguage.googleapis.com/v1beta/models/%MODEL%:generateContent",
    enableProxy: Boolean = false
) : Closeable {

    private val finalUrl = URL_TEMPLATE.replace("%MODEL%", MODEL.ifBlank { MewsRepository.defaultModel.apiModelName })
    private val httpClient = SharedHttpClient.createInstance(MewsRepository.PROXY_ADDRESS, MewsRepository.SERVER_KEY, enableProxy = enableProxy)
    private val jsonParser = SharedHttpClient.jsonParser
    private val MAX_RETRIES = 3

    suspend fun sendPrompt(prompt: String): String? {
        val requestBodyObj = GeminiRequest(
            contents = listOf(ContentInput(parts = listOf(PartInput(prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5, maxOutputTokens = 8192)
        )
        val requestBodyString = jsonParser.encodeToString(requestBodyObj)
        var attempt = 0
        while (attempt <= MAX_RETRIES) {
            try {
                val response = httpClient.post(
                    url = finalUrl,
                    body = requestBodyString,
                    headers = mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json")
                )
                if (response.status == 429) {
                    attempt++
                    if (attempt > MAX_RETRIES) return null
                    val waitTime = 2000L * (2.0.pow(attempt - 1).toLong())
                    delay(waitTime)
                    continue
                }
                if (response.status != 200) {
                    Log.e("LLMClient", "Error response status: ${response.status}")
                    return null
                }
                val responseString = response.body
                val geminiResponse = jsonParser.decodeFromString<GeminiResponse>(responseString)
                if (geminiResponse.error != null) {
                    Log.e("LLMClient", "Gemini API error: ${geminiResponse.error.message}")
                    if (geminiResponse.error.message?.contains("quota", true) == true) {
                        attempt++
                        if (attempt > MAX_RETRIES) return null
                        delay(5000L)
                        continue
                    }
                    return null
                }
                if (geminiResponse.promptFeedback?.blockReason != null) {
                    Log.w("LLMClient", "Blocked reason: ${geminiResponse.promptFeedback.blockReason}")
                    return null
                }
                return geminiResponse.candidates?.takeIf { it.isNotEmpty() }
                    ?.flatMap { it.content?.parts ?: emptyList() }
                    ?.joinToString("\n") { it.text }
            } catch (e: Exception) {
                Log.e("LLMClient", "Exception sending prompt", e)
                return null
            }
        }
        return null
    }

    override fun close() { httpClient.close() }

    @Serializable data class GeminiRequest(val contents: List<ContentInput>, val generationConfig: GenerationConfig? = null)
    @Serializable data class GenerationConfig(val temperature: Double, val maxOutputTokens: Int? = null)
    @Serializable data class ContentInput(val parts: List<PartInput>)
    @Serializable data class PartInput(val text: String)
    @Serializable data class GeminiResponse(val candidates: List<Candidate>? = null, val error: GeminiError? = null, val promptFeedback: PromptFeedback? = null)
    @Serializable data class GeminiError(val code: Int? = null, val message: String? = null, val status: String? = null)
    @Serializable data class PromptFeedback(val blockReason: String? = null)
    @Serializable data class Candidate(val content: Content?, val finishReason: String? = null)
    @Serializable data class Content(val parts: List<Part>)
    @Serializable data class Part(val text: String)
}

suspend fun validateGeminiKey(apiKey: String, proxyIp: String, proxyKey: String, enableProxy: Boolean): Boolean {
    val client = SharedHttpClient.createInstance(proxyIp, proxyKey, enableProxy)
    return try {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        client.get(url).status == 200
    } catch (e: Exception) {
        Log.e("ValidateKey", "Validation failed", e)
        false
    } finally { client.close() }
}

class NewsSummarizer(private val db: DbHelper, private val llm: LLMClient) {
    data class Topics(val title: String, val ids: List<Long>?, val weight: Int = 0)
    private val SUMMARY_BATCH_SIZE = 7
    private val NEWS_BATCH_SIZE = 40
    private val TAG = "NewsSummarizer"

    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 14515200,
        readyFunc: () -> Unit,
        filterTopics: Boolean = false
    ): SummarizationResult {
        try {
            Log.d(TAG, "Starting summarizeTopics")
            val messages = db.getMessages(messageSeconds).sortedBy { it.time }
            if (messages.isEmpty()) { readyFunc(); return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE) }

            val unfinishedTitles = db.getTitles().filter { it.text == "<промежуточный текст>" && it.time.toInt() == 0 }
            if (unfinishedTitles.isEmpty()) {
                val currentLanguage = MewsRepository.currentLanguage.first() ?: "english"
                if (!processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics)) {
                    readyFunc(); return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                }
                MewsRepository.setLastTitlesUpdate(System.currentTimeMillis())
            }

            val titlesToSummarize = db.getTitles().filter { it.text == "<промежуточный текст>" }
                .map { Topics(it.title, db.dbUnpack(it.ids).mapNotNull { id -> id.toLongOrNull() }) }

            if (titlesToSummarize.isEmpty()) { readyFunc(); return SummarizationResult.Success }

            val titleBatches = titlesToSummarize.chunked(SUMMARY_BATCH_SIZE)
            var processedBatches = 0
            val totalBatches = titleBatches.size
            val semaphore = Semaphore(1)
            val currentProgress = MewsRepository.updatingProgress.first()
            val remainingProgress = 0.95f - currentProgress
            var successCount = 0
            var wasEmptyAnswer = false

            coroutineScope {
                titleBatches.map { batch ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                processedBatches++
                                if (processedBatches > 1) delay(4000L)
                                MewsRepository.setUpdatingState("summarizing_topics")
                                MewsRepository.setUpdatingProgress((currentProgress + remainingProgress * processedBatches / totalBatches).coerceIn(0f, 0.95f))

                                val currentLanguage = MewsRepository.currentLanguage.first() ?: "english"
                                val topicsData = batch.mapNotNull { topic ->
                                    val suitableMessages = topic.ids?.mapNotNull { id -> messages.find { it.id == id } ?: db.getMessage(id) } ?: emptyList()
                                    if (suitableMessages.isEmpty()) null else Triple(topic, suitableMessages, suitableMessages.joinToString("\n") { "— ${it.mess}" })
                                }
                                if (topicsData.isEmpty()) return@withPermit

                                val response = withTimeout(150000L) { sumTopicsBatch(llm, topicsData, MewsRepository.bannedNewsFlow.value.joinToString("'; '"), currentLanguage) }
                                if (response.isBlank()) { wasEmptyAnswer = true; return@withPermit }

                                val jsonArray = safeParseJsonArray(response)
                                for (i in 0 until jsonArray.length()) {
                                    try {
                                        val obj = jsonArray.getJSONObject(i)
                                        val id = obj.getLong("id")
                                        val summary = obj.getString("summary")
                                        val originalData = topicsData.find { it.first.ids?.contains(id) == true }
                                        if (originalData != null) {
                                            val (topic, suitableMessages, _) = originalData
                                            db.updateTitle(
                                                name = topic.title,
                                                newTime = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis(),
                                                newText = summary,
                                                newSources = db.dbPack(*suitableMessages.map { it.source }.distinct().toTypedArray()),
                                                newLinks = db.dbPack(*suitableMessages.map { it.id.toString() }.distinct().toTypedArray())
                                            )
                                            successCount++
                                        }
                                    } catch (e: Exception) { Log.e(TAG, "Error parsing summary item", e) }
                                }
                            } catch (e: Exception) { Log.e(TAG, "Error in batch processing", e) }
                        }
                    }
                }.awaitAll()
            }
            if (successCount == 0 && titlesToSummarize.isNotEmpty()) {
                return if (wasEmptyAnswer) SummarizationResult.Failure(SummarizationErrorType.EMPTY_ANSWER) else SummarizationResult.Failure(SummarizationErrorType.SUMMARIZE_TOPICS_FAILED)
            }
            readyFunc(); return SummarizationResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Global error in summarizeTopics", e)
            readyFunc(); return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    private suspend fun processNewsInBatches(maxTopics: Int, messages: List<Message>, lang: String, filter: Boolean): Boolean {
        val batches = messages.distinctBy { it.id }.chunked(NEWS_BATCH_SIZE)
        val allExtracted = mutableListOf<Topics>()
        val semaphore = Semaphore(1)
        try {
            MewsRepository.setUpdatingState("extracting_topics")
            MewsRepository.setUpdatingProgress((MewsRepository.updatingProgress.first() + 0.1f).coerceIn(0f, 0.2f))
            coroutineScope {
                batches.map { batch -> async(Dispatchers.IO) { semaphore.withPermit { extractTopicsFromBatch(batch, maxTopics, lang) } } }
                    .forEach { it.await()?.let { allExtracted.addAll(it) } }
            }
            if (allExtracted.isEmpty()) return false
            return if (filter) {
                MewsRepository.setUpdatingState("filtering_topics")
                MewsRepository.setUpdatingProgress((MewsRepository.updatingProgress.first() + 0.1f).coerceIn(0f, 0.3f))
                mergeAndFilterTopics(allExtracted, maxTopics, lang)
            } else {
                allExtracted
                    .sortedByDescending { it.weight }
                    .take(maxTopics).forEach { batch ->
                        db.addTitle(0, batch.title, "<промежуточный текст>", "<промежуточный текст>", db.dbPack(*(batch.ids?.map { it.toString() } ?: emptyList()).toTypedArray()))
                    }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processNewsInBatches", e)
            return false
        }
    }

    private suspend fun extractTopicsFromBatch(batch: List<Message>, max: Int, lang: String): List<Topics>? {
        val prompt = "Сгруппируй новости по событиям (макс $max). Верни ТОЛЬКО JSON массив: [{\"title\": \"Заголовок\", \"id\": [101, 105], \"weight\": 8}]. Где weight - важность события от 1 до 10. Язык: $lang. Новости:\n${batch.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }}"
        return try {
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) ?: "" }
            if (response.isBlank()) return null
            val jsonArray = safeParseJsonArray(response)
            val topics = mutableListOf<Topics>()
            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val idsArr = obj.getJSONArray("id")
                    val w = obj.optInt("weight", 5)
                    topics.add(Topics(obj.getString("title"), (0 until idsArr.length()).map { idsArr.getLong(it) }, w))
                } catch (e: Exception) { continue }
            }
            topics
        } catch (e: Exception) {
            Log.e(TAG, "Error in extractTopicsFromBatch", e)
            null
        }
    }

    private suspend fun mergeAndFilterTopics(topics: List<Topics>, max: Int, lang: String): Boolean {
        val jsonInput = JSONArray(topics.map { t -> JSONObject().apply { put("title", t.title); put("ids", JSONArray(t.ids)); put("weight", t.weight) } }).toString()
        val prompt = "Объедини дублирующиеся темы (макс $max). Верни JSON: [{\"title\": \"Заголовок\", \"ids\": [1, 2, 3], \"weight\": 9}]. weight - итоговая важность темы от 1 до 10. Язык: $lang. Данные:\n$jsonInput"
        return try {
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) ?: "" }
            if (response.isBlank()) return false
            val jsonArray = safeParseJsonArray(response)
            val toAdd = mutableListOf<Topics>()
            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val idsArr = obj.getJSONArray("ids")
                    val w = obj.optInt("weight", 5)
                    toAdd.add(Topics(obj.getString("title"), (0 until idsArr.length()).map { idsArr.getLong(it) }, w))
                } catch (e: Exception) {}
            }
            if (toAdd.isEmpty()) return false
            db.titlesTimeKill(0)
            toAdd.sortedByDescending { it.weight }
                .take(max)
                .forEach { t -> db.addTitle(0, t.title, "<промежуточный текст>", "<промежуточный текст>", db.dbPack(*(t.ids?.map { it.toString() } ?: emptyList()).toTypedArray())) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in mergeAndFilterTopics", e)
            false
        }
    }

    private suspend fun sumTopicsBatch(llm: LLMClient, data: List<Triple<Topics, List<Message>, String>>, banned: String, lang: String): String {
        val jsonInput = JSONArray(data.map { (t, _, txt) ->
            JSONObject().apply {
                put("id", t.ids?.firstOrNull() ?: 0L)
                put("title", t.title)
                put("news_content", txt)
            }
        })

        val prompt = """
        Ты — профессиональный редактор новостной ленты. Твоя задача — написать живые, вовлекающие саммари для предоставленных тем.
        
        Правила работы:
        1. **Адаптивный объем (ВАЖНО):** Проанализируй количество фактов в исходном тексте "news_content".
           - Если информации много и тема сложная: пиши подробно, раскрывай детали, не "комкай" повествование. Целевой объем: до 500 слов.
           - Если новость стандартная или короткая: пиши емко. Целевой объем: до 300 слов.
        2. **Стиль:** Smart Casual. Пиши увлекательно, но сохраняй экспертность. Без кликбейта и воды.
        3. **Фильтр:** Если тема или контент касаются '$banned', поле summary должно быть пустой строкой "".
        4. **Язык вывода:** $lang.
        
        Формат вывода строго JSON (список объектов): 
        [{"id": 123, "title": "Заголовок темы", "summary": "Текст саммари..."}]
        
        Ввод:
        $jsonInput
    """.trimIndent()

        return llm.sendPrompt(prompt) ?: ""
    }

    private fun safeParseJsonArray(str: String): JSONArray {
        val clean = str.trim().removePrefix("```json").removeSuffix("```").trim()
        return try { JSONArray(clean) } catch (e: JSONException) {
            val last = clean.lastIndexOf('}')
            if (last != -1) try { JSONArray(clean.take(last + 1) + "]") } catch (e2: Exception) { JSONArray() } else JSONArray()
        }
    }
}