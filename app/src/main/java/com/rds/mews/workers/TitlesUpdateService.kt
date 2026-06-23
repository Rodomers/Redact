package com.rds.mews.workers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rds.mews.MainActivity
import com.rds.mews.R
import com.rds.mews.settings_manager.SummarizationErrorType
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.localcore.isNotificationPermissionGranted
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Date
import java.util.concurrent.CancellationException

class TitlesUpdateService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "TitlesUpdateChannel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val oneTimeUpdate = intent?.getBooleanExtra("oneTimeUpdate", false) ?: false
        startForeground(NOTIFICATION_ID, createNotification())

        scope.launch {
            if (!isNetworkAvailable(applicationContext) || !MewsRepository.isInitialized) {
                AlarmScheduler.schedule(applicationContext, System.currentTimeMillis() + 300000L)
                stopSelf(startId)
                return@launch
            }

            if (!oneTimeUpdate) AlarmScheduler.cancel(applicationContext)

            val updater = TitlesUpdater()
            val result = try {
                updater.performUpdate(oneTimeUpdate)
            } catch (_: CancellationException) {
                SummarizationResult.Failure(SummarizationErrorType.JOB_CANCELLED)
            } catch (e: Exception) {
                android.util.Log.e("TitlesUpdateService", "Fatal error in service", e)
                SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
            }

            if (!oneTimeUpdate) {
                scheduleNextUpdate()
            }

            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun scheduleNextUpdate() {
        val autoUpdateEnabled = MewsRepository.titlesAlarmUpdate.first()
        if (autoUpdateEnabled) {
            val titlesUpdatePeriodHours = MewsRepository.titlesAutoUpdateFrequency.first().num
            val titlesUpdateTimeMins = MewsRepository.titlesAlarmTimeMins.first()

            val nextRunTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, titlesUpdateTimeMins / 60)
                set(Calendar.MINUTE, titlesUpdateTimeMins % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            while (nextRunTime.before(Calendar.getInstance())) {
                nextRunTime.add(Calendar.HOUR_OF_DAY, titlesUpdatePeriodHours)
            }

            val nextRunTimeMillis = nextRunTime.timeInMillis
            AlarmScheduler.schedule(applicationContext, nextRunTimeMillis)

            println("TitlesUpdateService: Следующее обновление запланировано на ${Date(nextRunTimeMillis)}")
        }
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.titles_service_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.titles_service_title))
            .setContentText(getString(R.string.titles_service_text))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .build()
    }

    @SuppressLint("ServiceCast")
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}