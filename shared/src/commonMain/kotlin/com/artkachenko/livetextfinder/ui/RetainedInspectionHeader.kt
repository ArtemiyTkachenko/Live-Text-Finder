package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_options
import livetextfinder.shared.generated.resources.inspection_done
import livetextfinder.shared.generated.resources.inspection_edit_search
import livetextfinder.shared.generated.resources.inspection_lists
import livetextfinder.shared.generated.resources.inspection_more
import livetextfinder.shared.generated.resources.inspection_results
import livetextfinder.shared.generated.resources.inspection_results_occurrences
import livetextfinder.shared.generated.resources.inspection_results_terms
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.status_saving
import livetextfinder.shared.generated.resources.status_share_image
import livetextfinder.shared.generated.resources.status_share_preparing
import org.jetbrains.compose.resources.stringResource

internal object RetainedInspectionActionDispatcher {
    fun toggleSearchEditing(
        expanded: Boolean,
        setExpanded: (Boolean) -> Unit,
        clearFocus: () -> Unit,
        hideKeyboard: () -> Unit,
    ) {
        if (expanded) {
            clearFocus()
            hideKeyboard()
        }
        setExpanded(!expanded)
    }

    fun toggleLists(
        managerOpen: Boolean,
        onContextChanged: () -> Unit,
        setManagerOpen: (Boolean) -> Unit,
        collapseSearch: () -> Unit,
        closeEditor: () -> Unit,
    ) {
        onContextChanged()
        setManagerOpen(!managerOpen)
        if (managerOpen) collapseSearch()
        closeEditor()
    }

    fun showTerms(
        setTermsOpen: (Boolean) -> Unit,
        setOccurrencesOpen: (Boolean) -> Unit,
    ) {
        setOccurrencesOpen(false)
        setTermsOpen(true)
    }

    fun showOccurrences(
        clearFocus: () -> Unit,
        hideKeyboard: () -> Unit,
        setTermsOpen: (Boolean) -> Unit,
        setOccurrencesOpen: (Boolean) -> Unit,
    ) {
        clearFocus()
        hideKeyboard()
        setTermsOpen(false)
        setOccurrencesOpen(true)
    }

    fun returnToSource(
        imported: Boolean,
        resetLiveArea: () -> Unit,
        cancelImport: () -> Unit,
        resumeFrozen: () -> Unit,
    ) {
        resetLiveArea()
        if (imported) cancelImport() else resumeFrozen()
    }
}

@Composable
internal fun RetainedInspectionHeader(
    searchContext: String,
    saving: Boolean,
    editing: Boolean,
    managerOpen: Boolean,
    returnLabel: String?,
    resultCount: Int,
    occurrenceCount: Int,
    sharePreparing: Boolean,
    onEditSearch: () -> Unit,
    onReturn: () -> Unit,
    onTerms: () -> Unit,
    onOccurrences: () -> Unit,
    onLists: () -> Unit,
    onOptions: () -> Unit,
    onShare: () -> Unit,
    onCancelShare: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    var resultsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
    ) {
        if (!editing) {
            Text(
                text = searchContext,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (saving) stringResource(Res.string.status_saving) else " ",
                style = MaterialTheme.typography.labelSmall,
                color = if (saving) MaterialTheme.colorScheme.onSurfaceVariant else
                    Color.Transparent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
        ) {
            if (!managerOpen) {
                TextButton(onClick = onEditSearch, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        stringResource(
                            if (editing) Res.string.inspection_done
                            else Res.string.inspection_edit_search,
                        ),
                    )
                }
            }
            returnLabel?.let { label ->
                TextButton(
                    onClick = onReturn,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(label)
                }
            }
            androidx.compose.foundation.layout.Box {
                TextButton(
                    onClick = { resultsExpanded = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.inspection_results, resultCount))
                }
                DropdownMenu(
                    expanded = resultsExpanded,
                    onDismissRequest = { resultsExpanded = false },
                ) {
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(stringResource(Res.string.inspection_results_terms)) },
                        onClick = {
                            resultsExpanded = false
                            onTerms()
                        },
                    )
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(stringResource(Res.string.inspection_results_occurrences)) },
                        enabled = occurrenceCount > 0,
                        onClick = {
                            resultsExpanded = false
                            onOccurrences()
                        },
                    )
                }
            }
            androidx.compose.foundation.layout.Box {
                TextButton(
                    onClick = { moreExpanded = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.inspection_more))
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false },
                ) {
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(stringResource(Res.string.inspection_lists)) },
                        onClick = {
                            moreExpanded = false
                            onLists()
                        },
                    )
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(stringResource(Res.string.finder_options)) },
                        onClick = {
                            moreExpanded = false
                            onOptions()
                        },
                    )
                    if (!sharePreparing) {
                        DropdownMenuItem(
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(stringResource(Res.string.status_share_image)) },
                            onClick = {
                                moreExpanded = false
                                onShare()
                            },
                        )
                    }
                }
            }
        }
        if (sharePreparing) {
            Row(horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall)) {
                Text(
                    text = stringResource(Res.string.status_share_preparing),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onCancelShare,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(Res.string.status_cancel))
                }
            }
        }
    }
}
