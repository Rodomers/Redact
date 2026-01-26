package com.rds.mews.core

import android.content.Context
import java.text.BreakIterator
import java.util.Locale

object SnippetExtractor {

    fun extractSnippet(context: Context, text: String): String {
        val cleanText = TextSanitizer.sanitize(text)
        if (cleanText.isBlank()) return ""

        val langCode = StopWordsManager.detectLanguage(context, cleanText)
        val stopWords = StopWordsManager.getStopWords(context, langCode)

        val locale = if (langCode == "en") Locale.ENGLISH else Locale(langCode)
        val sentences = splitToSentences(cleanText, locale)

        val validSentences = filterJunkFooter(sentences)

        if (validSentences.isEmpty()) return cleanText.take(200)
        if (validSentences.size <= 2) return validSentences.joinToString(" ")

        val wordFrequencies = mutableMapOf<String, Int>()
        validSentences.forEach { sentence ->
            getMeaningfulWords(sentence, stopWords).forEach { word ->
                wordFrequencies[word] = (wordFrequencies[word] ?: 0) + 1
            }
        }

        data class ScoredSentence(val index: Int, val text: String, val score: Double)
        val scoredSentences = mutableListOf<ScoredSentence>()

        validSentences.forEachIndexed { index, sentence ->
            val words = getMeaningfulWords(sentence, stopWords)
            if (words.isNotEmpty()) {
                var score = words.sumOf { wordFrequencies[it] ?: 0 }.toDouble() / words.size

                if (index == 0) score *= 2.5
                else if (index == 1) score *= 1.5

                scoredSentences.add(ScoredSentence(index, sentence, score))
            }
        }

        return scoredSentences
            .sortedByDescending { it.score }
            .take(3)
            .sortedBy { it.index }
            .joinToString(" ") { it.text }
    }

    private fun filterJunkFooter(sentences: List<String>): List<String> {
        if (sentences.isEmpty()) return emptyList()
        val result = sentences.toMutableList()

        val last = result.last().lowercase()

        if (last.length < 30 && (last.contains(":") || last.contains("©"))) {
            result.removeAt(result.lastIndex)

            if (result.isNotEmpty()) {
                val preLast = result.last().lowercase()
                if (preLast.length < 30 && (preLast.contains(":") || preLast.contains("©"))) {
                    result.removeAt(result.lastIndex)
                }
            }
        }
        return result
    }

    private fun splitToSentences(text: String, locale: Locale): List<String> {
        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)
        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotBlank()) sentences.add(sentence)
            start = end
            end = iterator.next()
        }
        return sentences
    }

    private fun getMeaningfulWords(sentence: String, stopWords: Set<String>): List<String> {
        return sentence.lowercase()
            .split(Regex("[^\\p{L}0-9]+"))
            .filter { it.length > 2 && !stopWords.contains(it) }
    }
}