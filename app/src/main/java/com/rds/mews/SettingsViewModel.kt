package com.rds.mews

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
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
    }

    var isDarkMode = mutableStateOf(settingsManager.getBoolean(IS_DARK_MODE_KEY, false))
    var titlesNum = mutableIntStateOf(settingsManager.getInt(TITLES_NUM_KEY, 10))
    var titlesPeriod = mutableIntStateOf(settingsManager.getInt(TITLES_PERIOD_KEY, 24))

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener {_, key ->
        when (key) {
            IS_DARK_MODE_KEY -> isDarkMode.value = settingsManager.getBoolean(IS_DARK_MODE_KEY, false)
            TITLES_NUM_KEY -> titlesNum.intValue = settingsManager.getInt(TITLES_NUM_KEY, 10)
            TITLES_PERIOD_KEY -> titlesPeriod.intValue = settingsManager.getInt(TITLES_PERIOD_KEY, 24)
        }
    }

    init {
        settingsManager.registerListener(listener)
    }

    fun toggleDarkMode(newValue: Boolean) {
        settingsManager.saveBoolean(IS_DARK_MODE_KEY, newValue)
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