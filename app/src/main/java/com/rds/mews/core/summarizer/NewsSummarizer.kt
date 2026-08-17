package com.rds.mews.core.summarizer

import android.util.Log
import com.rds.mews.database.main.TitleEntity
import com.rds.mews.localcore.Message
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.UpdatingState
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.GeminiException
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.core.text.TextComparator
import com.rds.mews.core.text.TextSanitizer
import com.rds.mews.core.text.graph.KnowledgeGraphManager
import com.rds.mews.localcore.TitleStatus
import com.rds.mews.repositories.KeywordStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max


class NewsSummarizer(private val llm: LLMClient) {
    data class Topics(
        val title: String,
        val ids: List<Long>?,
        val weight: Int = 0,
        val id: Long = 0,
        val status: Int = 0,
        val keywords: List<String> = emptyList(),
        val isBlitz: Boolean = false
    )

    fun Topics.toRawTopicDto(): RawTopicDto {
        return RawTopicDto(
            id = this.id,
            title = this.title,
            ids = this.ids ?: emptyList(),
            weight = this.weight,
            keywords = this.keywords,
            isBlitz = this.isBlitz
        )
    }

    data class SummaryResult(
        val id: Long,
        val summary: String,
        val title: String,
        val macroTag: String?,
        val payload: SummaryPayload
    )

    data class SummaryPayload(
        val topic: Topics,
        val messages: List<Message>,
        val contentPayload: String,
        val requiresUpdateHeader: Boolean
    )

    private val MATCH_RATE = 0.55f
    private val SINGLE_NEWS_CHAR_LIMIT = 3000
    private val TAG = "NewsSummarizer"

    private var totalItemsToProcess = 0
    private var processedItemsCount = 0
    private var baseProgress = 0f


    private val appContext = MewsRepository.getAppContext()
    private val stateManager = SummarizerStateManager(appContext)
    private val batchController = BatchController(appContext)
    private val executionManager = LLMExecutionManager(llm, batchController)

    private fun safeReadyFunc(func: () -> Unit) {
        try { func() } catch (e: Exception) {
            Log.e(TAG, "readyFunc execution failed", e)
        }
    }

