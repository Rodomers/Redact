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
import com.rds.mews.core.text.TokenEstimator
import com.rds.mews.core.text.graph.GraphCache
import com.rds.mews.core.text.graph.KnowledgeGraphManager
import com.rds.mews.database.keyword_stats.BurstClusterEntity
import com.rds.mews.localcore.TitleStatus
import com.rds.mews.repositories.KeywordStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class NewsSummarizer(private val llm: LLMClient) {
    data class Topics(
        val title: String,
        val ids: List<Long>?,
        val weight: Int = 0,
        val id: Long = 0,
        val status: Int = 0,
        val keywords: List<String> = emptyList(),
        val isBlitz: Boolean = false,
        val fromBurst: Boolean = false
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

    data class ParsedSummaryBatch(
        val results: List<SummaryResult>,
        val noUpdateTopicIds: Set<Long> = emptySet()
    )

    data class SummaryPayload(
        val topic: Topics,
        val messages: List<Message>,
        val contentPayload: String,
        val requiresUpdateHeader: Boolean,
        val allMessagesTime: Long? = null
    )

    private data class PreparedMessage(
        val message: Message,
        val sanitizedText: String,
        val estimatedTokens: Int
    )

    companion object {
        private const val MATCH_RATE = 0.52f
        private const val TOPIC_TOKEN_LIMIT = 6000
        private const val TOPIC_MAX_MESSAGES_LIMIT = 30
        private const val SIGNAL_SIMILARITY_THRESHOLD = 0.8f

        private const val MAX_SUMMARIZATION_ATTEMPTS = 3
        private const val SINGLE_NEWS_CHAR_LIMIT = 3000
        private const val TAG = "NewsSummarizer"
    }

    private var totalItemsToProcess = 0
    private val processedItemsCount = AtomicInteger(0)
    private var baseProgress = 0f

    private val hasBlockedContent = AtomicBoolean(false)

    private val appContext = MewsRepository.getAppContext()
    private val stateManager = SummarizerStateManager(appContext)
    private val batchController = BatchController()
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
        adaptive: Boolean = false
    ): SummarizationResult {
        Log.i(TAG, "=== START PIPELINE ===")
        MewsRepository.setStoppedManually(false)
        hasBlockedContent.set(false)
        val currentLanguage = try {
            MewsRepository.currentLanguage.first() ?: "english"
        } catch (_: Exception) { "english" }

        val currentState = when (val state = stateManager.readState()) {
            null -> {
                val newState = SummarizerState(
                    updatingState = UpdatingState.UPDATING
                )
                stateManager.updateState { newState }
                newState
            }
            else -> state
        }

        val isFiltering = currentState.updatingState == UpdatingState.FILTERING
        val isSummarizing =
            currentState.updatingState == UpdatingState.SUMMARIZING || currentState.primaryTopics.isNotEmpty() || currentState.blitzTopics.isNotEmpty() || MewsRepository.getTitlesWithStatus(
                TitleStatus.PROCESSING.statusId
            ).isNotEmpty()
        val isExtracting = (currentState.updatingState == UpdatingState.DEFAULT || currentState.updatingState == UpdatingState.EXTRACTING || currentState.updatingState == UpdatingState.UPDATING) && !isSummarizing
        MewsRepository.setUpdatingState(currentState.updatingState)

        Log.i(TAG, "State: ${currentState.updatingState}, isExtracting: $isExtracting, remainingIds: ${currentState.remainingMessageIds.size}, victims: ${currentState.victimMessages.size}")

        try {
            val bannedWords = try { MewsRepository.bannedNewsFlow.value.joinToString("'; '").lowercase() } catch (_: Exception) { "" }
            var remainingIds = currentState.remainingMessageIds

            if (isExtracting && remainingIds.isEmpty() && currentState.victimMessages.isNotEmpty()) {
                Log.d(TAG, "[INIT] Moving ${currentState.victimMessages.size} victims to remaining queue")
                remainingIds = remainingIds + currentState.victimMessages
                stateManager.updateState { it.copy(victimMessages = emptyList(), remainingMessageIds = remainingIds) }
            }

            if (isExtracting && remainingIds.isEmpty() && currentState.stagedRawTopics.isEmpty()) {
                val target = if (adaptive) MewsRepository.getTargetWindowTimeMark() else System.currentTimeMillis() - messageSeconds * 1000L
                val msgs = MewsRepository.getUniqueMessagesList(target)
                Log.d(TAG, "[INIT] Fetched unique messages: ${msgs.size}, target time: $target")

                val processedMessageIds = try { MewsRepository.getAllUsedMessageIds(targetMs = target) } catch (_: Exception) { emptySet() }
                Log.d(TAG, "[INIT] Already processed IDs in DB: ${processedMessageIds.size}")

                val seenHashes = mutableSetOf<String>()
                remainingIds = msgs
                    .filter { it.id !in processedMessageIds }
                    .filter { msg -> seenHashes.add(TextComparator.getMd5(msg.cleanText)) }
                    .sortedBy { it.cleanText.length }
                    .map { it.id }

                Log.i(TAG, "[INIT] Final filtered queue for extraction: ${remainingIds.size} messages")

                if (remainingIds.isEmpty()) {
                    Log.i(TAG, "[INIT] Empty queue, aborting pipeline.")
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
                val rawMessagesUnsorted = MewsRepository.getMessages(ids = remainingIds)
                if (rawMessagesUnsorted.isNullOrEmpty()) {
                    Log.w(TAG, "[EXTRACTION] Raw messages fetched from DB are null or empty. Aborting.")
                    safeReadyFunc(readyFunc)
                    return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
                }

                val orderMap = remainingIds.withIndex().associate { it.value to it.index }
                val rawMessages = rawMessagesUnsorted.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }

                val activeClusters = try {
                    KeywordStatsRepository.getBurstClusters()
                        .sortedByDescending { it.peakZScore }
                        .fold(mutableListOf<BurstClusterEntity>()) { acc, candidate ->
                            val matchIndex = acc.indexOfFirst { existing ->
                                areKeywordListsSimilar(
                                    words1 = existing.keywords,
                                    words2 = candidate.keywords,
                                    wordSimilarityThreshold = 0.8f,
                                    clusterMatchRate = 0.8
                                )
                            }

                            if (matchIndex != -1) {
                                val existing = acc[matchIndex]
                                acc[matchIndex] = existing.copy(
                                    keywords = (existing.keywords + candidate.keywords).distinct(),
                                    messageIds = (existing.messageIds + candidate.messageIds).distinct(),
                                    peakZScore = maxOf(existing.peakZScore, candidate.peakZScore)
                                )
                            } else {
                                acc.add(candidate)
                            }
                            acc
                        }
                } catch (_: Exception) { emptyList() }

                Log.i(TAG, "[STAGE: EXTRACTION] Start execution manager for ${rawMessages.size} messages. Active Burst clusters: ${activeClusters.size}")

                MewsRepository.setUpdatingState(UpdatingState.EXTRACTING)
                stateManager.updateState { it.copy(updatingState = UpdatingState.EXTRACTING) }
                totalItemsToProcess = rawMessages.size
                processedItemsCount.set(0)
                val availableSpace = 0.4f

                executionManager.execute<Message, List<Topics>>(
                    items = rawMessages,
                    idExtractor = { it.id },
                    textExtractor = { it.cleanText },
                    batchMode = BatchMode.RAW_ITEMS,
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

                        if (relevantClusters.isNotEmpty()) {
                            Log.d(TAG, "[EXTRACTION] Prompt built with ${relevantClusters.size} injected burst clusters.")
                        }

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
                        val filtered = parsedTopics.filter { topic ->
                            !topic.keywords.any { bannedWords.contains(it) }
                        }
                        val rawTopicsList = filtered.map { it.toRawTopicDto() }

                        Log.d(TAG, "[EXTRACTION] Batch Success: Extracted ${parsedTopics.size} topics. Remaining queue: ${remainingIdsList.size}")

                        stateManager.updateState { state ->
                            state.copy(
                                remainingMessageIds = remainingIdsList,
                                stagedRawTopics = state.stagedRawTopics + rawTopicsList
                            )
                        }
                        val currentCount = processedItemsCount.addAndGet(batch.size)
                        MewsRepository.setUpdatingProgress(baseProgress + ((currentCount.toFloat() / totalItemsToProcess) * availableSpace))
                    },
                    onBatchFailure = { batch, _ ->
                        val failedIds = batch.map { it.id }
                        Log.w(TAG, "[EXTRACTION] Batch Failed (Final): ${failedIds.size} messages moved to victims.")
                        stateManager.updateState { it.copy(victimMessages = it.victimMessages + failedIds) }
                    }
                )
            }

            val filteringState = stateManager.readState() ?: return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)
            if (isFiltering || filteringState.stagedRawTopics.isNotEmpty()) {
                val extractionTime = when (val stateMark = stateManager.readState()?.timemark) {
                    null -> {
                        val currTime = System.currentTimeMillis()
                        stateManager.updateState { it.copy(timemark = currTime) }
                        currTime
                    }
                    else -> stateMark
                }

                val finalState = stateManager.readState() ?: return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)
                val extractedTopics = finalState.stagedRawTopics.map { it.toTopics() }

                Log.i(TAG, "[STAGE: FILTERING] Extracted ${extractedTopics.size} raw topics in total. Transitioning to Filtering...")

                MewsRepository.setUpdatingState(UpdatingState.FILTERING)
                stateManager.updateState { it.copy(updatingState = UpdatingState.FILTERING) }

                val normalizedTopics = extractedTopics.map { it.copy(weight = it.weight) }
                val blitzTopics = normalizedTopics.filter { it.isBlitz }
                val nonBlitzTopics = normalizedTopics.filter { !it.isBlitz }
                val filterTopics = MewsRepository.filterTopics.first()

                Log.d(TAG, "[FILTERING] Non-blitz count: ${nonBlitzTopics.size}, Blitz count: ${blitzTopics.size}")

                val allRawIds = normalizedTopics.flatMap { it.ids.orEmpty() }.distinct()
                val messageTimeMap = try {
                    if (allRawIds.isNotEmpty()) {
                        MewsRepository.getMessages(ids = allRawIds)?.associate { it.id to it.time } ?: emptyMap()
                    } else emptyMap()
                } catch (_: Exception) { emptyMap() }

                coroutineScope {
                    val blitzDeferred = async(Dispatchers.Default) {
                        mergeInTimeWindows(blitzTopics, messageTimeMap)
                    }

                    val mergedTopicsDeferred = async(Dispatchers.Default) {
                        if (filterTopics && nonBlitzTopics.isNotEmpty()) {
                            try {
                                Log.d(TAG, "[FILTERING] Starting LLM smart merge on raw non-blitz topics...")
                                var result: List<Topics> = smartMergeTopics(nonBlitzTopics, currentLanguage, bannedWords, maxTopics)
                                var attempts = 0
                                while (attempts < MAX_SUMMARIZATION_ATTEMPTS && result.size > maxTopics * 2) {
                                    attempts++
                                    result = smartMergeTopics(result, currentLanguage, bannedWords, maxTopics)
                                }
                                Log.d(TAG, "[FILTERING] LLM smart merge finished. Result size: ${result.size}. Applying final time-window algorithmic merge.")
                                val finalReg = mergeInTimeWindows(
                                    topics = result,
                                    messageTimeMap = messageTimeMap,
                                    enableCrossWindowMerge = false
                                )
                                Log.d(TAG, "[FILTERING] Final merged non-blitz topics size: ${finalReg.size}")
                                finalReg
                            } catch (e: Exception) {
                                Log.w(TAG, "[FILTERING] Smart merge failed, falling back to time-window algorithmic merge", e)
                                mergeInTimeWindows(nonBlitzTopics, messageTimeMap)
                            }
                        } else {
                            Log.d(TAG, "[FILTERING] Filter disabled or no non-blitz topics. Applying time-window algorithmic merge directly.")
                            mergeInTimeWindows(nonBlitzTopics, messageTimeMap)
                        }
                    }

                    val blitzMerged = blitzDeferred.await()
                    Log.d(TAG, "[FILTERING] Blitz after algorithmic merge: ${blitzMerged.size}")

                    val mergedTopics = mergedTopicsDeferred.await()

                    val normalizedFinalTopics = calculateCompositeScores(mergedTopics)
                    val normalizedBlitzMerged = calculateCompositeScores(blitzMerged)

                    val primaryTopics = normalizedFinalTopics.take(maxTopics)
                    val reserveTopics = normalizedFinalTopics.drop(maxTopics).take((maxTopics * 0.5).roundToInt())
                    val blitzRaw = normalizedBlitzMerged.take(maxTopics)

                    val allSelectedTopics = primaryTopics + reserveTopics
                    Log.i(TAG, "[STAGE: SAVING DRAFTS] Selected ${primaryTopics.size} primary, ${reserveTopics.size} reserve and ${blitzRaw.size} blitz topics. Updating Knowledge Graph and saving to DB...")

                    launch(Dispatchers.Default) {
                        try {
                            KnowledgeGraphManager.processTopicsAndBuildGraph(allSelectedTopics.map { it.keywords.toSet() })
                            GraphCache.clear()
                        } catch (e: Exception) {
                            Log.e(TAG, "Process graph failed: ${e.cause}, ${e.message}", e)
                        }
                    }

                    val primaryTopicsWithIdsDeferred = primaryTopics.map { async { saveTopicToDb(it, extractionTime) } }
                    val blitzRawWithIdsDeferred = blitzRaw.map { async { saveTopicToDb(it, extractionTime) } }

                    val primaryTopicsWithIds = primaryTopicsWithIdsDeferred.awaitAll()
                    val blitzRawWithIds = blitzRawWithIdsDeferred.awaitAll()

                    val primaryRawDto = primaryTopicsWithIds.map { it.toRawTopicDto() }
                    val blitzRawDto = blitzRawWithIds.map { it.toRawTopicDto() }

                    MewsRepository.setSourceSummarizingSyncTime()

                    stateManager.updateState { it.copy(
                        updatingState = UpdatingState.SUMMARIZING,
                        stagedRawTopics = emptyList(),
                        primaryTopics = primaryRawDto,
                        reserveTopics = reserveTopics.map { topic -> topic.toRawTopicDto() },
                        blitzTopics = blitzRawDto
                    )
                    }
                    baseProgress = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { baseProgress }
                }
            }

            val summarizationState = stateManager.readState() ?: return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)

            val dbTopics = MewsRepository.getTitlesWithStatus(TitleStatus.PROCESSING.statusId)

            val allTopics = (summarizationState.primaryTopics.map { it.toTopics() } +
                    summarizationState.blitzTopics.map { it.toTopics() } +
                    dbTopics).distinctBy { if (it.id != 0L) it.id else it.title }

            val normalTopics = allTopics.filter { !it.isBlitz }
            val blitzTopics = allTopics.filter { it.isBlitz }
            val currentTopics = allTopics.map { it.id }

            Log.i(TAG, "[STAGE: SUMMARIZATION PREP] Resuming. Primary to process: ${normalTopics.size}, Blitz to process: ${blitzTopics.size}")

            if (normalTopics.isEmpty() && blitzTopics.isEmpty()) {
                Log.i(TAG, "[SUMMARIZATION PREP] Queues are empty after filtering. Aborting.")
                stateManager.clearState()
                safeReadyFunc(readyFunc)
                return SummarizationResult.Failure(SummarizationErrorType.NO_NEWS_TO_ANALYZE)
            }

            MewsRepository.setUpdatingState(UpdatingState.SUMMARIZING)

            val preHistory = try {
                MewsRepository.getRecentTitlesForStorylines(
                    (stateManager.readState()?.timemark
                        ?: System.currentTimeMillis()) - 72 * 3600_000L
                )
                    .filter { it.updateTime != MewsRepository.lastTitlesUpdate.first() }
                    .filter { it.id !in currentTopics }
            } catch (_: Exception) { emptyList() }

            Log.d(TAG, "[SUMMARIZATION PREP] Prehistory entries loaded: ${preHistory.size}")

            val processedTopicsSignatures = Collections.synchronizedList(mutableListOf<Pair<String, List<String>>>())

            totalItemsToProcess = normalTopics.size + blitzTopics.size
            processedItemsCount.set(0)
            val availableProgressSpace = 0.95f - baseProgress

            val reserveMessageIds = summarizationState.reserveTopics.flatMap { it.ids }
            val allRequiredMessageIds = (normalTopics.flatMap { it.ids ?: emptyList() } +
                    blitzTopics.flatMap { it.ids ?: emptyList() } +
                    reserveMessageIds).distinct()
            val rawMessagesList =
                if (allRequiredMessageIds.isNotEmpty()) MewsRepository.getMessages(ids = allRequiredMessageIds)
                    ?: emptyList() else emptyList()

            Log.d(TAG, "[SUMMARIZATION PREP] Fetched ${rawMessagesList.size} raw messages from DB for payloads (including ${reserveMessageIds.size} reserve message IDs).")

            val normalPayloads = buildPayloads(normalTopics, processedTopicsSignatures, rawMessagesList, preHistory)
            val blitzPayloads = buildPayloads(blitzTopics, processedTopicsSignatures, rawMessagesList, preHistory)

            Log.i(TAG, "[STAGE: SUMMARIZATION EXECUTION] Normal payloads: ${normalPayloads.size}, Blitz payloads: ${blitzPayloads.size}")

            val failedTopics = Collections.synchronizedList(mutableListOf<Topics>())
            val successfulNormalIds = Collections.synchronizedSet(mutableSetOf<Long>())

            coroutineScope {
                val parentLinkingJobs = Collections.synchronizedList(mutableListOf<Job>())

                if (normalPayloads.isNotEmpty()) {
                    executionManager.execute<SummaryPayload, ParsedSummaryBatch>(
                        items = normalPayloads,
                        idExtractor = { it.topic.id },
                        textExtractor = { it.contentPayload },
                        batchMode = BatchMode.BASE_TOPICS,
                        promptBuilder = { batch -> PromptFactory.buildSummaryPrompt(batch, currentLanguage, bannedWords, isBlitz = false) },
                        responseParser = { response, batch ->
                            ResponseParser.parseSummaryResponse(response, batch, llm, bannedWords) {
                                hasBlockedContent.set(true)
                            }
                        },
                        onBatchSuccess = { batch, _, batchResult ->
                            val results = batchResult.results
                            val noUpdateIds = batchResult.noUpdateTopicIds
                            Log.d(TAG, "[SUMMARIZATION] Normal batch success. Generated ${results.size} summaries, ${noUpdateIds.size} NO_UPDATE.")
                            val jobs = processSummaryResults(this, results, preHistory, processedTopicsSignatures, summarizationState.timemark)
                            parentLinkingJobs.addAll(jobs)
                            val finishedIds = results.map { it.payload.topic.id }.toSet()
                            successfulNormalIds.addAll(finishedIds)

                            val resolvedIds = finishedIds + noUpdateIds
                            val omittedTopics = batch.map { it.topic }.filter { it.id !in resolvedIds }
                            if (omittedTopics.isNotEmpty()) {
                                Log.w(TAG, "[SUMMARIZATION] Model omitted ${omittedTopics.size} topics from response. Adding to failed list for retry.")
                                failedTopics.addAll(omittedTopics)
                            }
                            stateManager.updateState {
                                it.copy(
                                    primaryTopics = it.primaryTopics.filter { topic -> topic.id !in resolvedIds }
                                )
                            }
                            val currentCount = processedItemsCount.addAndGet(batch.size)
                            MewsRepository.setUpdatingProgress(baseProgress + ((currentCount.toFloat() / totalItemsToProcess) * availableProgressSpace))
                        },
                        onBatchFailure = { batch, _ ->
                            Log.w(TAG, "[SUMMARIZATION] Normal batch failed (Final). ${batch.size} topics moved to failed list.")
                            failedTopics.addAll(batch.map { it.topic })
                        }
                    )
                }

                if (blitzPayloads.isNotEmpty()) {
                    executionManager.execute<SummaryPayload, ParsedSummaryBatch>(
                        items = blitzPayloads,
                        idExtractor = { it.topic.id },
                        textExtractor = { it.contentPayload },
                        batchMode = BatchMode.BLITZ_TOPICS,
                        promptBuilder = { batch -> PromptFactory.buildSummaryPrompt(batch, currentLanguage, bannedWords, isBlitz = true) },
                        responseParser = { response, batch ->
                            ResponseParser.parseSummaryResponse(response, batch, llm, bannedWords) {
                                hasBlockedContent.set(true)
                            }
                        },
                        onBatchSuccess = { batch, _, batchResult ->
                            val results = batchResult.results
                            val noUpdateIds = batchResult.noUpdateTopicIds
                            Log.d(TAG, "[SUMMARIZATION] Blitz batch success. Generated ${results.size} summaries, ${noUpdateIds.size} NO_UPDATE.")
                            val jobs = processSummaryResults(this, results, preHistory, processedTopicsSignatures, summarizationState.timemark)
                            parentLinkingJobs.addAll(jobs)
                            val finishedIds = results.map { it.payload.topic.id }.toSet()

                            val resolvedIds = finishedIds + noUpdateIds
                            val omittedBlitz = batch.map { it.topic }.filter { it.id !in resolvedIds }
                            if (omittedBlitz.isNotEmpty()) {
                                Log.w(TAG, "[SUMMARIZATION] Model omitted ${omittedBlitz.size} blitz topics. Adding to failed list for retry.")
                                failedTopics.addAll(omittedBlitz)
                            }
                            stateManager.updateState {
                                it.copy(
                                    blitzTopics = it.blitzTopics.filter { topic -> topic.id !in resolvedIds }
                                )
                            }
                            val currentCount = processedItemsCount.addAndGet(batch.size)
                            MewsRepository.setUpdatingProgress(baseProgress + ((currentCount.toFloat() / totalItemsToProcess) * availableProgressSpace))
                        },
                        onBatchFailure = { batch, _ ->
                            Log.w(TAG, "[SUMMARIZATION] Blitz batch failed (Final). ${batch.size} topics moved to failed list.")
                            failedTopics.addAll(batch.map { it.topic })
                        }
                    )
                }

                val missingSlots = maxTopics - successfulNormalIds.size
                val reserveDtoList = stateManager.readState()?.reserveTopics ?: emptyList()

                if (missingSlots > 0 && reserveDtoList.isNotEmpty()) {
                    val neededCount = kotlin.math.ceil(missingSlots * 1.5).toInt().coerceAtMost(reserveDtoList.size)
                    val candidateReserve = reserveDtoList.take(neededCount).map { it.toTopics() }
                    Log.i(TAG, "[RESERVE ORCHESTRATION] Requesting $neededCount topics from reserve to compensate $missingSlots missing slots.")

                    val reserveWithIds = candidateReserve.map { saveTopicToDb(it, summarizationState.timemark) }
                    val reservePayloads = buildPayloads(reserveWithIds, processedTopicsSignatures, rawMessagesList, preHistory)

                    if (reservePayloads.isNotEmpty()) {
                        executionManager.execute<SummaryPayload, ParsedSummaryBatch>(
                            items = reservePayloads,
                            idExtractor = { it.topic.id },
                            textExtractor = { it.contentPayload },
                            batchMode = BatchMode.BASE_TOPICS,
                            promptBuilder = { batch -> PromptFactory.buildSummaryPrompt(batch, currentLanguage, bannedWords, isBlitz = false) },
                            responseParser = { response, batch ->
                                ResponseParser.parseSummaryResponse(response, batch, llm, bannedWords) {
                                    hasBlockedContent.set(true)
                                }
                            },
                            onBatchSuccess = { batch, _, batchResult ->
                                val availableSlots = maxTopics - successfulNormalIds.size
                                val validResults = batchResult.results.take(availableSlots.coerceAtLeast(0))
                                val jobs = processSummaryResults(this, validResults, preHistory, processedTopicsSignatures, summarizationState.timemark)
                                parentLinkingJobs.addAll(jobs)

                                val finishedIds = validResults.map { it.payload.topic.id }.toSet()
                                successfulNormalIds.addAll(finishedIds)

                                val unusedDraftIds = batch.map { it.topic.id }.filter { it !in finishedIds }
                                unusedDraftIds.forEach { MewsRepository.deleteTitleById(it) }
                            },
                            onBatchFailure = { batch, _ ->
                                batch.forEach { MewsRepository.deleteTitleById(it.topic.id) }
                            }
                        )
                    }
                    stateManager.updateState { it.copy(reserveTopics = emptyList()) }
                }

                if (parentLinkingJobs.isNotEmpty()) {
                    Log.i(TAG, "Awaiting completion of ${parentLinkingJobs.size} parent linking and DB update tasks...")
                    MewsRepository.setShowSummaryTooltip(true)
                    parentLinkingJobs.joinAll()
                }
            }

            val dbRemaining = MewsRepository.getTitlesWithStatus(TitleStatus.PROCESSING.statusId)
            dbRemaining.forEach { dbTopic ->
                if (failedTopics.find { it.id == dbTopic.id || it.title == dbTopic.title } == null) failedTopics.add(dbTopic)
            }

            if (failedTopics.isNotEmpty()) {
                val currentAttempt = summarizationState.attempt
                Log.w(TAG, "[SUMMARIZATION] Pipeline completed with ${failedTopics.size} failed topics. Preserving in StateManager.")
                stateManager.updateState { it.copy(
                    primaryTopics = failedTopics.filter { t -> !t.isBlitz }.map { topic -> topic.toRawTopicDto() },
                    blitzTopics = failedTopics.filter { t -> t.isBlitz }.map { topic -> topic.toRawTopicDto() },
                    updatingState = UpdatingState.SUMMARIZING,
                    attempt = currentAttempt + 1
                )
                }
                if (currentAttempt >= MAX_SUMMARIZATION_ATTEMPTS) {
                    stateManager.clearState()
                    dbRemaining.forEach { MewsRepository.deleteTitleById(it.id) }
                    Log.w(TAG, "Deleted ${dbRemaining.size} failed topics from DB.")
                }
                if (failedTopics.isNotEmpty()) {
                    MewsRepository.setFailedTitles(failedTopics.size)
                }
                MewsRepository.setLastTitlesUpdate(summarizationState.timemark)
                safeReadyFunc(readyFunc)

                val errorType = if (hasBlockedContent.get()) {
                    SummarizationErrorType.CONTENT_BLOCKED
                } else {
                    SummarizationErrorType.SUMMARIZE_TOPICS_FAILED
                }
                return if (currentAttempt < MAX_SUMMARIZATION_ATTEMPTS) SummarizationResult.Failure(errorType)
                else SummarizationResult.Success
            }

            MewsRepository.setLastTitlesUpdate(summarizationState.timemark)
            MewsRepository.setShowSummaryTooltip(false)
            Log.i(TAG, "=== PIPELINE FINISHED SUCCESSFULLY ===")

            KeywordStatsRepository.deleteClusters(summarizationState.timemark)
            stateManager.clearState()
            safeReadyFunc(readyFunc)
            return SummarizationResult.Success
        } catch (e: GeminiException) {
            Log.e(TAG, "Pipeline failed with GeminiException: ${e.errorType}", e)
            safeReadyFunc(readyFunc)
            return SummarizationResult.Failure(e.errorType, e)
        } catch (e: Exception) {
            if (e is CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable) {
                    val wasManual = MewsRepository.stoppedManually.first()
                    val keepProgress = MewsRepository.saveOnCancel.first()

                    if (wasManual && !keepProgress) {
                        Log.i(TAG, "Cancelled manually without saving: clearing state and removing drafts.")
                        stateManager.clearState()
                        MewsRepository.deleteTitlesWithStatus(TitleStatus.PROCESSING)
                    }
                    MewsRepository.setUpdatingState(UpdatingState.DEFAULT)
                }
                Log.w(TAG, "Pipeline job cancelled by user/coroutineScope.")
                throw e
            }
            Log.e(TAG, "Pipeline failed with unknown exception", e)
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
        Log.d(TAG, "[DB DRAFT] Saved draft topic '${t.title}' [ID: $generatedId, isBlitz: ${t.isBlitz}, weight: ${t.weight}]")
        return t.copy(id = generatedId)
    }

    private fun areKeywordListsSimilar(
        words1: List<String>,
        words2: List<String>,
        wordSimilarityThreshold: Float = 0.8f,
        clusterMatchRate: Double = 0.8
    ): Boolean {
        if (words1.isEmpty() || words2.isEmpty()) return false
        val minSize = minOf(words1.size, words2.size)

        val matches = words1.count { w1 ->
            words2.any { w2 -> TextComparator.areSimilar(w1, w2, wordSimilarityThreshold) }
        }

        return (matches.toDouble() / minSize) >= clusterMatchRate
    }


    private suspend fun compareTopics(
        topicTitle: String, topicKeywords: List<String>, otherTitle: String, otherKeywords: List<String>,
        topicText: String? = null, otherText: String? = null
    ): Double {
        if (topicKeywords.isEmpty() || otherKeywords.isEmpty()) return 0.0
        if (topicTitle.isEmpty() || otherTitle.isEmpty()) return 0.0

        val expandedTopicKeywords = topicKeywords.toMutableSet()
        for (kw in topicKeywords) expandedTopicKeywords += GraphCache.getRelatedEntities(kw, 0.9)

        val expandedOtherKeywords = otherKeywords.toMutableSet()
        for (kw in otherKeywords) expandedOtherKeywords += GraphCache.getRelatedEntities(kw, 0.9)

        val cleanTopicTitle = topicTitle.lowercase().trim()
        val cleanOtherTitle = otherTitle.lowercase().trim()

        val titlesThreshold = if (cleanTopicTitle == cleanOtherTitle) {
            1.0
        } else {
            val baseTitlesScore = TextComparator.countThreshold(cleanTopicTitle, cleanOtherTitle)
            val topicTitleTokens = TextComparator.tokenize(topicTitle)
            val otherTitleTokens = TextComparator.tokenize(otherTitle)
            TextComparator.combineWithSemanticScore(
                baseScore = baseTitlesScore,
                tokens1 = topicTitleTokens,
                tokens2 = otherTitleTokens
            ).toDouble()
        }

        var kwMatches = 0
        for (tk in expandedTopicKeywords) {
            for (hk in expandedOtherKeywords) {
                if (tk.equals(hk, ignoreCase = true) || TextComparator.areSimilar(tk.lowercase(), hk.lowercase(), 0.7f)) {
                    kwMatches++
                    break
                }
            }
        }

        val keywordScore = kwMatches.toDouble() / (minOf(
            expandedTopicKeywords.size,
            expandedOtherKeywords.size
        ).coerceAtLeast(1))

        if (keywordScore < 0.2 || titlesThreshold < 0.55f) return 0.0
        if (keywordScore > 0.9 || (titlesThreshold > 0.65 && keywordScore > 0.5)) return 1.0

        val hasSummary = !topicText.isNullOrBlank() && !otherText.isNullOrBlank()
        val summaryThreshold = if (hasSummary) {
            val baseScore = TextComparator.countThreshold(
                TextSanitizer.sanitize(topicText.lowercase()),
                TextSanitizer.sanitize(otherText.lowercase())
            )
            val topicTextTokens = TextComparator.tokenize(topicText)
            val historySummaryTokens = TextComparator.tokenize(otherText)
            val score = TextComparator.combineWithSemanticScore(
                baseScore = baseScore,
                tokens1 = topicTextTokens,
                tokens2 = historySummaryTokens
            ).toDouble()
            (score - 0.6).coerceIn(0.0, 1.0) * 2.5
        } else 0.0
        if (summaryThreshold > 0.7) return 1.0

        val totalWeight = if (hasSummary) 6.0 else 3.0
        println("Title: $otherTitle. Keywords score: $keywordScore, titles: $titlesThreshold, summary: $summaryThreshold. Total: ${(keywordScore * 2.0 + titlesThreshold * 1.0 + summaryThreshold * 3.0) / totalWeight}.")
        return (keywordScore * 2.0 + titlesThreshold * 1.0 + summaryThreshold * 3.0) / totalWeight
    }

    private suspend fun buildPayloads(
        topics: List<Topics>,
        processedTopicsSignatures: List<Pair<String, List<String>>>,
        rawMessagesList: List<Message>,
        preHistory: List<TitleEntity>,
        maxTokens: Int = TOPIC_TOKEN_LIMIT,
        charLimit: Int = SINGLE_NEWS_CHAR_LIMIT
    ): List<SummaryPayload> = coroutineScope {
        topics.map { topic ->
            async(Dispatchers.Default) {
                buildSingleTopicPayload(topic, processedTopicsSignatures, rawMessagesList, preHistory, maxTokens, charLimit)
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun buildSingleTopicPayload(
        topic: Topics,
        processedTopicsSignatures: List<Pair<String, List<String>>>,
        rawMessagesList: List<Message>,
        preHistory: List<TitleEntity>,
        maxTokens: Int,
        charLimit: Int
    ): SummaryPayload? {
        val isDuplicate = processedTopicsSignatures.any { (procTitle, procKeywords) ->
            compareTopics(
                topicTitle = topic.title,
                topicKeywords = topic.keywords,
                otherTitle = procTitle,
                otherKeywords = procKeywords
            ) >= MATCH_RATE
        }

        if (isDuplicate) {
            Log.d(TAG, "[PAYLOAD BUILDER] Dropped topic [${topic.id}] '${topic.title}' as DUPLICATE.")

            val existingInDb = try { MewsRepository.getTitleById(topic.id) } catch (_: Exception) { null }
            val isAlreadyWritten = existingInDb != null &&
                    existingInDb.summary.isNotBlank() &&
                    existingInDb.status == TitleStatus.DEFAULT.statusId

            if (!isAlreadyWritten) {
                MewsRepository.deleteTitleById(topic.id)
            } else {
                Log.d(TAG, "[PAYLOAD BUILDER] Topic [${topic.id}] already has summary in DB, preserving record.")
            }

            stateManager.updateState { s ->
                s.copy(
                    primaryTopics = s.primaryTopics.filter { it.id != topic.id },
                    blitzTopics = s.blitzTopics.filter { it.id != topic.id }
                )
            }
            processedItemsCount.incrementAndGet()
            return null
        }

        val messageIds = topic.ids ?: emptyList()
        val fetchedMessages = if (messageIds.isNotEmpty()) {
            rawMessagesList.filter { it.id in messageIds }
        } else emptyList()
        val allMessagesTime = fetchedMessages.minOfOrNull { it.time }

        if (fetchedMessages.isEmpty()) {
            Log.w(TAG, "[PAYLOAD BUILDER] Dropped topic [${topic.id}] '${topic.title}': No messages found in DB payload pool.")
            MewsRepository.deleteTitleById(topic.id)
            stateManager.updateState { s ->
                s.copy(
                    primaryTopics = s.primaryTopics.filter { it.id != topic.id },
                    blitzTopics = s.blitzTopics.filter { it.id != topic.id }
                )
            }
            return null
        }

        val finalPayloadMessages = filterTopicMessages(
            messages = fetchedMessages,
            maxMessages = if (topic.isBlitz) 5 else TOPIC_MAX_MESSAGES_LIMIT,
            maxTokens = maxTokens,
            charLimit = charLimit
        )

        var contentPayload = finalPayloadMessages.joinToString("\n") { "— ${it.cleanText}" }
        var requiresUpdate = false

        if (!topic.isBlitz) {
            val bestMatchId = findBestMatch(topic, null, preHistory)
            if (bestMatchId != null) {
                val matchedItem = preHistory.find { it.id == bestMatchId }
                if (matchedItem != null) {
                    requiresUpdate = !matchedItem.isRead
                    contentPayload = "ПРЕДЫДУЩИЙ КОНТЕКСТ СЮЖЕТА:\n${matchedItem.summary}\n\n$contentPayload"
                    Log.d(TAG, "[PAYLOAD BUILDER] Topic [${topic.id}] '${topic.title}' matched ancestor [$bestMatchId]. Requires update: $requiresUpdate")
                }
            }
        }

        return SummaryPayload(topic, finalPayloadMessages, contentPayload, requiresUpdate, allMessagesTime = allMessagesTime)
    }

    private fun filterTopicMessages(
        messages: List<Message>,
        maxMessages: Int,
        maxTokens: Int,
        charLimit: Int
    ): List<Message> {
        if (messages.isEmpty()) return emptyList()
        if (messages.size == 1) {
            val truncated = messages[0].cleanText.take(charLimit)
            return listOf(messages[0].copy(cleanText = truncated))
        }

        val prepared = messages
            .filter { it.cleanText.length >= 60 }
            .ifEmpty { messages }
            .sortedByDescending { it.cleanText.length }
            .map { msg ->
                val truncated = msg.cleanText.take(charLimit)
                PreparedMessage(
                    message = msg.copy(cleanText = truncated),
                    sanitizedText = TextSanitizer.sanitize(truncated),
                    estimatedTokens = TokenEstimator.estimate(truncated)
                )
            }
            .toMutableList()

        var threshold = 0.85f
        val minThreshold = 0.45f
        val step = 0.05f

        while ((prepared.size > maxMessages || prepared.sumOf { it.estimatedTokens } > maxTokens) &&
            prepared.size > 2 &&
            threshold >= minThreshold
        ) {
            var i = 0
            while (i < prepared.size) {
                var j = i + 1
                while (j < prepared.size) {
                    if (prepared.size <= 2) break
                    val isSimilar = TextComparator.areSimilar(
                        prepared[i].sanitizedText,
                        prepared[j].sanitizedText,
                        threshold
                    )
                    if (isSimilar) {
                        prepared.removeAt(j)
                    } else {
                        j++
                    }
                }
                i++
            }
            threshold -= step
        }

        val result = mutableListOf<Message>()
        var accumulatedTokens = 0

        for (item in prepared) {
            if (result.size >= maxMessages) break
            if (accumulatedTokens + item.estimatedTokens > maxTokens && result.isNotEmpty()) {
                break
            }
            result.add(item.message)
            accumulatedTokens += item.estimatedTokens
        }

        return if (result.isEmpty()) listOf(prepared.first().message) else result
    }

    private fun processSummaryResults(
        scope: CoroutineScope,
        results: List<SummaryResult>,
        preHistory: List<TitleEntity>,
        signatures: MutableList<Pair<String, List<String>>>,
        extractionTime: Long
    ): List<Job> {
        val jobs = mutableListOf<Job>()

        for (item in results) {
            val summary = item.summary
            val newTitle = item.title
            val macroTag = item.macroTag
            val (topic, suitableMessages, _) = item.payload

            if (summary.isBlank() || summary.length <= 15) {
                Log.d(TAG, "[RESULT PROCESSOR] Dropped summary for topic [${topic.id}] '${topic.title}'. Reason: blank or length <= 15.")
                continue
            }

            var bestMatchId: Long? = null
            val newTimeVal = item.payload.allMessagesTime ?: suitableMessages.minOfOrNull { it.time } ?: extractionTime

            val updateJob = scope.launch {
                MewsRepository.updateTitle(
                    id = topic.id,
                    newEventTime = newTimeVal,
                    newTitle = newTitle,
                    macroTag = macroTag,
                    isBlitz = topic.isBlitz,
                    summary = summary
                )
            }
            jobs.add(updateJob)
            Log.i(TAG, "[RESULT PROCESSOR] Topic [${topic.id}] created new independent DB entry (isBlitz: ${topic.isBlitz}).")

            signatures.add(Pair(newTitle, topic.keywords))

            if (!topic.isBlitz) {
                val job = scope.launch(Dispatchers.Default) {
                    val filteredHistory = preHistory.filter { historyItem ->
                        historyItem.id != topic.id && !(historyItem.eventTime > newTimeVal || (historyItem.eventTime == newTimeVal && historyItem.id >= topic.id))
                    }
                    bestMatchId = findBestMatch(topic, summary, filteredHistory)

                    val parentTopic = preHistory.find { it.id == bestMatchId }

                    if (parentTopic != null) {
                        MewsRepository.linkTopics(topic.id, parentTopic.id)
                        Log.i(TAG, "[RESULT PROCESSOR] Topic [${topic.id}] created new DB entry with ancestor link to [$bestMatchId].")
                    }
                }
                jobs.add(job)
            }
        }

        return jobs
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

        Log.d(TAG, "[BLITZ DEDUP] Topic '${topic.title}' reduced messages from ${topicMessages.size} to ${finalIds.size}")
        return topic.copy(ids = finalIds)
    }

    private suspend fun mergeInTimeWindows(
        topics: List<Topics>,
        messageTimeMap: Map<Long, Long>,
        windowMs: Long = 4 * 3600_000L,
        enableCrossWindowMerge: Boolean = true
    ): List<Topics> {
        if (topics.size <= 1) return topics

        val partitioned = topics.groupBy { topic ->
            val earliestTime = topic.ids?.mapNotNull { messageTimeMap[it] }?.minOrNull() ?: 0L
            if (earliestTime > 0L) earliestTime / windowMs else 0L
        }

        if (partitioned.size <= 1) {
            val merged = mutableListOf<Topics>()
            mergeIntoGlobal(topics, merged)
            return merged
        }

        val windowMerged = mutableListOf<Topics>()
        for ((_, windowTopics) in partitioned) {
            val localMerged = mutableListOf<Topics>()
            mergeIntoGlobal(windowTopics, localMerged)
            windowMerged.addAll(localMerged)
        }

        if (!enableCrossWindowMerge) {
            Log.d(TAG, "[MERGE WINDOWS] Cross-window merge disabled. Retained ${windowMerged.size} topics across ${partitioned.size} time windows.")
            return windowMerged
        }

        val finalMerged = mutableListOf<Topics>()
        mergeIntoGlobal(windowMerged, finalMerged)
        return finalMerged
    }

    private suspend fun calculateCompositeScores(topics: List<Topics>): List<Topics> {
        if (topics.isEmpty()) return emptyList()
        val burstClusters = try {
            KeywordStatsRepository.getBurstClusters()
        } catch (_: Exception) { emptyList() }

        val ln15 = kotlin.math.ln(15.0)

        return topics.map { topic ->
            val wLlm = topic.weight.coerceIn(1, 10).toDouble()
            val n = (topic.ids?.size ?: 1).coerceAtLeast(1).toDouble()
            val sVol = kotlin.math.min(10.0, 1.0 + 9.0 * (kotlin.math.ln(n) / ln15).coerceAtLeast(0.0))

            val peakZ = burstClusters
                .filter { cluster ->
                    cluster.keywords.any { ck ->
                        topic.keywords.any { tk -> TextComparator.areSimilar(ck, tk, 0.8f) }
                    }
                }
                .maxOfOrNull { it.peakZScore } ?: 0.0

            val sBurst = kotlin.math.min(10.0, kotlin.math.max(0.0, peakZ) * 2.0)
            val finalScore = (wLlm * 0.35) + (sVol * 0.45) + (sBurst * 0.20)
            val roundedWeight = finalScore.roundToInt().coerceIn(1, 10)
            topic.copy(weight = roundedWeight) to finalScore
        }.sortedByDescending { it.second }
            .map { it.first }
    }

    private suspend fun mergeIntoGlobal(newTopics: List<Topics>, globalCache: MutableList<Topics>) {
        Log.d(TAG, "[GLOBAL MERGE] Merging ${newTopics.size} new topics into global cache (Current size: ${globalCache.size})")
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

                val combinedKeywords = mutableListOf<String>()
                (existing.keywords + candidate.keywords).forEach { word ->
                    if (combinedKeywords.none { TextComparator.areSimilar(it, word, 0.85f) }) combinedKeywords.add(word)
                }
                val fromBurst = existing.fromBurst || candidate.fromBurst

                globalCache[existingIndex] = existing.copy(
                    ids = combinedIds,
                    weight = combinedIds.size,
                    keywords = combinedKeywords,
                    isBlitz = existing.isBlitz,
                    fromBurst = fromBurst
                )
            } else {
                globalCache.add(candidate.copy(weight = candidate.ids?.size ?: 1))
            }
        }
        Log.d(TAG, "[GLOBAL MERGE] Merge complete. Global cache size now: ${globalCache.size}")
    }

    private suspend fun smartMergeTopics(topics: List<Topics>, lang: String, banned: String, maxTopics: Int): List<Topics> {
        if (topics.isEmpty()) return emptyList()

        Log.d(TAG, "[SMART MERGE] Starting execution manager for ${topics.size} topics...")
        val mergedResults = mutableListOf<Topics>()
        val targetLimit = maxTopics * 2

        executionManager.execute<Topics, List<Topics>>(
            items = topics,
            idExtractor = { it.id },
            textExtractor = { it.title + " " + it.keywords.joinToString(" ") },
            batchMode = BatchMode.RAW_ITEMS,
            promptBuilder = { batch ->
                val indexedInput = batch.mapIndexed { index, t ->
                    JSONObject().apply {
                        put("ix", index)
                        put("t", t.title)
                        put("kw", t.keywords.joinToString(", "))
                        put("w", maxOf(t.weight, t.ids?.size ?: 1))
                        put("ob", t.fromBurst)
                    }
                }
                val jsonInput = JSONArray(indexedInput).toString()

                """
                Задача: Склей полные дубликаты и выдели наиболее значимые независимые новости.
                
                ПРАВИЛА:
                1. СЛИЯНИЕ ДУБЛИКАТОВ: Объединяй индексы в "src" (src: [ix1, ix2]) ТОЛЬКО если новости описывают один и тот же конкретный инцидент или прямое развитие одной ситуации.
                2. СТРОГИЙ ЗАПРЕТ ДАЙДЖЕСТОВ: Категорически ЗАПРЕЩЕНО объединять независимые события по общей тематике, сфере или городу. Никаких сборных тем («Новости дня», «События в регионе», «Сводка происшествий»). Каждое независимое событие — строго отдельный объект.
                3. ОТБОР И ФИЛЬТРАЦИЯ (НЕ БОЛЕЕ $targetLimit ТЕМ):
                   - Оставляй только важные, резонансные события. Мелкий инфошум, бытовые единичные случаи и спам просто ВЫБРАСЫВАЙ (не включай в ответ).
                   - Если независимых событий больше $targetLimit, отдавай предпочтение темам с наибольшим весом (w) и просто ОТБРАСЫВАЙ менее важные.
                   - Запрещено сжимать или объединять независимые события между собой ради попадания в лимит.
                   - Разрешено пересчитывать вес тем внутри пакета.
                   - Темы с флагом ob считай приоритетными. Их вес в выводе умножай на 1.3, но максимум 10.
                4. ОДИНОЧНЫЕ СОБЫТИЯ: Если важное событие не имеет дубликатов в списке — возвращай его отдельным объектом: "src": [ix].
                5. ЗАПРЕЩЕННЫЕ ТЕМЫ: Если новость относится к запрещенным темам ('$banned') — удали её и не включай в итоговый список.
                6. СТИЛЬ: Заголовки в стиле Smart Casual, точно отражающие суть конкретного инцидента.
                
                Ввод: [{"ix": 0, "t": "...", "kw": "...", "w": 5, "ob": "..."}]
                Формат ответа (СТРОГО JSON массив):
                [{"title": "Заголовок конкретного события", "src": [0, 2], "weight": 9}]
                
                Язык заголовков и ответа: $lang.
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
                            val w = maxOf(obj.optInt("weight", 5), mergedIds.size)
                            batchMerged.add(Topics(obj.getString("title"), mergedIds.toList(), w, keywords = mergedKeywords.take(8).toList(), isBlitz = false))
                        }
                    } catch (_: Exception) {}
                }
                batchMerged
            },
            onBatchSuccess = { _, _, parsedTopics ->
                Log.d(TAG, "[SMART MERGE] Batch success. Produced ${parsedTopics.size} merged topics.")
                mergedResults.addAll(parsedTopics)
            },
            onBatchFailure = { batch, _ ->
                Log.w(TAG, "[SMART MERGE] Batch failed (Final). Preserving ${batch.size} original topics without merging.")
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
                Максимальное количество тем: $maxLimit. Если событий больше — оставь самые резонансные и значимые.
                
                ИНЖЕКЦИЯ ПРИОРИТЕТОВ:
                $burstKeywords
                События, относящиеся к приоритетным сюжетам выше, выделяй ОБЯЗАТЕЛЬНО. Для них указывай флаг fromBurst = true, для остальных - fromBurst = false.
                
                ПРАВИЛА КЛАСТЕРИЗАЦИИ И АТОМАРНОСТИ:
                1. 1 ТЕМА = 1 ИНЦИДЕНТ: Категорически ЗАПРЕЩЕНО создавать дайджесты и зонтичные темы («Политика», «События на фронте», «Заявления властей», «Новости IT»). Каждое событие, удар, заявление или закон — отдельная тема.
                2. СЮЖЕТНАЯ СВЯЗЬ: Объединяй новости в одну тему (несколько id в массиве) ТОЛЬКО при наличии прямой причинно-следственной связи (инцидент + реакция + официальное последствие). Если связи нет — это разные темы.
                
                ФИЛЬТРАЦИЯ ИНФОШУМА (ВЫБРАСЫВАЙ БЕЗЖАЛОСТНО):
                - Полностью игнорируй спам, розыгрыши, рекламу и запрещенные темы: '$banned'.
                - ИГНОРИРУЙ бытовой лайфстайл, псевдонауку («ученые выяснили...», советы врачей, бруксизм, диеты), курьезы, гороскопы и бытовые ДТП/криминал без резонанса. Не включай их ни в основные темы, ни в блиц — просто удаляй.
                
                РАСПРЕДЕЛЕНИЕ ПО ТИПАМ (isBlitz) И ВЕСУ (weight):
                - weight (1-10): Оценивай реальную общественную значимость.
                  * 8–10: Международные события, войны, крупные законы, теракты, макроэкономика.
                  * 5–7: Заметные региональные/отраслевые события, крупные релизы, значимые кадровые перестановки.
                  * 1–4: Локальные точечные происшествия, небольшие апдейты.
                - isBlitz (true/false):
                  * false: Полноценное резонансное событие, по которому есть или ожидается развитие сюжета (weight от 5 до 10).
                  * true: Только короткие, изолированные, но общественно ВАЖНЫЕ факты (точечное назначение, сухой отчет, разовый локальный закон), не требующие длинной статьи.
                
                ИЗВЛЕЧЕНИЕ СУЩНОСТЕЙ (NER):
                Для каждой темы извлеки сущности по 6 категориям: 'persons', 'locations', 'organizations', 'events', 'products_tech', 'laws_regulations'.
                
                ФОРМАТ ОТВЕТА (СТРОГО JSON):
                [
                  {
                    "title": "Точный заголовок конкретного события", 
                    "id": [101, 105], 
                    "keywords": ["тег1", "тег2", "тег3"], 
                    "weight": 8, 
                    "isBlitz": false,
                    "fromBurst": false,
                    "entities": {
                       "persons": [{"entity": "Имя", "context": "фрагмент текста"}],
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
                    put("id", item.topic.id)
                    put("news_content", item.contentPayload)
                    if (!isBlitz) put("requires_update_header", item.requiresUpdateHeader)
                }
            })

            return if (isBlitz) {
                """
                Ты — аналитик. Твоя цель — написать краткий дайджест микроновостей. Каждая тема - 1-2 предложения.
                1. ДОСТОВЕРНОСТЬ: Строй обзор исключительно на фактах из поля news_content. Запрещено генерировать несуществующие данные.
                2. УМНАЯ ФИЛЬТРАЦИЯ: Если спам/запрет ('$banned') — вырезай. Если нарушена безопасность — верни "text": "REJECTED". Если новость уже описана без изменений — верни "text": "NO_UPDATE".
                3. ЯЗЫК: $lang. Точность дат, сумм, прямая речь в кавычках.
                4. СТРОГОЕ СООТВЕТСТВИЕ 1-К-1 (КРИТИЧЕСКИ ВАЖНО):
                   - В ответе ДОЛЖНО БЫТЬ РОВНО ${batch.size} JSON-объектов — СТРОГО ПО ОДНОМУ ОБЪЕКТУ НА КАЖДЫЙ ВХОДНОЙ ID!
                   - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО создавать несколько объектов с одинаковым ID. Если по одному ID передано несколько микроновостей, оформи их единым маркированным списком внутри поля "text" этого же объекта.
                   - Запрещено пропускать переданные ID.
                5.ФОРМАТИРОВАНИЕ: Разрешено использовать markdown, но маркированные списки ЗАПРЕЩЕНЫ.
                ФОРМАТ (СТРОГО JSON):
                [{"id": <id>, "title": "<Заголовок>", "text": "<текст>"}]
                Ввод: $jsonInput
                """.trimIndent()
                    } else {
                        """
                Ты — профессиональный журналист-аналитик ведущего новостного издания. Твоя задача — формировать качественные, фактурные и глубокие новостные материалы.
                
                ИНСТРУКЦИИ:
                1. СТРУКТУРА И ОБЪЕМ (МАСШТАБИРУЮТСЯ ПО ОБЪЕМУ ФАКТУРЫ):
                   - Не сжимай насыщенный фактами материал в 1–2 куцых предложения. Не выбрасывай цифры, таймлайны и аргументы ради экономии места.
                   - Если фактуры немного (1 новость): пиши 1–2 емких, плотных абзаца без пустой воды.
                   - Если фактуры достаточно: пиши разбор из 2–3 абзацев (суть события -> ключевые детали и контекст -> последствия/реакции).
                   - Если информации много (крупный сюжет, цепочка сообщений, контекст сюжета): пиши полноценный лонгрид (от 3–4 абзацев и более), подробно раскрывая предысторию, хронологию и позиции сторон.
                   - Если requires_update_header = true: пиши ТОЛЬКО дополнение (свежие факты, отсутствующие в предыстории).
                2. СТИЛЬ: Smart Casual, связный глубокий нарратив, строгая объективность. Ключевые цитаты сохраняй прямой речью в кавычках («»).
                3. ДОСТОВЕРНОСТЬ: Строй текст исключительно на news_content.
                4. ТОЧНОСТЬ ДАННЫХ: Даты, суммы, имена и статистика — строго без округлений и обобщений.
                5. УМНАЯ ФИЛЬТРАЦИЯ: Игнорируй ('$banned'). Если материал нарушает политики безопасности — верни "text": "REJECTED". Если в материале нет никакой новой фактуры по сравнению с 'ПРЕДЫДУЩИЙ КОНТЕКСТ СЮЖЕТА' — верни "text": "NO_UPDATE".
                6. МАКРОКАТЕГОРИЯ: 1 верхнеуровневая категория (Политика, Экономика, Технологии и т.д.).
                7. ЗАГОЛОВОК: Конкретный, отражающий суть события, без кликбейта.
                8. ПОЛНОТА ОТВЕТА (СТРОГО): В ответе ДОЛЖНО БЫТЬ РОВНО ${batch.size} объектов — по одному на каждый входной id.
                
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

                    val fromBurst = obj.optBoolean("fromBurst", false)

                    val mappedKeywords = keywordsList.map { it.lowercase() }
                    val isBlitz = obj.optBoolean("isBlitz", false) || mappedKeywords.contains("другое") || mappedKeywords.contains("other") || mappedKeywords.contains("blitz")

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
                                        it.cleanText.lowercase().contains(contextStr)
                                    }
                                    if (existsInOriginalText) {
                                        extractedEntities.getOrPut(category) { mutableListOf() }.add(entityName)
                                    }
                                }
                            }
                        }

                        keywordsList.forEach { kw ->
                            val existsInOriginalText = batch.filter { it.id in fullIdList }.any {
                                it.cleanText.lowercase().contains(kw.lowercase())
                            }
                            if (existsInOriginalText) {
                                validWordCounts[kw] = validWordCounts.getOrDefault(kw, 0.0) + 1.0
                            }
                        }
                    }

                    topics.add(Topics(title, fullIdList, w,
                        keywords = keywordsList, isBlitz = isBlitz, fromBurst = fromBurst))
                } catch (_: Exception) {}
            }

            Log.d("ResponseParser", "[PARSER EXTRACTION] Parsed ${topics.size} topics. Entities count: ${extractedEntities.values.sumOf { it.size }}")

            val minRequiredTopics = (batch.size * 0.1).toInt().coerceAtLeast(1)
            if (topics.size < minRequiredTopics) {
                throw GeminiException(
                    SummarizationErrorType.EMPTY_ANSWER,
                    "Model returned too few topics: ${topics.size} for batch of ${batch.size} messages (< 10%)."
                )
            }

            if (validWordCounts.isNotEmpty()) {
                KeywordStatsRepository.updateWordStats(validWordCounts)
            }
            if (extractedEntities.isNotEmpty()) {
                KeywordStatsRepository.updateEntities(extractedEntities)
            }

            return topics
        }

        suspend fun parseSummaryResponse(
            response: String,
            batch: List<SummaryPayload>,
            llmParser: LLMClient,
            bannedWords: String,
            onContentBlocked: (() -> Unit)? = null
        ): ParsedSummaryBatch {
            val (jsonArray, _) = llmParser.safeParseJsonArray(response)
            val results = mutableListOf<SummaryResult>()
            val noUpdateTopicIds = mutableSetOf<Long>()
            val processedTopicIds = mutableSetOf<Long>()

            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    val summary = obj.getString("text")
                    val newTitle = obj.optString("title").takeIf { it.isNotBlank() } ?: ""
                    val macroTag = obj.optString("macro_tag").takeIf { it.isNotBlank() }

                    val original = batch.find { it.topic.id == id }
                        ?: batch.find { it.topic.ids?.contains(id) == true }
                        ?: batch.find { TextComparator.areSimilar(it.topic.title, newTitle, 0.7f) }

                    if (original != null && processedTopicIds.contains(original.topic.id)) {
                        Log.w("ResponseParser", "[PARSER SUMMARY] Duplicate object for topic [${original.topic.id}] skipped (split protection).")
                        continue
                    }

                    val cleanSummary = summary.trim().uppercase()
                    val isRejected = TextComparator.areSimilar(cleanSummary, "REJECTED", SIGNAL_SIMILARITY_THRESHOLD)
                    val isNoUpdate = TextComparator.areSimilar(cleanSummary, "NO_UPDATE", SIGNAL_SIMILARITY_THRESHOLD)

                    if (isRejected) {
                        Log.w("ResponseParser", "[PARSER SUMMARY] Topic [${original?.topic?.id ?: id}] content was REJECTED by model.")
                        onContentBlocked?.invoke()
                        original?.let { processedTopicIds.add(it.topic.id) }
                        continue
                    }

                    if (isNoUpdate) {
                        Log.i("ResponseParser", "[PARSER SUMMARY] Topic [${original?.topic?.id ?: id}] marked as NO_UPDATE by model.")
                        if (original != null) {
                            MewsRepository.deleteTitleById(original.topic.id)
                            processedTopicIds.add(original.topic.id)
                            noUpdateTopicIds.add(original.topic.id)
                        }
                        continue
                    }

                    if (original != null && summary.trim().length > 15 && !bannedWords.contains(macroTag ?: " null ")) {
                        if (macroTag != null) MewsRepository.addTheme(macroTag)

                        results.add(SummaryResult(
                            id = original.topic.id,
                            summary = summary,
                            title = newTitle.ifBlank { original.topic.title },
                            macroTag = macroTag,
                            payload = original
                        ))
                        processedTopicIds.add(original.topic.id)
                    }
                } catch (e: Exception) {
                    Log.w("ResponseParser", "[PARSER SUMMARY] Error mapping summary JSON result", e)
                }
            }
            Log.d("ResponseParser", "[PARSER SUMMARY] Parsed ${results.size} summary results, ${noUpdateTopicIds.size} NO_UPDATE out of ${batch.size} payloads.")
            return ParsedSummaryBatch(results = results, noUpdateTopicIds = noUpdateTopicIds)
        }
    }
}