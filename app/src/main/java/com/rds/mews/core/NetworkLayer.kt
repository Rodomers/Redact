package com.rds.mews.core

import com.rds.mews.localcore.SourceType
import com.rds.mews.localcore.defineSourceType
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

@Serializable
data class MinifluxFeedCreationRequest(
    val feed_url: String,
    val category_id: Long = 1,
    val crawler: Boolean
)

@Serializable
data class MinifluxFeedCreationResponse(
    val feed_id: Long
)

@Serializable
data class MinifluxEntry(
    val id: Long,
    val feed_id: Long,
    val title: String,
    val url: String,
    val content: String,
    val author: String? = null,
    val published_at: String
)

@Serializable
data class MinifluxEntriesResponse(
    val total: Int,
    val entries: List<MinifluxEntry>
)

class MinifluxClient(
    private val httpClient: SharedHttpClient.JdkClient
) {
    private val baseUrl: String
        get() {
            val addr = MewsRepository.MINIFLUX_ADDRESS.trim().removeSuffix("/")
            val schemeAddr = if (addr.startsWith("http://") || addr.startsWith("https://")) addr else "https://$addr"
            return if (schemeAddr.endsWith("/v1")) schemeAddr else "$schemeAddr/v1"
        }

    private val apiKey: String
        get() = MewsRepository.MINIFLUX_KEY

    private fun buildMinifluxUrl(feedUrl: String): String {
        var finalUrl = feedUrl.trim()
        if (defineSourceType(finalUrl) == SourceType.TELEGRAM) {
            val username = if (finalUrl.startsWith("@")) {
                finalUrl.drop(1)
            } else {
                finalUrl.trimEnd('/').split("/").last().substringBefore("?")
            }
            val encodedKey = java.net.URLEncoder.encode(MewsRepository.SERVER_KEY, "UTF-8").replace("+", "%20")
            finalUrl = "http://${MewsRepository.HUB_ADDRESS}/telegram/channel/${username.trim()}?key=$encodedKey"
        }
        return finalUrl
    }

    suspend fun createFeed(feedUrl: String): Long = withContext(Dispatchers.IO) {
        val finalUrl = buildMinifluxUrl(feedUrl)
        val url = "$baseUrl/feeds"

        val requestBody = SharedHttpClient.jsonParser.encodeToString(
            MinifluxFeedCreationRequest(
                feed_url = finalUrl,
                category_id = 1L,
                crawler = !finalUrl.contains(MewsRepository.HUB_ADDRESS)
            )
        )

        val headers = mapOf(
            "X-Auth-Token" to apiKey,
            "Content-Type" to "application/json"
        )

        val response = httpClient.post(url, requestBody, headers)

        if (response.status !in 200..299 || response.error != null) {
            throw RuntimeException("Failed to create Miniflux feed: HTTP ${response.status}. Body: ${response.body}")
        }

        val creationResponse = SharedHttpClient.jsonParser.decodeFromString<MinifluxFeedCreationResponse>(response.body)
        return@withContext creationResponse.feed_id
    }

    suspend fun getEntries(feedId: Long, afterTimeMs: Long, limit: Int): List<MinifluxEntry> = withContext(Dispatchers.IO) {
        val afterSeconds = afterTimeMs / 1000
        val url = "$baseUrl/feeds/$feedId/entries?limit=$limit&published_after=$afterSeconds&_cb=${System.currentTimeMillis()}"

        val headers = mapOf(
            "X-Auth-Token" to apiKey,
            "Accept" to "application/json",
            "Cache-Control" to "no-cache, no-store, must-revalidate"
        )

        val response = httpClient.request(method = "GET", urlString = url, headers = headers)

        if (response.status !in 200..299 || response.error != null) {
            throw RuntimeException("Failed to fetch Miniflux entries: HTTP ${response.status}. Error: ${response.error?.message}")
        }

        val entriesResponse = SharedHttpClient.jsonParser.decodeFromString<MinifluxEntriesResponse>(response.body)
        return@withContext entriesResponse.entries
    }

    suspend fun getFeedIdByUrl(feedUrl: String): Long? = withContext(Dispatchers.IO) {
        val finalUrl = buildMinifluxUrl(feedUrl)

        val url = "$baseUrl/feeds?_cb=${System.currentTimeMillis()}"
        val headers = mapOf(
            "X-Auth-Token" to apiKey,
            "Accept" to "application/json",
            "Cache-Control" to "no-cache, no-store, must-revalidate"
        )
        val response = httpClient.request("GET", url, null, headers)

        if (response.status !in 200..299) return@withContext null

        @kotlinx.serialization.Serializable
        data class MinifluxFeed(val id: Long, val feed_url: String)
        val feeds = SharedHttpClient.jsonParser.decodeFromString<List<MinifluxFeed>>(response.body)

        var found = feeds.find { it.feed_url == finalUrl }

        if (found == null && finalUrl.contains("/telegram/channel/")) {
            val baseFinal = finalUrl.substringBefore("?")
            found = feeds.find { it.feed_url.substringBefore("?") == baseFinal }
        }

        return@withContext found?.id
    }
}

