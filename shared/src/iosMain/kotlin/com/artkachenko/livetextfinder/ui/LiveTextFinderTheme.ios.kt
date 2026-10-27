package com.artkachenko.livetextfinder.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal actual fun platformColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
): ColorScheme = fallbackColorScheme(darkTheme)
