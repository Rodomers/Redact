package com.rds.mews

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object AlarmScheduler {
    private const val TITLES_REQUEST_CODE = 101
    private const val RSS_REQUEST_CODE = 102
    private const val TAG = "AlarmScheduler"

    fun schedule(context: Context, timeMills: Long, rss: Boolean = false) {
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
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
    }
}

class TitlesAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workRequest = OneTimeWorkRequestBuilder<TitlesUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

class RSSAlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workRequest = OneTimeWorkRequestBuilder<RssUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

class BootCompletedReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val nextRunTimeMills = System.currentTimeMillis() + 120000L
            AlarmScheduler.schedule(context, nextRunTimeMills)
            AlarmScheduler.schedule(context, nextRunTimeMills, true)
        }
    }
}