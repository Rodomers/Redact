package com.rds.mews.core.summarizer

import com.rds.mews.core.text.TokenEstimator
import com.rds.mews.localcore.GeminiModelOption
import com.rds.mews.localcore.ModelBatchConfig
import com.rds.mews.repositories.MewsRepository
import kotlin.math.max
import kotlin.math.sqrt

enum class BatchMode(
    val maxItems: Int,
    val maxTokens: Int = 150000
) {
    RAW_ITEMS(60),
    BLITZ_TOPICS(10),
    BASE_TOPICS(20)
}

class BatchController {
    private var consecutiveFailures: Int = 0
    private var successfulBatchesCount: Int = 0

    companion object {
        private const val TARGET_TOKENS = 150000
        private const val REFERENCE_NEWS_CHAR_LENGTH = 1000
        private const val MAX_NEWS_COUNT = 60
        private const val MAX_BASE_TOPICS_COUNT = 10
        private const val MAX_BLITZ_TOPICS_COUNT = 20
        private const val MAX_K = 1.5
        private const val MIN_K = 0.5


    }

    fun <T> buildBatches(
        items: List<T>,
        model: GeminiModelOption,
        isProbeFlag: Boolean = false,
        textExtractor: (T) -> String,
        batchMode: BatchMode
    ): List<List<T>> {
        if (items.isEmpty()) return emptyList()

        val modelConfig = MewsRepository.modelBatchInfo.value.find { it.model == model } ?: ModelBatchConfig(model)

        val resultBatches = mutableListOf<List<T>>()
        if (batchMode == BatchMode.RAW_ITEMS) {
            val shortItems = items.filter { textExtractor(it).length <= 1000 }.sortedBy { textExtractor(it).length }
            val longItems = items.filter { textExtractor(it).length > 1000 }.sortedBy { textExtractor(it).length }

            resultBatches.addAll(processGroup(shortItems, modelConfig, isProbeFlag, textExtractor))
            resultBatches.addAll(processGroup(longItems, modelConfig, isProbeFlag, textExtractor))
        } else {
            resultBatches.addAll(processGroup(items, modelConfig, isProbeFlag, textExtractor, batchMode))
        }

        return resultBatches
    }

    private fun <T> processGroup(
        items: List<T>,
        config: ModelBatchConfig,
        isProbeFlag: Boolean,
        textExtractor: (T) -> String,
        batchMode: BatchMode = BatchMode.RAW_ITEMS
    ): List<List<T>> {
        val batches = mutableListOf<List<T>>()
        var currentBatch = mutableListOf<T>()
        var currentBatchTokens = 0
        var currentBatchCharSum = 0

        val effectiveSafetyTokens = if (isProbeFlag) (config.kSafeTokens * 1.15).coerceAtMost(MAX_K) else config.kSafeTokens
        val effectiveSafetyMessages = if (isProbeFlag) (config.kSafeMessages * 1.15).coerceAtMost(MAX_K) else config.kSafeMessages
        val effectiveSafetyBlitz = if (isProbeFlag) (config.kSafeBlitz * 1.15).coerceAtMost(MAX_K) else config.kSafeBlitz
        val effectiveSafetyTopics = if (isProbeFlag) (config.kSafeTopics * 1.15).coerceAtMost(MAX_K) else config.kSafeTopics

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

            val dynamicTokenLimit = (batchMode.maxTokens * kScale * effectiveSafetyTokens).toInt()
            val multiplier = when (batchMode) {
                BatchMode.RAW_ITEMS -> effectiveSafetyMessages
                BatchMode.BLITZ_TOPICS -> effectiveSafetyBlitz
                BatchMode.BASE_TOPICS -> effectiveSafetyTopics
            }
            val dynamicMaxCount = (batchMode.maxItems * multiplier).toInt()

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

    fun onBatchSuccess(model: GeminiModelOption, isProbe: Boolean, batchMode: BatchMode, kSafeTokens: Boolean) {
        val additional = if (isProbe) 0.15 else 0.02

        consecutiveFailures = 0
        successfulBatchesCount++
        MewsRepository.modifyModelBatchConfig(model) { currentConfig ->
            var updatedTokensSafety = currentConfig.kSafeTokens
            var updatedMessagesSafety = currentConfig.kSafeMessages
            var updatedBlitzSafety = currentConfig.kSafeBlitz
            var updatedTopicsSafety = currentConfig.kSafeTopics
            when (batchMode) {
                BatchMode.RAW_ITEMS -> {
                    if (!kSafeTokens) {
                        updatedMessagesSafety += additional
                    } else updatedTokensSafety += additional
                }

                BatchMode.BLITZ_TOPICS -> updatedBlitzSafety += additional
                BatchMode.BASE_TOPICS -> updatedTopicsSafety += additional
            }
            currentConfig.copy(
                kSafeTokens = updatedTokensSafety.coerceAtMost(MAX_K),
                kSafeMessages = updatedMessagesSafety.coerceAtMost(MAX_K),
                kSafeBlitz = updatedBlitzSafety.coerceAtMost(MAX_K),
                kSafeTopics = updatedTopicsSafety.coerceAtMost(MAX_K),
                successTokensCount = currentConfig.successTokensCount + 1,
                successMessagesCount = currentConfig.successMessagesCount + 1
            )
        }
    }

    fun onBatchFailure(model: GeminiModelOption, isProbe: Boolean, batchMode: BatchMode, kSafeTokens: Boolean, savedRatio: Double = 0.0) {
        if (isProbe) return

        consecutiveFailures++
        if (consecutiveFailures >= 2) {
            MewsRepository.modifyModelBatchConfig(model) { currentConfig ->
                var targetDecreaseMessages: Double = currentConfig.kSafeMessages
                var targetDecreaseTokens: Double = currentConfig.kSafeTokens
                var targetDecreaseBlitz: Double = currentConfig.kSafeBlitz
                var targetDecreaseTopics: Double = currentConfig.kSafeTopics
                val multiplier = if (savedRatio in MIN_K..1.0) savedRatio else 0.7

                when (batchMode) {
                    BatchMode.RAW_ITEMS -> {
                        if (!kSafeTokens) {
                            targetDecreaseMessages *= multiplier
                        } else {
                            targetDecreaseTokens *= multiplier
                        }
                    }

                    BatchMode.BLITZ_TOPICS -> targetDecreaseBlitz *= multiplier
                    BatchMode.BASE_TOPICS -> targetDecreaseTopics *= multiplier
                }


                val updatedTokensSafety = targetDecreaseTokens.coerceAtLeast(MIN_K)
                val updatedMessagesSafety = targetDecreaseMessages.coerceAtLeast(MIN_K)
                val updatedBlitzSafety = targetDecreaseBlitz.coerceAtLeast(MIN_K)
                val updatedTopicSafety = targetDecreaseTopics.coerceAtLeast(MIN_K)

                currentConfig.copy(
                    kSafeTokens = updatedTokensSafety,
                    kSafeMessages = updatedMessagesSafety,
                    kSafeBlitz = updatedBlitzSafety,
                    kSafeTopics = updatedTopicSafety
                )
            }
            consecutiveFailures = 0
        }
    }

    fun shouldProbe(): Boolean {
        return successfulBatchesCount > 0 && successfulBatchesCount % 15 == 0
    }
}