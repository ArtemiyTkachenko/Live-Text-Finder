package com.artkachenko.livetextfinder.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.CameraPreview
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImagePreview
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.inspection.StillInspectionState
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaEditorOverlay
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaState
import com.artkachenko.livetextfinder.scanarea.ScanAreaBadge
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.ui.FinderCameraOverlay
import com.artkachenko.livetextfinder.ui.StillInspectionViewport

/**
 * The camera or retained-image viewport with everything layered on it: match outlines, the
 * scan-area editor and badge.
 *
 * The viewport reports its measured size through [viewportState] and the camera's recognition
 * state through [onOcrState]. The value parameters are what the caller's composition displayed;
 * the owners are passed so draw-time and tap-time checks can compare against current state.
 */
@Composable
internal fun FinderViewport(
    inspection: StillInspectionState,
    viewportState: MutableState<IntSize>,
    searchState: FinderSearchState,
    ocrState: OcrUiState,
    onOcrState: (OcrUiState) -> Unit,
    importedState: ImportedImageState,
    freezeState: FreezePresentationState,
    liveArea: LiveSearchAreaState,
    context: FinderSourceContext,
    areaEditing: Boolean,
    areaGestureEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val editorDraft = liveArea.editorDraft
    val editorToken = editorDraft?.token ?: 0L
    StillInspectionViewport(
        sourceIdentity = inspection.inspectionIdentity,
        fit = inspection.fit,
        viewportSize = viewportState.value,
        transform = inspection.transform,
        onTransformChange = { inspection.transform = it },
        modifier = modifier
            .background(FinderCameraOverlay.viewportBackground)
            .testTag("finder-image-viewport")
            .onSizeChanged { viewportState.value = it },
    ) {
        when (importedState.phase) {
            ImportedImagePhase.Imported -> ImportedImagePreview(
                context.importedOwner,
                Modifier.fillMaxSize(),
                context.isContentVisible,
            )
            ImportedImagePhase.Picking,
            ImportedImagePhase.Reading,
            ImportedImagePhase.Recognizing -> Unit
            else -> CameraPreview(
                Modifier.fillMaxSize(),
                context.frozenOwner,
                context.liveCaptureControls,
                context.isContentVisible,
                onOcrState,
            )
        }
        HighlightOverlay(
            suppressed = areaEditing,
            search = searchState.search,
            activeSearch = searchState.activeSearch,
            presentation = searchState.presentation,
            ocrState = ocrState,
            importedState = importedState,
            freezeState = freezeState,
            liveArea = liveArea,
            inspection = inspection,
            viewportState = viewportState,
            context = context,
            modifier = Modifier.matchParentSize(),
        )
        if (areaGestureEnabled) {
            LiveSearchAreaEditorOverlay(
                area = editorDraft?.area ?: NormalizedSearchArea(0f, 0f, 1f, 1f),
                viewportSize = viewportState.value,
                onAreaChange = { liveArea.updateEditor(editorToken, it) },
                modifier = Modifier.matchParentSize(),
            )
        }
        if (!areaEditing && liveArea.area != null &&
            liveArea.owner.revision == searchState.presentation.areaRevision &&
            liveArea.owner.area == liveArea.area &&
            importedState.phase != ImportedImagePhase.Imported &&
            context.importedOwner.state.value.phase != ImportedImagePhase.Imported &&
            context.frozenOwner.state.value.mode == FreezeMode.Live &&
            context.isContentVisible()
        ) {
            ScanAreaBadge(
                Modifier.align(Alignment.TopStart)
                    .safeDrawingPadding().padding(start = 4.dp, top = 56.dp),
            )
        }
    }
}
