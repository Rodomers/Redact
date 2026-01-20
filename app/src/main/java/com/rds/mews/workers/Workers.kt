package com.rds.mews.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rds.mews.core.DbHelper
import com.rds.mews.core.RssFetcher
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class RssUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!MewsRepository.isInitialized) {
            return Result.retry()
        }

        val db = DbHelper(applicationContext)
        val enableProxy = MewsRepository.proxyEnabled.first()
        val fetcher = RssFetcher(db, enableProxy)

        val titlesPeriod = MewsRepository.titlesPeriod.first().num ?: 0L

        return try {
            withContext(Dispatchers.IO) {
                fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600)
                MewsRepository.setLastRssUpdate(System.currentTimeMillis())
                println("RssUpdateWorker: parsing finished successfully.")
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

class TitlesUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!MewsRepository.isInitialized) {
            return Result.retry()
        }

        val updater = TitlesUpdater(applicationContext)

        return try {
            when (val result = updater.performUpdate(isOneTime = true)) {
                is SummarizationResult.Success -> Result.success()
                is SummarizationResult.Failure -> {
                    if (result.type == SummarizationErrorType.JOB_CANCELLED) {
                        Result.failure()
                    } else {
                        // Ошибка сети или API
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