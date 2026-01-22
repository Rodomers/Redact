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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.io.Closeable
import kotlin.math.pow
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern

class SmartRateLimiter(private val minIntervalMs: Long = 4000L) {
    private val mutex = Mutex()
    private var nextAllowedTime = 0L

    suspend fun waitIfNeeded() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now < nextAllowedTime) {
                delay(nextAllowedTime - now)
            }
            nextAllowedTime = System.currentTimeMillis() + minIntervalMs
        }
    }

    suspend fun penalize() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val penaltyTarget = now + 10000L
            if (penaltyTarget > nextAllowedTime) {
                nextAllowedTime = penaltyTarget
            }
        }
    }
}

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
        } catch (_: Exception) {
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

                val responseString = response.body
                Log.d("LLMClient", "Raw response (Status ${response.status}): $responseString")

                if (response.status != 200) {
                    when (response.status) {
                        429 -> {
                            // ИЗМЕНЕНИЕ: Жесткая проверка на тип квоты.
                            // Если это RequestsPerDay или FreeTier - это жесткий бан на сутки, ждать нельзя.
                            if (responseString.contains("RequestsPerDay", ignoreCase = true)) {
                                Log.e("LLMClient", "Daily quota exceeded. Stopping immediately.")
                                throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, "Daily quota exceeded")
                            }

                            // Если просто quota (без уточнения про Day) или Rate limit - пробуем ждать
                            if (responseString.contains("quota", ignoreCase = true)) {
                                val waitTime = extractRetryTime(responseString)
                                if (waitTime > 0) {
                                    Log.w("LLMClient", "Rate limit hit. Waiting for ${waitTime}ms.")
                                    delay(waitTime + 1000)
                                    attempt++
                                    // Увеличиваем лимит попыток для wait-loop, но не бесконечно
                                    if (attempt > MAX_RETRIES + 2) throw GeminiException(SummarizationErrorType.RATE_LIMIT_EXCEEDED, "Retry loop exhausted")
                                    continue
                                }
                            }

                            attempt++
                            if (attempt > MAX_RETRIES) throw GeminiException(SummarizationErrorType.RATE_LIMIT_EXCEEDED)
                            val backoff = 2000L * (2.0.pow(attempt - 1).toLong())
                            delay(backoff)
                            continue
                        }
                        403 -> throw GeminiException(SummarizationErrorType.API_KEY_INVALID, "HTTP ${response.status}")
                        400 -> {
                            Log.e("LLMClient", "Bad Request (400). Likely content too large. Body: $responseString")
                            throw GeminiException(SummarizationErrorType.UNKNOWN_ERROR, "Content too large (HTTP 400)")
                        }
                        else -> {
                            Log.e("LLMClient", "Error response status: ${response.status}")
                            if (attempt >= MAX_RETRIES) throw GeminiException(SummarizationErrorType.UNKNOWN_ERROR, "HTTP ${response.status}")
                        }
                    }
                }

                val geminiResponse = try {
                    jsonParser.decodeFromString<GeminiResponse>(responseString)
                } catch (_: Exception) {
                    throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Response parsing failed")
                }

                if (geminiResponse.error != null) {
                    val msg = geminiResponse.error.message ?: ""
                    Log.e("LLMClient", "Gemini API error: $msg")

                    if (msg.contains("quota", true)) {
                        // Повторяем проверку на Daily limit и здесь
                        if (msg.contains("RequestsPerDay", ignoreCase = true) || msg.contains("FreeTier", ignoreCase = true)) {
                            throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, msg)
                        }

                        val waitTime = extractRetryTime(msg)
                        if (waitTime > 0) {
                            Log.w("LLMClient", "Quota error in JSON. Waiting ${waitTime}ms")
                            delay(waitTime + 1000)
                            attempt++
                            if (attempt > MAX_RETRIES + 2) throw GeminiException(SummarizationErrorType.RATE_LIMIT_EXCEEDED)
                            continue
                        }
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
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
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

    private fun extractRetryTime(message: String): Long {
        try {
            val pattern = Pattern.compile("retry in\\s+(\\d+(\\.\\d+)?)s")
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val seconds = matcher.group(1)?.toDoubleOrNull()
                if (seconds != null) {
                    return (seconds * 1000).toLong()
                }
            }
        } catch (e: Exception) {
            Log.e("LLMClient", "Failed to parse retry time", e)
        }
        return 0L
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
    data class Topics(val title: String, val ids: List<Long>?, val weight: Int = 0, val id: Long = 0)

    private val SUMMARY_START_BATCH_SIZE = 10
    private val EXTRACT_START_BATCH_SIZE = 60

    private val TAG = "NewsSummarizer"

    private val rateLimiter = SmartRateLimiter(minIntervalMs = 4000L)

    private var totalItemsToProcess = 0
    private var processedItemsCount = 0
    private var baseProgress = 0f

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

            val currentLanguage = try {
                MewsRepository.currentLanguage.first() ?: "english"
            } catch (_: Exception) { "english" }

            baseProgress = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { 0f }

            if (unfinishedTitles.isEmpty()) {
                try {
                    val extractionTime = System.currentTimeMillis()
                    processNewsInBatches(maxTopics, messages, currentLanguage, filterTopics)
                    MewsRepository.setLastTitlesUpdate(extractionTime)
                    baseProgress = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { baseProgress }
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
                .map { Topics(it.title, db.dbUnpack(it.ids).mapNotNull { id -> id.toLongOrNull() }, 0, it.id) }

            if (titlesToSummarize.isEmpty()) { safeReadyFunc(readyFunc); return SummarizationResult.Success }

            totalItemsToProcess = titlesToSummarize.size
            processedItemsCount = 0

            val availableProgressSpace = 0.95f - baseProgress

            var successCount = 0
            var lastError: SummarizationErrorType? = null

            val bannedWords = try {
                MewsRepository.bannedNewsFlow.value.joinToString("'; '")
            } catch (_: Exception) { "" }

            val batches = titlesToSummarize.chunked(SUMMARY_START_BATCH_SIZE)

            coroutineScope {
                batches.forEach { batch ->
                    MewsRepository.setUpdatingState("summarizing_topics")
                    val topicsData = batch.mapNotNull { topic ->
                        val suitableMessages = topic.ids?.mapNotNull { id -> messages.find { it.id == id } ?: db.getMessage(id) } ?: emptyList()
                        if (suitableMessages.isEmpty()) {
                            try { db.delTitle(name = topic.title) } catch (e: Exception) { Log.e(TAG, "Del empty title error", e) }
                            null
                        } else {
                            Triple(topic, suitableMessages, suitableMessages.joinToString("\n") { "— ${it.mess}" })
                        }
                    }

                    if (topicsData.isNotEmpty()) {
                        val results = adaptiveSummarizeBatch(topicsData, bannedWords, currentLanguage)
                        val successIds = results.map { it.first }.toSet()

                        if (results.isNotEmpty()) {
                            results.forEach { (_, resultData, originalData) ->
                                try {
                                    val (summary, newTitle) = resultData
                                    val (topic, suitableMessages, _) = originalData

                                    val newSources = db.dbPack(*suitableMessages.map { it.source }.distinct().toTypedArray())
                                    val newLinks = db.dbPack(*suitableMessages.map { it.id.toString() }.distinct().toTypedArray())
                                    val newTimeVal = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis()

                                    if (newTitle != topic.title) {
                                        db.delTitle(name = topic.title)
                                        db.addTitle(newTimeVal, newTitle, summary, newSources, newLinks)
                                    } else {
                                        db.updateTitle(topic.id, topic.title, summary, newSources, newLinks, newTimeVal)
                                    }
                                    successCount++
                                } catch(e: Exception) { Log.e(TAG, "Save error", e) }
                            }
                        } else {
                            if (lastError == null) lastError = SummarizationErrorType.SUMMARIZE_TOPICS_FAILED
                        }

                        topicsData.forEach { (topic, _, _) ->
                            if (!successIds.contains(topic.id)) {
                                Log.w(TAG, "Topic '${topic.title}' failed summarization. Cleanup zombie.")
                                try { db.delTitle(name = topic.title) } catch(e: Exception) { Log.e(TAG, "Zombie kill fail", e) }
                            }
                        }
                    }

                    processedItemsCount += batch.size
                    try {
                        val relativeProgress = processedItemsCount.toFloat() / totalItemsToProcess.toFloat()
                        val newProgress = baseProgress + (relativeProgress * availableProgressSpace)
                        MewsRepository.setUpdatingProgress(newProgress.coerceIn(0f, 0.95f))
                    } catch (_: Exception) {}
                }
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
                safeReadyFunc(readyFunc)
                return SummarizationResult.Failure(SummarizationErrorType.JOB_CANCELLED)
            }
            Log.e(TAG, "Global error in summarizeTopics", e)
            safeReadyFunc(readyFunc)
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    private suspend fun adaptiveSummarizeBatch(
        batch: List<Triple<Topics, List<Message>, String>>,
        bannedWords: String,
        lang: String
    ): List<Triple<Long, Pair<String, String>, Triple<Topics, List<Message>, String>>> {
        if (batch.isEmpty()) return emptyList()

        try {
            rateLimiter.waitIfNeeded()

            val response = try {
                withTimeout(180000L) {
                    sumTopicsBatch(llm, batch, bannedWords, lang)
                }
            } catch (e: GeminiException) {
                if (e.errorType == SummarizationErrorType.NO_NETWORK) throw e
                if (e.errorType == SummarizationErrorType.API_KEY_INVALID) throw e
                if (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED) throw e

                if (e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED) rateLimiter.penalize()

                if (e.errorType == SummarizationErrorType.CONTENT_BLOCKED) {
                    if (batch.size <= 1) {
                        val item = batch.firstOrNull()
                        item?.let {
                            Log.e(TAG, "Content blocked for title: '${it.first.title}'. Deleting from DB.")
                            try { db.delTitle(name = it.first.title) } catch (_: Exception) {}
                        }
                        return emptyList()
                    }
                    throw RuntimeException("Trigger split due to Blocked Content", e)
                }
                throw RuntimeException("Trigger split due to Gemini Error: ${e.errorType}", e)
            }

            val results = mutableListOf<Triple<Long, Pair<String, String>, Triple<Topics, List<Message>, String>>>()
            val (jsonArray, isBroken) = safeParseJsonArrayLenient(response)
            val processedIds = mutableSetOf<Long>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    val summary = obj.getString("summary")
                    val newTitle = obj.optString("title").takeIf { it.isNotBlank() } ?: ""

                    val original = batch.find { it.first.ids?.contains(id) == true }
                    if (original != null) {
                        val finalTitle = if (newTitle.isNotBlank()) newTitle else original.first.title
                        results.add(Triple(id, Pair(summary, finalTitle), original))
                        processedIds.add(id)
                    }
                } catch (_: Exception) {}
            }

            val victims = batch.filter { item ->
                val itemId = item.first.ids?.firstOrNull() ?: 0L
                !processedIds.contains(itemId)
            }

            if (victims.isNotEmpty()) {
                Log.w(TAG, "Summarization incomplete. Survivors: ${results.size}, Victims: ${victims.size}")
                val mid = victims.size / 2
                val left = adaptiveSummarizeBatch(victims.subList(0, mid), bannedWords, lang)
                val right = adaptiveSummarizeBatch(victims.subList(mid, victims.size), bannedWords, lang)
                results.addAll(left + right)
            }

            return results

        } catch (e: Exception) {
            if (e is GeminiException) {
                if (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED ||
                    e.errorType == SummarizationErrorType.API_KEY_INVALID ||
                    e.errorType == SummarizationErrorType.NO_NETWORK) {
                    throw e
                }
            }

            if (batch.size <= 1) {
                val item = batch.firstOrNull()
                Log.e(TAG, "Item failed summary completely: ${item?.first?.title}. Error: $e")
                return emptyList()
            }

            Log.w(TAG, "Batch summary failed, splitting ${batch.size} items SEQUENTIALLY...")

            val mid = batch.size / 2
            val left = adaptiveSummarizeBatch(batch.subList(0, mid), bannedWords, lang)
            val right = adaptiveSummarizeBatch(batch.subList(mid, batch.size), bannedWords, lang)
            return left + right
        }
    }

    private suspend fun processNewsInBatches(maxTopics: Int, messages: List<Message>, lang: String, filter: Boolean) {
        val uniqueMessages = messages.distinctBy { it.id }
        totalItemsToProcess = uniqueMessages.size
        processedItemsCount = 0

        val availableSpace = 0.4f

        try {
            MewsRepository.setUpdatingState("extracting_topics")
        } catch (_: Exception) {}

        val allExtracted = mutableListOf<Topics>()
        val batches = uniqueMessages.chunked(EXTRACT_START_BATCH_SIZE)

        coroutineScope {
            batches.forEach { batch ->
                val result = adaptiveExtractTopics(batch, maxTopics, lang)
                allExtracted.addAll(result)

                processedItemsCount += batch.size
                try {
                    val relativeProgress = processedItemsCount.toFloat() / totalItemsToProcess.toFloat()
                    val newProgress = baseProgress + (relativeProgress * availableSpace)
                    MewsRepository.setUpdatingProgress(newProgress)
                } catch(_: Exception) {}
            }
        }

        if (allExtracted.isEmpty()) {
            throw GeminiException(SummarizationErrorType.EXTRACT_TOPICS_FAILED, "No topics found")
        }

        if (filter) {
            try {
                val current = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { baseProgress + availableSpace }
                MewsRepository.setUpdatingState("filtering_topics")
                MewsRepository.setUpdatingProgress(current)
            } catch(_: Exception) {}
            mergeAndFilterTopics(allExtracted, maxTopics, lang)
        } else {
            allExtracted
                .sortedByDescending { it.weight }
                .take(maxTopics).forEach { batch ->
                    db.addTitle(0, batch.title, "<промежуточный текст>", "<промежуточный текст>", db.dbPack(*(batch.ids?.map { it.toString() } ?: emptyList()).toTypedArray()))
                }
        }
    }

    private suspend fun adaptiveExtractTopics(batch: List<Message>, max: Int, lang: String): List<Topics> {
        if (batch.isEmpty()) return emptyList()

        try {
            rateLimiter.waitIfNeeded()

            val sanitizedBatch = batch.joinToString("\n") {
                "• ${it.mess.replace("\"", "'").replace("`", "")} (id - ${it.id})"
            }
            val prompt = "Сгруппируй новости по событиям (макс $max). Верни ТОЛЬКО JSON массив: [{\"title\": \"Заголовок\", \"id\": [101, 105], \"weight\": 8}]. Где weight - важность события от 1 до 10. Язык: $lang. Новости:\n$sanitizedBatch"

            val response = try {
                withTimeout(120000L) { llm.sendPrompt(prompt) }
            } catch (e: GeminiException) {
                if (e.errorType == SummarizationErrorType.NO_NETWORK) throw e
                if (e.errorType == SummarizationErrorType.API_KEY_INVALID) throw e
                if (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED) throw e

                if (e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED) rateLimiter.penalize()

                throw RuntimeException("Trigger split", e)
            }

            val (jsonArray, isBroken) = safeParseJsonArrayLenient(response)

            val topics = mutableListOf<Topics>()
            val coveredIds = mutableSetOf<Long>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val idsArr = obj.getJSONArray("id")
                    val idsList = (0 until idsArr.length()).map { idsArr.getLong(it) }
                    val w = obj.optInt("weight", 5)
                    topics.add(Topics(obj.getString("title"), idsList, w))
                    coveredIds.addAll(idsList)
                } catch (_: Exception) { continue }
            }

            if (isBroken) {
                val victimMessages = batch.filter { !coveredIds.contains(it.id) }

                if (victimMessages.isNotEmpty()) {
                    if (victimMessages.size < batch.size) {
                        Log.w(TAG, "Extraction: ${victimMessages.size} victims lost due to broken JSON. Retrying them...")
                        val mid = victimMessages.size / 2
                        val left = adaptiveExtractTopics(victimMessages.subList(0, mid), max, lang)
                        val right = adaptiveExtractTopics(victimMessages.subList(mid, victimMessages.size), max, lang)
                        topics.addAll(left + right)
                    } else {
                        throw RuntimeException("JSON broken and no topics extracted")
                    }
                }
            }

            return topics

        } catch (e: Exception) {
            if (e is GeminiException) {
                if (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED ||
                    e.errorType == SummarizationErrorType.API_KEY_INVALID ||
                    e.errorType == SummarizationErrorType.NO_NETWORK) {
                    throw e
                }
            }

            if (batch.size <= 2) {
                Log.e(TAG, "Extraction failed for small batch: ${e.message}")
                return emptyList()
            }

            Log.w(TAG, "Batch extraction failed, splitting ${batch.size} items SEQUENTIALLY. Reason: ${e.message}")
            val mid = batch.size / 2

            val left = adaptiveExtractTopics(batch.subList(0, mid), max, lang)
            val right = adaptiveExtractTopics(batch.subList(mid, batch.size), max, lang)
            return left + right
        }
    }

    private suspend fun mergeAndFilterTopics(topics: List<Topics>, max: Int, lang: String) {
        val indexedInput = topics.mapIndexed { index, t ->
            JSONObject().apply {
                put("ix", index)
                put("t", t.title)
                put("w", t.weight)
            }
        }
        val jsonInput = JSONArray(indexedInput).toString()

        val prompt = """
            Задача: Объедини дублирующиеся новости.
            На входе: [{"ix": 0, "t": "Заголовок", "w": 5}].
            Верни JSON массив:
            [{"title": "Общий заголовок", "src": [0, 5], "weight": 9}]
            Где src - это список индексов (ix) исходных тем.
            Язык: $lang.
            Данные:
            $jsonInput
        """.trimIndent()

        try {
            rateLimiter.waitIfNeeded()
            val response = withTimeout(60000L) { llm.sendPrompt(prompt) }
            val (jsonArray, _) = safeParseJsonArrayLenient(response)

            val toAdd = mutableListOf<Topics>()
            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val sourcesIndices = obj.getJSONArray("src")

                    val mergedIds = mutableSetOf<Long>()
                    for (j in 0 until sourcesIndices.length()) {
                        val idx = sourcesIndices.getInt(j)
                        if (idx in topics.indices) {
                            topics[idx].ids?.let { mergedIds.addAll(it) }
                        }
                    }

                    if (mergedIds.isNotEmpty()) {
                        val w = obj.optInt("weight", 5)
                        toAdd.add(Topics(obj.getString("title"), mergedIds.toList(), w))
                    }
                } catch (_: Exception) {}
            }

            if (toAdd.isEmpty()) throw GeminiException(SummarizationErrorType.FILTER_FAILED)

            db.titlesTimeKill(0)
            toAdd.sortedByDescending { it.weight }
                .take(max)
                .forEach { t -> saveTopicToDb(t) }

        } catch (e: Exception) {
            Log.e(TAG, "Merge failed, falling back to originals", e)
            db.titlesTimeKill(0)
            topics.sortedByDescending { it.weight }
                .take(max)
                .forEach { t -> saveTopicToDb(t) }
        }
    }

    private fun saveTopicToDb(t: Topics) {
        db.addTitle(0, t.title, "<промежуточный текст>", "<промежуточный текст>", db.dbPack(*(t.ids?.map { it.toString() } ?: emptyList()).toTypedArray()))
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
        5. **Заголовок:** Проанализируй текущий заголовок "title". Если он плохо отражает суть текста "news_content", сгенерируй новый, более подходящий заголовок. Если старый хорош — оставь его.
        
        Формат вывода строго JSON (список объектов): 
        [{"id": 123, "title": "Заголовок темы (новый или старый)", "summary": "Текст саммари..."}]
        
        Ввод:
        $jsonInput
    """.trimIndent()

        return llm.sendPrompt(prompt)
    }

    private fun safeParseJsonArrayLenient(str: String): Pair<JSONArray, Boolean> {
        val clean = str.trim().removePrefix("```json").removeSuffix("```").trim()

        try {
            return Pair(JSONArray(clean), false)
        } catch (_: JSONException) {
            val lastObjEnd = clean.lastIndexOf('}')
            if (lastObjEnd != -1) {
                val repaired = clean.take(lastObjEnd + 1) + "]"
                try {
                    return Pair(JSONArray(repaired), true)
                } catch (_: Exception) {}
            }

            val rescued = JSONArray()
            var currIndex = 0
            var foundAny = false

            while (currIndex < clean.length) {
                val start = clean.indexOf("{", currIndex)
                if (start == -1) break

                var depth = 0
                var end = -1
                for (i in start until clean.length) {
                    if (clean[i] == '{') depth++
                    else if (clean[i] == '}') {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }

                if (end != -1) {
                    try {
                        val subStr = clean.substring(start, end + 1)
                        rescued.put(JSONObject(subStr))
                        foundAny = true
                    } catch (_: Exception) {}
                    currIndex = end + 1
                } else {
                    break
                }
            }

            if (foundAny) return Pair(rescued, true)

            throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Structure invalid and irrecoverable")
        }
    }
}