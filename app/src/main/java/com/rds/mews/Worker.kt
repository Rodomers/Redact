package com.rds.mews

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = DbHelper(applicationContext)
        val fetcher = RssFetcher(db)
        val settingsManager = SettingsManager(applicationContext)

        val titlesPeriod = settingsManager.getInt("titles_period", 24)

        return try {
            withContext(Dispatchers.IO) {
                if (fetcher.fetchAndStoreAll(titlesPeriod).errors.isEmpty()) {
                    settingsManager.saveLong("last_rss_update", System.currentTimeMillis())
                }
                println("Worker: parsing finished")
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}