package com.rds.mews.core

import android.content.Context
import android.net.ConnectivityManager
import com.rds.mews.database.SourceEntity
import com.rds.mews.localcore.SourceType
import com.rds.mews.localcore.defineSourceType
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class RssFetcher(
    enableProxy: Boolean = false
) {
    private val httpClient = SharedHttpClient.createInstance(MewsRepository.HUB_ADDRESS, MewsRepository.SERVER_KEY, enableProxy = false)

    suspend fun fetchAndStoreAll(messAliveTime: Long = 0L, maxTimeHours: Int = 168): FetchResult {
        val sourceList = try {
            MewsRepository.getAllSourceEntities()
        } catch (e: Exception) {
            return FetchResult(0, 0, 0, 0, listOf(e.message ?: "unknown"))
        }

        var feedsProcessed = 0
        var itemsAdded = 0
        var itemsSkipped = 0
        var feedsNotModified = 0
        val errors = mutableListOf<String>()

        val rfc1123Format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("GMT")
        }

        for (source in sourceList) {
            try {
                var fetchUrl = source.feedUrl
                val lastUpdated = source.lastSyncTime
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastUpdated

                if (lastUpdated > 0L && timeSinceLastUpdate < 15 * 60 * 1000L) {
                    feedsNotModified++
                    feedsProcessed++
                    continue
                }

                val sourceUpdateDelta: Long? = if (lastUpdated == 0L) null else timeSinceLastUpdate / 1000

                if (SourceType.fromId(source.sourceType) == SourceType.TELEGRAM) {
                    val channelName = fetchUrl.split("/").last().trim()
                    fetchUrl = buildTelegramRssUrl(channelName)
                    if (sourceUpdateDelta != null) fetchUrl = "$fetchUrl&filter_time=${sourceUpdateDelta}"
                }

                val headers = mutableMapOf<String, String>()

                if (lastUpdated > 0L) {
                    headers["If-Modified-Since"] = rfc1123Format.format(Date(lastUpdated))
                }

                source.etagHash?.let { etag ->
                    headers["If-None-Match"] = etag
                }

                val response = httpClient.request("GET", fetchUrl, null, headers)

                if (response.status == 304) {
                    feedsNotModified++
                    feedsProcessed++
                    continue
                }

                if (response.status != 200) {
                    errors.add("Ошибка HTTP ${response.status} при загрузке ${source.feedUrl}")
                    continue
                }

                val newEtag = response.headers.entries.firstOrNull { it.key.equals("ETag", ignoreCase = true) }?.value
                if (!newEtag.isNullOrBlank() && newEtag != source.etagHash) {
                    MewsRepository.updateSourceEtag(source.id, newEtag)
                }

                val xmlContent = response.body
                if (xmlContent.isBlank()) continue

                val doc = Jsoup.parse(xmlContent, fetchUrl, Parser.xmlParser())

                if (doc.selectFirst("rss, feed, channel") == null) throw RuntimeException("Invalid feed format")

                val items = parseRssItems(doc)

                for (item in items) {
                    val link = item.link ?: continue

                    if (MewsRepository.getMessageByLink(link) != null) {
                        itemsSkipped++
                        continue
                    }

                    val time = item.pubDateMillis ?: System.currentTimeMillis()
                    val maxAgeMillis = maxTimeHours * 60 * 60 * 1000L

                    if ((System.currentTimeMillis() - time) > maxAgeMillis) {
                        itemsSkipped++
                        continue
                    }

                    var text = buildMessageText(item)
                    if (text.length < 300) {
                        val fullText = HtmlExtractorFallback.fetchFullText(MewsRepository.getAppContext(), link, httpClient)
                        if (!fullText.isNullOrBlank()) {
                            text = "${item.title ?: ""}\n\n$fullText".trim()
                        }
                    }
                    MewsRepository.addMessage(sourceId = source.id, messageTime = time, link = link, messageText = text)
                    itemsAdded++
                }
                feedsProcessed++

            } catch (e: Exception) {
                errors.add("Ошибка при обработке RSS (id=${source.id}, link=${source.feedUrl}): ${e.message}")
            } finally {
                MewsRepository.messageTimeKill(864000)
            }
        }
        return FetchResult(feedsProcessed, itemsAdded, itemsSkipped, feedsNotModified, errors)
    }

    suspend fun fetchSingleSourceAsMinifluxEntries(source: SourceEntity, cursorTimeMs: Long, maxTimeHours: Int = 168): List<MinifluxEntry> {
        val currentTime = System.currentTimeMillis()
        val lastUpdated = source.lastSyncTime

        if (lastUpdated > 0L && (currentTime - lastUpdated) < 15 * 60 * 1000L) {
            return emptyList()
        }

        val maxAgeMillis = maxTimeHours * 60 * 60 * 1000L
        var fetchUrl = source.feedUrl

        if (SourceType.fromId(source.sourceType) == SourceType.TELEGRAM) {
            val channelName = fetchUrl.split("/").last().trim()
            fetchUrl = buildTelegramRssUrl(channelName)
        }

        val headers = mutableMapOf<String, String>()
        source.etagHash?.let { etag ->
            headers["If-None-Match"] = etag
        }

        try {
            val response = httpClient.request("GET", fetchUrl, null, headers)

            if (response.status == 304) return emptyList()
            if (response.status != 200) throw RuntimeException("HTTP ${response.status}")

            val newEtag = response.headers.entries.firstOrNull { it.key.equals("ETag", ignoreCase = true) }?.value
            if (!newEtag.isNullOrBlank() && newEtag != source.etagHash) {
                MewsRepository.updateSourceEtag(source.id, newEtag)
            }

            val xmlContent = response.body
            if (xmlContent.isBlank()) throw RuntimeException("Empty XML content")

            val doc = Jsoup.parse(xmlContent, fetchUrl, Parser.xmlParser())

            if (doc.selectFirst("rss, feed, channel") == null) throw RuntimeException("Invalid feed format")

            val rssItems = parseRssItems(doc)

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }

            return rssItems.mapNotNull { item ->
                val pubDateMillis = item.pubDateMillis ?: currentTime
                if ((currentTime - pubDateMillis) > maxAgeMillis || pubDateMillis <= cursorTimeMs) return@mapNotNull null

                val link = item.link ?: return@mapNotNull null

                var text = buildMessageText(item)
                if (text.length < 300) {
                    val fullText = HtmlExtractorFallback.fetchFullText(MewsRepository.getAppContext(), link, httpClient)
                    if (!fullText.isNullOrBlank()) {
                        text = "${item.title ?: ""}\n\n$fullText".trim()
                    }
                }

                MinifluxEntry(
                    id = link.hashCode().toLong(),
                    feed_id = source.id,
                    title = item.title ?: "Без заголовка",
                    url = link,
                    content = text,
                    published_at = isoFormat.format(Date(pubDateMillis))
                )
            }

        } catch (e: Exception) {
            if (SourceType.fromId(source.sourceType) == SourceType.TELEGRAM) {
                val fallbackEntries = TelegramRssClient().buildRss(source.feedUrl, source.id)
                return fallbackEntries.filter { entry ->
                    try {
                        val zdt = ZonedDateTime.parse(entry.published_at, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        val itemTimeMillis = zdt.toInstant().toEpochMilli()
                        (currentTime - itemTimeMillis) <= maxAgeMillis
                    } catch (_: Exception) {
                        true
                    }
                }
            } else {
                throw e
            }
        }
    }

    private data class RssItem(
        val title: String?,
        val link: String?,
        val description: String?,
        val pubDateMillis: Long?
    )

    private fun parseRssItems(doc: Document): List<RssItem> {
        val nodeList = doc.select("item")
        val result = mutableListOf<RssItem>()
        for (node in nodeList) {
            val title = elementText(node, "title")
            val link = elementText(node, "link") ?: elementText(node, "guid")
            val description = elementText(node, "description")
            val pubDateStr = elementText(node, "pubDate")
            val pubDateMillis = tryParseDate(pubDateStr)
            result += RssItem(title, link, description, pubDateMillis)
        }
        return result
    }

    private fun elementText(parent: Element, tagName: String): String? {
        val nList = parent.select(tagName)
        if (nList.isNotEmpty()) {
            val node = nList[nList.lastIndex]
            val text = node?.text()
            if (!text.isNullOrBlank()) return text.trim()
        }
        if (tagName.contains(":")) {
            val after = tagName.substringAfter(":")
            val nList2 = parent.select(after)
            if (nList2.isNotEmpty()) {
                val node = nList2[nList2.lastIndex]
                val text = node?.text()
                if (!text.isNullOrBlank()) return text.trim()
            }
        }
        return null
    }

    private fun buildMessageText(item: RssItem): String {
        val sb = StringBuilder()
        if (!item.title.isNullOrBlank()) {
            sb.append(item.title.trim())
        }
        val body = item.description
        if (!body.isNullOrBlank()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(body.trim())
        }
        return sb.toString()
    }

    private fun tryParseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val s = dateStr.trim()
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pat in patterns) {
            try {
                val df = SimpleDateFormat(pat, Locale.ENGLISH)
                df.isLenient = true
                val d = df.parse(s)
                if (d != null) return d.time
            } catch (_: ParseException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        val noParen = s.replace(Regex("\\(.*?\\)"), "").trim()
        if (noParen != s) return tryParseDate(noParen)
        return null
    }

    data class FetchResult(
        val feedsProcessed: Int,
        val itemsAdded: Int,
        val itemsSkipped: Int,
        val feedsNotModified: Int,
        val errors: List<String>
    )
}

object HtmlExtractorFallback {
    private val meteredMutex = Mutex()

    suspend fun fetchFullText(
        context: Context,
        url: String,
        httpClient: SharedHttpClient.JdkClient
    ): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isMetered = cm.isActiveNetworkMetered

        suspend fun doFetch(): String? {
            if (isMetered) delay(2000L)

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
            )

            val response = httpClient.request("GET", url, null, headers)
            if (response.status !in 200..299 || response.body.isBlank()) {
                return null
            }

            return extractCleanArticle(response.body)
        }

        return if (isMetered) {
            meteredMutex.withLock { doFetch() }
        } else {
            doFetch()
        }
    }

    fun extractCleanArticle(rawHtml: String): String {
        val doc = Jsoup.parse(rawHtml)
        doc.select("script, style, nav, footer, header, aside, iframe, noscript").remove()
        val contentElement = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst(".content, .post, .article")
            ?: doc.body()

        return contentElement?.text()?.trim() ?: ""
    }
}

fun buildTelegramRssUrl(username: String): String {
    return "http://${MewsRepository.HUB_ADDRESS}/telegram/channel/${username.trim()}?limit=100&key=${MewsRepository.SERVER_KEY}"
}