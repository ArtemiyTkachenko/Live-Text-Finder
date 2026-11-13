package com.artkachenko.livetextfinder.search

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** Owns the active search and serializes asynchronous session updates. */
class SearchSessionController(private val store: SearchSessionStore) {
    val state: MutableState<SearchSessionState> = mutableStateOf(SearchSessionState())

    private var manualRevision = 0L
    private var persistedRevision = 0L
    private var writeInFlight = false
    private var queuedWrite: Pair<Long, PersistedSearchState>? = null
    private var queryValidationError: SearchSessionError? = null
    private var resetInFlight = false

    init {
        store.load(::finishLoad)
    }

    /** Updates the latest manual query and exits saved-list mode. */
    fun setManualQuery(query: String) {
        if (query.length > MAX_MANUAL_QUERY_LENGTH) {
            queryValidationError = SearchSessionError.ManualQueryTooLong
            val current = state.value
            val error = if (current.recoveryRequired) current.error else queryValidationError
            state.value = current.copy(error = error)
            return
        }
        manualRevision++
        queryValidationError = null
        val current = state.value
        update(
            current.persisted.copy(manualQuery = query, activeListId = null),
            queryValidationError,
        )
    }

    /** Selects a saved list, or returns to the preserved manual query. */
    fun selectList(id: String?) {
        if (!state.value.loaded) {
            return
        }
        if (id != null && state.value.persisted.lists.none { it.id == id }) {
            return
        }
        if (state.value.persisted.activeListId == id) {
            return
        }
        update(state.value.persisted.copy(activeListId = id))
    }

    /** Creates or updates a list. Returns a visible validation error when rejected. */
    fun saveList(id: String?, nameInput: String, termsInput: String): SearchSessionError? {
        if (!state.value.loaded || state.value.recoveryRequired) {
            return SearchSessionError.ListsUnavailable
        }
        val current = state.value.persisted
        validatePersistedSearchState(current)?.let { error -> return error }
        if (id != null && current.lists.none { it.id == id }) {
            return SearchSessionError.ListMissing
        }

        val name = nameInput.trim()
        if (name.isEmpty()) {
            return SearchSessionError.ListNameBlank
        }
        if (name.length > MAX_NAME_LENGTH) {
            return SearchSessionError.ListNameTooLong
        }
        if (current.lists.any { it.id != id && it.name.equals(name, ignoreCase = true) }) {
            return SearchSessionError.ListNameDuplicate
        }

        val terms = validateSearchListTerms(termsInput).getOrElse { error ->
            return error.toSearchSessionError(SearchSessionError.BlankTermLine)
        }
        if (id == null && current.lists.size >= MAX_LISTS) {
            return SearchSessionError.TooManyLists
        }

        val stableId = id ?: newListId()
        val previous = id?.let { listId -> current.lists.first { it.id == listId } }
        val slots = allocatePaletteSlots(terms, previous)
        val replacement = SavedSearchList(stableId, name, terms, slots)
        val lists = if (id == null) {
            current.lists + replacement
        } else {
            current.lists.map { list -> if (list.id == id) replacement else list }
        }
        val activeListId = if (current.activeListId == id) stableId else current.activeListId
        update(current.copy(lists = lists, activeListId = activeListId))
        return null
    }

    /** Deletes a saved list and returns to the preserved manual query if it was active. */
    fun deleteList(id: String) {
        if (!state.value.loaded) {
            return
        }
        val current = state.value.persisted
        if (current.lists.none { it.id == id }) {
            return
        }
        val lists = current.lists.filterNot { it.id == id }
        val activeListId = current.activeListId.takeUnless { it == id }
        update(current.copy(lists = lists, activeListId = activeListId))
    }

    /** Changes the global outline colour after the saved session is healthy. */
    fun setHighlightColor(color: HighlightColor) {
        val current = state.value
        if (!current.loaded || current.recoveryRequired ||
            current.persisted.highlightColor == color
        ) {
            return
        }
        update(current.persisted.copy(highlightColor = color))
    }

    /** Changes the literal matching behavior after the saved session is healthy. */
    fun setMatchMode(matchMode: SearchMatchMode) {
        val current = state.value
        if (!current.loaded || current.recoveryRequired ||
            current.persisted.matchMode == matchMode
        ) {
            return
        }
        update(current.persisted.copy(matchMode = matchMode))
    }

