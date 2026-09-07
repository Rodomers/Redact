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
import kotlin.math.roundToInt

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

    private val MATCH_RATE = 0.45f
    private val MAX_SUMMARIZATION_ATTEMTS = 3
    private val SINGLE_NEWS_CHAR_LIMIT = 3000
    private val TAG = "NewsSummarizer"

    private var totalItemsToProcess = 0
    private var processedItemsCount = 0
    private var baseProgress = 0f

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
            val bannedWords = try { MewsRepository.bannedNewsFlow.value.joinToString("'; '") } catch (_: Exception) { "" }
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
                processedItemsCount = 0
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
                        val rawTopicsList = parsedTopics.map { it.toRawTopicDto() }

                        Log.d(TAG, "[EXTRACTION] Batch Success: Extracted ${parsedTopics.size} topics. Remaining queue: ${remainingIdsList.size}")

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
                        Log.w(TAG, "[EXTRACTION] Batch Failed (Final): ${failedIds.size} messages moved to victims.")
                        stateManager.updateState { it.copy(victimMessages = it.victimMessages + failedIds) }
                    }
                )
            }

            val currentState = stateManager.readState() ?: return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)
            if (isFiltering || currentState.stagedRawTopics.isNotEmpty()) {
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

                val normalizedTopics = extractedTopics.map { it.copy(weight = maxOf(it.weight, it.ids?.size ?: 1)) }
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
                        val preMergedBlitz = fastKeywordPreMerge(blitzTopics)
                        mergeInTimeWindows(preMergedBlitz, messageTimeMap)
                    }

                    val mergedTopicsDeferred = async(Dispatchers.Default) {
                        val preMergedNonBlitz = fastKeywordPreMerge(nonBlitzTopics)
                        Log.d(TAG, "[FILTERING] Fast keyword pre-merge reduced non-blitz from ${nonBlitzTopics.size} to ${preMergedNonBlitz.size}")

                        if (filterTopics && preMergedNonBlitz.isNotEmpty()) {
                            try {
                                Log.d(TAG, "[FILTERING] Starting LLM smart merge on pre-merged topics...")
                                var result: List<Topics> = smartMergeTopics(preMergedNonBlitz, currentLanguage, bannedWords, maxTopics)
                                var attempts = 0
                                while (attempts < MAX_SUMMARIZATION_ATTEMTS && result.size > maxTopics * 2) {
                                    attempts++
                                    result = smartMergeTopics(result, currentLanguage, bannedWords, maxTopics)
                                }
                                Log.d(TAG, "[FILTERING] LLM smart merge finished. Result size: ${result.size}. Applying final time-window algorithmic merge.")
                                val finalReg = mergeInTimeWindows(result, messageTimeMap)
                                Log.d(TAG, "[FILTERING] Final merged non-blitz topics size: ${finalReg.size}")
                                finalReg
                            } catch (e: Exception) {
                                Log.w(TAG, "[FILTERING] Smart merge failed, falling back to time-window algorithmic merge", e)
                                mergeInTimeWindows(preMergedNonBlitz, messageTimeMap)
                            }
                        } else {
                            Log.d(TAG, "[FILTERING] Filter disabled or no non-blitz topics. Applying time-window algorithmic merge directly.")
                            mergeInTimeWindows(preMergedNonBlitz, messageTimeMap)
                        }
                    }

                    val blitzMerged = blitzDeferred.await()
                    Log.d(TAG, "[FILTERING] Blitz after algorithmic merge: ${blitzMerged.size}")

                    val mergedTopics = mergedTopicsDeferred.await()

                    val normalizedFinalTopics = normalizeWeights(mergedTopics.sortedByDescending { it.ids?.size ?: 0 })
                    val normalizedBlitzMerged = normalizeWeights(blitzMerged.sortedByDescending { it.ids?.size ?: 0 })

                    val finalTopics = normalizedFinalTopics.take((maxTopics * 1.5).toInt())
                    val primaryTopics = finalTopics.take(maxTopics)
                    val blitzRaw = normalizedBlitzMerged.take(maxTopics)

                    Log.i(TAG, "[STAGE: SAVING DRAFTS] Selected ${primaryTopics.size} primary topics and ${blitzRaw.size} blitz topics. Updating Knowledge Graph and saving to DB...")

                    launch(Dispatchers.Default) {
                        try {
                            KnowledgeGraphManager.processTopicsAndBuildGraph(finalTopics.map { it.keywords.toSet() })
                            GraphCache.clear()
                        } catch (e: Exception) {
                            Log.e(TAG, "Process graph failed: ${e.cause}, ${e.message}")
                            throw e
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
                        reserveTopics = finalTopics.map { topic -> topic.toRawTopicDto() }.filter { element -> !primaryRawDto.contains(element) },
                        blitzTopics = blitzRawDto
                    )
                    }
                    baseProgress = try { MewsRepository.updatingProgress.first() } catch(_: Exception) { baseProgress }
                }
            }

            val summarizationState = stateManager.readState() ?: return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)

            val dbTopics = MewsRepository.getTitlesWithStatus(TitleStatus.PROCESSING.statusId)

            val normalTopics = (summarizationState.primaryTopics.map { it.toTopics() } + dbTopics.filter { !it.isBlitz }).distinctBy { it.title }
            val blitzTopics = (summarizationState.blitzTopics.map { it.toTopics() } + dbTopics.filter { it.isBlitz }).distinctBy { it.title }
            val currentTopics = normalTopics.map { it.id } + blitzTopics.map { it.id }

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

            Log.d(TAG, "[SUMMARIZATION PREP] Fetched ${rawMessagesList.size} raw messages from DB for payloads.")

            val normalPayloads = buildPayloads(normalTopics, processedTopicsSignatures, rawMessagesList, preHistory)

            val deduplicatedBlitz = blitzTopics.map { deduplicateBlitzTopicMessages(it, rawMessagesList) }
            val blitzPayloads = buildPayloads(deduplicatedBlitz, processedTopicsSignatures, rawMessagesList, preHistory)

            Log.i(TAG, "[STAGE: SUMMARIZATION EXECUTION] Normal payloads: ${normalPayloads.size}, Blitz payloads: ${blitzPayloads.size}")

            val failedTopics = mutableListOf<Topics>()

            coroutineScope {
                val parentLinkingJobs = mutableListOf<Job>()

                if (normalPayloads.isNotEmpty()) {
                    executionManager.execute<SummaryPayload, List<SummaryResult>>(
                        items = normalPayloads,
                        idExtractor = { it.topic.id },
                        textExtractor = { it.contentPayload },
                        batchMode = BatchMode.BASE_TOPICS,
                        promptBuilder = { batch -> PromptFactory.buildSummaryPrompt(batch, currentLanguage, bannedWords, isBlitz = false) },
                        responseParser = { response, batch -> ResponseParser.parseSummaryResponse(response, batch, llm) },
                        onBatchSuccess = { batch, _, results ->
                            Log.d(TAG, "[SUMMARIZATION] Normal batch success. Generated ${results.size} summaries.")
                            val jobs = processSummaryResults(this, results, preHistory, processedTopicsSignatures, summarizationState.timemark)
                            parentLinkingJobs.addAll(jobs)
                            val finishedIds = results.map { it.payload.topic.id }.toSet()
                            stateManager.updateState {
                                it.copy(
                                    primaryTopics = it.primaryTopics.filter { topic -> topic.id !in finishedIds }
                                )
                            }
                            processedItemsCount += batch.size
                            MewsRepository.setUpdatingProgress(baseProgress + ((processedItemsCount.toFloat() / totalItemsToProcess) * availableProgressSpace))
                        },
                        onBatchFailure = { batch, _ ->
                            Log.w(TAG, "[SUMMARIZATION] Normal batch failed (Final). ${batch.size} topics moved to failed list.")
                            failedTopics.addAll(batch.map { it.topic })
                        }
                    )
                }

                if (blitzPayloads.isNotEmpty()) {
                    executionManager.execute<SummaryPayload, List<SummaryResult>>(
                        items = blitzPayloads,
                        idExtractor = { it.topic.id },
                        textExtractor = { it.contentPayload },
                        batchMode = BatchMode.BLITZ_TOPICS,
                        promptBuilder = { batch -> PromptFactory.buildSummaryPrompt(batch, currentLanguage, bannedWords, isBlitz = true) },
                        responseParser = { response, batch -> ResponseParser.parseSummaryResponse(response, batch, llm) },
                        onBatchSuccess = { batch, _, results ->
                            Log.d(TAG, "[SUMMARIZATION] Blitz batch success. Generated ${results.size} summaries.")
                            val jobs = processSummaryResults(this, results, preHistory, processedTopicsSignatures, summarizationState.timemark)
                            parentLinkingJobs.addAll(jobs)
                            val finishedIds = results.map { it.payload.topic.id }.toSet()
                            stateManager.updateState {
                                it.copy(
                                    blitzTopics = it.blitzTopics.filter { topic -> topic.id !in finishedIds }
                                )
                            }
                            processedItemsCount += batch.size
                            MewsRepository.setUpdatingProgress(baseProgress + ((processedItemsCount.toFloat() / totalItemsToProcess) * availableProgressSpace))
                        },
                        onBatchFailure = { batch, _ ->
                            Log.w(TAG, "[SUMMARIZATION] Blitz batch failed (Final). ${batch.size} topics moved to failed list.")
                            failedTopics.addAll(batch.map { it.topic })
                        }
                    )
                }

                if (parentLinkingJobs.isNotEmpty()) {
                    Log.i(TAG, "Awaiting completion of ${parentLinkingJobs.size} parent linking tasks...")
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
                if (currentAttempt >= MAX_SUMMARIZATION_ATTEMTS) {
                    stateManager.clearState()
                    dbRemaining.forEach { MewsRepository.deleteTitleById(it.id) }
                    Log.w(TAG, "Deleted ${dbRemaining.size} failed topics from DB.")
                }
                if (failedTopics.isNotEmpty()) {
                    MewsRepository.setFailedTitles(failedTopics.size)
                }
                MewsRepository.setLastTitlesUpdate(summarizationState.timemark)
                safeReadyFunc(readyFunc)
                return if (currentAttempt < MAX_SUMMARIZATION_ATTEMTS) SummarizationResult.Failure(SummarizationErrorType.SUMMARIZE_TOPICS_FAILED)
                else SummarizationResult.Success
            }

            MewsRepository.setLastTitlesUpdate(summarizationState.timemark)
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

    suspend fun compareTopics(
        topicTitle: String, topicKeywords: List<String>, otherTitle: String, otherKeywords: List<String>,
        topicText: String? = null, otherText: String? = null
    ): Double {
        val expandedTopicKeywords = topicKeywords.toMutableSet()
        for (kw in topicKeywords) expandedTopicKeywords += GraphCache.getRelatedEntities(kw)

        val expandedOtherKeywords = otherKeywords.toMutableSet()
        for (kw in otherKeywords) expandedOtherKeywords += GraphCache.getRelatedEntities(kw)

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

        if (titlesThreshold >= 0.85) return 1.0

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
            topicKeywords.size + (expandedTopicKeywords.size * 0.5).toInt(),
            otherKeywords.size + (expandedOtherKeywords.size * 0.5).toInt()
        ).coerceAtLeast(1))

        val baseTitlesScore = TextComparator.countThreshold(
            topicTitle.lowercase(),
            otherTitle.lowercase()
        )
        if (kwMatches == 0 && baseTitlesScore < 0.2f) return 0.0

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

        val totalWeight = if (hasSummary) 7.0 else 4.0
