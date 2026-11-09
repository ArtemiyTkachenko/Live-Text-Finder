package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object FinderSpacing {
    val absent: Dp = 0.dp
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val screenMargin: Dp = 20.dp
    val extraLarge: Dp = 24.dp
    val majorSection: Dp = 32.dp
}

private val lightColors = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F1E2),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF49645C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E9DF),
    onSecondaryContainer = Color(0xFF10271F),
    tertiary = Color(0xFF795A20),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF281900),
    error = Color(0xFFBA4A42),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410006),
    background = Color(0xFFF5F8F7),
    onBackground = Color(0xFF17211F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17211F),
    surfaceVariant = Color(0xFFE3EAE7),
    onSurfaceVariant = Color(0xFF4B5D58),
    surfaceTint = Color(0xFF006B58),
    surfaceDim = Color(0xFFD7E0DC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAF9),
    surfaceContainer = Color(0xFFEEF3F1),
    surfaceContainerHigh = Color(0xFFE7EEEB),
    surfaceContainerHighest = Color(0xFFE0E8E4),
    inverseSurface = Color(0xFF293330),
    inverseOnSurface = Color(0xFFF0F5F2),
    inversePrimary = Color(0xFF60E1C1),
    outline = Color(0xFF52645F),
    outlineVariant = Color(0xFFC5D1CC),
    scrim = Color(0xFF000000),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF60E1C1),
    onPrimary = Color(0xFF09221C),
    primaryContainer = Color(0xFF164F42),
    onPrimaryContainer = Color(0xFFB4F5E4),
    secondary = Color(0xFFB5CEC4),
    onSecondary = Color(0xFF20352E),
    secondaryContainer = Color(0xFF364B43),
    onSecondaryContainer = Color(0xFFD1E8DE),
    tertiary = Color(0xFFE7C17B),
    onTertiary = Color(0xFF3E2E0A),
    tertiaryContainer = Color(0xFF5A4518),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF5F1512),
    errorContainer = Color(0xFF762B25),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0C1016),
    onBackground = Color(0xFFF4F7FA),
    surface = Color(0xFF171D25),
    onSurface = Color(0xFFF4F7FA),
    surfaceVariant = Color(0xFF293441),
    onSurfaceVariant = Color(0xFFA5B2C0),
    surfaceTint = Color(0xFF60E1C1),
    surfaceDim = Color(0xFF0C1016),
    surfaceBright = Color(0xFF303A47),
    surfaceContainerLowest = Color(0xFF11161D),
    surfaceContainerLow = Color(0xFF141A22),
    surfaceContainer = Color(0xFF171D25),
    surfaceContainerHigh = Color(0xFF232B36),
    surfaceContainerHighest = Color(0xFF2A3441),
    inverseSurface = Color(0xFFE1E8ED),
    inverseOnSurface = Color(0xFF202830),
    inversePrimary = Color(0xFF006B58),
    outline = Color(0xFF8797A8),
    outlineVariant = Color(0xFF334155),
    scrim = Color(0xFF000000),
)

private val baseTypography = Typography()
private val finderTypography = baseTypography.copy(
    displayLarge = baseTypography.displayLarge.copy(fontWeight = FontWeight.Normal),
    headlineSmall = baseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = baseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = baseTypography.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = baseTypography.bodyLarge.copy(fontWeight = FontWeight.Normal),
    bodyMedium = baseTypography.bodyMedium.copy(fontWeight = FontWeight.Normal),
    bodySmall = baseTypography.bodySmall.copy(fontWeight = FontWeight.Normal),
    labelLarge = baseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = baseTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = baseTypography.labelSmall.copy(fontWeight = FontWeight.Medium),
)

private val finderShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
internal fun LiveTextFinderTheme(
    darkThemeOverride: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = darkThemeOverride ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = platformColorScheme(darkTheme, dynamicColor),
        typography = finderTypography,
        shapes = finderShapes,
        content = content,
    )
}

@Composable
internal fun FinderPrimaryAction(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.padding(bottom = 16.dp).widthIn(min = 48.dp).heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
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
