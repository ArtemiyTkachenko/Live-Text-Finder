package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_options_help
import livetextfinder.shared.generated.resources.finder_options_import
import livetextfinder.shared.generated.resources.finder_options_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinderOptionsSheet(
    highlightLabel: String,
    highlightEnabled: Boolean,
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
