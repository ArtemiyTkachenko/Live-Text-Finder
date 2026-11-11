package com.artkachenko.livetextfinder

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ImportedImagePicker
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.camera.allowsLiveCamera
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import com.artkachenko.livetextfinder.camera.rememberFrozenFrameOwner
import com.artkachenko.livetextfinder.camera.rememberImportedImageOwner
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
    /** True when the preview is shown: no list manager, compact layout or keyboard. */
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
 * because its visibility predicate uses the current density and insets, so do not use it as a
 * `remember` or effect key.
 */
@Composable
internal fun finderSourceContext(
    session: SearchSessionController,
    listManager: SearchListManagerState,
    liveArea: LiveSearchAreaState,
    rootSizeState: State<IntSize>,
): FinderSourceContext {
    val importedOwner = rememberImportedImageOwner()
    ImportedImagePicker(importedOwner)
    val frozenOwner = rememberFrozenFrameOwner()
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val isContentVisible = {
        val rootSize = rootSizeState.value
        val isCompact =
            (rootSize != IntSize.Zero &&
                rootSize.height / density.density < COMPACT_HEIGHT_DP) ||
                density.fontScale >= COMPACT_FONT_SCALE
        !listManager.isOpen && !isCompact && imeInsets.getBottom(density) == 0
    }
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

private const val COMPACT_HEIGHT_DP = 500f
private const val COMPACT_FONT_SCALE = 1.3f
