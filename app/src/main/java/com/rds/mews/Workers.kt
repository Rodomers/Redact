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

        val titlesPeriod = settingsManager.getInt(MewsRepository.TITLES_PERIOD, 24)

        return try {
            withContext(Dispatchers.IO) {
                fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600)
                settingsManager.saveLong(MewsRepository.LAST_RSS_UPDATE, System.currentTimeMillis())
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
        settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, true)
        settingsManager.saveString(MewsRepository.UPDATING_STATE, "updating")
        val currentLLM = settingsManager.getString(MewsRepository.CURRENT_LLM_MODEL, "gemini-2.0-flash")
        val llmApiKey = settingsManager.getString(MewsRepository.USER_API_KEY, "")
        val rssLastUpdate = settingsManager.getLong(MewsRepository.LAST_RSS_UPDATE, 0L)
        val rssUpdateInterval = settingsManager.getInt(MewsRepository.RSS_UPDATE_INTERVAL, 30)
        val titlesPeriod = settingsManager.getInt(MewsRepository.TITLES_PERIOD, 24)
        val titlesNum = settingsManager.getInt(MewsRepository.TITLES_NUM, 10)
        val filterTopics = settingsManager.getBoolean(MewsRepository.FILTER_TOPICS, false)

        val db = DbHelper(applicationContext)
        val fetcher = RssFetcher(db)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey)
        val summarizer = NewsSummarizer(db, llm, settingsManager)

        return try {
            try {
                withContext(Dispatchers.IO) {
                    val noFetchErrors = when ((System.currentTimeMillis() - rssLastUpdate) / 60000L > rssUpdateInterval) {
                        true -> fetcher.fetchAndStoreAll(messAliveTime = 120.toLong() * 3600).errors.isEmpty()
                        else -> true
                    }

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
                    }
                }

                settingsManager.saveString(MewsRepository.UPDATING_STATE, "off")
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