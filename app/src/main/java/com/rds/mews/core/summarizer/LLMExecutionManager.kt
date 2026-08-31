package com.rds.mews.core.summarizer

import com.rds.mews.localcore.GeminiModelOption
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.GeminiException
import com.rds.mews.settings_manager.SummarizationErrorType

class LLMExecutionManager(
    private val llmClient: LLMClient,
    private val batchController: BatchController
) {
    private data class PendingBatch<T>(val data: List<T>, val attemptsLeft: Int)

    suspend fun <T, R> execute(
        items: List<T>,
        idExtractor: (T) -> Any,
        textExtractor: (T) -> String,
        batchMode: BatchMode,
        promptBuilder: suspend (List<T>) -> String,
        responseParser: suspend (String, List<T>) -> R,
        onBatchSuccess: (suspend (processedBatch: List<T>, remainingItems: List<T>, result: R) -> Unit)? = null,
        onBatchFailure: (suspend (failedBatch: List<T>, error: GeminiException) -> Unit)? = null
    ): List<R> {
        if (items.isEmpty()) return emptyList()

        val results = mutableListOf<R>()
        val remainingItems = items.toMutableList()

        while (remainingItems.isNotEmpty()) {
            val currentModel = getCurrentModelOption()
            val isProbe = batchController.shouldProbe()

            val batches = batchController.buildBatches(
                items = remainingItems,
                model = currentModel,
                isProbeFlag = isProbe,
                textExtractor = textExtractor,
                batchMode = batchMode
            )

            if (batches.isEmpty()) break

            val queue = ArrayDeque<PendingBatch<T>>()
            batches.forEach { queue.addLast(PendingBatch(it, 3)) }

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val batch = current.data

                val kSafeTokens = batch.firstOrNull()?.let { textExtractor(it).length > 1000 } ?: false

                try {
                    val prompt = promptBuilder(batch)
                    val response = llmClient.sendPrompt(prompt)
                    val parsedResult = responseParser(response, batch)

                    results.add(parsedResult)
                    batchController.onBatchSuccess(currentModel, isProbe, batchMode, kSafeTokens)

                    val processedIds = batch.map(idExtractor).toSet()
                    remainingItems.removeAll { item -> idExtractor(item) in processedIds }

                    onBatchSuccess?.invoke(batch, remainingItems.toList(), parsedResult)

                } catch (e: GeminiException) {
                    val isFatal = e.errorType in listOf(
                        SummarizationErrorType.QUOTA_EXCEEDED,
                        SummarizationErrorType.API_KEY_INVALID,
                        SummarizationErrorType.NO_NETWORK,
                        SummarizationErrorType.NO_NEWS_TO_ANALYZE,
                        SummarizationErrorType.JOB_CANCELLED
                    )

                    if (isFatal) {
                        if (e.errorType == SummarizationErrorType.QUOTA_EXCEEDED) {
                            batchController.onBatchFailure(currentModel, isProbe, batchMode, kSafeTokens)

                            val switched = llmClient.switchToFallbackModel()
                            if (!switched) {
                                onBatchFailure?.invoke(batch, e)
                                val failedIds = batch.map(idExtractor).toSet()
                                remainingItems.removeAll { item -> idExtractor(item) in failedIds }
                                throw e
                            }
                            queue.addFirst(current)
                            break
                        } else {
                            onBatchFailure?.invoke(batch, e)
                            val failedIds = batch.map(idExtractor).toSet()
                            remainingItems.removeAll { item -> idExtractor(item) in failedIds }
                            throw e
                        }
                    } else {
                        batchController.onBatchFailure(currentModel, isProbe, batchMode, kSafeTokens)

                        if (current.attemptsLeft > 1) {
                            if (batch.size > 1) {
                                val splitIndex = (batch.size * 0.618).toInt().coerceAtLeast(1)
                                val left = batch.subList(0, splitIndex)
                                val right = batch.subList(splitIndex, batch.size)
                                queue.addFirst(PendingBatch(right, current.attemptsLeft - 1))
                                queue.addFirst(PendingBatch(left, current.attemptsLeft - 1))
                            } else {
                                queue.addFirst(PendingBatch(batch, current.attemptsLeft - 1))
                            }
                        } else {
                            onBatchFailure?.invoke(batch, e)
                            val failedIds = batch.map(idExtractor).toSet()
                            remainingItems.removeAll { item -> idExtractor(item) in failedIds }
                        }
                    }
                }
            }
        }

        return results
    }

    private fun getCurrentModelOption(): GeminiModelOption {
        return MewsRepository.geminiModelsList.find {
            it.apiModelName == llmClient.currentModelApiName
        } ?: MewsRepository.defaultModel
    }
}