package com.rds.mews.core

import android.content.Context
import android.util.Log
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.GeminiException
import com.rds.mews.settings_manager.SummarizationErrorType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.pow

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
            throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Request serialization failed")
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
                            if (responseString.contains("RequestsPerDay", ignoreCase = true)) {
                                throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, "Daily quota exceeded")
                            }
                            if (responseString.contains("quota", ignoreCase = true)) {
                                val waitTime = extractRetryTime(responseString)
                                if (waitTime > 0) {
                                    delay(waitTime + 1000)
                                    attempt++
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
                        400 -> throw GeminiException(SummarizationErrorType.UNKNOWN_ERROR, "Content too large (HTTP 400)")
                        else -> {
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
                    if (msg.contains("quota", true)) {
                        if (msg.contains("RequestsPerDay", ignoreCase = true) || msg.contains("FreeTier", ignoreCase = true)) {
                            throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, msg)
                        }
                        val waitTime = extractRetryTime(msg)
                        if (waitTime > 0) {
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
                lastException = e
                attempt++
                if (attempt <= MAX_RETRIES) delay(1000L)
            }
        }
        val errorMsg = lastException?.message ?: "Unknown error"
        throw GeminiException(SummarizationErrorType.NO_NETWORK, errorMsg)
    }

    private fun extractRetryTime(message: String): Long {
        try {
            val pattern = Pattern.compile("retry in\\s+(\\d+(\\.\\d+)?)s")
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val seconds = matcher.group(1)?.toDoubleOrNull()
                if (seconds != null) return (seconds * 1000).toLong()
            }
        } catch (e: Exception) { }
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
    } catch (e: Exception) { false } finally { client.close() }
}

class NewsSummarizer(private val db: DbHelper, private val llm: LLMClient, private val context: Context) {

    data class Topics(
        val title: String,
        val ids: List<Long>?,
        val weight: Int = 0,
        val id: Long = 0,
        var snippet: String = ""
    )

    data class GroupedMessage(
        val representative: Message,
        val allIds: MutableList<Long>
    )

