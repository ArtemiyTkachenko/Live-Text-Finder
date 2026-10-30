package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import livetextfinder.shared.generated.resources.finder_match_substring
import livetextfinder.shared.generated.resources.finder_match_whole_words
import livetextfinder.shared.generated.resources.finder_options_help
import livetextfinder.shared.generated.resources.finder_options_import
import livetextfinder.shared.generated.resources.finder_options_matching
import livetextfinder.shared.generated.resources.finder_options_title
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
    onHighlight: () -> Unit,
    onLiveArea: () -> Unit,
    onImport: () -> Unit,
    onHelp: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .selectableGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FinderSpacing.large)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
        ) {
            Text(
                stringResource(Res.string.finder_options_title),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(
                onClick = onHighlight,
                enabled = highlightEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(highlightLabel)
            }
            Text(
                stringResource(Res.string.finder_options_matching),
                style = MaterialTheme.typography.titleSmall,
            )
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
            if (liveAreaLabel != null) {
                TextButton(onClick = onLiveArea, modifier = Modifier.fillMaxWidth()) {
                    Text(liveAreaLabel)
                }
            }
            if (showImport) {
                TextButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.finder_options_import))
                }
            }
            TextButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.finder_options_help))
            }
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
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
