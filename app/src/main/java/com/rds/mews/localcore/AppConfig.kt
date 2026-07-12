package com.rds.mews.localcore

import com.rds.mews.R
import com.rds.mews.localcore.TitlesPeriod.ADAPTIVE
import kotlinx.serialization.Serializable

@Serializable
enum class DarkTheme(val displayedName: Int, val isDark: Boolean?) {
    SYSTEM(R.string.settings_system_theme, null),
    LIGHT(R.string.settings_light_theme, false),
    DARK(R.string.settings_dark_theme, true);

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
    MATERIAL(R.string.settings_theme_material, 1),

    SLATE(R.string.settings_theme_slate, 2),
    PISTACHIO(R.string.settings_theme_pistachio, 3),
    SWISS(R.string.settings_theme_swiss, 4),
    INDUSTRIAL(R.string.settings_theme_industrial, 5),
    PAPER(R.string.settings_theme_paper, 6),
    STORM(R.string.settings_theme_storm, 7),
    COFFEE(R.string.settings_theme_coffee, 8),
    VIOLET(R.string.settings_theme_violet, 9),
    PEACH(R.string.settings_theme_peach, 10),
    INTERNATIONAL(R.string.settings_theme_international, 11);

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
    NUM_5(R.plurals.titles, 5),
    NUM_10(R.plurals.titles, 10),
    NUM_15(R.plurals.titles, 15),
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
enum class TitlesKeeping(val stringId: Int, val num: Int, val ms: Long) {
    DAYS_1(R.plurals.days, 1, 24*3600*1000),
    DAYS_2(R.plurals.days, 2, 48*3600*1000),
    DAYS_3(R.plurals.days, 3, 72*3600*1000),
    DAYS_4(R.plurals.days, 4, 96*3600*1000),
    DAYS_5(R.plurals.days, 5, 120*3600*1000),
    DAYS_6(R.plurals.days, 6, 144*3600*1000),
    DAYS_7(R.plurals.days, 7, 168*3600*1000);

    companion object {
        fun fromNum(key: Int?): TitlesPeriod {
            return TitlesPeriod.entries.find { it.num == key } ?: ADAPTIVE
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
    FLASH_LITE_3_0("3.1 Flash Lite Preview", "gemini-3.1-flash-lite-preview"),
    PRO_3_PREVIEW("3.1 Pro Preview", "gemini-3.1-pro-preview"),
    FLASH_3_5("3.5 Flash", "gemini-3.5-flash"),
    FLASH_LITE_LATEST("Flash Lite Latest", "gemini-flash-lite-latest"),
    FLASH_LATEST("Flash Latest", "gemini-flash-latest"),
    PRO_LATEST("Pro Latest", "gemini-pro-latest");

    companion object {
        fun fromKey(key: String): GeminiModelOption {
            return entries.find { it.apiModelName == key } ?: FLASH_LITE_LATEST
        }
    }
}

@Serializable
enum class TitleStatus(val statusId: Int) {
    DEFAULT(0),
    PROCESSING(1),
    ERROR(2),
    ARCHIVED(3);

    companion object {
        fun fromId(key: Int): TitleStatus {
            return entries.find { it.statusId == key } ?: DEFAULT
        }
    }
}

@Serializable
enum class TitleSorting(val stringId: Int, val id: Int) {
    OLDEST(R.string.settings_titles_sorting_oldest, 0),
    NEWEST(R.string.settings_titles_sorting_newest, 1);

    companion object {
        fun fromId(key: Int): TitleSorting {
            return entries.find { it.id == key } ?: OLDEST
        }
    }
}

@Serializable
enum class SourceType(val id: Int) {
    RSS(0),
    TELEGRAM(1);

    companion object {
        fun fromId(key: Int): SourceType {
            return entries.find { it.id == key } ?: RSS
        }
    }
}

@Serializable
enum class UpdatingState(val stringId: Int, val id: Int) {
    DEFAULT(R.string.update, 0),
    UPDATING(R.string.updating, 1),
    PARSING(R.string.parsing, 2),
    EXTRACTING(R.string.extracting_topics, 3),
    FILTERING(R.string.filtering_topics, 4),
    SUMMARIZING(R.string.summarizing, 5);

    companion object {
        fun fromId(key: Int): UpdatingState {
            return entries.find { it.id == key } ?: DEFAULT
        }
    }
}