    private val SUMMARY_START_BATCH_SIZE = 10
    private val EXTRACT_START_BATCH_SIZE = 80
    private val DEDUP_THRESHOLD = 0.85
    private val MERGE_SNIPPET_THRESHOLD = 0.65

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
        filterTopics: Boolean = true
    ): SummarizationResult {
        try {
            Log.d(TAG, "Starting summarizeTopics. Filter enabled: $filterTopics")

            val unfinishedTitles = db.getTitles().filter { it.text == "<промежуточный текст>" && it.time.toInt() == 0 }

            // Если есть черновики, значит это повторная попытка (Retry Mode)
            val isRetryMode = unfinishedTitles.isNotEmpty()

            // Загружаем сообщения только для первого прогона
            val rawMessages = if (!isRetryMode) {
                val msgs = db.getMessages(messageSeconds).sortedBy { it.time }
                if (msgs.isEmpty()) {
                    safeReadyFunc(readyFunc)
                    return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
                }
                msgs
            } else {
                emptyList()
            }

            val currentLanguage = try {
                MewsRepository.currentLanguage.first() ?: "english"
            } catch (_: Exception) { "english" }

            baseProgress = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { 0f }

            // === ЭТАП 1: ЭКСТРАКЦИЯ И МЕРДЖИНГ (ТОЛЬКО ЕСЛИ НЕ RETRY) ===
            if (!isRetryMode) {
                try {
                    val extractionTime = System.currentTimeMillis()

                    val groupedMessages = deduplicateMessages(rawMessages)
                    Log.d(TAG, "Dedup: Input ${rawMessages.size} -> Unique Groups ${groupedMessages.size}")

                    processNewsInBatches(maxTopics, groupedMessages, currentLanguage, filterTopics)

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

            // === ЭТАП 2: ФИНАЛЬНАЯ СУММАРИЗАЦИЯ ===
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
                        val suitableMessages = topic.ids?.mapNotNull { id ->
                            rawMessages.find { it.id == id } ?: db.getMessage(id)
                        } ?: emptyList()

                        if (suitableMessages.isEmpty()) {
                            try { db.delTitle(name = topic.title) } catch (e: Exception) { Log.e(TAG, "Del empty title error", e) }
                            null
                        } else {
                            val uniqueTextsForSummary = deduplicateForSummaryPayload(suitableMessages)
                            val contentPayload = uniqueTextsForSummary.joinToString("\n") { "— ${it.mess}" }
                            Triple(topic, suitableMessages, contentPayload)
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
                                    val allSources = suitableMessages.map { it.source }.distinct()
                                    val allLinks = suitableMessages.map { it.id.toString() }.distinct()
                                    val newSourcesPack = db.dbPack(*allSources.toTypedArray())
                                    val newLinksPack = db.dbPack(*allLinks.toTypedArray())
                                    val newTimeVal = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis()

                                    if (newTitle != topic.title) {
                                        db.delTitle(name = topic.title)
                                        db.addTitle(newTimeVal, newTitle, summary, newSourcesPack, newLinksPack)
                                    } else {
                                        db.updateTitle(topic.id, topic.title, summary, newSourcesPack, newLinksPack, newTimeVal)
                                    }
                                    successCount++
                                } catch(e: Exception) { Log.e(TAG, "Save error", e) }
                            }
                        } else {
                            if (lastError == null) lastError = SummarizationErrorType.SUMMARIZE_TOPICS_FAILED
                        }

                        // Удаляем зомби ТОЛЬКО если это режим повторной попытки (isRetryMode)
                        if (isRetryMode) {
                            topicsData.forEach { (topic, _, _) ->
                                if (!successIds.contains(topic.id)) {
                                    try { db.delTitle(name = topic.title) } catch(_: Exception) {}
                                }
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
            safeReadyFunc(readyFunc)
            return SummarizationResult.Failure(e.errorType, e)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                safeReadyFunc(readyFunc)
                return SummarizationResult.Failure(SummarizationErrorType.JOB_CANCELLED)
            }
            safeReadyFunc(readyFunc)
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    private fun deduplicateMessages(raw: List<Message>): List<GroupedMessage> {
        val groups = mutableListOf<GroupedMessage>()
        raw.forEach { msg ->
            val cleanMsg = TextSanitizer.sanitize(msg.mess)
            val existingGroup = groups.find { group ->
                val cleanGroup = TextSanitizer.sanitize(group.representative.mess)
                TextComparator.areSimilar(cleanMsg, cleanGroup, DEDUP_THRESHOLD)
            }
            if (existingGroup != null) {
                existingGroup.allIds.add(msg.id)
            } else {
                groups.add(GroupedMessage(msg, mutableListOf(msg.id)))
            }
        }
        return groups
    }

    private fun deduplicateForSummaryPayload(messages: List<Message>): List<Message> {
        val unique = mutableListOf<Message>()
        messages.forEach { msg ->
            val cleanMsg = TextSanitizer.sanitize(msg.mess)
            val exists = unique.any {
                TextComparator.areSimilar(TextSanitizer.sanitize(it.mess), cleanMsg, 0.95)
            }
            if (!exists) unique.add(msg)
        }
        return unique
    }

    private suspend fun processNewsInBatches(maxTopics: Int, uniqueGroups: List<GroupedMessage>, lang: String, filterEnabled: Boolean) {
        totalItemsToProcess = uniqueGroups.size
        processedItemsCount = 0
        val availableSpace = 0.4f

        try { MewsRepository.setUpdatingState("extracting_topics") } catch (_: Exception) {}

        val allExtracted = mutableListOf<Topics>()
        val batches = uniqueGroups.chunked(EXTRACT_START_BATCH_SIZE)

        coroutineScope {
            batches.forEach { batch ->
                val batchLimit = (batch.size * 0.6).toInt().coerceAtLeast(5)
                val result = adaptiveExtractTopics(batch, batchLimit, lang)
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

        val finalTopicsCandidate: List<Topics> = if (filterEnabled) {
            try {
                val current = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { baseProgress + availableSpace }
                MewsRepository.setUpdatingState("filtering_topics")
                MewsRepository.setUpdatingProgress(current)

                val smartMerged = smartMergeTopics(allExtracted, lang)
                simpleAlgorithmicMerge(smartMerged)
            } catch (e: Exception) {
                Log.e(TAG, "Smart merge failed, falling back to algo merge", e)
                simpleAlgorithmicMerge(allExtracted)
            }
        } else {
            simpleAlgorithmicMerge(allExtracted)
        }

        db.titlesTimeKill(0)

        finalTopicsCandidate
            .sortedByDescending { it.weight }
            .take(maxTopics)
            .forEach { t -> saveTopicToDb(t) }
    }

    private fun simpleAlgorithmicMerge(rawTopics: List<Topics>): List<Topics> {
        val merged = mutableListOf<Topics>()

        rawTopics.sortedByDescending { it.weight }.forEach { candidate ->
            val existingIndex = merged.indexOfFirst { existing ->
                if (existing.snippet.isNotBlank() && candidate.snippet.isNotBlank()) {
                    TextComparator.areSimilar(existing.snippet, candidate.snippet, MERGE_SNIPPET_THRESHOLD)
                } else {
                    TextComparator.areSimilar(existing.title, candidate.title, 0.75)
                }
            }

            if (existingIndex != -1) {
                val existing = merged[existingIndex]
                val combinedIds = (existing.ids.orEmpty() + candidate.ids.orEmpty()).distinct()
                val maxWeight = max(existing.weight, candidate.weight)
                val bestSnippet = if (existing.snippet.length > candidate.snippet.length) existing.snippet else candidate.snippet

                merged[existingIndex] = existing.copy(ids = combinedIds, weight = maxWeight, snippet = bestSnippet)
            } else {
                merged.add(candidate)
            }
        }
        return merged
    }

    private suspend fun smartMergeTopics(topics: List<Topics>, lang: String): List<Topics> {
        if (topics.size > 150) {
            val mid = topics.size / 2
            return smartMergeTopics(topics.subList(0, mid), lang) + smartMergeTopics(topics.subList(mid, topics.size), lang)
        }

        val indexedInput = topics.mapIndexed { index, t ->
            JSONObject().apply {
                put("ix", index)
                put("t", t.title)
                put("ctx", t.snippet.take(400))
                put("w", t.weight)
            }
        }
        val jsonInput = JSONArray(indexedInput).toString()

        val prompt = """
            Задача: Объедини дублирующиеся новости в кластеры.
            Ввод: [{"ix": 0, "t": "...", "ctx": "...", "w": 5}].
            Верни JSON массив: [{"title": "Общий заголовок", "src": [0, 5], "weight": 9}]
            Где src - это список индексов (ix). Язык: $lang.
            Данные: $jsonInput
        """.trimIndent()

        rateLimiter.waitIfNeeded()
        val response = withTimeout(120000L) { llm.sendPrompt(prompt) }
        val (jsonArray, _) = safeParseJsonArrayLenient(response)

        val mergedResults = mutableListOf<Topics>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val sourcesIndices = obj.getJSONArray("src")
                val mergedIds = mutableSetOf<Long>()
                var bestSnippet = ""

                for (j in 0 until sourcesIndices.length()) {
                    val idx = sourcesIndices.getInt(j)
                    if (idx in topics.indices) {
                        val t = topics[idx]
                        t.ids?.let { mergedIds.addAll(it) }
                        if (t.snippet.length > bestSnippet.length) bestSnippet = t.snippet
                    }
                }

                if (mergedIds.isNotEmpty()) {
                    val w = obj.optInt("weight", 5)
                    mergedResults.add(Topics(obj.getString("title"), mergedIds.toList(), w, snippet = bestSnippet))
                }
            } catch (_: Exception) {}
        }

        if (mergedResults.isEmpty()) throw GeminiException(SummarizationErrorType.FILTER_FAILED)
        return mergedResults
    }

    private suspend fun adaptiveExtractTopics(batch: List<GroupedMessage>, maxLimit: Int, lang: String): List<Topics> {
        if (batch.isEmpty()) return emptyList()

        try {
            rateLimiter.waitIfNeeded()
            val sanitizedBatch = batch.joinToString("\n") { grp ->
                "• ${grp.representative.mess.replace("\"", "'").replace("`", "").take(400)} (id - ${grp.representative.id})"
            }

            val prompt = """
                Проанализируй новости. Сгруппируй их по инфоповодам.
                СТРОГИЙ ФИЛЬТР: Игнорируй рекламу, промокоды, erid.
                Верни JSON массив: [{"title": "Заголовок", "id": [101, 105], "weight": 8}].
                weight - важность (1-10). Язык: $lang.
                Новости: $sanitizedBatch
            """.trimIndent()

            val response = try {
                withTimeout(120000L) { llm.sendPrompt(prompt) }
            } catch (e: GeminiException) {
                if (e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED) rateLimiter.penalize()
                if (e.errorType == SummarizationErrorType.NO_NETWORK || e.errorType == SummarizationErrorType.QUOTA_EXCEEDED) throw e
                throw RuntimeException("Trigger split", e)
            }

            val (jsonArray, isBroken) = safeParseJsonArrayLenient(response)
            val topics = mutableListOf<Topics>()
            val coveredIds = mutableSetOf<Long>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val idsArr = obj.getJSONArray("id")
                    val extractedRepIds = (0 until idsArr.length()).map { idsArr.getLong(it) }

                    val fullIdList = mutableListOf<Long>()
                    extractedRepIds.forEach { repId ->
                        val group = batch.find { it.representative.id == repId }
                        if (group != null) {
                            fullIdList.addAll(group.allIds)
                            coveredIds.add(repId)
                        } else {
                            fullIdList.add(repId)
                        }
                    }

                    val w = obj.optInt("weight", 5)
                    val title = obj.getString("title")

                    val representativeMsg = batch.find { it.representative.id == extractedRepIds.firstOrNull() }?.representative
                    val snippet = if (representativeMsg != null) {
                        SnippetExtractor.extractSnippet(context, representativeMsg.mess)
                    } else {
                        title
                    }

                    topics.add(Topics(title, fullIdList, w, snippet = snippet))
                } catch (_: Exception) { continue }
            }

            if (isBroken) {
                val victimGroups = batch.filter { !coveredIds.contains(it.representative.id) }
                if (victimGroups.isNotEmpty() && victimGroups.size < batch.size) {
                    val mid = victimGroups.size / 2
                    val left = adaptiveExtractTopics(victimGroups.subList(0, mid), maxLimit, lang)
                    val right = adaptiveExtractTopics(victimGroups.subList(mid, victimGroups.size), maxLimit, lang)
                    topics.addAll(left + right)
                }
            }
            return topics

        } catch (e: Exception) {
            if (batch.size > 2 && e !is GeminiException) {
                val mid = batch.size / 2
                val left = adaptiveExtractTopics(batch.subList(0, mid), maxLimit / 2, lang)
                val right = adaptiveExtractTopics(batch.subList(mid, batch.size), maxLimit / 2, lang)
                return left + right
            }
            return emptyList()
        }
    }

    private fun saveTopicToDb(t: Topics) {
        val idsPack = db.dbPack(*(t.ids?.map { it.toString() } ?: emptyList()).toTypedArray())
        db.addTitle(0, t.title, "<промежуточный текст>", "<промежуточный текст>", idsPack)
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
                withTimeout(180000L) { sumTopicsBatch(llm, batch, bannedWords, lang) }
            } catch (e: GeminiException) {
                if (e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED) rateLimiter.penalize()
                if (e.errorType == SummarizationErrorType.CONTENT_BLOCKED) {
                    if (batch.size <= 1) {
                        try { db.delTitle(name = batch.first().first.title) } catch (_: Exception) {}
                        return emptyList()
                    }
                    throw RuntimeException("Trigger split", e)
                }
                throw e
            }

            val results = mutableListOf<Triple<Long, Pair<String, String>, Triple<Topics, List<Message>, String>>>()
            val (jsonArray, _) = safeParseJsonArrayLenient(response)
            val processedIds = mutableSetOf<Long>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    val summary = obj.getString("summary")
                    val newTitle = obj.optString("title").takeIf { it.isNotBlank() } ?: ""

                    val original = batch.find { it.first.ids?.contains(id) == true }
                    if (original != null && summary.isNotBlank()) {
                        results.add(Triple(id, Pair(summary, newTitle.ifBlank { original.first.title }), original))
                        processedIds.add(id)
                    }
                } catch (_: Exception) {}
            }

            val victims = batch.filter { item -> !processedIds.contains(item.first.ids?.firstOrNull() ?: 0L) }
            if (victims.isNotEmpty() && victims.size < batch.size) {
                val mid = victims.size / 2
                results.addAll(adaptiveSummarizeBatch(victims.subList(0, mid), bannedWords, lang))
                results.addAll(adaptiveSummarizeBatch(victims.subList(mid, victims.size), bannedWords, lang))
            }
            return results
        } catch (e: Exception) {
            if (e is GeminiException && (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED || e.errorType == SummarizationErrorType.API_KEY_INVALID)) throw e
            if (batch.size > 1) {
                val mid = batch.size / 2
                return adaptiveSummarizeBatch(batch.subList(0, mid), bannedWords, lang) + adaptiveSummarizeBatch(batch.subList(mid, batch.size), bannedWords, lang)
            }
            return emptyList()
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
        Ты — редактор новостей. Напиши живые саммари.
        1. Объем: до 500 слов (лонгрид), до 300 (заметка).
        2. Стиль: Smart Casual.
        3. Фильтр: Если про '$banned', summary = "".
        4. Язык: $lang.
         JSON: [{"id": 123, "title": "...", "summary": "..."}]
        Ввод: $jsonInput
        """.trimIndent()
        return llm.sendPrompt(prompt)
    }

    private fun safeParseJsonArrayLenient(str: String): Pair<JSONArray, Boolean> {
        val clean = str.trim().removePrefix("```json").removeSuffix("```").trim()
        try { return Pair(JSONArray(clean), false) } catch (_: JSONException) {
            val lastObjEnd = clean.lastIndexOf(']')
            val start = clean.indexOf('[')
            if (start != -1 && lastObjEnd != -1 && lastObjEnd > start) {
                try { return Pair(JSONArray(clean.substring(start, lastObjEnd + 1)), true) } catch (_: Exception) {}
            }
            return Pair(JSONArray(), true)
        }
    }
}