package com.rds.mews.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rds.mews.core.parser.MinifluxClient
import com.rds.mews.core.parser.MinifluxEntry
import com.rds.mews.core.parser.RssFetcher
import com.rds.mews.core.SharedHttpClient
import com.rds.mews.database.main.MessageEntity
import com.rds.mews.database.main.SourceEntity
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.core.text.DuplicateDetector
import com.rds.mews.core.text.SnippetExtractor
import com.rds.mews.core.text.TextCleaner
import com.rds.mews.core.text.TextSanitizer
import com.rds.mews.core.text.graph.KnowledgeGraphManager
import com.rds.mews.core.text.stats.BurstDetector
import com.rds.mews.database.keyword_stats.BurstClusterEntity
import com.rds.mews.repositories.KeywordStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis

class RssUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val KEY_SOURCES = "sources"
    }

    override suspend fun doWork(): Result {
        if (!MewsRepository.isInitialized) return Result.retry()

        val sources = inputData.getBoolean(KEY_SOURCES, false)
        val enableProxy = MewsRepository.proxyEnabled.first()
        val fetcher = RssFetcher(enableProxy)
        val titlesPeriod = MewsRepository.titlesPeriod.first().num ?: 0L

        return try {
            withContext(Dispatchers.IO) {
                fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600)
                if (!sources) MewsRepository.setLastRssUpdate(System.currentTimeMillis())
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class ParserWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val duplicateDetector = DuplicateDetector()

    override suspend fun doWork(): Result {
        if (!MewsRepository.isInitialized) return Result.retry()

        return try {
            withContext(Dispatchers.IO) {
                val enableProxy = MewsRepository.proxyEnabled.first()
                val httpClient = SharedHttpClient.createInstance(
                    MewsRepository.HUB_ADDRESS,
                    MewsRepository.SERVER_KEY,
                    enableProxy
                )
                val minifluxClient = MinifluxClient(httpClient)
                val sourcesQueue = MewsRepository.getSourcesQueue()

                for (source in sourcesQueue) {
                    if (source.errCount < 3 && System.currentTimeMillis() - source.lastSyncTime > 1_800_000)
                        processSource(
                        source,
                        minifluxClient,
                        enableProxy
                    )
                }

                MewsRepository.messageTimeKill(864000L)
                KnowledgeGraphManager.applyDailyDecay()
                KeywordStatsRepository.clearOldKeywords()
                KeywordStatsRepository.recalculateHistoricalStats()
            }
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        }  catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            Log.e("ParserWorker", "Global pipeline failure", e)
            Result.failure()
        }
    }

    private suspend fun processSource(
        source: SourceEntity,
        minifluxClient: MinifluxClient,
        enableProxy: Boolean
    ) {
        try {
            val cursorTimeMs = maxOf(source.lastSyncTime, System.currentTimeMillis() - 864000000L)

            var serverFeedId: Long? = null
            try {
                serverFeedId = minifluxClient.getFeedIdByUrl(source.feedUrl)
                    ?: minifluxClient.createFeed(source.feedUrl)
            } catch (e: Exception) {
                Log.w("ParserWorker", "Failed to init Miniflux feed for ${source.id}", e)
            }

            val entries = fetchEntriesWithFallback(source, serverFeedId, cursorTimeMs, minifluxClient, enableProxy)

            if (entries.isEmpty()) {
                MewsRepository.resetErrorCount(source.id)
                return
            }

            val currentBatchSize = MewsRepository.parserBatchSize.value
            var newBatchSize = currentBatchSize

            entries.chunked(currentBatchSize).forEach { batch ->
                val executionTime = measureTimeMillis {
                    processBatch(source, batch)
                }

                if (executionTime > 3000) {
                    newBatchSize = maxOf(10, (newBatchSize * 0.8).toInt())
                } else if (executionTime < 500) {
                    newBatchSize = minOf(250, (newBatchSize * 1.2).toInt())
                }
            }

            if (newBatchSize != currentBatchSize) {
                MewsRepository.setParserBatchSize(newBatchSize)
            }

            MewsRepository.resetErrorCount(source.id)
        } catch (_: Exception) {
            MewsRepository.incrementErrorCount(source.id)
        }
    }

    private suspend fun processBatch(
        source: SourceEntity,
        batch: List<MinifluxEntry>
    ) {
        val pubTimes = batch.map { parseDate(it.published_at) ?: System.currentTimeMillis() }
        val minTimeMs = pubTimes.minOrNull() ?: System.currentTimeMillis()
        val maxTimeMs = pubTimes.maxOrNull() ?: System.currentTimeMillis()

        val windowStart = minTimeMs - (4 * 60 * 60 * 1000L)
        val windowEnd = maxTimeMs + (4 * 60 * 60 * 1000L)
        val windowMessages = MewsRepository.getMessagesInWindow(windowStart, windowEnd)

        val entitiesToInsert = mutableListOf<MessageEntity>()
        val syncTimeMs = System.currentTimeMillis()

        val batchWordCounts = mutableMapOf<String, Double>()
        val wordToSourcesMap = mutableMapOf<String, MutableSet<Long>>()

        var windowTokensCount = 0L
        for (msg in windowMessages) {
            val tokens = msg.cleanText.lowercase()
                .split(Regex("[^\\p{L}\\p{N}_-]+"))
                .filter { it.length > 1 }
            windowTokensCount += tokens.size
            for (token in tokens) {
                wordToSourcesMap.getOrPut(token) { mutableSetOf() }.add(msg.sourceId)
            }
        }

        for (i in batch.indices) {
            val entry = batch[i]
            val pubTimeMs = pubTimes[i]

            val cleanText = TextSanitizer.sanitize(TextCleaner.clean(entry.content))

            val tokens = cleanText.lowercase()
                .split(Regex("[^\\p{L}\\p{N}_-]+"))
                .filter { it.length > 1 }

            tokens.forEach { word ->
                batchWordCounts[word] = (batchWordCounts[word] ?: 0.0) + 1.0
                wordToSourcesMap.getOrPut(word) { mutableSetOf() }.add(source.id)
            }

            val windowTexts = windowMessages.map { it.cleanText }
            val isDuplicate = duplicateDetector.checkIsDuplicate(cleanText, windowTexts)

            entitiesToInsert.add(
                MessageEntity(
                    sourceId = source.id,
                    link = entry.url,
                    pubTime = pubTimeMs,
                    title = entry.title,
                    originalText = entry.content,
                    cleanText = cleanText,
                    isDuplicate = isDuplicate,
                    isRead = false,
                    factCheck = null
                )
            )
        }

        if (batchWordCounts.isNotEmpty()) {
            KeywordStatsRepository.updateWordStats(batchWordCounts)
        }

        val batchTokensCount = batchWordCounts.values.sum().toLong()
        val totalWindowWords = windowTokensCount + batchTokensCount
        var savedMessageIds = emptyList<Long>()

        if (entitiesToInsert.isNotEmpty()) {
            savedMessageIds = MewsRepository.insertBatchAndUpdateSourceTime(entitiesToInsert, source.id, syncTimeMs)
        }

        if (totalWindowWords > 0 && savedMessageIds.isNotEmpty()) {
            val messageTokensMap = savedMessageIds.indices.associate { idx ->
                val msgId = savedMessageIds[idx]
                val tokens = entitiesToInsert[idx].cleanText.lowercase()
                    .split(Regex("[^\\p{L}\\p{N}_-]+"))
                    .filter { it.length > 1 }
                    .toSet()
                msgId to tokens
            }

            val windowData = batchWordCounts.map { (word, count) ->
                val uniqueSourcesCount = wordToSourcesMap[word]?.size ?: 1
                BurstDetector.WordWindowData(
                    word = word,
                    count = count.toInt(),
                    uniqueDomainsCount = uniqueSourcesCount
                )
            }
            val burstDetector = BurstDetector()
            val burstResults = burstDetector.processWindow(
                windowData = windowData,
                totalWindowWords = totalWindowWords,
                totalNews = batch.size.toDouble()
            )
            val activeBursts = burstResults.filter { it.isBurst }

            if (activeBursts.isNotEmpty()) {
                val activeBurstMap = activeBursts.associateBy { it.word }
                val activeWords = activeBurstMap.keys.toMutableSet()

                val wordToMsgIdsMap = activeWords.associateWith { word ->
                    messageTokensMap.filterValues { tokens -> word in tokens }.keys.toList()
                }

                val savedBatchMessages = entitiesToInsert.zip(savedMessageIds).map { (entity, id) ->
                    entity.copy(id = id)
                }
                val allMessages = windowMessages + savedBatchMessages

                val unvisited = activeWords.toMutableSet()
                while (unvisited.isNotEmpty()) {
                    val startWord = unvisited.first()
                    unvisited.remove(startWord)

                    val currentClusterWords = mutableSetOf(startWord)
                    val queue = ArrayDeque<String>()
                    queue.add(startWord)

                    while (queue.isNotEmpty()) {
                        val word = queue.removeFirst()
                        val wordMsgs = wordToMsgIdsMap[word] ?: emptyList()

                        val neighborWords = unvisited.filter { otherWord ->
                            val otherMsgs = wordToMsgIdsMap[otherWord] ?: emptyList()
                            wordMsgs.any { it in otherMsgs }
                        }

                        for (neighbor in neighborWords) {
                            currentClusterWords.add(neighbor)
                            unvisited.remove(neighbor)
                            queue.add(neighbor)
                        }
                    }

                    val clusterMsgIds = currentClusterWords
                        .flatMap { wordToMsgIdsMap[it] ?: emptyList() }
                        .distinct()

                    val clusterBursts = currentClusterWords.mapNotNull { activeBurstMap[it] }
                    val peakZScore = clusterBursts.maxOfOrNull { it.zScore } ?: 0.0

                    val clusterEntity = BurstClusterEntity(
                        keywords = currentClusterWords.toList(),
                        messageIds = clusterMsgIds,
                        peakZScore = peakZScore,
                        timestamp = syncTimeMs
                    )

                    KeywordStatsRepository.saveBurstCluster(clusterEntity)

                    val clusterText = allMessages
                        .filter { it.id in clusterMsgIds }.joinToString("\n") { it.cleanText }

                    val snippet = SnippetExtractor.extractSnippet(
                        MewsRepository.getAppContext(),
                        clusterText
                    )

                    applicationContext.sendBurstNotification(snippet)
                }
            }
        }
    }

    private suspend fun fetchEntriesWithFallback(
        source: SourceEntity,
        serverFeedId: Long?,
        cursorTimeMs: Long,
        minifluxClient: MinifluxClient,
        enableProxy: Boolean
    ): List<MinifluxEntry> {
        if (serverFeedId != null) {
            try {
                return minifluxClient.getEntries(serverFeedId, cursorTimeMs, limit = 100)
            } catch (e: Exception) {
                Log.w("ParserWorker", "Miniflux failed, falling back to RSS for ${source.id}", e)
            }
        }

        return RssFetcher(enableProxy).fetchSingleSourceAsMinifluxEntries(source, cursorTimeMs)
    }

    private fun parseDate(dateStr: String): Long? {
        return try {
            Instant.parse(dateStr).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

class TitlesUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!MewsRepository.isInitialized) return Result.retry()

        return try {
            when (val result = TitlesUpdater().performUpdate(isOneTime = true)) {
                is SummarizationResult.Success -> Result.success()
                is SummarizationResult.Failure -> {
                    if (result.type == SummarizationErrorType.JOB_CANCELLED) {
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure()
        }
    }
}