    /** Retries the latest save, reloads after an ordinary read failure, or confirms reset. */
    fun retry() {
        val current = state.value
        if (!current.loaded && !current.recoveryRequired) {
            store.load(::finishLoad)
        } else if (current.recoveryRequired) {
            if (resetInFlight) {
                return
            }
            resetInFlight = true
            val manualRevisionAtReset = manualRevision
            store.reset { result ->
                resetInFlight = false
                val latest = state.value
                if (result.isSuccess) {
                    val manualQuery = if (manualRevision > manualRevisionAtReset) {
                        latest.persisted.manualQuery
                    } else {
                        ""
                    }
                    state.value = SearchSessionState(
                        persisted = PersistedSearchState(
                            manualQuery,
                            emptyList(),
                            null,
                            HighlightColor.Yellow,
                        ),
                        loaded = true,
                    )
                    queryValidationError = null
                    queueSave()
                } else {
                    state.value = latest.copy(
                        error = result.exceptionOrNull()
                            .toSearchSessionError(SearchSessionError.StorageResetFailed),
                        recoveryRequired = true,
                        saving = false,
                    )
                }
            }
        } else {
            state.value = current.copy(error = null)
            queueSave()
        }
    }

    private fun finishLoad(result: Result<PersistedSearchState?>) {
        val before = state.value
        result.fold(
            onSuccess = { loadedValue ->
                if (loadedValue == null) {
                    state.value = before.copy(loaded = true, error = queryValidationError)
                    if (manualRevision > 0L) {
                        queueSave()
                    }
                } else {
                    val persisted = loadedValue.copy(
                        manualQuery = if (manualRevision == 0L) {
                            loadedValue.manualQuery
                        } else {
                            before.persisted.manualQuery
                        },
                        activeListId = if (manualRevision == 0L) loadedValue.activeListId else null,
                    )
                    state.value = SearchSessionState(
                        persisted = persisted,
                        loaded = true,
                        error = queryValidationError,
                    )
                    if (manualRevision > 0L) {
                        queueSave()
                    }
                }
            },
            onFailure = { failure ->
                state.value = before.copy(
                    loaded = true,
                    error = failure.toSearchSessionError(SearchSessionError.StorageUnreadable),
                    recoveryRequired = true,
                )
            },
        )
    }

    private fun update(
        persisted: PersistedSearchState,
        validationError: SearchSessionError? = null,
    ) {
        val old = state.value
        val error = when {
            old.recoveryRequired -> old.error
            validationError != null -> validationError
            else -> null
        }
        state.value = old.copy(persisted = persisted, error = error)
        if (old.loaded && !old.recoveryRequired) {
            queueSave()
        }
    }

    private fun queueSave() {
        val current = state.value
        if (!current.loaded || current.recoveryRequired) {
            return
        }
        val validationError = validatePersistedSearchState(current.persisted)
        if (validationError != null) {
            state.value = current.copy(saving = false, error = validationError)
            return
        }
        persistedRevision++
        queuedWrite = persistedRevision to current.persisted
        state.value = current.copy(saving = true, error = queryValidationError)
        startNextWrite()
    }

    private fun startNextWrite() {
        if (writeInFlight) {
            return
        }
        val write = queuedWrite
        if (write == null) {
            state.value = state.value.copy(saving = false)
            return
        }
        queuedWrite = null
        writeInFlight = true
        store.save(write.second) { result ->
            writeInFlight = false
            if (write.first == persistedRevision) {
                val error = when {
                    state.value.recoveryRequired -> state.value.error
                    queryValidationError != null -> queryValidationError
                    else -> result.exceptionOrNull()
                        ?.toSearchSessionError(SearchSessionError.StorageSaveFailed)
                }
                state.value = state.value.copy(saving = false, error = error)
            }
            if (queuedWrite != null) {
                startNextWrite()
            }
        }
    }

    private fun newListId(): String {
        val existing = state.value.persisted.lists.mapTo(mutableSetOf()) { it.id }
        while (true) {
            val candidate = "list-${nextId++}"
            if (candidate !in existing) {
                return candidate
            }
        }
    }

    private companion object {
        var nextId = 1L
    }
}

/** Selects a loaded list and invalidates pending transfers only for a changed selection. */
internal fun dispatchSearchListSelection(
    session: SearchSessionController,
    id: String?,
    onDifferentSelection: () -> Unit,
): Boolean {
    val current = session.state.value
    if (!current.loaded || current.persisted.activeListId == id ||
        (id != null && current.persisted.lists.none { it.id == id })
    ) {
        return false
    }
    onDifferentSelection()
    session.selectList(id)
    return true
}
