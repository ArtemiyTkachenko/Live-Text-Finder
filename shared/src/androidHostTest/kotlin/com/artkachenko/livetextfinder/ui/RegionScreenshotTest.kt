package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.ScreenshotTier
import com.artkachenko.livetextfinder.ScreenshotVariant
import com.artkachenko.livetextfinder.assertScreenshot
import com.artkachenko.livetextfinder.drainMainLooper
import com.artkachenko.livetextfinder.screen.FinderLayoutMode
import com.artkachenko.livetextfinder.screen.FinderTopRegion
import com.artkachenko.livetextfinder.search.SavedSearchList
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.SearchListTransferState
import com.artkachenko.livetextfinder.search.SearchSessionError
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.capture_cancel_freeze
import livetextfinder.shared.generated.resources.capture_freeze
import livetextfinder.shared.generated.resources.capture_return_to_camera
import livetextfinder.shared.generated.resources.inspection_lists
import livetextfinder.shared.generated.resources.inspection_results
import livetextfinder.shared.generated.resources.inspection_return_frozen
import livetextfinder.shared.generated.resources.search_lists_close
import livetextfinder.shared.generated.resources.status_frozen
import livetextfinder.shared.generated.resources.status_scanning
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RegionScreenshotTest {
    private val receipts = SavedSearchList("receipts", "Receipts", listOf("invoice", "subtotal"))
    private val labels = SavedSearchList("labels", "Labels", listOf("lot number"))

    @After
    fun tearDown() {
        drainMainLooper()
    }

    @Test
    fun dockManualSearch() = captureDock(DockState.ManualSearch)

    @Test
    fun dockActiveList() = captureDock(DockState.ActiveList)

    @Test
    fun dockFreezePending() = captureDock(DockState.FreezePending)

    @Test
    fun dockRetainedImportedImage() = captureDock(DockState.RetainedImportedImage)

    @Test
    fun dockRetainedFrozenFrame() = captureDock(DockState.RetainedFrozenFrame)

    @Test
    fun dockSharePreparing() = captureDock(DockState.SharePreparing)

    @Test
    fun searchControlsInListManager() = capture("finder-search-controls", "list-manager") {
        FinderSearchControls(
            showTitle = true,
            search = searchBar(activeList = receipts, managerOpen = true),
            onOptions = ::ignore,
            resultsLabel = stringResource(Res.string.inspection_results, 3),
        )
    }

    @Test
    fun captureControlsInListManager() = capture("finder-capture-controls", "list-manager") {
        FinderCaptureControls(captureActions())
    }

    @Test
    fun topRegionLive() = captureTopRegion(FinderLayoutMode.Live, "live")

    @Test
    fun topRegionAreaEditing() = captureTopRegion(FinderLayoutMode.AreaEditing, "area-editing")

    @Test
    fun topRegionListManager() = captureTopRegion(FinderLayoutMode.ListManager, "list-manager")

    @Test
    fun topRegionStillInspection() =
        captureTopRegion(FinderLayoutMode.StillInspection, "still-inspection")

    @Test
    fun searchListsEmpty() = capture("finder-search-lists", "empty") {
        SearchLists()
    }

    @Test
    fun searchListsActiveList() = capture("finder-search-lists", "active-list") {
        SearchLists(lists = listOf(receipts, labels), activeListId = receipts.id)
    }

    @Test
    fun searchListsEditorError() = capture("finder-search-lists", "editor-error") {
        // The production manager scrolls. Show the validation message and save actions.
        Box(
            Modifier.height(ScreenshotTier.REGION.heightDp.dp)
                .verticalScroll(rememberScrollState(initial = Int.MAX_VALUE)),
        ) {
            SearchLists(
                editor = SearchListEditorState(
                    isOpen = true,
                    terms = "invoice",
                    error = SearchSessionError.ListNameBlank,
                ),
            )
        }
    }

    @Test
    fun stillInspectionControls() = capture("still-inspection-controls", "zoomed") {
        StillInspectionControls(
            matchCount = 3,
            zoom = 2f,
            canZoom = true,
            onZoomIn = ::ignore,
            onZoomOut = ::ignore,
            onReset = ::ignore,
            onMatches = ::ignore,
        )
    }

    @Test
    fun stillMatchNavigationControls() =
        capture("still-match-navigation-controls", "match-selected") {
            StillMatchNavigationControls(
                position = 2,
                count = 3,
                matchedTerm = "invoice",
                matchedText = "Invoice",
                canLocate = true,
                onPrevious = ::ignore,
                onNext = ::ignore,
                onCopy = ::ignore,
            )
        }

    private fun captureDock(state: DockState) = captureVariants("finder-live-dock", state.fixture) {
        val returnLabel = when (state) {
            DockState.RetainedImportedImage, DockState.SharePreparing ->
                stringResource(Res.string.capture_return_to_camera)
            DockState.RetainedFrozenFrame -> stringResource(Res.string.inspection_return_frozen)
            else -> null
        }
        FinderLiveDock(
            search = searchBar(activeList = receipts.takeIf { state == DockState.ActiveList }),
            capture = captureActions(
                freezePending = state == DockState.FreezePending,
                retained = returnLabel != null,
            ),
            resultsLabel = stringResource(Res.string.inspection_results, 3),
            returnLabel = returnLabel,
            sharePreparing = state == DockState.SharePreparing,
            onCancelShare = ::ignore,
        )
    }

    private fun captureTopRegion(mode: FinderLayoutMode, state: String) =
        captureVariants("finder-top-region", state) {
            val managerOpen = mode == FinderLayoutMode.ListManager
            Column {
                FinderTopRegion(
                    layoutMode = mode,
                    shouldShowViews = true,
                    searchBar = searchBar(
                        activeList = receipts.takeIf { managerOpen },
                        managerOpen = managerOpen,
                    ),
                    inlineResultsLabel = stringResource(Res.string.inspection_results, 3),
                    transferState = SearchListTransferState(),
                    onCancelTransfer = ::ignore,
                    onOptions = ::ignore,
                    status = {
                        StatusSummary(
                            message = stringResource(
                                if (mode == FinderLayoutMode.StillInspection) {
                                    Res.string.status_frozen
                                } else {
                                    Res.string.status_scanning
                                },
                            ),
                            details = null,
                            onDetails = ::ignore,
                            stageBand = mode.isLiveCamera,
                        )
                    },
                )
            }
        }

    @Composable
    private fun searchBar(
        activeList: SavedSearchList? = null,
        managerOpen: Boolean = false,
    ): FinderSearchBar = FinderSearchBar(
        query = "invoice",
        onQueryChange = {},
        searchListsLabel = stringResource(
            if (managerOpen) Res.string.search_lists_close else Res.string.inspection_lists,
        ),
        onSearchLists = ::ignore,
        activeListName = activeList?.name,
        activeListTermCount = activeList?.terms?.size ?: 0,
    )

    @Composable
    private fun captureActions(
        freezePending: Boolean = false,
        retained: Boolean = false,
    ): FinderCaptureActions = FinderCaptureActions(
        captureLabel = if (freezePending || retained) {
            null
        } else {
            stringResource(Res.string.capture_freeze)
        },
        captureEnabled = !freezePending && !retained,
        onCapture = ::ignore,
        pendingCaptureLabel = if (freezePending) {
            stringResource(Res.string.capture_cancel_freeze)
        } else {
            null
        },
        onCancelCapture = ::ignore,
        secondaryActionLabel = null,
        onSecondaryAction = ::ignore,
    )

    @Composable
    private fun SearchLists(
        lists: List<SavedSearchList> = emptyList(),
        activeListId: String? = null,
        editor: SearchListEditorState = SearchListEditorState(),
    ) {
        FinderSearchLists(
            lists = lists,
            activeListId = activeListId,
            editor = editor,
            canSave = true,
            onNew = ::ignore,
            onSelect = {},
            onEdit = {},
            onDelete = {},
            onNameChange = {},
            onTermsChange = {},
            onSave = ::ignore,
            onCancel = ::ignore,
        )
    }

    private fun capture(subject: String, state: String, content: @Composable () -> Unit) {
        assertScreenshot(ScreenshotTier.REGION, subject, state, ScreenshotVariant.LIGHT, content)
    }

    private fun captureVariants(subject: String, state: String, content: @Composable () -> Unit) {
        ScreenshotVariant.entries.forEach { variant ->
            assertScreenshot(ScreenshotTier.REGION, subject, state, variant, content)
        }
    }

    private fun ignore() = Unit

    private enum class DockState(val fixture: String) {
        ManualSearch("manual-search"),
        ActiveList("active-list"),
        FreezePending("freeze-pending"),
        RetainedImportedImage("retained-imported-image"),
        RetainedFrozenFrame("retained-frozen-frame"),
        SharePreparing("share-preparing"),
    }
}
