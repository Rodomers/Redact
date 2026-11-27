package com.rds.mews.core

import com.rds.mews.core.parser.MinifluxEntry
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

    private suspend fun fetchChannelMessages(channelUrl: String, lastSyncTimeMs: Long = 0L): List<RssItem> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = when {
            channelUrl.contains("/s/") -> channelUrl.substringBefore("?")
            channelUrl.startsWith("http") -> {
                val parts = channelUrl.trimEnd('/').split("/")
                val username = parts.last().substringBefore("?")
                "https://t.me/s/$username"
            }
            channelUrl.startsWith("@") -> "https://t.me/s/${channelUrl.drop(1)}"
            else -> "https://t.me/s/${channelUrl.trim()}"
        }

        val messages = mutableListOf<RssItem>()
        val seenLinks = mutableSetOf<String>()
        var currentUrl = cleanBaseUrl
        var pageCount = 0
        val maxPages = 15

        while (pageCount < maxPages) {
            pageCount++
            val html = downloadHtml(currentUrl)
            if (html.isBlank()) break

            val messageBlockRegex = Regex(
                """<div class="tgme_widget_message_wrap.*?</div>\s*</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )

            val blocks = messageBlockRegex.findAll(html).toList()
            if (blocks.isEmpty()) break

            var pageAddedCount = 0
            var minPostIdOnPage: Long? = null
            var reachedLastSync = false

            for (block in blocks) {
                val blockHtml = block.value

                val dataPost = Regex("""data-post="([^"]+)"""")
                    .find(blockHtml)
                    ?.groupValues?.get(1)
                    ?: ""

                val postIdNum = dataPost.split("/").lastOrNull()?.toLongOrNull()
                if (postIdNum != null) {
                    minPostIdOnPage = if (minPostIdOnPage == null) postIdNum else minOf(minPostIdOnPage, postIdNum)
                }

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

                if (messageUrl.isBlank() || seenLinks.contains(messageUrl)) continue

                val datetime = Regex("""<time[^>]*\sdatetime="([^"]+)"""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(blockHtml)?.groupValues?.get(1).orEmpty()

                if (lastSyncTimeMs > 0L && datetime.isNotBlank()) {
                    val msgTime = parseIsoDate(datetime)
                    if (msgTime in 1..lastSyncTimeMs) {
                        reachedLastSync = true
                    }
                }

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
                    seenLinks.add(messageUrl)
                    messages.add(RssItem(title, messageUrl, datetime, text))
                    pageAddedCount++
                }
            }

            if (reachedLastSync || pageAddedCount == 0 || minPostIdOnPage == null) break

            currentUrl = "$cleanBaseUrl?before=$minPostIdOnPage"
        }

        return@withContext messages
    }

    private fun parseIsoDate(isoStr: String): Long {
        return try {
            java.time.ZonedDateTime.parse(isoStr, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
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

    suspend fun buildRss(channelUrl: String, feedId: Long, lastSyncTimeMs: Long = 0L): List<MinifluxEntry> = withContext(Dispatchers.IO) {
        val items = fetchChannelMessages(channelUrl, lastSyncTimeMs)

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
            val elements = document.select(
                ".tgme_widget_message_photo_wrap, .tgme_widget_message_video_wrap, .tgme_widget_message_roundvideo_wrap"
            )
            val regex = Regex("""url\(['"]?([^'")]+)['"]?\)""")

            elements.mapNotNull { element ->
                val rawUrl = if (
                    element.hasClass("tgme_widget_message_video_wrap") ||
                    element.hasClass("tgme_widget_message_roundvideo_wrap")
                ) {
                    val videoElement = element.selectFirst("video[src], video source[src]")
                    val videoSrc = videoElement?.absUrl("src")?.ifBlank { videoElement.attr("src") }.orEmpty()
                    if (videoSrc.isNotBlank()) {
                        videoSrc
                    } else {
                        val thumbStyle = element.selectFirst(
                            ".tgme_widget_message_video_thumb, .tgme_widget_message_roundvideo_thumb"
                        )?.attr("style").orEmpty()
                        regex.find(thumbStyle)?.groupValues?.get(1)
                    }
                } else {
                    val style = element.attr("style")
                    regex.find(style)?.groupValues?.get(1)
                }

                rawUrl?.let { url ->
                    if (url.startsWith("//")) "https:$url" else url
                }?.takeIf { it.isNotBlank() }
            }.distinct()
        } finally {
            httpClient.close()
        }
    }
}