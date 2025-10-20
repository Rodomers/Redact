package com.rds.mews

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.currentCoroutineContext
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
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
        startForeground(NOTIFICATION_ID, createNotification())

        scope.launch {
            try {
                doWork()
            } catch (e: Exception) {
                // Обработка непредвиденных ошибок на уровне сервиса
                println("TitlesUpdateService: Глобальная ошибка в корутине: ${e.message}")
                e.printStackTrace()
            } finally {
                // Убеждаемся, что сервис всегда останавливается
                stopSelf(startId)
            }
        }

        // Если система убьет сервис, не перезапускать его автоматически
        return START_NOT_STICKY
    }

    private suspend fun doWork() {
        val applicationContext = this.applicationContext
        val settingsManager = SettingsManager(applicationContext)
        val db = DbHelper(applicationContext)

        val currentLLM = settingsManager.getString(MewsRepository.CURRENT_LLM_MODEL, "gemini-2.0-flash")
        val llmApiKey = settingsManager.getString(MewsRepository.USER_API_KEY, MewsRepository.DEFAULT_GEMINI_API_KEY)
        val rssLastUpdate = settingsManager.getLong(MewsRepository.LAST_RSS_UPDATE, 0L)
        val rssUpdateInterval = settingsManager.getInt(MewsRepository.RSS_UPDATE_INTERVAL, 30)
        val titlesPeriod = settingsManager.getInt(MewsRepository.TITLES_PERIOD, 24)
        val titlesNum = settingsManager.getInt(MewsRepository.TITLES_NUM, 10)
        val filterTopics = settingsManager.getBoolean(MewsRepository.FILTER_TOPICS, false)

        val fetcher = RssFetcher(db)
        val llm = LLMClient(MODEL = currentLLM, apiKey = llmApiKey)
        val summarizer = NewsSummarizer(db, llm)

        settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, true)
        settingsManager.saveString(MewsRepository.UPDATING_STATE, "updating")

        try {
            if (!currentCoroutineContext().isActive) return

            val needToFetchRss = (System.currentTimeMillis() - rssLastUpdate) / 60000L > rssUpdateInterval
            val noFetchErrors = if (needToFetchRss) {
                val result = fetcher.fetchAndStoreAll(messAliveTime = titlesPeriod.toLong() * 3600).errors.isEmpty()
                settingsManager.saveLong(MewsRepository.LAST_RSS_UPDATE, System.currentTimeMillis())
                result
            } else {
                true
            }

            if (!currentCoroutineContext().isActive) return

            if (noFetchErrors) {
                val titles = db.getTitles()
                if (titles.none { it.text.contains("<промежуточный текст>") || it.time == 0L || it.sources.contains("<промежуточный текст>") }) {
                    db.titlesTimeKill(0)
                }
                var iter = 0
                var res: SummarizationResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR)
                while (settingsManager.getBoolean(MewsRepository.UPDATING_TITLES, false) && iter <= 5 && currentCoroutineContext().isActive) {
                    res = summarizer.summarizeTopics(
                        maxTopics = titlesNum,
                        messageSeconds = titlesPeriod.toLong() * 3600,
                        readyFunc = {
                            settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, false)
                        },
                        filterTopics = filterTopics
                    )
                    if (res is SummarizationResult.Success) break
                    iter++
                }

                when (res) {
                    is SummarizationResult.Success -> {
                        settingsManager.clearLastError()
                        MewsRepository.triggerTitlesRefresh()
                    }
                    is SummarizationResult.Failure -> settingsManager.saveLastError(res)
                }
            }
            settingsManager.saveString(MewsRepository.UPDATING_STATE, "off")
            settingsManager.saveLong(MewsRepository.LAST_TITLES_UPDATE, System.currentTimeMillis())

            // --- ПЛАНИРОВАНИЕ СЛЕДУЮЩЕГО ЗАПУСКА ---
            val autoUpdateEnabled = settingsManager.getBoolean(MewsRepository.TITLES_AUTO_UPDATE, false)
            if (autoUpdateEnabled) {
                val titlesUpdatePeriodHours = settingsManager.getInt(MewsRepository.TITLES_AUTO_UPDATE_FREQUENCY, 24)
                val titlesUpdateTimeMins = settingsManager.getInt(MewsRepository.TITLES_ALARM_MINS, 540)

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

                println("TitlesUpdateService: Следующее обновление запланировано на ${
                    Date(
                        nextRunTimeMillis
                    )
                }")
            } else {
                AlarmScheduler.cancel(applicationContext)
                println("TitlesUpdateService: Автообновление отключено, запланированные задачи отменены.")
            }

        } catch (e: CancellationException) {
            println("TitlesUpdateService: Корутина отменена.")
        } catch (e: Exception) {
            println("TitlesUpdateService: Поймана ошибка. Тип: ${e.javaClass.simpleName}, Сообщение: ${e.message}")
            e.printStackTrace()
            val errorResult = SummarizationResult.Failure(SummarizationErrorType.UNKNOWN_ERROR, e)
            settingsManager.saveLastError(errorResult)
        } finally {
            println("TitlesUpdateService: Вход в блок FINALLY.")
            settingsManager.saveString(MewsRepository.UPDATING_STATE, "off")
            settingsManager.saveBoolean(MewsRepository.UPDATING_TITLES, false)
            println("TitlesUpdateService: Состояние обновлено на 'off'.")
        }
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.titles_service_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.titles_service_text))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}