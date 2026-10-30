package com.artkachenko.livetextfinder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.help_options_location
import livetextfinder.shared.generated.resources.help_still_body
import livetextfinder.shared.generated.resources.help_still_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UsageHelpDialog(onDismissRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("How to use") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HelpSection(
                    title = "Find text on Android",
                    body = "Enter a word or phrase, then point the camera at readable printed " +
                        "Latin text. Matching ignores case and finds substrings within each OCR " +
                        "line; " +
                        "phrases do not cross lines. No match means none was recognized, not " +
                        "proof of absence.",
                )
                HelpSection(
                    title = "Search lists",
                    body = "Create one entry per line; all entries are searched at once, and " +
                        "spaces within an entry stay part of its phrase. Selecting a list " +
                        "replaces the manual search. Editing the manual query returns to manual " +
                        "search.",
                )
                HelpSection(
                    title = "Preview and images",
                    body = "The keyboard or open Search lists hide the preview. Dismiss the " +
                        "keyboard or close lists to see it; compact windows may need more space. " +
                        "Freeze frame keeps one image until Resume live. Import one " +
                        "JPEG, PNG, or WebP image; Return to camera exits it. " +
                        "Images and OCR stay in memory, are not saved, and are discarded " +
                        "when the app is backgrounded " +
                        "or the screen is recreated. An open photo picker can still complete " +
                        "after you return." + if (liveSearchAreaAvailable()) {
                            " The optional live area restricts live matches to fully contained " +
                                "words and phrases; it does not crop capture. Frozen, imported, " +
                                "and shared images use the whole source. Freeze, Import, " +
                                "backgrounding, or recreation resets the area."
                        } else {
                            ""
                        },
                )
                HelpSection(
                    title = stringResource(Res.string.help_still_title),
                    body = stringResource(Res.string.help_still_body),
                )
                HelpSection(
                    title = "Colour and sharing on Android",
                    body = stringResource(Res.string.help_options_location) + " " +
                        "The selected colour applies to manual search. Each list entry gets a " +
                        "stable labelled colour across previews and shared images; open Search " +
                        "results to see each entry and its current count. " +
                        "Android saves the query, lists, selection, and colour. Share image is " +
                        "available for frozen or imported images; it prepares a temporary " +
                        "highlighted PNG and opens the system share sheet. Opening it does not " +
                        "confirm delivery or save to your gallery; the chosen app controls what " +
                        "happens next.",
                )
                HelpSection(
                    title = "iOS",
                    body = "Query, lists, and colour are kept in memory only. Camera OCR, image " +
                        "import, and image sharing are unavailable in this build.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Done") }
        },
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
