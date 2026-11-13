package com.artkachenko.livetextfinder.ui

import androidx.compose.runtime.Composable
import com.artkachenko.livetextfinder.search.MAX_LISTS
import com.artkachenko.livetextfinder.search.MAX_MANUAL_QUERY_LENGTH
import com.artkachenko.livetextfinder.search.MAX_NAME_LENGTH
import com.artkachenko.livetextfinder.search.MAX_TERMS_PER_LIST
import com.artkachenko.livetextfinder.search.MAX_TERM_LENGTH
import com.artkachenko.livetextfinder.search.MAX_TOTAL_TERM_CHARS
import com.artkachenko.livetextfinder.search.SearchSessionError
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_error_blank_term_line
import livetextfinder.shared.generated.resources.search_error_duplicate_terms
import livetextfinder.shared.generated.resources.search_error_list_missing
import livetextfinder.shared.generated.resources.search_error_list_name_blank
import livetextfinder.shared.generated.resources.search_error_list_name_duplicate
import livetextfinder.shared.generated.resources.search_error_list_name_too_long
import livetextfinder.shared.generated.resources.search_error_lists_unavailable
import livetextfinder.shared.generated.resources.search_error_query_too_long
import livetextfinder.shared.generated.resources.search_error_saved_duplicate_list_names
import livetextfinder.shared.generated.resources.search_error_saved_duplicate_terms
import livetextfinder.shared.generated.resources.search_error_saved_invalid_list_id
import livetextfinder.shared.generated.resources.search_error_saved_invalid_list_name
import livetextfinder.shared.generated.resources.search_error_saved_invalid_term_colors
import livetextfinder.shared.generated.resources.search_error_saved_invalid_term_count
import livetextfinder.shared.generated.resources.search_error_saved_noncanonical_term
import livetextfinder.shared.generated.resources.search_error_saved_selected_list_missing
import livetextfinder.shared.generated.resources.search_error_saved_term_too_long
import livetextfinder.shared.generated.resources.search_error_saved_too_many_lists
import livetextfinder.shared.generated.resources.search_error_saved_too_many_term_characters
import livetextfinder.shared.generated.resources.search_error_storage_reset_failed
import livetextfinder.shared.generated.resources.search_error_storage_save_failed
import livetextfinder.shared.generated.resources.search_error_storage_unreadable
import livetextfinder.shared.generated.resources.search_error_term_too_long
import livetextfinder.shared.generated.resources.search_error_too_many_lists
import livetextfinder.shared.generated.resources.search_error_too_many_term_characters
import livetextfinder.shared.generated.resources.search_error_too_many_terms
import org.jetbrains.compose.resources.stringResource

/** Returns the localized message for a saved-search [error], including its numeric limit. */
@Composable
internal fun searchSessionErrorMessage(error: SearchSessionError): String =
    when (error) {
        SearchSessionError.ManualQueryTooLong ->
            stringResource(Res.string.search_error_query_too_long, MAX_MANUAL_QUERY_LENGTH)
        SearchSessionError.ListsUnavailable ->
            stringResource(Res.string.search_error_lists_unavailable)
        SearchSessionError.ListMissing ->
            stringResource(Res.string.search_error_list_missing)
        SearchSessionError.ListNameBlank ->
            stringResource(Res.string.search_error_list_name_blank)
        SearchSessionError.ListNameTooLong ->
            stringResource(Res.string.search_error_list_name_too_long, MAX_NAME_LENGTH)
        SearchSessionError.ListNameDuplicate ->
            stringResource(Res.string.search_error_list_name_duplicate)
        SearchSessionError.TooManyLists ->
            stringResource(Res.string.search_error_too_many_lists, MAX_LISTS)
        SearchSessionError.TooManyTerms ->
            stringResource(Res.string.search_error_too_many_terms, MAX_TERMS_PER_LIST)
        SearchSessionError.BlankTermLine ->
            stringResource(Res.string.search_error_blank_term_line)
        SearchSessionError.TermTooLong ->
            stringResource(Res.string.search_error_term_too_long, MAX_TERM_LENGTH)
        SearchSessionError.DuplicateTerms ->
            stringResource(Res.string.search_error_duplicate_terms)
        SearchSessionError.TooManyTermCharacters ->
            stringResource(Res.string.search_error_too_many_term_characters, MAX_TOTAL_TERM_CHARS)
        SearchSessionError.StorageUnreadable ->
            stringResource(Res.string.search_error_storage_unreadable)
        SearchSessionError.StorageSaveFailed ->
            stringResource(Res.string.search_error_storage_save_failed)
        SearchSessionError.StorageResetFailed ->
            stringResource(Res.string.search_error_storage_reset_failed)
        SearchSessionError.SavedTooManyLists ->
            stringResource(Res.string.search_error_saved_too_many_lists)
        SearchSessionError.SavedInvalidListId ->
            stringResource(Res.string.search_error_saved_invalid_list_id)
        SearchSessionError.SavedInvalidListName ->
            stringResource(Res.string.search_error_saved_invalid_list_name)
        SearchSessionError.SavedDuplicateListNames ->
            stringResource(Res.string.search_error_saved_duplicate_list_names)
        SearchSessionError.SavedInvalidTermCount ->
            stringResource(Res.string.search_error_saved_invalid_term_count)
        SearchSessionError.SavedInvalidTermColors ->
            stringResource(Res.string.search_error_saved_invalid_term_colors)
        SearchSessionError.SavedNoncanonicalTerm ->
            stringResource(Res.string.search_error_saved_noncanonical_term)
        SearchSessionError.SavedTermTooLong ->
            stringResource(Res.string.search_error_saved_term_too_long)
        SearchSessionError.SavedDuplicateTerms ->
            stringResource(Res.string.search_error_saved_duplicate_terms)
        SearchSessionError.SavedTooManyTermCharacters ->
            stringResource(Res.string.search_error_saved_too_many_term_characters)
        SearchSessionError.SavedSelectedListMissing ->
            stringResource(Res.string.search_error_saved_selected_list_missing)
    }
