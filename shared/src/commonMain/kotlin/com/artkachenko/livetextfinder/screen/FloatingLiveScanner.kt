package com.artkachenko.livetextfinder.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.camera.LiveCaptureState
import com.artkachenko.livetextfinder.search.SearchMatchMode
import com.artkachenko.livetextfinder.ui.FinderSearchBar
import com.artkachenko.livetextfinder.ui.FloatingScannerAction
import com.artkachenko.livetextfinder.ui.FloatingScannerActionBar
import com.artkachenko.livetextfinder.ui.FloatingScannerIconButton
import com.artkachenko.livetextfinder.ui.FloatingScannerSearchField
import com.artkachenko.livetextfinder.ui.FloatingScannerStatusPill
import com.artkachenko.livetextfinder.ui.FloatingScannerZoomPill
import kotlin.math.roundToInt
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.capture_cancel_freeze
import livetextfinder.shared.generated.resources.capture_cancel_import
import livetextfinder.shared.generated.resources.capture_light
import livetextfinder.shared.generated.resources.finder_options
import livetextfinder.shared.generated.resources.scan_area_badge
import livetextfinder.shared.generated.resources.scanner_crop_free
import livetextfinder.shared.generated.resources.scanner_flash_off
import livetextfinder.shared.generated.resources.scanner_flash_on
import livetextfinder.shared.generated.resources.scanner_freeze
import livetextfinder.shared.generated.resources.scanner_image
import livetextfinder.shared.generated.resources.scanner_import
import livetextfinder.shared.generated.resources.scanner_list
import livetextfinder.shared.generated.resources.scanner_pause
import livetextfinder.shared.generated.resources.scanner_settings
import livetextfinder.shared.generated.resources.scanner_toggle_off
import livetextfinder.shared.generated.resources.scanner_toggle_on
import livetextfinder.shared.generated.resources.scanner_zoom_value
import livetextfinder.shared.generated.resources.status_cancel
import org.jetbrains.compose.resources.stringResource

