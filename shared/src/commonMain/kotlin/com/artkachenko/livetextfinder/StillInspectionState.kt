package com.artkachenko.livetextfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.StillViewTransform
import com.artkachenko.livetextfinder.camera.isImportInProgress
import com.artkachenko.livetextfinder.camera.plainTextClipEntry
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.Highlights
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.StillMatchCopyPayload
import com.artkachenko.livetextfinder.search.StillMatchRow
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.search.occurrenceBounds
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import com.artkachenko.livetextfinder.search.stillMatchRows
import com.artkachenko.livetextfinder.search.stillMatchText
import com.artkachenko.livetextfinder.ui.rememberStillViewTransform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.still_match_copied
import livetextfinder.shared.generated.resources.still_match_copy_failed
import org.jetbrains.compose.resources.stringResource

/**
 * Match selection, navigation, zoom and copy for the retained still image, as seen by one
 * composition.
 *
 * A new instance is created on every recomposition. Constructor values are the ones that
 * composition displayed; callbacks compare them with the owners' current state and do nothing
 * when the source, search, selection or viewport changed after the UI was composed.
 */
internal class StillInspectionState(
    val source: StillSource?,
    private val search: TextSearchResult,
    private val searchIdentity: String,
    val rows: List<StillMatchRow>,
    val fit: FrozenFrameFit?,
    private val expectedViewport: IntSize,
    private val currentViewport: () -> IntSize,
    private val density: Float,
    private val transformState: MutableState<StillViewTransform>,
    private val selectedIndexState: MutableState<Int?>,
    private val selectedTermIdState: MutableState<String?>,
    val copySession: StillMatchCopySession,
    private val navigationCoordinator: StillMatchNavigationCoordinator,
    private val importedOwner: ImportedImageOwner,
    private val frozenOwner: FrozenFrameOwner,
    private val session: SearchSessionController,
    private val clipboard: Clipboard,
    private val coroutineScope: CoroutineScope,
    private val copiedMessage: String,
    private val copyFailedMessage: String,
) {
    /** Keys the viewport's zoom state to the displayed source. */
    val inspectionIdentity: String? = source?.inspectionIdentity

    /** The selection this composition displayed; callbacks reject a newer selection. */
    val expectedSelectedIndex: Int? = selectedIndexState.value

    /** The selection right now, which may be newer than [expectedSelectedIndex]. */
    val selectedIndex: Int? get() = selectedIndexState.value

    /** The selected row among all matches. */
    val selectedRow: StillMatchRow? = rows.firstOrNull { it.index == expectedSelectedIndex }

    /** Matches limited to the selected term while the selection belongs to that term. */
    val effectiveRows: List<StillMatchRow> =
        effectiveStillMatchRows(rows, selectedTermIdState.value, expectedSelectedIndex)

    /** One-based position of the selection in [effectiveRows], or zero without a selection. */
    val selectedPosition: Int =
        effectiveRows.indexOfFirst { it.index == expectedSelectedIndex } + 1

    /** The selected row among [effectiveRows]. */
    val selectedNavigationRow: StillMatchRow? =
        effectiveRows.firstOrNull { it.index == expectedSelectedIndex }

    var transform: StillViewTransform
        get() = transformState.value
        set(value) {
            transformState.value = value
        }

    private val copyOpening: Any? = copySession.openingToken

    /** Returns whether [row] still belongs to the displayed source, search and selection. */
    fun isActionCurrent(row: StillMatchRow, requireLocateContext: Boolean = true): Boolean {
        val identity = source?.identity ?: return false
        val token = source.ownerToken ?: return false
        return navigationCoordinator.accepts(
            row,
            search,
            identity,
            searchIdentity,
            token,
            expectedSelectedIndex,
            expectedViewport,
            requireLocateContext,
        )
    }

    /** Zooms around the viewport centre; requires a valid [fit]. */
    fun zoomBy(factor: Float) {
        val viewport = currentViewport()
        transform = transform.zoomBy(
            factor,
            PreviewPoint(viewport.width / 2f, viewport.height / 2f),
            viewport.width.toFloat(),
            viewport.height.toFloat(),
            checkNotNull(fit),
        )
    }

    fun resetZoom() {
        transform = StillViewTransform()
    }

    /** Selects [row] and brings it into view when the displayed context is still current. */
    fun locate(row: StillMatchRow, onLocated: () -> Unit = {}): Boolean {
        val identity = source?.identity ?: return false
        val token = source.ownerToken ?: return false
        return performStillMatchLocate(
            navigationCoordinator,
            row,
            search,
            identity,
            searchIdentity,
            token,
            expectedSelectedIndex,
            expectedViewport,
            focus = { focus(row) },
            onLocated = onLocated,
        )
    }

    /** Locates the previous (-1) or next (1) row in [effectiveRows]. */
    fun locateAdjacent(direction: Int) {
        adjacentStillMatchRow(effectiveRows, selectedPosition, direction)?.let { locate(it) }
    }

    /** Locates the first match of [termId] and then limits navigation to that term. */
    fun locateFirstForTerm(termId: String, onLocated: () -> Unit) {
        val row = rows.firstOrNull {
            it.occurrence.termId == termId && stillMatchText(it, search) != null
        }
        if (row != null) {
            locate(row) {
                selectedTermIdState.value = termId
                onLocated()
            }
        }
    }

    /** Selects a row from the matches dialog without moving the image. */
    fun selectFromDialog(index: Int) {
        if (!copySession.acceptsOpening(copyOpening)) return
        val row = rows.firstOrNull { it.index == index }
        if (row != null && isActionCurrent(row, requireLocateContext = false)) {
            selectedIndexState.value = index
        }
    }

    fun dismissMatchesDialog() {
        if (copySession.acceptsOpening(copyOpening)) {
            copySession.updateDialogOpen(false)
        }
    }

    /** Closes the matches dialog on the selected row and clears the term filter. */
    fun showSelectedOnImage() {
        if (!copySession.acceptsOpening(copyOpening)) return
        val row = selectedRow
        if (row != null) {
            locate(row) {
                selectedTermIdState.value = null
                copySession.updateDialogOpen(false)
            }
        }
    }

    /** Closes the matches dialog and drops the selection. */
    fun clearSelection() {
        copySession.updateDialogOpen(false)
        selectedIndexState.value = null
    }

    /** Copies the selected match, revalidating the source before and during the write. */
    fun copySelected(payload: StillMatchCopyPayload) {
        val index = expectedSelectedIndex
        val row = selectedRow
        val identity = source?.identity
        val ownerToken = source?.ownerToken
        if (!copySession.acceptsOpening(copyOpening)) return
        if (identity == null || index == null || row == null || ownerToken == null ||
            !isCopyCurrent(row, index)
        ) {
            copySession.fail(copyOpening, copyFailedMessage)
            return
        }
        coroutineScope.launch {
            copySession.copy(
                token = copyOpening,
                contextIsCurrent = { isCopyCurrent(row, index) },
                copiedMessage = copiedMessage,
                failureMessage = copyFailedMessage,
            ) { requestIsCurrent ->
                StillMatchCopyCoordinator(
                    importedOwner = importedOwner,
                    frozenOwner = frozenOwner,
                    session = session,
                    selectedIndex = { selectedIndexState.value },
                    writeText = { text ->
                        clipboard.setClipEntry(plainTextClipEntry(text))
                    },
                    contextIsCurrent = requestIsCurrent,
                ).copy(row, search, identity, searchIdentity, index, ownerToken, payload)
            }
        }
    }

    private fun isCopyCurrent(row: StillMatchRow, index: Int?): Boolean {
        val token = source?.ownerToken ?: return false
        if (index == null ||
            selectedIndexState.value != index ||
            session.state.value.activeSearch.identity != searchIdentity ||
            stillMatchText(row, search) != row.matchedText
        ) {
            return false
        }
        val currentImport = importedOwner.state.value
        if (currentImport.phase.isImportInProgress) return false
        val currentSource = stillSourceOf(currentImport, frozenOwner.state.value)
        return currentSource?.ownerToken === token
    }

    private fun projectedHighlights(): Highlights? {
        val viewport = currentViewport()
        return when (source) {
            is StillSource.Imported -> source.snapshot?.let { snapshot ->
                projectImportedHighlights(
                    search,
                    snapshot,
                    viewport.width.toFloat(),
                    viewport.height.toFloat(),
                )
            }
            is StillSource.Frozen -> projectFrozenHighlights(
                search,
                viewport.width.toFloat(),
                viewport.height.toFloat(),
            )
            null -> null
        }
    }

    private fun focus(row: StillMatchRow): Boolean {
        val bounds = occurrenceBounds(row.occurrence, projectedHighlights() ?: return false)
            ?: return false
        val viewport = currentViewport()
        val focused = transform.focus(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            viewport.width.toFloat(),
            viewport.height.toFloat(),
            fit ?: return false,
            FOCUS_MARGIN_DP * density,
        ) ?: return false
        selectedIndexState.value = row.index
        transform = focused
        return true
    }

    private companion object {
        const val FOCUS_MARGIN_DP = 24f
    }
}

