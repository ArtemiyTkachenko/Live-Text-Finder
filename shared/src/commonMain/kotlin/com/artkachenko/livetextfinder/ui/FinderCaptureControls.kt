package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FinderCaptureControls(
    captureLabel: String?,
    captureEnabled: Boolean,
    onCapture: () -> Unit,
    pendingCaptureLabel: String?,
    onCancelCapture: () -> Unit,
    secondaryActionLabel: String?,
    onSecondaryAction: () -> Unit,
) {
    if (captureLabel == null && pendingCaptureLabel == null && secondaryActionLabel == null) {
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = FinderSpacing.screenMargin,
            vertical = FinderSpacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
    ) {
        captureLabel?.let { label ->
            FinderPrimaryAction(
                label = label,
                enabled = captureEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                onClick = onCapture,
            )
        }
        if (pendingCaptureLabel != null || secondaryActionLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
            ) {
                pendingCaptureLabel?.let { label ->
                    FinderSecondaryAction(label = label, onClick = onCancelCapture)
                }
                secondaryActionLabel?.let { label ->
                    FinderSecondaryAction(label = label, onClick = onSecondaryAction)
                }
            }
        }
    }
}

/** Capture controls for grouped [actions]. */
@Composable
internal fun FinderCaptureControls(actions: FinderCaptureActions) {
    FinderCaptureControls(
        captureLabel = actions.captureLabel,
        captureEnabled = actions.captureEnabled,
        onCapture = actions.onCapture,
        pendingCaptureLabel = actions.pendingCaptureLabel,
        onCancelCapture = actions.onCancelCapture,
        secondaryActionLabel = actions.secondaryActionLabel,
        onSecondaryAction = actions.onSecondaryAction,
    )
}
