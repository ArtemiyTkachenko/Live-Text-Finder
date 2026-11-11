package com.artkachenko.livetextfinder.search

/** A reusable named search containing independent literal terms or phrases. */
data class SavedSearchList(
    val id: String,
    val name: String,
    val terms: List<String>,
    val paletteSlots: List<Int> = legacyPaletteSlots(terms),
)

/** The app-owned values that are persisted across Android launches. */
data class PersistedSearchState(
    val manualQuery: String,
    val lists: List<SavedSearchList>,
    val activeListId: String?,
    val highlightColor: HighlightColor = HighlightColor.Yellow,
    val matchMode: SearchMatchMode = SearchMatchMode.Substring,
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
                return manualSearchSpec(persisted.manualQuery, persisted.matchMode)
            }

            check(list.paletteSlots.size == list.terms.size &&
                list.paletteSlots.all { it in SearchHighlightPalette.colors.indices } &&
                list.paletteSlots.toSet().size == list.paletteSlots.size
            ) {
                "The active list has invalid palette slots."
            }
            val terms = list.terms.mapIndexed { index, text ->
                SearchTerm("${list.id}:$index", text, list.paletteSlots[index])
            }
            val identity = buildString {
                append("list\u0000").append(list.id).append('\u0000')
                append(persisted.matchMode.storageKey).append('\u0000')
                terms.forEach { term ->
                    append(term.text.length).append(':').append(term.text)
                    append('@').append(term.paletteSlot).append(';')
                }
            }
            return ActiveSearchSpec(identity, list.name, terms, persisted.matchMode)
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
