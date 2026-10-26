package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Owns per-occurrence presentation and never combines geometry from different OCR snapshots. */
internal class SearchPresentationOwner {
    private var query: String? = null
    private var contextIdentity: FrameIdentity? = null
    private var contextTarget: PreviewTarget? = null
    private val retiredIdentities = mutableSetOf<FrameIdentity>()
    private var processedSnapshot: OcrSnapshot? = null
    private var processedReceipt: Long? = null
    private var occurrences = emptyList<PresentedOccurrence>()

    fun present(query: String, state: OcrUiState, nowMillis: Long): SearchPresentation {
        if (this.query != query) {
            this.query = query
            clearOccurrences()
            processedSnapshot = null
            processedReceipt = null
        }

        val latestSearch = searchRecognizedText(query, state)
        val snapshot = latestSearch.snapshot
        val projection = state.projection
        val receipt = state.receivedAtMillis
        if (query.isBlank()) {
            clearOccurrences()
            processedSnapshot = snapshot
            processedReceipt = receipt
            return result(query, latestSearch, state, emptyList())
        }

        if (state.status != OcrStatus.Scanning && state.status != OcrStatus.TextRecognized) {
            contextIdentity?.let { retiredIdentities += it }
            clearContext()
            return result(query, latestSearch, state, emptyList())
        }
        if (snapshot == null || receipt == null || !isReceiptFresh(receipt, nowMillis)) {
            clearOccurrences()
            processedSnapshot = snapshot
            processedReceipt = receipt
            return result(query, latestSearch, state, emptyList())
        }
        if (projection == null || projection.snapshot !== snapshot) {
            contextIdentity?.let { retiredIdentities += it }
            clearContext()
            return result(query, latestSearch, state, emptyList())
        }
        if (projection.identity in retiredIdentities) {
            clearOccurrences()
            processedSnapshot = snapshot
            processedReceipt = receipt
            return result(query, latestSearch, state, emptyList())
        }

        if (contextIdentity != null && contextIdentity != projection.identity) {
            retiredIdentities += checkNotNull(contextIdentity)
            clearOccurrences()
            contextIdentity = projection.identity
            contextTarget = projection.target
            processedSnapshot = null
            processedReceipt = null
        } else if (contextIdentity == projection.identity && contextTarget != null && contextTarget != projection.target) {
            retiredIdentities += projection.identity
            clearContext()
            return result(query, latestSearch, state, emptyList())
        } else if (contextIdentity == null) {
            clearOccurrences()
            contextIdentity = projection.identity
            contextTarget = projection.target
            processedSnapshot = null
            processedReceipt = null
        }

        // The identity matrix and dimensions are checked by the production projection helper.
        val geometry = projectHighlights(latestSearch, projection, projection.target.width, projection.target.height)
        if (!geometry.ready) {
            clearOccurrences()
            processedSnapshot = snapshot
            processedReceipt = receipt
            return result(query, latestSearch, state, emptyList())
        }

        if (snapshot !== processedSnapshot || receipt != processedReceipt) {
            val fresh = collectOccurrences(latestSearch, projection, receipt)
            occurrences = reconcile(occurrences, fresh, receipt, nowMillis)
            processedSnapshot = snapshot
            processedReceipt = receipt
        }

        occurrences = occurrences.filter { it.expiresAtMillis?.let { deadline -> nowMillis < deadline } != false }
        return result(query, latestSearch, state, occurrences)
    }

    private fun collectOccurrences(
        search: TextSearchResult,
        projection: FrameProjection,
        receipt: Long,
    ): List<PresentedOccurrence> {
        val snapshot = checkNotNull(search.snapshot)
        return search.occurrences.mapNotNull { occurrence ->
            val occurrenceSearch = search.copy(occurrences = listOf(occurrence))
            val outlines = projectHighlights(
                occurrenceSearch,
                projection,
                projection.target.width,
                projection.target.height,
            ).outlines
            if (outlines.isEmpty()) return@mapNotNull null
            val line = snapshot.lines.getOrNull(occurrence.lineIndex) ?: return@mapNotNull null
            val key = occurrence.elementIndices.mapNotNull { line.elements.getOrNull(it)?.text }
                .map { normalizeRecognizedText(it).lowercase() }
            if (key.isEmpty()) return@mapNotNull null
            PresentedOccurrence(
                key = key,
                snapshot = snapshot,
                projection = projection,
                occurrence = occurrence,
                outlines = outlines,
                receiptAtMillis = receipt,
                lastSeenAtMillis = receipt,
                firstMissingAtMillis = null,
                expiresAtMillis = null,
            )
        }
    }

