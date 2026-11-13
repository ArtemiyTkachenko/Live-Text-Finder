package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.MAX_TERMS_PER_LIST
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.previewSearchListTerms
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_list_cancel
import livetextfinder.shared.generated.resources.search_list_edit_heading
import livetextfinder.shared.generated.resources.search_list_name_label
import livetextfinder.shared.generated.resources.search_list_new_heading
import livetextfinder.shared.generated.resources.search_list_save
import livetextfinder.shared.generated.resources.search_list_terms_blank_warning
import livetextfinder.shared.generated.resources.search_list_terms_count
import livetextfinder.shared.generated.resources.search_list_terms_empty
import livetextfinder.shared.generated.resources.search_list_terms_entry
import livetextfinder.shared.generated.resources.search_list_terms_example
import livetextfinder.shared.generated.resources.search_list_terms_help
import livetextfinder.shared.generated.resources.search_list_terms_label
import livetextfinder.shared.generated.resources.search_list_terms_more
import livetextfinder.shared.generated.resources.search_list_terms_preview
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SearchListEditor(
    editor: SearchListEditorState,
    canSave: Boolean,
    onNameChange: (String) -> Unit,
    onTermsChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(FinderSpacing.large),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.medium),
        ) {
            Text(
                text = stringResource(
                    if (editor.editingId == null) {
                        Res.string.search_list_new_heading
                    } else {
                        Res.string.search_list_edit_heading
                    },
                ),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(Res.string.search_list_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = editor.terms,
                onValueChange = onTermsChange,
                label = { Text(stringResource(Res.string.search_list_terms_label)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = MaterialTheme.shapes.medium,
            )
            val preview = previewSearchListTerms(editor.terms)
            Text(
                stringResource(Res.string.search_list_terms_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(Res.string.search_list_terms_example),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    Res.string.search_list_terms_count,
                    preview.terms.size,
                    MAX_TERMS_PER_LIST,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.terms.isEmpty()) {
                Text(
                    stringResource(Res.string.search_list_terms_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(Res.string.search_list_terms_preview),
                    style = MaterialTheme.typography.titleSmall,
                )
                preview.terms.take(5).forEachIndexed { index, term ->
                    Text(
                        stringResource(
                            Res.string.search_list_terms_entry,
                            index + 1,
                            term,
                        ),
                    )
                }
                if (preview.terms.size > 5) {
                    Text(
                        pluralStringResource(
                            Res.plurals.search_list_terms_more,
                            preview.terms.size - 5,
                            preview.terms.size - 5,
                        ),
                    )
                }
            }
            if (preview.blankLineCount > 0) {
                Text(
                    stringResource(Res.string.search_list_terms_blank_warning),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            editor.error?.let { error ->
                Text(searchSessionErrorMessage(error), color = MaterialTheme.colorScheme.error)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                FinderPrimaryAction(
                    label = stringResource(Res.string.search_list_save),
                    enabled = canSave,
                    onClick = onSave,
                )
                FinderSecondaryAction(
                    label = stringResource(Res.string.search_list_cancel),
                    onClick = onCancel,
                )
            }
        }
    }
}
