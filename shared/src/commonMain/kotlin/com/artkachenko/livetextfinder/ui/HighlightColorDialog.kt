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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.HighlightColor
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.highlight_color_cyan
import livetextfinder.shared.generated.resources.highlight_color_done
import livetextfinder.shared.generated.resources.highlight_color_magenta
import livetextfinder.shared.generated.resources.highlight_color_title
import livetextfinder.shared.generated.resources.highlight_color_white
import livetextfinder.shared.generated.resources.highlight_color_yellow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HighlightColorDialog(
    selectedColor: HighlightColor,
    enabled: Boolean,
    onSelectColor: (HighlightColor) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.highlight_color_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
            ) {
                HighlightColor.entries.forEach { color ->
                    HighlightColorRow(
                        color = color,
                        selected = selectedColor == color,
                        enabled = enabled,
                        onClick = { onSelectColor(color) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.highlight_color_done))
            }
        },
    )
}

@Composable
private fun HighlightColorRow(
    color: HighlightColor,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(color.resourceLabel())
    val swatchShape = MaterialTheme.shapes.extraSmall
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .padding(horizontal = FinderSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(FinderSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Box(
            modifier = Modifier.size(24.dp)
                .background(
                    color = color.composeColor,
                    shape = swatchShape,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = swatchShape,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun HighlightColor.resourceLabel(): StringResource = when (this) {
    HighlightColor.Yellow -> Res.string.highlight_color_yellow
    HighlightColor.Cyan -> Res.string.highlight_color_cyan
    HighlightColor.Magenta -> Res.string.highlight_color_magenta
    HighlightColor.White -> Res.string.highlight_color_white
}
