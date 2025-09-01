package com.rds.mews

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SettingsManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun saveBoolean(key: String, value: Boolean) {
        sharedPreferences.edit { putBoolean(key, value) }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun saveInt(key: String, value: Int) {
        sharedPreferences.edit {putInt(key, value)}
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    fun saveLong(key: String, value: Long) {
        sharedPreferences.edit { putLong(key, value) }
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    fun saveString(key: String, value: String) {
        sharedPreferences.edit { putString(key, value) }
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: "null"
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

class SettingsViewModel(private val settingsManager: SettingsManager): ViewModel() {
    companion object {
        const val IS_DARK_MODE_KEY = "is_dark_mode"
        const val IS_MONET_KEY = "is_monet"
        const val TITLES_NUM_KEY = "titles_num"
        const val TITLES_PERIOD_KEY = "titles_period"
        const val USER_API_KEY = "user_api"
        const val CURRENT_LLM_MODEL = "current_model"
        const val SHOW_DATES = "show_dates"
        const val RSS_UPDATE_INTERVAL = "rss_update_interval"
        const val LAST_RSS_UPDATE = "last_rss_update"
    }

    var isDarkMode = mutableStateOf(settingsManager.getString(IS_DARK_MODE_KEY, "system"))
    var titlesNum = mutableIntStateOf(settingsManager.getInt(TITLES_NUM_KEY, 10))
    var titlesPeriod = mutableIntStateOf(settingsManager.getInt(TITLES_PERIOD_KEY, 24))
    var userApi = mutableStateOf(settingsManager.getString(USER_API_KEY, "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk"))
    var currentLlm = mutableStateOf(settingsManager.getString(CURRENT_LLM_MODEL, ""))
    var showDates = mutableStateOf(settingsManager.getBoolean(SHOW_DATES, false))
    var rssUpdateInterval = mutableIntStateOf(settingsManager.getInt(RSS_UPDATE_INTERVAL, 30))
    var lastRssUpdate = mutableLongStateOf(settingsManager.getLong(LAST_RSS_UPDATE, 0L))

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener {_, key ->
        when (key) {
            IS_DARK_MODE_KEY -> isDarkMode.value = settingsManager.getString(IS_DARK_MODE_KEY, "system")
            TITLES_NUM_KEY -> titlesNum.intValue = settingsManager.getInt(TITLES_NUM_KEY, 10)
            TITLES_PERIOD_KEY -> titlesPeriod.intValue = settingsManager.getInt(TITLES_PERIOD_KEY, 24)
            USER_API_KEY -> userApi.value = settingsManager.getString(USER_API_KEY, "")
            CURRENT_LLM_MODEL -> currentLlm.value = settingsManager.getString(CURRENT_LLM_MODEL, "")
            SHOW_DATES -> showDates.value = settingsManager.getBoolean(SHOW_DATES, false)
            RSS_UPDATE_INTERVAL -> rssUpdateInterval.intValue = settingsManager.getInt(RSS_UPDATE_INTERVAL, 30)
            LAST_RSS_UPDATE -> lastRssUpdate.longValue = settingsManager.getLong(LAST_RSS_UPDATE, 0L)
        }
    }

    init {
        settingsManager.registerListener(listener)
    }

    fun setDarkMode(newValue: String) {
        settingsManager.saveString(IS_DARK_MODE_KEY, newValue)
        isDarkMode.value = newValue
    }

    fun setTitlesNum(newValue: Int) {
        settingsManager.saveInt(TITLES_NUM_KEY, newValue)
        titlesNum.intValue = newValue
    }

    fun setTitlesPeriod(newValue: Int) {
        settingsManager.saveInt(TITLES_PERIOD_KEY, newValue)
        titlesPeriod.intValue = newValue
    }

    fun setUserGeminiApi(newValue: String) {
        settingsManager.saveString(USER_API_KEY, newValue)
        userApi.value = newValue
    }

    fun setCurrentLlm(newValue: String) {
        settingsManager.saveString(CURRENT_LLM_MODEL, newValue)
        currentLlm.value = newValue
    }

    fun setShowDates(newValue: Boolean) {
        settingsManager.saveBoolean(SHOW_DATES, newValue)
        showDates.value = newValue
    }

    fun setRssUpdateInterval(newValue: Int) {
        settingsManager.saveInt(RSS_UPDATE_INTERVAL, newValue)
        rssUpdateInterval.intValue = newValue
    }

    fun setLastRssUpdate(newValue: Long) {
        settingsManager.saveLong(LAST_RSS_UPDATE, newValue)
        lastRssUpdate.longValue = newValue
    }

    override fun onCleared() {
        super.onCleared()
        settingsManager.unregisterListener(listener)
    }
}

class SettingsViewModelFactory(private val settingsManager: SettingsManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}