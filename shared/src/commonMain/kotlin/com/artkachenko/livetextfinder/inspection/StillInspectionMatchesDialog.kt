package com.artkachenko.livetextfinder.inspection

import androidx.compose.runtime.Composable
import com.artkachenko.livetextfinder.search.StillMatchCopyPayload
import com.artkachenko.livetextfinder.ui.StillMatchesDialog

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
