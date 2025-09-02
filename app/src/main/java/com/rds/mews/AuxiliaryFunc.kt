package com.rds.mews

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit


// "dd.MM'\n'HH:mm"
fun getFormattedTimeUnix(unixTime: Long, showDates: Boolean = false): String {
    val instant = Instant.ofEpochMilli(unixTime)
    val zoneId = ZoneId.systemDefault()

    val formatter = when (showDates) {
        true -> DateTimeFormatter.ofPattern("HH:mm'\n'dd.MM")
            .withLocale(Locale.getDefault())
            .withZone(zoneId)
        else -> DateTimeFormatter.ofPattern("HH:mm")
            .withLocale(Locale.getDefault())
            .withZone(zoneId)
    }

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
    context: Context, db: DbHelper, settingsViewModel: SettingsViewModel, settingsManager: SettingsManager, returnExisting: Boolean = false, readyFunc: () -> Unit = {},
): List<Title> {
    if (!returnExisting) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateWorkRequest = OneTimeWorkRequestBuilder<TitlesUpdateWorker>()
            .setConstraints(constraints)
            .build()

        settingsViewModel.setUpdatingTitles(true)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "titles_update_work",
            ExistingWorkPolicy.KEEP,
            updateWorkRequest
        )

        settingsManager.awaitTitlesUpdate()
        readyFunc()
    }
    else {
        settingsViewModel.setUpdatingTitles(false)
        readyFunc()
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

fun scheduleRssUpdate(context: Context, intervalInMinutes: Int, sources: Boolean = false) {
    val repeatInterval = maxOf(15L, intervalInMinutes.toLong())

    val constraints = androidx.work.Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    var repeatingRequestBuilder = PeriodicWorkRequestBuilder<RssUpdateWorker>(repeatInterval, TimeUnit.MINUTES)
        .setConstraints(constraints)
    if (sources) repeatingRequestBuilder = repeatingRequestBuilder.setInitialDelay(40, TimeUnit.SECONDS)
    val repeatingRequest = repeatingRequestBuilder.build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "rss-update-work",
        ExistingPeriodicWorkPolicy.REPLACE,
        repeatingRequest
    )

    println("Scheduled RSS update")
}

fun changeRssUpdateSchedule(context: Context, settingsModel: SettingsViewModel, newValue: Int) {
    settingsModel.setRssUpdateInterval(newValue)
    scheduleRssUpdate(context, newValue)
}