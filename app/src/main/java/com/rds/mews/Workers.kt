package com.rds.mews

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.SystemFileSystem

class RssUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val db = DbHelper(applicationContext)
        val fetcher = RssFetcher(db)
        val repository = MewsRepository

        val titlesPeriod = repository.titlesPeriod.value

        return try {
            val workManager = WorkManager.getInstance(applicationContext)
            workManager.cancelAllWorkByTag("rss-update-work")
            workManager.cancelAllWorkByTag("rss-update-work-once")

            withContext(Dispatchers.IO) {
                fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600)
                repository.setLastRssUpdate(System.currentTimeMillis())
                println("Worker: parsing finished")
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        } finally {
            val nextRssUpdate = System.currentTimeMillis() + repository.rssUpdateInterval.value * 3600 * 1000L
            AlarmScheduler.schedule(applicationContext, nextRssUpdate)
        }
    }
}

class TitlesUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val repository = MewsRepository
        repository.setUpdatingTitles(true)
        repository.setUpdatingState("updating")
        val currentLLM = repository.currentLlmModel.value
        val llmApiKey = repository.userApiKey.value
        val rssLastUpdate = repository.lastRssUpdate.value
        val rssUpdateInterval = repository.rssUpdateInterval.value
        val titlesPeriod = repository.titlesPeriod.value
        val titlesNum = repository.titlesNum.value
        val filterTopics = repository.filterTopics.value
        val autoUpdate = repository.titlesAutoUpdate.value

        val db = DbHelper(applicationContext)
        val fetcher = RssFetcher(db)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey)
        val summarizer = NewsSummarizer(db, llm)

        return try {
            try {
                withContext(Dispatchers.IO) {
                    val noFetchErrors = when ((System.currentTimeMillis() - rssLastUpdate) / 60000L > rssUpdateInterval) {
                        true -> {
                            val result = fetcher.fetchAndStoreAll(messAliveTime = 120.toLong() * 3600).errors.isEmpty()
                            repository.setLastRssUpdate(System.currentTimeMillis())
                            result
                        }
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
                        while (repository.updatingTitles.value && iter <= 5) {
                            res = summarizer.summarizeTopics(
                                maxTopics = titlesNum,
                                messageSeconds = titlesPeriod.toLong() * 3600,
                                readyFunc = {
                                    repository.setUpdatingTitles(false)
                                    if (autoUpdate) {
                                        val nextRunTimeMills = System.currentTimeMillis() + titlesPeriod * 3600 * 1000L
                                        AlarmScheduler.schedule(applicationContext, nextRunTimeMills)
                                    }
                                },
                                filterTopics = filterTopics
                            )
                            iter++
                        }

                        when (res) {
                            is SummarizationResult.Success -> repository.clearError()
                            is SummarizationResult.Failure -> repository.saveLastError(res)
                        }
                    }
                }

                repository.setUpdatingState("off")
                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                val errorResult = SummarizationResult.Failure(
                    SummarizationErrorType.UNKNOWN_ERROR,
                    e
                )
                repository.saveLastError(errorResult)
                Result.failure()
            }
        } catch(e: Exception) {
            e.printStackTrace()
            val errorResult = SummarizationResult.Failure(
                SummarizationErrorType.UNKNOWN_ERROR,
                e
            )
            repository.saveLastError(errorResult)
            Result.failure()
        } finally {
            repository.setUpdatingTitles(false)
        }
    }
}