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
internal fun FinderCaptureControls(actions: FinderCaptureActions) {
    if (actions.captureLabel == null && actions.pendingCaptureLabel == null &&
        actions.secondaryActionLabel == null
    ) {
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = FinderSpacing.screenMargin,
            vertical = FinderSpacing.small,
        ),
        verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
    ) {
        actions.captureLabel?.let { label ->
            FinderPrimaryAction(
                label = label,
                enabled = actions.captureEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                onClick = actions.onCapture,
            )
        }
        if (actions.pendingCaptureLabel != null || actions.secondaryActionLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
            ) {
                actions.pendingCaptureLabel?.let { label ->
                    FinderSecondaryAction(label = label, onClick = actions.onCancelCapture)
                }
                actions.secondaryActionLabel?.let { label ->
                    FinderSecondaryAction(label = label, onClick = actions.onSecondaryAction)
                }
            }
        }
    }
}
