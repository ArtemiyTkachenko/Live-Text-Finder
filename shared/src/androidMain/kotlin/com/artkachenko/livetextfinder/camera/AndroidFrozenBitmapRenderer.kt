package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform

/** Draws the owned raw bitmap only while its frozen frame remains current and visible. */
internal fun DrawScope.drawFrozenBitmap(
    owner: FrozenFrameOwner,
    frame: FrozenSearchFrame,
    bitmap: Bitmap,
    isVisible: () -> Boolean,
) {
    if (!isVisible()) {
        return
    }
    val currentState = owner.state.value
    if (currentState.mode != FreezeMode.Frozen || currentState.frame !== frame ||
        owner.currentBitmap() !== bitmap
    ) {
        return
    }

    val frameInfo = frame.snapshot.frame
    if (bitmap.width != frameInfo.bufferWidth || bitmap.height != frameInfo.bufferHeight ||
        frameInfo.rotationDegrees !in VALID_ROTATIONS
    ) {
        return
    }
    val fit = FrozenFrameFit(
        rawWidth = bitmap.width,
        rawHeight = bitmap.height,
        rotationDegrees = frameInfo.rotationDegrees,
        viewportWidth = size.width,
        viewportHeight = size.height,
    )
    if (!fit.isValid || !isVisible()) {
        return
    }

    val center = Offset(size.width / 2f, size.height / 2f)
    withTransform({
        translate(center.x, center.y)
        rotate(frameInfo.rotationDegrees.toFloat(), Offset.Zero)
        scale(fit.scale, fit.scale, Offset.Zero)
        translate(-bitmap.width / 2f, -bitmap.height / 2f)
    }) {
        drawImage(bitmap.asImageBitmap())
    }
}

private val VALID_ROTATIONS = setOf(0, 90, 180, 270)
