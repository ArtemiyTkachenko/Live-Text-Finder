package com.artkachenko.livetextfinder.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.StillViewTransform
import com.artkachenko.livetextfinder.inspection.StillInspectionState
import com.artkachenko.livetextfinder.search.StillMatchCopyPayload
import com.artkachenko.livetextfinder.ui.FinderSearchBar
import com.artkachenko.livetextfinder.ui.FloatingScannerAction
import com.artkachenko.livetextfinder.ui.FloatingScannerMatchPill
import com.artkachenko.livetextfinder.ui.FloatingScannerZoomPill
import kotlin.math.roundToInt
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.capture_return_to_camera
import livetextfinder.shared.generated.resources.scanner_camera
import livetextfinder.shared.generated.resources.scanner_cancel_share
import livetextfinder.shared.generated.resources.scanner_content_copy
import livetextfinder.shared.generated.resources.scanner_list
import livetextfinder.shared.generated.resources.scanner_photo_camera
import livetextfinder.shared.generated.resources.scanner_share
import livetextfinder.shared.generated.resources.scanner_zoom_value
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.status_share_image
import livetextfinder.shared.generated.resources.still_match_copy_short
import org.jetbrains.compose.resources.stringResource

internal class FloatingRetainedControls(
    val zoom: Float,
    val canZoom: Boolean,
    val position: Int,
    val matchCount: Int,
    val canNavigate: Boolean,
    val canCopy: Boolean,
    val sharePreparing: Boolean,
    val onZoom: (Float) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onCopy: () -> Unit,
    val onShare: () -> Unit,
    val onCancelShare: () -> Unit,
    val onCamera: () -> Unit,
)

@Composable
internal fun FloatingRetainedPills(controls: FloatingRetainedControls) {
    val zoomTenths = (controls.zoom * 10f).roundToInt()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FloatingScannerZoomPill(
            zoomLabel = stringResource(
                Res.string.scanner_zoom_value,
                zoomTenths / 10,
                zoomTenths % 10,
            ),
            zoomOutEnabled = controls.canZoom && controls.zoom > StillViewTransform.MIN_ZOOM,
            zoomInEnabled = controls.canZoom && controls.zoom < StillViewTransform.MAX_ZOOM,
            onZoomOut = { controls.onZoom(0.8f) },
            onZoomIn = { controls.onZoom(1.25f) },
        )
        if (controls.matchCount > 0) {
            FloatingScannerMatchPill(
                position = controls.position,
                count = controls.matchCount,
                previousEnabled = controls.canNavigate && controls.position > 1,
                nextEnabled = controls.canNavigate && controls.position < controls.matchCount,
                onPrevious = controls.onPrevious,
                onNext = controls.onNext,
            )
        }
    }
}

@Composable
internal fun retainedActions(
    search: FinderSearchBar,
    controls: FloatingRetainedControls,
): List<FloatingScannerAction> {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return listOf(
        FloatingScannerAction(
            Res.drawable.scanner_list,
            search.searchListsLabel,
            search.onSearchLists,
        ),
        FloatingScannerAction(
            Res.drawable.scanner_content_copy,
            stringResource(Res.string.still_match_copy_short),
            controls.onCopy,
            enabled = controls.canCopy,
        ),
        FloatingScannerAction(
            Res.drawable.scanner_share,
            stringResource(
                if (controls.sharePreparing) Res.string.status_cancel else Res.string.scanner_share,
            ),
            if (controls.sharePreparing) controls.onCancelShare else controls.onShare,
            inProgress = controls.sharePreparing,
            accessibilityLabel = if (controls.sharePreparing) {
                stringResource(Res.string.scanner_cancel_share)
            } else {
                stringResource(Res.string.status_share_image)
            },
        ),
        FloatingScannerAction(
            icon = Res.drawable.scanner_photo_camera,
            label = stringResource(Res.string.scanner_camera),
            accessibilityLabel = stringResource(Res.string.capture_return_to_camera),
            onClick = {
                focus.clearFocus()
                keyboard?.hide()
                controls.onCamera()
            },
        ),
    )
}

internal fun floatingRetainedControls(
    inspection: StillInspectionState,
    resultsAvailable: Boolean,
    sharePreparing: Boolean,
    actions: FinderSourceActions,
): FloatingRetainedControls {
    val selected = inspection.selectedNavigationRow
    val first = inspection.effectiveRows.firstOrNull()
    val current = selected ?: first
    val canNavigate = resultsAvailable && current?.let { inspection.isActionCurrent(it) } == true
    return FloatingRetainedControls(
        zoom = inspection.transform.zoom,
        canZoom = inspection.fit?.isValid == true,
        position = inspection.selectedPosition,
        matchCount = inspection.effectiveRows.size,
        canNavigate = canNavigate,
        canCopy = selected?.let { inspection.isActionCurrent(it, requireLocateContext = false) }
            == true,
        sharePreparing = sharePreparing,
        onZoom = inspection::zoomBy,
        onPrevious = { inspection.locateAdjacent(-1) },
        onNext = {
            if (selected == null && first != null) {
                inspection.locate(first)
            } else {
                inspection.locateAdjacent(1)
            }
        },
        onCopy = { inspection.copySelected(StillMatchCopyPayload.MatchedText) },
        onShare = actions::shareStillImage,
        onCancelShare = actions::cancelShare,
        onCamera = actions::returnToCamera,
    )
}
