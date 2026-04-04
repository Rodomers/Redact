package com.rds.mews.localcore

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.collection.IntList
import androidx.collection.intListOf
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rds.mews.R
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.workers.ParserWorker
import com.rds.mews.workers.RssUpdateWorker
import com.rds.mews.workers.TitlesUpdateService
import com.rds.mews.workers.TitlesUpdateWorker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit


// "dd.MM'\n'HH:mm"
@SuppressLint("WeekBasedYear")
fun getFormattedTimeUnix(unixTime: Long, date: Boolean = false, fullDate: Boolean = false): String {
    val instant = Instant.ofEpochMilli(unixTime)
    val zoneId = ZoneId.systemDefault()

    val formatter = when {
        date -> DateTimeFormatter.ofPattern("dd.MM")
            .withLocale(Locale.getDefault())
            .withZone(zoneId)
        fullDate -> DateTimeFormatter.ofPattern("dd.MM.yyyy")
        else -> DateTimeFormatter.ofPattern("HH:mm")
            .withLocale(Locale.getDefault())
            .withZone(zoneId)
    }

    return formatter.format(instant)
}

fun formatUpdateTime(unixMillis: Long, today: LocalDate = LocalDate.now()): Pair<Int, String> {
    val instant = Instant.ofEpochMilli(unixMillis)
    val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val date = dateTime.toLocalDate()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val timeString = dateTime.format(timeFormatter)

    val yesterday = today.minusDays(1)

    val dateInt = when (date) {
        today -> R.string.today
        yesterday -> R.string.yesterday
        else -> 0
    }

    return Pair(dateInt, timeString)
}

fun intTimeToStr(time: Int): String {
    return if (time / 10 == 0) "0${time}" else time.toString()
}

fun defineSourceType(link: String): SourceType {
    return when {
        link.contains("@") || link.contains("t.me") || link.contains("telegram.me") -> SourceType.TELEGRAM
        else -> SourceType.RSS
    }
}

fun sourcesTypeInterpreter(sourceType: SourceType): Int {
    return when (sourceType) {
        SourceType.RSS -> R.string.source_type_rss
        SourceType.TELEGRAM -> R.string.source_type_telegram
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

fun strTransform(original: String, separator: String): String {
    val arr = original.split(", ")
    val res = arr.map {it -> it.trim()}.distinct()

    return res.joinToString(separator)
}

fun setRssUpdate(context: Context, sources: Boolean = false, intervalMin: Int = 30) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val inputData = workDataOf(RssUpdateWorker.KEY_SOURCES to sources)
    val periodicWorkRequestBuilder = PeriodicWorkRequestBuilder<RssUpdateWorker>(
        intervalMin.toLong(), TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .setInputData(inputData)
    if (sources) {
        periodicWorkRequestBuilder.setInitialDelay(40, TimeUnit.SECONDS)
    }

    val workRequest = periodicWorkRequestBuilder.build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "rss-update-work",
        ExistingPeriodicWorkPolicy.REPLACE,
        workRequest
    )
}

fun setParserUpdate(context: Context, isImmediateSetup: Boolean = false, intervalMin: Int = 30) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val periodicWorkRequestBuilder = PeriodicWorkRequestBuilder<ParserWorker>(
        30L, TimeUnit.MINUTES
    ).setConstraints(constraints)

    if (isImmediateSetup) {
        periodicWorkRequestBuilder.setInitialDelay(10, TimeUnit.SECONDS)
    }

    val workRequest = periodicWorkRequestBuilder.build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "parser-update-work",
        ExistingPeriodicWorkPolicy.REPLACE,
        workRequest
    )

    WorkManager.getInstance(context).cancelUniqueWork("rss-update-work")
}

