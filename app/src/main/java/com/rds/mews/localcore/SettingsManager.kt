package com.rds.mews.localcore

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.rds.mews.repositories.MewsRepository
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

    val bannedNewsFlow: Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener {prefs, key ->
            if (key == MewsRepository.BANNED_NEWS_SET) {
                trySend(prefs.getStringSet(key, setOf("")) ?: setOf(""))
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(getStringSet(MewsRepository.BANNED_NEWS_SET, setOf("")))

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

    fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        return sharedPreferences.getStringSet(key, defaultValue) ?: setOf("")
    }

    fun saveStringSet(key: String, defaultValue: Set<String>) {
        sharedPreferences.edit { putStringSet(key, defaultValue) }
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