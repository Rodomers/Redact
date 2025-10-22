package com.rds.mews

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

class SettingsManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val updatingTitlesFlow: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == MewsRepository.UPDATING_TITLES) {
                trySend(prefs.getBoolean(key, false))
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        trySend(getBoolean(MewsRepository.UPDATING_TITLES, false))

        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val updatingTitlesStateFlow: Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener {prefs, key ->
            if (key == MewsRepository.UPDATING_STATE) {
                trySend(prefs.getString(key, "off"))
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(getString(MewsRepository.UPDATING_STATE, "off"))

        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val lastTitlesUpdateFlow: Flow<Long> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener {prefs, key ->
            if (key == MewsRepository.LAST_TITLES_UPDATE) {
                trySend(prefs.getLong(key, 0L))
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(getLong(MewsRepository.LAST_TITLES_UPDATE, 0L))

        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private companion object {
        const val KEY_LAST_ERROR_TYPE = "last_error_type"
        const val KEY_LAST_ERROR_MESSAGE = "last_error_message"
    }

    suspend fun awaitTitlesUpdate() {
        updatingTitlesFlow.first() { !it }
    }

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

    @SuppressLint("CommitPrefEdits")
    fun saveLastError(failure: SummarizationResult.Failure) {
        sharedPreferences.edit().apply {
            putString(KEY_LAST_ERROR_TYPE, failure.type.name)
            putString(KEY_LAST_ERROR_MESSAGE, failure.cause?.message ?: "")
            apply()
        }
    }

    @SuppressLint("CommitPrefEdits")
    fun clearLastError() {
        sharedPreferences.edit().apply {
            remove(KEY_LAST_ERROR_TYPE)
            remove(KEY_LAST_ERROR_MESSAGE)
            apply()
        }
    }

    fun getLastError(): SummarizationResult.Failure? {
        val errorTypeName = sharedPreferences.getString(KEY_LAST_ERROR_TYPE, null) ?: return null

        return try {
            val errType = enumValueOf<SummarizationErrorType>(errorTypeName)
            val errMess = sharedPreferences.getString(KEY_LAST_ERROR_MESSAGE, "")

            SummarizationResult.Failure(errType, cause = Exception(errMess))
        } catch(e: IllegalArgumentException) {
            null
        }
    }

    val lastErrorFlow: Flow<SummarizationResult.Failure?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener {sharedPreferences, key ->
            if (key == KEY_LAST_ERROR_TYPE) trySend(getLastError())
        }

        trySend(getLastError())
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

//class SettingsViewModel(private val settingsManager: SettingsManager): ViewModel() {
//    companion object {
//        const val IS_DARK_MODE_KEY = "is_dark_mode"
//        const val IS_MONET_KEY = "is_monet"
//        const val TITLES_NUM_KEY = "titles_num"
//        const val TITLES_PERIOD_KEY = "titles_period"
//        const val USER_API_KEY = "user_api"
//        const val CURRENT_LLM_MODEL = "current_model"
//        const val SHOW_DATES = "show_dates"
//        const val RSS_UPDATE_INTERVAL = "rss_update_interval"
//        const val LAST_RSS_UPDATE = "last_rss_update"
//        const val LAST_TITLES_UPDATE = "last_titles_update"
//        const val UPDATING_TITLES = "updating_titles"
//        const val UPDATING_STATE = "updating_state"
//        const val COMPACT_TAB_BAR = "compact_tab_bar"
//        const val FILTER_TOPICS = "filter_topics"
//    }
//
//    var isDarkMode = mutableStateOf(settingsManager.getString(IS_DARK_MODE_KEY, "system"))
//    var isMonetColors = mutableStateOf(settingsManager.getBoolean(IS_MONET_KEY, false))
//    var titlesNum = mutableIntStateOf(settingsManager.getInt(TITLES_NUM_KEY, 10))
//    var titlesPeriod = mutableIntStateOf(settingsManager.getInt(TITLES_PERIOD_KEY, 24))
//    var userApi = mutableStateOf(settingsManager.getString(USER_API_KEY, "AIzaSyCNNpbcjd8lMRMtD6naikNMaRxnG-0HHkk"))
//    var currentLlm = mutableStateOf(settingsManager.getString(CURRENT_LLM_MODEL, ""))
//    var showDates = mutableStateOf(settingsManager.getBoolean(SHOW_DATES, false))
//    var rssUpdateInterval = mutableIntStateOf(settingsManager.getInt(RSS_UPDATE_INTERVAL, 15))
//    var lastRssUpdate = mutableLongStateOf(settingsManager.getLong(LAST_RSS_UPDATE, 0L))
//    var lastTitlesUpdate = mutableLongStateOf(settingsManager.getLong(LAST_TITLES_UPDATE, 0L))
//    var updatingTitles = mutableStateOf(settingsManager.getBoolean(UPDATING_TITLES, false))
//    val currentUpdatingState = mutableStateOf(settingsManager.getString(UPDATING_STATE, "off"))
//    val compactTabBar = mutableStateOf(settingsManager.getBoolean(COMPACT_TAB_BAR, false))
//    private val _lastError = MutableStateFlow<SummarizationResult.Failure?>(null)
//    val lastError: StateFlow<SummarizationResult.Failure?> = _lastError.asStateFlow()
//    val filterTopics = mutableStateOf(settingsManager.getBoolean(FILTER_TOPICS, false))
//
//    private val listener = SharedPreferences.OnSharedPreferenceChangeListener {_, key ->
//        when (key) {
//            IS_DARK_MODE_KEY -> isDarkMode.value = settingsManager.getString(IS_DARK_MODE_KEY, "system")
//            IS_MONET_KEY -> isMonetColors.value = settingsManager.getBoolean(IS_MONET_KEY, false)
//            TITLES_NUM_KEY -> titlesNum.intValue = settingsManager.getInt(TITLES_NUM_KEY, 10)
//            TITLES_PERIOD_KEY -> titlesPeriod.intValue = settingsManager.getInt(TITLES_PERIOD_KEY, 24)
//            USER_API_KEY -> userApi.value = settingsManager.getString(USER_API_KEY, "")
//            CURRENT_LLM_MODEL -> currentLlm.value = settingsManager.getString(CURRENT_LLM_MODEL, "gemini-2.0-flash")
//            SHOW_DATES -> showDates.value = settingsManager.getBoolean(SHOW_DATES, false)
//            RSS_UPDATE_INTERVAL -> rssUpdateInterval.intValue = settingsManager.getInt(RSS_UPDATE_INTERVAL, 15)
//            LAST_RSS_UPDATE -> lastRssUpdate.longValue = settingsManager.getLong(LAST_RSS_UPDATE, 0L)
//            LAST_TITLES_UPDATE -> lastTitlesUpdate.longValue = settingsManager.getLong(LAST_TITLES_UPDATE, 0L)
//            UPDATING_TITLES -> updatingTitles.value = settingsManager.getBoolean(UPDATING_TITLES, false)
//            UPDATING_STATE -> currentUpdatingState.value = settingsManager.getString(UPDATING_STATE, "off")
//            COMPACT_TAB_BAR -> compactTabBar.value = settingsManager.getBoolean(COMPACT_TAB_BAR, false)
//            FILTER_TOPICS -> filterTopics.value = settingsManager.getBoolean(FILTER_TOPICS, false)
//        }
//    }
//
//    init {
//        settingsManager.registerListener(listener)
//        listenForErrors()
//    }
//
//    fun setDarkMode(newValue: String) {
//        settingsManager.saveString(IS_DARK_MODE_KEY, newValue)
//        isDarkMode.value = newValue
//    }
//
//    fun setMonetColors(newValue: Boolean) {
//        settingsManager.saveBoolean(IS_MONET_KEY, newValue)
//        isMonetColors.value = newValue
//    }
//
//    fun setTitlesNum(newValue: Int) {
//        settingsManager.saveInt(TITLES_NUM_KEY, newValue)
//        titlesNum.intValue = newValue
//    }
//
//    fun setTitlesPeriod(newValue: Int) {
//        settingsManager.saveInt(TITLES_PERIOD_KEY, newValue)
//        titlesPeriod.intValue = newValue
//    }
//
//    fun setUserGeminiApi(newValue: String) {
//        settingsManager.saveString(USER_API_KEY, newValue)
//        userApi.value = newValue
//    }
//
//    fun setCurrentLlm(newValue: String) {
//        settingsManager.saveString(CURRENT_LLM_MODEL, newValue)
//        currentLlm.value = newValue
//    }
//
//    fun setShowDates(newValue: Boolean) {
//        settingsManager.saveBoolean(SHOW_DATES, newValue)
//        showDates.value = newValue
//    }
//
//    fun setRssUpdateInterval(newValue: Int) {
//        settingsManager.saveInt(RSS_UPDATE_INTERVAL, newValue)
//        rssUpdateInterval.intValue = newValue
//    }
//
//    fun setLastRssUpdate(newValue: Long) {
//        settingsManager.saveLong(LAST_RSS_UPDATE, newValue)
//        lastRssUpdate.longValue = newValue
//    }
//
//    fun setLastTitlesUpdate(newValue: Long) {
//        settingsManager.saveLong(LAST_TITLES_UPDATE, newValue)
//        lastTitlesUpdate.longValue = newValue
//    }
//
//    fun setUpdatingTitles(newValue: Boolean) {
//        settingsManager.saveBoolean(UPDATING_TITLES, newValue)
//        updatingTitles.value = newValue
//    }
//
//    fun setUpdatingState(newValue: String) {
//        settingsManager.saveString(UPDATING_STATE, newValue)
//        currentUpdatingState.value = newValue
//    }
//
//    fun setCompactTab(newValue: Boolean) {
//        settingsManager.saveBoolean(COMPACT_TAB_BAR, newValue)
//        compactTabBar.value = newValue
//    }
//
//    fun setFilterTopics(newValue: Boolean) {
//        settingsManager.saveBoolean(FILTER_TOPICS, newValue)
//        filterTopics.value = newValue
//    }
//
//    override fun onCleared() {
//        super.onCleared()
//        settingsManager.unregisterListener(listener)
//    }
//
//    private fun checkForSavedError() {
//        _lastError.value = settingsManager.getLastError()
//    }
//
//    fun clearError() {
//        _lastError.value = null
//        settingsManager.clearLastError()
//    }
//
//    private fun listenForErrors() {
//        viewModelScope.launch { settingsManager.lastErrorFlow.collect { error -> _lastError.value = error } }
//    }
//}
//
//class SettingsViewModelFactory(private val settingsManager: SettingsManager) : ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return SettingsViewModel(settingsManager) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}