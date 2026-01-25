package com.rds.mews.core

import java.security.MessageDigest
import java.util.Locale

object TextComparator {

    /**
     * Главный метод сравнения.
     * @param text1 Первый текст
     * @param text2 Второй текст
     * @param threshold Порог схожести от 0.0 до 1.0.
     * 1.0 = нужно полное совпадение (используется быстрый MD5).
     * 0.85 = допускаются небольшие различия (используется Jaccard).
     */
    fun areSimilar(text1: String, text2: String, threshold: Double): Boolean {
        if (text1.isEmpty() || text2.isEmpty()) return false
        if (text1 == text2) return true

        if (threshold == 1.0) {
            return getMd5(text1) == getMd5(text2)
        }

        val score = calculateJaccardSimilarity(text1, text2)
        return score >= threshold
    }

    /**
     * Алгоритм Жаккара: (Пересечение слов) / (Объединение слов)
     * Понимает, что "Мама мыла раму" и "Раму мыла мама" — это очень похожие тексты.
     */
    fun calculateJaccardSimilarity(s1: String, s2: String): Double {
        val tokens1 = tokenize(s1)
        val tokens2 = tokenize(s2)

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    /**
     * Разбивает текст на "токены" (слова), очищая от мусора.
     * Используем Set для уникальности слов.
     */
    private fun tokenize(text: String): Set<String> {
        return text.lowercase(Locale.getDefault())
            .split(Regex("[^a-zа-я0-9]+"))
            .filter { it.length > 2 }
            .toSet()
    }

    /**
     * Быстрый хэш для строгого сравнения
     */
    fun getMd5(input: String): String {
        val normalized = input.trim().lowercase().replace("\\s+".toRegex(), " ")
        val bytes = MessageDigest.getInstance("MD5").digest(normalized.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}