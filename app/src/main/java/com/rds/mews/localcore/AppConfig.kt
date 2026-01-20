package com.rds.mews.localcore

import com.rds.mews.R
import kotlinx.serialization.Serializable

@Serializable
enum class DarkTheme(val displayedName: Int, val isDark: Boolean?) {
    LIGHT(R.string.settings_light_theme, false),
    DARK(R.string.settings_dark_theme, true),
    SYSTEM(R.string.settings_system_theme, null);

    companion object {
        fun fromName(key: Int): DarkTheme {
            return entries.find { it.displayedName == key } ?: SYSTEM
        }

        fun fromBool(flag: Boolean?): DarkTheme {
            return entries.find { it.isDark == flag } ?: SYSTEM
        }

        fun fromOldString(value: String?): DarkTheme {
            return when (value) {
                "light" -> LIGHT
                "dark" -> DARK
                else -> SYSTEM
            }
        }
    }
}

@Serializable
enum class AppTheme(val themeName: Int, val id: Int) {
    DEFAULT(R.string.settings_theme_classic, 0),
    MATERIAL(R.string.settings_theme_material, 1);

    companion object {
        fun fromId(key: Int): AppTheme {
            return entries.find { it.id == key } ?: DEFAULT
        }
        
        fun fromMonet(isMonet: Boolean): AppTheme {
            return if (isMonet) MATERIAL else DEFAULT
        }
    }
}

@Serializable
enum class HeadersNum(val stringId: Int, val num: Int) {
    NUM_10(R.plurals.titles, 10),
    NUM_20(R.plurals.titles, 20),
    NUM_30(R.plurals.titles, 30),
    NUM_40(R.plurals.titles, 40),
    NUM_50(R.plurals.titles, 50);

    companion object {
        fun fromNum(key: Int): HeadersNum {
            return entries.find { it.num == key } ?: NUM_10
        }
    }
}

@Serializable
enum class TitlesPeriod(val stringId: Int, val num: Int?) {
    ADAPTIVE(R.string.adaptive, null),
    HRS_12(R.plurals.hours, 12),
    HRS_24(R.plurals.hours, 24),
    HRS_48(R.plurals.hours, 48),
    HRS_72(R.plurals.hours, 72),
    HRS_96(R.plurals.hours, 96),
    HRS_120(R.plurals.hours, 120);

    companion object {
        fun fromNum(key: Int?): TitlesPeriod {
            return entries.find { it.num == key } ?: ADAPTIVE
        }
    }
}

@Serializable
enum class AutoUpdateFrequency(val stringId: Int, val num: Int) {
    FREQ_12(R.plurals.hours, 12),
    FREQ_24(R.plurals.hours, 24),
    FREQ_48(R.plurals.hours, 48),
    FREQ_72(R.plurals.hours, 72),
    FREQ_96(R.plurals.hours, 96),
    FREQ_120(R.plurals.hours, 120);

    companion object {
        fun fromNum(key: Int): AutoUpdateFrequency {
            return entries.find { it.num == key } ?: FREQ_24
        }
    }
}

@Serializable
enum class GeminiModelOption(val displayedName: String, val apiModelName: String) {
    FLASH_LITE_2_5("2.5 Flash Lite", "gemini-2.5-flash-lite"),
    FLASH_2_5("2.5 Flash", "gemini-2.5-flash"),
    PRO_2_5("2.5 Pro", "gemini-2.5-pro"),
    FLASH_3_PREVIEW("3.0 Flash Preview", "gemini-3-flash-preview"),
    PRO_3_PREVIEW("3.0 Pro Preview", "gemini-3-pro-preview"),
    FLASH_LITE_LATEST("Flash Lite Latest", "gemini-flash-lite_latest"),
    FLASH_LATEST("Flash Latest", "gemini-flash-latest"),
    PRO_LATEST("Pro Latest", "gemini-pro-latest");

    companion object {
        fun fromKey(key: String): GeminiModelOption {
            return entries.find { it.apiModelName == key } ?: FLASH_LITE_LATEST
        }
    }
}