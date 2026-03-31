package com.rds.mews.text_filters

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.math.ceil

/**
 * Менеджер для работы со стоп-словами и определения языка текста
 * методом пересечения (Intersection Method).
 */
object StopWordsManager {

    private const val TAG = "StopWordsManager"
    private const val ASSETS_FOLDER = "stopwords"

    private val stopWordsCache = ConcurrentHashMap<String, Set<String>>()
    private val languageMap = ConcurrentHashMap<String, String>()

    @Volatile
    private var isInitialized = false

    fun init(context: Context, preloadAll: Boolean = true) {
        if (isInitialized) return

        try {
            val jsonString = context.assets.open("$ASSETS_FOLDER/languages.json")
                .bufferedReader()
                .use { it.readText() }

            val jsonObject = JSONObject(jsonString)
            val keys = jsonObject.keys()

            while (keys.hasNext()) {
                val code = keys.next()
                val fileName = jsonObject.getString(code)
                languageMap[code] = fileName
            }

            if (preloadAll) {
                languageMap.forEach { (code, fileName) ->
                    loadStopWordsForLang(context, code, fileName)
                }
            }

            isInitialized = true
            Log.d(TAG, "Initialized. Languages found: ${languageMap.size}")

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
        }
    }

    fun detectLanguage(context: Context, text: String): String {
        if (text.isBlank()) return "en"
        ensureLoaded(context)

        val tokens = tokenize(text)
        if (tokens.isEmpty()) return "en"

        var bestLang = "en"
        var maxIntersectionCount = -1

        stopWordsCache.forEach { (langCode, stopSet) ->
            val intersectionCount = tokens.count { stopSet.contains(it) }
            if (intersectionCount > maxIntersectionCount) {
                maxIntersectionCount = intersectionCount
                bestLang = langCode
            }
        }
        return if (maxIntersectionCount > 0) bestLang else "en"
    }

    fun getStopWords(context: Context, langCode: String): Set<String> {
        ensureLoaded(context)
        return stopWordsCache[langCode] ?: stopWordsCache["en"] ?: emptySet()
    }

    private fun ensureLoaded(context: Context) {
        if (stopWordsCache.isEmpty() && languageMap.isNotEmpty()) {
            languageMap.forEach { (code, fileName) ->
                loadStopWordsForLang(context, code, fileName)
            }
        } else if (!isInitialized) {
            init(context)
        }
    }

    private fun loadStopWordsForLang(context: Context, code: String, fileName: String) {
        if (stopWordsCache.containsKey(code)) return
        try {
            val words = mutableSetOf<String>()
            context.assets.open("$ASSETS_FOLDER/$fileName.txt").use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    val trimmed = line.trim().lowercase()
                    if (trimmed.isNotEmpty()) words.add(trimmed)
                }
            }
            stopWordsCache[code] = words
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load stop words for $code: ${e.message}")
            stopWordsCache[code] = emptySet()
        }
    }

    private fun tokenize(text: String): List<String> {
        val sample = if (text.length > 1000) text.take(1000) else text
        return sample.lowercase(Locale.getDefault())
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length > 1 }
    }
}

/**
 * Утилита для очистки текста от Markdown, ссылок и мусора.
 */
object TextSanitizer {

    private val IMAGE_PATTERN = Pattern.compile("!\\[.*?\\]\\(.*?\\)")
    private val LINK_TEXT_PATTERN = Pattern.compile("\\[(.*?)\\]\\(.*?\\)")
    private val FORMATTING_CHARS = Pattern.compile("[*`_#]")
    private val WHITESPACE_CLEANUP = Pattern.compile("\\s+")

    private val URL_DETECTOR = Pattern.compile("(https?://|t\\.me/)\\S+")

    /**
     * Очищает текст и удаляет последнее предложение, если в нем есть ссылка.
     */
    fun sanitize(text: String): String {
        if (text.isBlank()) return ""

        var clean = text
        clean = IMAGE_PATTERN.matcher(clean).replaceAll("")
        clean = LINK_TEXT_PATTERN.matcher(clean).replaceAll("$1")
        clean = FORMATTING_CHARS.matcher(clean).replaceAll("")
        clean = WHITESPACE_CLEANUP.matcher(clean).replaceAll(" ").trim()

        return cutFooterWithLink(clean)
    }

    private fun cutFooterWithLink(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).toMutableList()
        if (sentences.isEmpty()) return text

        var hasChanges = false
        while (sentences.isNotEmpty()) {
            val last = sentences.last()
            if (URL_DETECTOR.matcher(last).find()) {
                sentences.removeAt(sentences.lastIndex)
                hasChanges = true
            } else {
                break
            }
        }
        return if (hasChanges) sentences.joinToString(" ") else text
    }
}

object TokenEstimator {

    fun estimate(context: Context, text: String): Int {
        if (text.isEmpty()) return 0
        val langCode = StopWordsManager.detectLanguage(context, text)

        val charsPerToken = when (langCode) {
            "zh", "ja", "ko", "vi" -> 1.0
            "ru", "uk", "bg", "ar", "el", "he", "fa", "hi", "gu", "th" -> 2.5
            else -> 4.0
        }

        val tokenCount = ceil(text.length / charsPerToken).toInt()
        return tokenCount + 10
    }

    fun truncateToLimit(context: Context, text: String, maxTokens: Int): String {
        val estimated = estimate(context, text)
        if (estimated <= maxTokens) return text

        val ratio = maxTokens.toDouble() / estimated.toDouble()
        val newLength = (text.length * ratio * 0.9).toInt()

        return text.take(newLength) + "..."
    }
}