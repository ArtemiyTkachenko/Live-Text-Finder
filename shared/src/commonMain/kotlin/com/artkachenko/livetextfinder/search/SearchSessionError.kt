package com.artkachenko.livetextfinder.search

/** A user-visible saved-search problem. The UI resolves each value to localized text. */
enum class SearchSessionError {
    ManualQueryTooLong,
    ListsUnavailable,
    ListMissing,
    ListNameBlank,
    ListNameTooLong,
    ListNameDuplicate,
    TooManyLists,
    TooManyTerms,
    BlankTermLine,
    TermTooLong,
    DuplicateTerms,
    TooManyTermCharacters,
    StorageUnreadable,
    StorageSaveFailed,
    StorageResetFailed,
    SavedTooManyLists,
    SavedInvalidListId,
    SavedInvalidListName,
    SavedDuplicateListNames,
    SavedInvalidTermCount,
    SavedInvalidTermColors,
    SavedNoncanonicalTerm,
    SavedTermTooLong,
    SavedDuplicateTerms,
    SavedTooManyTermCharacters,
    SavedSelectedListMissing,
}

/** Carries a typed saved-search problem through a failed [Result] or a store callback. */
class SearchSessionException(val error: SearchSessionError) : IllegalArgumentException(error.name)

/** Returns the typed problem carried by this failure, or [fallback] for any other failure. */
internal fun Throwable?.toSearchSessionError(fallback: SearchSessionError): SearchSessionError =
    (this as? SearchSessionException)?.error ?: fallback
