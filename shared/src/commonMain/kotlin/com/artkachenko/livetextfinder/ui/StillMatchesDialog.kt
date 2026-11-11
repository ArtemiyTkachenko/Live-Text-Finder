package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.StillMatchRow
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_results_locate_hint
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.status_match_count
import livetextfinder.shared.generated.resources.still_copy_line
import livetextfinder.shared.generated.resources.still_copy_match
import livetextfinder.shared.generated.resources.still_match_show
import livetextfinder.shared.generated.resources.still_matches_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StillMatchesDialog(
    rows: List<StillMatchRow>,
    selectedIndex: Int?,
    copyMessage: String?,
    onSelect: (Int) -> Unit,
    onCopy: () -> Unit,
    onCopyLine: () -> Unit,
    onShowOnImage: () -> Unit,
    canLocate: Boolean = true,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.still_matches_title)) },
        text = {
            val selectedItemIndex = rows.indexOfFirst { row -> row.index == selectedIndex }
            val listState = rememberLazyListState()
            LaunchedEffect(selectedItemIndex) {
                if (selectedItemIndex >= 0) {
                    listState.scrollToItem(selectedItemIndex)
                }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    pluralStringResource(
                        Res.plurals.status_match_count,
                        rows.size,
                        rows.size,
                    ),
                    modifier = Modifier.padding(bottom = FinderSpacing.small),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!canLocate) {
                    Text(
                        stringResource(Res.string.search_results_locate_hint),
                        modifier = Modifier.padding(bottom = FinderSpacing.small),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 140.dp)
                        .semantics { selectableGroup() },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(rows, key = StillMatchRow::index) { row ->
                        StillMatchRowItem(
                            row = row,
                            selected = selectedIndex == row.index,
                            onSelect = { onSelect(row.index) },
                        )
                    }
                }
                copyMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FinderPrimaryAction(
                    label = stringResource(Res.string.still_match_show),
                    enabled = canLocate && selectedIndex != null &&
                        rows.any { row -> row.index == selectedIndex },
                    modifier = Modifier.fillMaxWidth().padding(top = FinderSpacing.small),
                    onClick = onShowOnImage,
                )
                FinderSecondaryAction(
                    label = stringResource(Res.string.still_copy_match),
                    enabled = selectedIndex != null &&
                        rows.any { row -> row.index == selectedIndex },
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCopy,
                )
                FinderSecondaryAction(
                    label = stringResource(Res.string.still_copy_line),
                    enabled = selectedIndex != null &&
                        rows.any { row -> row.index == selectedIndex },
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCopyLine,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        FinderSpacing.extraSmall,
                        Alignment.End,
                    ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    FinderSecondaryAction(
                        label = stringResource(Res.string.status_cancel),
                        onClick = onDismiss,
                    )
                }
            }
        },
        confirmButton = {},
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun StillMatchRowItem(
    row: StillMatchRow,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium,
            ),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                .padding(
                    horizontal = FinderSpacing.medium,
                    vertical = FinderSpacing.small,
                ),
        ) {
            Text(
                text = row.matchedText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(16.dp)
                        .background(row.termColor.composeColor, MaterialTheme.shapes.extraSmall)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.extraSmall,
                        ),
                )
                Text(
                    text = "${row.termLabel} · ${row.lineContext}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
