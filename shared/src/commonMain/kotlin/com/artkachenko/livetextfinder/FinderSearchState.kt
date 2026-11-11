package com.artkachenko.livetextfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.ActiveSearchSpec
import com.artkachenko.livetextfinder.search.Highlights
import com.artkachenko.livetextfinder.search.SearchPresentation
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.SearchSessionState
import com.artkachenko.livetextfinder.search.SearchTermSummary
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.search.normalizeRecognizedText
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.search.searchTermSummaries
import com.artkachenko.livetextfinder.utils.MonotonicClock
import kotlinx.coroutines.delay

/** The search result one composition displays for the current source, and what follows from it. */
internal class FinderSearchState(
    val activeSearch: ActiveSearchSpec,
    /** The result for the imported image, else the frozen frame, else the live presentation. */
    val search: TextSearchResult,
    /** The live camera presentation, including outlines retained through brief dropouts. */
    val presentation: SearchPresentation,
    /** Outlines projected into the viewport for the status region. */
    val highlights: Highlights,
    val resultsAvailable: Boolean,
    /** Why results are unavailable, or null when they are available. */
    val unavailableExplanation: String?,
    val termSummaries: List<SearchTermSummary>,
)

/**
 * Derives the displayed search state for the current source.
 *
 * The live presentation owner and its expiry timer are remembered; the timer recomposes once
 * when retained outlines expire. The returned object is new on every recomposition, so do not
 * use it as a `remember` or effect key.
 */
@Composable
internal fun finderSearchState(
    sessionState: SearchSessionState,
    ocrState: OcrUiState,
    importedState: ImportedImageState,
    freezeState: FreezePresentationState,
    liveArea: LiveSearchAreaState,
    viewport: IntSize,
    shouldShowViews: Boolean,
): FinderSearchState {
    val activeSearch = sessionState.activeSearch
    val highlightColor = sessionState.persisted.highlightColor
    var graceTimerRevision by remember { mutableIntStateOf(0) }
    val presentationOwner = remember { SearchPresentationOwner() }
    val presentation = remember(
        activeSearch,
        ocrState,
        graceTimerRevision,
        liveArea.area,
        liveArea.revision,
        viewport,
    ) {
        presentationOwner.present(
            activeSearch,
            ocrState,
            MonotonicClock.nowMillis(),
            liveArea.area,
            liveArea.revision,
            viewport.width,
            viewport.height,
        )
    }
    LaunchedEffect(presentation.expiresAtMillis) {
        val deadline = presentation.expiresAtMillis ?: return@LaunchedEffect
        val wait = deadline - MonotonicClock.nowMillis()
        if (wait > 0) delay(wait)
        graceTimerRevision++
    }
    val frozenFrame = freezeState.frame
    val frozenSearch = remember(activeSearch, frozenFrame) {
        frozenFrame?.let { frame ->
            searchRecognizedText(
                activeSearch,
                OcrUiState(OcrStatus.TextRecognized, snapshot = frame.snapshot),
            )
        }
    }
    val importedSearch = remember(activeSearch, importedState.snapshot) {
        importedState.snapshot?.let { snapshot ->
            searchRecognizedContent(activeSearch, snapshot)
        }
    }
    val search = importedSearch ?: frozenSearch ?: presentation.search
    val highlights = remember(
        search,
        importedState.snapshot,
        ocrState.projection,
        viewport,
        freezeState.mode,
    ) {
        if (importedState.phase == ImportedImagePhase.Imported &&
            importedState.snapshot != null
        ) {
            projectImportedHighlights(
                search,
                importedState.snapshot,
                viewport.width.toFloat(),
                viewport.height.toFloat(),
            )
        } else if (freezeState.mode == FreezeMode.Frozen) {
            projectFrozenHighlights(
                search,
                viewport.width.toFloat(),
                viewport.height.toFloat(),
            )
        } else {
            projectHighlights(search, ocrState.projection, viewport.width, viewport.height)
        }
    }
    val resultsAvailable = hasAvailableSearchResults(
        search = search,
        currentIdentity = activeSearch.identity,
        importedState = importedState,
        freezeState = freezeState,
        ocrState = ocrState,
        presentation = presentation,
        areaSelected = liveArea.area != null,
        currentAreaRevision = liveArea.revision,
        shouldShowViews = shouldShowViews,
        nowMillis = MonotonicClock.nowMillis(),
    )
    val unavailableGuidance = unavailableResultsGuidance(
        resultsAvailable = resultsAvailable,
        hasUsableTerms = activeSearch.terms.any {
            normalizeRecognizedText(it.text).isNotEmpty()
        },
        importedState = importedState,
        freezeState = freezeState,
        ocrState = ocrState,
        shouldShowViews = shouldShowViews,
        areaSelected = liveArea.area != null,
        areaReady = presentation.areaReady,
    )
    val termSummaries = remember(search, highlightColor) {
        searchTermSummaries(search, highlightColor)
    }
    return FinderSearchState(
        activeSearch = activeSearch,
        search = search,
        presentation = presentation,
        highlights = highlights,
        resultsAvailable = resultsAvailable,
        unavailableExplanation = unavailableResultsExplanation(unavailableGuidance),
        termSummaries = termSummaries,
    )
}
