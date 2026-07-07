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
import com.rds.mews.localcore.AppTheme
import com.rds.mews.localcore.DarkTheme

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

// --- 1. Slate Navy ---
val SlateLightScheme = lightColorScheme(
    background = SlateLightBg, surface = SlateLightBg,
    surfaceContainerLow = SlateLightFloat,
    secondaryContainer = SlateLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = SlateLightContainer, onPrimary = TextBlack
)

val SlateDarkScheme = darkColorScheme(
    background = SlateDarkBg, surface = SlateDarkBg,
    surfaceContainerLow = SlateDarkFloat,
    secondaryContainer = SlateDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = SlateDarkContainer, onPrimary = TextWhite
)

// --- 2. Pistachio ---
val PistachioLightScheme = lightColorScheme(
    background = PistachioLightBg, surface = PistachioLightBg,
    surfaceContainerLow = PistachioLightFloat,
    secondaryContainer = PistachioLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = PistachioLightContainer, onPrimary = TextBlack
)

val PistachioDarkScheme = darkColorScheme(
    background = PistachioDarkBg, surface = PistachioDarkBg,
    surfaceContainerLow = PistachioDarkFloat,
    secondaryContainer = PistachioDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = PistachioDarkContainer, onPrimary = TextWhite
)

// --- 3. Editorial (Swiss) ---
val SwissLightScheme = lightColorScheme(
    background = SwissLightBg, surface = SwissLightBg,
    surfaceContainerLow = SwissLightFloat,
    secondaryContainer = SwissLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = SwissLightContainer, onPrimary = TextBlack
)

val SwissDarkScheme = darkColorScheme(
    background = SwissDarkBg, surface = SwissDarkBg,
    surfaceContainerLow = SwissDarkFloat,
    secondaryContainer = SwissDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = SwissDarkContainer, onPrimary = TextWhite
)

// --- 4. Concrete (Industrial) ---
val ConcreteLightScheme = lightColorScheme(
    background = ConcreteLightBg, surface = ConcreteLightBg,
    surfaceContainerLow = ConcreteLightFloat,
    secondaryContainer = ConcreteLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = ConcreteLightContainer, onPrimary = TextBlack
)

val ConcreteDarkScheme = darkColorScheme(
    background = ConcreteDarkBg, surface = ConcreteDarkBg,
    surfaceContainerLow = ConcreteDarkFloat,
    secondaryContainer = ConcreteDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = ConcreteDarkContainer, onPrimary = TextWhite
)

// --- 5. Paper ---
val PaperLightScheme = lightColorScheme(
    background = PaperLightBg, surface = PaperLightBg,
    surfaceContainerLow = PaperLightFloat,
    secondaryContainer = PaperLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = PaperLightContainer, onPrimary = TextBlack
)

val PaperDarkScheme = darkColorScheme(
    background = PaperDarkBg, surface = PaperDarkBg,
    surfaceContainerLow = PaperDarkFloat,
    secondaryContainer = PaperDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = PaperDarkContainer, onPrimary = TextWhite
)

// --- 6. Storm ---
val StormLightScheme = lightColorScheme(
    background = StormLightBg, surface = StormLightBg,
    surfaceContainerLow = StormLightFloat,
    secondaryContainer = StormLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = StormLightContainer, onPrimary = TextBlack
)

val StormDarkScheme = darkColorScheme(
    background = StormDarkBg, surface = StormDarkBg,
    surfaceContainerLow = StormDarkFloat,
    secondaryContainer = StormDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = StormDarkContainer, onPrimary = TextWhite
)

// --- 7. Coffee ---
val CoffeeLightScheme = lightColorScheme(
    background = CoffeeLightBg, surface = CoffeeLightBg,
    surfaceContainerLow = CoffeeLightFloat,
    secondaryContainer = CoffeeLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = CoffeeLightContainer, onPrimary = TextBlack
)

val CoffeeDarkScheme = darkColorScheme(
    background = CoffeeDarkBg, surface = CoffeeDarkBg,
    surfaceContainerLow = CoffeeDarkFloat,
    secondaryContainer = CoffeeDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = CoffeeDarkContainer, onPrimary = TextWhite
)

