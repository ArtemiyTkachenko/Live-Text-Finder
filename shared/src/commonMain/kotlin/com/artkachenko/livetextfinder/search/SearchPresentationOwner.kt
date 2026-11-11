package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState

/** Owns per-occurrence raw provenance and separate bounded live display geometry. */
internal class SearchPresentationOwner {
    private var searchIdentity: String? = null
    private var areaRevision = Long.MIN_VALUE
    private var contextIdentity: FrameIdentity? = null
    private var contextTarget: PreviewTarget? = null
    private val retiredIdentities = mutableSetOf<FrameIdentity>()
    private var processedSnapshot: OcrSnapshot? = null
    private var processedReceipt: Long? = null
    private var occurrences = emptyList<PresentedOccurrence>()

    fun present(query: String, state: OcrUiState, nowMillis: Long): SearchPresentation {
        return present(manualSearchSpec(query), state, nowMillis)
    }

    fun present(
        spec: ActiveSearchSpec,
        state: OcrUiState,
        nowMillis: Long,
        area: NormalizedSearchArea? = null,
        currentAreaRevision: Long = 0,
        viewportWidth: Int = state.projection?.target?.width ?: 0,
        viewportHeight: Int = state.projection?.target?.height ?: 0,
    ): SearchPresentation {
        if (areaRevision != currentAreaRevision) {
            clearOccurrences()
            processedSnapshot = null
            processedReceipt = null
            areaRevision = currentAreaRevision
        }
        if (searchIdentity != spec.identity) {
            searchIdentity = spec.identity
            clearOccurrences()
            processedSnapshot = null
            processedReceipt = null
        }

        val recognizedSearch = searchRecognizedText(spec, state)
        val filteredSearch = filterOccurrencesToArea(
            recognizedSearch,
            state.projection,
            area,
            viewportWidth,
            viewportHeight,
        )
        val areaIsReady = area == null || (
            filteredSearch != null &&
                (state.status == OcrStatus.Scanning || state.status == OcrStatus.TextRecognized) &&
                recognizedSearch.snapshot != null && state.receivedAtMillis != null &&
                isReceiptFresh(state.receivedAtMillis, nowMillis)
            )
        val latestSearch = if (areaIsReady) {
            filteredSearch ?: recognizedSearch
        } else {
            recognizedSearch.copy(occurrences = emptyList())
        }
        val snapshot = latestSearch.snapshot
        val projection = state.projection
        val receipt = state.receivedAtMillis
        if (!areaIsReady || filteredSearch == null || spec.terms.none {
                normalizeRecognizedText(it.text).isNotEmpty()
            }
        ) {
            clearOccurrences()
            processedSnapshot = null
            processedReceipt = null
            return result(
                spec,
                latestSearch,
                state,
                emptyList(),
                currentAreaRevision,
                areaIsReady,
            )
        }

        if (state.status != OcrStatus.Scanning && state.status != OcrStatus.TextRecognized) {
            contextIdentity?.let { retiredIdentities += it }
            clearContext()
            return result(spec, latestSearch, state, emptyList(), currentAreaRevision, true)
        }
        if (snapshot == null || receipt == null || !isReceiptFresh(receipt, nowMillis)) {
            clearOccurrences()
            processedSnapshot = snapshot
            processedReceipt = receipt
            return result(spec, latestSearch, state, emptyList(), currentAreaRevision, true)
        }
        if (projection == null || projection.snapshot !== snapshot) {
            contextIdentity?.let { retiredIdentities += it }
            clearContext()
            return result(spec, latestSearch, state, emptyList(), currentAreaRevision, true)
        }
        if (projection.identity in retiredIdentities) {
            clearOccurrences()
            processedSnapshot = snapshot
            processedReceipt = receipt
            return result(spec, latestSearch, state, emptyList(), currentAreaRevision, true)
        }

        if (contextIdentity != null && contextIdentity != projection.identity) {
            retiredIdentities += checkNotNull(contextIdentity)
            clearOccurrences()
            contextIdentity = projection.identity
            contextTarget = projection.target
            processedSnapshot = null
            processedReceipt = null
        } else if (
            contextIdentity == projection.identity && contextTarget != null &&
            contextTarget != projection.target
        ) {
            retiredIdentities += projection.identity
            clearContext()
            return result(spec, latestSearch, state, emptyList(), currentAreaRevision, true)
        } else if (contextIdentity == null) {
            clearOccurrences()
            contextIdentity = projection.identity
            contextTarget = projection.target
            processedSnapshot = null
            processedReceipt = null
        }

        // The identity matrix and dimensions are checked by the production projection helper.
        val geometry = projectHighlights(
            latestSearch,
            projection,
            projection.target.width,
            projection.target.height,
        )
        if (!geometry.ready) {
            clearOccurrences()
            processedSnapshot = snapshot
            processedReceipt = receipt
            return result(spec, latestSearch, state, emptyList(), currentAreaRevision, true)
        }

        if (snapshot !== processedSnapshot || receipt != processedReceipt) {
            val fresh = collectPresentedOccurrences(latestSearch, projection, receipt)
            occurrences = reconcileOccurrences(occurrences, fresh, receipt, nowMillis)
            processedSnapshot = snapshot
            processedReceipt = receipt
        }

        occurrences = occurrences.filter {
            it.expiresAtMillis?.let { deadline -> nowMillis < deadline } != false
        }
        return result(spec, latestSearch, state, occurrences, currentAreaRevision, true)
    }

    private fun result(
        spec: ActiveSearchSpec,
        search: TextSearchResult,
        state: OcrUiState,
        groups: List<PresentedOccurrence>,
        areaRevision: Long,
        areaReady: Boolean,
    ): SearchPresentation {
        val projection = state.projection?.takeIf { it.snapshot === search.snapshot }
        val elements = mutableListOf<PresentedElement>()
        groups.forEach { group ->
            group.outlines.forEachIndexed { outlineIndex, outline ->
                val duplicate = elements.any { existing ->
                    existing.snapshot === group.snapshot &&
                        existing.outline.lineIndex == outline.lineIndex &&
                        existing.outline.elementIndex == outline.elementIndex &&
                        existing.group.termId == group.termId
                }
                if (!duplicate) {
                    elements += PresentedElement(
                        snapshot = group.snapshot,
                        projection = group.projection,
                        outline = outline,
                        group = group,
                        displayOutline = group.displayOutlines.getOrNull(outlineIndex) ?: outline,
                    )
                }
            }
        }
        return SearchPresentation(
            query = search.query,
            activeSearchIdentity = spec.identity,
            search = search,
            occurrences = groups,
            observedSnapshot = state.snapshot,
            observedReceiptAtMillis = state.receivedAtMillis,
            observedProjection = projection,
            expiresAtMillis = groups.mapNotNull { it.expiresAtMillis }.minOrNull(),
            outlinedElements = elements,
            areaRevision = areaRevision,
            areaReady = areaReady,
        )
    }

    private fun clearContext() {
        clearOccurrences()
        contextIdentity = null
        contextTarget = null
        processedSnapshot = null
        processedReceipt = null
    }

    private fun clearOccurrences() { occurrences = emptyList() }

    companion object {
        const val MATCH_GRACE_MILLIS = 300L
        const val RESULT_AGE_MILLIS = 1_500L
    }
}
