package com.artkachenko.livetextfinder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.artkachenko.livetextfinder.search.StillMatchCopyPayload
import com.artkachenko.livetextfinder.ui.ContextMessage
import com.artkachenko.livetextfinder.ui.FinderSpacing
import com.artkachenko.livetextfinder.ui.StillInspectionControls
import com.artkachenko.livetextfinder.ui.StillMatchNavigationControls
import com.artkachenko.livetextfinder.ui.StillMatchesDialog
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_results_locate_hint
import org.jetbrains.compose.resources.stringResource

/** Zoom and match navigation shown above the dock for a retained image. */
@Composable
internal fun StillInspectionFooter(
    inspection: StillInspectionState,
    shouldShowViews: Boolean,
    resultsAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(
            horizontal = FinderSpacing.large,
            vertical = FinderSpacing.extraSmall,
        ),
        verticalArrangement = Arrangement.spacedBy(
            FinderSpacing.extraSmall,
        ),
    ) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        StillInspectionControls(
            matchCount = inspection.rows.size,
            showMatchesAction = false,
            zoom = inspection.transform.zoom,
            canZoom = shouldShowViews && inspection.fit?.isValid == true,
            onZoomIn = { inspection.zoomBy(1.25f) },
            onZoomOut = { inspection.zoomBy(0.8f) },
            onReset = inspection::resetZoom,
            onMatches = {
                focusManager.clearFocus()
                keyboardController?.hide()
                inspection.copySession.updateDialogOpen(true)
            },
        )
        val selectedRow = inspection.selectedNavigationRow
        if (inspection.selectedPosition > 0 && selectedRow != null) {
            val canLocate = shouldShowViews &&
                resultsAvailable &&
                inspection.isActionCurrent(selectedRow)
            if (!canLocate) {
                ContextMessage(
                    message = stringResource(
                        Res.string.search_results_locate_hint,
                    ),
                )
            }
            StillMatchNavigationControls(
                position = inspection.selectedPosition,
                count = inspection.effectiveRows.size,
                matchedTerm = selectedRow.termLabel,
                matchedText = selectedRow.matchedText,
                canLocate = canLocate,
                onPrevious = { inspection.locateAdjacent(-1) },
                onNext = { inspection.locateAdjacent(1) },
                onCopy = {
                    inspection.copySelected(
                        StillMatchCopyPayload.MatchedText,
                    )
                },
            )
        }
    }
}

/** Lists every match on the retained image with select, copy and show-on-image actions. */
@Composable
internal fun StillInspectionMatchesDialog(
    inspection: StillInspectionState,
    shouldShowViews: Boolean,
    resultsAvailable: Boolean,
) {
    val selectedDialogRow = inspection.selectedRow
    StillMatchesDialog(
        rows = inspection.rows,
        selectedIndex = inspection.expectedSelectedIndex,
        copyMessage = inspection.copySession.message,
        onSelect = inspection::selectFromDialog,
        onDismiss = inspection::dismissMatchesDialog,
        onCopy = { inspection.copySelected(StillMatchCopyPayload.MatchedText) },
        onCopyLine = { inspection.copySelected(StillMatchCopyPayload.Line) },
        onShowOnImage = inspection::showSelectedOnImage,
        canLocate = shouldShowViews && selectedDialogRow?.let {
            inspection.isActionCurrent(it)
        } == true && resultsAvailable,
    )
}