/**
 * Returns this composition's view of the inspection state for [source].
 *
 * The selection, zoom and copy state are remembered and reset when the source or the active
 * search changes. The returned object is new on every recomposition, so do not use it as a
 * `remember` or effect key.
 */
@Composable
internal fun stillInspectionState(
    source: StillSource?,
    search: TextSearchResult,
    searchIdentity: String,
    highlightColor: HighlightColor,
    viewportState: State<IntSize>,
    context: FinderSourceContext,
): StillInspectionState {
    val viewport = viewportState.value
    val inspectionIdentity = source?.inspectionIdentity
    val transformState = rememberStillViewTransform(inspectionIdentity, viewport)
    val rows = remember(search, inspectionIdentity, highlightColor) {
        if (inspectionIdentity != null) {
            stillMatchRows(search, highlightColor)
        } else {
            emptyList()
        }
    }
    val copySession = rememberStillMatchCopySession(inspectionIdentity, searchIdentity)
    val selectedIndexState = remember(inspectionIdentity, searchIdentity) {
        mutableStateOf<Int?>(null)
    }
    val selectedTermIdState = remember(inspectionIdentity, searchIdentity) {
        mutableStateOf<String?>(null)
    }
    val navigationCoordinator = rememberStillMatchNavigationCoordinator(
        context.importedOwner,
        context.frozenOwner,
        context.session,
        selectedIndex = { selectedIndexState.value },
        viewport = { viewportState.value },
        visible = context.isContentVisible,
    )
    return StillInspectionState(
        source = source,
        search = search,
        searchIdentity = searchIdentity,
        rows = rows,
        fit = source?.fit(viewport),
        expectedViewport = viewport,
        currentViewport = { viewportState.value },
        density = LocalDensity.current.density,
        transformState = transformState,
        selectedIndexState = selectedIndexState,
        selectedTermIdState = selectedTermIdState,
        copySession = copySession,
        navigationCoordinator = navigationCoordinator,
        importedOwner = context.importedOwner,
        frozenOwner = context.frozenOwner,
        session = context.session,
        clipboard = LocalClipboard.current,
        coroutineScope = rememberCoroutineScope(),
        copiedMessage = stringResource(Res.string.still_match_copied),
        copyFailedMessage = stringResource(Res.string.still_match_copy_failed),
    )
}
