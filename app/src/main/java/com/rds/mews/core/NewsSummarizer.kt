package com.rds.mews.core

import android.util.Log
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.TitleStatus
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.GeminiException
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.text_filters.TextComparator
import com.rds.mews.text_filters.TextSanitizer
import com.rds.mews.text_filters.TokenEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import kotlin.math.max
import kotlin.math.pow

class SmartRateLimiter(private val minIntervalMs: Long = 4500L) {
    private val mutex = Mutex()
    private var nextAllowedTime = 0L

    suspend fun waitIfNeeded() {
        mutex.withLock {
            val now = System.nanoTime() / 1_000_000
            if (now < nextAllowedTime) {
                delay(nextAllowedTime - now)
            }
            nextAllowedTime = (System.nanoTime() / 1_000_000) + minIntervalMs
        }
    }

    suspend fun penalize() {
        mutex.withLock {
            val now = System.nanoTime() / 1_000_000
            val penaltyTarget = now + 15000L
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
    private var currentModelApiName = MODEL.ifBlank { MewsRepository.defaultModel.apiModelName }
    private var currentUrl = URL_TEMPLATE.replace("%MODEL%", currentModelApiName)
    private val httpClient = SharedHttpClient.createInstance(MewsRepository.PROXY_ADDRESS, MewsRepository.SERVER_KEY, enableProxy = enableProxy)
    private val jsonParser = SharedHttpClient.jsonParser
    private val MAX_RETRIES = 3
    private val TAG = "LLMClient"

    private fun switchToFallbackModel(): Boolean {
        val models = MewsRepository.geminiModelsList.filter { !it.apiModelName.lowercase().contains("pro") }
        val currentIndex = models.indexOfFirst { it.apiModelName == currentModelApiName }

        if (currentIndex > 0) {
            currentModelApiName = models[currentIndex - 1].apiModelName
            currentUrl = URL_TEMPLATE.replace("%MODEL%", currentModelApiName)
            return true
        } else if (currentIndex == -1) {
            currentModelApiName = models.first().apiModelName
            currentUrl = URL_TEMPLATE.replace("%MODEL%", currentModelApiName)
            return true
        }
        return false
    }

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
                    url = currentUrl,
                    body = requestBodyString,
                    headers = mapOf("x-goog-api-key" to apiKey, "Content-Type" to "application/json")
                )

                val responseString = response.body
                val responseStatus = response.status

                if (responseStatus != 200) {
                    when (responseStatus) {
                        429 -> {
                            handle429Error(responseString, attempt)
                            attempt++
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

                    if (msg.contains("Requests per day", true) || msg.contains("RequestsPerDay", true) || msg.contains("FreeTier", true)) {
                        throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, msg)
                    }

                    if (geminiResponse.error.code == 429 || msg.contains("quota", true)) {
                        handle429Error(msg, attempt)
                        attempt++
                        continue
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
                    val reason = geminiResponse.promptFeedback.blockReason
                    throw GeminiException(SummarizationErrorType.CONTENT_BLOCKED, reason)
                }

                val resultText = geminiResponse.candidates?.takeIf { it.isNotEmpty() }
                    ?.flatMap { it.content?.parts ?: emptyList() }
                    ?.joinToString("\n") { it.text }

                if (resultText.isNullOrBlank()) {
                    throw GeminiException(SummarizationErrorType.EMPTY_ANSWER)
                }
                return resultText

            } catch (e: GeminiException) {
                if (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED) {
                    if (switchToFallbackModel()) {
                        attempt = 0
                        continue
                    }
                }
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

    private suspend fun handle429Error(responseString: String, attempt: Int) {
        if (responseString.contains("Requests per day", ignoreCase = true) ||
            responseString.contains("RequestsPerDay", ignoreCase = true) ||
            responseString.contains("FreeTier", ignoreCase = true)) {
            throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, "Daily quota exceeded")
        }

        val waitTime = extractRetryTime(responseString)

        if (waitTime > 0) {
            delay(waitTime + 1000L)
        } else {
            val baseDelay = 15000L
            val exponentialMultiplier = 2.0.pow(attempt.toDouble()).toLong()
            val calcDelay = baseDelay * exponentialMultiplier
            delay(calcDelay)
        }

        if (attempt > MAX_RETRIES + 1) {
            throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, "Daily quota limit reached (empirical timeout)")
        }
    }

    private fun extractRetryTime(message: String): Long {
        val regex = """(\d+(?:\.\d+)?)s""".toRegex()
        val match = regex.find(message)
        return match?.groupValues?.get(1)?.let { numStr ->
            try {
                val seconds = numStr.toDouble()
                (seconds * 1000).toLong()
            } catch (_: Exception) {
                0L
            }
        } ?: 0L
    }

    fun safeParseJsonArray(str: String): Pair<JSONArray, Boolean> {
        val len = str.length
        var cursor = 0

        while (cursor < len) {
            val start = str.indexOf('[', cursor)
            if (start == -1) break

            val end = findMatchingCloseBracket(str, start)

            if (end != -1) {
                val candidate = str.substring(start, end + 1)
                try {
                    val json = JSONArray(candidate)
                    return Pair(json, candidate.length != str.length)
                } catch (_: JSONException) {
                }
            }

            cursor = start + 1
        }

        return Pair(JSONArray(), true)
    }

    private fun findMatchingCloseBracket(str: String, start: Int): Int {
        var balance = 0
        var inString = false
        var isEscaped = false

        for (i in start until str.length) {
            val c = str[i]
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '[') {
                    balance++
                } else if (c == ']') {
                    balance--
                    if (balance == 0) return i
                }
            }
        }
        return -1
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
    return withContext(Dispatchers.IO) {
        val client = SharedHttpClient.createInstance(proxyIp, proxyKey, enableProxy)
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val getUrl = client.get(url)
            println(getUrl.body)
            getUrl.status == 200
        } catch (e: Exception) {
            println("cause: ${e.cause}, message: ${e.message}")
            false
        } finally {
            client.close()
        }
    }
}

