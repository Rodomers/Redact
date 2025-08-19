package com.rds.mews

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


// "dd.MM'\n'HH:mm"
fun getFormattedTimeUnix(unixTime: Long): String {
    val instant = Instant.ofEpochMilli(unixTime)
    val zoneId = ZoneId.systemDefault()

    val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withLocale(Locale.getDefault())
        .withZone(zoneId)

    return formatter.format(instant)
}

suspend fun addSource(source: String, link: String, db: DbHelper): Long {
    return db.addRSS(source, link)
}

suspend fun delSource(source: String, db: DbHelper): Boolean {
    return db.delRSS(source = source)
}

suspend fun changeSource(sourceOld: String, sourceNew: String, db: DbHelper): Boolean {
    return when (sourceOld) {
        sourceNew -> false
        else -> db.changeRssSource(sourceOld, sourceNew)
    }
}

fun linkTransform(link: String): String {
    var res = link
    if (link.contains("t.me") || link.contains("telegram.me")) {
        res = "https://t.me/s/${link.substring(link.lastIndexOf('/') + 1)}"
    }
    if (link.contains('@')) {
        res = "https://t.me/s/${link.substring(link.lastIndexOf('@') + 1)}"
    }
    println(res)
    return res
}

suspend fun updateTitles(
    db: DbHelper, fetcher: RssFetcher, summarizer: NewsSummarizer, settingsViewModel: SettingsViewModel, returnExisting: Boolean = false
): List<Title> {
    if (!returnExisting) {
        try {
            if (fetcher.fetchAndStoreAll().errors.isEmpty()) {
                db.titlesTimeKill(0)
                db.messageTimeKill(settingsViewModel.titlesPeriod.intValue.toLong() * 3600)
                summarizer.summarizeTopics(
                    maxTopics = settingsViewModel.titlesNum.intValue,
                    messageSeconds = settingsViewModel.titlesPeriod.intValue.toLong() * 3600
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val list = db.getTitles((settingsViewModel.titlesPeriod.intValue * 3600).toLong())

    return list.map {
        Title(
            id = it.id,
            time = it.time,
            title = it.title,
            text = it.text,
            sources = strTransform(it.sources, ", "),
            links = strTransform(it.links, "\n")
        )
    }
}

fun strTransform(original: String, separator: String): String {
    val arr = original.split(", ")
    val res = arr.map {it -> it.trim()}.distinct()

    return res.joinToString(separator)
}