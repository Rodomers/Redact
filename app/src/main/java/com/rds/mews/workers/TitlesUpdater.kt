package com.rds.mews.workers

import android.content.Context
import com.rds.mews.core.DbHelper
import com.rds.mews.core.LLMClient
import com.rds.mews.core.NewsSummarizer
import com.rds.mews.core.RssFetcher
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.SummarizationErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

class TitlesUpdater(private val context: Context) {
    suspend fun performUpdate(isOneTime: Boolean): SummarizationResult {
        if (!MewsRepository.isInitialized) {
            return SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, Exception("Repository not initialized"))
        }

        val db = DbHelper(context)

        val updateDeltaMills = System.currentTimeMillis() - MewsRepository.lastTitlesUpdate.first()
        val enableProxy = MewsRepository.proxyEnabled.first()
        val currentLLM = MewsRepository.llmModel.first().apiModelName
        val llmApiKey = MewsRepository.userApiKey.first()
        val titlesUpdatePeriodSetting = MewsRepository.titlesPeriod.first().num

        val titlesPeriod = if (!isOneTime || titlesUpdatePeriodSetting == null) {
            updateDeltaMills / 3600000L + 1
        } else titlesUpdatePeriodSetting

        val titlesNum = MewsRepository.titlesNum.first().num
        val filterTopics = MewsRepository.filterTopics.first()

        val fetcher = RssFetcher(db)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey, enableProxy = enableProxy)
        val summarizer = NewsSummarizer(db, llm)

        val startTitles = db.getTitles().filter { it.text != "<промежуточный текст>" }.map { it.id }

        MewsRepository.setUpdatingTitles(true)
        MewsRepository.setUpdatingState("updating")

        var finalResult: SummarizationResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)

        try {
            currentCoroutineContext().ensureActive()

            MewsRepository.setUpdatingProgress(0.1f)
            MewsRepository.setUpdatingState("parsing")

            if (System.currentTimeMillis() - MewsRepository.lastRssUpdate.first() > 900000L) fetcher.fetchAndStoreAll(
                messAliveTime = titlesPeriod.toLong() * 3600
            )
            MewsRepository.setLastRssUpdate(System.currentTimeMillis())

            currentCoroutineContext().ensureActive()

            if (isOneTime || updateDeltaMills >= 7200000L) {
                var iter = 0
                while (MewsRepository.updatingTitles.first() && iter <= 3) {
                    currentCoroutineContext().ensureActive()

                    finalResult = summarizer.summarizeTopics(
                        maxTopics = titlesNum,
                        messageSeconds = titlesPeriod.toLong() * 3600,
                        readyFunc = { MewsRepository.setUpdatingTitles(false) },
                        filterTopics = filterTopics
                    )
                    if (finalResult is SummarizationResult.Success) break
                    iter++
                }

                val currentTitles = db.getTitles().filter { it.text != "<промежуточный текст>" }.map { it.id }
                if (currentTitles.sum() != startTitles.sum() && currentTitles.isNotEmpty()) {
                    MewsRepository.triggerTitlesRefresh()
                }

                if (finalResult is SummarizationResult.Success) {
                    MewsRepository.clearError()
                } else if (finalResult is SummarizationResult.Failure) {
                    MewsRepository.saveLastError(finalResult)
                }
            } else {
                finalResult = SummarizationResult.Success
            }

        } catch (e: Exception) {
            val error = if (e is kotlinx.coroutines.CancellationException) {
                println("TitlesUpdater: Задача отменена")
                SummarizationResult.Failure(SummarizationErrorType.JOB_CANCELLED)
            } else {
                println("TitlesUpdater: Ошибка ${e.message}")
                e.printStackTrace()
                SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
            }
            MewsRepository.saveLastError(error)
            finalResult = error
            if (e is kotlinx.coroutines.CancellationException) throw e

        } finally {
            println("TitlesUpdater: FINALLY block")
            MewsRepository.setUpdatingProgress(1f)
            MewsRepository.setUpdatingTitles(false)
            withContext(Dispatchers.IO) { delay(500L) }
            MewsRepository.setUpdatingState("off")
            MewsRepository.setUpdatingProgress(0f)
        }

        return finalResult
    }
}