package com.artkachenko.livetextfinder.search

/** Keeps a list editor's target and draft together while the manager is open. */
internal data class SearchListEditorState(
    val isOpen: Boolean = false,
    val editingId: String? = null,
    val name: String = "",
    val terms: String = "",
    val error: String? = null,
) {
    fun startNew(): SearchListEditorState = SearchListEditorState(isOpen = true)

    fun edit(list: SavedSearchList): SearchListEditorState = SearchListEditorState(
        isOpen = true,
        editingId = list.id,
        name = list.name,
        terms = list.terms.joinToString("\n"),
    )

    fun close(): SearchListEditorState = SearchListEditorState()
}
