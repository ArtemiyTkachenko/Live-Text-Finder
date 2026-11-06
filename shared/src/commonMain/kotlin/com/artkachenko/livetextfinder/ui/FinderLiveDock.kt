package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.SearchMatchMode
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_context_list_identity
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
    resultsLabel: String?,
    saving: Boolean,
    captureLabel: String?,
    captureEnabled: Boolean,
    onCapture: () -> Unit,
    pendingCaptureLabel: String?,
    onCancelCapture: () -> Unit,
    secondaryActionLabel: String?,
    onSecondaryAction: () -> Unit,
) {
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
            if (activeListName == null) {
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
                    activeListName = null,
                    termCount = 0,
                    manualQuery = "",
                    matchMode = matchMode,
                    saving = saving,
                    compact = true,
                    onTerms = onTerms,
                    onUseManual = onUseManual,
                    additionalActions = {
                        FinderSecondaryAction(searchListsLabel, onSearchLists)
                        resultsLabel?.let { FinderSecondaryAction(it, onResults) }
                    },
                    showUseManualAction = false,
                )
            } else {
                SearchContext(
                    activeListName = activeListName,
                    termCount = activeListTermCount,
                    manualQuery = query,
                    matchMode = matchMode,
                    saving = saving,
                    compact = false,
                    onTerms = onTerms,
                    onUseManual = onUseManual,
                    contextOverride = stringResource(
                        Res.string.search_context_list_identity,
                        activeListName,
                    ),
                    additionalActions = {
                        FinderSecondaryAction(searchListsLabel, onSearchLists)
                        resultsLabel?.let { FinderSecondaryAction(it, onResults) }
                    },
                    showUseManualAction = true,
                )
            }
            when {
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