fun setTitlesUpdate(context: Context) {
    if (isNotificationPermissionGranted(context)) {
        val serviceIntent = Intent(context, TitlesUpdateService::class.java)
        serviceIntent.putExtra("oneTimeUpdate", true)

        context.startForegroundService(serviceIntent)
    }
    else {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateWorkRequest = OneTimeWorkRequestBuilder<TitlesUpdateWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "titles_update_work",
            ExistingWorkPolicy.REPLACE,
            updateWorkRequest
        )
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

                SummarizationErrorType.EMPTY_ANSWER ->
                    intListOf(R.string.err_header_empty_answer, R.string.err_text_empty_answer, R.string.err_btn_empty_answer)

                SummarizationErrorType.NETWORK_TIMEOUT ->
                    intListOf(R.string.err_header_network_timeout, R.string.err_text_network_timeout, R.string.err_btn_network_timeout)

                SummarizationErrorType.JSON_PARSING_FAILED ->
                    intListOf(R.string.err_header_parsing, R.string.err_text_parsing, R.string.err_btn_parsing)

                SummarizationErrorType.NO_NEWS_TO_ANALYZE ->
                    intListOf(R.string.err_header_no_news, R.string.err_text_no_news, R.string.err_btn_no_news)

                SummarizationErrorType.FILTER_FAILED ->
                    intListOf(R.string.err_header_filter_failed, R.string.err_text_filter_failed, R.string.err_btn_filter_failed)

                SummarizationErrorType.JOB_CANCELLED ->
                    intListOf(R.string.err_header_job_cancelled, R.string.err_text_job_cancelled, R.string.err_btn_job_cancelled)

                SummarizationErrorType.RATE_LIMIT_EXCEEDED ->
                    intListOf(R.string.err_header_rate_limit_exceeded, R.string.err_text_rate_limit_exceeded, R.string.err_btn_rate_limit_exceeded)

                SummarizationErrorType.NO_NETWORK ->
                    intListOf(R.string.err_header_no_network, R.string.err_text_no_network, R.string.err_btn_no_network)

                SummarizationErrorType.UNPROCESSED_ITEMS ->
                    intListOf(R.string.err_header_unprocessed_items, R.string.err_text_unprocessed_items, R.string.err_btn_unprocessed_items)

                SummarizationErrorType.API_KEY_INVALID ->
                    intListOf(R.string.err_header_api_key, R.string.err_text_api_key, R.string.err_btn_api_key)

                SummarizationErrorType.QUOTA_EXCEEDED ->
                    intListOf(R.string.err_header_quota, R.string.err_text_quota, R.string.err_btn_quota)

                SummarizationErrorType.CONTENT_BLOCKED ->
                    intListOf(R.string.err_header_blocked, R.string.err_text_blocked, R.string.err_btn_blocked)

                else -> {
                    println("========= CRASH REPORT START =========")
                    result.cause?.printStackTrace()
                    println("========= CRASH REPORT END =========")

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

//@SuppressLint("BatteryLife")
//fun requestIgnoreBatteryOptimization(context: Context) {
//    val intent = Intent().apply {
//        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
//        data = "package:${context.packageName}".toUri()
//    }
//    context.startActivity(intent)
//}

fun isNotificationPermissionGranted(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

fun handleNotificationsPermissionRequest(activity: Activity, onShouldShowDialog: () -> Unit): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }

    val permission = Manifest.permission.POST_NOTIFICATIONS

    return when {
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED -> {
            true
        }

        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
            onShouldShowDialog()
            false
        }

        else -> {
            requestNotificationPermission(activity)
            false
        }
    }
}

fun requestNotificationPermission(activity: Activity) {
    val notificationRequestCode = 101

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                notificationRequestCode
            )
        }
    }
}

fun getScreenQuadrant(config: Configuration, elementBounds: IntRect): ScreenQuadrant {
    val screenWidthPx = config.screenWidthDp.dp.value * config.densityDpi / 160f
    val screenHeightPx = config.screenHeightDp.dp.value * config.densityDpi / 160f

    val centerX = elementBounds.center.x
    val centerY = elementBounds.center.y

    return when {
        centerX < screenWidthPx / 2 && centerY < screenHeightPx / 2 -> ScreenQuadrant.TopLeft
        centerX >= screenWidthPx / 2 && centerY < screenHeightPx / 2 -> ScreenQuadrant.TopRight
        centerX < screenWidthPx / 2 && centerY >= screenHeightPx / 2 -> ScreenQuadrant.BottomLeft
        else -> ScreenQuadrant.BottomRight
    }
}

fun getStringsFromDate(dateString: String): IntList? {
    try {
        val formattedDate = dateString.split(".")

        return when (formattedDate.last().toInt()) {
            1 -> intListOf(R.string.date_01, formattedDate.first().toInt())
            2 -> intListOf(R.string.date_02, formattedDate.first().toInt())
            3 -> intListOf(R.string.date_03, formattedDate.first().toInt())
            4 -> intListOf(R.string.date_04, formattedDate.first().toInt())
            5 -> intListOf(R.string.date_05, formattedDate.first().toInt())
            6 -> intListOf(R.string.date_06, formattedDate.first().toInt())
            7 -> intListOf(R.string.date_07, formattedDate.first().toInt())
            8 -> intListOf(R.string.date_08, formattedDate.first().toInt())
            9 -> intListOf(R.string.date_09, formattedDate.first().toInt())
            10 -> intListOf(R.string.date_10, formattedDate.first().toInt())
            11 -> intListOf(R.string.date_11, formattedDate.first().toInt())
            12 -> intListOf(R.string.date_12, formattedDate.first().toInt())
            else -> null
        }
    } catch (e: Exception) {
        return null
    }
}

fun updatingStateInterpreter(state: String?): Int {
    return when (state) {
        "summarizing_topics" -> R.string.summarizing
        "extracting_topics" -> R.string.extracting_topics
        "updating" -> R.string.updating
        "parsing" -> R.string.parsing
        "filtering_topics" -> R.string.filtering_topics
        else -> R.string.update
    }
}

fun rebuildSourceLink(strLink: String): String {
    val rawLink = strLink.trim()
    var finalLink: String

    return when {
        rawLink.startsWith("@") -> {
            val username = rawLink.drop(1)
            "https://t.me/s/${username}"
        }
        rawLink.contains("t.me") || rawLink.contains("telegram.me") -> {
            val username = rawLink.trimEnd('/')
                .split("/")
                .last()
                .substringBefore("?")

            "https://t.me/s/${username}"
        }
        else -> {
            rawLink
        }
    }
}