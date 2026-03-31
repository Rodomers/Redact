package com.rds.mews.text_filters

object TextCleaner {
    private val TELEGRAM_LINK_REGEX = Regex("""(?i)(https?://)?(t\.me|telegram\.me|telegram\.dog)/[a-zA-Z0-9_]+""")
    private val AD_TAIL_REGEX = Regex("""(?si)(читать далее|подробности в|источник:|подписывайтесь на наш канал|смотреть продолжение).*""")
    private val AD_PHRASES_REGEX = Regex("""(?i)(реклама:|спонсорский пост)""")

    private val HTML_SCRIPTS_STYLES_REGEX = Regex("""(?is)<(script|style)[^>]*>.*?</\1>""")
    private val HTML_COMMENTS_REGEX = Regex("""(?is)<!--.*?-->""")

    private val HTML_BLOCKS_REGEX = Regex("""(?i)</?(p|h[1-6]|div|ul|ol|li|blockquote)[^>]*>|<br\s*/?>""")
    private val HTML_TAGS_REGEX = Regex("""<[^>]+>""")

    private val WEB_UI_GARBAGE_REGEX = Regex("""(?im)^\s*(save|share\d*|click here to share.*|googleAdd.*?info|play videoplay video|video duration.*|read more)\s*$""")
    private val PUBLISH_DATE_REGEX = Regex("""(?im)^\s*Published On\s+.*$""")

    private val MULTIPLE_SPACES_REGEX = Regex("""[ \t]+""")

    fun clean(rawText: String): String {
        if (rawText.isBlank()) return ""

        var cleaned = rawText

        cleaned = cleaned.replace(HTML_SCRIPTS_STYLES_REGEX, "")
        cleaned = cleaned.replace(HTML_COMMENTS_REGEX, "")

        cleaned = cleaned.replace(HTML_BLOCKS_REGEX, "\n")
        cleaned = cleaned.replace(HTML_TAGS_REGEX, "")

        cleaned = cleaned.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&mdash;", "—")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")

        cleaned = cleaned.replace(WEB_UI_GARBAGE_REGEX, "")
        cleaned = cleaned.replace(PUBLISH_DATE_REGEX, "")

        cleaned = cleaned.replace(AD_TAIL_REGEX, "")
        cleaned = cleaned.replace(TELEGRAM_LINK_REGEX, "")
        cleaned = cleaned.replace(AD_PHRASES_REGEX, "")

        cleaned = cleaned.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        cleaned = cleaned.replace(MULTIPLE_SPACES_REGEX, " ")

        return cleaned.trim()
    }
}