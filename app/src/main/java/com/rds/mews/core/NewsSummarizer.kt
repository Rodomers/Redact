package com.rds.mews.core

import android.content.Context
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
import kotlin.math.max

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

class NewsSummarizer(private val db: DbHelper, private val llm: LLMClient, private val context: Context) {
    data class Topics(
        val title: String,
        val ids: List<Long>?, // Список ID всех сообщений, входящих в тему
        val weight: Int = 0,
        val id: Long = 0,
        var snippet: String = "" // Контекст (топ-3 предложения) для мерджинга
    )

    // Вспомогательный класс для группировки дублей
    data class GroupedMessage(
        val representative: Message, // Самое "полное" сообщение
        val allIds: MutableList<Long> // Список ID всех дубликатов
    )

    private val SUMMARY_START_BATCH_SIZE = 10
    private val EXTRACT_START_BATCH_SIZE = 70

    // Порог схожести для пре-дедупликации (85%)
    private val DEDUP_THRESHOLD = 0.85

    // Порог схожести для локального мерджинга по сниппетам (65%)
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
        filterTopics: Boolean = true // Флаг: True = Smart AI Merge, False = Local Algo Merge
    ): SummarizationResult {
        try {
            Log.d(TAG, "Starting summarizeTopics. Filter enabled: $filterTopics")

            // 1. Проверяем незавершенную работу
            val unfinishedTitles = db.getTitles().filter { it.text == "<промежуточный текст>" && it.time.toInt() == 0 }

            // 2. Загружаем сообщения только если нужно (нет незавершенной работы)
            val rawMessages = if (unfinishedTitles.isEmpty()) {
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

            // === ЭТАП 1: ЭКСТРАКЦИЯ И МЕРДЖИНГ ===
            if (unfinishedTitles.isEmpty()) {
                try {
                    val extractionTime = System.currentTimeMillis()

                    // А. Жесткая дедупликация "на лету"
                    val groupedMessages = deduplicateMessages(rawMessages)
                    Log.d(TAG, "Dedup: Input ${rawMessages.size} -> Unique Groups ${groupedMessages.size}")

                    // Б. Обработка батчами + Мердж (AI или Локальный, зависит от флага)
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

                        topicsData.forEach { (topic, _, _) ->
                            if (!successIds.contains(topic.id)) {
                                try { db.delTitle(name = topic.title) } catch(_: Exception) {}
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

    // --- ЛОГИКА ДЕДУПЛИКАЦИИ ---

    private fun deduplicateMessages(raw: List<Message>): List<GroupedMessage> {
        val groups = mutableListOf<GroupedMessage>()

        raw.forEach { msg ->
            val existingGroup = groups.find { group ->
                TextComparator.areSimilar(msg.mess, group.representative.mess, DEDUP_THRESHOLD)
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
            val exists = unique.any { TextComparator.areSimilar(it.mess, msg.mess, 0.95) }
            if (!exists) unique.add(msg)
        }
        return unique
    }

    // --- ЛОГИКА ЭКСТРАКЦИИ И МЕРДЖА ---

    private suspend fun processNewsInBatches(maxTopics: Int, uniqueGroups: List<GroupedMessage>, lang: String, filterEnabled: Boolean) {
        totalItemsToProcess = uniqueGroups.size
        processedItemsCount = 0
        val availableSpace = 0.4f

        try { MewsRepository.setUpdatingState("extracting_topics") } catch (_: Exception) {}

        val allExtracted = mutableListOf<Topics>()
        val batches = uniqueGroups.chunked(EXTRACT_START_BATCH_SIZE)

        coroutineScope {
            batches.forEach { batch ->
                // Динамический лимит для экстракции
                val batchLimit = (batch.size * 0.6).toInt().coerceAtLeast(5)

                // Извлекаем темы и генерируем для них сниппеты
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

        // === ГЛАВНАЯ РАЗВИЛКА МЕРДЖИНГА ===

        val finalTopicsCandidate: List<Topics> = if (filterEnabled) {
            // ВАРИАНТ А: AI Merge (Если включен фильтр)
            try {
                val current = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { baseProgress + availableSpace }
                MewsRepository.setUpdatingState("filtering_topics")
                MewsRepository.setUpdatingProgress(current)

                // Пробуем умный мерджинг
                smartMergeTopics(allExtracted, lang)
            } catch (e: Exception) {
                Log.e(TAG, "Smart merge failed, falling back to algo merge", e)
                // Если LLM не справилась (сеть, квота) — фолбэк на локальный
                simpleAlgorithmicMerge(allExtracted)
            }
        } else {
            // ВАРИАНТ Б: Local Merge (Если фильтр выключен)
            // Используем только алгоритмическое сравнение сниппетов
            simpleAlgorithmicMerge(allExtracted)
        }

        // === ФИНАЛЬНАЯ ОБРЕЗКА И СОХРАНЕНИЕ ===

        db.titlesTimeKill(0) // Чистим старое

        finalTopicsCandidate
            .sortedByDescending { it.weight }
            .take(maxTopics) // Соблюдаем лимит пользователя
            .forEach { t -> saveTopicToDb(t) }
    }

    /**
     * Быстрый локальный мердж. Склеивает топики, если их сниппеты или заголовки похожи.
     */
    private fun simpleAlgorithmicMerge(rawTopics: List<Topics>): List<Topics> {
        val merged = mutableListOf<Topics>()

        rawTopics.sortedByDescending { it.weight }.forEach { candidate ->
            val existingIndex = merged.indexOfFirst { existing ->
                // 1. Сравниваем сниппеты (самый надежный способ)
                if (existing.snippet.isNotBlank() && candidate.snippet.isNotBlank()) {
                    TextComparator.areSimilar(existing.snippet, candidate.snippet, MERGE_SNIPPET_THRESHOLD)
                } else {
                    // 2. Фолбэк на заголовки
                    TextComparator.areSimilar(existing.title, candidate.title, 0.75)
                }
            }

            if (existingIndex != -1) {
                val existing = merged[existingIndex]
                val combinedIds = (existing.ids.orEmpty() + candidate.ids.orEmpty()).distinct()
                val maxWeight = max(existing.weight, candidate.weight)
                val bestSnippet = if (existing.snippet.length > candidate.snippet.length) existing.snippet else candidate.snippet

                merged[existingIndex] = existing.copy(
                    ids = combinedIds,
                    weight = maxWeight,
                    snippet = bestSnippet
                )
            } else {
                merged.add(candidate)
            }
        }
        return merged
    }

    /**
     * Умный мерджинг с использованием LLM (восстановленный).
     */
    private suspend fun smartMergeTopics(topics: List<Topics>, lang: String): List<Topics> {
        // Рекурсия, если слишком много тем
        if (topics.size > 150) {
            val mid = topics.size / 2
            val left = smartMergeTopics(topics.subList(0, mid), lang)
            val right = smartMergeTopics(topics.subList(mid, topics.size), lang)
            return smartMergeTopics(left + right, lang)
        }

        val indexedInput = topics.mapIndexed { index, t ->
            JSONObject().apply {
                put("ix", index)
                put("t", t.title)
                put("ctx", t.snippet.take(400)) // Берем достаточно контекста для LLM
                put("w", t.weight)
            }
        }
        val jsonInput = JSONArray(indexedInput).toString()

        val prompt = """
            Задача: Объедини дублирующиеся новости в кластеры.
            Используй поле "ctx" (контекст) чтобы понять, об одном ли событии речь.
            Если события разные — НЕ объединяй.
            
            Ввод: [{"ix": 0, "t": "...", "ctx": "...", "w": 5}].
            
            Верни JSON массив:
            [{"title": "Общий заголовок", "src": [0, 5], "weight": 9}]
            Где src - это список индексов (ix) из ввода.
            Язык: $lang.
            
            Данные:
            $jsonInput
        """.trimIndent()

        rateLimiter.waitIfNeeded()
        val response = withTimeout(120000L) { llm.sendPrompt(prompt) }
        val (jsonArray, _) = safeParseJsonArrayLenient(response)

        val mergedResults = mutableListOf<Topics>()
        // Чтобы не потерять те темы, которые LLM могла "забыть", можно отслеживать использованные индексы,
        // но для простоты полагаемся на то, что модель вернет всё сгруппированное.

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
                    mergedResults.add(Topics(obj.getString("title"), mergedIds.toList(), w))
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
                Проанализируй список новостей. Сгруппируй их по конкретным инфоповодам.
                Лимит: не более $maxLimit событий (выбирай самые важные).
                Верни JSON массив: [{"title": "Заголовок", "id": [101, 105], "weight": 8}].
                weight - важность (1-10). Язык: $lang.
                Новости:
                $sanitizedBatch
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

                    // --- ГЕНЕРАЦИЯ СНИППЕТА ---
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

    // --- МЕТОДЫ ГЕНЕРАЦИИ САММАРИ ---

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
                        try { db.delTitle(name = batch.first().first.title) } catch (_: Exception) {}
                        return emptyList()
                    }
                    throw RuntimeException("Trigger split due to Blocked Content", e)
                }
                throw RuntimeException("Trigger split due to Gemini Error: ${e.errorType}", e)
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
                    if (original != null) {
                        val finalTitle = newTitle.ifBlank { original.first.title }
                        if (summary.isNotBlank()) {
                            results.add(Triple(id, Pair(summary, finalTitle), original))
                        }
                        processedIds.add(id)
                    }
                } catch (_: Exception) {}
            }

            val victims = batch.filter { item ->
                val itemId = item.first.ids?.firstOrNull() ?: 0L
                !processedIds.contains(itemId)
            }

            if (victims.isNotEmpty()) {
                val mid = victims.size / 2
                val left = adaptiveSummarizeBatch(victims.subList(0, mid), bannedWords, lang)
                val right = adaptiveSummarizeBatch(victims.subList(mid, victims.size), bannedWords, lang)
                results.addAll(left + right)
            }
            return results

        } catch (e: Exception) {
            if (e is GeminiException && (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED || e.errorType == SummarizationErrorType.API_KEY_INVALID)) {
                throw e
            }
            if (batch.size > 1) {
                val mid = batch.size / 2
                val left = adaptiveSummarizeBatch(batch.subList(0, mid), bannedWords, lang)
                val right = adaptiveSummarizeBatch(batch.subList(mid, batch.size), bannedWords, lang)
                return left + right
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
        Ты — профессиональный редактор новостной ленты. Твоя задача — написать живые, вовлекающие саммари.
        
        Правила:
        1. **Адаптивный объем:** - Сложная тема: до 500 слов, подробно.
           - Короткая новость: до 300 слов, емко.
        2. **Стиль:** Smart Casual. Без кликбейта.
        3. **Фильтр:** Если тема касается '$banned', summary должно быть "".
        4. **Язык:** $lang.
        5. **Заголовок:** Если 'title' не отражает суть 'news_content', придумай новый.
        
        Формат JSON: 
        [{"id": 123, "title": "Заголовок", "summary": "Текст..."}]
        
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
            throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Structure invalid")
        }
    }
}