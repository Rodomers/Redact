package com.rds.mews.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.localcore.isNotificationPermissionGranted

fun Context.sendSuccessNotification() {
    val channelId = "update_service_channel"
    val notificationId = 1

    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        channelId,
        getString(R.string.titles_updated_name),
        NotificationManager.IMPORTANCE_DEFAULT
    )
    manager.createNotificationChannel(channel)

    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("selected_tab", 1)
    }
    val pendingIntent = PendingIntent.getActivity(
        this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle(getString(R.string.titles_updated_title))
        .setContentText(getString(R.string.titles_updated_text))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    if (isNotificationPermissionGranted(this)) {
        manager.notify(notificationId, notification)
    }
}