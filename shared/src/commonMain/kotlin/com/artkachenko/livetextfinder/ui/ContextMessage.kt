package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ContextMessage(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    floating: Boolean = false,
) {
    val foreground = if (floating) {
        FinderCameraOverlay.onControlScrim
    } else if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (floating) {
            FinderCameraOverlay.controlScrim
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = FinderSpacing.medium,
                vertical = FinderSpacing.small,
            ),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.small),
        ) {
            Text(
                text = message,
                color = foreground,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null && onAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onAction,
                        colors = if (floating) {
                            ButtonDefaults.textButtonColors(contentColor = foreground)
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                    ) { Text(actionLabel) }
                }
            }
        }
    }
}
