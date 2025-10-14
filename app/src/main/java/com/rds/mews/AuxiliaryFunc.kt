package com.rds.mews

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.collection.IntList
import androidx.collection.intListOf
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit


// "dd.MM'\n'HH:mm"
fun getFormattedTimeUnix(unixTime: Long, date: Boolean = false): String {
    val instant = Instant.ofEpochMilli(unixTime)
    val zoneId = ZoneId.systemDefault()

    val formatter = when (date) {
        true -> DateTimeFormatter.ofPattern("dd.MM")
            .withLocale(Locale.getDefault())
            .withZone(zoneId)
        else -> DateTimeFormatter.ofPattern("HH:mm")
            .withLocale(Locale.getDefault())
            .withZone(zoneId)
    }

    return formatter.format(instant)
}

fun formatUpdateTime(unixMillis: Long): Pair<Int, String> {
    val instant = Instant.ofEpochMilli(unixMillis)
    val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val date = dateTime.toLocalDate()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val timeString = dateTime.format(timeFormatter)

    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val dateInt = when (date) {
        today -> R.string.today
        yesterday -> R.string.yesterday
        else -> 0
    }

    return Pair(dateInt, timeString)
}

fun defineSourceType(link: String): SourceType {
    return when {
        link.contains("t.me") -> SourceType.TELEGRAM_CHANNEL
        else -> SourceType.RSS_FEED
    }
}

fun sourcesTypeInterpreter(sourceType: SourceType): Int {
    return when (sourceType) {
        SourceType.RSS_FEED -> R.string.source_type_rss
        SourceType.TELEGRAM_CHANNEL -> R.string.source_type_telegram
    }
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

//suspend fun updateTitles(
//    context: Context, db: DbHelper, repository: MewsRepository, settingsManager: SettingsManager, returnExisting: Boolean = false, readyFunc: () -> Unit = {},
//): List<Title> {
//    if (!returnExisting) {
//        repository.cancelTitlesAutoUpdates(context)
//
//        val constraints = androidx.work.Constraints.Builder()
//            .setRequiredNetworkType(NetworkType.CONNECTED)
//            .build()
//
//        val updateWorkRequest = OneTimeWorkRequestBuilder<TitlesUpdateWorker>()
//            .setConstraints(constraints)
//            .build()
//
//        repository.setUpdatingTitles(true)
//        WorkManager.getInstance(context).enqueueUniqueWork(
//            "titles_update_work",
//            ExistingWorkPolicy.KEEP,
//            updateWorkRequest
//        )
//
//        settingsManager.awaitTitlesUpdate()
//        readyFunc()
//    }
//    else {
//        if (settingsManager.getBoolean(repository.UPDATING_TITLES, false)) {
//            val constraints = androidx.work.Constraints.Builder()
//                .setRequiredNetworkType(NetworkType.CONNECTED)
//                .build()
//
//            val updateWorkRequest = OneTimeWorkRequestBuilder<TitlesUpdateWorker>()
//                .setConstraints(constraints)
//                .build()
//
//            repository.setUpdatingTitles(true)
//            WorkManager.getInstance(context).enqueueUniqueWork(
//                "titles_update_work",
//                ExistingWorkPolicy.REPLACE,
//                updateWorkRequest
//            )
//
//            settingsManager.awaitTitlesUpdate()
//        }
//        readyFunc()
//    }
//
//    val list = db.getTitles()
//
//    return list
//}

fun strTransform(original: String, separator: String): String {
    val arr = original.split(", ")
    val res = arr.map {it -> it.trim()}.distinct()

    return res.joinToString(separator)
}

fun setRssUpdate(context: Context, sources: Boolean = false) {
    val constraints = androidx.work.Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val oneTimeWorkRequestBuilder = OneTimeWorkRequestBuilder<RssUpdateWorker>()
        .setConstraints(constraints)
    if (sources) oneTimeWorkRequestBuilder.setInitialDelay(40, TimeUnit.SECONDS)
    val workRequest = oneTimeWorkRequestBuilder.build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "rss-update-work-once",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}

fun Context.observeStringSharedPreference(key: String, defaultValue: String): Flow<String> {
    val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)

    return callbackFlow {
        trySend(sharedPreferences.getString(key, defaultValue) ?: defaultValue)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener {prefs, changedKey ->
            if (key == changedKey) trySend(prefs.getString(key, defaultValue) ?: defaultValue)
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}

fun mapResultToUiResources(result: SummarizationResult): IntList {
    return when (result) {
        is SummarizationResult.Success -> {
            intListOf(R.string.err_header_success, R.string.err_text_success, R.string.err_btn_success)
        }
        is SummarizationResult.Failure -> {
            when (result.type) {
                SummarizationErrorType.EXTRACT_TOPICS_FAILED ->
                    intListOf(R.string.err_header_extractFail, R.string.err_text_extractFail, R.string.err_btn_extractFail)

                SummarizationErrorType.SUMMARIZE_TOPICS_FAILED ->
                    intListOf(R.string.err_header_summarizing, R.string.err_text_summarizing, R.string.err_btn_summarizing)

                SummarizationErrorType.NETWORK_TIMEOUT ->
                    intListOf(R.string.err_header_network_timeout, R.string.err_text_network_timeout, R.string.err_btn_network_timeout)

                SummarizationErrorType.CRITICAL_SUMMARIZATION_ERROR ->
                    intListOf(R.string.err_header_sumCritical, R.string.err_text_sumCritical, R.string.err_btn_sumCritical)

                SummarizationErrorType.JSON_PARSING_FAILED ->
                    intListOf(R.string.err_header_parsing, R.string.err_text_parsing, R.string.err_btn_parsing)

                SummarizationErrorType.NO_NEWS_TO_ANALYZE ->
                    intListOf(R.string.err_header_no_news, R.string.err_text_no_news, R.string.err_btn_no_news)

                SummarizationErrorType.FILTER_FAILED ->
                    intListOf(R.string.err_header_filter_failed, R.string.err_text_filter_failed,R.string.err_btn_filter_failed)

                SummarizationErrorType.UNKNOWN_ERROR ->
                {
                    println(result.cause)

                    intListOf(
                        R.string.err_header_interpreter,
                        R.string.err_text_interpreter,
                        R.string.err_btn_interpreter
                    )
                }
            }
        }
    }
}

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

fun isScheduleExactAlarm(context: Context): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true
}

@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimization(context: Context) {
    val intent = Intent().apply {
        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        data = "package:${context.packageName}".toUri()
    }
    context.startActivity(intent)
}