package com.rds.mews.core

import java.text.BreakIterator
import java.util.Locale
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

object SnippetExtractor {
    /**
     * Выделяет главное предложение (сниппет) из текста.
     * Автоматически определяет язык и использует соответствующие стоп-слова.
     */
    fun extractSnippet(context: Context, text: String): String {
        val cleanText = text.replace(Regex("[*`_]"), "")
        if (cleanText.isBlank()) return ""

        val langCode = StopWordsManager.detectLanguage(context, cleanText)

        val stopWords = StopWordsManager.getStopWords(context, langCode)

        val locale = if (langCode == "en") Locale.ENGLISH else Locale(langCode)
        val sentences = splitToSentences(cleanText, locale)

        if (sentences.size <= 2) return sentences.firstOrNull() ?: cleanText

        val wordFrequencies = mutableMapOf<String, Int>()

        sentences.forEach { sentence ->
            getMeaningfulWords(sentence, stopWords).forEach { word ->
                wordFrequencies[word] = (wordFrequencies[word] ?: 0) + 1
            }
        }

        var bestSentence = sentences[0]
        var maxScore = -1.0

        sentences.forEachIndexed { index, sentence ->
            val words = getMeaningfulWords(sentence, stopWords)
            if (words.isNotEmpty()) {
                var score = words.sumOf { wordFrequencies[it] ?: 0 }.toDouble() / words.size

                if (index == 0) score *= 2.0
                else if (index == 1) score *= 1.5
                else if (index == sentences.lastIndex) score *= 0.8

                if (score > maxScore) {
                    maxScore = score
                    bestSentence = sentence
                }
            }
        }

        return bestSentence.trim()
    }

    private fun splitToSentences(text: String, locale: Locale): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotBlank()) {
                sentences.add(sentence)
            }
            start = end
            end = iterator.next()
        }
        return sentences
    }

    private fun getMeaningfulWords(sentence: String, stopWords: Set<String>): List<String> {
        return sentence.lowercase()
            .split(Regex("[^\\p{L}0-9]+"))
            .filter { it.length > 1 && !stopWords.contains(it) }
    }
}

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

    /**
     * Инициализация: читаем languages.json и (опционально) сразу грузим все списки.
     * Лучше вызывать это в корутине при старте приложения.
     */
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

    /**
     * Главный метод: Определяет язык текста по количеству совпадений стоп-слов.
     * @return Код языка (например, "ru", "en"). Фолбэк -> "en".
     */
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

    /**
     * Возвращает список стоп-слов для заданного кода языка.
     */
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
                    if (trimmed.isNotEmpty()) {
                        words.add(trimmed)
                    }
                }
            }
            stopWordsCache[code] = words
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load stop words for $code ($fileName.txt): ${e.message}")
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