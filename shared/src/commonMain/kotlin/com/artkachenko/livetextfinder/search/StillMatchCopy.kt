package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.RecognizedTextContent

internal data class StillMatchSourceState(
    val sourceIdentity: String?,
    val content: RecognizedTextContent?,
    val ownerToken: Any?,
)

/** Writes one selected match only while its source, snapshot and query still own the view. */
internal suspend fun copyStillMatch(
    row: StillMatchRow,
    result: TextSearchResult,
    expectedSourceIdentity: String,
    expectedSearchIdentity: String,
    selectedIndex: Int,
    expectedOwnerToken: Any,
    currentSource: () -> StillMatchSourceState,
    currentSearchIdentity: () -> String,
    currentSelectedIndex: () -> Int?,
    writeText: suspend (String) -> Unit,
    currentContextCurrent: () -> Boolean = { true },
): Boolean {
    if (row.index != selectedIndex || result.spec.identity != expectedSearchIdentity) {
        return false
    }
    val text = stillMatchText(row, result) ?: return false

    fun isCurrent(): Boolean {
        val current = currentSource()
        return current.sourceIdentity == expectedSourceIdentity &&
            current.content === result.content && current.ownerToken === expectedOwnerToken &&
            currentSearchIdentity() == expectedSearchIdentity &&
            currentSelectedIndex() == selectedIndex && currentContextCurrent()
    }

    if (!isCurrent()) return false
    writeText(text)
    return true
}
