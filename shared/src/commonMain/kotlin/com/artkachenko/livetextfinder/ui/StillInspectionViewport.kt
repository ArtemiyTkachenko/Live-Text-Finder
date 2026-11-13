package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.StillViewTransform
import com.artkachenko.livetextfinder.search.PreviewPoint

/** Remembers view state only for one retained source in one measured viewport. */
@Composable
internal fun rememberStillViewTransform(
    sourceIdentity: String?,
    viewportSize: IntSize,
): MutableState<StillViewTransform> = remember(sourceIdentity, viewportSize) {
    mutableStateOf(StillViewTransform())
}

/** One clipped viewport applies a single transform to its complete still-image subtree. */
@Composable
internal fun StillInspectionViewport(
    sourceIdentity: String?,
    fit: FrozenFrameFit?,
    viewportSize: IntSize,
    transform: StillViewTransform,
    onTransformChange: (StillViewTransform) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentTransform by rememberUpdatedState(transform)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    Box(
        modifier = modifier
            .then(if (sourceIdentity != null) Modifier.clipToBounds() else Modifier)
            .pointerInput(sourceIdentity, fit, viewportSize) {
                if (sourceIdentity != null && fit?.isValid == true &&
                    viewportSize.width > 0 && viewportSize.height > 0
                ) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        currentOnTransformChange(
                            currentTransform.zoomBy(
                                factor = zoom,
                                centroid = PreviewPoint(centroid.x, centroid.y),
                                viewportWidth = viewportSize.width.toFloat(),
                                viewportHeight = viewportSize.height.toFloat(),
                                fit = fit,
                                panX = pan.x,
                                panY = pan.y,
                            ),
                        )
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier.matchParentSize().then(
                if (sourceIdentity != null) {
                    Modifier.graphicsLayer {
                        scaleX = if (currentTransform.isRenderable) currentTransform.zoom else 1f
                        scaleY = if (currentTransform.isRenderable) currentTransform.zoom else 1f
                        translationX = if (currentTransform.isRenderable) currentTransform.panX
                        else 0f
                        translationY = if (currentTransform.isRenderable) currentTransform.panY
                        else 0f
                    }
                } else {
                    Modifier
                },
            ),
        ) {
            content()
        }
    }
}
