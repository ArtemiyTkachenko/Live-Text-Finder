package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.SearchMatchMode
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_match_substring
import livetextfinder.shared.generated.resources.finder_match_whole_words
import livetextfinder.shared.generated.resources.search_context_manual
import livetextfinder.shared.generated.resources.search_context_terms
import livetextfinder.shared.generated.resources.search_context_use_manual
import livetextfinder.shared.generated.resources.search_list_term_count
import livetextfinder.shared.generated.resources.status_saving
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SearchContext(
    activeListName: String?,
    termCount: Int,
    manualQuery: String,
    matchMode: SearchMatchMode,
    saving: Boolean,
    compact: Boolean,
    onTerms: () -> Unit,
    onUseManual: () -> Unit,
    contextOverride: String? = null,
    savingLabel: String? = null,
    additionalActions: @Composable FlowRowScope.() -> Unit = {},
    showUseManualAction: Boolean = true,
) {
    val context = contextOverride ?: if (activeListName != null) {
        activeListName
    } else {
        val manualQuerySuffix = manualQuery.takeIf {
            compact && it.isNotBlank()
        }?.let { query -> " · $query" }.orEmpty()
        stringResource(Res.string.search_context_manual) + manualQuerySuffix
    }
    val termCountLabel = activeListName?.let {
        pluralStringResource(Res.plurals.search_list_term_count, termCount, termCount)
    }
    val modeLabel = stringResource(
        if (matchMode == SearchMatchMode.Substring) {
            Res.string.finder_match_substring
        } else {
            Res.string.finder_match_whole_words
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context,
                modifier = Modifier.weight(1f),
                style = if (activeListName == null) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = if (activeListName == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            termCountLabel?.let { countLabel ->
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = modeLabel,
                    modifier = Modifier.padding(
                        horizontal = FinderSpacing.extraSmall,
                        vertical = 4.dp,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
        ) {
            FinderSecondaryAction(
                label = stringResource(Res.string.search_context_terms),
                onClick = onTerms,
            )
            additionalActions()
            if (activeListName != null && showUseManualAction) {
                FinderSecondaryAction(
                    label = stringResource(Res.string.search_context_use_manual),
                    onClick = onUseManual,
                )
            }
        }
        Text(
            text = savingLabel ?: if (saving) stringResource(Res.string.status_saving) else " ",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = if (saving) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