    suspend fun summarizeTopics(
        maxTopics: Int = 20,
        messageSeconds: Long = 0,
        readyFunc: () -> Unit,
        filterTopics: Boolean = true,
        adaptive: Boolean = false
    ): SummarizationResult {
        val currentLanguage = try {
            MewsRepository.currentLanguage.first() ?: "english"
        } catch (_: Exception) { "english" }

        val currentState = when (val state = stateManager.readState()) {
            null -> {
                val newState = SummarizerState()
                stateManager.updateState { newState }
                newState
            }
            else -> state
        }

        val isExtracting = currentState.updatingState == UpdatingState.DEFAULT || currentState.updatingState == UpdatingState.EXTRACTING
        MewsRepository.setUpdatingState(currentState.updatingState)

        try {
            val bannedWords = try { MewsRepository.bannedNewsFlow.value.joinToString("'; '") } catch (_: Exception) { "" }
            var remainingIds = currentState.remainingMessageIds

            if (isExtracting && remainingIds.isEmpty() && currentState.victimMessages.isNotEmpty()) {
                remainingIds += currentState.victimMessages
                stateManager.updateState { it.copy(victimMessages = emptyList(), remainingMessageIds = remainingIds) }
            }

            if (isExtracting && remainingIds.isEmpty() && currentState.stagedRawTopics.isEmpty()) {
                val target = if (adaptive) MewsRepository.getTargetWindowTimeMark() else System.currentTimeMillis() - messageSeconds * 1000L
                val msgs = MewsRepository.getUniqueMessagesList(target)
                Log.d(TAG, "msgs size: ${msgs.size}, target time: $target")

                val processedMessageIds = try { MewsRepository.getAllUsedMessageIds(targetMs = target) } catch (_: Exception) { emptySet() }

                val seenHashes = mutableSetOf<String>()
                remainingIds = msgs
                    .filter { it.id !in processedMessageIds }
                    .filter { msg -> seenHashes.add(TextComparator.getMd5(msg.cleanText)) }
                    .sortedBy { it.cleanText.length }
                    .map { it.id }

                if (remainingIds.isEmpty()) {
                    safeReadyFunc(readyFunc)
                    return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
                }
                stateManager.updateState {
                    it.copy(
                        remainingMessageIds = remainingIds,
                        updatingState = UpdatingState.EXTRACTING,
                        timemark = System.currentTimeMillis()
                    )
                }
            }

            baseProgress = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { 0f }

            if (isExtracting) {
                val extractionTime = when (val stateMark = stateManager.readState()?.timemark) {
                    null -> {
                        val currTime = System.currentTimeMillis()
                        stateManager.updateState { it.copy(timemark = currTime) }
                        currTime
                    }
                    else -> stateMark
                }
                val rawMessagesUnsorted = MewsRepository.getMessages(ids = remainingIds)
                if (rawMessagesUnsorted.isNullOrEmpty()) {
                    safeReadyFunc(readyFunc)
                    return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
                }

                val orderMap = remainingIds.withIndex().associate { it.value to it.index }
                val rawMessages = rawMessagesUnsorted.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }

                val activeClusters = try {
                    KeywordStatsRepository.getBurstClusters()
                } catch (_: Exception) { emptyList() }

                MewsRepository.setUpdatingState(UpdatingState.EXTRACTING)
                stateManager.updateState { it.copy(updatingState = UpdatingState.EXTRACTING) }
                totalItemsToProcess = rawMessages.size
                processedItemsCount = 0
                val availableSpace = 0.4f

                executionManager.execute<Message, List<Topics>>(
                    items = rawMessages,
                    idExtractor = { it.id },
                    textExtractor = { it.cleanText },
                    promptBuilder = { batch ->
                        val batchTokens = batch.flatMap { TextComparator.tokenize(it.cleanText) }.toSet()
                        val relevantClusters = activeClusters.filter { cluster ->
                            cluster.keywords.any { it.lowercase() in batchTokens }
                        }
                        val burstContext = if (relevantClusters.isNotEmpty()) {
                            relevantClusters.joinToString("\n") { cluster ->
                                "- Приоритетный сюжет: [${cluster.keywords.joinToString(", ")}]"
                            }
                        } else ""

                        PromptFactory.buildExtractionPrompt(
                            batch = batch,
                            maxLimit = maxTopics,
                            lang = currentLanguage,
                            banned = bannedWords,
                            burstKeywords = burstContext,
                            charLimit = SINGLE_NEWS_CHAR_LIMIT
                        )
                    },
                    responseParser = { response, batch -> ResponseParser.parseExtractionResponse(response, batch) },
                    onBatchSuccess = { batch, remaining, parsedTopics ->
                        val remainingIdsList = remaining.map { it.id }
                        val rawTopicsList = parsedTopics.map { it.toRawTopicDto() }

                        stateManager.updateState { state ->
                            state.copy(
                                remainingMessageIds = remainingIdsList,
                                stagedRawTopics = state.stagedRawTopics + rawTopicsList
                            )
                        }
                        processedItemsCount += batch.size
                        MewsRepository.setUpdatingProgress(baseProgress + ((processedItemsCount.toFloat() / totalItemsToProcess) * availableSpace))
                    },
                    onBatchFailure = { batch, _ ->
                        val failedIds = batch.map { it.id }
                        stateManager.updateState { it.copy(victimMessages = it.victimMessages + failedIds) }
                    }
                )

                val finalState = stateManager.readState() ?: return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)
                val extractedTopics = finalState.stagedRawTopics.map { it.toTopics() }

                MewsRepository.setUpdatingState(UpdatingState.FILTERING)
                stateManager.updateState { it.copy(updatingState = UpdatingState.FILTERING) }

                val blitzTopics = extractedTopics.filter { it.isBlitz }
                val nonBlitzTopics = extractedTopics.filter { !it.isBlitz }

                val blitzMerged = mutableListOf<Topics>()
                mergeIntoGlobal(blitzTopics, blitzMerged)

                val mergedTopics = if (filterTopics && nonBlitzTopics.isNotEmpty()) {
                    try {
                        val result = smartMergeTopics(nonBlitzTopics, currentLanguage, bannedWords)
                        val finalReg = mutableListOf<Topics>()
                        mergeIntoGlobal(result, finalReg)
                        finalReg
                    } catch (e: Exception) {
                        Log.w(TAG, "Smart merge failed, falling back to algorithmic merge", e)
                        val fallbackMerge = mutableListOf<Topics>()
                        mergeIntoGlobal(nonBlitzTopics, fallbackMerge)
                        fallbackMerge
                    }
                } else {
                    val fallbackMerge = mutableListOf<Topics>()
                    mergeIntoGlobal(nonBlitzTopics, fallbackMerge)
                    fallbackMerge
                }

                val finalTopics = mergedTopics.sortedByDescending { it.weight }.take((maxTopics * 1.5).toInt())
                val primaryTopics = finalTopics.take(maxTopics)
                val blitzRaw = blitzMerged.sortedByDescending { it.weight }.take(maxTopics)

                KnowledgeGraphManager.processTopicsAndBuildGraph(finalTopics.map { it.keywords.toSet() })

                val primaryTopicsWithIds = primaryTopics.map { saveTopicToDb(it, extractionTime) }
                val blitzRawWithIds = blitzRaw.map { saveTopicToDb(it, extractionTime) }

                val primaryRawDto = primaryTopicsWithIds.map { it.toRawTopicDto() }
                val blitzRawDto = blitzRawWithIds.map { it.toRawTopicDto() }

                MewsRepository.setSourceSummarizingSyncTime()

                stateManager.updateState { it.copy(
                    updatingState = UpdatingState.SUMMARIZING,
                    primaryTopics = primaryRawDto,
                    reserveTopics = finalTopics.map { topic -> topic.toRawTopicDto() }.filter { element -> !primaryRawDto.contains(element) },
                    blitzTopics = blitzRawDto
                )
                }
                baseProgress = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { baseProgress }
            }