object SourceResolver {
    data class ResolvedSource(
        val name: String,
        val websiteUrl: String,
        val feedUrl: String,
        val type: SourceType,
        val avatarUrl: String? = null
    )

    suspend fun resolveSourceDetails(strLink: String, enableProxy: Boolean = false): ResolvedSource? {
        val httpClient = SharedHttpClient.createInstance(
            MewsRepository.HUB_ADDRESS,
            MewsRepository.SERVER_KEY,
            enableProxy = enableProxy
        )

        try {
            val rawLink = strLink.trim()
            var websiteUrl = rawLink
            var feedUrlToSave = rawLink
            var urlToPing = rawLink

            val sourceType = defineSourceType(rawLink)
            if (sourceType == SourceType.TELEGRAM) {
                val username = when {
                    rawLink.startsWith("@") -> rawLink.drop(1)
                    else -> rawLink.trimEnd('/').split("/").last().substringBefore("?")
                }
                websiteUrl = "https://t.me/s/$username"
                feedUrlToSave = websiteUrl
                urlToPing = buildTelegramRssUrl(username)
            } else {
                if (!websiteUrl.startsWith("http")) {
                    websiteUrl = "https://$websiteUrl"
                    feedUrlToSave = websiteUrl
                }

                val discoveredFeed = findRssLink(websiteUrl, httpClient)
                if (discoveredFeed != null) {
                    feedUrlToSave = discoveredFeed
                }
                urlToPing = feedUrlToSave
            }

            val response = httpClient.get(urlToPing)

            if (response.status !in 200..299) {
                return null
            }

            val xmlContent = response.body
            if (xmlContent.isBlank()) return null

            val doc = Jsoup.parse(xmlContent, urlToPing, Parser.xmlParser())

            if (doc.selectFirst("rss, feed, channel") == null) return null

            val name = doc.select("title").firstOrNull()?.text() ?: return null

            if (name.lowercase() == "welcome to rsshub!") return null

            val finalName = if (name.lowercase().contains("telegram")) {
                if (name.contains("-")) name.substringBeforeLast("-").trim() else name.trim()
            } else {
                name
            }

            val xmlAvatarUrl = doc.selectFirst("channel > image > url, logo, icon")?.text()?.takeIf { it.isNotBlank() }
            val avatarUrl = xmlAvatarUrl ?: findAvatarUrl(websiteUrl, httpClient, sourceType)

            return ResolvedSource(
                name = finalName,
                websiteUrl = websiteUrl,
                feedUrl = feedUrlToSave,
                type = sourceType,
                avatarUrl = avatarUrl
            )

        } catch (_: Exception) {
            return null
        } finally {
            httpClient.close()
        }
    }

    private suspend fun findRssLink(url: String, httpClient: SharedHttpClient.JdkClient): String? = withContext(Dispatchers.IO) {
        val response = httpClient.get(url)
        if (response.status !in 200..299 || response.body.isBlank()) {
            return@withContext null
        }

        try {
            val doc = Jsoup.parse(response.body, url)
            val linkElement = doc.selectFirst(
                "link[rel=alternate][type=application/rss+xml], " +
                        "link[rel=alternate][type=application/atom+xml]"
            )
            linkElement?.attr("abs:href")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun findAvatarUrl(url: String, httpClient: SharedHttpClient.JdkClient, type: SourceType): String? = withContext(Dispatchers.IO) {
        try {
            val response = httpClient.get(url)
            if (response.status !in 200..299 || response.body.isBlank()) {
                return@withContext null
            }

            val doc = Jsoup.parse(response.body, url)

            if (type == SourceType.TELEGRAM) {
                val imgElement = doc.selectFirst("img.tgme_page_photo_image")
                return@withContext imgElement?.attr("src")?.takeIf { it.isNotBlank() }
            } else {
                val appleIcon = doc.selectFirst("link[rel=apple-touch-icon]")
                val appleUrl = appleIcon?.attr("abs:href")
                if (!appleUrl.isNullOrBlank()) return@withContext appleUrl

                val shortcutIcon = doc.selectFirst("link[rel=\"shortcut icon\"]")
                val shortcutUrl = shortcutIcon?.attr("abs:href")
                if (!shortcutUrl.isNullOrBlank()) return@withContext shortcutUrl

                val icon = doc.selectFirst("link[rel=icon]")
                val iconUrl = icon?.attr("abs:href")
                if (!iconUrl.isNullOrBlank()) return@withContext iconUrl

                val ogImage = doc.selectFirst("meta[property=\"og:image\"]")
                val ogUrl = ogImage?.attr("abs:content")
                if (!ogUrl.isNullOrBlank()) return@withContext ogUrl
            }
        } catch (_: Exception) {
        }
        return@withContext null
    }

    private fun buildTelegramRssUrl(username: String): String {
        return "http://${MewsRepository.HUB_ADDRESS}/telegram/channel/${username.trim()}?limit=1&key=${MewsRepository.SERVER_KEY}"
    }
}