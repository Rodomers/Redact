package com.rds.mews.core

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import com.rds.mews.repositories.MewsRepository

class TelegramRssClient(private val enableProxy: Boolean = false) {

    data class RssItem(
        val title: String,
        val link: String,
        val pubDate: String,
        val description: String
    )

    private suspend fun fetchChannelMessages(channelUrl: String): List<RssItem> = withContext(Dispatchers.IO) {
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

        return@withContext messages
    }

    private suspend fun downloadHtml(url: String): String = withContext(Dispatchers.IO) {
        val httpClient = SharedHttpClient.createInstance(
            MewsRepository.HUB_ADDRESS,
            MewsRepository.SERVER_KEY,
            enableProxy
        )
        try {
            val response = httpClient.get(url)
            if (response.status in 200..299) response.body else ""
        } catch (e: Exception) {
            ""
        } finally {
            httpClient.close()
        }
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

    suspend fun buildRss(channelUrl: String, feedId: Long): List<MinifluxEntry> = withContext(Dispatchers.IO) {
        val items = fetchChannelMessages(channelUrl)

        return@withContext items.map { item ->
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

    suspend fun scrapeEmbedMedia(postLink: String): List<String> = withContext(Dispatchers.IO) {
        val embedUrl = if (postLink.contains("?")) {
            "$postLink&embed=1"
        } else {
            "$postLink?embed=1"
        }

        val httpClient = SharedHttpClient.createInstance(
            MewsRepository.HUB_ADDRESS,
            MewsRepository.SERVER_KEY,
            enableProxy
        )
        try {
            val response = httpClient.get(embedUrl)
            if (response.status !in 200..299) return@withContext emptyList()

            val document = Jsoup.parse(response.body, embedUrl)
            val elements = document.select(".tgme_widget_message_photo_wrap")
            val regex = Regex("""url\('([^']+)'\)""")

            elements.mapNotNull { element ->
                val style = element.attr("style")
                regex.find(style)?.groupValues?.get(1)
            }
        } finally {
            httpClient.close()
        }
    }
}