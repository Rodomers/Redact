package com.rds.mews.core.text

import com.rds.mews.core.text.graph.GraphCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object TextComparator {
    private val TOKENIZE_REGEX = Regex("""[\p{L}\p{Nd}]+""")

    fun areSimilar(text1: String, text2: String, threshold: Float): Boolean {
        if (text1.isEmpty() || text2.isEmpty()) return false
        if (text1 == text2) return true

        if (threshold >= 0.99) {
            return getMd5(text1) == getMd5(text2)
        }

        return countThreshold(text1, text2) >= threshold
    }

    fun countThreshold(text1: String, text2: String): Float {
        if (text1.isEmpty() || text2.isEmpty()) return 0f
        if (text1 == text2) return 1f

        val lang1 = StopWordsManager.fastLangDetect(text1)
        val lang2 = StopWordsManager.fastLangDetect(text2)
        if (lang1 != lang2) return 0f
        if (text1.length > 500 || text2.length > 500) return calculateDice(text1, text2)

        return when (lang1) {
            LanguageGroup.CYRILLIC -> calculateDice(text1, text2)
            else -> calculateLevenshtein(text1, text2)
        }
    }

    suspend fun combineWithSemanticScore(
        baseScore: Float,
        tokens1: Set<String>,
        tokens2: Set<String>,
        graphTrustFactor: Float = 1.0f
    ): Float {
        if (baseScore >= 0.99f || graphTrustFactor <= 0f) return baseScore.coerceIn(0f, 1f)
        if (tokens1.isEmpty() || tokens2.isEmpty()) return baseScore.coerceIn(0f, 1f)

        val semanticScore = calculateSemanticBonus(tokens1, tokens2)

        val finalScore = baseScore + graphTrustFactor * (1.0f - baseScore) * semanticScore

        return finalScore.coerceIn(0f, 1f)
    }

    private fun calculateLevenshtein(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0f

        var prevCost = IntArray(len2 + 1) { it }
        var currCost = IntArray(len2 + 1)

        for (i in 1..len1) {
            currCost[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                currCost[j] = minOf(
                    prevCost[j] + 1,
                    currCost[j - 1] + 1,
                    prevCost[j - 1] + cost
                )
            }
            val temp = prevCost
            prevCost = currCost
            currCost = temp
        }

        val maxLen = maxOf(len1, len2)
        return 1.0f - prevCost[len2].toFloat() / maxLen.toFloat()
    }

    private fun getBigrams(s: String): Map<String, Int> {
        val bigramsMap = mutableMapOf<String, Int>()
        if (s.length < 2) return bigramsMap
        for (i in 0..s.length - 2) {
            val bigram = s.substring(i, i + 2)
            bigramsMap[bigram] = (bigramsMap[bigram] ?: 0) + 1
        }
        return bigramsMap
    }

    private fun calculateDice(s1: String, s2: String, calibrationFactor: Float = 1.15f): Float {
        if (s1 == s2) return 1.0f
        if (s1.length < 2 || s2.length < 2) return 0.0f

        val b1 = getBigrams(s1)
        val b2 = getBigrams(s2)

        var intersection = 0
        for ((bigram, count1) in b1) {
            val count2 = b2[bigram] ?: 0
            intersection += minOf(count1, count2)
        }

        val totalBigrams = b1.values.sum() + b2.values.sum()
        if (totalBigrams == 0) return 0.0f

        val baseDice = (2.0f * intersection) / totalBigrams
        return minOf(1.0f, baseDice * calibrationFactor)
    }

    private suspend fun calculateSemanticBonus(
        tokens1: Set<String>,
        tokens2: Set<String>
    ): Float = withContext(Dispatchers.Default) {
        if (tokens1.isEmpty() || tokens2.isEmpty()) return@withContext 0f

        val canonicalMap1 = tokens1.associateWith { token ->
            val alias = GraphCache.findAlias(token)
            alias?.keywordTarget ?: alias?.entityTarget ?: token
        }
        val canonicalMap2 = tokens2.associateWith { token ->
            val alias = GraphCache.findAlias(token)
            alias?.keywordTarget ?: alias?.entityTarget ?: token
        }

        var totalWeight = 0.0
        val matchedTokens2 = mutableSetOf<String>()

        for ((_, c1) in canonicalMap1) {
            var maxPairWeight = 0.0
            var bestMatchT2: String? = null

            for ((t2, c2) in canonicalMap2) {
                if (t2 in matchedTokens2) continue

                val pairWeight = if (c1 == c2) {
                    1.0
                } else {
                    GraphCache.getEdgeWeight(c1, c2)
                }

                if (pairWeight > maxPairWeight) {
                    maxPairWeight = pairWeight
                    bestMatchT2 = t2
                }
            }

            if (bestMatchT2 != null) {
                matchedTokens2.add(bestMatchT2)
                totalWeight += maxPairWeight
            }
        }

        val minSize = minOf(tokens1.size, tokens2.size)
        return@withContext if (minSize > 0) (totalWeight / minSize).toFloat().coerceIn(0f, 1f) else 0f
    }

    fun tokenize(text: String): Set<String> {
        if (text.isBlank()) return emptySet()

        return TOKENIZE_REGEX
            .findAll(text.lowercase())
            .map { it.value.lowercase().trim() }
            .filter { it.length > 1 }
            .toSet()
    }

    fun getMd5(input: String): String {
        val normalized = input.trim().lowercase().replace("\\s+".toRegex(), " ")
        val bytes = MessageDigest.getInstance("MD5").digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}