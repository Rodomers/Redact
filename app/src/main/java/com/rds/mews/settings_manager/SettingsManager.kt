package com.rds.mews.settings_manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import com.rds.mews.localcore.*
import com.rds.mews.repositories.MewsRepository
import kotlinx.coroutines.flow.Flow

private val Context.dataStore: DataStore<AppSettings> by dataStore(
    fileName = "app_settings.json",
    serializer = AppSettingsSerializer,
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "settings") { sharedPrefs: SharedPreferencesView, currentData: AppSettings ->

                val errType = sharedPrefs.getString("last_error_type", null)
                val errMsg = sharedPrefs.getString("last_error_message", "")
                val migratedError = if (errType != null) {
                    try {
                        SavedError(enumValueOf<SummarizationErrorType>(errType), errMsg ?: "")
                    } catch (e: Exception) { null }
                } else null

                val oldThemeStr = sharedPrefs.getString(MewsRepository.CURRENT_THEME, "system")
                val migratedDarkTheme = DarkTheme.fromOldString(oldThemeStr)

                val oldIsMonet = sharedPrefs.getBoolean(MewsRepository.IS_MONET, false)
                val migratedAppTheme = AppTheme.fromMonet(oldIsMonet)

                val oldTitlesNum = sharedPrefs.getInt(MewsRepository.TITLES_NUM, 10)
                val migratedTitlesNum = HeadersNum.fromNum(oldTitlesNum)

                val oldPeriod = sharedPrefs.getInt(MewsRepository.TITLES_PERIOD, 24)
                val migratedPeriod = TitlesPeriod.fromNum(oldPeriod)

                val oldFreq = sharedPrefs.getInt(MewsRepository.TITLES_AUTO_UPDATE_FREQUENCY, 24)
                val migratedFreq = AutoUpdateFrequency.fromNum(oldFreq)

                val oldModelKey = sharedPrefs.getString(MewsRepository.CURRENT_LLM_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"
                val migratedModel = GeminiModelOption.fromKey(oldModelKey)

                currentData.copy(
                    darkTheme = migratedDarkTheme,
                    appTheme = migratedAppTheme,
                    titlesNum = migratedTitlesNum,
                    titlesPeriod = migratedPeriod,
                    titlesAutoUpdateFrequency = migratedFreq,
                    llmModel = migratedModel,

                    updatingTitles = sharedPrefs.getBoolean(MewsRepository.UPDATING_TITLES, currentData.updatingTitles),
                    updatingState = sharedPrefs.getString(MewsRepository.UPDATING_STATE, currentData.updatingState) ?: currentData.updatingState,
                    updatingProgress = sharedPrefs.getFloat(MewsRepository.UPDATING_PROGRESS, currentData.updatingProgress),
                    lastTitlesUpdate = sharedPrefs.getLong(MewsRepository.LAST_TITLES_UPDATE, currentData.lastTitlesUpdate),
                    bannedNews = sharedPrefs.getStringSet(MewsRepository.BANNED_NEWS_SET, currentData.bannedNews) ?: currentData.bannedNews,
                    userApiKey = sharedPrefs.getString(MewsRepository.USER_API_KEY, currentData.userApiKey) ?: currentData.userApiKey,
                    showDates = sharedPrefs.getBoolean(MewsRepository.SHOW_DATES, currentData.showDates),
                    rssUpdateInterval = sharedPrefs.getInt(MewsRepository.RSS_UPDATE_INTERVAL, currentData.rssUpdateInterval),
                    lastRssUpdate = sharedPrefs.getLong(MewsRepository.LAST_RSS_UPDATE, currentData.lastRssUpdate),
                    enableProxy = sharedPrefs.getBoolean(MewsRepository.ENABLE_PROXY, currentData.enableProxy),
                    compactTabBar = sharedPrefs.getBoolean(MewsRepository.COMPACT_TAB_BAR, currentData.compactTabBar),
                    filterTopics = sharedPrefs.getBoolean(MewsRepository.FILTER_TOPICS, currentData.filterTopics),
                    innerTimestamps = sharedPrefs.getBoolean(MewsRepository.INNER_TIMESTAMPS, currentData.innerTimestamps),
                    showSnippets = sharedPrefs.getBoolean(MewsRepository.SHOW_SNIPPETS, currentData.showSnippets),
                    titlesAutoUpdate = sharedPrefs.getBoolean(MewsRepository.TITLES_AUTO_UPDATE, currentData.titlesAutoUpdate),
                    titlesAlarmTimeMins = sharedPrefs.getInt(MewsRepository.TITLES_ALARM_MINS, currentData.titlesAlarmTimeMins),
                    alarmsAllowed = sharedPrefs.getBoolean(MewsRepository.ALARMS_ALLOWED, currentData.alarmsAllowed),
                    notificationsGranted = sharedPrefs.getBoolean(MewsRepository.NOTIFICATIONS_GRANTED, currentData.notificationsGranted),
                    currentLanguage = sharedPrefs.getString(MewsRepository.CURRENT_LANGUAGE, null) ?: "english",

                    lastError = migratedError
                )
            }
        )
    }
)

class SettingsManager(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        try {
            context.dataStore.updateData(transform)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveLastError(failure: SummarizationResult.Failure) {
        val savedError = SavedError(
            type = failure.type,
            message = failure.cause?.message ?: ""
        )
        updateSettings { it.copy(lastError = savedError) }
    }

    suspend fun clearLastError() {
        updateSettings { it.copy(lastError = null) }
    }
}