package com.rds.mews.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

object AlarmScheduler {
    private const val TITLES_REQUEST_CODE = 101
    private const val RSS_REQUEST_CODE = 102
    private const val TAG = "AlarmScheduler"

    fun schedule(context: Context, timeMills: Long, rss: Boolean = false, oneTimeTitlesUpdate: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val receiver = when (rss) {
            true -> RSSAlarmReceiver::class.java
            else -> TitlesAlarmReceiver::class.java
        }
        val requestCode = when (rss) {
            true -> RSS_REQUEST_CODE
            else -> TITLES_REQUEST_CODE
        }
        val intent = Intent(context.applicationContext, receiver)
        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (requestCode == TITLES_REQUEST_CODE) intent.putExtra("oneTimeTitlesUpdate", oneTimeTitlesUpdate)

        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMills, pendingIntent)
        }
        else alarmManager.set(AlarmManager.RTC_WAKEUP, timeMills, pendingIntent)
    }

    fun cancel (context: Context, rss: Boolean = false) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val receiver = when (rss) {
            true -> RSSAlarmReceiver::class.java
            else -> TitlesAlarmReceiver::class.java
        }
        val requestCode = when (rss) {
            true -> RSS_REQUEST_CODE
            else -> TITLES_REQUEST_CODE
        }
        val intent = Intent(context.applicationContext, receiver)
        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

class TitlesAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, TitlesUpdateService::class.java)
        serviceIntent.putExtra("oneTimeUpdate", intent.getBooleanExtra("oneTimeTitlesUpdate", false))

        context.startForegroundService(serviceIntent)
    }
}

class RSSAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workRequest = OneTimeWorkRequestBuilder<RssUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        println("BootCompletedReceiver: Boot completed received")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!MewsRepository.isInitialized) {
                    println("BootCompletedReceiver: Repository not initialized, attempting manual init (fallback)")
                    return@launch
                }

                val isAutoUpdateEnabled = MewsRepository.titlesAlarmUpdate.first()

                if (isAutoUpdateEnabled) {
                    val titlesUpdatePeriodHrs = MewsRepository.titlesAutoUpdateFrequency.first()
                    val titlesUpdateTimeMins = MewsRepository.titlesAlarmTimeMins.first()

                    val nextRunTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, titlesUpdateTimeMins / 60)
                        set(Calendar.MINUTE, titlesUpdateTimeMins % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val now = Calendar.getInstance()
                    while (nextRunTime.before(now)) {
                        val addHours = if (titlesUpdatePeriodHrs == 12) 12 else 24
                        nextRunTime.add(Calendar.HOUR_OF_DAY, addHours)
                    }

                    val nextRunTimeMillis = nextRunTime.timeInMillis

                    AlarmScheduler.schedule(context, nextRunTimeMillis)

                    println("BootCompletedReceiver: Следующее обновление восстановлено на ${Date(nextRunTimeMillis)}")
                } else {
                    println("BootCompletedReceiver: Автообновление отключено в настройках.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}