package com.jlptstudy.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ui-design.md 3.1. 동적 색은 쓰지 않는다.
val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F4A7A), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF), onPrimaryContainer = Color(0xFF0F1E3D),
    inversePrimary = Color(0xFFADC6FF),
    secondary = Color(0xFF5A5F71), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE2F5), onSecondaryContainer = Color(0xFF171B2C),
    tertiary = Color(0xFF1E6B60), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC3EDE3), onTertiaryContainer = Color(0xFF00201B),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF9F4), onBackground = Color(0xFF1B1C1F),
    surface = Color(0xFFFBF9F4), onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFE3E1DC), onSurfaceVariant = Color(0xFF46474F),
    surfaceTint = Color(0xFF2F4A7A),
    inverseSurface = Color(0xFF303034), inverseOnSurface = Color(0xFFF2F0F4),
    outline = Color(0xFF76777F), outlineVariant = Color(0xFFC6C6CF), scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF9F4), surfaceDim = Color(0xFFDBD9D4),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF3F1EC),
    surfaceContainer = Color(0xFFEFEDE8), surfaceContainerHigh = Color(0xFFE9E7E2),
    surfaceContainerHighest = Color(0xFFE3E1DC),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFADC6FF), onPrimary = Color(0xFF102F5E),
    primaryContainer = Color(0xFF2C4675), onPrimaryContainer = Color(0xFFD8E2FF),
    inversePrimary = Color(0xFF2F4A7A),
    secondary = Color(0xFFC2C6D9), onSecondary = Color(0xFF2C3042),
    secondaryContainer = Color(0xFF424659), onSecondaryContainer = Color(0xFFDEE2F5),
    tertiary = Color(0xFF8AD3C4), onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF005046), onTertiaryContainer = Color(0xFFC3EDE3),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131316), onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF131316), onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF353438), onSurfaceVariant = Color(0xFFC6C6CF),
    surfaceTint = Color(0xFFADC6FF),
    inverseSurface = Color(0xFFE4E2E6), inverseOnSurface = Color(0xFF303034),
    outline = Color(0xFF90909A), outlineVariant = Color(0xFF46474F), scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF39393C), surfaceDim = Color(0xFF131316),
    surfaceContainerLowest = Color(0xFF0E0E11), surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF1F1F23), surfaceContainerHigh = Color(0xFF2A2A2D),
    surfaceContainerHighest = Color(0xFF353438),
)

/** M3 역할에 없는 정답 표시 색(3.1.3). */
@Immutable
data class ExtendedColors(val correct: Color, val correctContainer: Color, val onCorrectContainer: Color)

val LightExtendedColors = ExtendedColors(Color(0xFF1B6B3A), Color(0xFFD3F0DC), Color(0xFF0B2E17))
val DarkExtendedColors = ExtendedColors(Color(0xFF8AD6A0), Color(0xFF0F5129), Color(0xFFD3F0DC))

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
