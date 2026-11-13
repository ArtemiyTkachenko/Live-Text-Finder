package com.artkachenko.livetextfinder.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.artkachenko.livetextfinder.search.SearchListTransferOwner
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.SearchSessionState

/**
 * Publishes the current saved-search and list-manager context to [transferOwner] after every
 * composition, so a list import or export started from an older context is rejected.
 *
 * The published predicate captures the manager's visibility, draft and revision and compares
 * them with live values, which covers a change that has not been recomposed yet.
 */
@Composable
internal fun SearchListTransferContextEffect(
    transferOwner: SearchListTransferOwner,
    session: SearchSessionController,
    sessionState: SearchSessionState,
    searchEditing: SavedSearchEditingState,
) {
    val transferCopy = searchListTransferCopy()
    SideEffect {
        val managerOpen = searchEditing.isOpen
        val editor = searchEditing.editor
        val contextRevision = searchEditing.contextRevision
        transferOwner.updateContext(
            sessionState = sessionState,
            managerOpen = managerOpen,
            editor = editor,
            contextRevision = contextRevision,
            copy = transferCopy,
            isContextCurrent = {
                session.state.value == sessionState &&
                    searchEditing.isOpen == managerOpen &&
                    searchEditing.editor == editor &&
                    searchEditing.contextRevision == contextRevision
            },
            onImportDraft = searchEditing::acceptImportedDraft,
        )
    }
}
