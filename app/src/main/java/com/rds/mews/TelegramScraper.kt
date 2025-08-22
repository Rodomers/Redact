package com.rds.mews

import org.jsoup.nodes.Document
import java.net.URL
import java.net.HttpURLConnection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup
import org.jsoup.parser.Parser


class TelegramRssClient {


    data class RssItem(
        val title: String,
        val link: String,
        val pubDate: String,
        val description: String
    )

    private fun fetchChannelMessages(channelUrl: String): List<RssItem> {
        val html = downloadHtml(channelUrl)

        // 1. Разбиваем на блоки сообщений
        val messageBlockRegex = Regex(
            """<div class="tgme_widget_message_wrap.*?</div>\s*</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        val blocks = messageBlockRegex.findAll(html)
        val messages = mutableListOf<RssItem>()

        for (block in blocks) {
            val blockHtml = block.value

            // link (из data-post) + запасной вариант из ссылки даты
            val dataPost = Regex("""data-post="([^"]+)"""")
                .find(blockHtml)
                ?.groupValues?.get(1)
                ?: ""

            val messageUrl = when {
                dataPost.isNotEmpty() -> "https://t.me/$dataPost"
                else -> {
                    // запасной вариант: href у .tgme_widget_message_date
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

                // datetime (универсально по любому <time ... datetime="...">)
            val datetime = Regex("""<time[^>]*\sdatetime="([^"]+)"""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(blockHtml)?.groupValues?.get(1).orEmpty()


            // 4. Вытаскиваем текст (игнорируем фото/видео)
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

            val title = text.substring(0, text.length / 3) + "..."
            // val date = formatDate(datetime)
            if (text.isNotEmpty()) {
                messages.add(RssItem(title, messageUrl, datetime, text))
//                println("[$title\n$messageUrl\n$datetime\n$text]")
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

    private fun formatDate(isoDate: String): String {
        val zdt = ZonedDateTime.parse(isoDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(zdt)
    }

    fun buildRss(channelUrl: String): Document {
        val items = fetchChannelMessages(channelUrl)
        val doc = Jsoup.parse(downloadHtml(channelUrl))
        val ogTitle = doc.selectFirst("title")
        val title = ogTitle ?: doc.title()
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.append("\n<rss version=\"2.0\">\n<channel>\n")
        sb.append("$title\n")
        sb.append("<link>$channelUrl</link>\n")
        sb.append("<description>RSS feed for $channelUrl</description>\n")

        for (item in items) {
            sb.append("<item>\n")
            sb.append("<title><![CDATA[${item.title}]]></title>\n")
            sb.append("<link>${item.link}</link>\n")
            sb.append("<pubDate>${item.pubDate}</pubDate>\n")
            sb.append("<description><![CDATA[${item.description}]]></description>\n")
            sb.append("</item>\n")
        }

        sb.append("</channel>\n</rss>")

        return Jsoup.parse(sb.toString(), "", Parser.xmlParser())
    }


}
