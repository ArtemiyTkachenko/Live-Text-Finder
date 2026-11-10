package com.artkachenko.livetextfinder.ui

import com.artkachenko.livetextfinder.search.SearchMatchMode

/** The query, active-list context and search actions shared by the dock and inline search bar. */
internal class FinderSearchBar(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val activeListName: String?,
    val activeListTermCount: Int,
    val matchMode: SearchMatchMode,
    val saving: Boolean,
    val searchListsLabel: String,
    val onSearchLists: () -> Unit,
    val onTerms: () -> Unit,
    val onUseManual: () -> Unit,
    val onResults: () -> Unit,
)

/**
 * The capture button's alternatives, in priority order: capture or resume, cancel a pending
 * capture, then a secondary source action. A null label hides that alternative.
 */
internal class FinderCaptureActions(
    val captureLabel: String?,
    val captureEnabled: Boolean,
    val onCapture: () -> Unit,
    val pendingCaptureLabel: String?,
    val onCancelCapture: () -> Unit,
    val secondaryActionLabel: String?,
    val onSecondaryAction: () -> Unit,
)
