package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.still_match_copy_short
import livetextfinder.shared.generated.resources.still_match_next
import livetextfinder.shared.generated.resources.still_match_position
import livetextfinder.shared.generated.resources.still_match_position_description
import livetextfinder.shared.generated.resources.still_match_previous
import livetextfinder.shared.generated.resources.still_matches
import livetextfinder.shared.generated.resources.still_reset_view
import livetextfinder.shared.generated.resources.still_zoom_in
import livetextfinder.shared.generated.resources.still_zoom_out
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StillInspectionControls(
    matchCount: Int,
    showMatchesAction: Boolean = true,
    zoom: Float,
    canZoom: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onMatches: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        TextButton(
            onClick = onZoomIn,
            enabled = canZoom && zoom < 4f,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.still_zoom_in))
        }
        TextButton(
            onClick = onZoomOut,
            enabled = canZoom && zoom > 1f,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.still_zoom_out))
        }
        TextButton(
            onClick = onReset,
            enabled = canZoom && zoom != 1f,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.still_reset_view))
        }
        if (showMatchesAction) {
            TextButton(
                onClick = onMatches,
                enabled = matchCount > 0,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.still_matches, matchCount))
            }
        }
    }
}

@Composable
internal fun StillMatchNavigationControls(
    position: Int,
    count: Int,
    matchedTerm: String,
    matchedText: String,
    canLocate: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCopy: () -> Unit,
) {
    val positionDescription = stringResource(
        Res.string.still_match_position_description,
        position,
        count,
        matchedTerm,
        matchedText,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center,
        ) {
            TextButton(
                onClick = onPrevious,
                enabled = canLocate && position > 1,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.still_match_previous))
            }
            Text(
                stringResource(Res.string.still_match_position, position, count),
                modifier = Modifier.semantics {
                    contentDescription = positionDescription
                },
            )
            TextButton(
                onClick = onNext,
                enabled = canLocate && position in 1 until count,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.still_match_next))
            }
            TextButton(
                onClick = onCopy,
                enabled = position > 0,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.still_match_copy_short))
            }
        }
        Text(
            text = "$matchedTerm: $matchedText",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
