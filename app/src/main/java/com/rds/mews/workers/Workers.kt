package com.rds.mews.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rds.mews.core.DbHelper
import com.rds.mews.core.LLMClient
import com.rds.mews.core.NewsSummarizer
import com.rds.mews.core.RssFetcher
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
        val db = DbHelper(applicationContext)

        val currentLLM = MewsRepository.llmModel.first().apiModelName
        val llmApiKey = MewsRepository.userApiKey.first()
        val rssLastUpdate = MewsRepository.lastRssUpdate.first()
        val rssUpdateInterval = MewsRepository.rssUpdateInterval.first()
        val titlesPeriod = MewsRepository.titlesPeriod.first().num
        val titlesNum = MewsRepository.titlesNum.first().num
        val filterTopics = MewsRepository.filterTopics.first()
        val enableProxy = MewsRepository.proxyEnabled.first()

        val updateDeltaMills = System.currentTimeMillis() - MewsRepository.lastTitlesUpdate.first()
        val titlesUpdatePeriod = if (titlesPeriod == 0) {
            updateDeltaMills / 3600000L + 1
        } else titlesPeriod ?: 0L

        val fetcher = RssFetcher(db)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey, enableProxy = enableProxy)
        val summarizer = NewsSummarizer(db, llm)

        MewsRepository.setUpdatingTitles(true)
        MewsRepository.setUpdatingState("updating")

        try {
            withContext(Dispatchers.IO) {
                if (isStopped) return@withContext

                MewsRepository.setUpdatingProgress(0.1f)

                MewsRepository.setUpdatingState("parsing")
                val result = fetcher.fetchAndStoreAll(messAliveTime = titlesUpdatePeriod.toLong() * 3600).errors.isEmpty()
                MewsRepository.setLastRssUpdate(System.currentTimeMillis())

                if (isStopped) return@withContext

                if (true) {
//                    val titles = db.getTitles()
//                    if (titles.none { it.text.contains("<промежуточный текст>") || it.time == 0L || it.sources.contains("<промежуточный текст>") }) {
//                        db.titlesTimeKill(0)
//                    }
                    var iter = 0
                    var res: SummarizationResult = SummarizationResult.Failure(
                        SummarizationErrorType.UNKNOWN_ERROR)
                    while (MewsRepository.updatingTitles.first() && iter <= 3 && !isStopped) {
                        res = summarizer.summarizeTopics(
                            maxTopics = titlesNum,
                            messageSeconds = titlesUpdatePeriod.toLong() * 3600,
                            readyFunc = {
                                MewsRepository.setUpdatingTitles(false)
                            },
                            filterTopics = filterTopics
                        )
                        if (res is SummarizationResult.Success) break
                        iter++
                    }

                    when (res) {
                        is SummarizationResult.Success -> {
                            MewsRepository.clearError()
                            MewsRepository.triggerTitlesRefresh()
                        }
                        is SummarizationResult.Failure -> MewsRepository.saveLastError(res)
                    }
                }
            }

            return Result.success()
        } catch (e: CancellationException) {
            MewsRepository.clearError()
            MewsRepository.saveLastError(SummarizationResult.Failure(SummarizationErrorType.JOB_CANCELLED))
            println("TitlesUpdateWorker: Пойман CancellationException. Работа была отменена системой.")
            throw e
        } catch (e: Exception) {
            println("TitlesUpdateWorker: Поймана ошибка. Тип: ${e.javaClass.simpleName}, Сообщение: ${e.message}")
            e.printStackTrace()
            val errorResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
            MewsRepository.saveLastError(errorResult)
            return Result.retry()
        }  finally {
            if (!MewsRepository.isInitialized) {
                val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                MewsRepository.initialize(applicationContext, appScope)
                println("TitlesUpdateWorker: Репозиторий инициализирован с новым AppScope.")
            }
            MewsRepository.triggerTitlesRefresh()

            println("TitlesUpdateWorker: Вход в блок FINALLY. isStopped = $isStopped")
            MewsRepository.setUpdatingProgress(1f)
            MewsRepository.setUpdatingTitles(false)
            delay(500L)
            MewsRepository.setUpdatingState("off")
            MewsRepository.setUpdatingProgress(0f)
            println("TitlesUpdateWorker: Состояние обновлено на 'off'.")
        }
    }
}