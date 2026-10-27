package com.artkachenko.livetextfinder.search

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Blends only a uniquely paired, continuously present live occurrence within strict bounds. */
internal fun stabilizeLiveOutlines(
    previous: PresentedOccurrence,
    current: PresentedOccurrence,
): List<ElementOutline>? {
    if (previous.firstMissingAtMillis != null ||
        previous.termId != current.termId ||
        previous.key != current.key ||
        current.key.toSet().size != current.key.size ||
        previous.projection.identity != current.projection.identity ||
        previous.projection.target != current.projection.target ||
        previous.occurrence.lineIndex != current.occurrence.lineIndex ||
        previous.occurrence.elementIndices != current.occurrence.elementIndices ||
        previous.outlines.size != current.outlines.size ||
        previous.displayOutlines.size != current.outlines.size
    ) {
        return null
    }

    val result = mutableListOf<ElementOutline>()
    previous.outlines.indices.forEach { index ->
        val oldRaw = previous.outlines[index]
        val oldDisplay = previous.displayOutlines[index]
        val newRaw = current.outlines[index]
        if (oldRaw.lineIndex != newRaw.lineIndex || oldRaw.elementIndex != newRaw.elementIndex ||
            oldDisplay.lineIndex != newRaw.lineIndex ||
            oldDisplay.elementIndex != newRaw.elementIndex
        ) {
            return null
        }
        val blended = blendOutline(oldRaw, oldDisplay, newRaw) ?: return null
        result += blended
    }
    return result
}

private fun blendOutline(
    previousRaw: ElementOutline,
    previousDisplay: ElementOutline,
    currentRaw: ElementOutline,
): ElementOutline? {
    val old = previousRaw.points
    val current = currentRaw.points
    if (!validQuad(old) || !validQuad(current) ||
        !sameWinding(old, current) ||
        !similarBounds(old, current) || !similarEdges(old, current) ||
        previousDisplay.points.size != 4 || previousDisplay.points.any { !it.isFinite() }
    ) {
        return null
    }

    val blended = old.indices.map { index ->
        val prior = previousDisplay.points[index]
        val latest = current[index]
        if (distance(prior, latest) > MAX_VERTEX_STEP) return null
        PreviewPoint((prior.x + latest.x) * 0.5f, (prior.y + latest.y) * 0.5f)
    }
    if (!validQuad(blended) || !sameWinding(blended, current)) return null
    val maximumError = blended.indices.maxOf { index -> distance(blended[index], current[index]) }
    if (maximumError > MAX_DISPLAY_ERROR) return null
    return if (maximumError <= CONVERGENCE_ERROR) {
        currentRaw
    } else {
        currentRaw.copy(points = blended)
    }
}

private fun similarBounds(first: List<PreviewPoint>, second: List<PreviewPoint>): Boolean {
    val firstBounds = bounds(first)
    val secondBounds = bounds(second)
    if (firstBounds.width <= 0.0 || firstBounds.height <= 0.0) return false
    return relativeChange(firstBounds.width, secondBounds.width) <= MAX_SIZE_CHANGE &&
        relativeChange(firstBounds.height, secondBounds.height) <= MAX_SIZE_CHANGE
}

private fun similarEdges(first: List<PreviewPoint>, second: List<PreviewPoint>): Boolean =
    first.indices.all { index ->
        val next = (index + 1) % first.size
        val firstAngle = edgeAngle(first[index], first[next])
        val secondAngle = edgeAngle(second[index], second[next])
        angleDifference(firstAngle, secondAngle) <= MAX_EDGE_ANGLE_CHANGE
    }

private fun edgeAngle(first: PreviewPoint, second: PreviewPoint): Double =
    atan2((second.y - first.y).toDouble(), (second.x - first.x).toDouble()) * 180.0 / PI

private fun angleDifference(first: Double, second: Double): Double {
    val difference = (first - second + 540.0) % 360.0 - 180.0
    return abs(difference)
}

private fun bounds(points: List<PreviewPoint>) = OutlineBounds(
    left = points.minOf { it.x.toDouble() },
    top = points.minOf { it.y.toDouble() },
    right = points.maxOf { it.x.toDouble() },
    bottom = points.maxOf { it.y.toDouble() },
)

private fun relativeChange(first: Double, second: Double): Double = abs(first - second) / first

private fun validQuad(points: List<PreviewPoint>): Boolean {
    if (points.size != 4 || points.any { !it.isFinite() }) return false
    val turns = points.indices.map { index ->
        val first = points[index]
        val second = points[(index + 1) % points.size]
        val third = points[(index + 2) % points.size]
        (second.x.toDouble() - first.x) * (third.y - second.y) -
            (second.y.toDouble() - first.y) * (third.x - second.x)
    }
    return turns.all { it > GEOMETRY_EPSILON } || turns.all { it < -GEOMETRY_EPSILON }
}

private fun signedArea(points: List<PreviewPoint>): Double = points.indices.sumOf { index ->
    val first = points[index]
    val second = points[(index + 1) % points.size]
    first.x.toDouble() * second.y - second.x.toDouble() * first.y
}

private fun sameWinding(first: List<PreviewPoint>, second: List<PreviewPoint>): Boolean =
    (signedArea(first) > 0.0) == (signedArea(second) > 0.0)

private fun distance(first: PreviewPoint, second: PreviewPoint): Double =
    hypot((first.x - second.x).toDouble(), (first.y - second.y).toDouble())

private fun PreviewPoint.isFinite(): Boolean = x.isFinite() && y.isFinite()

private data class OutlineBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width get() = right - left
    val height get() = bottom - top
}

private const val MAX_EDGE_ANGLE_CHANGE = 5.0
private const val MAX_SIZE_CHANGE = 0.10
private const val MAX_VERTEX_STEP = 6.0
private const val MAX_DISPLAY_ERROR = 3.0
private const val CONVERGENCE_ERROR = 0.125
private const val GEOMETRY_EPSILON = 1e-6
