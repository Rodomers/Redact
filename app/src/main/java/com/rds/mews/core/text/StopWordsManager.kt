package com.rds.mews.core.text

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

enum class LanguageGroup(val langCodes: List<String>) {
    LATIN(
        listOf(
            "ca", "cs", "da", "nl", "en", "fi", "fr", "de",
            "id", "ms", "it", "nb", "pl", "pt", "ro", "sk",
            "es", "sv", "tr", "vi"
        )
    ),
    CYRILLIC(
        listOf(
            "bg", "ru", "uk"
        )
    ),
    CJK(
        listOf(
            "zh", "ja", "ko"
        )
    ),
    OTHER(
        listOf(
            "ar",
            "gu",
            "he",
            "hi",
            "fa",
            "el"
        )
    );

    companion object {
        fun findGroup(langCode: String): LanguageGroup? {
            return entries.find { it.langCodes.contains(langCode) }
        }
    }
}

object StopWordsManager {

    private const val TAG = "StopWordsManager"
    private const val ASSETS_FOLDER = "stopwords"

    private val stopWordsCache = ConcurrentHashMap<String, Set<String>>()
    private val languageMap = ConcurrentHashMap<String, String>()

    private const val TOKENIZE_REGEX = "[^\\p{L}]+"

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

    fun fastLangDetect(text: String): LanguageGroup {
        val count = mutableListOf(0, 0, 0, 0)
        val groups = listOf(LanguageGroup.LATIN, LanguageGroup.CYRILLIC, LanguageGroup.CJK,
            LanguageGroup.OTHER)
        text.take(150).forEach { ch ->
            if (ch.isLetter()) {
                val code = ch.code
                when (code) {
                    in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F -> count[0]++
                    in 0x0400..0x04FF -> count[1]++
                    in 0x4E00..0x9FFF, in 0x3040..0x30FF, in 0xAC00..0xD7AF -> count[2]++
                    else -> count[3]++
                }
            }
        }
        return groups[count.indexOfLast{ it == count.max() }]
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
            .split(Regex(TOKENIZE_REGEX))
            .filter { it.length > 1 }
    }
}