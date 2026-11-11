package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.RecognitionDetails
import com.artkachenko.livetextfinder.StatusSource
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.status_details_close
import livetextfinder.shared.generated.resources.status_dimensions
import livetextfinder.shared.generated.resources.status_dimensions_rotation
import livetextfinder.shared.generated.resources.status_empty_content
import livetextfinder.shared.generated.resources.status_latin_boundary
import livetextfinder.shared.generated.resources.status_lines
import livetextfinder.shared.generated.resources.status_no_text_sample
import livetextfinder.shared.generated.resources.status_outlined_count
import livetextfinder.shared.generated.resources.status_retention
import livetextfinder.shared.generated.resources.status_source_frozen
import livetextfinder.shared.generated.resources.status_source_imported
import livetextfinder.shared.generated.resources.status_source_live
import livetextfinder.shared.generated.resources.status_text_sample
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
            FinderPrimaryAction(
                label = stringResource(Res.string.status_details_close),
                enabled = true,
                onClick = onDismissRequest,
            )
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
