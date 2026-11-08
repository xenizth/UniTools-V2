package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FlashPrimaryDark,
    onPrimary = FlashOnPrimaryDark,
    primaryContainer = FlashPrimaryContainerDark,
    onPrimaryContainer = FlashOnPrimaryContainerDark,
    secondary = FlashSecondaryDark,
    onSecondary = FlashOnSecondaryDark,
    secondaryContainer = FlashSecondaryContainerDark,
    onSecondaryContainer = FlashOnSecondaryContainerDark,
    tertiary = FlashTertiaryDark,
    onTertiary = FlashOnTertiaryDark,
    tertiaryContainer = FlashTertiaryContainerDark,
    onTertiaryContainer = FlashOnTertiaryContainerDark,
    error = FlashErrorDark,
    onError = FlashOnErrorDark,
    errorContainer = FlashErrorContainerDark,
    onErrorContainer = FlashOnErrorContainerDark,
    background = FlashBackgroundDark,
    onBackground = FlashOnBackgroundDark,
    surface = FlashSurfaceDark,
    onSurface = FlashOnSurfaceDark,
    surfaceVariant = FlashSurfaceVariantDark,
    onSurfaceVariant = FlashOnSurfaceVariantDark,
    outline = FlashOutlineDark,
    outlineVariant = FlashOutlineVariantDark,
    inverseSurface = FlashInverseSurfaceDark,
    inverseOnSurface = FlashInverseOnSurfaceDark,
    inversePrimary = FlashInversePrimaryDark,
    surfaceDim = FlashSurfaceDimDark,
    surfaceBright = FlashSurfaceBrightDark,
    surfaceContainerLowest = FlashSurfaceContainerLowestDark,
    surfaceContainerLow = FlashSurfaceContainerLowDark,
    surfaceContainer = FlashSurfaceContainerDark,
    surfaceContainerHigh = FlashSurfaceContainerHighDark,
    surfaceContainerHighest = FlashSurfaceContainerHighestDark
)

private val LightColorScheme = lightColorScheme(
    primary = FlashPrimaryLight,
    onPrimary = FlashOnPrimaryLight,
    primaryContainer = FlashPrimaryContainerLight,
    onPrimaryContainer = FlashOnPrimaryContainerLight,
    secondary = FlashSecondaryLight,
    onSecondary = FlashOnSecondaryLight,
    secondaryContainer = FlashSecondaryContainerLight,
    onSecondaryContainer = FlashOnSecondaryContainerLight,
    tertiary = FlashTertiaryLight,
    onTertiary = FlashOnTertiaryLight,
    tertiaryContainer = FlashTertiaryContainerLight,
    onTertiaryContainer = FlashOnTertiaryContainerLight,
    error = FlashErrorLight,
    onError = FlashOnErrorLight,
    errorContainer = FlashErrorContainerLight,
    onErrorContainer = FlashOnErrorContainerLight,
    background = FlashBackgroundLight,
    onBackground = FlashOnBackgroundLight,
    surface = FlashSurfaceLight,
    onSurface = FlashOnSurfaceLight,
    surfaceVariant = FlashSurfaceVariantLight,
    onSurfaceVariant = FlashOnSurfaceVariantLight,
    outline = FlashOutlineLight,
    outlineVariant = FlashOutlineVariantLight,
    inverseSurface = FlashInverseSurfaceLight,
    inverseOnSurface = FlashInverseOnSurfaceLight,
    inversePrimary = FlashInversePrimaryLight,
    surfaceDim = FlashSurfaceDimLight,
    surfaceBright = FlashSurfaceBrightLight,
    surfaceContainerLowest = FlashSurfaceContainerLowestLight,
    surfaceContainerLow = FlashSurfaceContainerLowLight,
    surfaceContainer = FlashSurfaceContainerLight,
    surfaceContainerHigh = FlashSurfaceContainerHighLight,
    surfaceContainerHighest = FlashSurfaceContainerHighestLight
)

/**
 * UniTools uses a deliberate brand identity (signal-cyan on circuit-navy, matching the launcher
 * icon) rather than per-device Material You wallpaper theming, so [dynamicColor] defaults to
 * false. Pass true explicitly if wallpaper-matched colors are ever wanted instead.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