/** Overlays the live scanner controls on the caller's full-bleed viewport. */
@Composable
internal fun FloatingLiveScanner(
    searchBar: FinderSearchBar,
    liveControls: LiveCaptureControls,
    captureEnabled: Boolean,
    freezePending: Boolean,
    onFreeze: () -> Unit,
    onCancelFreeze: () -> Unit,
    areaEnabled: Boolean,
    onArea: () -> Unit,
    importEnabled: Boolean,
    importPending: Boolean,
    onImport: () -> Unit,
    onCancelImport: () -> Unit,
    onMatchMode: (SearchMatchMode) -> Unit,
    onOptions: () -> Unit,
    statusLabel: String,
    onStatus: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    statusEnabled: Boolean = true,
    matchModeEnabled: Boolean = true,
    messages: @Composable () -> Unit = { Unit },
    retained: FloatingRetainedControls? = null,
    retainedViewport: @Composable (Modifier) -> Unit = {},
) {
    val density = LocalDensity.current
    val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
    var bottomHeight by remember { mutableIntStateOf(0) }
    val cameraState = liveControls.state.value
    val lease = cameraState.lease
    val cameraActionsEnabled = cameraState.ready && !cameraState.zoomPending &&
        !freezePending && !importPending
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
            .testTag("floating-live-scanner")
            .safeDrawingPadding()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
    ) {
        val showZoom = retained == null && !keyboardOpen && maxHeight >= 476.dp && cameraState.hasZoom
        val showLabels = maxHeight >= 336.dp
        val showRetainedPills = retained != null && !keyboardOpen && showLabels
        if (retained != null) {
            val bottom = with(density) { bottomHeight.toDp() }
            val top = if (keyboardOpen) 0.dp else 56.dp
            retainedViewport(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .padding(top = top)
                    .height((maxHeight - bottom - top).coerceAtLeast(0.dp)),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!keyboardOpen) {
                FloatingLiveTopRow(
                    cameraState = if (retained == null) cameraState else LiveCaptureState(),
                    flashEnabled = cameraActionsEnabled && !cameraState.torchPending,
                    onFlash = { liveControls.toggleTorch(lease) },
                    onOptions = onOptions,
                    statusLabel = statusLabel,
                    statusEnabled = statusEnabled,
                    onStatus = onStatus,
                )
            }
            messages()
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnackbarHost(snackbarHostState)
            Column(
                modifier = Modifier.onSizeChanged { bottomHeight = it.height },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showRetainedPills) {
                    FloatingRetainedPills(checkNotNull(retained))
                } else if (showZoom) {
                    val zoomTenths = (cameraState.zoom * 10f).roundToInt()
                    FloatingScannerZoomPill(
                        zoomLabel = stringResource(
                            Res.string.scanner_zoom_value,
                            zoomTenths / 10,
                            zoomTenths % 10,
                        ),
                        zoomOutEnabled = cameraActionsEnabled &&
                            cameraState.zoom > cameraState.minZoom,
                        zoomInEnabled = cameraActionsEnabled &&
                            cameraState.zoom < cameraState.maxZoom,
                        onZoomOut = { liveControls.zoomOut(lease) },
                        onZoomIn = { liveControls.zoomIn(lease) },
                        modifier = Modifier.testTag("floating-live-zoom"),
                    )
                }
                FloatingScannerSearchField(
                    query = searchBar.query,
                    onQueryChange = searchBar.onQueryChange,
                    wholeWords = searchBar.matchMode == SearchMatchMode.WholeWord,
                    onWholeWordsChange = { checked ->
                        onMatchMode(
                            if (checked) SearchMatchMode.WholeWord else SearchMatchMode.Substring,
                        )
                    },
                    modifier = Modifier.testTag("floating-live-search"),
                    activeListName = searchBar.activeListName,
                    matchModeEnabled = matchModeEnabled,
                    onUseManual = searchBar.onUseManual,
                )
                if (!keyboardOpen) {
                    FloatingScannerActionBar(
                        actions = if (retained != null) {
                            retainedActions(searchBar, retained)
                        } else listOf(
                            FloatingScannerAction(
                                icon = Res.drawable.scanner_list,
                                label = searchBar.searchListsLabel,
                                onClick = searchBar.onSearchLists,
                            ),
                            FloatingScannerAction(
                                icon = Res.drawable.scanner_crop_free,
                                label = stringResource(Res.string.scan_area_badge),
                                onClick = onArea,
                                enabled = areaEnabled,
                            ),
                            FloatingScannerAction(
                                icon = Res.drawable.scanner_pause,
                                label = stringResource(
                                    if (freezePending) {
                                        Res.string.status_cancel
                                    } else {
                                        Res.string.scanner_freeze
                                    },
                                ),
                                onClick = if (freezePending) onCancelFreeze else onFreeze,
                                enabled = freezePending || captureEnabled,
                                inProgress = freezePending,
                                accessibilityLabel = if (freezePending) {
                                    stringResource(Res.string.capture_cancel_freeze)
                                } else {
                                    null
                                },
                            ),
                            FloatingScannerAction(
                                icon = Res.drawable.scanner_image,
                                label = stringResource(
                                    if (importPending) {
                                        Res.string.status_cancel
                                    } else {
                                        Res.string.scanner_import
                                    },
                                ),
                                onClick = if (importPending) onCancelImport else onImport,
                                enabled = importPending || importEnabled,
                                inProgress = importPending,
                                accessibilityLabel = if (importPending) {
                                    stringResource(Res.string.capture_cancel_import)
                                } else {
                                    null
                                },
                            ),
                        ),
                        modifier = Modifier.testTag("floating-live-action-bar"),
                        showLabels = showLabels,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingLiveTopRow(
    cameraState: LiveCaptureState,
    flashEnabled: Boolean,
    onFlash: () -> Unit,
    onOptions: () -> Unit,
    statusLabel: String,
    statusEnabled: Boolean,
    onStatus: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FloatingScannerStatusPill(
            label = statusLabel,
            onClick = onStatus,
            modifier = Modifier.padding(horizontal = 56.dp).testTag("floating-live-status"),
            enabled = statusEnabled,
        )
        if (cameraState.hasFlash) {
            FloatingScannerIconButton(
                icon = if (cameraState.torchOn) {
                    Res.drawable.scanner_flash_on
                } else {
                    Res.drawable.scanner_flash_off
                },
                label = stringResource(Res.string.capture_light),
                stateLabel = stringResource(
                    if (cameraState.torchOn) {
                        Res.string.scanner_toggle_on
                    } else {
                        Res.string.scanner_toggle_off
                    },
                ),
                enabled = flashEnabled,
                onClick = onFlash,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        FloatingScannerIconButton(
            icon = Res.drawable.scanner_settings,
            label = stringResource(Res.string.finder_options),
            onClick = onOptions,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
