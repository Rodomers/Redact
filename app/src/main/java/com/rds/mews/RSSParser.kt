package com.rds.mews

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


// --- Нужные минимальные интерфейсы / структуры (используйте ваши Dataclasses.RSS при наличии) ---
/**
 * Ожидаемый небольшой датакласс RSS (в вашем проекте уже есть Dataclasses.kt с классом RSS).
 * Если у вас другой пакет/имя, удалите/замените этот класс и используйте существующий.
 */

// Интерфейс, который должен реализовать адаптер к вашему DBHelper.
// Предпочтительно реализовать простые методы: получить все RSS, проверить наличие сообщения по ссылке,
// и добавить новое сообщение (time в millis).

// --- Парсер RSS-потоков ---`
class RssFetcher(
    private val db: DbHelper,
    private val httpTimeoutMs: Int = 15000
) {

    // Основной запуск — парсит все RSS-каналы и сохраняет новые сообщения
    suspend fun fetchAndStoreAll(): FetchResult {
        val rssList = try {
            db.getRSS()
        } catch (e: Exception) {
            println("Ошибка при получении списка RSS из БД: ${e.message}")
            return FetchResult(0, 0, 0, listOf(e.message ?: "unknown"))
        }

        var feedsProcessed = 0
        var itemsAdded = 0
        var itemsSkipped = 0
        val errors = mutableListOf<String>()

        for (rss in rssList) {
            try {
                val doc: Document = Jsoup.connect(rss.link).get()
                val items = parseRssItems(doc)
                for (item in items) {
                    val link = item.link ?: continue // если нет ссылки — пропускаем
                    val desc = item.description ?: continue
                    if (db.findMessage(rss.source, desc) != null) {
                        itemsSkipped++
                        continue
                    }
                    val time = item.pubDateMillis ?: System.currentTimeMillis()
                    val text = buildMessageText(item)
                    db.addMessage(messageTime = time, link = link, source = rss.source, messageText = text)
                    itemsAdded++
                }
                feedsProcessed++

            } catch (e: Exception) {
                val msg = "Ошибка при обработке RSS (id=${rss.id}, link=${rss.link}): ${e.message}"
                println(msg)
                errors.add(msg)
            }
        }

        return FetchResult(feedsProcessed, itemsAdded, itemsSkipped, errors)
    }

    // --- HTTP загрузка (байты) ---
    @Throws(Exception::class)
    suspend private fun fetchUrlBytes(urlStr: String): ByteArray {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = httpTimeoutMs
            readTimeout = httpTimeoutMs
            instanceFollowRedirects = true
            requestMethod ="GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Accept-Encoding", "gzip, deflate")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw Exception("HTTP $code for $urlStr")
            }
            val input = BufferedInputStream(conn.inputStream)
            val buffer = ByteArrayOutputStream()
            val data = ByteArray(8192)
            var n: Int
            while (input.read(data).also { n = it } != -1) {
                buffer.write(data, 0, n)
            }
            input.close()
            return buffer.toByteArray() }
        finally {
            conn.disconnect()
        }
    }



    // --- Структура внутреннего Item'а ---
    private data class RssItem(
        val title: String? = null,
        val link: String? = null,
        val description: String? = null,
        val pubDateMillis: Long? = null
    )


    private fun parseRssItems(doc: Document): List<RssItem> {
        val nodeList = doc.select("item")
        val result = mutableListOf<RssItem>()
        for (i in 0 until nodeList.lastIndex) {
            val node = nodeList[i] ?: continue
            val title = elementText(node, "title")
            val link = elementText(node, "link") ?: elementText(node, "guid") // fallback to guid
            val description = elementText(node, "description")
            val pubDateStr = elementText(node, "pubDate")
            val pubDateMillis = tryParseDate(pubDateStr)
            result += RssItem(title, link, description, pubDateMillis)
        }
        return result
    }


    private fun elementText(parent: Element, tagName: String): String? {
        // пытаемся прямой поиск
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

    data class FetchResult(
        val feedsProcessed: Int,
        val itemsAdded: Int,
        val itemsSkipped: Int,
        val errors: List<String>
    )
}
