package com.artkachenko.livetextfinder.search

/** Builds one presented occurrence per match that projects into the current preview target. */
internal fun collectPresentedOccurrences(
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
            termId = occurrence.termId,
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

/**
 * Merges freshly observed occurrences with those presented before.
 *
 * A fresh occurrence uniquely associated with a previous one keeps a stabilized display outline.
 * A previous occurrence that is now missing is retained until its grace or age deadline, unless
 * a fresh occurrence overlaps it as a re-segmentation or is a displaced copy of the same text.
 */
internal fun reconcileOccurrences(
    previous: List<PresentedOccurrence>,
    fresh: List<PresentedOccurrence>,
    receipt: Long,
    nowMillis: Long,
): List<PresentedOccurrence> {
    val eligiblePrevious = previous.filter { group ->
        isReceiptFresh(group.receiptAtMillis, nowMillis) &&
            group.expiresAtMillis?.let { nowMillis < it } != false
    }
    val matching = associateOccurrences(eligiblePrevious, fresh)
    val freshForOld = matching.pairs.associate { it.oldIndex to it.newIndex }
    val oldForFresh = matching.pairs.associate { it.newIndex to it.oldIndex }
    val ambiguousOld = matching.ambiguousOld
    val displayedFresh = fresh.mapIndexed { newIndex, current ->
        val previousIndex = matching.pairs.singleOrNull { it.newIndex == newIndex }?.oldIndex
        val previousGroup = previousIndex?.let { eligiblePrevious[it] }
        val displayOutlines = previousGroup?.let { previous ->
            stabilizeLiveOutlines(previous, current)
        } ?: current.outlines
        current.copy(displayOutlines = displayOutlines)
    }

    val retained = eligiblePrevious.mapIndexedNotNull { oldIndex, old ->
        if (oldIndex in freshForOld || oldIndex in ambiguousOld) return@mapIndexedNotNull null

        val suppressForSegmentation = fresh.any { new ->
            new.termId == old.termId && isSegmentationOverlap(old, new)
        }
        val suppressDisplacedCopy = fresh.indices.any { newIndex ->
            newIndex !in oldForFresh && fresh[newIndex].termId == old.termId &&
                fresh[newIndex].key == old.key
        }
        if (suppressForSegmentation || suppressDisplacedCopy) return@mapIndexedNotNull null

        val firstMissing = old.firstMissingAtMillis ?: receipt
        val expiry = minOf(
            firstMissing + SearchPresentationOwner.MATCH_GRACE_MILLIS,
            old.lastSeenAtMillis + SearchPresentationOwner.RESULT_AGE_MILLIS,
        )
        if (nowMillis >= expiry) null else old.copy(
            firstMissingAtMillis = firstMissing,
            expiresAtMillis = expiry,
        )
    }
    // Fresh groups retain current raw frame provenance; display geometry is separately bounded.
    return displayedFresh + retained
}
