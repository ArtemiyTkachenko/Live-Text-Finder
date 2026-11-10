package com.artkachenko.livetextfinder.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.confirm_dialog_cancel
import org.jetbrains.compose.resources.stringResource

/** Asks for confirmation before a destructive action, with Cancel as the safe default. */
@Composable
internal fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            FinderPrimaryAction(
                label = confirmLabel,
                enabled = true,
                destructive = true,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            FinderSecondaryAction(
                label = stringResource(Res.string.confirm_dialog_cancel),
                onClick = onDismiss,
            )
        },
    )
}
