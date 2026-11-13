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
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_match_help
import livetextfinder.shared.generated.resources.help_colour_body
import livetextfinder.shared.generated.resources.help_colour_title
import livetextfinder.shared.generated.resources.help_done
import livetextfinder.shared.generated.resources.help_find_body
import livetextfinder.shared.generated.resources.help_find_title
import livetextfinder.shared.generated.resources.help_ios_body
import livetextfinder.shared.generated.resources.help_ios_title
import livetextfinder.shared.generated.resources.help_lists_body
import livetextfinder.shared.generated.resources.help_lists_title
import livetextfinder.shared.generated.resources.help_options_location
import livetextfinder.shared.generated.resources.help_preview_body
import livetextfinder.shared.generated.resources.help_preview_scan_area
import livetextfinder.shared.generated.resources.help_preview_title
import livetextfinder.shared.generated.resources.help_still_body
import livetextfinder.shared.generated.resources.help_still_title
import livetextfinder.shared.generated.resources.help_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UsageHelpDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.help_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.medium),
            ) {
                HelpSection(
                    title = stringResource(Res.string.help_find_title),
                    body = stringResource(
                        Res.string.help_find_body,
                        stringResource(Res.string.finder_match_help),
                    ),
                )
                HelpSection(
                    title = stringResource(Res.string.help_lists_title),
                    body = stringResource(Res.string.help_lists_body),
                )
                HelpSection(
                    title = stringResource(Res.string.help_preview_title),
                    body = stringResource(Res.string.help_preview_body) +
                        if (liveSearchAreaAvailable()) {
                            " " + stringResource(Res.string.help_preview_scan_area)
                        } else {
                            ""
                        },
                )
                HelpSection(
                    title = stringResource(Res.string.help_still_title),
                    body = stringResource(Res.string.help_still_body),
                )
                HelpSection(
                    title = stringResource(Res.string.help_colour_title),
                    body = stringResource(
                        Res.string.help_colour_body,
                        stringResource(Res.string.help_options_location),
                    ),
                )
                HelpSection(
                    title = stringResource(Res.string.help_ios_title),
                    body = stringResource(Res.string.help_ios_body),
                )
            }
        },
        confirmButton = {
            FinderPrimaryAction(
                label = stringResource(Res.string.help_done),
                enabled = true,
                onClick = onDismissRequest,
            )
        },
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FinderSpacing.small)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
