package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.SearchMatchMode
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_match_help
import livetextfinder.shared.generated.resources.finder_match_substring
import livetextfinder.shared.generated.resources.finder_match_whole_words
import livetextfinder.shared.generated.resources.finder_options_camera_group
import livetextfinder.shared.generated.resources.finder_options_help
import livetextfinder.shared.generated.resources.finder_options_import
import livetextfinder.shared.generated.resources.finder_options_search_group
import livetextfinder.shared.generated.resources.finder_options_title
import livetextfinder.shared.generated.resources.finder_options_transfer_group
import livetextfinder.shared.generated.resources.highlight_color_custom_description
import livetextfinder.shared.generated.resources.search_list_export
import livetextfinder.shared.generated.resources.search_list_import
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinderOptionsSheet(
    highlightLabel: String,
    highlightEnabled: Boolean,
    matchMode: SearchMatchMode,
    matchModeEnabled: Boolean,
    onMatchMode: (SearchMatchMode) -> Unit,
    liveAreaLabel: String?,
    showImport: Boolean,
    showListTransfer: Boolean,
    listTransferHelp: String?,
    importListEnabled: Boolean,
    exportListEnabled: Boolean,
    onImportList: () -> Unit,
    onExportList: () -> Unit,
    onHighlight: () -> Unit,
    onLiveArea: () -> Unit,
    onImport: () -> Unit,
    onHelp: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FinderSpacing.large)
                .padding(bottom = FinderSpacing.majorSection),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.small),
        ) {
            Text(
                stringResource(Res.string.finder_options_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OptionsGroupTitle(stringResource(Res.string.finder_options_search_group))
            FinderOptionsActionRow(
                label = highlightLabel,
                enabled = highlightEnabled,
                onClick = onHighlight,
                supportingDescription = stringResource(
                    Res.string.highlight_color_custom_description,
                ),
            )
            Column(modifier = Modifier.selectableGroup()) {
                MatchModeOption(
                    label = stringResource(Res.string.finder_match_substring),
                    selected = matchMode == SearchMatchMode.Substring,
                    enabled = matchModeEnabled,
                    onClick = { onMatchMode(SearchMatchMode.Substring) },
                )
                MatchModeOption(
                    label = stringResource(Res.string.finder_match_whole_words),
                    selected = matchMode == SearchMatchMode.WholeWord,
                    enabled = matchModeEnabled,
                    onClick = { onMatchMode(SearchMatchMode.WholeWord) },
                )
            }
            Text(
                stringResource(Res.string.finder_match_help),
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (liveAreaLabel != null || showImport) {
                OptionsGroupTitle(stringResource(Res.string.finder_options_camera_group))
                if (liveAreaLabel != null) {
                    FinderOptionsActionRow(label = liveAreaLabel, onClick = onLiveArea)
                }
                if (showImport) {
                    FinderOptionsActionRow(
                        label = stringResource(Res.string.finder_options_import),
                        onClick = onImport,
                    )
                }
            }
            if (showListTransfer) {
                OptionsGroupTitle(stringResource(Res.string.finder_options_transfer_group))
                FinderOptionsActionRow(
                    label = stringResource(Res.string.search_list_import),
                    enabled = importListEnabled,
                    onClick = onImportList,
                )
                FinderOptionsActionRow(
                    label = stringResource(Res.string.search_list_export),
                    enabled = exportListEnabled,
                    onClick = onExportList,
                )
                if (listTransferHelp != null) {
                    Text(
                        text = listTransferHelp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FinderOptionsActionRow(
                label = stringResource(Res.string.finder_options_help),
                onClick = onHelp,
            )
        }
    }
}

@Composable
private fun MatchModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = LocalContentColor.current
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (enabled) {
                contentColor
            } else {
                contentColor.copy(alpha = contentColor.alpha * 0.38f)
            },
        )
        RadioButton(selected, onClick = null, enabled = enabled)
    }
}

@Composable
private fun OptionsGroupTitle(label: String) {
    Text(
        label,
        modifier = Modifier.padding(top = FinderSpacing.small),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FinderOptionsActionRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    supportingDescription: String? = null,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = PaddingValues(
            horizontal = FinderSpacing.medium,
            vertical = FinderSpacing.small,
        ),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                supportingDescription?.let { description ->
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
