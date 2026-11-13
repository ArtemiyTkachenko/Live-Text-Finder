package com.artkachenko.livetextfinder.search

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A previous occurrence paired one-to-one with a fresh occurrence of the same text. */
internal data class OccurrenceMatch(val oldIndex: Int, val newIndex: Int)

/** Unique pairs, plus previous occurrences left unpaired because the pairing was ambiguous. */
internal data class OccurrenceAssociation(
    val pairs: List<OccurrenceMatch>,
    val ambiguousOld: Set<Int>,
)

/**
 * Pairs previous and fresh occurrences of the same term and text whose bounds are close in
 * position and size. A pair is kept only when each side is the other's single best candidate.
 */
internal fun associateOccurrences(
    old: List<PresentedOccurrence>,
    fresh: List<PresentedOccurrence>,
): OccurrenceAssociation {
    val candidates = mutableListOf<Candidate>()
    for (oldIndex in old.indices) {
        for (newIndex in fresh.indices) {
            if (old[oldIndex].termId != fresh[newIndex].termId ||
                old[oldIndex].key != fresh[newIndex].key
            ) {
                continue
            }
            val score = associationScore(
                old[oldIndex].bounds(),
                fresh[newIndex].bounds(),
            ) ?: continue
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
    }.map { OccurrenceMatch(it.oldIndex, it.newIndex) }
    return OccurrenceAssociation(pairs, ambiguousOld)
}

/** Returns whether [second] covers enough of [first] to be a re-segmentation of the same text. */
internal fun isSegmentationOverlap(
    first: PresentedOccurrence,
    second: PresentedOccurrence,
): Boolean = overlapOfSmallerArea(first.bounds(), second.bounds()) >= SEGMENT_OVERLAP

private fun best(candidates: List<Candidate>): List<Candidate> {
    val highest = candidates.maxOfOrNull { it.iou } ?: return emptyList()
    return candidates.filter { highest - it.iou <= SCORE_EPSILON }
}

private data class Candidate(val oldIndex: Int, val newIndex: Int, val iou: Double)

private const val CENTER_DISTANCE_RATIO = 0.25
private const val MIN_SIZE_RATIO = 0.5
private const val MAX_SIZE_RATIO = 2.0
private const val SEGMENT_OVERLAP = 0.5
private const val SCORE_EPSILON = 1e-6
private const val MIN_ASSOCIATION_IOU = 0.5

private data class Bounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
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
    if (first.width <= 0 || first.height <= 0 || second.width <= 0 || second.height <= 0) {
        return null
    }
    val widthRatio = first.width / second.width
    val heightRatio = first.height / second.height
    if (widthRatio !in MIN_SIZE_RATIO..MAX_SIZE_RATIO ||
        heightRatio !in MIN_SIZE_RATIO..MAX_SIZE_RATIO
    ) {
        return null
    }
    val smallerDiagonal = min(first.diagonal, second.diagonal)
    val centerDistance = hypot(first.centerX - second.centerX, first.centerY - second.centerY)
    if (centerDistance > CENTER_DISTANCE_RATIO * smallerDiagonal) return null
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
