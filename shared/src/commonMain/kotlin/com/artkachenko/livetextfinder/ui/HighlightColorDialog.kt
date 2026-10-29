package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.HighlightColor
import kotlin.math.roundToInt
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.highlight_color_apply
import livetextfinder.shared.generated.resources.highlight_color_blue
import livetextfinder.shared.generated.resources.highlight_color_cancel
import livetextfinder.shared.generated.resources.highlight_color_custom
import livetextfinder.shared.generated.resources.highlight_color_custom_description
import livetextfinder.shared.generated.resources.highlight_color_custom_selected
import livetextfinder.shared.generated.resources.highlight_color_cyan
import livetextfinder.shared.generated.resources.highlight_color_done
import livetextfinder.shared.generated.resources.highlight_color_green
import livetextfinder.shared.generated.resources.highlight_color_magenta
import livetextfinder.shared.generated.resources.highlight_color_preview
import livetextfinder.shared.generated.resources.highlight_color_red
import livetextfinder.shared.generated.resources.highlight_color_title
import livetextfinder.shared.generated.resources.highlight_color_white
import livetextfinder.shared.generated.resources.highlight_color_yellow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HighlightColorDialog(
    selectedColor: HighlightColor,
    enabled: Boolean,
    onSelectColor: (HighlightColor) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var customEditor by remember { mutableStateOf(false) }
    var red by remember(selectedColor) {
        mutableIntStateOf((selectedColor.argb shr 16 and 0xFF).toInt())
    }
    var green by remember(selectedColor) {
        mutableIntStateOf((selectedColor.argb shr 8 and 0xFF).toInt())
    }
    var blue by remember(selectedColor) { mutableIntStateOf((selectedColor.argb and 0xFF).toInt()) }
    val draft = HighlightColor.fromRgb(red, green, blue)
    val isCustomSelected = HighlightColor.entries.none { it == selectedColor }

    fun resetDraftToSelection() {
        red = (selectedColor.argb shr 16 and 0xFF).toInt()
        green = (selectedColor.argb shr 8 and 0xFF).toInt()
        blue = (selectedColor.argb and 0xFF).toInt()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.highlight_color_title)) },
        text = {
            if (customEditor) {
                CustomColorEditor(
                    red = red,
                    green = green,
                    blue = blue,
                    color = draft,
                    enabled = enabled,
                    onRedChange = { red = it },
                    onGreenChange = { green = it },
                    onBlueChange = { blue = it },
                )
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                ) {
                    Text(
                        text = stringResource(Res.string.highlight_color_custom_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HighlightColor.entries.forEach { color ->
                        HighlightColorRow(
                            color = color,
                            selected = selectedColor == color,
                            enabled = enabled,
                            onClick = { onSelectColor(color) },
                        )
                    }
                    CustomColorRow(
                        color = selectedColor,
                        selected = isCustomSelected,
                        enabled = enabled,
                        onClick = {
                            resetDraftToSelection()
                            customEditor = true
                        },
                    )
                }
            }
        },
        dismissButton = if (customEditor) {
            {
                TextButton(
                    onClick = {
                        resetDraftToSelection()
                        customEditor = false
                    },
                ) {
                    Text(stringResource(Res.string.highlight_color_cancel))
                }
            }
        } else {
            null
        },
        confirmButton = {
            if (customEditor) {
                TextButton(
                    enabled = enabled,
                    onClick = {
                        onSelectColor(draft)
                        onDismissRequest()
                    },
                ) {
                    Text(stringResource(Res.string.highlight_color_apply))
                }
            } else {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(Res.string.highlight_color_done))
                }
            }
        },
    )
}

@Composable
private fun CustomColorRow(
    color: HighlightColor,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = if (selected) {
        stringResource(Res.string.highlight_color_custom_selected, color.displayName)
    } else {
        stringResource(Res.string.highlight_color_custom)
    }
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
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Box(
            modifier = Modifier.size(24.dp)
                .background(color.composeColor, swatchShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, swatchShape),
        )
        Column {
            Text(stringResource(Res.string.highlight_color_custom))
            if (selected) {
                Text(color.displayName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CustomColorEditor(
    red: Int,
    green: Int,
    blue: Int,
    color: HighlightColor,
    enabled: Boolean,
    onRedChange: (Int) -> Unit,
    onGreenChange: (Int) -> Unit,
    onBlueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
    ) {
        Text(
            text = stringResource(Res.string.highlight_color_custom_description),
            style = MaterialTheme.typography.bodyMedium,
        )
        val previewLabel = stringResource(Res.string.highlight_color_preview)
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .background(color.composeColor, MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                .semantics { contentDescription = previewLabel },
        )
        Text(color.hexRgb, style = MaterialTheme.typography.titleMedium)
        RgbSlider(
            label = stringResource(Res.string.highlight_color_red),
            value = red,
            enabled = enabled,
            onValueChange = onRedChange,
        )
        RgbSlider(
            label = stringResource(Res.string.highlight_color_green),
            value = green,
            enabled = enabled,
            onValueChange = onGreenChange,
        )
        RgbSlider(
            label = stringResource(Res.string.highlight_color_blue),
            value = blue,
            enabled = enabled,
            onValueChange = onBlueChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RgbSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val sliderColors = SliderDefaults.colors()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value.toString(), style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            enabled = enabled,
            valueRange = 0f..255f,
            interactionSource = interactionSource,
            thumb = {
                Box(
                    modifier = Modifier.height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        colors = sliderColors,
                        enabled = enabled,
                    )
                }
            },
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun HighlightColorRow(
    color: HighlightColor,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = color.resourceLabel()
    val labelText = stringResource(label)
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
            .semantics { contentDescription = labelText }
            .padding(horizontal = FinderSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(FinderSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Box(
            modifier = Modifier.size(24.dp)
                .background(color.composeColor, swatchShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, swatchShape),
        )
        Text(text = labelText, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun HighlightColor.resourceLabel() = when (argb) {
    HighlightColor.Yellow.argb -> Res.string.highlight_color_yellow
    HighlightColor.Cyan.argb -> Res.string.highlight_color_cyan
    HighlightColor.Magenta.argb -> Res.string.highlight_color_magenta
    HighlightColor.White.argb -> Res.string.highlight_color_white
    else -> Res.string.highlight_color_custom
}
