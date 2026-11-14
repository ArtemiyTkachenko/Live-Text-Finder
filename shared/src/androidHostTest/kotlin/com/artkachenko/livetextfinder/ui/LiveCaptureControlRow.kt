package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import kotlin.math.roundToInt
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.capture_focus
import livetextfinder.shared.generated.resources.capture_light
import livetextfinder.shared.generated.resources.capture_light_off
import livetextfinder.shared.generated.resources.capture_light_on
import livetextfinder.shared.generated.resources.capture_zoom_in
import livetextfinder.shared.generated.resources.capture_zoom_out
import org.jetbrains.compose.resources.stringResource

/** Retains supported controls during transient stream changes without moving the primary dock. */
@Composable
internal fun LiveCaptureControlRow(
    controls: LiveCaptureControls,
    contextEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = controls.state.value
    if (!state.hasControls) return
    val lease = state.lease
    val enabled = contextEnabled && state.ready && !state.zoomPending
    val lightDescription = stringResource(
        if (state.torchOn) Res.string.capture_light_on else Res.string.capture_light_off,
    )
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.hasZoom) {
                CaptureControlButton(
                    "−", stringResource(Res.string.capture_zoom_out),
                    enabled && state.zoom > state.minZoom,
                ) { controls.zoomOut(lease) }
                Text(
                    "${(state.zoom * 10).roundToInt() / 10f}×",
                    style = MaterialTheme.typography.labelLarge,
                )
                CaptureControlButton(
                    "+", stringResource(Res.string.capture_zoom_in),
                    enabled && state.zoom < state.maxZoom,
                ) { controls.zoomIn(lease) }
            }
            if (state.hasFlash) {
                TextButton(
                    onClick = { controls.toggleTorch(lease) },
                    enabled = enabled && !state.torchPending,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = lightDescription
                            role = Role.Switch
                            toggleableState = if (state.torchOn) {
                                ToggleableState.On
                            } else {
                                ToggleableState.Off
                            }
                        },
                ) {
                    Text(stringResource(Res.string.capture_light))
                }
            }
            if (state.hasFocus) {
                val label = stringResource(Res.string.capture_focus)
                CaptureControlButton(label, label, enabled && !state.focusing) {
                    controls.focusCenter(lease)
                }
            }
        }
    }
}

@Composable
private fun CaptureControlButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(label)
    }
}
