package com.rds.mews.core.summarizer

import android.content.Context
import com.rds.mews.core.text.TokenEstimator
import com.rds.mews.localcore.GeminiModelOption
import com.rds.mews.localcore.ModelBatchConfig
import com.rds.mews.repositories.MewsRepository
import kotlin.math.max
import kotlin.math.sqrt

class BatchController(
    private val context: Context
) {
    private var consecutiveFailures: Int = 0
    private var successfulBatchesCount: Int = 0

    companion object {
        private const val TARGET_TOKENS = 15000
        private const val REFERENCE_NEWS_CHAR_LENGTH = 600
        private const val MAX_NEWS_COUNT = 80
        private const val MAX_K = 1.5
        private const val MIN_K = 0.5
    }

    fun <T> buildBatches(
        items: List<T>,
        model: GeminiModelOption,
        isProbeFlag: Boolean = false,
        textExtractor: (T) -> String
    ): List<List<T>> {
        if (items.isEmpty()) return emptyList()

        val modelConfig = MewsRepository.modelBatchInfo.value.find { it.model == model } ?: ModelBatchConfig(model)

        val shortItems = items.filter { textExtractor(it).length <= 1000 }.sortedBy { textExtractor(it).length }
        val longItems = items.filter { textExtractor(it).length > 1000 }.sortedBy { textExtractor(it).length }

        val resultBatches = mutableListOf<List<T>>()

        resultBatches.addAll(processGroup(shortItems, modelConfig, isProbeFlag, textExtractor))
        resultBatches.addAll(processGroup(longItems, modelConfig, isProbeFlag, textExtractor))

        return resultBatches
    }

    private fun <T> processGroup(
        items: List<T>,
        config: ModelBatchConfig,
        isProbeFlag: Boolean,
        textExtractor: (T) -> String
    ): List<List<T>> {
        val batches = mutableListOf<List<T>>()
        var currentBatch = mutableListOf<T>()
        var currentBatchTokens = 0
        var currentBatchCharSum = 0

        val effectiveSafetyTokens = if (isProbeFlag) (config.kSafeTokens * 1.15).coerceAtMost(MAX_K) else config.kSafeTokens
        val effectiveSafetyMessages = if (isProbeFlag) (config.kSafeMessages * 1.15).coerceAtMost(MAX_K) else config.kSafeMessages

        for (item in items) {
            val text = textExtractor(item)
            val itemTokens = try {
                TokenEstimator.estimate(text)
            } catch (_: Exception) {
                max(1, text.length / 3)
            }

            val newCharSum = currentBatchCharSum + text.length
            val newCount = currentBatch.size + 1
            val avgLen = (newCharSum.toDouble() / newCount).coerceAtLeast(1.0)

            val kScale = sqrt(REFERENCE_NEWS_CHAR_LENGTH.toDouble() / avgLen)

            val dynamicTokenLimit = (TARGET_TOKENS * kScale * effectiveSafetyTokens).toInt()
            val dynamicMaxCount = (MAX_NEWS_COUNT * effectiveSafetyMessages).toInt()

            if (((currentBatchTokens + itemTokens) > dynamicTokenLimit || currentBatch.size >= dynamicMaxCount) && currentBatch.isNotEmpty()) {
                batches.add(currentBatch)
                currentBatch = mutableListOf()
                currentBatchTokens = 0
                currentBatchCharSum = 0
            }

            currentBatch.add(item)
            currentBatchTokens += itemTokens
            currentBatchCharSum += text.length
        }

        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }

        return batches
    }

    fun onBatchSuccess(model: GeminiModelOption, isProbe: Boolean) {
        consecutiveFailures = 0
        if (!isProbe) {
            successfulBatchesCount++
            MewsRepository.modifyModelBatchConfig(model) { currentConfig ->
                val updatedTokensSafety = (currentConfig.kSafeTokens + 0.02).coerceAtMost(MAX_K)
                val updatedMessagesSafety = (currentConfig.kSafeMessages + 0.02).coerceAtMost(MAX_K)
                currentConfig.copy(
                    kSafeTokens = updatedTokensSafety,
                    kSafeMessages = updatedMessagesSafety,
                    successTokensCount = currentConfig.successTokensCount + 1,
                    successMessagesCount = currentConfig.successMessagesCount + 1
                )
            }
        }
    }

    fun onBatchFailure(model: GeminiModelOption, isProbe: Boolean, savedRatio: Double = 0.0) {
        if (isProbe) return

        consecutiveFailures++
        if (consecutiveFailures >= 2) {
            MewsRepository.modifyModelBatchConfig(model) { currentConfig ->
                val targetDecreaseTokens = if (savedRatio in MIN_K..1.0) savedRatio else currentConfig.kSafeTokens * 0.7
                val targetDecreaseMessages = if (savedRatio in MIN_K..1.0) savedRatio else currentConfig.kSafeMessages * 0.7

                val updatedTokensSafety = targetDecreaseTokens.coerceAtLeast(MIN_K)
                val updatedMessagesSafety = targetDecreaseMessages.coerceAtLeast(MIN_K)

                currentConfig.copy(
                    kSafeTokens = updatedTokensSafety,
                    kSafeMessages = updatedMessagesSafety
                )
            }
            consecutiveFailures = 0
        }
    }

    fun shouldProbe(): Boolean {
        return successfulBatchesCount > 0 && successfulBatchesCount % 15 == 0
    }
}