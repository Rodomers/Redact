package com.rds.mews

import android.system.Os.link
import kotlinx.coroutines.delay
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import com.rds.mews.TelegramRssClient
import org.jsoup.parser.Parser

// --- Парсер RSS-потоков ---`
class RssFetcher(
    private val db: DbHelper, // Передавать DbHelper из основной программы
) {

    // Основной запуск — парсит все RSS-каналы и сохраняет новые сообщения
    suspend fun fetchAndStoreAll(messAliveTime: Long, maxTime: Int = 168): FetchResult {
        val rssList = try {
            db.getRSS() // получаем RSS из БД
        } catch (e: Exception) {
            println("Ошибка при получении списка RSS из БД: ${e.message}") // Обработка ошибки про неполучении RSS
            return FetchResult(0, 0, 0, listOf(e.message ?: "unknown"))
        }

        var feedsProcessed = 0 // Кол-во проверенных новостей
        var itemsAdded = 0 // Кол-во добавленных новостей в БД
        var itemsSkipped = 0 // Кол-во пропущенных новостей (старые, ошибки, т.д.)
        val errors = mutableListOf<String>() // Список ошибок


        for ((index, rss) in rssList.withIndex()) {
            try {
                var doc: Document = Jsoup.parse("", "", Parser.xmlParser())
                val tgClient = TelegramRssClient()
                if (rss.link.contains("t.me")) {
                    doc = tgClient.buildRss(rss.link)
                } else {
                    println("not tg fetch start")
                    doc = Jsoup.connect(rss.link).get() // Получение XML из RSS
                    println("not tg fetch end")
                }
                val items = parseRssItems(doc) // Парс полученного XML
                for (item in items) {
                    println(item)
                    val link = item.link ?: continue // если нет ссылки — пропускаем
                    val desc = item.description ?: continue // Нет описания новости - пропускаем
                    if (db.findMessage(rss.source, desc) != null) {
                        itemsSkipped++ // Есть новость в БД - пропускаем
                        continue
                    }
                    val time = item.pubDateMillis ?: System.currentTimeMillis() // Пытаемся получить время новости, иначе системное
                    val text = buildMessageText(item) // Текст новости
                    if (time - System.currentTimeMillis() > maxTime * 60 * 60 * 100){
                        itemsSkipped++ // Проверка на актуальность
                        continue
                    }
                    // Добавление в БД
                    db.addMessage(messageTime = time, link = link, source = rss.source, messageText = text)
                    itemsAdded++
                }
                feedsProcessed++

//                if (rss.link.contains("t.me") && index != rssList.lastIndex && rssList.drop(index + 1).any { it.link.contains("t.me") }) {
//                    println("Entered delay")
//                    delay(40000L)
//                }

            } catch (e: Exception) {
                val msg = "Ошибка при обработке RSS (id=${rss.id}, link=${rss.link}): ${e.message}"
                println(msg) // Ошибка лол
                errors.add(msg)
            }
            finally {
                db.messageTimeKill(messAliveTime.toLong() * 3)
            }
        }

        return FetchResult(feedsProcessed, itemsAdded, itemsSkipped, errors)
    }

    // --- Структура внутреннего Item'а ---
    private data class RssItem(
        val title: String? = null,
        val link: String? = null,
        val description: String? = null,
        val pubDateMillis: Long? = null
    )

    // Парсинг предмета из XML по полям
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
suspend fun RSSName(strLink: String): String? {
    try {
        val tgClient = TelegramRssClient()
        var doc: Document
        if (strLink.contains("t.me")) {
            doc = tgClient.buildRss(strLink)
        } else {
            doc = Jsoup.connect(strLink).get() // Получение XML из RSS
        }
        return doc.select("title")[0].text()
    } catch (e: Exception){
        println(e)
        return null
    }
}
