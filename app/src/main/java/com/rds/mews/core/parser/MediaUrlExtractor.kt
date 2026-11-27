package com.rds.mews.core.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

object MediaUrlExtractor {

    private val MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
        "mp4", "webm", "mkv", "mov", "m4v", "3gp", "m3u8", "ts"
    )

    fun resolveMediaUrl(baseUrl: String, rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || trimmed.startsWith("data:", ignoreCase = true) || trimmed.startsWith("javascript:", ignoreCase = true)) {
            return ""
        }
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        if (trimmed.startsWith("//")) {
            return "https:$trimmed"
        }
        return try {
            if (baseUrl.isNotBlank()) {
                val baseUri = URI(baseUrl)
                val resolved = baseUri.resolve(trimmed).toString()
                if (resolved.startsWith("http://", ignoreCase = true) || resolved.startsWith("https://", ignoreCase = true)) {
                    resolved
                } else {
                    ""
                }
            } else {
                trimmed
            }
        } catch (_: Exception) {
            if (baseUrl.startsWith("http", ignoreCase = true)) {
                val host = baseUrl.substringBefore("/", baseUrl).substringBefore("?")
                if (trimmed.startsWith("/")) "$host$trimmed" else "$host/$trimmed"
            } else {
                ""
            }
        }
    }

    fun isMediaEnclosure(type: String, url: String): Boolean {
        if (url.isBlank()) return false
        val lowerType = type.lowercase(Locale.ROOT)
        if (lowerType.startsWith("image/") || lowerType.startsWith("video/")) {
            return true
        }
        val cleanUrl = url.substringBefore("?").substringBefore("#").lowercase(Locale.ROOT)
        val ext = cleanUrl.substringAfterLast('.', "")
        return ext in MEDIA_EXTENSIONS
    }

    private val HAS_RESIZE_SUFFIX_REGEX = Regex("""-\d{2,4}x\d{2,4}\.""", RegexOption.IGNORE_CASE)
    private val DISCARD_QUERY_PARAMS = setOf("w", "h", "width", "height", "resize", "fit", "crop", "quality", "q", "ssl", "size")

    fun getCanonicalKey(url: String): String {
        if (url.isBlank()) return ""
        val cleanUrl = url.trim().replace(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
            .substringBefore("#")

        val pathPart = cleanUrl.substringBefore("?")
        val queryPart = if (cleanUrl.contains("?")) cleanUrl.substringAfter("?") else ""

        val normalizedPath = pathPart.replace(Regex("""(-\d{2,4}x\d{2,4})(\.[a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE), "$2")

        val filteredQueryParams = if (queryPart.isNotBlank()) {
            queryPart.split("&")
                .filterNot { param ->
                    val key = param.substringBefore("=").lowercase(Locale.ROOT)
                    key in DISCARD_QUERY_PARAMS
                }
                .sorted()
                .joinToString("&")
        } else ""

        return if (filteredQueryParams.isNotBlank()) {
            "$normalizedPath?$filteredQueryParams"
        } else {
            normalizedPath
        }
    }

    fun deduplicateUrls(urls: List<String>): List<String> {
        if (urls.isEmpty()) return emptyList()
        val groups = LinkedHashMap<String, MutableList<String>>()

        for (url in urls) {
            val key = getCanonicalKey(url)
            if (key.isNotBlank()) {
                groups.getOrPut(key) { mutableListOf() }.add(url)
            }
        }

        val result = mutableListOf<String>()
        for ((_, candidateUrls) in groups) {
            val bestUrl = candidateUrls.maxByOrNull { url ->
                var score = 0
                if (url.startsWith("https://", ignoreCase = true)) score += 100
                if (!url.contains(HAS_RESIZE_SUFFIX_REGEX)) score += 200
                if (!url.contains("?")) score += 10
                score
            } ?: candidateUrls.first()
            result.add(bestUrl)
        }

        return result
    }

    fun extractMediaFromHtml(htmlContent: String, baseUrl: String): List<String> {
        if (htmlContent.isBlank()) return emptyList()
        val doc = Jsoup.parse(htmlContent, baseUrl)
        val rawList = mutableListOf<String>()

        val metaTags = doc.select(
            "meta[property=\"og:image\"], meta[property=\"og:image:url\"], meta[property=\"og:image:secure_url\"], " +
            "meta[property=\"og:video\"], meta[property=\"og:video:url\"], meta[property=\"og:video:secure_url\"], " +
            "meta[name=\"twitter:image\"], meta[name=\"twitter:image:src\"], meta[name=\"twitter:player:stream\"]"
        )
        for (meta in metaTags) {
            val content = meta.attr("content").ifBlank { meta.attr("value") }
            val resolved = resolveMediaUrl(baseUrl, content)
            if (resolved.isNotBlank() && isFilterableMedia(resolved)) {
                rawList.add(resolved)
            }
        }

        val elements = doc.select("img, video, source")
        for (el in elements) {
            val candidate = extractCandidateUrl(el)
            val resolved = resolveMediaUrl(baseUrl, candidate)
            if (resolved.isNotBlank() && isFilterableMedia(resolved)) {
                rawList.add(resolved)
            }
        }

        return deduplicateUrls(rawList)
    }

    private fun extractCandidateUrl(el: Element): String {
        val dataSrc = el.attr("data-src")
            .ifBlank { el.attr("data-original") }
            .ifBlank { el.attr("data-lazy-src") }
            .ifBlank { el.attr("data-actualsrc") }
        if (dataSrc.isNotBlank()) return dataSrc

        val src = el.attr("src")
        if (src.isNotBlank() && !src.startsWith("data:", ignoreCase = true)) return src

        val srcset = el.attr("srcset").ifBlank { el.attr("data-srcset") }
        if (srcset.isNotBlank()) {
            val firstCandidate = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!firstCandidate.isNullOrBlank() && !firstCandidate.startsWith("data:", ignoreCase = true)) {
                return firstCandidate
            }
        }

        return if (src.isNotBlank() && !src.startsWith("data:", ignoreCase = true)) src else ""
    }

    private fun isFilterableMedia(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return !(lower.contains("1x1") || lower.contains("pixel.gif") || lower.contains("spacer.gif") || lower.contains("blank.gif"))
    }
}
