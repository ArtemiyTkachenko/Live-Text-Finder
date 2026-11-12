package com.artkachenko.livetextfinder.inspection

import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.FrozenSearchFrame
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.search.FrameIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class StillSourceTest {
    private val viewport = IntSize(200, 100)
    private val imported = ImportedTextSnapshot("", emptyList(), 40, 30, 7L)
    private val frame = FrozenSearchFrame(
        3L,
        FrameIdentity(1L, 2L),
        OcrSnapshot(
            "",
            emptyList(),
            FrameInfo(0L, 80, 60, 90, TextBounds(0, 0, 80, 60), emptyList()),
        ),
    )

    @Test
    fun liveCamera_hasNoStillSource() {
        assertNull(stillSourceOf(ImportedImageState(), FreezePresentationState()))
        assertNull(
            stillSourceOf(
                ImportedImageState(ImportedImagePhase.Recognizing),
                FreezePresentationState(FreezeMode.Freezing, requestId = 3L),
            ),
        )
    }

    @Test
    fun importedImage_usesSnapshotIdentityTokenAndUprightFit() {
        val source = stillSourceOf(
            ImportedImageState(ImportedImagePhase.Imported, snapshot = imported),
            FreezePresentationState(),
        )

        assertIs<StillSource.Imported>(source)
        assertEquals("import:7", source.identity)
        assertEquals("import:7", source.inspectionIdentity)
        assertSame(imported, source.ownerToken)
        assertEquals(FrozenFrameFit(40, 30, 0, 200f, 100f), source.fit(viewport))
    }

    @Test
    fun frozenFrame_keysInspectionByRequestAndFitsRawBufferRotation() {
        val source = stillSourceOf(
            ImportedImageState(),
            FreezePresentationState(FreezeMode.Frozen, frame = frame),
        )

        assertIs<StillSource.Frozen>(source)
        assertEquals("frozen:${frame.identity}", source.identity)
        assertEquals("frozen:${frame.identity}:3", source.inspectionIdentity)
        assertSame(frame, source.ownerToken)
        assertEquals(FrozenFrameFit(80, 60, 90, 200f, 100f), source.fit(viewport))
    }

    @Test
    fun importedImage_takesPriorityOverFrozenFrame() {
        val source = stillSourceOf(
            ImportedImageState(ImportedImagePhase.Imported, snapshot = imported),
            FreezePresentationState(FreezeMode.Frozen, frame = frame),
        )

        assertIs<StillSource.Imported>(source)
    }

    @Test
    fun missingRetainedContent_keepsIdentityWithoutTokenOrFit() {
        val importedSource = StillSource.Imported(null)
        val frozenSource = StillSource.Frozen(null)

        assertEquals("import:null", importedSource.identity)
        assertNull(importedSource.ownerToken)
        assertNull(importedSource.fit(viewport))
        assertEquals("frozen:null", frozenSource.identity)
        assertEquals("frozen:null:null", frozenSource.inspectionIdentity)
        assertNull(frozenSource.ownerToken)
        assertNull(frozenSource.fit(viewport))
    }
}
