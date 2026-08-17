package com.rds.mews.core.summarizer

import android.util.Log
import com.rds.mews.core.SharedHttpClient
import com.rds.mews.repositories.MewsRepository
import com.rds.mews.settings_manager.GeminiException
import com.rds.mews.settings_manager.SummarizationErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

class SmartRateLimiter(private val minIntervalMs: Long = 4500L) {
    private val mutex = Mutex()
    private var nextAllowedTime = 0L

    suspend fun waitIfNeeded() {
        mutex.withLock {
            val now = System.nanoTime() / 1_000_000
            if (now < nextAllowedTime) {
                delay((nextAllowedTime - now).milliseconds)
            }
            nextAllowedTime = (System.nanoTime() / 1_000_000) + minIntervalMs
        }
    }

    suspend fun penalize() {
        mutex.withLock {
            val now = System.nanoTime() / 1_000_000
            val penaltyTarget = now + 15000L
            if (penaltyTarget > nextAllowedTime) {
                nextAllowedTime = penaltyTarget
            }
        }
    }
}

class LLMClient(
    val apiKey: String = "",
    val MODEL: String = MewsRepository.defaultModel.apiModelName,
    private val URL_TEMPLATE: String = "https://generativelanguage.googleapis.com/v1beta/models/%MODEL%:generateContent",
    enableProxy: Boolean = false
) : Closeable {
    var currentModelApiName = MODEL.ifBlank { MewsRepository.defaultModel.apiModelName }
    var currentUrl = URL_TEMPLATE.replace("%MODEL%", currentModelApiName)

    private val rateLimiter = SmartRateLimiter()
    private val httpClient = SharedHttpClient.createInstance(MewsRepository.PROXY_ADDRESS, MewsRepository.SERVER_KEY, enableProxy = enableProxy)
    private val jsonParser = SharedHttpClient.jsonParser
    private val MAX_RETRIES = 3
    private val TAG = "LLMClient"

    fun switchToFallbackModel(): Boolean {
        val models = MewsRepository.geminiModelsList.filter { !it.apiModelName.lowercase().contains("pro") }
        val currentIndex = models.indexOfFirst { it.apiModelName == currentModelApiName }

        if (currentIndex > 0) {
            currentModelApiName = models[currentIndex - 1].apiModelName
            currentUrl = URL_TEMPLATE.replace("%MODEL%", currentModelApiName)
            return true
        } else if (currentIndex == -1) {
            currentModelApiName = models.first().apiModelName
            currentUrl = URL_TEMPLATE.replace("%MODEL%", currentModelApiName)
            return true
        }
        return false
    }

    suspend fun sendPrompt(prompt: String, customConfig: GenerationConfig? = null): String {
        val config = customConfig ?: GenerationConfig(
            temperature = 0.5,
            maxOutputTokens = 8192,
            responseMimeType = "application/json"
        )

        val requestBodyObj = GeminiRequest(
            contents = listOf(ContentInput(parts = listOf(PartInput(prompt)))),
            generationConfig = config
        )

        val requestBodyString = try {
            jsonParser.encodeToString(requestBodyObj)
        } catch (e: Exception) {
            throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Request serialization failed: ${e.message}")
        }

        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= MAX_RETRIES) {
            try {
                rateLimiter.waitIfNeeded()

                val response = httpClient.post(
                    url = currentUrl,
                    body = requestBodyString,
                    headers = mapOf(
                        "x-goog-api-key" to apiKey,
                        "Content-Type" to "application/json"
                    )
                )

                val responseString = response.body
                val responseStatus = response.status

                if (responseStatus != 200) {
                    when (responseStatus) {
                        429 -> {
                            handle429Error(responseString, attempt)
                            attempt++
                            continue
                        }
                        403 -> throw GeminiException(SummarizationErrorType.API_KEY_INVALID, "HTTP 403: ${response.status}")
                        400 -> throw GeminiException(SummarizationErrorType.UNKNOWN_ERROR, "HTTP 400: Content too large or malformed request")
                        else -> {
                            if (attempt >= MAX_RETRIES) {
                                throw GeminiException(SummarizationErrorType.UNKNOWN_ERROR, "HTTP Status Error: ${response.status}")
                            }
                        }
                    }
                }

                val geminiResponse = try {
                    jsonParser.decodeFromString<GeminiResponse>(responseString)
                } catch (e: Exception) {
                    throw GeminiException(SummarizationErrorType.JSON_PARSING_FAILED, "Response deserialization failed: ${e.message}")
                }

                if (geminiResponse.error != null) {
                    val msg = geminiResponse.error.message ?: ""

                    if (msg.contains("Requests per day", true) || msg.contains("RequestsPerDay", true) || msg.contains("FreeTier", true)) {
                        throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, msg)
                    }

                    if (geminiResponse.error.code == 429 || msg.contains("quota", true)) {
                        handle429Error(msg, attempt)
                        attempt++
                        continue
                    }

                    if (msg.contains("key", true) || geminiResponse.error.code == 400) {
                        throw GeminiException(SummarizationErrorType.API_KEY_INVALID, msg)
                    }

                    attempt++
                    if (attempt > MAX_RETRIES) throw GeminiException(SummarizationErrorType.UNKNOWN_ERROR, msg)
                    delay(5000L.milliseconds)
                    continue
                }

                if (geminiResponse.promptFeedback?.blockReason != null) {
                    val reason = geminiResponse.promptFeedback.blockReason
                    throw GeminiException(SummarizationErrorType.CONTENT_BLOCKED, "Content blocked due to: $reason")
                }

                val resultText = geminiResponse.candidates?.takeIf { it.isNotEmpty() }
                    ?.flatMap { it.content?.parts ?: emptyList() }
                    ?.joinToString("\n") { it.text }

                if (resultText.isNullOrBlank()) {
                    throw GeminiException(SummarizationErrorType.EMPTY_ANSWER, "LLM returned empty or null answer")
                }
                return resultText

            } catch (e: GeminiException) {
                throw e
            } catch (_: TimeoutCancellationException) {
                throw GeminiException(SummarizationErrorType.NETWORK_TIMEOUT, "Network timeout during Gemini call")
            } catch (e: Exception) {
                lastException = e
                attempt++
                if (attempt <= MAX_RETRIES) delay(1000L.milliseconds)
            }
        }

        val errorMsg = lastException?.message ?: "Unknown connection error"
        throw GeminiException(SummarizationErrorType.NO_NETWORK, errorMsg)
    }


    private suspend fun handle429Error(responseString: String, attempt: Int) {
        rateLimiter.penalize()

        if (responseString.contains("Requests per day", ignoreCase = true) ||
            responseString.contains("RequestsPerDay", ignoreCase = true) ||
            responseString.contains("FreeTier", ignoreCase = true)) {
            throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, "Daily quota exceeded")
        }

        val waitTime = extractRetryTime(responseString)

        if (waitTime > 0) {
            delay((waitTime + 1000L).milliseconds)
        } else {
            val baseDelay = 15000L
            val exponentialMultiplier = 2.0.pow(attempt.toDouble()).toLong()
            val calcDelay = baseDelay * exponentialMultiplier
            delay(calcDelay.milliseconds)
        }

        if (attempt > MAX_RETRIES + 1) {
            throw GeminiException(SummarizationErrorType.QUOTA_EXCEEDED, "Daily quota limit reached (empirical timeout)")
        }
    }

    private fun extractRetryTime(message: String): Long {
        val regex = """(\d+(?:\.\d+)?)s""".toRegex()
        val match = regex.find(message)
        return match?.groupValues?.get(1)?.let { numStr ->
            try {
                val seconds = numStr.toDouble()
                (seconds * 1000).toLong()
            } catch (_: Exception) {
                0L
            }
        } ?: 0L
    }

    fun safeParseJsonArray(str: String): Pair<JSONArray, Boolean> {
        val len = str.length
        var cursor = 0

        while (cursor < len) {
            val start = str.indexOf('[', cursor)
            if (start == -1) break

            val end = findMatchingCloseBracket(str, start)

            if (end != -1) {
                val candidate = str.substring(start, end + 1)
                try {
                    val json = JSONArray(candidate)
                    return Pair(json, candidate.length != str.length)
                } catch (_: JSONException) {
                }
            }
            cursor = start + 1
        }

        return Pair(JSONArray(), true)
    }

    /**
     * [СПОРНАЯ ХРЕНЬ]: Извлечение и парсинг одиночного JSON-объекта (требуется для извлечения сущностей NER из пункта 3.1 спецификации).
     */
    fun safeParseJsonObject(str: String): JSONObject? {
        val len = str.length
        var cursor = 0

        while (cursor < len) {
            val start = str.indexOf('{', cursor)
            if (start == -1) break

            val end = findMatchingCloseBracket(str, start, '{', '}')

            if (end != -1) {
                val candidate = str.substring(start, end + 1)
                try {
                    return JSONObject(candidate)
                } catch (_: JSONException) {
                }
            }
            cursor = start + 1
        }
        return null
    }

    private fun findMatchingCloseBracket(str: String, start: Int, openChar: Char = '[', closeChar: Char = ']'): Int {
        var balance = 0
        var inString = false
        var isEscaped = false

        for (i in start until str.length) {
            val c = str[i]
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == openChar) {
                    balance++
                } else if (c == closeChar) {
                    balance--
                    if (balance == 0) return i
                }
            }
        }
        return -1
    }

    override fun close() {
        httpClient.close()
    }

    @Serializable
    data class GeminiRequest(
        val contents: List<ContentInput>,
        val generationConfig: GenerationConfig? = null
    )

    @Serializable
    data class GenerationConfig(
        val temperature: Double = 0.5,
        val maxOutputTokens: Int? = null,
        val responseMimeType: String? = null
    )

    @Serializable
    data class ContentInput(val parts: List<PartInput>)

    @Serializable
    data class PartInput(val text: String)

    @Serializable
    data class GeminiResponse(
        val candidates: List<Candidate>? = null,
        val error: GeminiError? = null,
        val promptFeedback: PromptFeedback? = null
    )

    @Serializable
    data class GeminiError(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null
    )

    @Serializable
    data class PromptFeedback(val blockReason: String? = null)

    @Serializable
    data class Candidate(
        val content: Content?,
        val finishReason: String? = null
    )

    @Serializable
    data class Content(val parts: List<Part>)

    @Serializable
    data class Part(val text: String)
}

suspend fun validateGeminiKey(apiKey: String, proxyIp: String, proxyKey: String, enableProxy: Boolean): Boolean {
    return withContext(Dispatchers.IO) {
        val client = SharedHttpClient.createInstance(proxyIp, proxyKey, enableProxy)
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val getUrl = client.get(url)
            getUrl.status == 200
        } catch (e: Exception) {
            Log.e("LLMClientValidation", "Validation failed: ${e.message}")
            false
        } finally {
            client.close()
        }
    }
}