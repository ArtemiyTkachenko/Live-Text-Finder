package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.SearchMatchMode
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.inspection_lists
import livetextfinder.shared.generated.resources.inspection_more
import livetextfinder.shared.generated.resources.search_context_list_identity
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.status_share_image
import livetextfinder.shared.generated.resources.status_share_preparing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FinderLiveDock(
    query: String,
    onQueryChange: (String) -> Unit,
    activeListName: String?,
    activeListTermCount: Int,
    matchMode: SearchMatchMode,
    searchListsLabel: String,
    onSearchLists: () -> Unit,
    onTerms: () -> Unit,
    onUseManual: () -> Unit,
    onResults: () -> Unit,
    onOccurrences: () -> Unit = onResults,
    resultsLabel: String?,
    occurrencesEnabled: Boolean = true,
    saving: Boolean,
    captureLabel: String?,
    captureEnabled: Boolean,
    onCapture: () -> Unit,
    pendingCaptureLabel: String?,
    onCancelCapture: () -> Unit,
    secondaryActionLabel: String?,
    onSecondaryAction: () -> Unit,
    returnLabel: String? = null,
    onReturn: () -> Unit = {},
    onShare: () -> Unit = {},
    onCancelShare: () -> Unit,
    sharePreparing: Boolean = false,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = FinderSpacing.large)
                .padding(top = FinderSpacing.small, bottom = FinderSpacing.extraSmall),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Find in recognized text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            SearchContext(
                activeListName = activeListName,
                termCount = activeListTermCount,
                manualQuery = "",
                matchMode = matchMode,
                saving = saving,
                compact = true,
                onTerms = onTerms,
                onUseManual = onUseManual,
                contextOverride = activeListName?.let {
                    stringResource(Res.string.search_context_list_identity, it)
                },
                additionalActions = {
                    resultsLabel?.let {
                        FinderSecondaryAction(
                            it,
                            if (returnLabel != null) onOccurrences else onResults,
                            enabled = returnLabel == null || occurrencesEnabled,
                        )
                    }
                    if (returnLabel != null) {
                        androidx.compose.foundation.layout.Box {
                            FinderSecondaryAction(
                                stringResource(Res.string.inspection_more),
                                { moreExpanded = true },
                            )
                            DropdownMenu(
                                expanded = moreExpanded,
                                onDismissRequest = { moreExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(searchListsLabel) },
                                    onClick = {
                                        moreExpanded = false
                                        onSearchLists()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (sharePreparing) {
                                                    Res.string.status_share_preparing
                                                } else {
                                                    Res.string.status_share_image
                                                },
                                            ),
                                        )
                                    },
                                    enabled = !sharePreparing,
                                    onClick = {
                                        moreExpanded = false
                                        onShare()
                                    },
                                )
                            }
                        }
                    } else {
                        FinderSecondaryAction(searchListsLabel, onSearchLists)
                    }
                },
                showUseManualAction = activeListName != null,
            )
            if (sharePreparing) {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                ) {
                    Text(
                        stringResource(Res.string.status_share_preparing),
                    )
                    FinderSecondaryAction(
                        label = stringResource(Res.string.status_cancel),
                        onClick = onCancelShare,
                    )
                }
            }
            when {
                returnLabel != null -> FinderPrimaryAction(
                    label = returnLabel,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = onReturn,
                )
                captureLabel != null -> FinderPrimaryAction(
                    label = captureLabel,
                    enabled = captureEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = onCapture,
                )
                pendingCaptureLabel != null -> FinderSecondaryAction(
                    label = pendingCaptureLabel,
                    onClick = onCancelCapture,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )
                secondaryActionLabel != null -> FinderSecondaryAction(
                    label = secondaryActionLabel,
                    onClick = onSecondaryAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                )
                else -> Spacer(Modifier.fillMaxWidth().heightIn(min = 52.dp))
            }
        }
    }
}
