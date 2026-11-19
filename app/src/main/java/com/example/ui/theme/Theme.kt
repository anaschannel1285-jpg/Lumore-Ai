package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val LumoreDarkColorScheme = darkColorScheme(
    primary = StudioPrimaryDark,
    onPrimary = StudioOnPrimaryDark,
    primaryContainer = StudioPrimaryContainerDark,
    onPrimaryContainer = StudioOnPrimaryContainerDark,
    secondary = StudioSecondaryDark,
    secondaryContainer = StudioSecondaryContainerDark,
    onSecondaryContainer = StudioOnSecondaryContainerDark,
    tertiary = StudioTertiaryDark,
    tertiaryContainer = StudioTertiaryContainerDark,
    onTertiaryContainer = StudioOnTertiaryContainerDark,
    background = StudioDarkBackground,
    onBackground = StudioDarkOnBackground,
    surface = StudioDarkSurface,
    onSurface = StudioDarkOnBackground,
    surfaceVariant = StudioDarkSurfaceVariant,
    onSurfaceVariant = StudioDarkOnSurfaceVariant,
    surfaceContainerLow = StudioDarkSurface,
    surfaceContainer = StudioDarkSurfaceVariant,
    surfaceContainerHigh = StudioDarkSurfaceElevated,
    outline = StudioDarkOutline,
    outlineVariant = StudioDarkOutline,
    error = StudioErrorDark,
    errorContainer = StudioErrorContainerDark,
    onErrorContainer = StudioOnErrorContainerDark
)

private val LumoreLightColorScheme = lightColorScheme(
    primary = StudioPrimaryLight,
    onPrimary = StudioOnPrimaryLight,
    primaryContainer = StudioPrimaryContainerLight,
    onPrimaryContainer = StudioOnPrimaryContainerLight,
    secondary = StudioSecondaryLight,
    secondaryContainer = StudioSecondaryContainerLight,
    onSecondaryContainer = StudioOnSecondaryContainerLight,
    tertiary = StudioTertiaryLight,
    tertiaryContainer = StudioTertiaryContainerLight,
    onTertiaryContainer = StudioOnTertiaryContainerLight,
    background = StudioLightBackground,
    onBackground = StudioLightOnBackground,
    surface = StudioLightSurface,
    onSurface = StudioLightOnBackground,
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = StudioLightOnSurfaceVariant,
    surfaceContainerLow = StudioLightSurface,
    surfaceContainer = StudioLightSurfaceElevated,
    surfaceContainerHigh = StudioLightSurfaceVariant,
    outline = StudioLightOutline,
    outlineVariant = StudioLightOutline,
    error = StudioErrorLight,
    errorContainer = StudioErrorContainerLight,
    onErrorContainer = StudioOnErrorContainerLight
)

val LumoreShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LumoreTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (isDark) LumoreDarkColorScheme else LumoreLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumoreTypography,
        shapes = LumoreShapes,
        content = content
    )
}
