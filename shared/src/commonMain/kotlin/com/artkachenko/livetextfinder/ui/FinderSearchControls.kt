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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.artkachenko.livetextfinder.search.SearchMatchMode
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_find_text
import livetextfinder.shared.generated.resources.finder_options
import livetextfinder.shared.generated.resources.search_context_manual_inactive
import livetextfinder.shared.generated.resources.search_context_use_manual
import livetextfinder.shared.generated.resources.search_context_use_manual_short
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FinderSearchControls(
    showTitle: Boolean,
    search: FinderSearchBar,
    onOptions: () -> Unit,
    resultsLabel: String? = null,
    searchContext: String? = null,
    savingLabel: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = FinderSpacing.large,
                vertical = FinderSpacing.extraSmall,
            ),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showTitle) {
                    Text(
                        text = "Live Text Finder",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FinderSecondaryAction(
                    label = stringResource(Res.string.finder_options),
                    onClick = onOptions,
                )
            }
            OutlinedTextField(
                value = search.query,
                onValueChange = search.onQueryChange,
                label = { Text(stringResource(Res.string.finder_find_text)) },
                trailingIcon = if (search.activeListName != null) {
                    {
                        FinderSecondaryAction(
                            label = stringResource(Res.string.search_context_use_manual_short),
                            onClick = search.onUseManual,
                            accessibilityLabel = stringResource(
                                Res.string.search_context_use_manual,
                            ),
                        )
                    }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (search.activeListName == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
                supportingText = if (search.activeListName != null) {
                    { Text(stringResource(Res.string.search_context_manual_inactive)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            SearchContext(
                activeListName = search.activeListName,
                termCount = search.activeListTermCount,
                manualQuery = search.query,
                matchMode = search.matchMode,
                saving = search.saving,
                compact = false,
                onTerms = search.onTerms,
                onUseManual = search.onUseManual,
                contextOverride = searchContext,
                savingLabel = savingLabel,
                showUseManualAction = false,
                additionalActions = {
                    FinderSecondaryAction(search.searchListsLabel, search.onSearchLists)
                    resultsLabel?.let { label ->
                        FinderSecondaryAction(label, search.onResults)
                    }
                },
            )
        }
    }
}
