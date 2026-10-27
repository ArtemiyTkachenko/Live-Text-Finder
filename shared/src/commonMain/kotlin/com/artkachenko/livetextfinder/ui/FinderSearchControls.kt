package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun FinderSearchControls(
    showTitle: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    searchListsLabel: String,
    onSearchLists: () -> Unit,
    searchContext: String,
    highlightLabel: String,
    highlightEnabled: Boolean,
    onHighlight: () -> Unit,
    onHelp: () -> Unit,
    captureLabel: String?,
    captureEnabled: Boolean,
    onCapture: () -> Unit,
    pendingCaptureLabel: String?,
    onCancelCapture: () -> Unit,
    liveAreaLabel: String?,
    onLiveArea: () -> Unit,
    secondaryActionLabel: String?,
    onSecondaryAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = FinderSpacing.small,
                vertical = FinderSpacing.extraSmall,
            ),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
        ) {
            if (showTitle) {
                Text(
                    "Live Text Finder",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = {
                    Text("Find in recognized text")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSearchLists) {
                    Text(searchListsLabel)
                }
                Text(
                    searchContext,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onHighlight,
                    enabled = highlightEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(highlightLabel)
                }
                TextButton(onClick = onHelp) {
                    Text("Help")
                }
            }
            when {
                captureLabel != null -> {
                    if (liveAreaLabel == null) {
                        FinderPrimaryAction(
                            label = captureLabel,
                            enabled = captureEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onCapture,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FinderPrimaryAction(
                                label = captureLabel,
                                enabled = captureEnabled,
                                modifier = Modifier.weight(1f),
                                onClick = onCapture,
                            )
                            TextButton(
                                onClick = onLiveArea,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(liveAreaLabel)
                            }
                        }
                    }
                }
                pendingCaptureLabel != null -> TextButton(onClick = onCancelCapture) {
                    Text(pendingCaptureLabel)
                }
            }
            if (secondaryActionLabel != null) {
                TextButton(onClick = onSecondaryAction) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}
