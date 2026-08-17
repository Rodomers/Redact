package com.rds.mews.core.text

class DuplicateDetector {
    fun checkIsDuplicate(newText: String, windowTexts: List<String>, threshold: Double = 0.85): Boolean {
        if (newText.isEmpty() || windowTexts.isEmpty()) return false

        val newTextLength = newText.length

        val minLength = (newTextLength * 0.70).toInt()
        val maxLength = (newTextLength * 1.30).toInt()

        for (existingText in windowTexts) {
            if (existingText.length !in minLength..maxLength) {
                continue
            }

            if (TextComparator.areSimilar(newText, existingText, threshold.toFloat())) {
                return true
            }
        }

        return false
    }
}