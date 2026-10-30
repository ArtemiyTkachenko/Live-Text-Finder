package com.artkachenko.livetextfinder.search

import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SearchPresentationOwnerTest {
    private val identityMatrix = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val target = PreviewTarget(400, 200, identityMatrix)
    private val frameIdentity = FrameIdentity(7, 3)
    private val owner = SearchPresentationOwner()

    private data class Box(
        val text: String,
        val left: Int,
        val top: Int = 20,
        val right: Int = left + 40,
        val bottom: Int = 50,
        val corners: List<TextPoint> = emptyList(),
    )

    private fun state(
        boxes: List<Box>,
        timestamp: Long,
        receipt: Long,
        status: OcrStatus = OcrStatus.TextRecognized,
        identity: FrameIdentity = frameIdentity,
        previewTarget: PreviewTarget = target,
    ): OcrUiState {
        val elements = boxes.map { box ->
            TextElement(
                box.text,
                TextBounds(box.left, box.top, box.right, box.bottom),
                box.corners,
            )
        }
        val text = boxes.joinToString(" ") { it.text }
        val snapshot = com.artkachenko.livetextfinder.ocr.OcrSnapshot(
            text,
            listOf(TextLine(text, null, emptyList(), elements)),
            FrameInfo(timestamp, 400, 200, 0, TextBounds(0, 0, 400, 200), identityMatrix),
        )
        return OcrUiState(
            status,
            snapshot = snapshot,
            projection = FrameProjection(snapshot, identity, previewTarget),
            receivedAtMillis = receipt,
        )
    }

    private fun present(query: String, state: OcrUiState, now: Long) = owner.present(query, state, now)
    private fun present(
        spec: ActiveSearchSpec,
        state: OcrUiState,
        now: Long,
    ) = owner.present(spec, state, now)

    private fun rotatedSquare(
        degrees: Double,
        timestamp: Long,
        receipt: Long,
        reverse: Boolean = false,
    ): OcrUiState {
        val radians = degrees * PI / 180.0
        val cosine = cos(radians)
        val sine = sin(radians)
        val corners = listOf(10 to 10, 90 to 10, 90 to 90, 10 to 90).map { (x, y) ->
            val deltaX = x - 50
            val deltaY = y - 50
            TextPoint(
                (50 + deltaX * cosine - deltaY * sine).roundToInt(),
                (50 + deltaX * sine + deltaY * cosine).roundToInt(),
            )
        }.let { points -> if (reverse) points.reversed() else points }
        return state(
            listOf(Box("alpha", 10, 10, 90, 90, corners)),
            timestamp,
            receipt,
        )
    }

    private fun byLeft(presentation: SearchPresentation, left: Float) =
        presentation.occurrences.first { it.boundsLeft() == left }
    private fun PresentedOccurrence.boundsLeft() = outlines.flatMap { it.points }.minOf { it.x }

    @Test fun oneMissingOccurrenceRetainsItsOwnSourceWhileCurrentMatchUpdates() {
        val both = state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0)
        val initial = present("alpha", both, 0)
        val currentA = state(listOf(Box("alpha", 12)), 2, 100)
        val partial = present("alpha", currentA, 100)

        assertEquals(1, partial.search.occurrences.size)
        assertEquals(2, partial.occurrences.size)
        assertEquals(2, partial.outlinedElements.size)
        val updatedA = byLeft(partial, 12f)
        val heldB = byLeft(partial, 150f)
        assertSame(currentA.snapshot, updatedA.snapshot)
        assertNull(updatedA.firstMissingAtMillis)
        assertSame(both.snapshot, heldB.snapshot)
        assertSame(heldB.snapshot, heldB.projection.snapshot)
        assertEquals(100L, heldB.firstMissingAtMillis)
        assertEquals(400L, heldB.expiresAtMillis)
        assertSame(initial.occurrences[1].snapshot, heldB.snapshot)
    }

    @Test
    fun overlappingTermOwnersExpireIndependentlyWhenCurrentGeometrySharesElementIndex() {
        val spec = ActiveSearchSpec(
            "two-terms",
            "Two terms",
            listOf(SearchTerm("alpha", "alpha"), SearchTerm("beta", "beta")),
        )
        val initial = state(listOf(Box("alpha beta", 10)), 1, 0)
        present(spec, initial, 0)
        val latest = state(listOf(Box("beta", 12)), 2, 100)

        val retained = present(spec, latest, 100)
        val drawable = drawableElementsForSearch(
            spec.identity,
            spec.identity,
            retained,
            latest,
            100,
            400f,
            200f,
        )
        assertEquals(setOf("alpha", "beta"), drawable.map { it.group.termId }.toSet())
        assertEquals(2, drawable.distinctBy { it.displayOutline.points }.size)

        val afterExpiry = drawableElementsForSearch(
            spec.identity,
            spec.identity,
            retained,
            latest,
            400,
            400f,
            200f,
        )
        assertEquals(listOf("beta"), afterExpiry.map { it.group.termId })
        assertEquals(0, afterExpiry.single().outline.elementIndex)
    }

    @Test
    fun displayOutlineScaffoldingDefaultsToExactRawGeometry() {
        val source = state(listOf(Box("alpha", 10)), 1, 0)
        val presentation = present("alpha", source, 0)

        assertEquals(1, presentation.outlinedElements.size)
        val element = presentation.outlinedElements.single()
        assertSame(element.outline, element.displayOutline)
        assertSame(source.snapshot, element.snapshot)
        assertSame(source.snapshot, element.group.snapshot)
    }

    @Test
    fun uniquelyMatchedLiveDisplayReducesSmallTranslationWhileRawGeometryStaysCurrent() {
        val firstState = state(listOf(Box("alpha", 10)), 1, 0)
        present("alpha", firstState, 0)
        val currentState = state(listOf(Box("alpha", 12)), 2, 100)

        val presentation = present("alpha", currentState, 100)

        val element = presentation.outlinedElements.single()
        val rawLeft = element.outline.points.minOf { it.x }
        val displayLeft = element.displayOutline.points.minOf { it.x }
        assertEquals(12f, rawLeft)
        assertSame(currentState.snapshot, element.snapshot)
        assertSame(currentState.snapshot, element.group.snapshot)
        assertTrue(displayLeft > 10f && displayLeft < rawLeft)
        assertTrue(rawLeft - displayLeft <= 3f)
    }

    @Test
    fun eligibleLiveDrawableCarriesThePairedDisplayOutlineAndCurrentRawOutline() {
        val firstState = state(listOf(Box("alpha", 10)), 1, 0)
        present("alpha", firstState, 0)
        val currentState = state(listOf(Box("alpha", 12)), 2, 100)
        val presentation = present("alpha", currentState, 100)

        val drawable = drawableElements(
            "alpha",
            "alpha",
            presentation,
            currentState,
            100,
            400f,
            200f,
        ).single()

        assertEquals(12f, drawable.outline.points.minOf { it.x })
        assertTrue(drawable.displayOutline.points.minOf { it.x } in 10.1f..11.9f)
        assertSame(currentState.snapshot, drawable.snapshot)
    }

    @Test
    fun alternatingTwoAxisLiveJitterStaysWithinThreePixelsAndReducesDisplayedMotion() {
        val localOwner = SearchPresentationOwner()
        val base = Box("alpha", 100, top = 60, right = 140, bottom = 90)
        val firstState = state(listOf(base), 1, 0)
        val first = localOwner.present("alpha", firstState, 0).outlinedElements.single()
        var previousRaw = first.outline.points
        var previousDisplay = first.displayOutline.points
        val shifts = listOf(2 to 2, -2 to -2, 2 to -2, -2 to 2, 2 to 2, -2 to -2)

        shifts.forEachIndexed { index, (shiftX, shiftY) ->
            val current = state(
                listOf(
                    base.copy(
                        left = base.left + shiftX,
                        top = base.top + shiftY,
                        right = base.right + shiftX,
                        bottom = base.bottom + shiftY,
                    ),
                ),
                timestamp = index + 2L,
                receipt = (index + 1) * 100L,
            )
            val presentation = localOwner.present("alpha", current, (index + 1) * 100L)
            val drawable = drawableElements(
                "alpha",
                "alpha",
                presentation,
                current,
                (index + 1) * 100L,
                400f,
                200f,
            ).single()
            val expectedRaw = listOf(
                PreviewPoint(100f + shiftX, 60f + shiftY),
                PreviewPoint(140f + shiftX, 60f + shiftY),
                PreviewPoint(140f + shiftX, 90f + shiftY),
                PreviewPoint(100f + shiftX, 90f + shiftY),
            )
            val rawStep = hypot(
                (drawable.outline.points.first().x - previousRaw.first().x).toDouble(),
                (drawable.outline.points.first().y - previousRaw.first().y).toDouble(),
            )
            val displayStep = hypot(
                (drawable.displayOutline.points.first().x - previousDisplay.first().x).toDouble(),
                (drawable.displayOutline.points.first().y - previousDisplay.first().y).toDouble(),
            )

            assertEquals(expectedRaw, drawable.outline.points)
            assertSame(current.snapshot, drawable.snapshot)
            assertSame(current.snapshot, drawable.projection.snapshot)
            drawable.outline.points.zip(drawable.displayOutline.points).forEach { (raw, display) ->
                val error = hypot(
                    (raw.x - display.x).toDouble(),
                    (raw.y - display.y).toDouble(),
                )
                assertTrue(error <= 3.0)
            }
            assertTrue(displayStep < rawStep)
            previousRaw = drawable.outline.points
            previousDisplay = drawable.displayOutline.points
        }
    }

    @Test
    fun staleLatestReceiptCannotExposeSmoothedDisplayGeometry() {
        val firstState = state(listOf(Box("alpha", 10)), 1, 0)
        present("alpha", firstState, 0)
        val currentState = state(listOf(Box("alpha", 12)), 2, 100)
        val presentation = present("alpha", currentState, 100)

        assertTrue(
            drawableElements(
                "alpha",
                "alpha",
                presentation,
                currentState.copy(receivedAtMillis = 101),
                100,
                400f,
                200f,
            ).isEmpty(),
        )
    }

    @Test
    fun constantInputConvergesAndTimerRecompositionDoesNotBlendAgain() {
        val localOwner = SearchPresentationOwner()
        val first = state(listOf(Box("alpha", 10)), 1, 0)
        localOwner.present("alpha", first, 0)
        val frames = (1..5).map { index ->
            state(listOf(Box("alpha", 12)), index + 1L, index * 100L)
        }

        val displayed = frames.mapIndexed { index, frame ->
            localOwner.present("alpha", frame, (index + 1) * 100L)
                .outlinedElements.single().displayOutline.points.minOf { it.x }
        }
        val repeated = localOwner.present("alpha", frames.last(), 700)

        assertEquals(listOf(11f, 11.5f, 11.75f, 12f, 12f), displayed)
        assertEquals(12f, repeated.outlinedElements.single().displayOutline.points.minOf { it.x })
        assertEquals(12f, repeated.outlinedElements.single().outline.points.minOf { it.x })
    }

    @Test
    fun firstDetectionLargeMovementAndReappearingOccurrenceUseRawGeometryImmediately() {
        val localOwner = SearchPresentationOwner()
        val first = state(listOf(Box("alpha", 10)), 1, 0)
        assertEquals(
            10f,
            localOwner.present("alpha", first, 0).outlinedElements.single()
                .displayOutline.points.minOf { it.x },
        )

        val jittered = state(listOf(Box("alpha", 12)), 2, 100)
        localOwner.present("alpha", jittered, 100)
        val missing = state(emptyList(), 3, 200)
        val held = localOwner.present("alpha", missing, 200)
        assertEquals(11f, held.outlinedElements.single().displayOutline.points.minOf { it.x })

        val returned = state(listOf(Box("alpha", 14)), 4, 300)
        val reappeared = localOwner.present("alpha", returned, 300).outlinedElements.single()
        assertEquals(14f, reappeared.outline.points.minOf { it.x })
        assertEquals(14f, reappeared.displayOutline.points.minOf { it.x })

        val displaced = state(listOf(Box("alpha", 300)), 5, 400)
        val moved = localOwner.present("alpha", displaced, 400).outlinedElements.single()
        assertEquals(300f, moved.outline.points.minOf { it.x })
        assertEquals(300f, moved.displayOutline.points.minOf { it.x })
    }

    @Test
    fun stabilizationAcceptsTenPercentSizeBoundaryAndRejectsLargerShapeChange() {
        val localOwner = SearchPresentationOwner()
        localOwner.present("alpha", state(listOf(Box("alpha", 10)), 1, 0), 0)
        val exactBoundary = state(listOf(Box("alpha", 10, right = 54)), 2, 100)
        val blended = localOwner.present("alpha", exactBoundary, 100).outlinedElements.single()

        assertEquals(54f, blended.outline.points.maxOf { it.x })
        assertTrue(blended.displayOutline.points.maxOf { it.x } < 54f)

        val outsideBoundary = state(listOf(Box("alpha", 10, right = 59)), 3, 200)
        val raw = localOwner.present("alpha", outsideBoundary, 200).outlinedElements.single()
        assertEquals(raw.outline.points, raw.displayOutline.points)
    }

    @Test
    fun sixPixelVertexStepBlendsButLargerStepUsesRawGeometry() {
        val boundaryOwner = SearchPresentationOwner()
        boundaryOwner.present("alpha", state(listOf(Box("alpha", 10)), 1, 0), 0)
        val sixPixel = boundaryOwner.present(
            "alpha",
            state(listOf(Box("alpha", 16)), 2, 100),
            100,
        ).outlinedElements.single()
        assertEquals(16f, sixPixel.outline.points.minOf { it.x })
        assertEquals(13f, sixPixel.displayOutline.points.minOf { it.x })

        val beyondBoundaryOwner = SearchPresentationOwner()
        beyondBoundaryOwner.present("alpha", state(listOf(Box("alpha", 10)), 1, 0), 0)
        val sevenPixel = beyondBoundaryOwner.present(
            "alpha",
            state(listOf(Box("alpha", 17)), 2, 100),
            100,
        ).outlinedElements.single()
        assertEquals(sevenPixel.outline.points, sevenPixel.displayOutline.points)
    }

    @Test
    fun edgeAngleAndWindingChangesBeyondFiveDegreesResetDisplayToRaw() {
        val localOwner = SearchPresentationOwner()
        localOwner.present("alpha", rotatedSquare(0.0, 1, 0), 0)
        val withinAngle = localOwner.present("alpha", rotatedSquare(4.0, 2, 100), 100)
            .outlinedElements.single()
        assertTrue(withinAngle.displayOutline.points != withinAngle.outline.points)

        val beyondAngle = localOwner.present("alpha", rotatedSquare(10.0, 3, 200), 200)
            .outlinedElements.single()
        assertEquals(beyondAngle.outline.points, beyondAngle.displayOutline.points)

        val reversed = rotatedSquare(10.0, 4, 300, reverse = true)
        val changedWinding = localOwner.present("alpha", reversed, 300).outlinedElements.single()
        assertEquals(changedWinding.outline.points, changedWinding.displayOutline.points)
    }

    @Test
    fun reorderedElementCorrespondenceAndChangedSearchIdentityDoNotReuseDisplayGeometry() {
        val localOwner = SearchPresentationOwner()
        localOwner.present(
            "echo",
            state(listOf(Box("echo", 10), Box("echo", 150)), 1, 0),
            0,
        )
        val shifted = state(listOf(Box("echo", 12), Box("echo", 152)), 2, 100)
        localOwner.present("echo", shifted, 100)
        val reordered = state(listOf(Box("echo", 154), Box("echo", 14)), 3, 200)
        val reorderedPresentation = localOwner.present("echo", reordered, 200)

        assertTrue(reorderedPresentation.outlinedElements.all {
            it.outline.points == it.displayOutline.points
        })
        val changedQuery = state(listOf(Box("echo", 16)), 4, 300)
        val changedSearch = localOwner.present("echo ", changedQuery, 300)
        assertEquals(
            changedSearch.outlinedElements.single().outline.points,
            changedSearch.outlinedElements.single().displayOutline.points,
        )
    }

    @Test
    fun ambiguousRepeatedTokensUseRawGeometryForEveryCurrentOccurrence() {
        val localOwner = SearchPresentationOwner()
        val sameLocation = listOf(Box("echo", 30), Box("echo", 30))
        localOwner.present("echo", state(sameLocation, 1, 0), 0)
        val current = state(listOf(Box("echo", 32), Box("echo", 32)), 2, 100)

        val presentation = localOwner.present("echo", current, 100)

        assertEquals(2, presentation.outlinedElements.size)
        assertTrue(presentation.outlinedElements.all {
            it.outline.points == it.displayOutline.points
        })
    }

    @Test
    fun repeatedTokensInsideOnePhraseUseRawGeometryAfterMotion() {
        val localOwner = SearchPresentationOwner()
        localOwner.present(
            "echo echo",
            state(listOf(Box("echo", 10), Box("echo", 55)), 1, 0),
            0,
        )
        val current = state(listOf(Box("echo", 12), Box("echo", 57)), 2, 100)

        val presentation = localOwner.present("echo echo", current, 100)

        assertEquals(1, presentation.occurrences.size)
        assertEquals(2, presentation.occurrences.single().outlines.size)
        assertEquals(
            presentation.occurrences.single().outlines,
            presentation.occurrences.single().displayOutlines,
        )
    }

    @Test
    fun drawableGuardRejectsDisplayOutlinePairedWithAnotherRawElement() {
        val localOwner = SearchPresentationOwner()
        localOwner.present(
            "alpha beta",
            state(listOf(Box("alpha", 10), Box("beta", 60)), 1, 0),
            0,
        )
        val current = state(listOf(Box("alpha", 12), Box("beta", 62)), 2, 100)
        val presentation = localOwner.present("alpha beta", current, 100)
        val elements = presentation.outlinedElements
        assertEquals(2, elements.size)

        val mismatched = presentation.copy(
            outlinedElements = listOf(
                elements[0].copy(displayOutline = elements[1].displayOutline),
                elements[1],
            ),
        )

        val drawable = drawableElements(
            "alpha beta",
            "alpha beta",
            mismatched,
            current,
            100,
            400f,
            200f,
        )
        assertEquals(1, drawable.size)
        assertEquals(elements[1].outline, drawable.single().outline)
    }

    @Test
    fun sessionTargetAndSearchIdentityChangesStartFromRawGeometry() {
        val localOwner = SearchPresentationOwner()
        localOwner.present("alpha", state(listOf(Box("alpha", 10)), 1, 0), 0)
        localOwner.present("alpha", state(listOf(Box("alpha", 12)), 2, 100), 100)

        val newSession = state(
            listOf(Box("alpha", 14)),
            3,
            200,
            identity = frameIdentity.copy(generation = frameIdentity.generation + 1),
        )
        val sessionReset = localOwner.present("alpha", newSession, 200).outlinedElements.single()
        assertEquals(sessionReset.outline.points, sessionReset.displayOutline.points)

        val shiftedTarget = target.copy(sensorToView = listOf(
            1f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f,
        ))
        val targetResetState = state(
            listOf(Box("alpha", 16)),
            4,
            300,
            identity = frameIdentity.copy(generation = frameIdentity.generation + 2),
            previewTarget = shiftedTarget,
        )
        val targetReset = localOwner.present(
            "alpha",
            targetResetState,
            300,
        ).outlinedElements.single()
        assertEquals(targetReset.outline.points, targetReset.displayOutline.points)

        val newSearch = manualSearchSpec("alpha", SearchMatchMode.WholeWord)
        val searchReset = localOwner.present(
            newSearch,
            state(
                listOf(Box("alpha", 18)),
                5,
                400,
                identity = checkNotNull(targetResetState.projection).identity,
                previewTarget = shiftedTarget,
            ),
            400,
        ).outlinedElements.single()
        assertEquals(searchReset.outline.points, searchReset.displayOutline.points)
    }

    @Test fun independentMissingDeadlinesDoNotRenewWhenOtherOccurrenceKeepsUpdating() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val a100 = state(listOf(Box("alpha", 12)), 2, 100)
        assertEquals(400L, present("alpha", a100, 100).expiresAtMillis)
        val a200 = state(listOf(Box("alpha", 14)), 3, 200)
        val at200 = present("alpha", a200, 200)
        assertEquals(400L, at200.expiresAtMillis)
        assertEquals(100L, byLeft(at200, 150f).firstMissingAtMillis)

        val blank = state(emptyList(), 4, 250)
        val at250 = present("alpha", blank, 250)
        assertEquals(400L, at250.expiresAtMillis)
        assertEquals(250L, byLeft(at250, 14f).firstMissingAtMillis)
        val at400 = present("alpha", blank, 400)
        assertEquals(1, at400.occurrences.size)
        assertEquals(14f, byLeft(at400, 14f).boundsLeft())
        assertEquals(550L, at400.expiresAtMillis)
    }

    @Test fun returningNearbyOccurrenceRefreshesItsSourceAndCancelsItsHold() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val partial = state(listOf(Box("alpha", 12)), 2, 100)
        present("alpha", partial, 100)
        val returned = state(listOf(Box("alpha", 14), Box("alpha", 154)), 3, 200)
        val next = present("alpha", returned, 200)

        assertEquals(2, next.occurrences.size)
        assertEquals(2, next.search.occurrences.size)
        assertTrue(next.occurrences.all { it.snapshot === returned.snapshot })
        assertTrue(next.occurrences.all { it.firstMissingAtMillis == null && it.expiresAtMillis == null })
        assertNull(next.expiresAtMillis)
    }

    @Test fun timerRecompositionsDoNotRenewAndEachDeadlineExpiresWithoutOcrCallback() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val latest = state(listOf(Box("alpha", 12)), 2, 100)
        val at100 = present("alpha", latest, 100)
        assertEquals(400L, at100.expiresAtMillis)
        assertEquals(1, present("alpha", latest, 399).search.occurrences.size)
        assertEquals(2, present("alpha", latest, 399).occurrences.size)
        val at400 = present("alpha", latest, 400)
        assertEquals(1, at400.search.occurrences.size)
        assertEquals(1, at400.occurrences.size)
        assertNull(at400.expiresAtMillis)
    }

    @Test fun firstMissingDeadlineIsCappedByOriginalReceiptFreshness() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val latePartial = state(listOf(Box("alpha", 12)), 2, 1_300)
        val held = present("alpha", latePartial, 1_300)
        assertEquals(1_500L, held.expiresAtMillis)
        assertEquals(200L, held.expiresAtMillis!! - checkNotNull(byLeft(held, 150f).firstMissingAtMillis))
        assertEquals(1, present("alpha", latePartial, 1_500).occurrences.size)
    }

    @Test fun identicalWordsAssociateOneToOneByGeometryAfterElementReordering() {
        val original = state(listOf(Box("echo", 10), Box("echo", 150)), 1, 0)
        present("echo", original, 0)
        val reorderedAndMissingLeft = state(listOf(Box("echo", 150), Box("other", 250)), 2, 100)
        val next = present("echo", reorderedAndMissingLeft, 100)

        assertEquals(1, next.search.occurrences.size)
        assertEquals(2, next.occurrences.size)
        val currentRight = next.occurrences.first { it.snapshot === reorderedAndMissingLeft.snapshot }
        val heldLeft = next.occurrences.first { it.snapshot === original.snapshot }
        assertEquals(listOf(0), currentRight.occurrence.elementIndices)
        assertEquals(listOf(0), heldLeft.occurrence.elementIndices)
        assertEquals(150f, currentRight.boundsLeft())
        assertEquals(10f, heldLeft.boundsLeft())
    }

    @Test fun clearlyDisplacedSameTextObservationRetiresOldCopy() {
        val original = state(listOf(Box("echo", 10)), 1, 0)
        present("echo", original, 0)
        val displaced = state(listOf(Box("echo", 300)), 2, 100)
        val next = present("echo", displaced, 100)

        assertEquals(1, next.occurrences.size)
        assertSame(displaced.snapshot, next.occurrences.single().snapshot)
        assertNull(next.expiresAtMillis)
    }

    @Test fun overlappingSameTextOccurrenceDoesNotLeaveAnUnmatchedGhost() {
        val original = state(listOf(Box("echo", 10), Box("echo", 30)), 1, 0)
        present("echo", original, 0)

        val current = state(listOf(Box("echo", 10)), 2, 100)
        val next = present("echo", current, 100)

        assertEquals(1, next.search.occurrences.size)
        assertEquals(1, next.occurrences.size)
        assertSame(current.snapshot, next.occurrences.single().snapshot)
        assertEquals(10f, next.outlinedElements.single().outline.points.minOf { it.x })
    }

    @Test
    fun anotherTermCannotSuppressRetentionAndChangingListIdentityClearsIt() {
        val spec = ActiveSearchSpec(
            "list-one",
            "Parts",
            listOf(SearchTerm("one:0", "alpha"), SearchTerm("one:1", "beta")),
        )
        present(spec, state(listOf(Box("alpha", 10)), 1, 0), 0)
        val betaOnly = state(listOf(Box("beta", 10)), 2, 100)
        val retained = present(spec, betaOnly, 100)
        assertEquals(1, retained.search.occurrences.size)
        assertEquals(setOf("one:0", "one:1"), retained.occurrences.map { it.termId }.toSet())
        assertEquals(2, retained.occurrences.size)

        val otherList = spec.copy(
            identity = "list-two",
            terms = listOf(SearchTerm("two:0", "alpha"), SearchTerm("two:1", "beta")),
        )
        val switched = present(otherList, betaOnly, 110)
        assertEquals(1, switched.occurrences.size)
        assertEquals("two:1", switched.occurrences.single().termId)
    }

    @Test
    fun liveSessionIdentityChangeSuppressesPreviouslyComposedHighlights() {
        val session = inMemorySearchSessionController()
        session.setManualQuery("alpha")
        val composedSearch = session.state.value.activeSearch
        val matchingState = state(listOf(Box("alpha", 10)), 1, 0)
        val presentation = SearchPresentationOwner().present(composedSearch, matchingState, 0)
        session.setManualQuery("beta")

        val drawable = drawableElementsForSession(
            session,
            composedSearch,
            presentation,
            matchingState,
            1,
            400f,
            200f,
        )

        assertTrue(drawable.isEmpty())
    }

    @Test fun segmentationChangeContainingOldPhraseUsesSmallerAreaOverlapAndNoGhost() {
        val split = state(listOf(Box("alpha", 20, right = 35), Box("beta", 40, right = 55)), 1, 0)
        val first = present("alpha beta", split, 0)
        assertEquals(1, first.occurrences.size)
        assertEquals(2, first.occurrences.single().outlines.size)
        assertEquals(listOf(0, 1), first.occurrences.single().occurrence.elementIndices)

        val merged = state(listOf(Box("alpha beta", 10, right = 100)), 2, 100)
        val next = present("alpha beta", merged, 100)
        assertEquals(1, next.search.occurrences.size)
        assertEquals(1, next.occurrences.size)
        assertSame(merged.snapshot, next.occurrences.single().snapshot)
        assertEquals(1, next.outlinedElements.size)
    }

    @Test fun phraseHoldKeepsItsWholeSourceFrameAndEveryParticipatingOutline() {
        val phrase = state(listOf(Box("new", 10, right = 45), Box("york", 50, right = 100)), 1, 0)
        val current = present("new york", phrase, 0)
        val blank = state(listOf(Box("street", 200)), 2, 100)
        val held = present("new york", blank, 100)

        assertEquals(1, current.occurrences.size)
        assertEquals(2, current.occurrences.single().outlines.size)
        assertEquals(0, held.search.occurrences.size)
        assertEquals(1, held.occurrences.size)
        assertSame(phrase.snapshot, held.occurrences.single().snapshot)
        assertSame(phrase.snapshot, held.occurrences.single().projection.snapshot)
        assertEquals(2, held.occurrences.single().outlines.size)
    }

    @Test fun latestMatcherCountsStayTruthfulWhileOtherOccurrencesRemainOutlined() {
        state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0).also { present("alpha", it, 0) }
        val latest = state(listOf(Box("alpha", 12)), 2, 100)
        val result = present("alpha", latest, 100)

        assertEquals(1, result.search.occurrences.size)
        assertEquals(2, result.occurrences.size)
        assertEquals(2, result.outlinedElements.size)
        assertEquals(1, result.occurrences.count { it.firstMissingAtMillis == null })
        assertEquals(1, result.occurrences.count { it.firstMissingAtMillis != null })
    }

    @Test fun repeatedQueryOccurrencesInOneElementDrawAndCountOneDistinctOutline() {
        val banana = state(listOf(Box("banana", 10, right = 130)), 1, 0)
        val result = present("an", banana, 0)
        assertEquals(2, result.search.occurrences.size)
        assertEquals(2, result.occurrences.size)
        assertEquals(1, result.outlinedElements.size)
        assertEquals(1, drawableElements("an", "an", result, banana, 1, 400f, 200f).size)
    }

    @Test fun queryGeometrySessionErrorAndFreshnessInvalidationsClearAllEntries() {
        val matching = state(listOf(Box("alpha", 10), Box("alpha", 150)), 1, 0)
        present("alpha", matching, 0)
        val partial = state(listOf(Box("alpha", 12)), 2, 100)
        assertEquals(2, present("alpha", partial, 100).occurrences.size)

        val changedQuery = present("beta", partial, 110)
        assertEquals(0, changedQuery.occurrences.size)
        assertEquals(0, present("alpha", partial.copy(projection = null), 120).occurrences.size)

        present("alpha", matching, 130)
        val newGeneration = state(listOf(Box("alpha", 12)), 3, 140, identity = frameIdentity.copy(generation = 4))
        assertEquals(1, present("alpha", newGeneration, 140).occurrences.size)
        val otherTarget = state(listOf(Box("alpha", 14)), 4, 150, identity = frameIdentity.copy(generation = 5),
            previewTarget = target.copy(width = 200))
        assertEquals(1, present("alpha", otherTarget, 150).occurrences.size)

        val error = OcrUiState(OcrStatus.Error, "recognition failed")
        assertEquals(0, present("alpha", error, 160).occurrences.size)
        assertEquals(0, present("alpha", matching, 1_500).occurrences.size)
    }

    @Test fun drawGuardRejectsNewCallbackBeforeCompositionAndChecksEverySourceDeadline() {
        val a = state(listOf(Box("alpha", 10)), 1, 0)
        val exact = present("alpha", a, 0)
        assertEquals(1, drawableElements("alpha", "alpha", exact, a, 1_499, 400f, 200f).size)
        assertTrue(drawableElements("alpha", "alpha", exact, a, 1_500, 400f, 200f).isEmpty())

        val newer = state(listOf(Box("alpha", 12)), 2, 100)
        assertTrue(drawableElements("alpha", "alpha", exact, newer, 100, 400f, 200f).isEmpty())
        assertTrue(drawableElements("beta", "alpha", exact, a, 100, 400f, 200f).isEmpty())

        val both = state(listOf(Box("alpha", 10), Box("alpha", 150)), 3, 200)
        present("alpha", both, 200)
        val partial = state(listOf(Box("alpha", 12)), 4, 250)
        val held = present("alpha", partial, 250)
        assertEquals(2, drawableElements("alpha", "alpha", held, partial, 549, 400f, 200f).size)
        assertEquals(1, drawableElements("alpha", "alpha", held, partial, 550, 400f, 200f).size)
        assertTrue(drawableElements("alpha", "alpha", held, partial.copy(projection = null), 300, 400f, 200f).isEmpty())
    }

    @Test fun blankCallbackGraceUsesCurrentBlankDiagnosticsAndExpiresAtFirstDeadline() {
        val matching = state(listOf(Box("alpha", 10)), 1, 0)
        present("alpha", matching, 0)
        val blank = state(listOf(Box("other", 150)), 2, 100)
        val held = present("alpha", blank, 100)
        assertSame(blank.snapshot, held.search.snapshot)
        assertEquals(0, held.search.occurrences.size)
        assertEquals(1, held.occurrences.size)
        assertEquals(400L, held.expiresAtMillis)
    }
}
