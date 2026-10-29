package com.artkachenko.livetextfinder.search

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

internal const val MAX_LISTS = 50
internal const val MAX_MANUAL_QUERY_LENGTH = 10_000
internal const val MAX_NAME_LENGTH = 60
internal const val MAX_TERMS_PER_LIST = 32
internal const val MAX_TERM_LENGTH = 120
internal const val MAX_TOTAL_TERM_CHARS = 3_000

/** A presentation-only view of the exact normalized line parsing used when saving a list. */
internal data class SearchListTermsPreview(
    val terms: List<String>,
    val blankLineCount: Int,
)

internal fun previewSearchListTerms(input: String): SearchListTermsPreview {
    val lines = input.lines()
    val terms = lines.map(::normalizeRecognizedText)
    return SearchListTermsPreview(
        terms = terms.filter(String::isNotEmpty),
        blankLineCount = if (input.isEmpty()) 0 else terms.count(String::isEmpty),
    )
}

/** A reusable named search containing independent literal terms or phrases. */
data class SavedSearchList(val id: String, val name: String, val terms: List<String>)

/** The app-owned values that are persisted across Android launches. */
data class PersistedSearchState(
    val manualQuery: String,
    val lists: List<SavedSearchList>,
    val activeListId: String?,
    val highlightColor: HighlightColor = HighlightColor.Yellow,
)

/** The current load, search, and persistence state shown by the interface. */
data class SearchSessionState(
    val persisted: PersistedSearchState = PersistedSearchState("", emptyList(), null),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val recoveryRequired: Boolean = false,
) {
    val activeList: SavedSearchList?
        get() = persisted.lists.firstOrNull { it.id == persisted.activeListId }

    val activeSearch: ActiveSearchSpec
        get() {
            val list = activeList
            if (list == null) {
                return manualSearchSpec(persisted.manualQuery)
            }

            val terms = list.terms.mapIndexed { index, text ->
                SearchTerm("${list.id}:$index", text)
            }
            val identity = buildString {
                append("list\u0000").append(list.id).append('\u0000')
                terms.forEach { term -> append(term.text.length).append(':').append(term.text) }
            }
            return ActiveSearchSpec(identity, list.name, terms)
        }
}

/** Storage boundary for the app-owned search session. Callbacks must return on the UI thread. */
interface SearchSessionStore {
    fun load(callback: (Result<PersistedSearchState?>) -> Unit)

    fun save(value: PersistedSearchState, callback: (Result<Unit>) -> Unit)

    fun reset(callback: (Result<Unit>) -> Unit)
}

/** In-memory storage used by the iOS placeholder. */
class InMemorySearchSessionStore : SearchSessionStore {
    private var value: PersistedSearchState? = null

    override fun load(callback: (Result<PersistedSearchState?>) -> Unit) {
        callback(Result.success(value))
    }

    override fun save(value: PersistedSearchState, callback: (Result<Unit>) -> Unit) {
        this.value = value
        callback(Result.success(Unit))
    }

    override fun reset(callback: (Result<Unit>) -> Unit) {
        value = null
        callback(Result.success(Unit))
    }
}

/** Creates an in-memory session for the iOS placeholder. */
fun inMemorySearchSessionController(): SearchSessionController =
    SearchSessionController(InMemorySearchSessionStore())

