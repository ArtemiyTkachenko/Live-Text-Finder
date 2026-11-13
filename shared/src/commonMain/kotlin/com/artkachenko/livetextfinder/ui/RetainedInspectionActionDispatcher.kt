package com.artkachenko.livetextfinder.ui

internal object RetainedInspectionActionDispatcher {
    fun toggleLists(
        managerOpen: Boolean,
        onContextChanged: () -> Unit,
        setManagerOpen: (Boolean) -> Unit,
        collapseSearch: () -> Unit,
        closeEditor: () -> Unit,
    ) {
        onContextChanged()
        setManagerOpen(!managerOpen)
        if (managerOpen) collapseSearch()
        closeEditor()
    }

    fun showTerms(
        setTermsOpen: (Boolean) -> Unit,
        setOccurrencesOpen: (Boolean) -> Unit,
    ) {
        setOccurrencesOpen(false)
        setTermsOpen(true)
    }

    fun showOccurrences(
        clearFocus: () -> Unit,
        hideKeyboard: () -> Unit,
        setTermsOpen: (Boolean) -> Unit,
        setOccurrencesOpen: (Boolean) -> Unit,
    ) {
        clearFocus()
        hideKeyboard()
        setTermsOpen(false)
        setOccurrencesOpen(true)
    }

    fun returnToSource(
        imported: Boolean,
        resetLiveArea: () -> Unit,
        cancelImport: () -> Unit,
        resumeFrozen: () -> Unit,
    ) {
        resetLiveArea()
        if (imported) cancelImport() else resumeFrozen()
    }
}
