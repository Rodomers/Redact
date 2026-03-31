package com.rds.mews.core

import java.net.URL
import java.net.HttpURLConnection
import java.util.UUID

class TelegramRssClient {

    data class RssItem(
        val title: String,
        val link: String,
        val pubDate: String,
        val description: String
    )

    private fun fetchChannelMessages(channelUrl: String): List<RssItem> {
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

    private fun downloadHtml(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        return connection.inputStream.bufferedReader().use { it.readText() }
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

    fun buildRss(channelUrl: String, feedId: Long): List<MinifluxEntry> {
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
}