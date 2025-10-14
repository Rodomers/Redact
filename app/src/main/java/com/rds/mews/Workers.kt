package com.rds.mews

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RssUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = DbHelper(applicationContext)
        val settingsManager = SettingsManager(applicationContext)
        val fetcher = RssFetcher(db)

        val titlesPeriod = settingsManager.getInt(MewsRepository.TITLES_PERIOD, 24)
        val rssUpdateInterval = settingsManager.getInt(MewsRepository.RSS_UPDATE_INTERVAL, 30)

        return try {
            withContext(Dispatchers.IO) {
                fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600)
                settingsManager.saveLong(MewsRepository.LAST_RSS_UPDATE, System.currentTimeMillis())
                println("RssUpdateWorker: parsing finished successfully.")
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        } finally {
            val nextRssUpdate = System.currentTimeMillis() + rssUpdateInterval * 60 * 1000L
            AlarmScheduler.schedule(applicationContext, nextRssUpdate)
        }
    }
}

class TitlesUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        val db = DbHelper(applicationContext)

        val currentLLM = settingsManager.getString(MewsRepository.CURRENT_LLM_MODEL, "gemini-2.0-flash")
        val llmApiKey = settingsManager.getString(MewsRepository.USER_API_KEY, MewsRepository.DEFAULT_GEMINI_API_KEY)
        val rssLastUpdate = settingsManager.getLong(MewsRepository.LAST_RSS_UPDATE, 0L)
        val rssUpdateInterval = settingsManager.getInt(MewsRepository.RSS_UPDATE_INTERVAL, 30)
        val titlesPeriod = settingsManager.getInt(MewsRepository.TITLES_PERIOD, 24)
        val titlesNum = settingsManager.getInt(MewsRepository.TITLES_NUM, 10)
        val filterTopics = settingsManager.getBoolean(MewsRepository.FILTER_TOPICS, false)
        val autoUpdate = settingsManager.getBoolean(MewsRepository.TITLES_AUTO_UPDATE, false)

        val fetcher = RssFetcher(db)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey)
        val summarizer = NewsSummarizer(db, llm)

        settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, true)
        settingsManager.saveString(MewsRepository.UPDATING_STATE, "updating")

        try {
            withContext(Dispatchers.IO) {
                if (isStopped) return@withContext

                val needToFetchRss = (System.currentTimeMillis() - rssLastUpdate) / 60000L > rssUpdateInterval
                val noFetchErrors = if (needToFetchRss) {
                    val result = fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600).errors.isEmpty()
                    settingsManager.saveLong(MewsRepository.LAST_RSS_UPDATE, System.currentTimeMillis())
                    result
                } else {
                    true
                }

                if (isStopped) return@withContext

                    if (noFetchErrors) {
                        val titles = db.getTitles()
                        if (!(titles.any {it.text.contains("<промежуточный текст>") || it.time == 0.toLong() || it.sources.contains("<промежуточный текст>")})) {
                            db.titlesTimeKill(0)
                        }
                        var iter = 0
                        var res: SummarizationResult = SummarizationResult.Failure(
                            SummarizationErrorType.UNKNOWN_ERROR)
                        while (settingsManager.getBoolean(MewsRepository.UPDATING_TITLES, false) && iter <= 5) {
                            res = summarizer.summarizeTopics(
                                maxTopics = titlesNum,
                                messageSeconds = titlesPeriod.toLong() * 3600,
                                readyFunc = {
                                    settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, false)
                                },
                                filterTopics = filterTopics
                            )
                            iter++
                        }

                    when (res) {
                        is SummarizationResult.Success -> settingsManager.clearLastError()
                        is SummarizationResult.Failure -> settingsManager.saveLastError(res)
                    }

                    if (autoUpdate && !isStopped) {
                        val nextRunTimeMills = System.currentTimeMillis() + titlesPeriod * 3600 * 1000L
                        AlarmScheduler.schedule(applicationContext, nextRunTimeMills)
                    }
                }
            }
            settingsManager.saveString(MewsRepository.UPDATING_STATE, "off")
            settingsManager.saveLong(MewsRepository.LAST_TITLES_UPDATE, System.currentTimeMillis())
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            val errorResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
            settingsManager.saveLastError(errorResult)
            if (isStopped) return Result.failure()
        }
        return Result.retry()
    }
}