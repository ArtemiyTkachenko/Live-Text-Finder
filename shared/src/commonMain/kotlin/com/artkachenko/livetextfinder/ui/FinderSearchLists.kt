package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.MAX_TERMS_PER_LIST
import com.artkachenko.livetextfinder.search.SavedSearchList
import com.artkachenko.livetextfinder.search.SearchListEditorState
import com.artkachenko.livetextfinder.search.previewSearchListTerms
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_list_cancel
import livetextfinder.shared.generated.resources.search_list_delete
import livetextfinder.shared.generated.resources.search_list_delete_accessibility
import livetextfinder.shared.generated.resources.search_list_edit
import livetextfinder.shared.generated.resources.search_list_edit_accessibility
import livetextfinder.shared.generated.resources.search_list_edit_heading
import livetextfinder.shared.generated.resources.search_list_name_label
import livetextfinder.shared.generated.resources.search_list_new_heading
import livetextfinder.shared.generated.resources.search_list_save
import livetextfinder.shared.generated.resources.search_list_select_accessibility
import livetextfinder.shared.generated.resources.search_list_selected
import livetextfinder.shared.generated.resources.search_list_term_count
import livetextfinder.shared.generated.resources.search_list_terms_blank_warning
import livetextfinder.shared.generated.resources.search_list_terms_count
import livetextfinder.shared.generated.resources.search_list_terms_empty
import livetextfinder.shared.generated.resources.search_list_terms_entry
import livetextfinder.shared.generated.resources.search_list_terms_example
import livetextfinder.shared.generated.resources.search_list_terms_help
import livetextfinder.shared.generated.resources.search_list_terms_label
import livetextfinder.shared.generated.resources.search_list_terms_more
import livetextfinder.shared.generated.resources.search_list_terms_preview
import livetextfinder.shared.generated.resources.search_lists_heading
import livetextfinder.shared.generated.resources.search_lists_new
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FinderSearchLists(
    lists: List<SavedSearchList>,
    activeListId: String?,
    editor: SearchListEditorState,
    canSave: Boolean,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (SavedSearchList) -> Unit,
    onDelete: (SavedSearchList) -> Unit,
    onNameChange: (String) -> Unit,
    onTermsChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(FinderSpacing.large),
        verticalArrangement = Arrangement.spacedBy(FinderSpacing.large),
    ) {
        Text(
            text = stringResource(Res.string.search_lists_heading),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onNew, enabled = canSave) {
            Text(stringResource(Res.string.search_lists_new))
        }
        Column(
            modifier = Modifier.selectableGroup().fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.medium),
        ) {
            lists.forEach { list ->
                SearchListRow(
                    list = list,
                    selected = activeListId == list.id,
                    onSelect = { onSelect(list.id) },
                    onEdit = { onEdit(list) },
                    onDelete = { onDelete(list) },
                )
            }
        }
        if (editor.isOpen) {
            SearchListEditor(
                editor = editor,
                canSave = canSave,
                onNameChange = onNameChange,
                onTermsChange = onTermsChange,
                onSave = onSave,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun SearchListRow(
    list: SavedSearchList,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val selectLabel = stringResource(Res.string.search_list_select_accessibility, list.name)
    val editLabel = stringResource(Res.string.search_list_edit_accessibility, list.name)
    val deleteLabel = stringResource(Res.string.search_list_delete_accessibility, list.name)
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                    .semantics { contentDescription = selectLabel }
                    .padding(FinderSpacing.large),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
            ) {
                Text(
                    text = list.name,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = pluralStringResource(
                        Res.plurals.search_list_term_count,
                        list.terms.size,
                        list.terms.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (selected) {
                    Text(
                        text = stringResource(Res.string.search_list_selected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = editLabel },
            ) {
                Text(stringResource(Res.string.search_list_edit))
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = deleteLabel },
            ) {
                Text(stringResource(Res.string.search_list_delete))
            }
        }
    }
}

@Composable
private fun SearchListEditor(
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
            OutlinedTextField(
                value = editor.terms,
                onValueChange = onTermsChange,
                label = { Text(stringResource(Res.string.search_list_terms_label)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = MaterialTheme.shapes.medium,
            )
            Text(
                stringResource(Res.string.search_list_terms_preview),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(
                    Res.string.search_list_terms_count,
                    preview.terms.size,
                    MAX_TERMS_PER_LIST,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (preview.terms.isEmpty()) {
                Text(stringResource(Res.string.search_list_terms_empty))
            } else {
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
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onSave, enabled = canSave) {
                    Text(stringResource(Res.string.search_list_save))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(Res.string.search_list_cancel))
                }
            }
        }
    }
}
