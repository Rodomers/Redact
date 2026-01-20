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
import kotlinx.coroutines.currentCoroutineContext
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rds.mews.core.DbHelper
import com.rds.mews.core.LLMClient
import com.rds.mews.MainActivity
import com.rds.mews.core.NewsSummarizer
import com.rds.mews.R
import com.rds.mews.core.RssFetcher
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
            try {
                doWork(oneTimeUpdate)
            } catch (e: Exception) {
                println("TitlesUpdateService: Глобальная ошибка в корутине: ${e.message}")
                e.printStackTrace()
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun doWork(oneTimeUpdate: Boolean) {
        val applicationContext = this.applicationContext
        val db = DbHelper(applicationContext)

        if (!isNetworkAvailable(applicationContext) || !MewsRepository.isInitialized) {
            AlarmScheduler.schedule(applicationContext, System.currentTimeMillis() + 300000L)
            println("TitlesUpdateService: задача перепланирована на ${System.currentTimeMillis() + 300000L} unix")
            return
        }

        if (!oneTimeUpdate) AlarmScheduler.cancel(applicationContext)

        val updateDeltaMills = System.currentTimeMillis() - MewsRepository.lastTitlesUpdate.first()
        val enableProxy = MewsRepository.proxyEnabled.first()

        val currentLLM = MewsRepository.llmModel.first().apiModelName
        val llmApiKey = MewsRepository.userApiKey.first()
        val rssLastUpdate = MewsRepository.lastRssUpdate.first()
        val rssUpdateInterval = MewsRepository.rssUpdateInterval.first()
        val titlesUpdatePeriod = MewsRepository.titlesPeriod.first().num
        val titlesPeriod = if (!oneTimeUpdate || titlesUpdatePeriod == 0) {
            updateDeltaMills / 3600000L + 1
        } else titlesUpdatePeriod ?: 0L
        val titlesNum = MewsRepository.titlesNum.first().num
        val filterTopics = MewsRepository.filterTopics.first()

        val fetcher = RssFetcher(db)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey, enableProxy = enableProxy)
        val summarizer = NewsSummarizer(db, llm)

        val startTitlesNum = db.getTitles().filter { it.text != "<промежуточный текст>" }.size

        MewsRepository.setUpdatingTitles(true)
        MewsRepository.setUpdatingState("updating")

        try {
            if (!currentCoroutineContext().isActive) return

            // надо вызывать ошибку парсинга, но не прекращать обновление

            MewsRepository.setUpdatingProgress(0.1f)

            MewsRepository.setUpdatingState("parsing")
            val result = fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600).errors.isEmpty()
            MewsRepository.setLastRssUpdate(System.currentTimeMillis())

            if (!currentCoroutineContext().isActive) return

            if ((oneTimeUpdate || updateDeltaMills >= 7200000L)) {
//                val titles = db.getTitles()
//                if (titles.none { it.text.contains("<промежуточный текст>") || it.time == 0L || it.sources.contains("<промежуточный текст>") }) {
//                    db.titlesTimeKill(0)
//                }
                var iter = 0
                var res: SummarizationResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)
                while (MewsRepository.updatingTitles.first() && iter <= 3 && currentCoroutineContext().isActive) {
                    res = summarizer.summarizeTopics(
                        maxTopics = titlesNum,
                        messageSeconds = titlesPeriod.toLong() * 3600,
                        readyFunc = {
                            MewsRepository.setUpdatingTitles(false)
                        },
                        filterTopics = filterTopics
                    )
                    if (res is SummarizationResult.Success) break
                    iter++
                }

                val currentTitlesNum = db.getTitles().filter { it.text != "<промежуточный текст>" }.size
                if (startTitlesNum != currentTitlesNum && currentTitlesNum != 0) {
                    if (!MewsRepository.isInitialized) {
                        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                        MewsRepository.initialize(applicationContext, appScope)
                    }
                    MewsRepository.triggerTitlesRefresh()
                }

                when (res) {
                    is SummarizationResult.Success -> {
                        MewsRepository.clearError()

                        if (!oneTimeUpdate) sendSuccessNotification()
                    }
                    is SummarizationResult.Failure -> MewsRepository.saveLastError(res)
                }
            }

            val autoUpdateEnabled = MewsRepository.titlesAlarmUpdate.first()
            if (autoUpdateEnabled && !oneTimeUpdate) {
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
        } catch (e: CancellationException) {
            println("TitlesUpdateService: Корутина отменена.")
        } catch (e: Exception) {
            println("TitlesUpdateService: Поймана ошибка. Тип: ${e.javaClass.simpleName}, Сообщение: ${e.message}")
            e.printStackTrace()
            val errorResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
            MewsRepository.saveLastError(errorResult)
        } finally {
            println("TitlesUpdateService: Вход в блок FINALLY.")
            MewsRepository.setUpdatingProgress(1f)
            MewsRepository.setUpdatingTitles(false)
            delay(500L)
            MewsRepository.setUpdatingState("off")
            MewsRepository.setUpdatingProgress(0f)
            println("TitlesUpdateService: Состояние обновлено на 'off'.")
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

    private fun sendSuccessNotification() {
        val channelId = "update_service_channel"
        val notificationId = 1

        val channel = NotificationChannel(
            channelId,
            getString(R.string.titles_updated_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("selected_tab", 1)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.titles_updated_title))
            .setContentText(getString(R.string.titles_updated_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (isNotificationPermissionGranted(applicationContext)) {
            manager.notify(notificationId, notification)
        }
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