package com.artkachenko.livetextfinder.status

import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.FrozenSearchFrame
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.search.ActiveSearchSpec
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.SearchPresentation
import com.artkachenko.livetextfinder.search.SearchTerm
import com.artkachenko.livetextfinder.search.TextSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchResultAvailabilityTest {
    private val spec = ActiveSearchSpec("active", "Parts", listOf(SearchTerm("x", "fox")))
    private val liveSnapshot = OcrSnapshot("fox", emptyList(), frameInfo())

    @Test
    fun liveResultsRequireCurrentIdentityVisibleScanningAndFreshExactSnapshot() {
        assertTrue(liveAvailable())
        assertFalse(liveAvailable(identity = "changed"))
        assertFalse(liveAvailable(status = OcrStatus.Starting))
        assertFalse(liveAvailable(status = OcrStatus.Paused))
        assertFalse(liveAvailable(status = OcrStatus.Error))
        assertFalse(liveAvailable(visible = false))
        assertFalse(liveAvailable(snapshot = null))
        assertFalse(liveAvailable(searchSnapshot = OcrSnapshot("fox", emptyList(), frameInfo())))
        assertFalse(liveAvailable(receipt = null))
        assertFalse(liveAvailable(receipt = 1_500L, now = 1_499L))
        assertTrue(liveAvailable(receipt = 0L, now = 1_499L))
        assertFalse(liveAvailable(receipt = 0L, now = 1_500L))
        assertFalse(liveAvailable(receipt = 0L, now = 1_501L))
        assertFalse(
            liveAvailable(
                searchSpec = spec.copy(terms = listOf(SearchTerm("blank", " \n "))),
            ),
        )
    }

    @Test
    fun customAreaRequiresCurrentReadyMappingWhileZeroRawOccurrencesStayEligible() {
        assertTrue(liveAvailable(areaSelected = true, areaRevision = 5, presentationRevision = 5))
        assertFalse(liveAvailable(areaSelected = true, areaRevision = 5, presentationRevision = 4))
        assertFalse(liveAvailable(areaSelected = true, areaReady = false))
        assertTrue(liveAvailable(occurrenceCount = 0))
    }

    @Test
    fun importFailureCanFallThroughToLiveResults() {
        assertTrue(liveAvailable(importPhase = ImportedImagePhase.Error))
        assertFalse(liveAvailable(importPhase = ImportedImagePhase.Recognizing))
    }

    @Test
    fun hiddenFrozenAndImportedSnapshotsKeepCountAvailabilityWithoutGeometry() {
        val imported = ImportedTextSnapshot("fox", emptyList(), 300, 200, 3L)
        val importedSearch = TextSearchResult(
            "fox",
            imported,
            emptyList(),
            spec,
        )
        assertTrue(
            hasAvailableSearchResults(
                search = importedSearch,
                currentIdentity = spec.identity,
                importedState = ImportedImageState(
                    ImportedImagePhase.Imported,
                    snapshot = imported,
                ),
                freezeState = FreezePresentationState(),
                ocrState = OcrUiState(),
                presentation = presentation(importedSearch),
                areaSelected = false,
                currentAreaRevision = 0,
                shouldShowViews = false,
                nowMillis = 1_000L,
            ),
        )

        val frozenSearch = TextSearchResult("fox", liveSnapshot, emptyList(), spec)
        val frame = FrozenSearchFrame(1L, FrameIdentity(1L, 1L), liveSnapshot)
        assertTrue(
            hasAvailableSearchResults(
                search = frozenSearch,
                currentIdentity = spec.identity,
                importedState = ImportedImageState(),
                freezeState = FreezePresentationState(FreezeMode.Frozen, frame = frame),
                ocrState = OcrUiState(OcrStatus.Paused),
                presentation = presentation(frozenSearch),
                areaSelected = false,
                currentAreaRevision = 0,
                shouldShowViews = false,
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun unavailableGuidanceExplainsCurrentStateAndPrioritizesActiveTerms() {
        assertEquals(
            null,
            guidance(hasUsableTerms = false, status = OcrStatus.Paused),
        )
        assertEquals(
            UnavailableResultsGuidance.Importing,
            guidance(importPhase = ImportedImagePhase.Picking, status = OcrStatus.Paused),
        )
        assertEquals(
            UnavailableResultsGuidance.Freezing,
            guidance(
                freezeState = FreezePresentationState(mode = FreezeMode.Freezing),
                status = OcrStatus.Paused,
            ),
        )
        assertEquals(
            UnavailableResultsGuidance.MissingImportedSource,
            guidance(importPhase = ImportedImagePhase.Imported),
        )
        assertEquals(
            UnavailableResultsGuidance.MissingFrozenSource,
            guidance(freezeState = FreezePresentationState(mode = FreezeMode.Frozen)),
        )
        assertEquals(
            UnavailableResultsGuidance.Waiting,
            guidance(importedState = ImportedImageState(
                phase = ImportedImagePhase.Imported,
                snapshot = ImportedTextSnapshot("fox", emptyList(), 300, 200, 4L),
            )),
        )
        assertEquals(
            UnavailableResultsGuidance.Waiting,
            guidance(freezeState = FreezePresentationState(
                mode = FreezeMode.Frozen,
                frame = FrozenSearchFrame(1L, FrameIdentity(1L, 1L), liveSnapshot),
            )),
        )
        assertEquals(
            UnavailableResultsGuidance.Permission,
            guidance(status = OcrStatus.PermissionRequired),
        )
        assertEquals(UnavailableResultsGuidance.Starting, guidance(status = OcrStatus.Starting))
        assertEquals(UnavailableResultsGuidance.Paused, guidance(status = OcrStatus.Paused))
        assertEquals(UnavailableResultsGuidance.Error, guidance(status = OcrStatus.Error))
        assertEquals(
            UnavailableResultsGuidance.Unavailable,
            guidance(status = OcrStatus.Unavailable),
        )
        assertEquals(UnavailableResultsGuidance.HiddenPreview, guidance(visible = false))
        assertEquals(
            UnavailableResultsGuidance.HiddenPreview,
            guidance(visible = false, status = OcrStatus.Paused),
        )
        assertEquals(
            UnavailableResultsGuidance.AreaMapping,
            guidance(areaSelected = true, areaReady = false),
        )
        assertEquals(UnavailableResultsGuidance.Waiting, guidance())
        assertEquals(null, guidance(resultsAvailable = true, status = OcrStatus.Paused))
    }

    private fun guidance(
        resultsAvailable: Boolean = false,
        hasUsableTerms: Boolean = true,
        importPhase: ImportedImagePhase = ImportedImagePhase.Live,
        importedState: ImportedImageState? = null,
        freezeState: FreezePresentationState = FreezePresentationState(),
        status: OcrStatus = OcrStatus.Scanning,
        visible: Boolean = true,
        areaSelected: Boolean = false,
        areaReady: Boolean = true,
    ) = unavailableResultsGuidance(
        resultsAvailable = resultsAvailable,
        hasUsableTerms = hasUsableTerms,
        importedState = importedState ?: ImportedImageState(importPhase),
        freezeState = freezeState,
        ocrState = OcrUiState(status),
        shouldShowViews = visible,
        areaSelected = areaSelected,
        areaReady = areaReady,
    )

    private fun liveAvailable(
        identity: String = spec.identity,
        status: OcrStatus = OcrStatus.Scanning,
        snapshot: OcrSnapshot? = liveSnapshot,
        searchSnapshot: OcrSnapshot? = liveSnapshot,
        receipt: Long? = 0L,
        now: Long = 1_499L,
        visible: Boolean = true,
        areaSelected: Boolean = false,
        areaRevision: Long = 5L,
        presentationRevision: Long = areaRevision,
        areaReady: Boolean = true,
        importPhase: ImportedImagePhase = ImportedImagePhase.Live,
        occurrenceCount: Int = 1,
        searchSpec: ActiveSearchSpec = spec,
    ): Boolean {
        val search = TextSearchResult(
            "fox",
            searchSnapshot,
            List(occurrenceCount) { index ->
                com.artkachenko.livetextfinder.search.TextOccurrence(
                    0,
                    index,
                    index + 1,
                    emptyList(),
                )
            },
            searchSpec,
        )
        return hasAvailableSearchResults(
            search = search,
            currentIdentity = identity,
            importedState = ImportedImageState(importPhase),
            freezeState = FreezePresentationState(),
            ocrState = OcrUiState(status, snapshot = snapshot, receivedAtMillis = receipt),
            presentation = presentation(search, presentationRevision, areaReady),
            areaSelected = areaSelected,
            currentAreaRevision = areaRevision,
            shouldShowViews = visible,
            nowMillis = now,
        )
    }

    private fun presentation(
        search: TextSearchResult,
        areaRevision: Long = 0L,
        areaReady: Boolean = true,
    ) = SearchPresentation(
        query = search.query,
        activeSearchIdentity = spec.identity,
        search = search,
        occurrences = emptyList(),
        observedSnapshot = search.snapshot,
        observedReceiptAtMillis = 0L,
        observedProjection = null,
        expiresAtMillis = null,
        outlinedElements = emptyList(),
        areaRevision = areaRevision,
        areaReady = areaReady,
    )

    private fun frameInfo() = FrameInfo(
        timestampNanos = 1L,
        bufferWidth = 300,
        bufferHeight = 200,
        rotationDegrees = 0,
        crop = TextBounds(0, 0, 300, 200),
        sensorToBufferMatrix = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
    )
}
