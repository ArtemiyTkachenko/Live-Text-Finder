package com.artkachenko.livetextfinder.search

import androidx.compose.ui.graphics.Color

/** A selectable outline colour with its stable storage key and opaque ARGB value. */
enum class HighlightColor(
    val storageKey: String,
    val displayName: String,
    val argb: Long,
) {
    Yellow("yellow", "Yellow", 0xFFFFFF00),
    Cyan("cyan", "Cyan", 0xFF00FFFF),
    Magenta("magenta", "Magenta", 0xFFFF00FF),
    White("white", "White", 0xFFFFFFFF),
    ;

    val composeColor: Color
        get() = Color(argb.toInt())

    companion object {
        fun fromStorageKey(key: String): HighlightColor? =
            entries.firstOrNull { it.storageKey == key }
    }
}
