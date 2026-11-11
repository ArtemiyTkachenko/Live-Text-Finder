package com.artkachenko.livetextfinder

import androidx.compose.runtime.Composable
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.allowsLiveCamera
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.capture_cancel_freeze
import livetextfinder.shared.generated.resources.capture_cancel_import
import livetextfinder.shared.generated.resources.capture_freeze
import livetextfinder.shared.generated.resources.capture_resume
import livetextfinder.shared.generated.resources.capture_return_to_camera
import livetextfinder.shared.generated.resources.inspection_lists
import livetextfinder.shared.generated.resources.inspection_results
import livetextfinder.shared.generated.resources.inspection_return_frozen
import livetextfinder.shared.generated.resources.scan_area_option_custom
import livetextfinder.shared.generated.resources.scan_area_option_full_frame
import livetextfinder.shared.generated.resources.search_lists_close
import livetextfinder.shared.generated.resources.search_results_unavailable
import org.jetbrains.compose.resources.stringResource

/** The state-dependent labels of the main screen; a null label hides its control. */
internal class FinderLabels(
    /** Freeze or resume, on the capture button. */
    val capture: String?,
    /** Cancels a freeze that is still pending. */
    val pendingCapture: String?,
    /** Leaves an imported image or cancels a running import. */
    val secondaryAction: String?,
    /** Returns from a retained image to the camera. */
    val returnToCamera: String?,
    /** The scan-area entry in the options sheet. */
    val liveArea: String?,
    val searchLists: String,
    /** The match count or its unavailable notice, when there is something to search for. */
    val results: String?,
)

/** Resolves the main screen's labels for the displayed source and search. */
@Composable
internal fun finderLabels(
    importedPhase: ImportedImagePhase,
    freezeMode: FreezeMode,
    stillSource: StillSource?,
    areaSelected: Boolean,
    managerOpen: Boolean,
    hasSearch: Boolean,
    resultsAvailable: Boolean,
    occurrenceCount: Int,
): FinderLabels {
    val hasLiveCaptureControls = importedPhase.allowsLiveCamera
    val capture = if (!hasLiveCaptureControls) {
        null
    } else {
        when (freezeMode) {
            FreezeMode.Live -> stringResource(Res.string.capture_freeze)
            FreezeMode.Freezing -> null
            FreezeMode.Frozen -> stringResource(Res.string.capture_resume)
        }
    }
    val pendingCapture = if (hasLiveCaptureControls && freezeMode == FreezeMode.Freezing) {
        stringResource(Res.string.capture_cancel_freeze)
    } else {
        null
    }
    val secondaryAction = when (importedPhase) {
        ImportedImagePhase.Imported -> stringResource(Res.string.capture_return_to_camera)
        ImportedImagePhase.Picking,
        ImportedImagePhase.Reading,
        ImportedImagePhase.Recognizing -> stringResource(Res.string.capture_cancel_import)
        ImportedImagePhase.Unsupported,
        ImportedImagePhase.Live,
        ImportedImagePhase.Error -> null
    }
    val returnToCamera = when (stillSource) {
        is StillSource.Imported -> stringResource(Res.string.capture_return_to_camera)
        is StillSource.Frozen -> stringResource(Res.string.inspection_return_frozen)
        null -> null
    }
    val liveArea = if (
        hasLiveCaptureControls && freezeMode == FreezeMode.Live && liveSearchAreaAvailable()
    ) {
        if (areaSelected) {
            stringResource(Res.string.scan_area_option_custom)
        } else {
            stringResource(Res.string.scan_area_option_full_frame)
        }
    } else {
        null
    }
    val searchLists = if (managerOpen) {
        stringResource(Res.string.search_lists_close)
    } else {
        stringResource(Res.string.inspection_lists)
    }
    val resultsSummary = if (resultsAvailable) {
        stringResource(Res.string.inspection_results, occurrenceCount)
    } else {
        stringResource(Res.string.search_results_unavailable)
    }
    return FinderLabels(
        capture = capture,
        pendingCapture = pendingCapture,
        secondaryAction = secondaryAction,
        returnToCamera = returnToCamera,
        liveArea = liveArea,
        searchLists = searchLists,
        results = resultsSummary.takeIf { hasSearch },
    )
}
