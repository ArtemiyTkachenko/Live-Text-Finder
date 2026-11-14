package com.artkachenko.livetextfinder.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class FinderLayoutModeTest {
    @Test
    fun stillSource_outranksManagerAndAreaEditor() {
        assertEquals(FinderLayoutMode.StillInspection, finderLayoutMode(true, false, false))
        assertEquals(FinderLayoutMode.StillInspection, finderLayoutMode(true, true, true))
    }

    @Test
    fun liveCamera_resolvesManagerThenAreaEditorThenLive() {
        assertEquals(FinderLayoutMode.ListManager, finderLayoutMode(false, true, false))
        assertEquals(FinderLayoutMode.ListManager, finderLayoutMode(false, true, true))
        assertEquals(FinderLayoutMode.AreaEditing, finderLayoutMode(false, false, true))
        assertEquals(FinderLayoutMode.Live, finderLayoutMode(false, false, false))
    }

    @Test
    fun onlyLiveAndAreaEditingUseTheLiveViewport() {
        assertEquals(
            setOf(FinderLayoutMode.Live, FinderLayoutMode.AreaEditing),
            FinderLayoutMode.entries.filter { it.isLiveCamera }.toSet(),
        )
    }
}
