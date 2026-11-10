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
    query: String,
    onQueryChange: (String) -> Unit,
    searchListsLabel: String,
    onSearchLists: () -> Unit,
    activeListName: String? = null,
    activeListTermCount: Int = 0,
    matchMode: SearchMatchMode = SearchMatchMode.Substring,
    onTerms: () -> Unit = {},
    onUseManual: () -> Unit = {},
    searchContext: String? = null,
    onOptions: () -> Unit,
    saving: Boolean = false,
    savingLabel: String? = null,
    resultsLabel: String? = null,
    onResults: () -> Unit = {},
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
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(Res.string.finder_find_text)) },
                trailingIcon = if (activeListName != null) {
                    {
                        FinderSecondaryAction(
                            label = stringResource(Res.string.search_context_use_manual_short),
                            onClick = onUseManual,
                            accessibilityLabel = stringResource(
                                Res.string.search_context_use_manual,
                            ),
                        )
                    }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (activeListName == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
                supportingText = if (activeListName != null) {
                    { Text(stringResource(Res.string.search_context_manual_inactive)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            SearchContext(
                activeListName = activeListName,
                termCount = activeListTermCount,
                manualQuery = query,
                matchMode = matchMode,
                saving = saving,
                compact = false,
                onTerms = onTerms,
                onUseManual = onUseManual,
                contextOverride = searchContext,
                savingLabel = savingLabel,
                showUseManualAction = false,
                additionalActions = {
                    FinderSecondaryAction(searchListsLabel, onSearchLists)
                    resultsLabel?.let { label ->
                        FinderSecondaryAction(label, onResults)
                    }
                },
            )
        }
    }
}

/** Inline search bar for a grouped [search] bar; shown while the list manager is open. */
@Composable
internal fun FinderSearchControls(
    showTitle: Boolean,
    search: FinderSearchBar,
    resultsLabel: String?,
    onOptions: () -> Unit,
) {
    FinderSearchControls(
        showTitle = showTitle,
        query = search.query,
        onQueryChange = search.onQueryChange,
        searchListsLabel = search.searchListsLabel,
        onSearchLists = search.onSearchLists,
        activeListName = search.activeListName,
        activeListTermCount = search.activeListTermCount,
        matchMode = search.matchMode,
        onTerms = search.onTerms,
        onUseManual = search.onUseManual,
        onOptions = onOptions,
        saving = search.saving,
        resultsLabel = resultsLabel,
        onResults = search.onResults,
    )
}
