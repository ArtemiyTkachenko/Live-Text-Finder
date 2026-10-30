package com.artkachenko.livetextfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.StillMatchRow
import com.artkachenko.livetextfinder.search.StillMatchSourceState
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.search.stillMatchText

internal fun filterStillMatchRows(
    rows: List<StillMatchRow>,
    termId: String?,
): List<StillMatchRow> = if (termId == null) rows else rows.filter {
    it.occurrence.termId == termId
}

internal fun effectiveStillMatchRows(
    rows: List<StillMatchRow>,
    termId: String?,
    selectedIndex: Int?,
): List<StillMatchRow> {
    val filtered = filterStillMatchRows(rows, termId)
    return if (selectedIndex != null && filtered.none { it.index == selectedIndex }) {
        rows
    } else {
        filtered
    }
}

internal fun adjacentStillMatchRow(
    rows: List<StillMatchRow>,
    position: Int,
    direction: Int,
): StillMatchRow? {
    if (direction !in listOf(-1, 1) || position !in 1..rows.size) return null
    return rows.getOrNull(position - 1 + direction)
}

internal fun performStillMatchLocate(
    coordinator: StillMatchNavigationCoordinator,
    row: StillMatchRow,
    result: TextSearchResult,
    sourceIdentity: String,
    searchIdentity: String,
    ownerToken: Any,
    expectedSelection: Int?,
    expectedViewport: IntSize,
    focus: () -> Boolean,
    onLocated: () -> Unit,
): Boolean {
    if (!coordinator.accepts(
            row,
            result,
            sourceIdentity,
            searchIdentity,
            ownerToken,
            expectedSelection,
            expectedViewport,
        ) || !focus()
    ) return false
    onLocated()
    return true
}

/** Revalidates a captured navigation callback against its exact retained image and viewport. */
internal class StillMatchNavigationCoordinator(
    private val importedOwner: ImportedImageOwner,
    private val frozenOwner: FrozenFrameOwner,
    private val session: SearchSessionController,
    private val selectedIndex: () -> Int?,
    private val viewport: () -> IntSize,
    private val visible: () -> Boolean,
) {
    fun accepts(
        row: StillMatchRow,
        result: TextSearchResult,
        sourceIdentity: String,
        searchIdentity: String,
        ownerToken: Any,
        expectedSelection: Int?,
        expectedViewport: IntSize,
        requireLocateContext: Boolean = true,
    ): Boolean {
        val importState = importedOwner.state.value
        if (importState.phase == ImportedImagePhase.Picking ||
            importState.phase == ImportedImagePhase.Reading ||
            importState.phase == ImportedImagePhase.Recognizing ||
            result.spec.identity != searchIdentity ||
            requireLocateContext && (expectedViewport.width <= 0 ||
                expectedViewport.height <= 0 || !visible() || viewport() != expectedViewport) ||
            session.state.value.activeSearch.identity != searchIdentity ||
            selectedIndex() != expectedSelection
        ) return false
        val currentSource = when {
            importState.phase == ImportedImagePhase.Imported -> {
                val snapshot = importState.snapshot
                StillMatchSourceState(
                    snapshot?.let { "import:${it.sourceIdentity}" },
                    snapshot,
                    snapshot,
                )
            }
            frozenOwner.state.value.mode == FreezeMode.Frozen -> {
                val frame = frozenOwner.state.value.frame
                StillMatchSourceState(
                    frame?.let { "frozen:${it.identity}" }, frame?.snapshot, frame
                )
            }
            else -> StillMatchSourceState(null, null, null)
        }
        return currentSource.sourceIdentity == sourceIdentity &&
            currentSource.ownerToken === ownerToken && currentSource.content === result.content &&
            stillMatchText(row, result) == row.matchedText
    }
}

@Composable
internal fun rememberStillMatchNavigationCoordinator(
    importedOwner: ImportedImageOwner,
    frozenOwner: FrozenFrameOwner,
    session: SearchSessionController,
    selectedIndex: () -> Int?,
    viewport: () -> IntSize,
    visible: () -> Boolean,
): StillMatchNavigationCoordinator {
    val currentSelection = rememberUpdatedState(selectedIndex)
    val currentViewport = rememberUpdatedState(viewport)
    val currentVisibility = rememberUpdatedState(visible)
    return remember(importedOwner, frozenOwner, session) {
        StillMatchNavigationCoordinator(
            importedOwner,
            frozenOwner,
            session,
            selectedIndex = { currentSelection.value() },
            viewport = { currentViewport.value() },
            visible = { currentVisibility.value() },
        )
    }
}
