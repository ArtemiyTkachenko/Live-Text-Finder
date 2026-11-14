package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_find_text
import livetextfinder.shared.generated.resources.inspection_more
import livetextfinder.shared.generated.resources.search_context_list_identity
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.status_share_image
import livetextfinder.shared.generated.resources.status_share_preparing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FinderLiveDock(
    search: FinderSearchBar,
    capture: FinderCaptureActions,
    resultsLabel: String?,
    onCancelShare: () -> Unit,
    onOccurrences: () -> Unit = search.onResults,
    occurrencesEnabled: Boolean = true,
    returnLabel: String? = null,
    onReturn: () -> Unit = {},
    onShare: () -> Unit = {},
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
                value = search.query,
                onValueChange = search.onQueryChange,
                label = { Text(stringResource(Res.string.finder_find_text)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            SearchContext(
                activeListName = search.activeListName,
                termCount = search.activeListTermCount,
                manualQuery = "",
                matchMode = search.matchMode,
                saving = search.saving,
                compact = true,
                onTerms = search.onTerms,
                onUseManual = search.onUseManual,
                contextOverride = search.activeListName?.let {
                    stringResource(Res.string.search_context_list_identity, it)
                },
                additionalActions = {
                    resultsLabel?.let {
                        FinderSecondaryAction(
                            it,
                            if (returnLabel != null) onOccurrences else search.onResults,
                            enabled = returnLabel == null || occurrencesEnabled,
                        )
                    }
                    if (returnLabel != null) {
                        Box {
                            FinderSecondaryAction(
                                stringResource(Res.string.inspection_more),
                                { moreExpanded = true },
                            )
                            DropdownMenu(
                                expanded = moreExpanded,
                                onDismissRequest = { moreExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(search.searchListsLabel) },
                                    onClick = {
                                        moreExpanded = false
                                        search.onSearchLists()
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
                        FinderSecondaryAction(search.searchListsLabel, search.onSearchLists)
                    }
                },
                showUseManualAction = search.activeListName != null,
            )
            if (sharePreparing) {
                Row(
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
            // FinderPrimaryAction adds its own bottom inset. The other alternatives reserve the
            // same space, so the dock keeps one height when the action changes.
            val actionSlot = Modifier.fillMaxWidth()
                .padding(bottom = FinderSpacing.large)
                .heightIn(min = 52.dp)
            when {
                returnLabel != null -> FinderPrimaryAction(
                    label = returnLabel,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = onReturn,
                )
                capture.captureLabel != null -> FinderPrimaryAction(
                    label = capture.captureLabel,
                    enabled = capture.captureEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = capture.onCapture,
                )
                capture.pendingCaptureLabel != null -> FinderSecondaryAction(
                    label = capture.pendingCaptureLabel,
                    onClick = capture.onCancelCapture,
                    modifier = actionSlot,
                )
                capture.secondaryActionLabel != null -> FinderSecondaryAction(
                    label = capture.secondaryActionLabel,
                    onClick = capture.onSecondaryAction,
                    modifier = actionSlot,
                )
                else -> Spacer(actionSlot)
            }
        }
    }
}
