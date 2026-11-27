package com.rds.mews.core.parser

import android.content.Context
import android.net.ConnectivityManager
import com.rds.mews.core.SharedHttpClient
import com.rds.mews.core.TelegramRssClient
import com.rds.mews.database.main.SourceEntity
import com.rds.mews.localcore.SourceType
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.delay
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
import java.util.TimeZone
import kotlin.time.Duration.Companion.milliseconds

class RssFetcher(
    enableProxy: Boolean = false
) {
    private val httpClient = SharedHttpClient.createInstance(MewsRepository.HUB_ADDRESS, MewsRepository.SERVER_KEY, enableProxy = false)
    private val minifluxClient = MinifluxClient(httpClient)

    suspend fun fetchAndStoreAll(messAliveTime: Long = 0L, maxTimeHours: Int = 168): FetchResult {
        MewsRepository.checkServersHealth()
        val isParserHealthy = MewsRepository.checkParserHealth()
        val isHubHealthy = MewsRepository.checkHubHealth()

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

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (source in sourceList) {
            val isTelegram = SourceType.fromId(source.sourceType) == SourceType.TELEGRAM
            var processedSuccessfully = false
            var maxPubTime = source.lastSyncTime

            if (isParserHealthy) {
                try {
                    val feedId = minifluxClient.getFeedIdByUrl(source.feedUrl)
                        ?: throw RuntimeException("Feed ID not found in Miniflux")

                    val entries = minifluxClient.getEntries(feedId, source.lastSyncTime, 500)

                    if (entries.isEmpty()) {
                        feedsNotModified++
                        feedsProcessed++
                        continue
                    }

                    for (entry in entries) {
                        val pubTime = try { isoFormat.parse(entry.published_at)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                        if (pubTime > maxPubTime) maxPubTime = pubTime

                        if (MewsRepository.getMessageByLink(entry.url) != null) {
                            itemsSkipped++
                            continue
                        }

                        var text = entry.content.trim()
                        val mediaUrls = LinkedHashSet<String>()

                        if (entry.content.isNotBlank()) {
                            mediaUrls.addAll(MediaUrlExtractor.extractMediaFromHtml(entry.content, entry.url))
                        }

                        val needFullText = text.length < 300
                        val needMedia = mediaUrls.isEmpty()

                        if (needFullText || needMedia) {
                            val pageDetails = HtmlExtractorFallback.fetchPageDetails(MewsRepository.getAppContext(), entry.url, httpClient)
                            if (pageDetails != null) {
                                if (needFullText && pageDetails.text.isNotBlank()) {
                                    text = "${entry.title}\n\n${pageDetails.text}".trim()
                                }
                                if (needMedia && pageDetails.mediaUrls.isNotEmpty()) {
                                    mediaUrls.addAll(pageDetails.mediaUrls)
                                }
                            }
                        }

                        MewsRepository.addMessage(source.id, pubTime, entry.url, text, mediaUrls = mediaUrls.toList())
                        itemsAdded++
                    }

                    if (maxPubTime > source.lastSyncTime) {
                        MewsRepository.updateSourceLastSyncTime(source.id, maxPubTime)
                    }

                    feedsProcessed++
                    processedSuccessfully = true

                } catch (minifluxException: Exception) {
                    errors.add("Miniflux недоступен для источника ${source.id} (${minifluxException.message}). Запуск локального парсинга.")
                }
            }

            if (!processedSuccessfully && isHubHealthy) {
                try {
                    var fetchUrl = source.feedUrl

                    if (isTelegram) {
                        val channelName = source.feedUrl.split("/").last().trim()
                        fetchUrl = buildTelegramRssUrl(channelName)
                        val lastUpdated = source.lastSyncTime
                        if (lastUpdated > 0L) {
                            val delta = (System.currentTimeMillis() - lastUpdated) / 1000
                            fetchUrl = "$fetchUrl&filter_time=$delta"
                        }
                    }

                    val response = httpClient.request("GET", fetchUrl, null, emptyMap())
                    if (response.status == 200 && response.body.isNotBlank()) {
                        val doc = Jsoup.parse(response.body, fetchUrl, Parser.xmlParser())
                        val items = parseRssItems(doc, fetchUrl)

                        for (item in items) {
                            val time = item.pubDateMillis ?: System.currentTimeMillis()
                            if (time > maxPubTime) maxPubTime = time

                            val link = item.link ?: continue
                            if (MewsRepository.getMessageByLink(link) != null) {
                                itemsSkipped++
                                continue
                            }

                            var text = buildMessageText(item)
                            val mediaUrls = item.mediaUrls.toMutableSet()

                            val needFullText = text.length < 300
                            val needMedia = mediaUrls.isEmpty()

                            if (needFullText || needMedia) {
                                val pageDetails = HtmlExtractorFallback.fetchPageDetails(MewsRepository.getAppContext(), link, httpClient)
                                if (pageDetails != null) {
                                    if (needFullText && pageDetails.text.isNotBlank()) {
                                        text = "${item.title ?: ""}\n\n${pageDetails.text}".trim()
                                    }
                                    if (needMedia && pageDetails.mediaUrls.isNotEmpty()) {
                                        mediaUrls.addAll(pageDetails.mediaUrls)
                                    }
                                }
                            }

                            MewsRepository.addMessage(source.id, time, link, text, mediaUrls = mediaUrls.toList())
                            itemsAdded++
                        }

                        if (maxPubTime > source.lastSyncTime) {
                            MewsRepository.updateSourceLastSyncTime(source.id, maxPubTime)
                        }

                        feedsProcessed++
                        processedSuccessfully = true
                    } else {
                        errors.add("RSSHub недоступен (HTTP ${response.status}) для ${source.feedUrl}. Запуск локального скрейпинга.")
                    }
                } catch (hubException: Exception) {
                    errors.add("Ошибка обращения к RSSHub для ${source.id}: ${hubException.message}. Запуск локального скрейпинга.")
                }


                if (!processedSuccessfully) {
                    try {
                        if (isTelegram) {
                            val fallbackEntries = TelegramRssClient().buildRss(source.feedUrl, source.id, source.lastSyncTime)
                            if (fallbackEntries.isNotEmpty()) {
                                for (entry in fallbackEntries) {
                                    val pubTime = try { isoFormat.parse(entry.published_at)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                                    if (pubTime > maxPubTime) maxPubTime = pubTime

                                    if (MewsRepository.getMessageByLink(entry.url) != null) {
                                        itemsSkipped++
                                        continue
                                    }

                                    MewsRepository.addMessage(source.id, pubTime, entry.url, entry.content, mediaUrls = emptyList())
                                    itemsAdded++
                                }

                                if (maxPubTime > source.lastSyncTime) {
                                    MewsRepository.updateSourceLastSyncTime(source.id, maxPubTime)
                                }

                                feedsProcessed++
                                processedSuccessfully = true
                            }
                        } else {
                            val response = httpClient.request("GET", source.feedUrl, null, emptyMap())
                            if (response.status == 200 && response.body.isNotBlank()) {
                                val doc = Jsoup.parse(response.body, source.feedUrl, Parser.xmlParser())
                                val items = parseRssItems(doc, source.feedUrl)
                                for (item in items) {
                                    val time = item.pubDateMillis ?: System.currentTimeMillis()
                                    if (time > maxPubTime) maxPubTime = time

                                    val link = item.link ?: continue
                                    if (MewsRepository.getMessageByLink(link) != null) {
                                        itemsSkipped++
                                        continue
                                    }

                                    var text = buildMessageText(item)
                                    val mediaUrls = item.mediaUrls.toMutableSet()

                                    val needFullText = text.length < 300
                                    val needMedia = mediaUrls.isEmpty()

                                    if (needFullText || needMedia) {
                                        val pageDetails = HtmlExtractorFallback.fetchPageDetails(MewsRepository.getAppContext(), link, httpClient)
                                        if (pageDetails != null) {
                                            if (needFullText && pageDetails.text.isNotBlank()) {
                                                text = "${item.title ?: ""}\n\n${pageDetails.text}".trim()
                                            }
                                            if (needMedia && pageDetails.mediaUrls.isNotEmpty()) {
                                                mediaUrls.addAll(pageDetails.mediaUrls)
                                            }
                                        }
                                    }

                                    MewsRepository.addMessage(source.id, time, link, text, mediaUrls = mediaUrls.toList())
                                    itemsAdded++
                                }

                                if (maxPubTime > source.lastSyncTime) {
                                    MewsRepository.updateSourceLastSyncTime(source.id, maxPubTime)
                                }

                                feedsProcessed++
                                processedSuccessfully = true
                            }
                        }
                    } catch (fallbackException: Exception) {
                        errors.add("Критический сбой всех 3 уровней парсинга для источника ${source.id}: ${fallbackException.message}")
                    } finally {
                        MewsRepository.messageTimeKill(864000)
                    }
                }
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

            val rssItems = parseRssItems(doc, fetchUrl)

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            return rssItems.mapNotNull { item ->
                val pubDateMillis = item.pubDateMillis ?: currentTime
                if ((currentTime - pubDateMillis) > maxAgeMillis || pubDateMillis <= cursorTimeMs) return@mapNotNull null

                val link = item.link ?: return@mapNotNull null

                var text = buildMessageText(item)
                if (text.length < 300) {
                    val pageDetails = HtmlExtractorFallback.fetchPageDetails(MewsRepository.getAppContext(), link, httpClient)
                    if (pageDetails?.text?.isNotBlank() == true) {
                        text = "${item.title ?: ""}\n\n${pageDetails.text}".trim()
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
                val fallbackEntries = TelegramRssClient().buildRss(source.feedUrl, source.id, source.lastSyncTime)
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
        val pubDateMillis: Long?,
        val mediaUrls: List<String>
    )

    private fun parseRssItems(doc: Document, baseFeedUrl: String = ""): List<RssItem> {
        val nodeList = doc.select("item, entry")
        val result = mutableListOf<RssItem>()
        for (node in nodeList) {
            val title = elementText(node, "title")
            val link = elementText(node, "link") ?: elementText(node, "guid")
            val description = elementText(node, "description") ?: elementText(node, "content") ?: elementText(node, "summary")
            val pubDateStr = elementText(node, "pubDate") ?: elementText(node, "published") ?: elementText(node, "updated")
            val pubDateMillis = tryParseDate(pubDateStr)

            val itemLink = if (!link.isNullOrBlank()) link else baseFeedUrl
            val mediaUrls = LinkedHashSet<String>()

            val enclosures = node.select("enclosure")
            for (enclosure in enclosures) {
                val type = enclosure.attr("type")
                val url = enclosure.attr("url")
                if (MediaUrlExtractor.isMediaEnclosure(type, url)) {
                    val resolved = MediaUrlExtractor.resolveMediaUrl(itemLink, url)
                    if (resolved.isNotBlank()) mediaUrls.add(resolved)
                }
            }

            val mediaContents = node.getElementsByTag("media:content")
            for (mc in mediaContents) {
                val url = mc.attr("url").ifBlank { mc.attr("src") }
                val resolved = MediaUrlExtractor.resolveMediaUrl(itemLink, url)
                if (resolved.isNotBlank()) mediaUrls.add(resolved)
            }

            val mediaThumbnails = node.getElementsByTag("media:thumbnail")
            for (mt in mediaThumbnails) {
                val url = mt.attr("url").ifBlank { mt.attr("src") }
                val resolved = MediaUrlExtractor.resolveMediaUrl(itemLink, url)
                if (resolved.isNotBlank()) mediaUrls.add(resolved)
            }

            var contentEncoded: String? = null
            val ceList = node.getElementsByTag("content:encoded")
            if (ceList.isNotEmpty()) {
                contentEncoded = ceList[ceList.lastIndex]?.text()?.trim()
            }

            val htmlTexts = listOfNotNull(description, contentEncoded).joinToString(" ")
            if (htmlTexts.isNotBlank()) {
                val extracted = MediaUrlExtractor.extractMediaFromHtml(htmlTexts, itemLink)
                mediaUrls.addAll(extracted)
            }

            result += RssItem(title, link, description, pubDateMillis, mediaUrls.toList())
        }
        return result
    }

    private fun elementText(parent: Element, tagName: String): String? {
        val nList = parent.select(tagName)
        if (nList.isNotEmpty()) {
            val node = nList[nList.lastIndex]
            val href = node?.attr("href")
            if (!href.isNullOrBlank()) return href.trim()
            val text = node?.text()
            if (!text.isNullOrBlank()) return text.trim()
        }
        if (tagName.contains(":")) {
            val after = tagName.substringAfter(":")
            val nList2 = parent.select(after)
            if (nList2.isNotEmpty()) {
                val node = nList2[nList2.lastIndex]
                val href = node?.attr("href")
                if (!href.isNullOrBlank()) return href.trim()
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

    data class ExtractedPage(
        val text: String,
        val mediaUrls: List<String> = emptyList()
    )

    suspend fun fetchPageDetails(
        context: Context,
        url: String,
        httpClient: SharedHttpClient.JdkClient
    ): ExtractedPage? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isMetered = cm.isActiveNetworkMetered

        suspend fun doFetch(): ExtractedPage? {
            if (isMetered) delay(2000L.milliseconds)

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
            )

            val response = httpClient.request("GET", url, null, headers)
            if (response.status !in 200..299 || response.body.isBlank()) {
                return null
            }

            return extractPageDetails(response.body, url)
        }

        return if (isMetered) {
            meteredMutex.withLock { doFetch() }
        } else {
            doFetch()
        }
    }

    suspend fun fetchFullText(
        context: Context,
        url: String,
        httpClient: SharedHttpClient.JdkClient
    ): String? {
        return fetchPageDetails(context, url, httpClient)?.text
    }

    fun extractPageDetails(rawHtml: String, baseUrl: String): ExtractedPage {
        val doc = Jsoup.parse(rawHtml, baseUrl)
        val mediaUrls = MediaUrlExtractor.extractMediaFromHtml(rawHtml, baseUrl)

        doc.select("script, style, nav, footer, header, aside, iframe, noscript").remove()
        val contentElement = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst(".content, .post, .article")
            ?: doc.body()

        val text = contentElement?.text()?.trim() ?: ""
        return ExtractedPage(text = text, mediaUrls = mediaUrls)
    }

    fun extractCleanArticle(rawHtml: String): String {
        return extractPageDetails(rawHtml, "").text
    }
}

fun buildTelegramRssUrl(username: String): String {
    return "http://${MewsRepository.HUB_ADDRESS}/telegram/channel/${username.trim()}?limit=500&key=${MewsRepository.SERVER_KEY}"
}