package com.artkachenko.livetextfinder.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colours and dimensions for content drawn over camera imagery.
 *
 * These are fixed rather than taken from the Material colour scheme, so guides and labels stay
 * legible over any scene in both light and dark themes.
 */
internal object FinderCameraOverlay {
    /** Fills the viewport behind a letterboxed preview or retained image. */
    val viewportBackground: Color = Color.Black

    /** Scan-area border and handle foreground. */
    val guide: Color = Color.White

    /** Drawn under [guide] so the border stays visible on bright scenes. */
    val guideUnderlay: Color = Color.Black

    /** Background for a text label placed directly on the preview. */
    val labelScrim: Color = Color.Black.copy(alpha = 0.7f)

    /** Text colour on [labelScrim]. */
    val onLabelScrim: Color = Color.White

    /** Shared surface and foreground for floating scanner controls. */
    val controlScrim: Color = Color.Black.copy(alpha = 0.6f)
    val onControlScrim: Color = Color.White

    /** Inverted surface for the selected Whole words toggle. */
    val selectedControlContainer: Color = Color.White
    val onSelectedControl: Color = Color.Black

    val guideStrokeWidth: Dp = 3.dp
    val guideUnderlayStrokeWidth: Dp = 5.dp
    val guideDashLength: Dp = 10.dp
    val guideGapLength: Dp = 6.dp
    val handleRadius: Dp = 10.dp
    val handleCoreRadius: Dp = 7.dp
    val labelPaddingHorizontal: Dp = 6.dp
    val labelPaddingVertical: Dp = 3.dp
}

/** Returns the dashed stroke used for the scan-area border. */
internal fun Density.scanAreaGuideStroke(
    width: Dp = FinderCameraOverlay.guideStrokeWidth,
): Stroke = Stroke(
    width = width.toPx(),
    pathEffect = PathEffect.dashPathEffect(
        floatArrayOf(
            FinderCameraOverlay.guideDashLength.toPx(),
            FinderCameraOverlay.guideGapLength.toPx(),
        ),
    ),
)
