package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AnriDarkColorScheme =
  darkColorScheme(
    primary = TealLuminous,
    onPrimary = OnTealDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = AmberSpark,
    onSecondary = Color(0xFF2B1700),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = SkyAccent,
    onTertiary = Color(0xFF002438),
    tertiaryContainer = SkyContainerDark,
    onTertiaryContainer = OnSkyContainerDark,
    background = ObsidianBg,
    onBackground = InkLight,
    surface = ObsidianSurface,
    onSurface = InkLight,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
    error = CoralErrorDark,
    onError = Color(0xFF450A0A),
    errorContainer = CoralErrorContainerDark,
    onErrorContainer = Color(0xFFFEE2E2),
  )

private val AnriLightColorScheme =
  lightColorScheme(
    primary = TealDeep,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = AmberLight,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = SkyLight,
    onTertiary = Color.White,
    tertiaryContainer = SkyContainerLight,
    onTertiaryContainer = OnSkyContainerLight,
    background = AlabasterBg,
    onBackground = InkDark,
    surface = AlabasterSurface,
    onSurface = InkDark,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = InkMutedLight,
    surfaceContainerLow = AlabasterBg,
    surfaceContainer = AlabasterSurfaceVariant,
    surfaceContainerHigh = AlabasterSurfaceHigh,
    outline = AlabasterOutline,
    outlineVariant = AlabasterOutlineSubtle,
    error = CrimsonErrorLight,
    onError = Color.White,
    errorContainer = CrimsonErrorContainerLight,
    onErrorContainer = Color(0xFF7F1D1D),
  )

val AnriShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun AnriTheme(
  darkModeOverride: String = "dark",
  content: @Composable () -> Unit,
) {
  val isDark =
    when (darkModeOverride) {
      "light" -> false
      "dark" -> true
      else -> isSystemInDarkTheme()
    }
  val colorScheme = if (isDark) AnriDarkColorScheme else AnriLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AnriTypography,
    shapes = AnriShapes,
    content = content,
  )
}
