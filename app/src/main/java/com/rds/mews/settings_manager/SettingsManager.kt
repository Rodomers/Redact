package com.rds.mews.settings_manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import com.rds.mews.localcore.SummarizationResult
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.flow.Flow

private val Context.dataStore: DataStore<AppSettings> by dataStore(
    fileName = "app_settings.json",
    serializer = AppSettingsSerializer,
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "settings") { sharedPrefs: SharedPreferencesView, currentData: AppSettings ->
                // МИГРАЦИЯ: Ручной маппинг старых ключей в новый Data Class
                // Если ключа нет в старых настройках, берется значение из currentData (default)

                // Читаем ошибку отдельно
                val errType = sharedPrefs.getString("last_error_type", null)
                val errMsg = sharedPrefs.getString("last_error_message", "")
                val migratedError = if (errType != null) {
                    try {
                        SavedError(
                            type = enumValueOf<SummarizationErrorType>(errType),
                            message = errMsg ?: ""
                        )
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                } else null

                currentData.copy(
                    updatingTitles = sharedPrefs.getBoolean(MewsRepository.UPDATING_TITLES, currentData.updatingTitles),
                    updatingState = sharedPrefs.getString(MewsRepository.UPDATING_STATE, currentData.updatingState) ?: currentData.updatingState,
                    updatingProgress = sharedPrefs.getFloat(MewsRepository.UPDATING_PROGRESS, currentData.updatingProgress),
                    lastTitlesUpdate = sharedPrefs.getLong(MewsRepository.LAST_TITLES_UPDATE, currentData.lastTitlesUpdate),

                    bannedNews = sharedPrefs.getStringSet(MewsRepository.BANNED_NEWS_SET, currentData.bannedNews) ?: currentData.bannedNews,

                    currentTheme = sharedPrefs.getString(MewsRepository.CURRENT_THEME, currentData.currentTheme) ?: currentData.currentTheme,
                    isMonet = sharedPrefs.getBoolean(MewsRepository.IS_MONET, currentData.isMonet),
                    titlesNum = sharedPrefs.getInt(MewsRepository.TITLES_NUM, currentData.titlesNum),
                    titlesPeriod = sharedPrefs.getInt(MewsRepository.TITLES_PERIOD, currentData.titlesPeriod),

                    userApiKey = sharedPrefs.getString(MewsRepository.USER_API_KEY, currentData.userApiKey) ?: currentData.userApiKey,
                    currentLlmModel = sharedPrefs.getString(MewsRepository.CURRENT_LLM_MODEL, currentData.currentLlmModel) ?: currentData.currentLlmModel,

                    showDates = sharedPrefs.getBoolean(MewsRepository.SHOW_DATES, currentData.showDates),
                    rssUpdateInterval = sharedPrefs.getInt(MewsRepository.RSS_UPDATE_INTERVAL, currentData.rssUpdateInterval),
                    lastRssUpdate = sharedPrefs.getLong(MewsRepository.LAST_RSS_UPDATE, currentData.lastRssUpdate),
                    enableProxy = sharedPrefs.getBoolean(MewsRepository.ENABLE_PROXY, currentData.enableProxy),

                    compactTabBar = sharedPrefs.getBoolean(MewsRepository.COMPACT_TAB_BAR, currentData.compactTabBar),
                    filterTopics = sharedPrefs.getBoolean(MewsRepository.FILTER_TOPICS, currentData.filterTopics),
                    innerTimestamps = sharedPrefs.getBoolean(MewsRepository.INNER_TIMESTAMPS, currentData.innerTimestamps),
                    showSnippets = sharedPrefs.getBoolean(MewsRepository.SHOW_SNIPPETS, currentData.showSnippets),

                    titlesAutoUpdate = sharedPrefs.getBoolean(MewsRepository.TITLES_AUTO_UPDATE, currentData.titlesAutoUpdate),
                    titlesAutoUpdateFrequency = sharedPrefs.getInt(MewsRepository.TITLES_AUTO_UPDATE_FREQUENCY, currentData.titlesAutoUpdateFrequency),
                    titlesAlarmTimeMins = sharedPrefs.getInt(MewsRepository.TITLES_ALARM_MINS, currentData.titlesAlarmTimeMins),
                    alarmsAllowed = sharedPrefs.getBoolean(MewsRepository.ALARMS_ALLOWED, currentData.alarmsAllowed),
                    notificationsGranted = sharedPrefs.getBoolean(MewsRepository.NOTIFICATIONS_GRANTED, currentData.notificationsGranted),

                    currentLanguage = sharedPrefs.getString(MewsRepository.CURRENT_LANGUAGE, null), // Будет обработано в repo

                    lastError = migratedError
                )
            }
        )
    }
)

class SettingsManager(private val context: Context) {
    // Основной поток данных.
    // Используем sharingStarted.Eagerly, чтобы настройки загрузились сразу и миграция прошла
    val settings: Flow<AppSettings> = context.dataStore.data

    // --- Generic Update Helper ---
    // Это единственный метод, который реально пишет в диск.
    // Все остальные функции - просто обертки над ним.
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        try {
            context.dataStore.updateData(transform)
        } catch (e: Exception) {
            e.printStackTrace()
            // Можно добавить логирование ошибок записи
        }
    }

    // --- Helpers for Error Object ---
    suspend fun saveLastError(failure: SummarizationResult.Failure) {
        val savedError = SavedError(
            type = failure.type, // Теперь передаем тип напрямую
            message = failure.cause?.message ?: ""
        )
        updateSettings { it.copy(lastError = savedError) }
    }

    suspend fun clearLastError() {
        updateSettings { it.copy(lastError = null) }
    }
}