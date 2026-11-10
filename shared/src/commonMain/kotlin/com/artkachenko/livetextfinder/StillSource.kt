package com.artkachenko.livetextfinder

import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.FrozenSearchFrame
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot

/** The retained still image displayed instead of the live camera preview. */
internal sealed class StillSource {
    /** Identifies the retained source for search, share and copy validation. */
    abstract val identity: String

    /** Keys inspection state, such as zoom and selection, to one retained source request. */
    abstract val inspectionIdentity: String

    /** The owner-held instance; compared by reference to reject a replaced source. */
    abstract val ownerToken: Any?

    /** Fits the upright source into [viewport], or returns null without retained content. */
    abstract fun fit(viewport: IntSize): FrozenFrameFit?

    /** An image chosen through the system picker. */
    class Imported(val snapshot: ImportedTextSnapshot?) : StillSource() {
        override val identity: String = importedSourceIdentity(snapshot)
        override val inspectionIdentity: String get() = identity
        override val ownerToken: Any? get() = snapshot

        override fun fit(viewport: IntSize): FrozenFrameFit? =
            snapshot?.let { content ->
                FrozenFrameFit(
                    content.width,
                    content.height,
                    0,
                    viewport.width.toFloat(),
                    viewport.height.toFloat(),
                )
            }
    }

    /** A camera frame retained by Freeze. */
    class Frozen(val frame: FrozenSearchFrame?) : StillSource() {
        override val identity: String = frozenSourceIdentity(frame)
        override val inspectionIdentity: String get() = "$identity:${frame?.requestId}"
        override val ownerToken: Any? get() = frame

        override fun fit(viewport: IntSize): FrozenFrameFit? =
            frame?.snapshot?.frame?.let { info ->
                FrozenFrameFit(
                    info.bufferWidth,
                    info.bufferHeight,
                    info.rotationDegrees,
                    viewport.width.toFloat(),
                    viewport.height.toFloat(),
                )
            }
    }
}

/** Returns the source identity used for an imported image. */
internal fun importedSourceIdentity(snapshot: ImportedTextSnapshot?): String =
    "import:${snapshot?.sourceIdentity}"

/** Returns the source identity used for a frozen camera frame. */
internal fun frozenSourceIdentity(frame: FrozenSearchFrame?): String =
    "frozen:${frame?.identity}"

/** Resolves the displayed still source; an imported image takes priority over a frozen frame. */
internal fun stillSourceOf(
    importedState: ImportedImageState,
    freezeState: FreezePresentationState,
): StillSource? = when {
    importedState.phase == ImportedImagePhase.Imported ->
        StillSource.Imported(importedState.snapshot)
    freezeState.mode == FreezeMode.Frozen -> StillSource.Frozen(freezeState.frame)
    else -> null
}
