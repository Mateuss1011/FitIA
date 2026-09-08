package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.data.repository.AppThemeMode

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonLime,
    secondary = ElectricBlue,
    background = VoidBlack,
    surface = CharcoalDark,
    onBackground = LightGrey,
    onSurface = LightGrey,
    onPrimary = VoidBlack,
    error = WarningRed,
    surfaceVariant = CharcoalLighter,
    outline = OutlinedBorder
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NeonLime,
    secondary = ElectricBlueDark,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    onPrimary = VoidBlack,
    error = LightWarningRed,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurface,
    outline = LightOutline
  )

@Composable
fun MyApplicationTheme(
  themeMode: AppThemeMode = AppThemeMode.DARK,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    AppThemeMode.DARK -> true
    AppThemeMode.LIGHT -> false
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
  }

  val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

