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
    fun chromeFlags_matchEachMode() {
        val flags = FinderLayoutMode.entries.associateWith { mode ->
            Triple(mode.usesDockChrome, mode.showsDock, mode.isLiveCamera)
        }

        assertEquals(
            mapOf(
                FinderLayoutMode.Live to Triple(true, true, true),
                FinderLayoutMode.AreaEditing to Triple(true, false, true),
                FinderLayoutMode.ListManager to Triple(false, false, false),
                FinderLayoutMode.StillInspection to Triple(true, true, false),
            ),
            flags,
        )
    }
}
