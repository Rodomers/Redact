package com.rds.mews.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rds.mews.core.DbHelper
import com.rds.mews.core.LLMClient
import com.rds.mews.core.NewsSummarizer
import com.rds.mews.core.RssFetcher
import com.rds.mews.localcore.SettingsManager
import com.rds.mews.localcore.SummarizationErrorType
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class RssUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = DbHelper(applicationContext)
        val settingsManager = SettingsManager(applicationContext)
        val enableProxy = settingsManager.getBoolean(MewsRepository.ENABLE_PROXY, false)
        val fetcher = RssFetcher(db, settingsManager, enableProxy)

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
            Result.retry()
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

        val currentLLM = settingsManager.getString(MewsRepository.CURRENT_LLM_MODEL, MewsRepository.defaultModel.key)
        val llmApiKey = settingsManager.getString(MewsRepository.USER_API_KEY, MewsRepository.DEFAULT_GEMINI_API_KEY)
        val rssLastUpdate = settingsManager.getLong(MewsRepository.LAST_RSS_UPDATE, 0L)
        val rssUpdateInterval = settingsManager.getInt(MewsRepository.RSS_UPDATE_INTERVAL, 30)
        val titlesPeriod = settingsManager.getInt(MewsRepository.TITLES_PERIOD, 24)
        val titlesNum = settingsManager.getInt(MewsRepository.TITLES_NUM, 10)
        val filterTopics = settingsManager.getBoolean(MewsRepository.FILTER_TOPICS, false)
        val enableProxy = settingsManager.getBoolean(MewsRepository.ENABLE_PROXY, false)

        val fetcher = RssFetcher(db, settingsManager)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey, enableProxy = enableProxy)
        val summarizer = NewsSummarizer(db, llm)

        settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, true)
        settingsManager.saveString(MewsRepository.UPDATING_STATE, "updating")

        try {
            withContext(Dispatchers.IO) {
                if (isStopped) return@withContext

                settingsManager.saveFloat(MewsRepository.UPDATING_PROGRESS, 0.1f)

                val needToFetchRss = (System.currentTimeMillis() - rssLastUpdate) / 60000L > rssUpdateInterval
                val noFetchErrors = if (needToFetchRss) {
                    val result = fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600).errors.isEmpty()
                    settingsManager.saveLong(MewsRepository.LAST_RSS_UPDATE, System.currentTimeMillis())

                    true
                } else {
                    true
                }

                if (isStopped) return@withContext

                if (noFetchErrors) {
//                    val titles = db.getTitles()
//                    if (titles.none { it.text.contains("<промежуточный текст>") || it.time == 0L || it.sources.contains("<промежуточный текст>") }) {
//                        db.titlesTimeKill(0)
//                    }
                    var iter = 0
                    var res: SummarizationResult = SummarizationResult.Failure(
                        SummarizationErrorType.UNKNOWN_ERROR)
                    while (settingsManager.getBoolean(MewsRepository.UPDATING_TITLES, false) && iter <= 5 && !isStopped) {
                        res = summarizer.summarizeTopics(
                            maxTopics = titlesNum,
                            messageSeconds = titlesPeriod.toLong() * 3600,
                            readyFunc = {
                                settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, false)
                            },
                            filterTopics = filterTopics,
                            settingsManager = settingsManager
                        )
                        if (res is SummarizationResult.Success) break
                        iter++
                    }

                    when (res) {
                        is SummarizationResult.Success -> {
                            settingsManager.clearLastError()
                            MewsRepository.triggerTitlesRefresh()
                        }
                        is SummarizationResult.Failure -> settingsManager.saveLastError(res)
                    }
                }
            }

            return Result.success()
        } catch (e: CancellationException) {
            settingsManager.clearLastError()
            settingsManager.saveLastError(SummarizationResult.Failure(SummarizationErrorType.JOB_CANCELLED))
            println("TitlesUpdateWorker: Пойман CancellationException. Работа была отменена системой.")
            throw e
        } catch (e: Exception) {
            println("TitlesUpdateWorker: Поймана ошибка. Тип: ${e.javaClass.simpleName}, Сообщение: ${e.message}")
            e.printStackTrace()
            val errorResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
            settingsManager.saveLastError(errorResult)
            return Result.retry()
        }  finally {
            if (!MewsRepository.isInitialized) {
                val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                MewsRepository.initialize(applicationContext, appScope)
                println("TitlesUpdateWorker: Репозиторий инициализирован с новым AppScope.")
            }
            MewsRepository.triggerTitlesRefresh()

            println("TitlesUpdateWorker: Вход в блок FINALLY. isStopped = $isStopped")
            settingsManager.saveFloat(MewsRepository.UPDATING_PROGRESS, 1f)
            settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, false)
            delay(500L)
            settingsManager.saveString(MewsRepository.UPDATING_STATE, "off")
            settingsManager.saveFloat(MewsRepository.UPDATING_PROGRESS, 0f)
            println("TitlesUpdateWorker: Состояние обновлено на 'off'.")
        }
    }
}