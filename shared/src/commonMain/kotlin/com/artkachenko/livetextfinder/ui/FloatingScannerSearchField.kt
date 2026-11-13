package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_find_text
import livetextfinder.shared.generated.resources.finder_match_whole_words
import livetextfinder.shared.generated.resources.scanner_close
import livetextfinder.shared.generated.resources.scanner_match_word
import livetextfinder.shared.generated.resources.scanner_search
import livetextfinder.shared.generated.resources.scanner_toggle_off
import livetextfinder.shared.generated.resources.scanner_toggle_on
import livetextfinder.shared.generated.resources.search_context_use_manual
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Search input with a caller-controlled whole-word toggle. */
@Composable
internal fun FloatingScannerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    wholeWords: Boolean,
    onWholeWordsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeListName: String? = null,
    matchModeEnabled: Boolean = true,
    onUseManual: () -> Unit = { Unit },
) {
    val label = stringResource(Res.string.finder_find_text)
    val contentColor = FinderCameraOverlay.onControlScrim
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(FinderCameraOverlay.controlScrim)
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.scanner_search),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
        if (activeListName != null) {
            ActiveListChip(
                name = activeListName,
                onRemove = onUseManual,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        BasicTextField(
            value = if (activeListName == null) query else "",
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .semantics { contentDescription = label },
            textStyle = textStyle.copy(color = contentColor),
            cursorBrush = SolidColor(contentColor),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (activeListName == null && query.isEmpty()) {
                        Text(text = label, color = contentColor, style = textStyle, maxLines = 1)
                    }
                    innerTextField()
                }
            },
        )
        WholeWordsToggle(wholeWords, matchModeEnabled, onWholeWordsChange)
    }
}

@Composable
private fun ActiveListChip(
    name: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.search_context_use_manual)
    Row(
        modifier = modifier.clip(CircleShape)
            .background(FinderCameraOverlay.selectedControlContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = FinderCameraOverlay.onSelectedControl,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 100.dp).padding(start = 12.dp),
        )
        ScannerControlTooltip(label) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = FinderCameraOverlay.onSelectedControl,
                ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.scanner_close),
                    contentDescription = label,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun WholeWordsToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val label = stringResource(Res.string.finder_match_whole_words)
    val description = stringResource(
        if (checked) Res.string.scanner_toggle_on else Res.string.scanner_toggle_off,
    )
    ScannerControlTooltip(label) {
        IconToggleButton(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(48.dp).semantics { stateDescription = description },
            colors = IconButtonDefaults.iconToggleButtonColors(
                contentColor = FinderCameraOverlay.onControlScrim,
                checkedContainerColor = FinderCameraOverlay.selectedControlContainer,
                checkedContentColor = FinderCameraOverlay.onSelectedControl,
            ),
        ) {
            Icon(
                painter = painterResource(Res.drawable.scanner_match_word),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
