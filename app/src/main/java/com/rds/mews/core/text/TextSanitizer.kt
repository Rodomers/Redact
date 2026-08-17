package com.rds.mews.core.text

import java.util.regex.Pattern

object TextSanitizer {

    private val IMAGE_PATTERN = Pattern.compile("!\\[.*?\\]\\(.*?\\)")
    private val LINK_TEXT_PATTERN = Pattern.compile("\\[(.*?)\\]\\(.*?\\)")
    private val FORMATTING_CHARS = Pattern.compile("[*`_#]")
    private val WHITESPACE_CLEANUP = Pattern.compile("\\s+")

    private val URL_DETECTOR = Pattern.compile("(https?://|t\\.me/)\\S+")

    fun sanitize(text: String, saveWhitespace: Boolean = false): String {
        if (text.isBlank()) return ""

        var clean = text
        clean = IMAGE_PATTERN.matcher(clean).replaceAll("")
        clean = LINK_TEXT_PATTERN.matcher(clean).replaceAll("$1")
        clean = FORMATTING_CHARS.matcher(clean).replaceAll("")
        if (!saveWhitespace) clean = WHITESPACE_CLEANUP.matcher(clean).replaceAll(" ").trim()

        return cutFooterWithLink(clean)
    }

    private fun cutFooterWithLink(text: String): String {
        var currentEnd = text.length

        while (currentEnd > 0) {
            var sentenceStart = 0
            for (i in (currentEnd - 2) downTo 0) {
                val ch = text[i]
                if (ch == '\n' || ((ch == '.' || ch == '!' || ch == '?') && text[i + 1].isWhitespace())) {
                    sentenceStart = i + 1
                    break
                }
            }
            if (sentenceStart <= 10) return text

            val sentenceCandidate = text.substring(sentenceStart, currentEnd).trim()
            if (sentenceCandidate.isNotEmpty() && URL_DETECTOR.matcher(sentenceCandidate).find()) {
                currentEnd = sentenceStart
                while (currentEnd > 0 && text[currentEnd - 1].isWhitespace()) {
                    currentEnd--
                }
            } else {
                break
            }
        }

        return if (currentEnd == text.length) text else text.substring(0, currentEnd).trim()
    }
}