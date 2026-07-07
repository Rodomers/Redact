package com.rds.mews.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rds.mews.core.MinifluxClient
import com.rds.mews.core.MinifluxEntry
import com.rds.mews.core.RssFetcher
import com.rds.mews.core.SharedHttpClient
import com.rds.mews.database.MessageEntity
import com.rds.mews.database.SourceEntity
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.text_filters.DuplicateDetector
import com.rds.mews.text_filters.TextCleaner
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
            }
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
        val windowTexts = MewsRepository.getCleanTextsInWindow(windowStart, windowEnd)

        val entitiesToInsert = mutableListOf<MessageEntity>()
        val syncTimeMs = System.currentTimeMillis()

        for (i in batch.indices) {
            val entry = batch[i]
            val pubTimeMs = pubTimes[i]
            val cleanText = TextCleaner.clean(entry.content)

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

        if (entitiesToInsert.isNotEmpty()) {
            MewsRepository.insertBatchAndUpdateSourceTime(entitiesToInsert, source.id, syncTimeMs)
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