package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object FinderSpacing {
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val extraLarge: Dp = 24.dp
}

private val lightColors = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131B2C),
    tertiary = Color(0xFF705575),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFAD8FC),
    onTertiaryContainer = Color(0xFF28132F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF9FF),
    onBackground = Color(0xFF191B20),
    surface = Color(0xFFFAF9FF),
    onSurface = Color(0xFF191B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF444650),
    surfaceTint = Color(0xFF005AC1),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceBright = Color(0xFFFAF9FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEFEEF5),
    surfaceContainerHigh = Color(0xFFE9E8EF),
    surfaceContainerHighest = Color(0xFFE3E2E9),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0EFF6),
    inversePrimary = Color(0xFFADC6FF),
    outline = Color(0xFF747680),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283042),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2845),
    tertiaryContainer = Color(0xFF573E5D),
    onTertiaryContainer = Color(0xFFFAD8FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF444650),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFADC6FF),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF373940),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191B20),
    surfaceContainer = Color(0xFF1D1F25),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceContainerHighest = Color(0xFF33353B),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    inversePrimary = Color(0xFF005AC1),
    outline = Color(0xFF8E909A),
    outlineVariant = Color(0xFF444650),
    scrim = Color(0xFF000000),
)

@Composable
internal fun LiveTextFinderTheme(
    darkThemeOverride: Boolean? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = darkThemeOverride ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = platformColorScheme(darkTheme, dynamicColor),
        typography = Typography(),
        shapes = Shapes(),
        content = content,
    )
}

@Composable
internal fun FinderPrimaryAction(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Text(label)
    }
}

@Composable
internal expect fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
): ColorScheme

internal fun fallbackColorScheme(darkTheme: Boolean) = if (darkTheme) darkColors else lightColors
