package com.artkachenko.livetextfinder.screen

import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.ImageShareOwner
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.inspection.shareStillImage
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.isCameraRunning
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaState
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.ui.RetainedInspectionActionDispatcher

/**
 * The user actions that change the displayed source: freeze and resume, import, return to the
 * camera, share, and starting a scan-area edit.
 *
 * A new instance is created on every recomposition. [importedState], [freezeState], [ocrState]
 * and [search] are what that composition displayed and decide which action a control performs;
 * [context] supplies current owner state where an action must recheck it. Every source change
 * resets the live scan area first.
 */
internal class FinderSourceActions(
    private val context: FinderSourceContext,
    private val liveArea: LiveSearchAreaState,
    private val shareOwner: ImageShareOwner,
    private val importedState: ImportedImageState,
    private val freezeState: FreezePresentationState,
    private val ocrState: OcrUiState,
    private val search: TextSearchResult,
) {
    /** True when the capture button may be used for the displayed freeze mode. */
    val captureEnabled: Boolean = freezeState.mode != FreezeMode.Live ||
        !context.liveCaptureControls.state.value.zoomPending && ocrState.status.isCameraRunning

    /** Freezes the live frame or resumes the camera from a frozen frame. */
    fun capture() {
        when (freezeState.mode) {
            FreezeMode.Live -> {
                liveArea.reset()
                context.frozenOwner.freeze(
                    cameraReady = !context.liveCaptureControls.state.value.zoomPending &&
                        context.isCaptureContextCurrent() &&
                        ocrState.status.isCameraRunning,
                )
            }
            FreezeMode.Frozen -> {
                liveArea.reset()
                context.frozenOwner.resume()
            }
            FreezeMode.Freezing -> Unit
        }
    }

    fun cancelCapture() {
        context.frozenOwner.cancel()
    }

    /** Leaves an imported image or cancels an import that is still in progress. */
    fun leaveOrCancelImport() {
        when (importedState.phase) {
            ImportedImagePhase.Imported -> {
                liveArea.reset()
                context.importedOwner.cancel()
            }
            ImportedImagePhase.Picking,
            ImportedImagePhase.Reading,
            ImportedImagePhase.Recognizing -> context.importedOwner.cancel()
            ImportedImagePhase.Unsupported,
            ImportedImagePhase.Live,
            ImportedImagePhase.Error -> Unit
        }
    }

    /** Returns from the retained image to the live camera. */
    fun returnToCamera() {
        RetainedInspectionActionDispatcher.returnToSource(
            imported = importedState.phase == ImportedImagePhase.Imported,
            resetLiveArea = liveArea::reset,
            cancelImport = context.importedOwner::cancel,
            resumeFrozen = context.frozenOwner::resume,
        )
    }

    /** Opens the system image picker, discarding any frozen frame first. */
    fun beginImport() {
        liveArea.reset()
        if (freezeState.mode == FreezeMode.Freezing) {
            context.frozenOwner.cancel()
        }
        context.frozenOwner.resume()
        context.importedOwner.beginPicker()
    }

    fun shareStillImage() {
        shareOwner.shareStillImage(
            imported = importedState.phase == ImportedImagePhase.Imported,
            importedOwner = context.importedOwner,
            frozenOwner = context.frozenOwner,
            session = context.session,
            search = search,
        )
    }

    fun cancelShare() {
        shareOwner.cancel()
    }

    /** Edits the scan area on the preview when the live camera is usable, else in the dialog. */
    fun startAreaEditing() {
        liveArea.startEditing(
            onPreview = !context.liveCaptureControls.state.value.zoomPending &&
                context.isLiveAreaContextCurrent(),
        )
    }
}
