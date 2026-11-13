package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.artkachenko.livetextfinder.search.SavedSearchList
import com.artkachenko.livetextfinder.search.SearchListEditorState
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_list_delete
import livetextfinder.shared.generated.resources.search_list_delete_accessibility
import livetextfinder.shared.generated.resources.search_list_edit
import livetextfinder.shared.generated.resources.search_list_edit_accessibility
import livetextfinder.shared.generated.resources.search_list_select_accessibility
import livetextfinder.shared.generated.resources.search_list_selected
import livetextfinder.shared.generated.resources.search_list_term_count
import livetextfinder.shared.generated.resources.search_lists_empty
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(FinderSpacing.small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.search_lists_heading),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            if (editor.isOpen) {
                FinderSecondaryAction(
                    label = stringResource(Res.string.search_lists_new),
                    onClick = onNew,
                    enabled = canSave,
                )
            } else {
                FinderPrimaryAction(
                    label = stringResource(Res.string.search_lists_new),
                    enabled = canSave,
                    onClick = onNew,
                )
            }
        }
        if (lists.isEmpty() && !editor.isOpen) {
            Text(
                text = stringResource(Res.string.search_lists_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = onSelect,
                        )
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selected) {
                        Text(
                            text = stringResource(Res.string.search_list_selected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = FinderSpacing.small),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FinderSecondaryAction(
                        label = stringResource(Res.string.search_list_edit),
                        onClick = onEdit,
                        modifier = Modifier.semantics { contentDescription = editLabel },
                    )
                    FinderSecondaryAction(
                        label = stringResource(Res.string.search_list_delete),
                        onClick = onDelete,
                        modifier = Modifier.semantics { contentDescription = deleteLabel },
                        destructive = true,
                    )
                }
            }
        }
    }
}
