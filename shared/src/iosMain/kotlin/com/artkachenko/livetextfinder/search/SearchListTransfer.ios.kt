package com.artkachenko.livetextfinder.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberSearchListTransferOwner(): SearchListTransferOwner =
    remember { SearchListTransferOwner() }

internal actual class SearchListTransferOwner actual constructor() {
    private val mutableState = mutableStateOf(SearchListTransferState())
    internal actual val state: State<SearchListTransferState> = mutableState

    internal actual fun updateContext(
        sessionState: SearchSessionState,
        managerOpen: Boolean,
        editor: SearchListEditorState,
        contextRevision: Long,
        copy: SearchListTransferCopy,
        isContextCurrent: () -> Boolean,
        onImportDraft: (String, List<String>) -> Unit,
    ) = Unit

    internal actual fun contextChanged() = Unit

    internal actual fun importList() = Unit

    internal actual fun exportSelectedList() = Unit

    internal actual fun cancel() = Unit

    internal actual fun dispose() = Unit
}
