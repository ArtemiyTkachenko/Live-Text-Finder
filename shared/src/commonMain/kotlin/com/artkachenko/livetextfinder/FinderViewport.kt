package com.artkachenko.livetextfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.CameraPreview
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImagePreview
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.ActiveSearchSpec
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.SearchPresentation
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.ui.FinderCameraOverlay
import com.artkachenko.livetextfinder.ui.FinderSpacing
import com.artkachenko.livetextfinder.ui.LiveCaptureControlRow
import com.artkachenko.livetextfinder.ui.StillInspectionViewport

/**
 * The camera or retained-image viewport with everything layered on it: match outlines, the
 * scan-area editor and badge, and the live capture controls.
 *
 * The viewport reports its measured size through [viewportState] and the camera's recognition
 * state through [onOcrState]. The value parameters are what the caller's composition displayed;
 * the owners are passed so draw-time and tap-time checks can compare against current state.
 */
@Composable
internal fun FinderViewport(
    inspection: StillInspectionState,
    viewportState: MutableState<IntSize>,
    search: TextSearchResult,
    activeSearch: ActiveSearchSpec,
    presentation: SearchPresentation,
    ocrState: OcrUiState,
    onOcrState: (OcrUiState) -> Unit,
    importedState: ImportedImageState,
    freezeState: FreezePresentationState,
    liveArea: LiveSearchAreaState,
    importedOwner: ImportedImageOwner,
    frozenOwner: FrozenFrameOwner,
    session: SearchSessionController,
    liveCaptureControls: LiveCaptureControls,
    isContentVisible: () -> Boolean,
    captureContextCurrent: () -> Boolean,
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
                importedOwner,
                Modifier.fillMaxSize(),
                isContentVisible,
            )
            ImportedImagePhase.Picking,
            ImportedImagePhase.Reading,
            ImportedImagePhase.Recognizing -> Unit
            else -> CameraPreview(
                Modifier.fillMaxSize(),
                frozenOwner,
                liveCaptureControls,
                isContentVisible,
                onOcrState,
            )
        }
        HighlightOverlay(
            suppressed = areaEditing,
            search = search,
            activeSearch = activeSearch,
            presentation = presentation,
            ocrState = ocrState,
            importedState = importedState,
            freezeState = freezeState,
            liveArea = liveArea,
            inspection = inspection,
            viewportState = viewportState,
            importedOwner = importedOwner,
            frozenOwner = frozenOwner,
            session = session,
            isContentVisible = isContentVisible,
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
            liveArea.owner.revision == presentation.areaRevision &&
            liveArea.owner.area == liveArea.area &&
            importedState.phase != ImportedImagePhase.Imported &&
            importedOwner.state.value.phase != ImportedImagePhase.Imported &&
            frozenOwner.state.value.mode == FreezeMode.Live &&
            isContentVisible()
        ) {
            ScanAreaBadge(Modifier.align(Alignment.TopStart))
        }
        if (inspection.source == null) {
            LiveCaptureControlRow(
                controls = liveCaptureControls,
                contextEnabled = captureContextCurrent(),
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(FinderSpacing.small),
            )
        }
    }
}
