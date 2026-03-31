package com.rds.mews.text_filters

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max

object TextComparator {
    fun areSimilar(text1: String, text2: String, threshold: Double): Boolean {
        if (text1.isEmpty() || text2.isEmpty()) return false
        if (text1 == text2) return true

        if (threshold >= 0.99) {
            return getMd5(text1) == getMd5(text2)
        }

        return calculateWeightedJacquard(text1, text2) >= threshold
    }

    private fun calculateWeightedJacquard(s1: String, s2: String): Double {
        val tokens1 = tokenizeWithWeights(s1)
        val tokens2 = tokenizeWithWeights(s2)

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        var intersectionWeight = 0.0
        val unionTokens = tokens1.keys.toMutableSet().apply { addAll(tokens2.keys) }

        tokens1.forEach { (token, weight) ->
            if (tokens2.containsKey(token)) {
                intersectionWeight += weight
            }
        }

        var unionWeight = 0.0
        unionTokens.forEach { token ->
            val w1 = tokens1[token] ?: 0.0
            val w2 = tokens2[token] ?: 0.0
            unionWeight += max(w1, w2)
        }

        return if (unionWeight == 0.0) 0.0 else intersectionWeight / unionWeight
    }

    private fun tokenizeWithWeights(text: String): Map<String, Double> {
        val words = text.trim().split(Regex("[^a-zA-Zа-яА-Я0-9]+")).filter { it.length > 2 }
        val resultMap = mutableMapOf<String, Double>()

        for (rawWord in words) {
            val lowerWord = rawWord.lowercase(Locale.getDefault())
            var weight = 1.0

            if (rawWord.first().isUpperCase() && (rawWord.length < 2 || rawWord[1].isLowerCase())) {
                weight = 3.0
            }
            else if (rawWord.length > 7) {
                weight = 1.5
            }

            resultMap[lowerWord] = max(resultMap[lowerWord] ?: 0.0, weight)
        }
        return resultMap
    }

    fun getMd5(input: String): String {
        val normalized = input.trim().lowercase().replace("\\s+".toRegex(), " ")
        val bytes = MessageDigest.getInstance("MD5").digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}