//        Log.i(TAG, "Theme title: $otherTitle, keyword: $keywordScore, titles: $titlesThreshold, summary: $summaryThreshold, total: ${(keywordScore * 2.0 + titlesThreshold * 2.0 + summaryThreshold * 3.0) / totalWeight}")
        return (keywordScore * 2.0 + titlesThreshold * 2.0 + summaryThreshold * 3.0) / totalWeight
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
                Log.d(TAG, "[PAYLOAD BUILDER] Dropped topic [${topic.id}] '${topic.title}' as DUPLICATE.")
                processedItemsCount++
                return@mapNotNull null
            }

            val messageIds = topic.ids ?: emptyList()
            val fetchedMessages = if (messageIds.isNotEmpty()) {
                rawMessagesList.filter { it.id in messageIds }
            } else emptyList()

            if (fetchedMessages.isEmpty()) {
                Log.w(TAG, "[PAYLOAD BUILDER] Dropped topic [${topic.id}] '${topic.title}': No messages found in DB payload pool.")
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
                        Log.d(TAG, "[PAYLOAD BUILDER] Topic [${topic.id}] '${topic.title}' matched ancestor [$bestMatchId]. Requires update: $requiresUpdate")
                    }
                }
            }

            SummaryPayload(topic, uniqueMessages, contentPayload, requiresUpdate)
        }
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

            if (summary.isBlank() || summary == "REJECTED" || summary.length <= 15) {
                Log.d(TAG, "[RESULT PROCESSOR] Dropped summary for topic [${topic.id}] '${topic.title}'. Reason: REJECTED, blank or length <= 15.")
                continue
            }

            var bestMatchId: Long? = null
            val newTimeVal = suitableMessages.minOfOrNull { it.time } ?: extractionTime

            scope.launch {
                MewsRepository.updateTitle(
                    id = topic.id,
                    newEventTime = newTimeVal,
                    newTitle = newTitle,
                    macroTag = macroTag,
                    isBlitz = topic.isBlitz,
                    summary = summary
                )
            }
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
                        MewsRepository.manuallyLinkTopics(topic.id, parentTopic.id)
                        Log.i(TAG, "[RESULT PROCESSOR] Topic [${topic.id}] created new DB entry with ancestor link to [$bestMatchId].")
                    }
                }
                jobs.add(job)
            }
        }

        return jobs
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

        Log.d(TAG, "[BLITZ DEDUP] Topic '${topic.title}' reduced messages from ${topicMessages.size} to ${finalIds.size}")
        return topic.copy(ids = finalIds)
    }

    private fun fastKeywordPreMerge(topics: List<Topics>): List<Topics> {
        if (topics.size <= 1) return topics
        val result = mutableListOf<Topics>()

        for (candidate in topics) {
            val matchIndex = result.indexOfFirst { existing ->
                if (existing.isBlitz != candidate.isBlitz) return@indexOfFirst false
                if (existing.keywords.isEmpty() || candidate.keywords.isEmpty()) return@indexOfFirst false

                var matches = 0
                for (ck in candidate.keywords) {
                    for (ek in existing.keywords) {
                        if (ck.equals(ek, ignoreCase = true) || TextComparator.areSimilar(ck.lowercase(), ek.lowercase(), 0.75f)) {
                            matches++
                            break
                        }
                    }
                }

                val minSize = minOf(candidate.keywords.size, existing.keywords.size)
                matches >= 2 && minSize > 0 && ((matches.toDouble() / minSize) >= MATCH_RATE)
            }

            if (matchIndex != -1) {
                val existing = result[matchIndex]
                val combinedIds = (existing.ids.orEmpty() + candidate.ids.orEmpty()).distinct()
                val combinedKeywords = mutableListOf<String>()
                (existing.keywords + candidate.keywords).forEach { word ->
                    if (combinedKeywords.none { TextComparator.areSimilar(it, word, 0.85f) }) {
                        combinedKeywords.add(word)
                    }
                }
                result[matchIndex] = existing.copy(
                    ids = combinedIds,
                    weight = combinedIds.size,
                    keywords = combinedKeywords
                )
            } else {
                result.add(candidate.copy(weight = candidate.ids?.size ?: 1))
            }
        }
        return result
    }

    private suspend fun mergeInTimeWindows(
        topics: List<Topics>,
        messageTimeMap: Map<Long, Long>,
        windowMs: Long = 4 * 3600_000L
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

        val finalMerged = mutableListOf<Topics>()
        mergeIntoGlobal(windowMerged, finalMerged)
        return finalMerged
    }

    private fun normalizeWeights(topics: List<Topics>): List<Topics> {
        if (topics.isEmpty()) return emptyList()
        val maxCount = topics.maxOfOrNull { it.ids?.size ?: 0 } ?: 0
        if (maxCount == 0) return topics.map { it.copy(weight = 0) }

        return topics.map { topic ->
            val count = topic.ids?.size ?: 0
            val normalized = ((count.toDouble() / maxCount) * 10.0).roundToInt().coerceIn(0, 10)
            topic.copy(weight = normalized)
        }
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

                globalCache[existingIndex] = existing.copy(
                    ids = combinedIds,
                    weight = combinedIds.size,
                    keywords = combinedKeywords,
                    isBlitz = existing.isBlitz
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
                    }
                }
                val jsonInput = JSONArray(indexedInput).toString()

                """
                Задача: Склей полные дубликаты и выдели наиболее значимые независимые новости.
                
                ПРАВИЛА:
                1. СЛИЯНИЕ ДУБЛИКАТОВ: Объединяй индексы в "src" (src: [ix1, ix2]) ТОЛЬКО если новости описывают один и тот же конкретный инцидент или прямое развитие одной ситуации.
                2. СТРОГИЙ ЗАПРЕТ ДАЙДЖЕСТОВ: Категорически ЗАПРЕЩЕНО объединять независимые события по общей тематике, сфере (IT, экономика, происшествия) или городу. Никаких сборных тем («Новости дня», «События в регионе», «Сводка происшествий»). Каждое независимое событие — строго отдельный объект.
                3. ОТБОР И ФИЛЬТРАЦИЯ (НЕ БОЛЕЕ $targetLimit ТЕМ):
                   - Оставляй только важные, резонансные события. Мелкий инфошум, бытовые единичные случаи и спам просто ВЫБРАСЫВАЙ (не включай в ответ).
                   - Если независимых событий больше $targetLimit, отдавай предпочтение темам с наибольшим весом (w) и просто ОТБРАСЫВАЙ менее важные.
                   - Запрещено сжимать или объединять независимые события между собой ради попадания в лимит.
                4. ОДИНОЧНЫЕ СОБЫТИЯ: Если важное событие не имеет дубликатов в списке — возвращай его отдельным объектом: "src": [ix].
                5. ЗАПРЕЩЕННЫЕ ТЕМЫ: Если новость относится к запрещенным темам ('$banned') — удали её и не включай в итоговый список.
                6. СТИЛЬ: Заголовки в стиле Smart Casual, точно отражающие суть конкретного инцидента.
                
                Ввод: [{"ix": 0, "t": "...", "kw": "...", "w": 5}]
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
                Максимальное количество выделяемых тем: $maxLimit. Если событий больше — выбери самые резонансные и значимые.
                
                ИНЖЕКЦИЯ ПРИОРИТЕТОВ: В текущем наборе присутствуют признаки следующих критически важных развивающихся событий:
                $burstKeywords
                Если находишь новости, относящиеся к этим приоритетным сюжетам, ОБЯЗАТЕЛЬНО выделяй их в отдельные темы, даже если они кажутся незначительными.
                
                Группируй новости по Сюжетным Линиям:
                1. Цепочка событий (ОСТАВЛЯТЬ ВМЕСТЕ): Например, событие + реакция + последствия = ОДНА тема.
                2. Сюжетная кластеризация: Объединяй события в одну тему только при наличии прямой причинно-следственной связи.
                ФИЛЬТРАЦИЯ (СТРОГО): Игнорируй рекламу, розыгрыши, а также темы: '$banned'.
                
                ИНСТРУКЦИЯ ДЛЯ КАТЕГОРИИ "ДРУГОЕ":
                Используй тему "Другое" (на языке $lang) исключительно для единичных, изолированных микроновостей.
                1. Флаг isBlitz: true.
                2. Максимальное количество: $maxLimit микроновстей.
                3. Каждая микроновость - отдельная тема. Запрещено объединять микротемы в кластеры, если они не связаны между собой.
                
                ИЗВЛЕЧЕНИЕ СУЩНОСТЕЙ (NER): Для каждой темы извлеки все ключевые сущности, упомянутые в тексте. Строго распредели их по 6 макрокатегориям: 'persons' (люди, должности), 'locations' (география), 'organizations' (компании, партии), 'events' (события, конфликты), 'products_tech' (товами, технологии, крипта), 'laws_regulations' (законы, договоры).
    
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
                4. Объединение в комплексы СТРОГО ЗАПРЕЩЕНО. Одна новость - один объект.
                ФОРМАТ (СТРОГО JSON):
                [{"id": <id>, "title": "<Заголовок>", "text": "<текст>"}]
                Ввод: $jsonInput
                """.trimIndent()
            } else {
                """
                Ты — профессиональный журналист-аналитик. Твоя задача — формировать сбалансированные новостные заметки средней длины, избегая как кратких выжимок, так и раздутых лонгридов.
                
                ИНСТРУКЦИИ:
                1. СТРУКТУРА И ОБЪЕМ:
                   - Если requires_update_header = false: пиши связный материал из 2–4 абзацев (Лид с ключевым событием -> Детализация фактов и цитат -> Последствия/контекст из источника). Не схлопывай насыщенные фактами новости в 1–2 предложения. Если исходный текст очень короткий — ограничься 1 емким абзацем без воды.
                   - Если requires_update_header = true: пиши ТОЛЬКО дополнение (1–2 абзаца свежих фактов). Начни строго с "**Обновление:** " (или аналога на $lang).
                2. СТИЛЬ: Smart Casual, связный нарратив, строгая объективность. Ключевые цитаты сохраняй прямой речью в кавычках («»).
                3. ДОСТОВЕРНОСТЬ: Строй текст исключительно на news_content. Внешние знания допустимы только для кратких пояснений терминов.
                4. ТОЧНОСТЬ ДАННЫХ: Даты, суммы, имена и статистика — строго без округлений и обобщений.
                5. УМНАЯ ФИЛЬТРАЦИЯ: Игнорируй ('$banned'). Если полезной информации после очистки не осталось — верни "text": "REJECTED".
                6. МАКРОКАТЕГОРИЯ: 1 верхнеуровневая категория (Политика, Экономика, Технологии и т.д.).
                7. ЗАГОЛОВОК: Не общий, достаточно точно отражающий событие.
                
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
                                        it.cleanText.lowercase().contains(contextStr)
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
                    Log.w("ResponseParser", "[PARSER SUMMARY] Error mapping summary JSON result", e)
                }
            }
            Log.d("ResponseParser", "[PARSER SUMMARY] Parsed ${results.size} summary results out of ${batch.size} payloads.")
            return results
        }
    }
}