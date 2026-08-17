package com.rds.mews.core.text

import kotlin.math.ceil

object TokenEstimator {

    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        val langGroup = StopWordsManager.fastLangDetect(text)

        val charsPerToken = when (langGroup) {
            LanguageGroup.CJK -> 1.0
            LanguageGroup.CYRILLIC -> 2.5
            else -> 4.0
        }

        val tokenCount = ceil(text.length / charsPerToken).toInt()
        return tokenCount + 10
    }

    fun truncateToLimit(text: String, maxTokens: Int): String {
        val estimated = estimate(text)
        if (estimated <= maxTokens) return text

        val ratio = maxTokens.toDouble() / estimated.toDouble()
        val newLength = (text.length * ratio * 0.9).toInt()

        return text.take(newLength) + "..."
    }
}