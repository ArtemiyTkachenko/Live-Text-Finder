package com.artkachenko.livetextfinder.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

internal data class SearchListTransferState(
    val supported: Boolean = false,
    val importEnabled: Boolean = false,
    val exportEnabled: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

internal data class SearchListTransferCopy(
    val fallbackName: String,
    val chooseFile: String,
    val readingFile: String,
    val chooseDestination: String,
    val savingFile: String,
    val importCanceled: String,
    val exportCanceled: String,
    val importReady: String,
    val exportComplete: String,
    val anotherTransfer: String,
    val importUnavailable: String,
    val exportUnavailable: String,
    val listCannotExport: String,
    val importFailed: String,
    val exportFailed: String,
)

@Composable
internal expect fun rememberSearchListTransferOwner(): SearchListTransferOwner

internal expect class SearchListTransferOwner() {
    internal val state: State<SearchListTransferState>

    internal fun updateContext(
        sessionState: SearchSessionState,
        managerOpen: Boolean,
        editor: SearchListEditorState,
        contextRevision: Long,
        copy: SearchListTransferCopy,
        isContextCurrent: () -> Boolean,
        onImportDraft: (String, List<String>) -> Unit,
    )

    internal fun contextChanged()

    internal fun importList()

    internal fun exportSelectedList()

    internal fun cancel()

    internal fun dispose()
}
