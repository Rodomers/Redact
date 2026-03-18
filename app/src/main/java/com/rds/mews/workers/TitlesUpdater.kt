package com.rds.mews.workers

import android.util.Log
import com.rds.mews.core.LLMClient
import com.rds.mews.core.NewsSummarizer
import com.rds.mews.core.RssFetcher
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.TitlesPeriod
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.SummarizationErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class TitlesUpdater() {
    suspend fun performUpdate(isOneTime: Boolean): SummarizationResult {
        if (!MewsRepository.isInitialized) {
            return SummarizationResult.Failure(
                SummarizationErrorType.UNKNOWN_ERROR,
                Exception("Repository not initialized")
            )
        }

        // Объявляем переменные снаружи try, чтобы иметь к ним доступ в finally
        var llmClient: LLMClient? = null
        var finalResult: SummarizationResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)

        try {
            val lastUpdate = MewsRepository.lastTitlesUpdate.first()
            val updateDeltaMills = System.currentTimeMillis() - lastUpdate
            val enableProxy = MewsRepository.proxyEnabled.first()
            val currentLLM = MewsRepository.llmModel.first().apiModelName
            val llmApiKey = MewsRepository.userApiKey.first()
            val titlesUpdatePeriodSetting = MewsRepository.titlesPeriod.first().num

            val titlesPeriod = if (!isOneTime || titlesUpdatePeriodSetting == null) {
                if (lastUpdate == 0L) TitlesPeriod.HRS_24.num ?: 24
                else (updateDeltaMills / 3600000L + 1).coerceIn(12, 120)
            } else {
                titlesUpdatePeriodSetting
            }

            val titlesNum = MewsRepository.titlesNum.first().num
            val filterTopics = MewsRepository.filterTopics.first()

            val fetcher = RssFetcher()

            llmClient = LLMClient(MODEL = currentLLM, apiKey = llmApiKey, enableProxy = enableProxy)
            val summarizer = NewsSummarizer(llmClient)

            MewsRepository.setUpdatingTitles(true)
            MewsRepository.setUpdatingState("updating")

            currentCoroutineContext().ensureActive()

            MewsRepository.setUpdatingProgress(0.1f)

            val lastRssTime = MewsRepository.lastRssUpdate.first()
            if (System.currentTimeMillis() - lastRssTime > 900000L) {
                MewsRepository.setUpdatingState("parsing")
                fetcher.fetchAndStoreAll()
            }
            MewsRepository.setLastRssUpdate(System.currentTimeMillis())

            currentCoroutineContext().ensureActive()

            if (isOneTime || updateDeltaMills >= 7200000L) {
                var iter = 0

                var continueUpdate = true

                while (continueUpdate && iter <= 1) {
                    currentCoroutineContext().ensureActive()

                    finalResult = summarizer.summarizeTopics(
                        maxTopics = titlesNum,
                        messageSeconds = titlesPeriod.toLong() * 3600,
                        readyFunc = {
                            continueUpdate = false
                            MewsRepository.setUpdatingTitles(false)
                        },
                        filterTopics = filterTopics
                    )

                    if (finalResult is SummarizationResult.Success) break
                    iter++
                }

                if (finalResult is SummarizationResult.Success) {
                    MewsRepository.delTitles(System.currentTimeMillis() - MewsRepository.titlesKeeping.value.ms)
                    MewsRepository.clearError()
                } else if (finalResult is SummarizationResult.Failure) {
                    MewsRepository.saveLastError(finalResult)
                }
            } else {
                finalResult = SummarizationResult.Failure(SummarizationErrorType.SMALL_INTERVAL)
            }

        } catch (e: Exception) {
            val error = if (e is kotlinx.coroutines.CancellationException) {
                Log.d("TitlesUpdater", "Задача отменена")
                SummarizationResult.Failure(SummarizationErrorType.JOB_CANCELLED)
            } else {
                Log.e("TitlesUpdater", "Критическая ошибка обновления: ${e.message}", e)
                SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
            }

            MewsRepository.saveLastError(error)
            finalResult = error

            if (e is kotlinx.coroutines.CancellationException) throw e

        } finally {
            Log.d("TitlesUpdater", "FINALLY block executed")

            try {
                llmClient?.close()
            } catch (e: Exception) {
                Log.e("TitlesUpdater", "Error closing LLMClient", e)
            }

            MewsRepository.setUpdatingProgress(1f)
            MewsRepository.setUpdatingTitles(false)

            withContext(Dispatchers.IO) { delay(500L) }

            MewsRepository.setUpdatingState("off")
            MewsRepository.setUpdatingProgress(0f)
        }

        return finalResult
    }
}