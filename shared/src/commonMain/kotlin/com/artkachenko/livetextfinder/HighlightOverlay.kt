package com.artkachenko.livetextfinder

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.camera.isImportInProgress
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.ActiveSearchSpec
import com.artkachenko.livetextfinder.search.Highlights
import com.artkachenko.livetextfinder.search.SearchHighlightPalette
import com.artkachenko.livetextfinder.search.SearchPresentation
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.drawSelectedOccurrenceMarker
import com.artkachenko.livetextfinder.search.drawableElementsForSession
import com.artkachenko.livetextfinder.search.importedHighlightsForDraw
import com.artkachenko.livetextfinder.search.isPresentationCurrentAreaRevision
import com.artkachenko.livetextfinder.search.occurrenceBounds
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.utils.MonotonicClock

/**
 * Draws match outlines, the selected-match marker and the committed scan-area border over the
 * live, frozen or imported preview.
 *
 * The value parameters are what the caller's composition displayed. At draw time they are
 * compared with the owners' current state, and nothing stale is drawn: outlines from a replaced
 * source, a changed search or an outdated scan area are suppressed until recomposition.
 */
@Composable
internal fun HighlightOverlay(
    suppressed: Boolean,
    search: TextSearchResult,
    activeSearch: ActiveSearchSpec,
    presentation: SearchPresentation,
    ocrState: OcrUiState,
    importedState: ImportedImageState,
    freezeState: FreezePresentationState,
    liveArea: LiveSearchAreaState,
    inspection: StillInspectionState,
    viewportState: State<IntSize>,
    importedOwner: ImportedImageOwner,
    frozenOwner: FrozenFrameOwner,
    session: SearchSessionController,
    isContentVisible: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (suppressed) return@Canvas
        val currentFreezeState = frozenOwner.state.value
        val currentSearchIdentity =
            session.state.value.activeSearch.identity
        val currentImportState = importedOwner.state.value
        val areaIsCurrent =
            isPresentationCurrentAreaRevision(
                presentation,
                liveArea.owner.revision,
            ) &&
            liveArea.revision == presentation.areaRevision
        val liveAreaIsDisplayed = areaIsCurrent && liveArea.area != null &&
            importedState.phase != ImportedImagePhase.Imported &&
            !importedState.phase.isImportInProgress &&
            currentFreezeState.mode == FreezeMode.Live &&
            currentImportState.phase != ImportedImagePhase.Imported &&
            !currentImportState.phase.isImportInProgress &&
            liveArea.owner.area == liveArea.area &&
            isContentVisible() &&
            size.width == viewportState.value.width.toFloat() &&
            size.height == viewportState.value.height.toFloat() &&
            size.width > 0f && size.height > 0f
        val outlines = if (!areaIsCurrent) {
            emptyList()
        } else if (
            importedState.phase == ImportedImagePhase.Imported ||
            currentImportState.phase == ImportedImagePhase.Imported
        ) {
            importedHighlightsForDraw(
                search,
                importedState.snapshot,
                currentImportState,
                activeSearch.identity,
                currentSearchIdentity,
                size.width,
                size.height,
                isContentVisible(),
            ).outlines
        } else if (!isContentVisible()) {
            emptyList()
        } else if (importedState.phase.isImportInProgress) {
            emptyList()
        } else if (freezeState.mode == FreezeMode.Frozen) {
            if (currentFreezeState.mode == FreezeMode.Frozen &&
                currentFreezeState.frame === freezeState.frame &&
                currentSearchIdentity == activeSearch.identity
            ) {
                projectFrozenHighlights(
                    search,
                    size.width,
                    size.height,
                ).outlines
            } else {
                emptyList()
            }
        } else {
            if (currentFreezeState.mode == FreezeMode.Live ||
                currentFreezeState.mode == FreezeMode.Freezing
            ) {
                drawableElementsForSession(
                    session,
                    activeSearch,
                    presentation,
                    ocrState,
                    MonotonicClock.nowMillis(),
                    size.width,
                    size.height,
                ).map { it.displayOutline }
            } else {
                emptyList()
            }
        }
        val currentHighlightColor =
            session.state.value.persisted.highlightColor
        val termColors = SearchHighlightPalette.colorsByTerm(
            activeSearch,
            currentHighlightColor,
        )
        if (liveAreaIsDisplayed) {
            val area = checkNotNull(liveArea.area)
            clipRect(
                left = area.left * size.width,
                top = area.top * size.height,
                right = area.right * size.width,
                bottom = area.bottom * size.height,
            ) {
                drawHighlightOutlines(
                    outlines,
                    currentHighlightColor,
                    termColors,
                )
            }
        } else {
            drawHighlightOutlines(
                outlines,
                currentHighlightColor,
                termColors,
            )
        }
        val selectedRow = inspection.rows.firstOrNull {
            it.index == inspection.selectedIndex
        }
        val selectedBounds = selectedRow?.takeIf {
            inspection.isActionCurrent(it)
        }?.let { row ->
            occurrenceBounds(row.occurrence, Highlights(true, outlines))
        }
        selectedBounds?.let { bounds ->
            drawSelectedOccurrenceMarker(bounds)
        }
        if (liveAreaIsDisplayed) {
            val area = checkNotNull(liveArea.area)
            drawRect(
                color = Color.White,
                topLeft = Offset(
                    area.left * size.width,
                    area.top * size.height,
                ),
                size = Size(
                    (area.right - area.left) * size.width,
                    (area.bottom - area.top) * size.height,
                ),
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(10.dp.toPx(), 6.dp.toPx()),
                    ),
                ),
            )
        }
    }
}
