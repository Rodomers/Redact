package com.rds.mews.core

import android.annotation.SuppressLint
import android.util.Log
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.SettingsManager
import com.rds.mews.localcore.SummarizationErrorType
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
import kotlin.math.min
import java.io.Closeable
import kotlin.math.pow

class LLMClient(
    val apiKey: String = "",
    val MODEL: String = MewsRepository.defaultModel.key,
    private val URL_TEMPLATE: String = "https://generativelanguage.googleapis.com/v1beta/models/%MODEL%:generateContent",
    enableProxy: Boolean = false
) : Closeable {

    private val finalUrl = URL_TEMPLATE.replace("%MODEL%", MODEL.ifBlank { MewsRepository.defaultModel.key })
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
                if (response.status != 200) return null
                val responseString = response.body
                val geminiResponse = jsonParser.decodeFromString<GeminiResponse>(responseString)
                if (geminiResponse.error != null) {
                    if (geminiResponse.error.message?.contains("quota", true) == true) {
                        attempt++
                        if (attempt > MAX_RETRIES) return null
                        delay(5000L)
                        continue
                    }
                    return null
                }
                if (geminiResponse.promptFeedback?.blockReason != null) return null
                return geminiResponse.candidates?.takeIf { it.isNotEmpty() }
                    ?.flatMap { it.content?.parts ?: emptyList() }
                    ?.joinToString("\n") { it.text }
            } catch (e: Exception) {
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
    } catch (e: Exception) { false } finally { client.close() }
}

private const val TAG = "SumDebug"

