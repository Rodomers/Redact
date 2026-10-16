package com.rds.mews.core.summarizer

import com.rds.mews.localcore.GeminiModelOption
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.GeminiException
import com.rds.mews.settings_manager.SummarizationErrorType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        val stateMutex = Mutex()
        var lastFatalKeyException: GeminiException? = null

        while (remainingItems.isNotEmpty()) {
            val activeKeys = llmClient.getAvailableKeys()
            if (activeKeys.isEmpty()) {
                val fatalError = lastFatalKeyException ?: GeminiException(
                    SummarizationErrorType.QUOTA_EXCEEDED,
                    "All configured API keys exhausted or invalid"
                )
                onBatchFailure?.invoke(remainingItems.toList(), fatalError)
                remainingItems.clear()
                throw fatalError
            }

            val primaryModel = getModelOptionForKey(activeKeys.first())
            val isProbe = batchController.shouldProbe()

            val batches = batchController.buildBatches(
                items = remainingItems,
                model = primaryModel,
                isProbeFlag = isProbe,
                textExtractor = textExtractor,
                batchMode = batchMode
            )

            if (batches.isEmpty()) break

            val queue = ArrayDeque<PendingBatch<T>>()
            batches.forEach { queue.addLast(PendingBatch(it, 3)) }

            var abortWaveException: GeminiException? = null

            coroutineScope {
                for (workerKey in activeKeys) {
                    launch {
                        while (true) {
                            val current = stateMutex.withLock {
                                if (abortWaveException != null || queue.isEmpty()) null
                                else queue.removeFirst()
                            } ?: break

                            val batch = current.data
                            val workerModel = getModelOptionForKey(workerKey)
                            val kSafeTokens = batch.firstOrNull()?.let { textExtractor(it).length > 1000 } ?: false

                            try {
                                val prompt = promptBuilder(batch)
                                val response = llmClient.sendPrompt(prompt, targetApiKey = workerKey)
                                println("LLM Response:\n$response")
                                val parsedResult = responseParser(response, batch)

                                stateMutex.withLock {
                                    results.add(parsedResult)
                                    batchController.onBatchSuccess(workerModel, isProbe, batchMode, kSafeTokens)

                                    val processedIds = batch.map(idExtractor).toSet()
                                    remainingItems.removeAll { item -> idExtractor(item) in processedIds }

                                    onBatchSuccess?.invoke(batch, remainingItems.toList(), parsedResult)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: GeminiException) {
                                val isFatal = e.errorType in listOf(
                                    SummarizationErrorType.QUOTA_EXCEEDED,
                                    SummarizationErrorType.API_KEY_INVALID,
                                    SummarizationErrorType.NO_NETWORK,
                                    SummarizationErrorType.NO_NEWS_TO_ANALYZE,
                                    SummarizationErrorType.JOB_CANCELLED
                                )

                                if (isFatal) {
                                    when (e.errorType) {
                                        SummarizationErrorType.QUOTA_EXCEEDED -> {
                                            stateMutex.withLock {
                                                val switched = llmClient.switchToFallbackModel(workerKey)
                                                queue.addFirst(current)
                                                if (!switched) {
                                                    llmClient.markKeyExhausted(workerKey)
                                                    lastFatalKeyException = e
                                                    return@launch
                                                }
                                            }
                                        }
                                        SummarizationErrorType.API_KEY_INVALID -> {
                                            stateMutex.withLock {
                                                queue.addFirst(current)
                                                llmClient.markKeyExhausted(workerKey)
                                                lastFatalKeyException = e
                                                return@launch
                                            }
                                        }
                                        else -> {
                                            stateMutex.withLock {
                                                if (abortWaveException == null) {
                                                    abortWaveException = e
                                                }
                                                queue.addFirst(current)
                                                return@launch
                                            }
                                        }
                                    }
                                } else {
                                    stateMutex.withLock {
                                        batchController.onBatchFailure(workerModel, isProbe, batchMode, kSafeTokens)

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
                    }
                }
            }

            abortWaveException?.let { fatal ->
                val unprocessed = queue.flatMap { it.data }
                if (unprocessed.isNotEmpty()) {
                    onBatchFailure?.invoke(unprocessed, fatal)
                }
                throw fatal
            }

            if (queue.isNotEmpty() && llmClient.getAvailableKeys().isEmpty()) {
                val fatalError = lastFatalKeyException ?: GeminiException(
                    SummarizationErrorType.QUOTA_EXCEEDED,
                    "All API keys failed during batch execution"
                )
                val unprocessed = queue.flatMap { it.data }
                onBatchFailure?.invoke(unprocessed, fatalError)
                val failedIds = unprocessed.map(idExtractor).toSet()
                remainingItems.removeAll { item -> idExtractor(item) in failedIds }
                throw fatalError
            }
        }

        return results
    }

    private fun getModelOptionForKey(key: String): GeminiModelOption {
        val modelApiName = llmClient.getModelForKey(key)
        return MewsRepository.geminiModelsList.find {
            it.apiModelName == modelApiName
        } ?: MewsRepository.defaultModel
    }
}