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
                fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600)
                settingsManager.saveLong("last_rss_update", System.currentTimeMillis())
                println("Worker: parsing finished")
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}

class TitlesUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        settingsManager.saveBoolean("updating_titles", true)
        val currentLLM = settingsManager.getString("current_model", "")
        val llmApiKey = settingsManager.getString("user_api", "")
        val rssLastUpdate = settingsManager.getLong("last_rss_update", 0L)
        val rssUpdateInterval = settingsManager.getInt("rss_update_interval", 30)
        val titlesPeriod = settingsManager.getInt("titles_period", 24)
        val titlesNum = settingsManager.getInt("titles_num", 10)

        val db = DbHelper(applicationContext)
        val fetcher = RssFetcher(db)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey)
        val summarizer = NewsSummarizer(db, llm)

        return try {
            try {
                withContext(Dispatchers.IO) {
                    val noFetchErrors = when ((System.currentTimeMillis() - rssLastUpdate) / 60000L > rssUpdateInterval) {
                        true -> fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600).errors.isEmpty()
                        else -> true
                    }

                    if (noFetchErrors) {
                        val titles = db.getTitles()
                        if (!(titles.any {it.text.contains("<промежуточный текст>") || it.time == 0.toLong() || it.sources.contains("<промежуточный текст>")})) {
                            db.titlesTimeKill(0)
                        }
                        var iter = 0
                        while (settingsManager.getBoolean("updating_titles", false) && iter <= 5) {
                            summarizer.summarizeTopics(
                                maxTopics = titlesNum,
                                messageSeconds = titlesPeriod.toLong() * 3600,
                                readyFunc = { settingsManager.saveBoolean("updating_titles", false) }
                            )
                            iter++
                        }
                    }
                }

                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure()
            }
        } catch(e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}