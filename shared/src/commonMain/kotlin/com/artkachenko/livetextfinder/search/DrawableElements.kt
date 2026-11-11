package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.OcrUiState

/** Filters entries against the latest callback at draw time and preserves per-source expiry. */
internal fun drawableElements(
    currentQuery: String,
    composedQuery: String,
    presentation: SearchPresentation,
    latestState: OcrUiState,
    nowMillis: Long,
    overlayWidth: Float,
    overlayHeight: Float,
): List<PresentedElement> {
    return drawableElementsForSearch(
        manualSearchSpec(currentQuery).identity,
        manualSearchSpec(composedQuery).identity,
        presentation,
        latestState,
        nowMillis,
        overlayWidth,
        overlayHeight,
    )
}

internal fun drawableElementsForSearch(
    currentSearchIdentity: String,
    composedSearchIdentity: String,
    presentation: SearchPresentation,
    latestState: OcrUiState,
    nowMillis: Long,
    overlayWidth: Float,
    overlayHeight: Float,
): List<PresentedElement> {
    val latestProjection = latestState.projection ?: return emptyList()
    val latestSnapshot = latestState.snapshot ?: return emptyList()
    val latestReceipt = latestState.receivedAtMillis ?: return emptyList()
    if (currentSearchIdentity != composedSearchIdentity ||
        currentSearchIdentity != presentation.activeSearchIdentity ||
        latestSnapshot !== presentation.observedSnapshot ||
        latestReceipt != presentation.observedReceiptAtMillis ||
        presentation.observedProjection?.identity != latestProjection.identity ||
        presentation.observedProjection.target != latestProjection.target ||
        latestProjection.snapshot !== latestSnapshot || nowMillis < latestReceipt ||
        nowMillis - latestReceipt >= SearchPresentationOwner.RESULT_AGE_MILLIS
    ) return emptyList()

    return presentation.outlinedElements.filter { element ->
        val group = element.group
        val sourceReceipt = group.receiptAtMillis
        val sourceAgeIsValid = nowMillis >= sourceReceipt &&
            nowMillis - sourceReceipt < SearchPresentationOwner.RESULT_AGE_MILLIS
        val targetIsCurrent = group.projection.identity == latestProjection.identity &&
            group.projection.target == latestProjection.target
        val sourceIsEligible = if (group.firstMissingAtMillis == null) {
            group.snapshot === latestSnapshot && sourceReceipt == latestReceipt
        } else {
            group.expiresAtMillis?.let { nowMillis < it } == true
        }
        val outlineIndex = group.outlines.indexOf(element.outline)
        val displayPairIsCurrent = outlineIndex >= 0 &&
            group.displayOutlines.getOrNull(outlineIndex) == element.displayOutline
        element.snapshot === group.snapshot && group.projection.snapshot === group.snapshot &&
            displayPairIsCurrent && sourceAgeIsValid && targetIsCurrent &&
            sourceIsEligible &&
            overlayWidth == group.projection.target.width.toFloat() &&
            overlayHeight == group.projection.target.height.toFloat()
    }
}

internal fun drawableElementsForSession(
    session: SearchSessionController,
    composedSearch: ActiveSearchSpec,
    presentation: SearchPresentation,
    latestState: OcrUiState,
    nowMillis: Long,
    overlayWidth: Float,
    overlayHeight: Float,
): List<PresentedElement> = drawableElementsForSearch(
    session.state.value.activeSearch.identity,
    composedSearch.identity,
    presentation,
    latestState,
    nowMillis,
    overlayWidth,
    overlayHeight,
)