// --- 8. Violet ---
val VioletLightScheme = lightColorScheme(
    background = VioletLightBg, surface = VioletLightBg,
    surfaceContainerLow = VioletLightFloat,
    secondaryContainer = VioletLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = VioletLightContainer, onPrimary = TextBlack
)

val VioletDarkScheme = darkColorScheme(
    background = VioletDarkBg, surface = VioletDarkBg,
    surfaceContainerLow = VioletDarkFloat,
    secondaryContainer = VioletDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = VioletDarkContainer, onPrimary = TextWhite
)

// --- 10. Peach Scheme ---
val PeachLightScheme = lightColorScheme(
    background = PeachLightBg, surface = PeachLightBg,
    surfaceContainerLow = PeachLightFloat,
    secondaryContainer = PeachLightContainer,
    onSecondaryContainer = TextBlack,
    onSurface = TextBlack, onBackground = TextBlack,
    primary = PeachLightContainer, onPrimary = TextBlack
)

val PeachDarkScheme = darkColorScheme(
    background = PeachDarkBg, surface = PeachDarkBg,
    surfaceContainerLow = PeachDarkFloat,
    secondaryContainer = PeachDarkContainer,
    onSecondaryContainer = TextWhite,
    onSurface = TextWhite, onBackground = TextWhite,
    primary = PeachDarkContainer, onPrimary = TextWhite
)

// --- 11. International Scheme ---
val InterLightScheme = lightColorScheme(
    background = InterLightBg, surface = InterLightBg,
    surfaceContainerLow = InterLightFloat,

    secondaryContainer = InterLightContainer,
    onSecondaryContainer = InterLightContent,

    onSurface = InterLightText,
    onBackground = InterLightText,

    primary = InterLightContainer, onPrimary = InterLightText
)

val InterDarkScheme = darkColorScheme(
    background = InterDarkBg, surface = InterDarkBg,
    surfaceContainerLow = InterDarkFloat,

    secondaryContainer = InterDarkContainer,
    onSecondaryContainer = InterDarkContent,

    onSurface = InterDarkText,
    onBackground = InterDarkText,

    primary = InterDarkContainer, onPrimary = InterDarkText
)

val typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun MewsTheme(
    systemDarkTheme: Boolean = isSystemInDarkTheme(),
    settingsTheme: DarkTheme,
    appTheme: AppTheme,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val useDarkTheme = when (settingsTheme) {
        DarkTheme.LIGHT -> false
        DarkTheme.DARK -> true
        else -> systemDarkTheme
    }

    val useDynamicColor = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) &&
            dynamicColor &&
            appTheme == AppTheme.MATERIAL

    val colorScheme = when {
        useDynamicColor -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> when (appTheme) {
            AppTheme.DEFAULT, AppTheme.MATERIAL -> if (useDarkTheme) DarkColorScheme else LightColorScheme

            AppTheme.SLATE -> if (useDarkTheme) SlateDarkScheme else SlateLightScheme
            AppTheme.PISTACHIO -> if (useDarkTheme) PistachioDarkScheme else PistachioLightScheme
            AppTheme.SWISS -> if (useDarkTheme) SwissDarkScheme else SwissLightScheme
            AppTheme.INDUSTRIAL -> if (useDarkTheme) ConcreteDarkScheme else ConcreteLightScheme

            AppTheme.PAPER -> if (useDarkTheme) PaperDarkScheme else PaperLightScheme

            AppTheme.STORM -> if (useDarkTheme) StormDarkScheme else StormLightScheme
            AppTheme.COFFEE -> if (useDarkTheme) CoffeeDarkScheme else CoffeeLightScheme
            AppTheme.VIOLET -> if (useDarkTheme) VioletDarkScheme else VioletLightScheme
            AppTheme.PEACH -> if (useDarkTheme) PeachDarkScheme else PeachLightScheme
            AppTheme.INTERNATIONAL -> if (useDarkTheme) InterDarkScheme else InterLightScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val insetsController = WindowCompat.getInsetsController(window, view)

            insetsController.isAppearanceLightStatusBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}