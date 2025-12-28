package com.rds.mews.core

import com.rds.mews.localcore.SettingsManager
import com.rds.mews.repositories.MewsRepository
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

// --- Парсер RSS-потоков ---`
class RssFetcher(
    private val db: DbHelper,
    private val settingsManager: SettingsManager,
    enableProxy: Boolean = false
) {
    // Получаем экземпляр общего HTTP-клиента
    private val httpClient = SharedHttpClient.createInstance(MewsRepository.HUB_ADDRESS, MewsRepository.SERVER_KEY, enableProxy = enableProxy)

    // Основной запуск — парсит все RSS-каналы и сохраняет новые сообщения
    suspend fun fetchAndStoreAll(messAliveTime: Long, maxTimeHours: Int = 168): FetchResult {
        val rssList = try {
            db.getRSS() // --- ИСПРАВЛЕНО: Получаем RSS из БД только один раз ---
        } catch (e: Exception) {
            println("Ошибка при получении списка RSS из БД: ${e.message}")
            return FetchResult(0, 0, 0, listOf(e.message ?: "unknown"))
        }

        val lastUpdated = settingsManager.getLong(MewsRepository.LAST_RSS_UPDATE, 0L)
        val newsUpdateDelta: Long? = when (lastUpdated) {
            0L -> null
            else -> (System.currentTimeMillis() - lastUpdated) / 1000
        }

        var feedsProcessed = 0
        var itemsAdded = 0
        var itemsSkipped = 0
        val errors = mutableListOf<String>()

        for (rss in rssList) {
            try {
                var fetchUrl = rss.link

                if (fetchUrl.contains("t.me")) {
                    fetchUrl = buildTelegramRssUrl(fetchUrl.split("/").last().trim())
//                    fetchUrl = "http://${MewsRepository.HUB_ADDRESS}/telegram/channel/${fetchUrl.split("/").last().trim()}?limit=100&key=${MewsRepository.SERVER_KEY}"
                    if (newsUpdateDelta != null) fetchUrl = "$fetchUrl&filter_time=${newsUpdateDelta}"
                }
                println("Fetching RSS: $fetchUrl")

                val xmlContent: String = httpClient.get(fetchUrl).body()
                val doc = Jsoup.parse(xmlContent, fetchUrl, Parser.xmlParser())

                val items = parseRssItems(doc)
                println("items: ${items.size}")
                for (item in items) {
                    val link = item.link ?: continue
                    val desc = item.description ?: continue
                    if (db.findMessage(rss.source, desc) != null) {
                        itemsSkipped++
                        continue
                    }

                    val time = item.pubDateMillis ?: System.currentTimeMillis()
                    val maxAgeMillis = maxTimeHours * 60 * 60 * 1000L

                    if ((System.currentTimeMillis() - time) > maxAgeMillis) {
                        itemsSkipped++
                        continue
                    }

                    val text = buildMessageText(item)
                    db.addMessage(messageTime = time, link = link, source = rss.source, messageText = text)
                    itemsAdded++
                }
                feedsProcessed++

            } catch (e: Exception) {
                val msg = "Ошибка при обработке RSS (id=${rss.id}, link=${rss.link}): ${e.message}"
                println(msg)
                errors.add(msg)
            } finally {
                db.messageTimeKill(messAliveTime * 3)
            }
        }
        return FetchResult(feedsProcessed, itemsAdded, itemsSkipped, errors)
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
        // пытаемся прямой поиск по тэгу
        val nList = parent.select(tagName)
        if (nList.isNotEmpty()) {
            val node = nList[nList.lastIndex]// nList.item(0)
            val text = node?.text()// node?.textContent
            if (!text.isNullOrBlank()) return text.trim()
        }
        // пробуем искать без двоеточия (на случай content:encoded -> encoded)
        if (tagName.contains(":")) {
            val after = tagName.substringAfter(":")
            val nList2 = parent.select(after)
            if (nList.isNotEmpty()) {
                val node = nList2[nList2.lastIndex] // nList2.item(0)
                val text = node?.text()
                if (!text.isNullOrBlank()) return text.trim()
            }
        }
        return null
    }

    // --- Построение сообщения для сохранения в messages.message ---
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

    // --- Парсинг даты pubDate в миллисекунды (попытаться несколько форматов) ---
    private fun tryParseDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val s = dateStr.trim()
        // Список возможных шаблонов (обычные варианты для RSS)
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
                // пробуем следующий формат
            } catch (_: IllegalArgumentException) {
            }
        }
        // Попытка устранить лишние части (например: "Tue, 01 Jan 2019 12:34:56 +0000 (GMT)")
        val noParen = s.replace(Regex("\\(.*?\\)"), "").trim()
        if (noParen != s) return tryParseDate(noParen)
        // если не получилось - вернуть null
        return null
    }
    // тип для возвращения релузьтата парсиинга
    data class FetchResult(
        val feedsProcessed: Int,
        val itemsAdded: Int,
        val itemsSkipped: Int,
        val errors: List<String>
    )
}


suspend fun getRssName(strLink: String, enableProxy: Boolean = true): String? {
    val httpClient = SharedHttpClient.createInstance(
        MewsRepository.HUB_ADDRESS,
        MewsRepository.SERVER_KEY,
        enableProxy = enableProxy
    )

    try {
        val rawLink = strLink.trim()
        val finalLink: String

        when {
            rawLink.startsWith("@") -> {
                val username = rawLink.drop(1)
                finalLink = buildTelegramRssUrl(username)
            }
            rawLink.contains("t.me") || rawLink.contains("telegram.me") -> {
                val username = rawLink.trimEnd('/')
                    .split("/")
                    .last()
                    .substringBefore("?")

                finalLink = buildTelegramRssUrl(username)
            }
            else -> {
                finalLink = rawLink
            }
        }

        val xmlContent: String = httpClient.get(finalLink).body()
        val doc = Jsoup.parse(xmlContent, finalLink, Parser.xmlParser())

        val name = doc.select("title").firstOrNull()?.text() ?: return null
        if (name.lowercase() == "welcome to rsshub!") return null

        if (name.lowercase().contains("telegram")) {
            if (!name.lowercase().contains("channel")) return null

            return name.take(name.indexOfFirst { it == '-' }).trim()
        }

        return name

    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

private fun buildTelegramRssUrl(username: String): String {
    return "https://${MewsRepository.HUB_ADDRESS}/telegram/channel/${username.trim()}?limit=1&key=${MewsRepository.SERVER_KEY}"
}
