package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.capture_zoom_in
import livetextfinder.shared.generated.resources.capture_zoom_out
import livetextfinder.shared.generated.resources.scanner_chevron_left
import livetextfinder.shared.generated.resources.scanner_chevron_right
import livetextfinder.shared.generated.resources.scanner_match_position
import livetextfinder.shared.generated.resources.scanner_no_match_selected
import livetextfinder.shared.generated.resources.scanner_zoom_in
import livetextfinder.shared.generated.resources.scanner_zoom_out
import livetextfinder.shared.generated.resources.still_match_next
import livetextfinder.shared.generated.resources.still_match_previous
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A floating action with an accessible label and a 48dp round touch target. */
@Composable
internal fun FloatingScannerIconButton(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateLabel: String? = null,
) {
    ScannerIconButton(
        icon = icon,
        label = label,
        onClick = onClick,
        enabled = enabled,
        background = FinderCameraOverlay.controlScrim,
        modifier = modifier,
        stateLabel = stateLabel,
    )
}

/** A single-line status and entry point for the current search results. */
@Composable
internal fun FloatingScannerStatusPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ScannerPill(
        modifier = modifier,
        interaction = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = label,
            color = scannerControlContentColor(enabled),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** Display and interaction for a single scanner bar action. */
internal class FloatingScannerAction(
    val icon: DrawableResource,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val inProgress: Boolean = false,
    val accessibilityLabel: String? = null,
)

/** A scanner bar action with an icon and optional visible text. */
@Composable
internal fun FloatingScannerActionItem(
    action: FloatingScannerAction,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val accessibilityLabel = action.accessibilityLabel ?: action.label
    val targetDescription = if (showLabel) action.accessibilityLabel else accessibilityLabel
    val content: @Composable (Modifier) -> Unit = { itemModifier ->
        Column(
            modifier = itemModifier
                .widthIn(min = 48.dp)
                .heightIn(min = 48.dp)
                .clickable(enabled = action.enabled, role = Role.Button, onClick = action.onClick)
                .semantics {
                    if (targetDescription != null) {
                        contentDescription = targetDescription
                    }
                    if (action.inProgress) {
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    }
                }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            val contentColor = scannerControlContentColor(action.enabled)
            if (action.inProgress) {
                ScannerPendingIndicator(contentColor)
            } else {
                Icon(
                    painter = painterResource(action.icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor,
                )
            }
            if (showLabel) {
                Text(
                    text = action.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = if (action.accessibilityLabel != null) {
                        Modifier.clearAndSetSemantics {
                        }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
    if (showLabel) {
        content(modifier)
    } else {
        ScannerControlTooltip(accessibilityLabel, modifier) {
            content(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ScannerPendingIndicator(color: Color) {
    // Use a static indeterminate mark to keep the control legible in a still image.
    Canvas(Modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(strokeWidth, strokeWidth),
            size = size.copy(
                width = size.width - strokeWidth * 2,
                height = size.height - strokeWidth * 2,
            ),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

/** Four equal-width actions on a single floating surface. */
@Composable
internal fun FloatingScannerActionBar(
    actions: List<FloatingScannerAction>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    require(actions.size == 4)
    ScannerPill(modifier.fillMaxWidth()) {
        actions.forEach { action ->
            FloatingScannerActionItem(action, Modifier.weight(1f), showLabel = showLabels)
        }
    }
}

/** Zoom controls for the current preview. The caller supplies the zoom value and actions. */
@Composable
internal fun FloatingScannerZoomPill(
    zoomLabel: String,
    zoomOutEnabled: Boolean,
    zoomInEnabled: Boolean,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScannerPill(modifier) {
        ScannerIconButton(
            icon = Res.drawable.scanner_zoom_out,
            label = stringResource(Res.string.capture_zoom_out),
            onClick = onZoomOut,
            enabled = zoomOutEnabled,
            background = Color.Transparent,
        )
        ScannerPillReadout(zoomLabel)
        ScannerIconButton(
            icon = Res.drawable.scanner_zoom_in,
            label = stringResource(Res.string.capture_zoom_in),
            onClick = onZoomIn,
            enabled = zoomInEnabled,
            background = Color.Transparent,
        )
    }
}

/** Previous and next match controls for inspection of a retained image. */
@Composable
internal fun FloatingScannerMatchPill(
    position: Int,
    count: Int,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScannerPill(modifier) {
        ScannerIconButton(
            icon = Res.drawable.scanner_chevron_left,
            label = stringResource(Res.string.still_match_previous),
            onClick = onPrevious,
            enabled = previousEnabled,
            background = Color.Transparent,
        )
        ScannerPillReadout(
            if (position > 0) {
                stringResource(Res.string.scanner_match_position, position, count)
            } else {
                stringResource(Res.string.scanner_no_match_selected, count)
            },
        )
        ScannerIconButton(
            icon = Res.drawable.scanner_chevron_right,
            label = stringResource(Res.string.still_match_next),
            onClick = onNext,
            enabled = nextEnabled,
            background = Color.Transparent,
        )
    }
}

@Composable
private fun ScannerPill(
    modifier: Modifier,
    interaction: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(FinderCameraOverlay.controlScrim)
            .then(interaction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun ScannerPillReadout(label: String) {
    Text(
        text = label,
        color = FinderCameraOverlay.onControlScrim,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun ScannerIconButton(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    background: Color,
    modifier: Modifier = Modifier,
    stateLabel: String? = null,
) {
    Box(modifier) {
        ScannerControlTooltip(label) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(48.dp).semantics {
                    if (stateLabel != null) {
                        stateDescription = stateLabel
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = background,
                    contentColor = FinderCameraOverlay.onControlScrim,
                    disabledContainerColor = background,
                    disabledContentColor = scannerControlContentColor(enabled = false),
                ),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Anchors a plain tooltip to an icon control. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScannerControlTooltip(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = {
            PlainTooltip(
                containerColor = FinderCameraOverlay.controlScrim,
                contentColor = FinderCameraOverlay.onControlScrim,
            ) {
                Text(label)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        focusable = false,
        content = content,
    )
}

private fun scannerControlContentColor(enabled: Boolean): Color = if (enabled) {
    FinderCameraOverlay.onControlScrim
} else {
    FinderCameraOverlay.onControlScrim.copy(alpha = 0.5f)
}
