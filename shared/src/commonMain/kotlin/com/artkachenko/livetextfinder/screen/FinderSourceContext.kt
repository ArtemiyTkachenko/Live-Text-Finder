package com.artkachenko.livetextfinder.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ImportedImagePicker
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.camera.allowsLiveCamera
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import com.artkachenko.livetextfinder.camera.rememberFrozenFrameOwner
import com.artkachenko.livetextfinder.camera.rememberImportedImageOwner
import com.artkachenko.livetextfinder.lists.SavedSearchEditingState
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaState
import com.artkachenko.livetextfinder.search.SearchSessionController

/**
 * The long-lived owners behind the displayed source, with predicates that read their state at
 * the moment they are called.
 *
 * Composables receive the values their composition displayed as ordinary parameters and use
 * this context to compare them with current state at draw or tap time.
 */
internal class FinderSourceContext(
    val session: SearchSessionController,
    val importedOwner: ImportedImageOwner,
    val frozenOwner: FrozenFrameOwner,
    val liveCaptureControls: LiveCaptureControls,
    /** True when the displayed source has a viewport. */
    val isContentVisible: () -> Boolean,
    /** True when live capture controls may act: live camera, visible and no area editing. */
    val isCaptureContextCurrent: () -> Boolean,
) {
    /** Returns whether the live camera is the visible source and supports a scan area. */
    fun isLiveAreaContextCurrent(): Boolean {
        val phase = importedOwner.state.value.phase
        return frozenOwner.state.value.mode == FreezeMode.Live &&
            phase.allowsLiveCamera &&
            isContentVisible() &&
            liveSearchAreaAvailable()
    }
}

/**
 * Creates the image owners and live capture controls and returns this composition's context.
 *
 * The owners and controls are remembered. The returned context is new on every recomposition
 * and captures the current manager state holder, so do not use it as a
 * `remember` or effect key.
 */
@Composable
internal fun finderSourceContext(
    session: SearchSessionController,
    searchEditing: SavedSearchEditingState,
    liveArea: LiveSearchAreaState,
): FinderSourceContext {
    val importedOwner = rememberImportedImageOwner()
    ImportedImagePicker(importedOwner)
    val frozenOwner = rememberFrozenFrameOwner()
    val isContentVisible = { !searchEditing.isOpen }
    val captureContextCurrent = {
        val phase = importedOwner.state.value.phase
        frozenOwner.state.value.mode == FreezeMode.Live &&
            phase.allowsLiveCamera &&
            liveArea.isIdle &&
            isContentVisible()
    }
    val captureSourceCurrent = {
        val phase = importedOwner.state.value.phase
        frozenOwner.state.value.mode != FreezeMode.Frozen &&
            phase.allowsLiveCamera &&
            isContentVisible()
    }
    val latestCaptureContext by rememberUpdatedState(captureContextCurrent)
    val latestCaptureSource by rememberUpdatedState(captureSourceCurrent)
    val liveCaptureControls = remember {
        LiveCaptureControls(
            isContextCurrent = { latestCaptureContext() },
            isCaptureContextCurrent = { latestCaptureSource() },
        )
    }
    return FinderSourceContext(
        session = session,
        importedOwner = importedOwner,
        frozenOwner = frozenOwner,
        liveCaptureControls = liveCaptureControls,
        isContentVisible = isContentVisible,
        isCaptureContextCurrent = captureContextCurrent,
    )
}