class NewsSummarizer(private val db: DbHelper, private val llm: LLMClient) {
    data class Topics(val title: String, val ids: List<Long>?)
    private val SUMMARY_BATCH_SIZE = 7
    private val NEWS_BATCH_SIZE = 40

    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 14515200,
        readyFunc: () -> Unit,
        settingsManager: SettingsManager,
        filterTopics: Boolean = false
    ): SummarizationResult {
        Log.d(TAG, "Summarizer: Started summarizeTopics")
        try {
            val messages = db.getMessages(messageSeconds).sortedBy { it.time }
            Log.d(TAG, "Summarizer: Found ${messages.size} messages.")

            if (messages.isEmpty()) {
                readyFunc(); return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
            }

            val unfinishedTitles = db.getTitles().filter { it.text == "<промежуточный текст>" && it.time.toInt() == 0 }

            if (unfinishedTitles.isEmpty()) {
                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")
                if (!processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics, settingsManager)) {
                    readyFunc(); return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED)
                }
                settingsManager.saveLong(MewsRepository.LAST_TITLES_UPDATE, System.currentTimeMillis())
            }

            val titlesToSummarize = db.getTitles().filter { it.text == "<промежуточный текст>" }
                .map { Topics(it.title, db.dbUnpack(it.ids).mapNotNull { id -> id.toLongOrNull() }) }

            if (titlesToSummarize.isEmpty()) { readyFunc(); return SummarizationResult.Success }

            val titleBatches = titlesToSummarize.chunked(SUMMARY_BATCH_SIZE)
            var processedBatches = 0
            val totalBatches = titleBatches.size
            val semaphore = Semaphore(1)
            val currentProgress = settingsManager.getFloat(MewsRepository.UPDATING_PROGRESS, 0f)
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
                                settingsManager.saveString(MewsRepository.UPDATING_STATE, "summarizing_topics")
                                settingsManager.saveFloat(MewsRepository.UPDATING_PROGRESS, (currentProgress + remainingProgress * processedBatches / totalBatches).coerceIn(0f, 0.95f))

                                val currentLanguage = settingsManager.getString(MewsRepository.CURRENT_LANGUAGE, "russian")

                                val topicsData = batch.mapIndexedNotNull { index, topic ->
                                    val suitableMessages = topic.ids?.mapNotNull { id -> messages.find { it.id == id } ?: db.getMessage(id) } ?: emptyList()
                                    if (suitableMessages.isEmpty()) null
                                    else Triple(index, topic, suitableMessages)
                                }

                                if (topicsData.isEmpty()) return@withPermit

                                val response = withTimeout(150000L) { sumTopicsBatch(llm, topicsData, MewsRepository.bannedNewsFlow.value.joinToString("'; '"), currentLanguage) }
                                if (response.isBlank()) { wasEmptyAnswer = true; return@withPermit }

                                val jsonArray = safeParseJsonArray(response)

                                for (i in 0 until jsonArray.length()) {
                                    try {
                                        val obj = jsonArray.getJSONObject(i)
                                        val id = obj.optInt("id", -1)

                                        val originalData = if (id != -1) {
                                            topicsData.find { it.first == id }
                                        } else {
                                            val titleKey = obj.optString("title")
                                            topicsData.find { it.second.title == titleKey }
                                        }

                                        if (originalData != null) {
                                            val (_, topic, suitableMessages) = originalData
                                            val summary = obj.optString("summary")

                                            if (summary.isNotBlank()) {
                                                db.updateTitle(
                                                    name = topic.title,
                                                    newTime = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis(),
                                                    newText = summary,
                                                    newSources = db.dbPack(*suitableMessages.map { it.source }.distinct().toTypedArray()),
                                                    newLinks = db.dbPack(*suitableMessages.map { it.id.toString() }.distinct().toTypedArray())
                                                )
                                                successCount++
                                            }
                                        }
                                    } catch (e: Exception) { Log.e(TAG, "Error parsing summary item", e) }
                                }
                            } catch (e: Exception) { Log.e(TAG, "Batch processing error", e) }
                        }
                    }
                }.awaitAll()
            }
            if (successCount == 0 && titlesToSummarize.isNotEmpty()) {
                return if (wasEmptyAnswer) SummarizationResult.Failure(SummarizationErrorType.EMPTY_ANSWER) else SummarizationResult.Failure(SummarizationErrorType.SUMMARIZE_TOPICS_FAILED)
            }
            readyFunc(); return SummarizationResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Global error", e)
            readyFunc(); return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    private suspend fun processNewsInBatches(maxTopics: Int, messages: List<Message>, lang: String, filter: Boolean, sm: SettingsManager): Boolean {
        val batches = messages.distinctBy { it.id }.chunked(NEWS_BATCH_SIZE)
        val allExtractedWithScore = mutableListOf<Pair<Topics, Int>>()
        val semaphore = Semaphore(1)

        try {
            sm.saveString(MewsRepository.UPDATING_STATE, "extracting_topics")
            sm.saveFloat(MewsRepository.UPDATING_PROGRESS, (sm.getFloat(MewsRepository.UPDATING_PROGRESS, 0.1f) + 0.1f).coerceIn(0f, 0.2f))

            coroutineScope {
                batches.map { batch ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit { extractTopicsFromBatch(batch, maxTopics, lang) }
                    }
                }.forEach {
                    it.await()?.let { list -> allExtractedWithScore.addAll(list) }
                }
            }

            if (allExtractedWithScore.isEmpty()) return false

            // Сортируем ВСЕ найденные темы по важности (score), чтобы при обрезке остались самые важные
            val sortedTopics = allExtractedWithScore.sortedByDescending { it.second }.map { it.first }

            return if (filter) {
                sm.saveString(MewsRepository.UPDATING_STATE, "filtering_topics")
                sm.saveFloat(MewsRepository.UPDATING_PROGRESS, (sm.getFloat(MewsRepository.UPDATING_PROGRESS, 0.1f) + 0.1f).coerceIn(0f, 0.3f))
                mergeAndFilterTopics(sortedTopics, maxTopics, lang)
            } else {
                sortedTopics.take(maxTopics).forEach { topic ->
                    db.addTitle(0, topic.title, "<промежуточный текст>", "<промежуточный текст>", db.dbPack(*(topic.ids?.map { it.toString() } ?: emptyList()).toTypedArray()))
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Process batches error", e)
            return false
        }
    }

    private suspend fun extractTopicsFromBatch(batch: List<Message>, max: Int, lang: String): List<Pair<Topics, Int>>? {
        // Запрос теперь требует поле score
        val prompt = """
            Сгруппируй новости по событиям (макс $max).
            Для каждой группы оцени важность 'score' (0-10), где 10 — главное мировое событие, 0 — мусор.
            Верни JSON: [{"title": "Заголовок", "id": [1, 2], "score": 8}].
            Язык: $lang.
            Новости:
            ${batch.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }}
        """.trimIndent()

        return try {
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) ?: "" }
            if (response.isBlank()) return null

            val jsonArray = safeParseJsonArray(response)
            val result = mutableListOf<Pair<Topics, Int>>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val idsArr = obj.getJSONArray("id")
                    val topic = Topics(obj.getString("title"), (0 until idsArr.length()).map { idsArr.getLong(it) })
                    val score = obj.optInt("score", 5) // По умолчанию средняя важность
                    result.add(topic to score)
                } catch (e: Exception) { continue }
            }
            result
        } catch (e: Exception) { null }
    }

    private suspend fun mergeAndFilterTopics(topics: List<Topics>, max: Int, lang: String): Boolean {
        val jsonInput = JSONArray(topics.map { t -> JSONObject().apply { put("title", t.title); put("ids", JSONArray(t.ids)) } }).toString()
        val prompt = "Объедини дубли. Верни самых важных (макс $max). JSON: [{\"title\": \"...\", \"ids\": [...]}]. Язык: $lang. Данные:\n$jsonInput"

        return try {
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) ?: "" }
            if (response.isBlank()) return false

            val jsonArray = safeParseJsonArray(response)
            val toAdd = mutableListOf<Topics>()

            for (i in 0 until min(jsonArray.length(), max)) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val idsArr = obj.getJSONArray("ids")
                    toAdd.add(Topics(obj.getString("title"), (0 until idsArr.length()).map { idsArr.getLong(it) }))
                } catch (e: Exception) {}
            }

            if (toAdd.isEmpty()) return false

            db.titlesTimeKill(0)
            toAdd.forEach { t -> db.addTitle(0, t.title, "<промежуточный текст>", "<промежуточный текст>", db.dbPack(*(t.ids?.map { it.toString() } ?: emptyList()).toTypedArray())) }
            true
        } catch (e: Exception) { false }
    }

    private suspend fun sumTopicsBatch(llm: LLMClient, data: List<Triple<Int, Topics, List<Message>>>, banned: String, lang: String): String {
        val jsonInput = JSONArray(data.map { (id, t, msgs) ->
            JSONObject().apply {
                put("id", id)
                put("title", t.title)
                put("news_content", msgs.joinToString("\n") { "— ${it.mess}" })
            }
        })

        val prompt = """
        Ты — редактор. Напиши саммари.
        1. **ID:** Верни поле "id" (Int) из входных данных.
        2. **Объем:** Сложная тема - до 500 слов, простая - до 300.
        3. **Фильтр:** Если тема о '$banned', summary = "".
        4. **Язык:** $lang.
        
        JSON: [{"id": 0, "title": "Заголовок", "summary": "Текст..."}]
        
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