/** Validates the exact canonical shape accepted by the Android JSON store. */
internal fun validatePersistedSearchState(value: PersistedSearchState): String? {
    if (value.manualQuery.length > MAX_MANUAL_QUERY_LENGTH) {
        return "Manual queries can contain at most $MAX_MANUAL_QUERY_LENGTH characters."
    }
    if (value.lists.size > MAX_LISTS) {
        return "Saved search data contains too many lists."
    }

    val seenIds = mutableSetOf<String>()
    val seenNames = mutableListOf<String>()
    for (list in value.lists) {
        if (list.id.length !in 1..100 || !seenIds.add(list.id)) {
            return "Saved search data contains an invalid or duplicate list ID."
        }
        if (list.name.isBlank() || list.name != list.name.trim() ||
            list.name.length > MAX_NAME_LENGTH
        ) {
            return "Saved search data contains an invalid list name."
        }
        if (seenNames.any { it.equals(list.name, ignoreCase = true) }) {
            return "Saved search data contains duplicate list names."
        }
        seenNames.add(list.name)
        if (list.terms.size !in 1..MAX_TERMS_PER_LIST) {
            return "Saved search data contains an invalid term count."
        }

        var totalTermChars = 0
        val seenTerms = mutableListOf<String>()
        for (term in list.terms) {
            if (term.isBlank() || term != normalizeRecognizedText(term)) {
                return "Saved search data contains a blank or noncanonical term."
            }
            if (term.length > MAX_TERM_LENGTH) {
                return "Saved search data contains a term that is too long."
            }
            if (seenTerms.any { it.equals(term, ignoreCase = true) }) {
                return "Saved search data contains duplicate terms."
            }
            seenTerms.add(term)
            totalTermChars += term.length
        }
        if (totalTermChars > MAX_TOTAL_TERM_CHARS) {
            return "Saved search data contains too many term characters."
        }
    }

    if (value.activeListId != null && seenIds.none { it == value.activeListId }) {
        return "The selected saved search list is missing."
    }
    return null
}

/** Owns the active search and serializes asynchronous session updates. */
class SearchSessionController(private val store: SearchSessionStore) {
    val state: MutableState<SearchSessionState> = mutableStateOf(SearchSessionState())

    private var manualRevision = 0L
    private var persistedRevision = 0L
    private var writeInFlight = false
    private var queuedWrite: Pair<Long, PersistedSearchState>? = null
    private var queryValidationError: String? = null
    private var resetInFlight = false

    init {
        store.load(::finishLoad)
    }

    /** Updates the latest manual query and exits saved-list mode. */
    fun setManualQuery(query: String) {
        if (query.length > MAX_MANUAL_QUERY_LENGTH) {
            queryValidationError =
                "Manual queries can contain at most $MAX_MANUAL_QUERY_LENGTH characters."
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
        update(state.value.persisted.copy(activeListId = id))
    }

    /** Creates or updates a list. Returns a visible validation error when rejected. */
    fun saveList(id: String?, nameInput: String, termsInput: String): String? {
        if (!state.value.loaded || state.value.recoveryRequired) {
            return "Search lists are unavailable until storage is recovered."
        }
        val current = state.value.persisted
        if (id != null && current.lists.none { it.id == id }) {
            return "That saved list no longer exists. Reopen the editor before saving."
        }

        val name = nameInput.trim()
        if (name.isEmpty()) {
            return "Enter a list name."
        }
        if (name.length > MAX_NAME_LENGTH) {
            return "List names can contain at most $MAX_NAME_LENGTH characters."
        }
        if (current.lists.any { it.id != id && it.name.equals(name, ignoreCase = true) }) {
            return "That list name is already in use."
        }

        val rawLines = termsInput.lines()
        if (rawLines.size > MAX_TERMS_PER_LIST) {
            return "Lists can contain at most $MAX_TERMS_PER_LIST terms."
        }
        val terms = rawLines.map(::normalizeRecognizedText)
        if (terms.any { it.isEmpty() }) {
            return "Remove blank term lines. Each line must contain a term or phrase."
        }
        if (terms.any { it.length > MAX_TERM_LENGTH }) {
            return "Each term can contain at most $MAX_TERM_LENGTH characters."
        }
        if (terms.indices.any { index ->
                terms.take(index).any { it.equals(terms[index], ignoreCase = true) }
            }
        ) {
            return "Terms must be unique, ignoring case."
        }
        if (terms.sumOf { it.length } > MAX_TOTAL_TERM_CHARS) {
            return "A list can contain at most $MAX_TOTAL_TERM_CHARS total term characters."
        }
        if (id == null && current.lists.size >= MAX_LISTS) {
            return "You can save at most $MAX_LISTS lists."
        }

        val stableId = id ?: newListId()
        val replacement = SavedSearchList(stableId, name, terms)
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
                        error = result.exceptionOrNull()?.message ?: "Storage reset failed.",
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
                    error = failure.message ?: "Saved search data could not be read.",
                    recoveryRequired = true,
                )
            },
        )
    }

    private fun update(persisted: PersistedSearchState, validationError: String? = null) {
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
                    else -> result.exceptionOrNull()?.message
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