            val summarizationState = stateManager.readState() ?: return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)

            val normalTopics = summarizationState.primaryTopics.map { it.toTopics() }
            val blitzTopics = summarizationState.blitzTopics.map { it.toTopics() }

            if (normalTopics.isEmpty() && blitzTopics.isEmpty()) {
                stateManager.clearState()
                safeReadyFunc(readyFunc)
                return SummarizationResult.Success
            }

            val preHistory = try {
                MewsRepository.getRecentTitlesForStorylines(
                    (stateManager.readState()?.timemark
                        ?: System.currentTimeMillis()) - 72 * 3600_000L
                )
                    .filter { it.updateTime != MewsRepository.lastTitlesUpdate.first() }
            } catch (_: Exception) { emptyList() }

            val processedTopicsSignatures = mutableListOf<Pair<String, List<String>>>()
            preHistory.forEach { processedTopicsSignatures.add(it.title to it.keywords) }

            totalItemsToProcess = normalTopics.size + blitzTopics.size
            processedItemsCount = 0
            val availableProgressSpace = 0.95f - baseProgress

            val allRequiredMessageIds =
                (normalTopics.flatMap { it.ids ?: emptyList() } + blitzTopics.flatMap {
                    it.ids ?: emptyList()
                }).distinct()
            val rawMessagesList =
                if (allRequiredMessageIds.isNotEmpty()) MewsRepository.getMessages(ids = allRequiredMessageIds)
                    ?: emptyList() else emptyList()

            val normalPayloads = buildPayloads(normalTopics, processedTopicsSignatures, rawMessagesList, preHistory)

            val deduplicatedBlitz = blitzTopics.map { deduplicateBlitzTopicMessages(it, rawMessagesList) }
            val blitzPayloads = buildPayloads(deduplicatedBlitz, processedTopicsSignatures, rawMessagesList, preHistory)

            val finalSummaryResults = mutableListOf<SummaryResult>()
            val failedTopics = mutableListOf<Topics>()

            if (normalPayloads.isNotEmpty()) {
                executionManager.execute<SummaryPayload, List<SummaryResult>>(
                    items = normalPayloads,
                    idExtractor = { it.topic.id },
                    textExtractor = { it.contentPayload },
                    promptBuilder = { batch -> PromptFactory.buildSummaryPrompt(batch, currentLanguage, bannedWords, isBlitz = false) },
                    responseParser = { response, batch -> ResponseParser.parseSummaryResponse(response, batch, llm) },
                    onBatchSuccess = { batch, _, results ->
                        processSummaryResults(results, preHistory, processedTopicsSignatures, summarizationState.timemark)
                        processedItemsCount += batch.size
                        MewsRepository.setUpdatingProgress(baseProgress + ((processedItemsCount.toFloat() / totalItemsToProcess) * availableProgressSpace))
                    },
                    onBatchFailure = { batch, _ ->
                        failedTopics.addAll(batch.map { it.topic })
                    }
                )
            }

            if (blitzPayloads.isNotEmpty()) {
                val limitedBlitzPayloads = blitzPayloads.take(maxTopics)
                executionManager.execute<SummaryPayload, List<SummaryResult>>(
                    items = limitedBlitzPayloads,
                    idExtractor = { it.topic.id },
                    textExtractor = { it.contentPayload },
                    promptBuilder = { batch -> PromptFactory.buildSummaryPrompt(batch, currentLanguage, bannedWords, isBlitz = true) },
                    responseParser = { response, batch -> ResponseParser.parseSummaryResponse(response, batch, llm) },
                    // вопрос выше
                    onBatchSuccess = { batch, _, results ->
                        processSummaryResults(results, preHistory, processedTopicsSignatures, summarizationState.timemark)
                        processedItemsCount += batch.size
                        MewsRepository.setUpdatingProgress(baseProgress + ((processedItemsCount.toFloat() / totalItemsToProcess) * availableProgressSpace))
                    },
                    onBatchFailure = { batch, _ ->
                        failedTopics.addAll(batch.map { it.topic })
                    }
                )
            }

            processSummaryResults(finalSummaryResults, preHistory, processedTopicsSignatures, summarizationState.timemark)

            MewsRepository.setLastTitlesUpdate(summarizationState.timemark)

            stateManager.clearState()
            safeReadyFunc(readyFunc)
            return SummarizationResult.Success
        } catch (e: GeminiException) {
            safeReadyFunc(readyFunc)
            return SummarizationResult.Failure(e.errorType, e)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            safeReadyFunc(readyFunc)
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
        }
    }

    private suspend fun saveTopicToDb(t: Topics, extractionTime: Long): Topics {
        val ids = t.ids ?: emptyList()
        val generatedId = MewsRepository.addTitle(
            newTimeVal = extractionTime,
            newTitle = t.title,
            summary = "",
            messageIds = ids,
            status = TitleStatus.PROCESSING,
            keywords = t.keywords,
            isBlitz = t.isBlitz
        )
        return t.copy(id = generatedId)
    }

    private suspend fun compareTopics(
        topicTitle: String, topicKeywords: List<String>, otherTitle: String, otherKeywords: List<String>,
        topicText: String? = null, otherText: String? = null
    ): Double {
        val expandedTopicKeywords = KnowledgeGraphManager.expandContext(topicKeywords.toSet())
        val expandedOtherKeywords = KnowledgeGraphManager.expandContext(otherKeywords.toSet())

        var kwMatches = 0

        for (tk in expandedTopicKeywords) {
            for (hk in expandedOtherKeywords) {
                if (tk.equals(hk, ignoreCase = true) || TextComparator.areSimilar(tk.lowercase(), hk.lowercase(), 0.8f)) {
                    kwMatches++
                    break
                }
            }
        }

        val keywordScore = kwMatches.toDouble() / (minOf(expandedTopicKeywords.size, expandedOtherKeywords.size).coerceAtLeast(1))

        val baseTitlesScore = TextComparator.countThreshold(
            topicTitle.lowercase(),
            otherTitle.lowercase()
        )
        val topicTitleTokens = TextComparator.tokenize(topicTitle)
        val otherTitleTokens = TextComparator.tokenize(otherTitle)
        val titlesThreshold = TextComparator.combineWithSemanticScore(
            baseScore = baseTitlesScore,
            tokens1 = topicTitleTokens,
            tokens2 = otherTitleTokens
        ).toDouble()

        val hasSummary = !topicText.isNullOrBlank() && !otherText.isNullOrBlank()
        val summaryThreshold = if (hasSummary) {
            val baseScore = TextComparator.countThreshold(
                TextSanitizer.sanitize(topicText.lowercase()),
                TextSanitizer.sanitize(otherText.lowercase())
            )
            val topicTextTokens = TextComparator.tokenize(topicText)
            val historySummaryTokens = TextComparator.tokenize(otherText)
            TextComparator.combineWithSemanticScore(
                baseScore = baseScore,
                tokens1 = topicTextTokens,
                tokens2 = historySummaryTokens
            ).toDouble()
        } else 0.0

        val totalWeight = if (hasSummary) 4.0 else 2.0
        return (keywordScore + titlesThreshold + summaryThreshold * 2.0) / totalWeight
    }

    private suspend fun buildPayloads(
        topics: List<Topics>,
        processedTopicsSignatures: List<Pair<String, List<String>>>,
        rawMessagesList: List<Message>,
        preHistory: List<TitleEntity>
    ): List<SummaryPayload> {
        return topics.mapNotNull { topic ->
            val isDuplicate = processedTopicsSignatures.any { (procTitle, procKeywords) ->
                compareTopics(
                    topicTitle = topic.title,
                    topicKeywords = topic.keywords,
                    otherTitle = procTitle,
                    otherKeywords = procKeywords
                ) >= MATCH_RATE
            }

            if (isDuplicate) {
                processedItemsCount++
                return@mapNotNull null
            }

            val messageIds = topic.ids ?: emptyList()
            val fetchedMessages = if (messageIds.isNotEmpty()) {
                rawMessagesList.filter { it.id in messageIds }
            } else emptyList()

            if (fetchedMessages.isEmpty()) {
                return@mapNotNull null
            }

            val uniqueMessages = deduplicateForSummaryPayload(fetchedMessages)
            var contentPayload = uniqueMessages.joinToString("\n") { "— ${it.cleanText.take(SINGLE_NEWS_CHAR_LIMIT)}" }
            var requiresUpdate = false

            if (!topic.isBlitz) {
                val bestMatchId = findBestMatch(topic, null, preHistory)
                if (bestMatchId != null) {
                    val matchedItem = preHistory.find { it.id == bestMatchId }
                    if (matchedItem != null) {
                        requiresUpdate = !matchedItem.isRead
                        contentPayload = "ПРЕДЫДУЩИЙ КОНТЕКСТ СЮЖЕТА:\n${matchedItem.summary}\n\n$contentPayload"
                    }
                }
            }

            SummaryPayload(topic, uniqueMessages, contentPayload, requiresUpdate)
        }
    }

    private suspend fun processSummaryResults(
        results: List<SummaryResult>,
        preHistory: List<TitleEntity>,
        signatures: MutableList<Pair<String, List<String>>>,
        extractionTime: Long
    ) {
        for (item in results) {
            val summary = item.summary
            val newTitle = item.title
            val macroTag = item.macroTag
            val (topic, suitableMessages, _) = item.payload

            if (summary.isBlank() || summary == "REJECTED" || summary.length <= 15) continue

            var bestMatchId: Long? = null

            if (!topic.isBlitz) {
                val newTimeVal = suitableMessages.minOfOrNull { it.time } ?: extractionTime
                val filteredHistory = preHistory.filter { historyItem ->
                    historyItem.id != topic.id && !(historyItem.eventTime > newTimeVal || (historyItem.eventTime == newTimeVal && historyItem.id >= topic.id))
                }
                bestMatchId = findBestMatch(topic, summary, filteredHistory)
            }

            var absorbed = false
            if (bestMatchId != null) {
                val parentTopic = preHistory.find { it.id == bestMatchId }
                val isParentUnread = parentTopic?.isRead == false
                val isParentRecent = parentTopic != null && (extractionTime - parentTopic.eventTime) <= 72 * 3600_000L

                if (parentTopic != null && isParentUnread && isParentRecent) {
                    val combinedSummary = "${parentTopic.summary}\n\n$summary"
                    MewsRepository.updateTitle(
                        id = bestMatchId,
                        newTitle = parentTopic.title,
                        summary = combinedSummary,
                        parentId = null,
                        macroTag = macroTag,
                        newMessageIds = topic.ids
                    )
                    absorbed = true
                } else {
                    MewsRepository.addTitle(
                        newTimeVal = suitableMessages.minOfOrNull { it.time } ?: extractionTime,
                        newTitle = newTitle,
                        summary = summary,
                        messageIds = suitableMessages.map { it.id },
                        keywords = topic.keywords,
                        parentId = bestMatchId
                    )
                }
            } else {
                MewsRepository.addTitle(
                    newTimeVal = suitableMessages.minOfOrNull { it.time } ?: extractionTime,
                    newTitle = newTitle,
                    summary = summary,
                    messageIds = suitableMessages.map { it.id },
                    keywords = topic.keywords,
                    isBlitz = topic.isBlitz
                )
            }

            if (!absorbed) {
                signatures.add(Pair(newTitle, topic.keywords))
            }
        }
    }

    private fun deduplicateForSummaryPayload(messages: List<Message>): List<Message> {
        val unique = mutableListOf<Message>()
        messages.forEach { msg ->
            val cleanMsg = TextSanitizer.sanitize(msg.cleanText)
            val exists = unique.any { TextComparator.areSimilar(TextSanitizer.sanitize(it.cleanText), cleanMsg, 0.85f) }
            if (!exists) unique.add(msg)
        }
        return unique
    }

    private fun deduplicateBlitzTopicMessages(
        topic: Topics,
        rawMessages: List<Message>
    ): Topics {
        val ids = topic.ids ?: return topic
        if (ids.size <= 5) return topic

        val topicMessages = rawMessages.filter { it.id in ids }
        if (topicMessages.size <= 5) return topic.copy(ids = topicMessages.map { it.id })

        var currentThreshold = 0.85f
        val minThreshold = 0.40f
        val step = 0.05f

        val activeMessages = topicMessages.toMutableList()

        while (activeMessages.size > 5 && currentThreshold >= minThreshold) {
            var i = 0
            while (i < activeMessages.size && activeMessages.size > 5) {
                var j = i + 1
                while (j < activeMessages.size && activeMessages.size > 5) {
                    val isSimilar = TextComparator.areSimilar(
                        activeMessages[i].cleanText,
                        activeMessages[j].cleanText,
                        currentThreshold
                    )
                    if (isSimilar) {
                        if (activeMessages[i].cleanText.length >= activeMessages[j].cleanText.length) {
                            activeMessages.removeAt(j)
                        } else {
                            activeMessages.removeAt(i)
                            i--
                            break
                        }
                    } else {
                        j++
                    }
                }
                i++
            }
            currentThreshold -= step
        }

        val finalIds = activeMessages
            .sortedByDescending { it.cleanText.length }
            .take(5)
            .map { it.id }

        return topic.copy(ids = finalIds)
    }


    private suspend fun mergeIntoGlobal(newTopics: List<Topics>, globalCache: MutableList<Topics>) {
        newTopics.forEach { candidate ->
            val existingIndex = globalCache.indexOfFirst { existing ->
                if (existing.isBlitz != candidate.isBlitz) return@indexOfFirst false

                compareTopics(
                    candidate.title, candidate.keywords, existing.title, existing.keywords
                ) >= MATCH_RATE
            }

            if (existingIndex != -1) {
                val existing = globalCache[existingIndex]
                val combinedIds = (existing.ids.orEmpty() + candidate.ids.orEmpty()).distinct()
                val maxWeight = max(existing.weight, candidate.weight)

                val combinedKeywords = mutableListOf<String>()
                (existing.keywords + candidate.keywords).forEach { word ->
                    if (combinedKeywords.none { TextComparator.areSimilar(it, word, 0.85f) }) combinedKeywords.add(word)
                }

                globalCache[existingIndex] = existing.copy(
                    ids = combinedIds,
                    weight = maxWeight,
                    keywords = combinedKeywords,
                    isBlitz = true
                )
            } else {
                globalCache.add(candidate)
            }
        }
    }

    private suspend fun smartMergeTopics(topics: List<Topics>, lang: String, banned: String): List<Topics> {
        if (topics.isEmpty()) return emptyList()

        val mergedResults = mutableListOf<Topics>()

        executionManager.execute<Topics, List<Topics>>(
            items = topics,
            idExtractor = { it.id },
            textExtractor = { it.title + " " + it.keywords.joinToString(" ") },
            promptBuilder = { batch ->
                val indexedInput = batch.mapIndexed { index, t ->
                    JSONObject().apply {
                        put("ix", index)
                        put("t", t.title)
                        put("kw", t.keywords.joinToString(", "))
                        put("w", t.weight)
                    }
                }
                val jsonInput = JSONArray(indexedInput).toString()

                """
                Задача: Объедини дублирующиеся новости в кластеры.
                Важно:
                1. УЗКОЕ СЛИЯНИЕ СЮЖЕТОВ: Объединяй дубликаты и новости, описывающие один и тот же инцидент или принадлежащие к одному узкому развивающемуся сюжету. Избегай объединения независимых событий только на основе общей локации или категории.
                2. Если группа новостей относится к запрещенным темам ('$banned') — НЕ включай её в итоговый список. Удали.
                3. Формируй заголовки в стиле Smart Casual.
                Ввод: [{"ix": 0, "t": "...", "kw": "...", "w": 5}].
                Верни ТОЛЬКО JSON массив:
                [{"title": "Общий заголовок", "src": [0, 5], "weight": 9}]
                Где src - это список индексов (ix). Язык: $lang.
                Данные: $jsonInput
                """.trimIndent()
            },
            responseParser = { response, batch ->
                val (jsonArray, _) = llm.safeParseJsonArray(response)
                val batchMerged = mutableListOf<Topics>()

                for (i in 0 until jsonArray.length()) {
                    try {
                        val obj = jsonArray.getJSONObject(i)
                        val sourcesIndices = obj.getJSONArray("src")
                        val mergedIds = mutableSetOf<Long>()
                        val mergedKeywords = mutableSetOf<String>()

                        for (j in 0 until sourcesIndices.length()) {
                            val idx = sourcesIndices.getInt(j)
                            if (idx in batch.indices) {
                                val t = batch[idx]
                                t.ids?.let { mergedIds.addAll(it) }
                                mergedKeywords.addAll(t.keywords)
                            }
                        }

                        if (mergedIds.isNotEmpty()) {
                            val w = obj.optInt("weight", 5)
                            batchMerged.add(Topics(obj.getString("title"), mergedIds.toList(), w, keywords = mergedKeywords.take(8).toList(), isBlitz = false))
                        }
                    } catch (_: Exception) {}
                }
                batchMerged
            },
            onBatchSuccess = { _, _, parsedTopics ->
                mergedResults.addAll(parsedTopics)
            },
            onBatchFailure = { batch, _ ->
                mergedResults.addAll(batch)
            }
        )

        return mergedResults
    }


    private suspend fun findBestMatch(topic: Topics, summaryToCompare: String?, history: List<TitleEntity>, aimedScore: Double = MATCH_RATE.toDouble()): Long? {
        if (topic.isBlitz) return null

        var bestMatchId: Long? = null
        var maxScore = 0.0

        for (historyItem in history) {
            if (historyItem.isBlitz) continue

            val score = compareTopics(
                topicTitle = topic.title,
                topicKeywords = topic.keywords,
                topicText = summaryToCompare,
                otherTitle = historyItem.title,
                otherKeywords = historyItem.keywords,
                otherText = historyItem.summary
            )

            if (score >= aimedScore && score > maxScore) {
                maxScore = score
                bestMatchId = historyItem.id
            }
        }

        return bestMatchId
    }

    object PromptFactory {
        fun buildExtractionPrompt(
            batch: List<Message>,
            maxLimit: Int,
            lang: String,
            banned: String,
            burstKeywords: String,
            charLimit: Int
        ): String {
            val sanitizedBatch = batch.joinToString("\n") { msg ->
                "• ${msg.cleanText.replace("\"", "'").replace("`", "").take(charLimit)} (id - ${msg.id})"
            }

            return """
                Проанализируй новости и выдели ОТДЕЛЬНЫЕ новостные события.
                ЛИМИТЫ (СТРОГО): Максимальное количество тем: $maxLimit. Если событий больше — выбери самые резонансные и значимые.
                
                ИНЖЕКЦИЯ ПРИОРИТЕТОВ: В текущем наборе присутствуют признаки следующих критически важных развивающихся событий:
                $burstKeywords
                Если находишь новости, относящиеся к этим приоритетным сюжетам, ОБЯЗАТЕЛЬНО выделяй их в отдельные темы, даже если они кажутся незначительными.
                
                Группируй новости по Сюжетным Линиям:
                1. Цепочка событий (ОСТАВЛЯТЬ ВМЕСТЕ): Например, событие + реакция + последствия = ОДНА тема.
                2. Сюжетная кластеризация: Объединяй события в одну тему только при наличии прямой причинно-следственной связи.
                ФИЛЬТРАЦИЯ (СТРОГО): Игнорируй рекламу, розыгрыши, а также темы: '$banned'.
                
                ИНСТРУКЦИЯ ДЛЯ КАТЕГОРИИ "ДРУГОЕ":
                Используй тему "Другое" (на языке $lang) исключительно для единичных, изолированных микроновостей.
                1. Массив keywords: ["Другое"] (строго одно слово).
                2. Флаг isBlitz: true.
                3. Не более 5 самых важных микроновостей.
                
                ИЗВЛЕЧЕНИЕ СУЩНОСТЕЙ (NER): Для каждой темы, кроме "Другое", извлеки ключевые сущности, упомянутые в тексте. Строго распредели их по 6 макрокатегориям: 'persons' (люди, должности), 'locations' (география), 'organizations' (компании, партии), 'events' (события, конфликты), 'products_tech' (товами, технологии, крипта), 'laws_regulations' (законы, договоры).
    
                Верни JSON массив (СТРОГО):
                [
                  {
                    "title": "Заголовок", 
                    "id": [101, 105], 
                    "keywords": ["тег1", "тег2"], 
                    "weight": 8, 
                    "isBlitz": false,
                    "entities": {
                       "persons": [{"entity": "Имя", "context": "фрагмент текста с упоминанием"}],
                       "locations": [],
                       "organizations": [],
                       "events": [],
                       "products_tech": [],
                       "laws_regulations": []
                    }
                  }
                ]
                Язык: $lang.
                Новости:
                $sanitizedBatch
            """.trimIndent()
        }

        fun buildSummaryPrompt(batch: List<SummaryPayload>, lang: String, banned: String, isBlitz: Boolean): String {
            val jsonInput = JSONArray(batch.map { item ->
                JSONObject().apply {
                    put("id", item.topic.ids?.firstOrNull() ?: 0L)
                    put("title", item.topic.title)
                    put("news_content", item.contentPayload)
                    if (!isBlitz) put("requires_update_header", item.requiresUpdateHeader)
                }
            })

            return if (isBlitz) {
                """
                Ты — аналитик. Твоя цель — написать краткий дайджест микроновостей. Форматируй ответ как маркированный список (1-2 предложения на событие с кратким подзаголовком).
                1. ДОСТОВЕРНОСТЬ: Строй обзор исключительно на фактах из поля news_content. Запрещено генерировать несуществующие данные.
                2. УМНАЯ ФИЛЬТРАЦИЯ: Если спам/запрет ('$banned') — вырезай. REJECTED только если не осталось смысла вообще.
                3. ЯЗЫК: $lang. Точность дат, сумм, прямая речь в кавычках.
                ФОРМАТ (СТРОГО JSON):
                [{"id": <id>, "title": "<Заголовок>", "text": "<текст>"}]
                Ввод: $jsonInput
                """.trimIndent()
            } else {
                """
                Ты — профессиональный журналист-аналитик. Оптимальный объем — около 300 слов.
                1. СТИЛЬ: Smart Casual, связная статья, строгая объективность.
                2. ДОСТОВЕРНОСТЬ: Исключительно из news_content. Внешние знания только для пояснений.
                3. ТОЧНОСТЬ: Даты, суммы, статистика без округлений.
                4. УМНАЯ ФИЛЬТРАЦИЯ: Игнорируй ('$banned'). Если пустая новость — верни REJECTED.
                5. МАКРОКАТЕГОРИЯ: 1 общая категория сюжета (Политика, Экономика и тд).
                6. ОБРАБОТКА ОБНОВЛЕНИЙ: Если requires_update_header = true, пиши ТОЛЬКО дополнение к фактам. Начни с "**Обновление:**` или аналога на выбранном языке. Если false — самостоятельная статья.
                ЯЗЫК: $lang.
                ФОРМАТ (СТРОГО JSON):
                [{"id": <id>, "title": "<Заголовок>", "text": "<статья>", "macro_tag": "<Категория>"}]
                Ввод: $jsonInput
                """.trimIndent()
            }
        }
    }

    object ResponseParser {
        suspend fun parseExtractionResponse(response: String, batch: List<Message>): List<Topics> {
            val llmParser = LLMClient()
            val (jsonArray, _) = llmParser.safeParseJsonArray(response)
            val topics = mutableListOf<Topics>()

            val extractedEntities = mutableMapOf<String, MutableList<String>>()
            val validWordCounts = mutableMapOf<String, Double>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val idsArr = obj.getJSONArray("id")
                    val fullIdList = (0 until idsArr.length()).map { idsArr.getLong(it) }.toMutableList()

                    val w = obj.optInt("weight", 5)
                    val title = obj.getString("title")

                    val keywordsList = mutableListOf<String>()
                    val keywordsJson = obj.optJSONArray("keywords")
                    if (keywordsJson != null) {
                        for (k in 0 until keywordsJson.length()) {
                            keywordsList.add(keywordsJson.getString(k))
                        }
                    }

                    val mappedKeywords = keywordsList.map { it.lowercase() }
                    val isBlitz = obj.optBoolean("isBlitz", false) || mappedKeywords.contains("другое") || mappedKeywords.contains("other") || mappedKeywords.contains("blitz")
                    val finalKeywords = if (isBlitz) mappedKeywords.take(1) else keywordsList

                    if (obj.has("entities")) {
                        val entitiesObj = obj.getJSONObject("entities")
                        val iterator = entitiesObj.keys()

                        while (iterator.hasNext()) {
                            val category = iterator.next()
                            val entArray = entitiesObj.optJSONArray(category) ?: continue
                            for (j in 0 until entArray.length()) {
                                val entObj = entArray.optJSONObject(j) ?: continue
                                val entityName = entObj.optString("entity").lowercase().trim()
                                val contextStr = entObj.optString("context").lowercase().trim()
                                if (entityName.isEmpty() || contextStr.isEmpty() || entityName.length > contextStr.length) continue

                                val contextTokens = TextComparator.tokenize(contextStr)
                                val entityIsReasonable = contextStr.contains(entityName) || contextTokens.any { TextComparator.areSimilar(it, entityName, 0.8f) }

                                if (entityIsReasonable) {
                                    val existsInOriginalText = batch.filter { it.id in fullIdList }.any {
                                        it.cleanText.contains(contextStr)
                                    }
                                    if (existsInOriginalText) {
                                        extractedEntities.getOrPut(category) { mutableListOf() }.add(entityName)
                                    }
                                }
                            }
                        }

                        finalKeywords.forEach { kw ->
                            val existsInOriginalText = batch.filter { it.id in fullIdList }.any {
                                it.cleanText.lowercase().contains(kw.lowercase())
                            }
                            if (existsInOriginalText) {
                                validWordCounts[kw] = validWordCounts.getOrDefault(kw, 0.0) + 1.0
                            }
                        }
                    }

                    topics.add(Topics(title, fullIdList, w, keywords = finalKeywords, isBlitz = isBlitz))
                } catch (_: Exception) {}
            }

            if (validWordCounts.isNotEmpty()) {
                KeywordStatsRepository.updateWordStats(validWordCounts)
            }
            if (extractedEntities.isNotEmpty()) {
                KeywordStatsRepository.updateEntities(extractedEntities)
            }

            return topics
        }

        fun parseSummaryResponse(response: String, batch: List<SummaryPayload>, llmParser: LLMClient): List<SummaryResult> {
            val (jsonArray, _) = llmParser.safeParseJsonArray(response)
            val results = mutableListOf<SummaryResult>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    val summary = obj.getString("text")
                    val newTitle = obj.optString("title").takeIf { it.isNotBlank() } ?: ""
                    val macroTag = obj.optString("macro_tag").takeIf { it.isNotBlank() }

                    val original = batch.find { it.topic.ids?.contains(id) == true }

                    if (original != null && summary.trim() != "REJECTED" && summary.trim().length > 15) {
                        results.add(SummaryResult(
                            id = id,
                            summary = summary,
                            title = newTitle.ifBlank { original.topic.title },
                            macroTag = macroTag,
                            payload = original
                        ))
                    }
                } catch (e: Exception) {
                    Log.w("ResponseParser", "Error mapping summary JSON result", e)
                }
            }
            return results
        }
    }
}