package com.rds.mews.core

import java.util.UUID
import kotlinx.coroutines.withContext

class TelegramRssClient(private val httpClient: SharedHttpClient.JdkClient) {

    data class RssItem(
        val title: String,
        val link: String,
        val pubDate: String,
        val description: String
    )

    private suspend fun fetchChannelMessages(channelUrl: String): List<RssItem> {
        val html = downloadHtml(channelUrl)

        val messageBlockRegex = Regex(
            """<div class="tgme_widget_message_wrap.*?</div>\s*</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        val blocks = messageBlockRegex.findAll(html)
        val messages = mutableListOf<RssItem>()

        for (block in blocks) {
            val blockHtml = block.value

            val dataPost = Regex("""data-post="([^"]+)"""")
                .find(blockHtml)
                ?.groupValues?.get(1)
                ?: ""

            val messageUrl = when {
                dataPost.isNotEmpty() -> "https://t.me/$dataPost"
                else -> {
                    val href = Regex("""<a\s+class="tgme_widget_message_date"[^>]*href="([^"]+)"""",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                    ).find(blockHtml)?.groupValues?.get(1).orEmpty()

                    when {
                        href.startsWith("http") -> href
                        href.isNotEmpty()       -> "https://t.me$href"
                        else                    -> ""
                    }
                }
            }

            val datetime = Regex("""<time[^>]*\sdatetime="([^"]+)"""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(blockHtml)?.groupValues?.get(1).orEmpty()

            val textRaw = Regex(
                """<div class="tgme_widget_message_text[^"]*?"[^>]*>(.*?)</div>""",
                RegexOption.DOT_MATCHES_ALL
            ).find(blockHtml)?.groupValues?.get(1)
                ?: Regex(
                    """<div class="media_supported_cont">.*?<div class="tgme_widget_message_text[^"]*?"[^>]*>(.*?)</div>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                ).find(blockHtml)?.groupValues?.get(1)
                ?: ""

            val text = stripHtml(textRaw).trim()
            val title = if (text.length > 30) text.substring(0, text.length / 3) + "..." else text

            if (text.isNotEmpty()) {
                messages.add(RssItem(title, messageUrl, datetime, text))
            }
        }

        return messages
    }

    private suspend fun downloadHtml(url: String): String {
        return httpClient.get(url).body
    }

    private fun stripHtml(input: String): String {
        return input.replace(Regex("<.*?>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#036;", "$")
    }

    suspend fun buildRss(channelUrl: String, feedId: Long): List<MinifluxEntry> {
        val items = fetchChannelMessages(channelUrl)

        return items.map { item ->
            MinifluxEntry(
                id = UUID.nameUUIDFromBytes(item.link.toByteArray()).mostSignificantBits,
                feed_id = feedId,
                title = item.title,
                url = item.link,
                content = item.description,
                published_at = item.pubDate
            )
        }
    }

    suspend fun scrapeEmbedMedia(postLink: String): List<String> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val embedUrl = if (postLink.contains("?")) {
            "$postLink&embed=1"
        } else {
            "$postLink?embed=1"
        }

        val html = httpClient.get(embedUrl).body
        val document = org.jsoup.Jsoup.parse(html)
        val elements = document.select(".tgme_widget_message_photo_wrap")
        val regex = Regex("""url\('([^']+)'\)""")

        elements.mapNotNull { element ->
            val style = element.attr("style")
            regex.find(style)?.groupValues?.get(1)
        }
    }
}