    private fun reconcile(
        previous: List<PresentedOccurrence>,
        fresh: List<PresentedOccurrence>,
        receipt: Long,
        nowMillis: Long,
    ): List<PresentedOccurrence> {
        val eligiblePrevious = previous.filter { group ->
            isReceiptFresh(group.receiptAtMillis, nowMillis) &&
                group.expiresAtMillis?.let { nowMillis < it } != false
        }
        val matching = associate(eligiblePrevious, fresh)
        val freshForOld = matching.pairs.associate { it.oldIndex to it.newIndex }
        val oldForFresh = matching.pairs.associate { it.newIndex to it.oldIndex }
        val ambiguousOld = matching.ambiguousOld

        val retained = eligiblePrevious.mapIndexedNotNull { oldIndex, old ->
            if (oldIndex in freshForOld || oldIndex in ambiguousOld) return@mapIndexedNotNull null

            val suppressForSegmentation = fresh.any { new ->
                overlapOfSmallerArea(old.bounds(), new.bounds()) >= SEGMENT_OVERLAP
            }
            val suppressDisplacedCopy = fresh.indices.any { newIndex ->
                newIndex !in oldForFresh && fresh[newIndex].key == old.key
            }
            if (suppressForSegmentation || suppressDisplacedCopy) return@mapIndexedNotNull null

            val firstMissing = old.firstMissingAtMillis ?: receipt
            val expiry = minOf(
                firstMissing + MATCH_GRACE_MILLIS,
                old.lastSeenAtMillis + RESULT_AGE_MILLIS,
            )
            if (nowMillis >= expiry) null else old.copy(
                firstMissingAtMillis = firstMissing,
                expiresAtMillis = expiry,
            )
        }
        // Every fresh group becomes visible immediately with its own exact source frame.
        return fresh + retained
    }

    private fun associate(
        old: List<PresentedOccurrence>,
        fresh: List<PresentedOccurrence>,
    ): Association {
        val candidates = mutableListOf<Candidate>()
        for (oldIndex in old.indices) {
            for (newIndex in fresh.indices) {
                if (old[oldIndex].key != fresh[newIndex].key) continue
                val score = associationScore(old[oldIndex].bounds(), fresh[newIndex].bounds()) ?: continue
                candidates += Candidate(oldIndex, newIndex, score)
            }
        }
        val ambiguousOld = mutableSetOf<Int>()
        val ambiguousNew = mutableSetOf<Int>()
        val oldBest = candidates.groupBy { it.oldIndex }.mapValues { (_, values) -> best(values) }
        val newBest = candidates.groupBy { it.newIndex }.mapValues { (_, values) -> best(values) }
        oldBest.values.filter { it.size > 1 }.flatten().forEach { ambiguousOld += it.oldIndex }
        newBest.values.filter { it.size > 1 }.flatten().forEach { ambiguousOld += it.oldIndex }
        newBest.values.filter { it.size > 1 }.flatten().forEach { ambiguousNew += it.newIndex }

        val pairs = candidates.filter { candidate ->
            candidate.oldIndex !in ambiguousOld && candidate.newIndex !in ambiguousNew &&
                oldBest[candidate.oldIndex]?.singleOrNull() == candidate &&
                newBest[candidate.newIndex]?.singleOrNull() == candidate
        }.map { Match(it.oldIndex, it.newIndex) }
        return Association(pairs, ambiguousOld)
    }

    private fun best(candidates: List<Candidate>): List<Candidate> {
        val highest = candidates.maxOfOrNull { it.iou } ?: return emptyList()
        return candidates.filter { highest - it.iou <= SCORE_EPSILON }
    }

