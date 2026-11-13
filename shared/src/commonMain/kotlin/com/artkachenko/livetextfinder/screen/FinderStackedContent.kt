package com.artkachenko.livetextfinder.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.inspection.StillInspectionState
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaState
import com.artkachenko.livetextfinder.search.SearchListTransferState
import com.artkachenko.livetextfinder.ui.FinderCaptureActions
import com.artkachenko.livetextfinder.ui.FinderContentAssembly
import com.artkachenko.livetextfinder.ui.FinderSearchBar

/** Retains the stacked layout for the list manager and retained-image inspection. */
@Composable
internal fun FinderStackedContent(
    layoutMode: FinderLayoutMode,
    searchBar: FinderSearchBar,
    captureActions: FinderCaptureActions,
    labels: FinderLabels,
    actions: FinderSourceActions,
    dialogs: FinderDialogsState,
    inspection: StillInspectionState,
    liveArea: LiveSearchAreaState,
    context: FinderSourceContext,
    shouldShowViews: Boolean,
    resultsAvailable: Boolean,
    sharePreparing: Boolean,
    snackbarHostState: SnackbarHostState,
    transferState: SearchListTransferState,
    onCancelTransfer: () -> Unit,
    rootSizeState: MutableState<IntSize>,
    viewport: @Composable (Modifier) -> Unit,
    status: @Composable () -> Unit,
    manager: @Composable () -> Unit,
) {
    var footerTop by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        FinderContentAssembly(
            scrollState = rememberScrollState(),
            scrollable = !shouldShowViews,
            modifier = Modifier.fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .onSizeChanged { rootSizeState.value = it },
            topContent = {
                FinderTopRegion(
                    layoutMode = layoutMode,
                    shouldShowViews = shouldShowViews,
                    searchBar = searchBar,
                    inlineResultsLabel = labels.results,
                    transferState = transferState,
                    onCancelTransfer = onCancelTransfer,
                    onOptions = { dialogs.optionsOpen = true },
                    status = status,
                )
            },
            managerContent = { manager() },
            viewportContent = {
                if (shouldShowViews) {
                    viewport(Modifier.weight(1f).fillMaxWidth())
                }
            },
            footerContent = {
                Spacer(Modifier.onGloballyPositioned { footerTop = it.positionInParent().y })
                FinderFooter(
                    layoutMode = layoutMode,
                    searchBar = searchBar,
                    captureActions = captureActions,
                    labels = labels,
                    actions = actions,
                    dialogs = dialogs,
                    inspection = inspection,
                    liveArea = liveArea,
                    context = context,
                    shouldShowViews = shouldShowViews,
                    resultsAvailable = resultsAvailable,
                    sharePreparing = sharePreparing,
                )
            },
        )
        val footerHeight = if (shouldShowViews) {
            (rootSizeState.value.height - footerTop).coerceAtLeast(0f)
        } else {
            0f
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .imePadding()
                .padding(bottom = with(density) { footerHeight.toDp() }),
        )
    }
}
