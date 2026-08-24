package com.rds.mews.core.text.stats

import com.rds.mews.repositories.KeywordStatsRepository
import kotlin.math.sqrt

class BurstDetector {
    companion object {
        private const val MIN_WINDOW_TOKENS = 1000
        private const val BURST_Z_THRESHOLD = 3.1
        private const val BURST_IDF_MULTIPLIER = 1.5f
        private const val COLD_START_ABSOLUTE_THRESHOLD = 4.0
        private const val EPSILON = 1e-6
        private val repository = KeywordStatsRepository
    }

    data class WordWindowData(
        val word: String,
        val count: Int,
        val uniqueDomainsCount: Int
    )

    data class BurstResult(
        val word: String,
        val baseIdf: Float,
        val finalIdf: Float,
        val isBurst: Boolean,
        val zScore: Double
    )



    /**
     * Обработка окна ключевых слов и расчет финального значения IDF с учетом всплесков.
     *
     * @param windowData Список токенов с их частотой и количеством источников в окне.
     * @param totalWindowWords Суммарный объем токенов в текущем окне (Mw).
     * @param totalNews Общее число обработанных документов N.
     */
    suspend fun processWindow(
        windowData: List<WordWindowData>,
        totalWindowWords: Long,
        totalNews: Double
    ): List<BurstResult> {
        if (totalWindowWords < MIN_WINDOW_TOKENS) {
            return windowData.map { item ->
                val baseIdf = repository.getSmoothedIdf(item.word, totalNews)
                BurstResult(
                    word = item.word,
                    baseIdf = baseIdf,
                    finalIdf = baseIdf,
                    isBurst = false,
                    zScore = 0.0
                )
            }
        }

        val results = mutableListOf<BurstResult>()
        for (item in windowData) {
            val baseIdf = repository.getSmoothedIdf(item.word, totalNews)

            val sourceFactor = calculateSourceDiversification(item.uniqueDomainsCount)

            val adjustedFreq = item.count.toDouble() * sourceFactor

            val stat = repository.getWordStat(item.word)
            val isBurst: Boolean
            val zScore: Double

            if (stat == null || stat.historicalMean == 0.0) {
                isBurst = adjustedFreq >= COLD_START_ABSOLUTE_THRESHOLD
                zScore = if (isBurst) BURST_Z_THRESHOLD else 0.0
            } else {
                val stdDev = sqrt(stat.historicalVar + EPSILON)
                zScore = (adjustedFreq - stat.historicalMean) / stdDev
                isBurst = zScore > BURST_Z_THRESHOLD
            }

            val finalIdf = if (isBurst) baseIdf * BURST_IDF_MULTIPLIER else baseIdf
            results.add(
                BurstResult(
                    word = item.word,
                    baseIdf = baseIdf,
                    finalIdf = finalIdf,
                    isBurst = isBurst,
                    zScore = zScore
                )
            )
        }

        return results
    }

    private fun calculateSourceDiversification(uniqueDomainsCount: Int): Double {
        if (uniqueDomainsCount <= 1) return 0.0
        return 1.0 - (1.0 / uniqueDomainsCount.toDouble())
    }
}