class NewsSummarizer(private val llm: LLMClient) {
    data class Topics(
        val title: String,
        val ids: List<Long>?,
        val weight: Int = 0,
        val id: Long = 0,
        val status: Int = 0,
        val keywords: List<String> = emptyList()
    )
    data class GroupedMessage(
        val representative: Message,
        val allIds: MutableList<Long>
    )

    private val BATCH_CHAR_LIMIT = 165000
    private val MAX_NEWS_LIMIT = 110
    private val SINGLE_NEWS_CHAR_LIMIT = 3000
    private val TAG = "NewsSummarizer"

    private val rateLimiter = SmartRateLimiter(minIntervalMs = 4500L)

    private var totalItemsToProcess = 0
    private var processedItemsCount = 0
    private var baseProgress = 0f

    private fun safeReadyFunc(func: () -> Unit) {
        try { func() } catch (e: Exception) {
            Log.e(TAG, "readyFunc execution failed", e)
        }
    }

    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 36000,
        readyFunc: () -> Unit,
        filterTopics: Boolean = true
    ): SummarizationResult {
        MewsRepository.setUpdatingState("extracting_topics")
        try {
            val isRetryMode = MewsRepository.getTitlesWithStatus(TitleStatus.PROCESSING.statusId).isNotEmpty()
            val bannedWords = try { MewsRepository.bannedNewsFlow.value.joinToString("'; '") } catch (_: Exception) { "" }

            val rawMessages = if (!isRetryMode) {
                val msgs = MewsRepository.getUniqueMessagesList(messageSeconds)

                val processedMessageIds = try {
                    MewsRepository.getProcessedMessageIds(System.currentTimeMillis() - (72 * 60 * 60 * 1000L))
                } catch (_: Exception) {
                    emptySet()
                }

                val finalMsgs = msgs.filter { it.id !in processedMessageIds }

                if (finalMsgs.isEmpty()) {
                    safeReadyFunc(readyFunc)
                    return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
                }
                finalMsgs.map { it.copy(originalText = "") }
            } else { emptyList() }

            val currentLanguage = try {
                MewsRepository.currentLanguage.first() ?: "english"
            } catch (_: Exception) { "english" }

            baseProgress = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { 0f }

            if (!isRetryMode) {
                try {
                    val extractionTime = System.currentTimeMillis()
                    val sortedMessages = rawMessages.sortedBy { it.time }
                    val startTime = sortedMessages.firstOrNull()?.time ?: 0L

                    val totalDuration = sortedMessages.last().time - startTime
                    val windowDuration = max(totalDuration / 3, 3600_000L)

                    val MIN_NEWS_PER_CHUNK = 20

                    val buffer = mutableListOf<Message>()
                    var currentWindowEnd = startTime + windowDuration
                    val globalApprovedTopics = mutableListOf<Topics>()

                    totalItemsToProcess = rawMessages.size
                    processedItemsCount = 0
                    val availableSpace = 0.4f

                    MewsRepository.setUpdatingState("extracting_topics")

                    for (msg in sortedMessages) {
                        while (msg.time > currentWindowEnd) {
                            if (buffer.size >= MIN_NEWS_PER_CHUNK) {
                                processNewsBuffer(buffer, currentLanguage, filterTopics, bannedWords, globalApprovedTopics, availableSpace)
                                buffer.clear()
                            }
                            currentWindowEnd += windowDuration
                        }
                        buffer.add(msg)
                    }

                    if (buffer.isNotEmpty()) {
                        processNewsBuffer(buffer, currentLanguage, filterTopics, bannedWords, globalApprovedTopics, availableSpace)
                        buffer.clear()
                    }

                    val mergedTopics = if (filterTopics && globalApprovedTopics.isNotEmpty()) {
                        MewsRepository.setUpdatingState("filtering_topics")
                        try {
                            smartMergeTopics(globalApprovedTopics, currentLanguage, 0, bannedWords)
                        } catch (e: Exception) {
                            Log.w(TAG, "Smart merge failed, falling back to algorithmic merge", e)
                            globalApprovedTopics
                        }
                    } else {
                        globalApprovedTopics
                    }

                    val msgTimeMap = rawMessages.associate { it.id to it.time }
                    val topicsWithTime = mergedTopics.map { t ->
                        val tMin = t.ids?.mapNotNull { msgTimeMap[it] }?.minOrNull() ?: startTime
                        t to tMin
                    }

                    val windowMap = topicsWithTime.groupBy { (it.second - startTime) / windowDuration }
                    val finalSelected = mutableListOf<Topics>()
                    val pool = mutableListOf<Topics>()

                    windowMap.values.forEach { windowItems ->
                        val best = windowItems.maxByOrNull { it.first.weight }?.first
                        if (best != null) {
                            finalSelected.add(best)
                            windowItems.forEach { if (it.first != best) pool.add(it.first) }
                        }
                    }

                    val needed = maxTopics - finalSelected.size
                    if (needed > 0) {
                        finalSelected.addAll(pool.sortedByDescending { it.weight }.take(needed))
                    }

                    val finalTopics = finalSelected.sortedByDescending { it.weight }.take(maxTopics)
                    finalTopics.forEach { saveTopicToDb(it) }

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

            val titlesToSummarize = MewsRepository.getTitlesWithStatus(TitleStatus.PROCESSING.statusId)
            if (titlesToSummarize.isEmpty()) {
                safeReadyFunc(readyFunc)
                return SummarizationResult.Success
            }

            totalItemsToProcess = titlesToSummarize.size
            processedItemsCount = 0
            val batches = titlesToSummarize.chunked(10)
            val availableProgressSpace = 0.95f - baseProgress
            var successCount = 0
            var lastError: SummarizationErrorType? = null

            val summarizedTopicsData = mutableListOf<Triple<Long, Long, Pair<String, String>>>()

            coroutineScope {
                batches.forEachIndexed { index, batch ->
                    MewsRepository.setUpdatingState("summarizing_topics")

                    val topicsData = batch.mapNotNull { topic ->
                        val messageIds = topic.ids ?: emptyList()
                        val cachedMessages = messageIds.mapNotNull { id -> rawMessages.find { it.id == id } }
                        val missingIds = messageIds - cachedMessages.map { it.id }.toSet()

                        val fetchedMessages = if (missingIds.isNotEmpty()) {
                            MewsRepository.getMessages(missingIds.joinToString(", "))
                        } else {
                            emptyList()
                        } ?: emptyList()

                        val suitableMessages = cachedMessages + fetchedMessages

                        if (suitableMessages.isEmpty()) {
                            MewsRepository.deleteTitleById(topic.id)
                            null
                        } else {
                            val uniqueTextsForSummary = deduplicateForSummaryPayload(suitableMessages)
                            val contentPayload = uniqueTextsForSummary.joinToString("\n") { "— ${it.cleanText.take(SINGLE_NEWS_CHAR_LIMIT)}" }
                            Triple(topic, suitableMessages, contentPayload)
                        }
                    }

                    if (topicsData.isNotEmpty()) {
                        try {
                            val results = adaptiveSummarizeBatch(topicsData, bannedWords, currentLanguage, 0)
                            if (results.isNotEmpty()) {
                                for ((_, resultData, originalData) in results) {
                                    try {
                                        val (summary, newTitle) = resultData
                                        val (topic, suitableMessages, _) = originalData

                                        if (summary.isBlank() || summary == "REJECTED") {
                                            MewsRepository.deleteTitleById(topic.id)
                                            continue
                                        }

                                        val newTimeVal = suitableMessages.minOfOrNull { it.time } ?: System.currentTimeMillis()

                                        MewsRepository.updateTitle(
                                            id = topic.id,
                                            newTimeVal = newTimeVal,
                                            newTitle = newTitle,
                                            summary = summary,
                                            parentId = null
                                        )

                                        summarizedTopicsData.add(Triple(topic.id, newTimeVal, Pair(newTitle, summary)))
                                        successCount++
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch(_: Exception) {
                                    }
                                }
                            } else {
                                if (lastError == null) lastError = SummarizationErrorType.SUMMARIZE_TOPICS_FAILED
                            }

                            if (isRetryMode) {
                                val successIds = results.map { (_, _, originalData) -> originalData.first.id }.toSet()
                                for ((topic, _, _) in topicsData) {
                                    if (topic.id !in successIds) {
                                        try {
                                            MewsRepository.deleteTitleById(topic.id)
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch(_: Exception) {
                                        }
                                    }
                                }
                            }

                        } catch (e: GeminiException) {
                            if (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED ||
                                e.errorType == SummarizationErrorType.NO_NETWORK ||
                                e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED) {
                                lastError = e.errorType
                            } else {
                                for ((t, _, _) in topicsData) {
                                    try { MewsRepository.deleteTitleById(t.id) }
                                    catch (ex: kotlinx.coroutines.CancellationException) { throw ex }
                                    catch(_: Exception) {}
                                }
                            }
                        }
                    }
                    processedItemsCount += batch.size
                    try {
                        val relativeProgress = processedItemsCount.toFloat() / totalItemsToProcess.toFloat()
                        val newProgress = baseProgress + (relativeProgress * availableProgressSpace)
                        MewsRepository.setUpdatingProgress(newProgress.coerceIn(0f, 0.95f))
                    } catch (_: Exception) {
                    }
                }
            }

            if (successCount > 0) {
                try {
                    val history = MewsRepository.getRecentTitlesForStorylines(System.currentTimeMillis() - (72 * 60 * 60 * 1000L)).toMutableList()
                    val sortedSummarized = summarizedTopicsData.sortedBy { it.second }
                    val minKeywordMatches = 2

                    for ((topicId, topicTime, titleAndSummary) in sortedSummarized) {
                        val (newTitle, summary) = titleAndSummary
                        val currentTopic = history.find { it.id == topicId }

                        if (currentTopic != null) {
                            var bestMatchId: Long? = null
                            var maxScore = 0.0

                            for (historyItem in history) {
                                if (historyItem.id == topicId) continue
                                if (historyItem.eventTime > topicTime || (historyItem.eventTime == topicTime && historyItem.id >= topicId)) continue

                                var keywordMatches = 0
                                println(
                                    "current keywords: ${currentTopic.keywords.joinToString(", ")},\nhistory keywords: ${
                                        historyItem.keywords.joinToString(
                                            ", "
                                        )
                                    }"
                                )
                                for (tk in currentTopic.keywords) {
                                    for (hk in historyItem.keywords) {
                                        if (tk.equals(hk, ignoreCase = true) || TextComparator.areSimilar(tk.lowercase(), hk.lowercase(), 0.85)) {
                                            keywordMatches++
                                            break
                                        }
                                    }
                                }

                                if (keywordMatches > minKeywordMatches) {
                                    bestMatchId = historyItem.id
                                    break
                                }

                                val match45Title = TextComparator.areSimilar(newTitle, historyItem.title, 0.45)
                                val match45Summary = TextComparator.areSimilar(summary, historyItem.summary, 0.45)

                                if (keywordMatches >= minKeywordMatches && (match45Title || match45Summary)) {
                                    var currentScore = keywordMatches.toDouble()
                                    if (match45Title) currentScore += 0.5
                                    if (match45Summary) currentScore += 0.5

                                    if (currentScore > maxScore) {
                                        maxScore = currentScore
                                        bestMatchId = historyItem.id
                                    }
                                }
                            }

                            if (bestMatchId != null) {
                                history.removeIf { it.id == bestMatchId }
                                MewsRepository.updateTitle(
                                    id = topicId,
                                    newTimeVal = topicTime,
                                    newTitle = newTitle,
                                    summary = summary,
                                    parentId = bestMatchId
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "Storylines matching failed", e)
                    } else throw e
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

    private fun createBatches(groups: List<GroupedMessage>): List<List<GroupedMessage>> {
        val batches = mutableListOf<List<GroupedMessage>>()
        var currentBatch = mutableListOf<GroupedMessage>()
        var currentBatchTokens = 0

        for (group in groups) {
            val msgText = group.representative.cleanText.take(SINGLE_NEWS_CHAR_LIMIT)
            val msgCharLen = msgText.length

            val msgTokens = try {
                TokenEstimator.estimate(MewsRepository.getAppContext(), msgText)
            } catch (_: Exception) {
                msgCharLen / 3
            }

            val isTokenLimitExceeded = currentBatchTokens + msgTokens > BATCH_CHAR_LIMIT
            val isCountLimitExceeded = currentBatch.size >= MAX_NEWS_LIMIT

            if ((isTokenLimitExceeded || isCountLimitExceeded) && currentBatch.isNotEmpty()) {
                batches.add(currentBatch)
                currentBatch = mutableListOf()
                currentBatchTokens = 0
            }

            currentBatch.add(group)
            currentBatchTokens += msgTokens
        }

        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }
        return batches
    }

    private fun deduplicateForSummaryPayload(messages: List<Message>): List<Message> {
        val unique = mutableListOf<Message>()
        messages.forEach { msg ->
            val cleanMsg = TextSanitizer.sanitize(msg.cleanText)
            val exists = unique.any { TextComparator.areSimilar(TextSanitizer.sanitize(it.cleanText), cleanMsg, 0.8) }
            if (!exists) unique.add(msg)
        }
        return unique
    }

    private suspend fun processNewsBuffer(
        buffer: List<Message>,
        lang: String,
        filterEnabled: Boolean,
        bannedNews: String,
        globalApprovedTopics: MutableList<Topics>,
        availableSpace: Float
    ) {
        val uniqueGroups = buffer.map { GroupedMessage(it, mutableListOf(it.id)) }
        val batches = createBatches(uniqueGroups)
        val allExtracted = mutableListOf<Topics>()
        var criticalError: GeminiException? = null

        Log.d(TAG, "Extraction: Splitting into ${batches.size} sequential batches.")

        coroutineScope {
            for ((index, batch) in batches.withIndex()) {
                Log.d(TAG, "Extraction: Processing batch ${index + 1}/${batches.size} (Size: ${batch.size})")
                try {
                    val batchLimit = batch.size
                    val result = adaptiveExtractTopics(batch, batchLimit, lang, 2, bannedNews)
                    allExtracted.addAll(result)

                    processedItemsCount += batch.size
                    try {
                        val relativeProgress = processedItemsCount.toFloat() / totalItemsToProcess.toFloat()
                        val newProgress = baseProgress + (relativeProgress * availableSpace)
                        MewsRepository.setUpdatingProgress(newProgress)
                    } catch(_: Exception) {}

                } catch (e: GeminiException) {
                    criticalError = e
                    Log.e(TAG, "Extraction error during batch ${index + 1}: ${e.errorType}")
                    if (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED ||
                        e.errorType == SummarizationErrorType.NO_NETWORK ||
                        e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED) {
                        break
                    }
                }
            }
        }

        if (allExtracted.isEmpty() && criticalError != null) throw criticalError
        Log.d(TAG, "Extraction complete. Total topics raw: ${allExtracted.size}")
        mergeIntoGlobal(allExtracted, globalApprovedTopics)
    }

    private fun mergeIntoGlobal(newTopics: List<Topics>, globalCache: MutableList<Topics>) {
        val keywordMatches = 2
        newTopics.forEach { candidate ->
            val existingIndex = globalCache.indexOfFirst { existing ->
                var isKeywordMatch = false
                if (existing.keywords.size >= keywordMatches && candidate.keywords.size >= keywordMatches) {
                    var matchCount = 0
                    for (k1 in existing.keywords) {
                        for (k2 in candidate.keywords) {
                            if (TextComparator.areSimilar(k1.lowercase(), k2.lowercase(), 0.85)) {
                                matchCount++
                                break
                            }
                        }
                    }
                    if (matchCount >= keywordMatches) isKeywordMatch = true
                }
                isKeywordMatch || TextComparator.areSimilar(existing.title, candidate.title, 0.85)
            }

            if (existingIndex != -1) {
                val existing = globalCache[existingIndex]
                val combinedIds = (existing.ids.orEmpty() + candidate.ids.orEmpty()).distinct()
                val maxWeight = max(existing.weight, candidate.weight)
                val combinedKeywords = (existing.keywords + candidate.keywords)
                    .distinctBy { it.lowercase() }
                    .take(8)

                globalCache[existingIndex] = existing.copy(
                    ids = combinedIds,
                    weight = maxWeight,
                    keywords = combinedKeywords
                )
            } else {
                globalCache.add(candidate)
            }
        }
    }

    private suspend fun smartMergeTopics(topics: List<Topics>, lang: String, depth: Int, banned: String): List<Topics> {
        if (topics.isEmpty()) return emptyList()
        if (topics.size > 150 && depth < 3) {
            val mid = topics.size / 2
            return smartMergeTopics(topics.subList(0, mid), lang, depth + 1, banned) +
                    smartMergeTopics(topics.subList(mid, topics.size), lang, depth + 1, banned)
        }

        val indexedInput = topics.mapIndexed { index, t ->
            JSONObject().apply {
                put("ix", index)
                put("t", t.title)
                put("kw", t.keywords.joinToString(", "))
                put("w", t.weight)
            }
        }
        val jsonInput = JSONArray(indexedInput).toString()

        val prompt = """
            Задача: Объедини дублирующиеся новости в кластеры.
            Важно:
            1. УЗКОЕ СЛИЯНИЕ СЮЖЕТОВ: Объединяй дубликаты и новости, описывающие один и тот же инцидент или принадлежащие к одному узкому развивающемуся сюжету. СТРОГИЙ ЗАПРЕТ: Не объединяй независимые события только на основе общей локации или категории.
            2. Если группа новостей относится к запрещенным темам ('$banned') — НЕ включай её в итоговый список. Удали.
            3. Формируй заголовки в стиле Smart Casual.
            Ввод: [{"ix": 0, "t": "...", "kw": "...", "w": 5}].
            Верни ТОЛЬКО JSON массив:
            [{"title": "Общий заголовок", "src": [0, 5], "weight": 9}]
            Где src - это список индексов (ix). Язык: $lang.
            Данные: $jsonInput
        """.trimIndent()

        rateLimiter.waitIfNeeded()
        val response = withTimeout(120000L) { llm.sendPrompt(prompt) }
        val (jsonArray, _) = llm.safeParseJsonArray(response)

        val mergedResults = mutableListOf<Topics>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                val sourcesIndices = obj.getJSONArray("src")
                val mergedIds = mutableSetOf<Long>()
                val mergedKeywords = mutableSetOf<String>()

                for (j in 0 until sourcesIndices.length()) {
                    val idx = sourcesIndices.getInt(j)
                    if (idx in topics.indices) {
                        val t = topics[idx]
                        t.ids?.let { mergedIds.addAll(it) }
                        mergedKeywords.addAll(t.keywords)
                    }
                }

                if (mergedIds.isNotEmpty()) {
                    val w = obj.optInt("weight", 5)
                    mergedResults.add(Topics(obj.getString("title"), mergedIds.toList(), w, keywords = mergedKeywords.take(8).toList()))
                }
            } catch (_: Exception) {
            }
        }

        if (mergedResults.isEmpty()) throw GeminiException(SummarizationErrorType.FILTER_FAILED)
        return mergedResults
    }

    private suspend fun adaptiveExtractTopics(batch: List<GroupedMessage>, maxLimit: Int, lang: String, depth: Int, banned: String): List<Topics> {
        if (batch.isEmpty()) return emptyList()

        try {
            rateLimiter.waitIfNeeded()
            val sanitizedBatch = batch.joinToString("\n") { grp ->
                "• ${grp.representative.cleanText.replace("\"", "'").replace("`", "").take(SINGLE_NEWS_CHAR_LIMIT)} (id - ${grp.representative.id})"
            }

            val prompt = """
                Проанализируй новости и выдели ОТДЕЛЬНЫЕ новостные события.
                ЛИМИТЫ (СТРОГО): Максимальное количество тем: $maxLimit. Если событий больше — выбери самые резонансные и значимые. Мелкие, локальные или малозначительные новости ИГНОРИРУЙ.
                Группируй новости по Сюжетным Линиям:
                1. Цепочка событий (ОСТАВЛЯТЬ ВМЕСТЕ): Например, событие + реакция + последствия = ОДНА тема.
                2. Сюжетная кластеризация: Объединяй события в одну тему ТОЛЬКО при наличии прямой причинно-следственной связи, игнорируя территориальные совпадения. Строгий запрет на общие рубрики.
                ФИЛЬТРАЦИЯ (СТРОГО): Игнорируй: рекламу, розыгрыши призов, итоги конкурсов, спам, а также темы: '$banned'.
                ИНСТРУКЦИИ ЗАГОЛОВКОВ: Пиши в стиле Smart Casual: живые, человеческие заголовки без канцелярита.
                Верни JSON массив: [{"title": "Заголовок", "id": [101, 105], "keywords": ["тег1", "тег2"], "weight": 8}].
                weight - важность (1-10). keywords - 3-5 ключевых слов. Язык: $lang.
                Новости: $sanitizedBatch
            """.trimIndent()

            val response = try {
                withTimeout(120000L) { llm.sendPrompt(prompt) }
            } catch (e: GeminiException) {
                if (e.errorType == SummarizationErrorType.NO_NETWORK || e.errorType == SummarizationErrorType.QUOTA_EXCEEDED || e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED) {
                    if (e.errorType == SummarizationErrorType.RATE_LIMIT_EXCEEDED) rateLimiter.penalize()
                    throw e
                }
                throw RuntimeException("Trigger split", e)
            }

            val (jsonArray, isBroken) = llm.safeParseJsonArray(response)
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

                    val keywordsList = mutableListOf<String>()
                    val keywordsJson = obj.optJSONArray("keywords")
                    if (keywordsJson != null) {
                        for (k in 0 until keywordsJson.length()) {
                            keywordsList.add(keywordsJson.getString(k))
                        }
                    }

                    topics.add(Topics(title, fullIdList, w, keywords = keywordsList))
                } catch (_: Exception) {
                    continue
                }
            }

            if (isBroken && depth < 3) {
                val victimGroups = batch.filter { !coveredIds.contains(it.representative.id) }
                if (victimGroups.isNotEmpty() && victimGroups.size < batch.size) {
                    Log.w(TAG, "Split triggered: Extracted JSON broken or incomplete, processing ${victimGroups.size} victims.")
                    val mid = victimGroups.size / 2
                    val left = adaptiveExtractTopics(victimGroups.subList(0, mid), maxLimit, lang, depth + 1, banned)
                    val right = adaptiveExtractTopics(victimGroups.subList(mid, victimGroups.size), maxLimit, lang, depth + 1, banned)
                    topics.addAll(left + right)
                }
            }
            return topics

        } catch (e: Exception) {
            if (batch.size > 2 && e !is GeminiException && depth < 3) {
                Log.w(TAG, "Exception trapped in extraction. Force splitting batch. Depth: $depth", e)
                val mid = batch.size / 2
                val left = adaptiveExtractTopics(batch.subList(0, mid), maxLimit / 2, lang, depth + 1, banned)
                val right = adaptiveExtractTopics(batch.subList(mid, batch.size), maxLimit / 2, lang, depth + 1, banned)
                return left + right
            }
            Log.e(TAG, "Batch unrecoverable at depth $depth", e)
            return emptyList()
        }
    }

    private suspend fun saveTopicToDb(t: Topics) {
        val ids = t.ids ?: emptyList()
        MewsRepository.addTitle(
            newTimeVal = 0,
            newTitle = t.title,
            summary = "",
            messageIds = ids,
            status = TitleStatus.PROCESSING,
            keywords = t.keywords
        )
    }

    private suspend fun adaptiveSummarizeBatch(
        batch: List<Triple<Topics, List<Message>, String>>,
        bannedWords: String,
        lang: String,
        depth: Int
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
                        try { MewsRepository.deleteTitleById(batch.first().first.id) } catch (ex: Exception) { Log.e(TAG, "Delete block failed", ex) }
                        return emptyList()
                    }
                    Log.w(TAG, "Content blocked. Splitting summary batch.", e)
                    throw RuntimeException("Trigger split", e)
                }
                throw e
            }

            val results = mutableListOf<Triple<Long, Pair<String, String>, Triple<Topics, List<Message>, String>>>()
            val (jsonArray, _) = llm.safeParseJsonArray(response)
            val processedIds = mutableSetOf<Long>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    val summary = obj.getString("summary")
                    val newTitle = obj.optString("title").takeIf { it.isNotBlank() } ?: ""

                    val original = batch.find { it.first.ids?.contains(id) == true }

                    if (summary.trim() == "REJECTED" || summary.trim().length <= 15) {
                        if (original != null) {
                            MewsRepository.deleteTitleById(original.first.id)
                        }
                        continue
                    }

                    if (original != null) {
                        results.add(Triple(id, Pair(summary, newTitle.ifBlank { original.first.title }), original))
                        processedIds.add(id)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error mapping summary JSON result back to original data", e)
                }
            }

            val victims = batch.filter { item -> !processedIds.contains(item.first.ids?.firstOrNull() ?: 0L) }
            if (victims.isNotEmpty() && victims.size < batch.size && depth < 3) {
                Log.w(TAG, "Missing topics in summary response. Splitting victims. Depth: $depth")
                val mid = victims.size / 2
                results.addAll(adaptiveSummarizeBatch(victims.subList(0, mid), bannedWords, lang, depth + 1))
                results.addAll(adaptiveSummarizeBatch(victims.subList(mid, victims.size), bannedWords, lang, depth + 1))
            }
            return results
        } catch (e: Exception) {
            if (e is GeminiException && (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED || e.errorType == SummarizationErrorType.API_KEY_INVALID)) throw e
            if (batch.size > 1 && depth < 3) {
                Log.w(TAG, "Unknown exception in summary. Splitting batch. Depth: $depth", e)
                val mid = batch.size / 2
                return adaptiveSummarizeBatch(batch.subList(0, mid), bannedWords, lang, depth + 1) +
                        adaptiveSummarizeBatch(batch.subList(mid, batch.size), bannedWords, lang, depth + 1)
            }
            Log.e(TAG, "Summary batch failed completely at depth $depth", e)
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
        Ты — аналитик и обозреватель. Твоя цель — написать подробный лонгрид или развернутую статью, объем которой прямо пропорционален количеству входных данных. Запрещено искусственно сжимать текст.
        Инструкции:
        1. СТИЛЬ: Smart Casual с упором на литературность и читаемость. Строгая новостная объективность без эмоциональных оценок.
        2. ДЕТАЛИЗАЦИЯ И ФАКТОЛОГИЯ: Пиши максимально развернуто. Твоя задача не сделать краткую выжимку, а литературно объединить все факты. Перенеси из источника 100% конкретных цифр, имен, названий организаций, деталей и точных цитат.
        3. КРИТИЧЕСКИЙ ЗАПРЕТ: Запрещено генерировать данные, имена, номера и последствия, отсутствующие во входящем JSON.
        4. ФОРМАТИРОВАНИЕ: Выделяй жирным шрифтом ключевые имена, даты, организации и термины. Разделяй длинные или сложные сюжеты логическими абзацами.
        5. УМНАЯ ФИЛЬТРАЦИЯ: Если текст несет спам или запрещенные темы ('$banned'), верни строго слово REJECTED без дополнительных символов.
        6. ЯЗЫК: ${lang}.
        7. ПРЕДОХРАНИТЕЛЬ СЮЖЕТОВ: Если внутри одного id сгруппировано несколько связанных под-событий, ты обязан ПОДРОБНО РАСКРЫТЬ суть каждого из них. Сжатие фактов до размытых формулировок категорически запрещено. Выделяй разные аспекты, реакции или хронологию событий отдельными абзацами.
        ФОРМАТ ОТВЕТА (СТРОГО JSON):
        [{"id": <id из input>, "title": "<заголовок>", "summary": "<отформатированный текст статьи или строго "REJECTED">"}]
        Ввод: $jsonInput
        """.trimIndent()

        return llm.sendPrompt(prompt)
    }
}