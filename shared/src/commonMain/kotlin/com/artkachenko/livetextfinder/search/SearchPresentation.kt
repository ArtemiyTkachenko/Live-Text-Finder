package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.OcrSnapshot

/** One matched occurrence with its raw source provenance and bounded display geometry. */
internal data class PresentedOccurrence(
    val termId: String,
    val key: List<String>,
    val snapshot: OcrSnapshot,
    val projection: FrameProjection,
    val occurrence: TextOccurrence,
    val outlines: List<ElementOutline>,
    val displayOutlines: List<ElementOutline> = outlines,
    val receiptAtMillis: Long,
    val lastSeenAtMillis: Long,
    val firstMissingAtMillis: Long?,
    val expiresAtMillis: Long?,
)

internal data class SearchPresentation(
    val query: String,
    val activeSearchIdentity: String,
    val search: TextSearchResult,
    val occurrences: List<PresentedOccurrence>,
    val observedSnapshot: OcrSnapshot?,
    val observedReceiptAtMillis: Long?,
    val observedProjection: FrameProjection?,
    val expiresAtMillis: Long?,
    val outlinedElements: List<PresentedElement>,
    val areaRevision: Long = 0,
    val areaReady: Boolean = true,
)

internal fun isPresentationCurrentAreaRevision(
    presentation: SearchPresentation,
    currentRevision: Long,
): Boolean = presentation.areaRevision == currentRevision

internal data class PresentedElement(
    val snapshot: OcrSnapshot,
    val projection: FrameProjection,
    val outline: ElementOutline,
    val group: PresentedOccurrence,
    val displayOutline: ElementOutline = outline,
)

/** Returns whether a live result received at [receipt] is still young enough to present. */
internal fun isReceiptFresh(receipt: Long, nowMillis: Long): Boolean =
    nowMillis >= receipt && nowMillis - receipt < SearchPresentationOwner.RESULT_AGE_MILLIS
