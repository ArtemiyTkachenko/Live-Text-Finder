package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.RecognitionDetails
import com.artkachenko.livetextfinder.SearchFeedback
import com.artkachenko.livetextfinder.SearchFeedbackKind
import com.artkachenko.livetextfinder.StatusSource
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.status_area_waiting
import livetextfinder.shared.generated.resources.status_details
import livetextfinder.shared.generated.resources.status_empty_content
import livetextfinder.shared.generated.resources.status_enter_query
import livetextfinder.shared.generated.resources.status_freezing
import livetextfinder.shared.generated.resources.status_frozen
import livetextfinder.shared.generated.resources.status_highlights_waiting
import livetextfinder.shared.generated.resources.status_imported
import livetextfinder.shared.generated.resources.status_match_count
import livetextfinder.shared.generated.resources.status_no_area_matches
import livetextfinder.shared.generated.resources.status_no_matches
import livetextfinder.shared.generated.resources.status_no_readable_text
import livetextfinder.shared.generated.resources.status_results_unavailable
import livetextfinder.shared.generated.resources.status_retained_outlines
import livetextfinder.shared.generated.resources.status_scanning
import livetextfinder.shared.generated.resources.status_source_live
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
    stageBand: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(
            if (stageBand) Modifier.heightIn(min = 56.dp) else Modifier,
        ),
        color = if (stageBand) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = if (stageBand) RectangleShape else MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = FinderSpacing.medium,
                vertical = if (stageBand || details != null) {
                    FinderSpacing.extraSmall
                } else {
                    FinderSpacing.small
                },
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
                minLines = if (stageBand) 2 else 1,
            )
            if (details != null) {
                FinderSecondaryAction(
                    label = stringResource(Res.string.status_details),
                    onClick = onDetails,
                )
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
        SearchFeedbackKind.ResultsUnavailable -> retainedMessage(
            stringResource(Res.string.status_results_unavailable),
            feedback.retainedOutlines,
        )
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
