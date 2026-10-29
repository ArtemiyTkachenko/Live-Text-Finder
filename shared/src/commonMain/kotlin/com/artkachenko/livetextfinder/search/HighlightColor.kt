package com.artkachenko.livetextfinder.search

import androidx.compose.ui.graphics.Color

/** An immutable opaque RGB outline colour with stable persistence and display values. */
class HighlightColor(val argb: Long) {
    init {
        require(argb in MIN_ARGB..MAX_ARGB) {
            "ARGB value must be an unsigned 32-bit number."
        }
        require((argb ushr 24) == OPAQUE_ALPHA) {
            "Highlight colours must be opaque."
        }
    }

    val storageKey: String
        get() = entries.firstOrNull { it.argb == argb }?.presetStorageKey
            ?: "custom:${hexRgb}"

    val displayName: String
        get() = entries.firstOrNull { it.argb == argb }?.presetDisplayName ?: hexRgb

    val hexRgb: String
        get() = "#${argb.toString(16).takeLast(6).uppercase()}"

    val composeColor: Color
        get() = Color(argb.toInt())

    fun copy(argb: Long = this.argb): HighlightColor = HighlightColor(argb)

    override fun equals(other: Any?): Boolean = other is HighlightColor && argb == other.argb

    override fun hashCode(): Int = argb.hashCode()

    override fun toString(): String = "HighlightColor($hexRgb)"

    private val presetStorageKey: String?
        get() = PRESET_STORAGE_KEYS[argb]

    private val presetDisplayName: String?
        get() = PRESET_DISPLAY_NAMES[argb]

    companion object {
        val Yellow = HighlightColor(0xFFFFFF00)
        val Cyan = HighlightColor(0xFF00FFFF)
        val Magenta = HighlightColor(0xFFFF00FF)
        val White = HighlightColor(0xFFFFFFFF)
        val entries: List<HighlightColor> = listOf(Yellow, Cyan, Magenta, White)

        private const val MIN_ARGB = 0L
        private const val MAX_ARGB = 0xFFFFFFFFL
        private const val OPAQUE_ALPHA = 0xFFL
        private val PRESET_STORAGE_KEYS = mapOf(
            Yellow.argb to "yellow",
            Cyan.argb to "cyan",
            Magenta.argb to "magenta",
            White.argb to "white",
        )
        private val PRESET_DISPLAY_NAMES = mapOf(
            Yellow.argb to "Yellow",
            Cyan.argb to "Cyan",
            Magenta.argb to "Magenta",
            White.argb to "White",
        )

        /** Creates a colour from red, green and blue channels in the inclusive range 0–255. */
        fun fromRgb(red: Int, green: Int, blue: Int): HighlightColor {
            require(red in 0..255 && green in 0..255 && blue in 0..255) {
                "RGB channels must be in the range 0–255."
            }
            val argb = OPAQUE_ALPHA shl 24 or (red.toLong() shl 16) or
                (green.toLong() shl 8) or blue.toLong()
            return entries.firstOrNull { it.argb == argb } ?: HighlightColor(argb)
        }

        /** Resolves a preset or a canonical custom key such as `custom:#12ABEF`. */
        fun fromStorageKey(key: String): HighlightColor? {
            entries.firstOrNull { it.presetStorageKey == key }?.let { return it }
            if (!key.startsWith("custom:#") || key.length != 14) {
                return null
            }
            val rgb = key.substring(8)
            if (rgb.any { it !in '0'..'9' && it !in 'A'..'F' }) {
                return null
            }
            return fromRgb(
                red = rgb.substring(0, 2).toInt(16),
                green = rgb.substring(2, 4).toInt(16),
                blue = rgb.substring(4, 6).toInt(16),
            )
        }
    }
}
