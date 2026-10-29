package com.artkachenko.livetextfinder.search

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class HighlightColorTest {
    @Test
    fun customRgbIsOpaqueCanonicalAndPresetEquivalentByEffectiveArgb() {
        val customYellow = HighlightColor.fromRgb(255, 255, 0)
        val custom = HighlightColor.fromRgb(18, 171, 239)

        assertEquals(HighlightColor.Yellow, customYellow)
        assertEquals("yellow", customYellow.storageKey)
        assertEquals("#12ABEF", custom.displayName)
        assertEquals("custom:#12ABEF", custom.storageKey)
        assertEquals(0xFF12ABEFL, custom.argb)
        assertEquals(Color(custom.argb.toInt()), custom.composeColor)
        assertNotEquals(custom, HighlightColor.fromRgb(18, 171, 238))
    }

    @Test
    fun constructorsFactoriesAndCopiesRejectInvalidChannelsAndAlpha() {
        assertFailsWith<IllegalArgumentException> { HighlightColor(0x00112233) }
        assertFailsWith<IllegalArgumentException> { HighlightColor(-1) }
        assertFailsWith<IllegalArgumentException> { HighlightColor(0x1_0000_0000) }
        assertFailsWith<IllegalArgumentException> { HighlightColor.Yellow.copy(0x00112233) }
        assertFailsWith<IllegalArgumentException> { HighlightColor.fromRgb(-1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { HighlightColor.fromRgb(0, 256, 0) }
        assertFailsWith<IllegalArgumentException> { HighlightColor.fromRgb(0, 0, -1) }
    }

    @Test
    fun storageKeysAcceptOnlyPresetsAndCanonicalOpaqueCustomRgb() {
        assertEquals(HighlightColor.Cyan, HighlightColor.fromStorageKey("cyan"))
        assertEquals(
            HighlightColor.fromRgb(18, 171, 239),
            HighlightColor.fromStorageKey("custom:#12ABEF"),
        )
        listOf(
            "custom:#12abef",
            "custom:#12ABEG",
            "custom:#12ABEF00",
            "custom:12ABEF",
            "custom:#12ABEF ",
        ).forEach { key -> assertEquals(null, HighlightColor.fromStorageKey(key)) }
    }
}
