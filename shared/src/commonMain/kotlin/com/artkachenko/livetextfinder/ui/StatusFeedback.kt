package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.RecognitionDetails
import com.artkachenko.livetextfinder.SearchFeedback
import com.artkachenko.livetextfinder.SearchFeedbackKind
import com.artkachenko.livetextfinder.StatusSource
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.status_area_waiting
import livetextfinder.shared.generated.resources.status_details
import livetextfinder.shared.generated.resources.status_details_close
import livetextfinder.shared.generated.resources.status_dimensions
import livetextfinder.shared.generated.resources.status_dimensions_rotation
import livetextfinder.shared.generated.resources.status_empty_content
import livetextfinder.shared.generated.resources.status_enter_query
import livetextfinder.shared.generated.resources.status_freezing
import livetextfinder.shared.generated.resources.status_frozen
import livetextfinder.shared.generated.resources.status_highlights_waiting
import livetextfinder.shared.generated.resources.status_imported
import livetextfinder.shared.generated.resources.status_latin_boundary
import livetextfinder.shared.generated.resources.status_lines
import livetextfinder.shared.generated.resources.status_match_count
import livetextfinder.shared.generated.resources.status_no_area_matches
import livetextfinder.shared.generated.resources.status_no_matches
import livetextfinder.shared.generated.resources.status_no_readable_text
import livetextfinder.shared.generated.resources.status_no_text_sample
import livetextfinder.shared.generated.resources.status_outlined_count
import livetextfinder.shared.generated.resources.status_retained_outlines
import livetextfinder.shared.generated.resources.status_retention
import livetextfinder.shared.generated.resources.status_scanning
import livetextfinder.shared.generated.resources.status_source_frozen
import livetextfinder.shared.generated.resources.status_source_imported
import livetextfinder.shared.generated.resources.status_source_live
import livetextfinder.shared.generated.resources.status_text_sample
import livetextfinder.shared.generated.resources.status_waiting_fresh
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StatusSummary(
    message: String,
    details: RecognitionDetails?,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = FinderSpacing.medium,
                vertical = FinderSpacing.small,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FinderSpacing.small),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (details != null) {
                TextButton(onClick = onDetails) {
                    Text(stringResource(Res.string.status_details))
                }
            }
        }
    }
}

@Composable
internal fun sourceStatusMessage(
    source: StatusSource,
    feedback: SearchFeedback,
    scanning: Boolean = false,
): String {
    val content = when (feedback.kind) {
        SearchFeedbackKind.EnterQuery -> stringResource(Res.string.status_enter_query)
        SearchFeedbackKind.WaitingForFreshText -> stringResource(Res.string.status_waiting_fresh)
        SearchFeedbackKind.WaitingForContent -> stringResource(Res.string.status_empty_content)
        SearchFeedbackKind.WaitingForAreaMapping -> stringResource(Res.string.status_area_waiting)
        SearchFeedbackKind.NoReadableText -> retainedMessage(
            stringResource(Res.string.status_no_readable_text),
            feedback.retainedOutlines,
        )
        SearchFeedbackKind.NoMatches -> retainedMessage(
            stringResource(Res.string.status_no_matches),
            feedback.retainedOutlines,
        )
        SearchFeedbackKind.NoMatchesInArea -> retainedMessage(
            stringResource(Res.string.status_no_area_matches),
            feedback.retainedOutlines,
        )
        SearchFeedbackKind.Matches -> {
            val count = pluralStringResource(
                Res.plurals.status_match_count,
                feedback.matchCount,
                feedback.matchCount,
            )
            retainedMessage(count, feedback.retainedOutlines)
        }
        SearchFeedbackKind.WaitingForHighlights -> {
            val count = pluralStringResource(
                Res.plurals.status_match_count,
                feedback.matchCount,
                feedback.matchCount,
            )
            "$count · ${stringResource(Res.string.status_highlights_waiting)}"
        }
    }
    return when (source) {
        StatusSource.ImportedImage ->
            "${stringResource(Res.string.status_imported)} · $content"
        StatusSource.FrozenFrame ->
            "${stringResource(Res.string.status_frozen)} · $content"
        StatusSource.LiveCamera -> {
            val label = if (scanning) {
                stringResource(Res.string.status_scanning)
            } else {
                stringResource(Res.string.status_source_live)
            }
            "$label · $content"
        }
        StatusSource.ImportProgress -> content
        StatusSource.FreezingFrame -> stringResource(Res.string.status_freezing)
    }
}

@Composable
private fun retainedMessage(message: String, retainedOutlines: Boolean): String =
    if (retainedOutlines) {
        "$message · ${stringResource(Res.string.status_retained_outlines)}"
    } else {
        message
    }

@Composable
internal fun StatusDetailsDialog(
    title: String,
    details: RecognitionDetails,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.small),
            ) {
                StatusDetailsContent(details)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.status_details_close))
            }
        },
    )
}

@Composable
private fun StatusDetailsContent(details: RecognitionDetails) {
    Text(
        text = when (details.source) {
            StatusSource.ImportedImage -> stringResource(Res.string.status_source_imported)
            StatusSource.FrozenFrame -> stringResource(Res.string.status_source_frozen)
            else -> stringResource(Res.string.status_source_live)
        },
        style = MaterialTheme.typography.titleSmall,
    )
    Text(stringResource(Res.string.status_latin_boundary))
    if (details.recognizedLineCount == null) {
        Text(stringResource(Res.string.status_empty_content))
    } else {
        Text(stringResource(Res.string.status_lines, details.recognizedLineCount))
    }
    details.outlinedElementCount?.let { count ->
        Text(
            pluralStringResource(
                Res.plurals.status_outlined_count,
                count,
                count,
            ),
        )
    }
    if (details.retainedOutlines) {
        Text(stringResource(Res.string.status_retention))
    }
    if (details.imageWidth != null && details.imageHeight != null) {
        val rotation = details.rotationDegrees
        if (rotation == null) {
            Text(
                stringResource(
                    Res.string.status_dimensions,
                    details.imageWidth,
                    details.imageHeight,
                ),
            )
        } else {
            Text(
                stringResource(
                    Res.string.status_dimensions_rotation,
                    details.imageWidth,
                    details.imageHeight,
                    rotation,
                ),
            )
        }
    }
    if (details.textUnavailable) {
        Text(stringResource(Res.string.status_no_text_sample))
    } else {
        details.textSample?.let { sample ->
            Text(stringResource(Res.string.status_text_sample, sample))
        }
    }
    details.sourceMessage?.let { sourceMessage ->
        Text(sourceMessage)
    }
}

@Composable
internal fun ContextMessage(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = FinderSpacing.medium,
                vertical = FinderSpacing.small,
            ),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.small),
        ) {
            Text(
                text = message,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null && onAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}