    private fun result(
        query: String,
        search: TextSearchResult,
        state: OcrUiState,
        groups: List<PresentedOccurrence>,
    ): SearchPresentation {
        val projection = state.projection?.takeIf { it.snapshot === search.snapshot }
        val elements = mutableListOf<PresentedElement>()
        groups.forEach { group ->
            group.outlines.forEach { outline ->
                val duplicate = elements.any { existing ->
                    existing.snapshot === group.snapshot && existing.outline.lineIndex == outline.lineIndex &&
                        existing.outline.elementIndex == outline.elementIndex
                }
                if (!duplicate) elements += PresentedElement(group.snapshot, group.projection, outline, group)
            }
        }
        return SearchPresentation(
            query = query,
            search = search,
            occurrences = groups,
            observedSnapshot = state.snapshot,
            observedReceiptAtMillis = state.receivedAtMillis,
            observedProjection = projection,
            expiresAtMillis = groups.mapNotNull { it.expiresAtMillis }.minOrNull(),
            outlinedElements = elements,
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

internal data class PresentedOccurrence(
    val key: List<String>,
    val snapshot: OcrSnapshot,
    val projection: FrameProjection,
    val occurrence: TextOccurrence,
    val outlines: List<ElementOutline>,
    val receiptAtMillis: Long,
    val lastSeenAtMillis: Long,
    val firstMissingAtMillis: Long?,
    val expiresAtMillis: Long?,
)

internal data class SearchPresentation(
    val query: String,
    val search: TextSearchResult,
    val occurrences: List<PresentedOccurrence>,
    val observedSnapshot: OcrSnapshot?,
    val observedReceiptAtMillis: Long?,
    val observedProjection: FrameProjection?,
    val expiresAtMillis: Long?,
    val outlinedElements: List<PresentedElement>,
)

internal data class PresentedElement(
    val snapshot: OcrSnapshot,
    val projection: FrameProjection,
    val outline: ElementOutline,
    val group: PresentedOccurrence,
)

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
    val latestProjection = latestState.projection ?: return emptyList()
    val latestSnapshot = latestState.snapshot ?: return emptyList()
    val latestReceipt = latestState.receivedAtMillis ?: return emptyList()
    if (currentQuery != composedQuery || currentQuery != presentation.query ||
        latestSnapshot !== presentation.observedSnapshot || latestReceipt != presentation.observedReceiptAtMillis ||
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
        element.snapshot === group.snapshot && group.projection.snapshot === group.snapshot &&
            element.outline in group.outlines && sourceAgeIsValid && targetIsCurrent && sourceIsEligible &&
            overlayWidth == group.projection.target.width.toFloat() &&
            overlayHeight == group.projection.target.height.toFloat()
    }
}

private data class Candidate(val oldIndex: Int, val newIndex: Int, val iou: Double)
private data class Match(val oldIndex: Int, val newIndex: Int)
private data class Association(val pairs: List<Match>, val ambiguousOld: Set<Int>)
private const val CENTER_DISTANCE_RATIO = 0.25
private const val MIN_SIZE_RATIO = 0.5
private const val MAX_SIZE_RATIO = 2.0
private const val SEGMENT_OVERLAP = 0.5
private const val SCORE_EPSILON = 1e-6
private const val MIN_ASSOCIATION_IOU = 0.5
private data class Bounds(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val diagonal get() = hypot(width, height)
}

private fun PresentedOccurrence.bounds(): Bounds {
    val points = outlines.flatMap { it.points }
    return Bounds(
        points.minOf { it.x.toDouble() },
        points.minOf { it.y.toDouble() },
        points.maxOf { it.x.toDouble() },
        points.maxOf { it.y.toDouble() },
    )
}

private fun associationScore(first: Bounds, second: Bounds): Double? {
    if (first.width <= 0 || first.height <= 0 || second.width <= 0 || second.height <= 0) return null
    val widthRatio = first.width / second.width
    val heightRatio = first.height / second.height
    if (widthRatio !in MIN_SIZE_RATIO..MAX_SIZE_RATIO || heightRatio !in MIN_SIZE_RATIO..MAX_SIZE_RATIO) return null
    val smallerDiagonal = min(first.diagonal, second.diagonal)
    if (hypot(first.centerX - second.centerX, first.centerY - second.centerY) > CENTER_DISTANCE_RATIO * smallerDiagonal) return null
    val intersection = intersectionArea(first, second)
    val union = first.area + second.area - intersection
    if (union <= 0) return null
    val iou = intersection / union
    return iou.takeIf { it >= MIN_ASSOCIATION_IOU }
}

private fun overlapOfSmallerArea(first: Bounds, second: Bounds): Double {
    val smallerArea = min(first.area, second.area)
    if (smallerArea <= 0) return 0.0
    return intersectionArea(first, second) / smallerArea
}

private fun intersectionArea(first: Bounds, second: Bounds): Double {
    val width = max(0.0, min(first.right, second.right) - max(first.left, second.left))
    val height = max(0.0, min(first.bottom, second.bottom) - max(first.top, second.top))
    return width * height
}

private fun isReceiptFresh(receipt: Long, nowMillis: Long) =
    nowMillis >= receipt && nowMillis - receipt < SearchPresentationOwner.RESULT_AGE_MILLIS
