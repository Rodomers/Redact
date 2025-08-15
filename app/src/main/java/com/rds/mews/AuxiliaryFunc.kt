package com.rds.mews

import okio.Source
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun getFormattedTimeUnix(unixTime: Long): String {
    val instant = Instant.ofEpochSecond(unixTime)
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