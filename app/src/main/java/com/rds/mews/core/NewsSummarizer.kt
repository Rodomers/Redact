package com.rds.mews.core

import android.util.Log
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.GeminiException
import com.rds.mews.settings_manager.SummarizationErrorType
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

    suspend fun sendPrompt(prompt: String): String {
        val requestBodyObj = GeminiRequest(
            contents = listOf(ContentInput(parts = listOf(PartInput(prompt)))),
            generationConfig = GenerationConfig(temperature = 0.5, maxOutputTokens = 8192)
        )
        val requestBodyString = try {
            jsonParser.encodeToString(requestBodyObj)
        } catch (e: Exception) {
            throw GeminiException(
                SummarizationErrorType.JSON_PARSING_FAILED,
                "Request serialization failed"
            )
        }

        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= MAX_RETRIES) {
            try {
                val response = httpClient.post(
                    url = finalUrl,
                    body = requestBodyString,
                    headers = mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json")
                )

                if (response.status != 200) {
                    when (response.status) {
                        429 -> {
                            attempt++
                            if (attempt > MAX_RETRIES) throw GeminiException(SummarizationErrorType.RATE_LIMIT_EXCEEDED)
                            val waitTime = 2000L * (2.0.pow(attempt - 1).toLong())
                            delay(waitTime)
                            continue
                        }
                        400, 403 -> throw GeminiException(SummarizationErrorType.API_KEY_INVALID, "HTTP ${response.status}")
                        else -> {
                            Log.e("LLMClient", "Error response status: ${response.status}")
                            if (attempt >= MAX_RETRIES) throw GeminiException(SummarizationErrorType.UNKNOWN_ERROR, "HTTP ${response.status}")
                        }
                    }
                }

                val responseString = response.body
                Log.d("LLMClient", "Raw response: $responseString") // <-- Добавить это
                val geminiResponse = try {
                    jsonParser.decodeFromString<GeminiResponse>(responseString)
                } catch (e: Exception) {
                    throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Response parsing failed")
                }

                if (geminiResponse.error != null) {
                    Log.e("LLMClient", "Gemini API error: ${geminiResponse.error.message}")
                    val msg = geminiResponse.error.message ?: ""

                    if (msg.contains("quota", true)) {
                        throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, msg)
                    }
                    if (msg.contains("key", true) || geminiResponse.error.code == 400) {
                        throw GeminiException(SummarizationErrorType.API_KEY_INVALID, msg)
                    }

                    attempt++
                    if (attempt > MAX_RETRIES) throw GeminiException(SummarizationErrorType.UNKNOWN_ERROR, msg)
                    delay(5000L)
                    continue
                }

                if (geminiResponse.promptFeedback?.blockReason != null) {
                    Log.w("LLMClient", "Blocked reason: ${geminiResponse.promptFeedback.blockReason}")
                    throw GeminiException(SummarizationErrorType.CONTENT_BLOCKED, geminiResponse.promptFeedback.blockReason)
                }

                val resultText = geminiResponse.candidates?.takeIf { it.isNotEmpty() }
                    ?.flatMap { it.content?.parts ?: emptyList() }
                    ?.joinToString("\n") { it.text }

                if (resultText.isNullOrBlank()) {
                    throw GeminiException(SummarizationErrorType.EMPTY_ANSWER)
                }

                return resultText

            } catch (e: GeminiException) {
                throw e
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                throw GeminiException(SummarizationErrorType.NETWORK_TIMEOUT)
            } catch (e: Exception) {
                Log.e("LLMClient", "Exception sending prompt", e)
                lastException = e
                attempt++
                if (attempt <= MAX_RETRIES) delay(1000L)
            }
        }
        val errorMsg = lastException?.message ?: "Empty message in ${lastException?.javaClass?.name}"
        throw GeminiException(SummarizationErrorType.NO_NETWORK, errorMsg)
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

    private fun safeReadyFunc(func: () -> Unit) {
        try { func() } catch (e: Exception) { Log.e(TAG, "readyFunc failed", e) }
    }

    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 14515200,
        readyFunc: () -> Unit,
        filterTopics: Boolean = false
    ): SummarizationResult {
        try {
            Log.d(TAG, "Starting summarizeTopics")
            val messages = db.getMessages(messageSeconds).sortedBy { it.time }
            if (messages.isEmpty()) { safeReadyFunc(readyFunc); return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE) }

            val unfinishedTitles = db.getTitles().filter { it.text == "<промежуточный текст>" && it.time.toInt() == 0 }

            // Безопасное получение языка
            val currentLanguage = try {
                MewsRepository.currentLanguage.first() ?: "english"
            } catch (e: Exception) { "english" }

            if (unfinishedTitles.isEmpty()) {
                try {
                    processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics)
                    MewsRepository.setLastTitlesUpdate(System.currentTimeMillis())
                } catch (e: GeminiException) {
                    safeReadyFunc(readyFunc)
                    return SummarizationResult.Failure(e.errorType, e)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    safeReadyFunc(readyFunc)
                    return SummarizationResult.Failure(SummarizationErrorType.EXTRACT_TOPICS_FAILED, e)
                }
            }

            val titlesToSummarize = db.getTitles().filter { it.text == "<промежуточный текст>" }
                .map { Topics(it.title, db.dbUnpack(it.ids).mapNotNull { id -> id.toLongOrNull() }) }

            if (titlesToSummarize.isEmpty()) { safeReadyFunc(readyFunc); return SummarizationResult.Success }

            val titleBatches = titlesToSummarize.chunked(SUMMARY_BATCH_SIZE)
            var processedBatches = 0
            val totalBatches = titleBatches.size
            val semaphore = Semaphore(1)

            // Безопасное получение прогресса
            val currentProgress = try { MewsRepository.updatingProgress.first() } catch(e: Exception) { 0f }
            val remainingProgress = 0.95f - currentProgress
            var successCount = 0

            // ФИКС: Получаем список забаненных слов ДО цикла и безопасно.
            // Если репозиторий упадет тут, мы просто будем работать без фильтра, а не крашить весь процесс.
            val bannedWords = try {
                MewsRepository.bannedNewsFlow.value.joinToString("'; '")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get banned words, continuing without filter", e)
                ""
            }

            var lastError: SummarizationErrorType? = null

            coroutineScope {
                titleBatches.map { batch ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                processedBatches++
                                if (processedBatches > 1) delay(4000L)

                                // ФИКС: Оборачиваем обновление UI в try-catch.
                                // Ошибка обновления прогрессбара не должна останавливать саммаризацию.
                                try {
                                    MewsRepository.setUpdatingState("summarizing_topics")
                                    MewsRepository.setUpdatingProgress((currentProgress + remainingProgress * processedBatches / totalBatches).coerceIn(0f, 0.95f))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to update UI progress", e)
                                }

                                val topicsData = batch.mapNotNull { topic ->
                                    val suitableMessages = topic.ids?.mapNotNull { id -> messages.find { it.id == id } ?: db.getMessage(id) } ?: emptyList()
                                    if (suitableMessages.isEmpty()) null else Triple(topic, suitableMessages, suitableMessages.joinToString("\n") { "— ${it.mess}" })
                                }
                                if (topicsData.isEmpty()) return@withPermit

                                val response = withTimeout(150000L) {
                                    sumTopicsBatch(llm, topicsData, bannedWords, currentLanguage)
                                }

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
                            } catch (e: GeminiException) {
                                Log.e(TAG, "Batch failed: ${e.errorType}")
                                lastError = e.errorType
                                // Если ошибка критическая (квота, ключ) — прерываем всё сразу
                                if (e.errorType == SummarizationErrorType.API_KEY_INVALID ||
                                    e.errorType == SummarizationErrorType.QUOTA_EXCEEDED ||
                                    e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED ||
                                    e.errorType == SummarizationErrorType.NO_NETWORK) {
                                    throw e
                                }
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                lastError = SummarizationErrorType.NETWORK_TIMEOUT
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Log.e(TAG, "CRITICAL ERROR in batch processing. Type: ${e.javaClass.name}", e)
                                lastError = SummarizationErrorType.UNKNOWN_ERROR
                            }
                        }
                    }
                }.awaitAll()
            }

            if (successCount == 0 && titlesToSummarize.isNotEmpty()) {
                val error = lastError ?: SummarizationErrorType.SUMMARIZE_TOPICS_FAILED
                safeReadyFunc(readyFunc)
                return SummarizationResult.Failure(error)
            }
            safeReadyFunc(readyFunc)
            return SummarizationResult.Success
        } catch (e: GeminiException) {
            Log.e(TAG, "Summarization aborted: ${e.errorType}")
            safeReadyFunc(readyFunc)
            return SummarizationResult.Failure(e.errorType, e)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Summarization cancelled")
                safeReadyFunc(readyFunc)
                return SummarizationResult.Failure(SummarizationErrorType.JOB_CANCELLED)
            }
            Log.e(TAG, "Global error in summarizeTopics", e)
            safeReadyFunc(readyFunc)
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    private suspend fun processNewsInBatches(maxTopics: Int, messages: List<Message>, lang: String, filter: Boolean) {
        val batches = messages.distinctBy { it.id }.chunked(NEWS_BATCH_SIZE)
        val allExtracted = mutableListOf<Topics>()
        val semaphore = Semaphore(1)

        try {
            val current = try { MewsRepository.updatingProgress.first() } catch (e:Exception) { 0f }
            MewsRepository.setUpdatingState("extracting_topics")
            MewsRepository.setUpdatingProgress((current + 0.1f).coerceIn(0f, 0.2f))
        } catch (e: Exception) { Log.e(TAG, "Repo update failed", e) }

        coroutineScope {
            batches.map { batch -> async(Dispatchers.IO) { semaphore.withPermit { extractTopicsFromBatch(batch, maxTopics, lang) } } }
                .forEach {
                    it.await().let { allExtracted.addAll(it) }
                }
        }

        if (allExtracted.isEmpty()) {
            throw GeminiException(SummarizationErrorType.EXTRACT_TOPICS_FAILED, "No topics found")
        }

        if (filter) {
            try {
                val current = try { MewsRepository.updatingProgress.first() } catch(e:Exception) { 0f }
                MewsRepository.setUpdatingState("filtering_topics")
                MewsRepository.setUpdatingProgress((current + 0.1f).coerceIn(0f, 0.3f))
            } catch(e: Exception) {}
            mergeAndFilterTopics(allExtracted, maxTopics, lang)
        } else {
            allExtracted
                .sortedByDescending { it.weight }
                .take(maxTopics).forEach { batch ->
                    db.addTitle(0, batch.title, "<промежуточный текст>", "<промежуточный текст>", db.dbPack(*(batch.ids?.map { it.toString() } ?: emptyList()).toTypedArray()))
                }
        }
    }

    private suspend fun extractTopicsFromBatch(batch: List<Message>, max: Int, lang: String): List<Topics> {
        val prompt = "Сгруппируй новости по событиям (макс $max). Верни ТОЛЬКО JSON массив: [{\"title\": \"Заголовок\", \"id\": [101, 105], \"weight\": 8}]. Где weight - важность события от 1 до 10. Язык: $lang. Новости:\n${batch.joinToString("\n") { "• ${it.mess} (id - ${it.id})" }}"
        val response = withTimeout(60000L) { llm.sendPrompt(prompt) }
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
        return topics
    }

    private suspend fun mergeAndFilterTopics(topics: List<Topics>, max: Int, lang: String) {
        val jsonInput = JSONArray(topics.map { t -> JSONObject().apply { put("title", t.title); put("ids", JSONArray(t.ids)); put("weight", t.weight) } }).toString()
        val prompt = "Объедини дублирующиеся темы (макс $max). Верни JSON: [{\"title\": \"Заголовок\", \"ids\": [1, 2, 3], \"weight\": 9}]. weight - итоговая важность темы от 1 до 10. Язык: $lang. Данные:\n$jsonInput"

        val response = withTimeout(60000L) { llm.sendPrompt(prompt) }
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

        if (toAdd.isEmpty()) throw GeminiException(SummarizationErrorType.FILTER_FAILED)

        db.titlesTimeKill(0)
        toAdd.sortedByDescending { it.weight }
            .take(max)
            .forEach { t -> db.addTitle(0, t.title, "<промежуточный текст>", "<промежуточный текст>", db.dbPack(*(t.ids?.map { it.toString() } ?: emptyList()).toTypedArray())) }
    }

    private suspend fun sumTopicsBatch(llm: LLMClient, data: List<Triple<Topics, List<Message>, String>>, banned: String, lang: String): String {
        val jsonInput = JSONArray(data.map { (t, _, txt) ->
            JSONObject().apply {
                put("id", t.ids?.firstOrNull() ?: 0L)
                put("title", t.title)
                put("news_content", txt)
            }
        })

        Log.d(TAG, "Generating summary for ${data.size} items. Json length: ${jsonInput.toString().length}")

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

        return llm.sendPrompt(prompt)
    }

    private fun safeParseJsonArray(str: String): JSONArray {
        val clean = str.trim().removePrefix("```json").removeSuffix("```").trim()
        try {
            return JSONArray(clean)
        } catch (e: JSONException) {
            val last = clean.lastIndexOf('}')
            if (last != -1) {
                try {
                    return JSONArray(clean.take(last + 1) + "]")
                } catch (e2: Exception) {
                    throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Repair failed")
                }
            }
            throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Invalid structure")
        }
    }
}