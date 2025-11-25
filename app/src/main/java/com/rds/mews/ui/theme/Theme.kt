package com.rds.mews.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    secondaryContainer = Color.LightGray,
    onSecondary = NearlyWhiteGray,
    background = Color.White,
    surface = Color.White,
    onSecondaryContainer = Color.Black,
    onPrimary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceContainerLow = WhiteGray
)

private val DarkColorScheme = darkColorScheme(
    secondaryContainer = LighterGray, // Светлый серый для выделения
    onSecondary = Color.DarkGray, // Средний серый для второстепенных элементов
    background = DarkGray, // Тёмный фон
    onSecondaryContainer = Color.White,
    surface = DarkGray, // Тёмная поверхность
    onPrimary = Color.White, // Белый текст на `primary` фоне
    onBackground = Color.White, // Белый текст на `background` фоне
    onSurface = Color.White, // Белый текст на `surface` фоне,
    surfaceContainerLow = MediumGray
)

// Добавленный объект Typography, который отсутствовал
val typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* При необходимости здесь можно переопределить и другие стили текста,
       такие как titleLarge, bodyMedium и т.д. */
)

@Composable
fun MewsTheme(
    systemDarkTheme: Boolean = isSystemInDarkTheme(),
    settingsTheme: String,
    monetTheme: Boolean,
    // Динамические цвета доступны на Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val turnDynamicColor = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) && monetTheme
    val context = LocalContext.current

    val colorScheme = when (settingsTheme) {
        "light" -> if (turnDynamicColor) dynamicLightColorScheme(context) else LightColorScheme
        "dark" -> if (turnDynamicColor) dynamicDarkColorScheme(context) else DarkColorScheme
        else -> when {
            turnDynamicColor -> if (systemDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> if (systemDarkTheme) DarkColorScheme else LightColorScheme
        }
    }
//    val colorScheme = when {
//        // Если динамический цвет включен и поддерживается, используем его
//        dynamicColor && supportsDynamicColor -> {
//            if (systemDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }
//        // В противном случае используем вашу логику выбора темы
//        settingsTheme == "dark" -> DarkColorScheme
//        settingsTheme == "light" -> LightColorScheme
//        else -> if (systemDarkTheme) DarkColorScheme else LightColorScheme
//    }

    // Добавлен SideEffect для управления цветом и иконками системной строки состояния
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Устанавливаем цвет строки состояния в соответствии с фоном темы
            window.statusBarColor = Color.Transparent.toArgb()

            // Указываем, что контент будет рисоваться под системными панелями
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // Устанавливаем цвет иконок в строке состояния
            val insetsController = WindowCompat.getInsetsController(window, view)
            when (settingsTheme) {
                "dark" -> insetsController.isAppearanceLightStatusBars = false
                "light" -> insetsController.isAppearanceLightStatusBars = true
                else -> insetsController.isAppearanceLightStatusBars = !systemDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}