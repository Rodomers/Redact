package com.rds.mews.text_filters

class DuplicateDetector {

    /**
     * Проверяет, является ли [newText] дубликатом среди списка [windowTexts].
     *
     * @param newText Новый текст для проверки.
     * @param windowTexts Список уже существующих текстов (например, в БД за последние 24 часа).
     * @param threshold Порог схожести по алгоритму Жаккара (по умолчанию 0.85).
     * @return true, если найден дубликат, иначе false.
     */
    fun checkIsDuplicate(newText: String, windowTexts: List<String>, threshold: Double = 0.85): Boolean {
        if (newText.isEmpty() || windowTexts.isEmpty()) return false

        val newTextLength = newText.length

        // Fast-Fail: Предвычисляем границы допустимых длин до начала цикла.
        // Если тексты отличаются по длине более чем на 30%, они точно не дубликаты.
        // Вынесение этого расчета за пределы цикла экономит процессорное время.
        val minLength = (newTextLength * 0.70).toInt()
        val maxLength = (newTextLength * 1.30).toInt()

        for (existingText in windowTexts) {
            // Если длина текущего текста выходит за рамки ±30%, немедленно переходим к следующему
            if (existingText.length !in minLength..maxLength) {
                continue
            }

            // Если длины близки, запускаем тяжеловесную проверку через TextComparator
            if (TextComparator.areSimilar(newText, existingText, threshold)) {
                return true // Дубликат найден, немедленно прерываем цикл
            }
        }

        // Если цикл завершился и совпадений не найдено
        return false
    }
}