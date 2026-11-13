package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.SearchTermSummary
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_context_count_unavailable
import livetextfinder.shared.generated.resources.search_result_count_description
import livetextfinder.shared.generated.resources.search_results_done
import livetextfinder.shared.generated.resources.search_results_locate_hint
import livetextfinder.shared.generated.resources.search_results_matched_terms
import livetextfinder.shared.generated.resources.search_results_occurrences
import livetextfinder.shared.generated.resources.search_results_title
import livetextfinder.shared.generated.resources.search_results_unavailable_explanation
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SearchTermResultsDialog(
    terms: List<SearchTermSummary>,
    resultsAvailable: Boolean = true,
    unavailableExplanation: String? = null,
    canLocate: Boolean = true,
    onSelect: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.search_results_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (resultsAvailable) {
                    val occurrenceTotal = terms.sumOf(SearchTermSummary::occurrenceCount)
                    val matchedTermCount = terms.count { term -> term.occurrenceCount > 0 }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(FinderSpacing.large),
                            verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                        ) {
                            Text(
                                text = stringResource(Res.string.search_results_occurrences),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = occurrenceTotal.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = pluralStringResource(
                                    Res.plurals.search_results_matched_terms,
                                    matchedTermCount,
                                    matchedTermCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!resultsAvailable) {
                    Text(
                        unavailableExplanation
                            ?: stringResource(Res.string.search_results_unavailable_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (resultsAvailable && !canLocate) {
                    Text(
                        stringResource(Res.string.search_results_locate_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 220.dp)
                        .semantics { selectableGroup() },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(terms, key = SearchTermSummary::termId) { term ->
                        val countDescription = stringResource(
                            Res.string.search_result_count_description,
                        )
                        val positive = resultsAvailable && term.occurrenceCount > 0
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (positive) MaterialTheme.colorScheme.surfaceContainer
                                    else MaterialTheme.colorScheme.surface,
                                )
                                .selectable(
                                    selected = false,
                                    enabled = resultsAvailable && canLocate &&
                                        term.occurrenceCount > 0,
                                    role = Role.Button,
                                    onClick = { onSelect(term.termId) },
                                )
                                .padding(
                                    horizontal = FinderSpacing.medium,
                                    vertical = FinderSpacing.small,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp)
                                    .background(
                                        term.color.composeColor,
                                        MaterialTheme.shapes.extraSmall,
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.extraSmall,
                                    ),
                            )
                            Text(
                                term.label,
                                modifier = Modifier.weight(1f),
                                style = if (positive) MaterialTheme.typography.bodyLarge
                                else MaterialTheme.typography.bodyMedium,
                                color = if (positive) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                color = if (positive) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = if (resultsAvailable) term.occurrenceCount.toString()
                                    else stringResource(
                                        Res.string.search_context_count_unavailable,
                                    ),
                                    modifier = Modifier.semantics {
                                        if (resultsAvailable) {
                                            contentDescription =
                                                "$countDescription ${term.occurrenceCount}"
                                        }
                                    }.padding(
                                        horizontal = FinderSpacing.small,
                                        vertical = FinderSpacing.extraSmall,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (positive) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FinderSecondaryAction(
                label = stringResource(Res.string.search_results_done),
                onClick = onDismiss,
            )
        },
    )
}
