package com.artkachenko.livetextfinder.inspection

import android.graphics.Bitmap
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ProcessedImportedImage
import com.artkachenko.livetextfinder.drainMainLooper
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.search.StillMatchCopyPayload
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.stillMatchRows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class StillMatchCopySessionTest {
    @After
    fun runPendingMainLooperWork() {
        drainMainLooper()
    }

    @Test
    fun suspendedSuccessOrFailureCannotChangeReopenedDialogOrStartFromItsOldCallback() {
        StillMatchCopyPayload.entries.forEach { payload ->
            listOf(false, true).forEach { fail ->
                Fixture().use { fixture ->
                    runBlocking {
                        val ui = StillMatchCopySession()
                        ui.updateDialogOpen(true)
                        val openingA = ui.openingToken
                        val release = CompletableDeferred<Unit>()
                        val job = launch(start = CoroutineStart.UNDISPATCHED) {
                            fixture.copy(ui, openingA, payload) {
                                release.await()
                                if (fail) error("Controlled clipboard failure after start")
                            }
                        }
                        // The platform write has started; it cannot be revoked by the app.
                        assertEquals(1, fixture.writes.size)
                        ui.updateDialogOpen(false)
                        ui.updateDialogOpen(true)
                        val openingB = ui.openingToken
                        assertFalse(openingA === openingB)
                        release.complete(Unit)
                        job.join()
                        assertTrue(ui.dialogOpen)
                        assertNull(ui.message)
                        assertTrue(ui.acceptsOpening(openingB))

                        fixture.copy(ui, openingA, payload)
                        assertEquals(1, fixture.writes.size)
                        assertTrue(ui.dialogOpen)
                        assertNull(ui.message)
                        fixture.copy(ui, openingB, payload)
                        assertEquals(2, fixture.writes.size)
                        assertFalse(ui.dialogOpen)
                        assertEquals("Copied", ui.message)
                    }
                }
            }
        }
    }

    @Test
    fun staleBeforeWriteIsRejectedAndNormalToolbarCopyStillWorks() {
        Fixture().use { fixture ->
            runBlocking {
                val ui = StillMatchCopySession()
                ui.updateDialogOpen(true)
                val staleOpening = ui.openingToken
                ui.updateDialogOpen(false)
                fixture.copy(ui, staleOpening)
                assertTrue(fixture.writes.isEmpty())
                fixture.copy(ui, null, current = { false })
                assertTrue(fixture.writes.isEmpty())
                fixture.session.setManualQuery("other")
                fixture.copy(ui, null)
                assertTrue(fixture.writes.isEmpty())
                fixture.session.setManualQuery("mixed")
                fixture.copy(ui, null)
                assertEquals(listOf("MiXeD"), fixture.writes)
                assertEquals("Copied", ui.message)
            }
        }
    }

    @Test
    fun openingInvalidatedBeforePlatformWriteProducesZeroWrites() {
        Fixture().use { fixture ->
            runBlocking {
                val ui = StillMatchCopySession()
                ui.updateDialogOpen(true)
                val opening = ui.openingToken
                val release = CompletableDeferred<Unit>()
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    fixture.copy(ui, opening, beforeWrite = { release.await() })
                }
                assertTrue(fixture.writes.isEmpty())
                ui.updateDialogOpen(false)
                ui.updateDialogOpen(true)
                release.complete(Unit)
                job.join()
                assertTrue(fixture.writes.isEmpty())
                assertTrue(ui.dialogOpen)
                assertNull(ui.message)
            }
        }
    }

    @Test
    fun newerRequestOwnsFeedbackWithinTheSameOpening() {
        Fixture().use { fixture ->
            runBlocking {
                val ui = StillMatchCopySession()
                ui.updateDialogOpen(true)
                val opening = ui.openingToken
                val releaseFirst = CompletableDeferred<Unit>()
                val releaseSecond = CompletableDeferred<Unit>()
                val first = launch(start = CoroutineStart.UNDISPATCHED) {
                    fixture.copy(ui, opening) {
                        releaseFirst.await()
                        error("First request failed")
                    }
                }
                val second = launch(start = CoroutineStart.UNDISPATCHED) {
                    fixture.copy(ui, opening, StillMatchCopyPayload.Line) { releaseSecond.await() }
                }
                releaseFirst.complete(Unit)
                first.join()
                assertTrue(ui.dialogOpen)
                assertNull(ui.message)
                releaseSecond.complete(Unit)
                second.join()
                assertFalse(ui.dialogOpen)
                assertEquals("Copied", ui.message)
                assertEquals(listOf("MiXeD", "Before MiXeD after"), fixture.writes)
            }
        }
    }

    @Test
    fun invalidatedSessionCannotFinishOrWriteAgainAndCancellationPropagates() {
        Fixture().use { fixture ->
            runBlocking {
                val ui = StillMatchCopySession()
                ui.updateDialogOpen(true)
                val opening = ui.openingToken
                val release = CompletableDeferred<Unit>()
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    fixture.copy(ui, opening) { release.await() }
                }
                ui.invalidate()
                release.complete(Unit)
                job.join()
                assertNull(ui.message)
                fixture.copy(ui, opening)
                assertEquals(1, fixture.writes.size)

                val fresh = StillMatchCopySession()
                fresh.updateDialogOpen(true)
                var cancelled = false
                try {
                    fixture.copy(fresh, fresh.openingToken) {
                        throw CancellationException("Controlled platform cancellation")
                    }
                } catch (error: CancellationException) {
                    cancelled = true
                }
                assertTrue(cancelled)
                assertTrue(fresh.dialogOpen)
                assertNull(fresh.message)
                fixture.copy(fresh, fresh.openingToken) { error("Current clipboard failure") }
                assertEquals("Could not copy", fresh.message)
                assertTrue(fresh.dialogOpen)
            }
        }
    }

    private class Fixture : AutoCloseable {
        val importedOwner = ImportedImageOwner()
        val frozenOwner = FrozenFrameOwner()
        val session = inMemorySearchSessionController()
        val writes = mutableListOf<String>()
        val snapshot = ImportedTextSnapshot(
            text = "Untrusted diagnostic aggregate",
            lines = listOf(
                TextLine(
                    "Untrusted diagnostic line", null, emptyList(),
                    listOf("Before", "MiXeD", "after").map {
                        TextElement(it, TextBounds(0, 0, 100, 20), emptyList())
                    },
                ),
            ),
            width = 100,
            height = 20,
            sourceIdentity = 1,
        )
        val result = run {
            session.setManualQuery("mixed")
            val request = checkNotNull(importedOwner.beginPicker())
            check(
                importedOwner.adoptProcessedImage(
                    request,
                    ProcessedImportedImage(
                        Bitmap.createBitmap(100, 20, Bitmap.Config.ARGB_8888), snapshot,
                    ),
                ),
            )
            searchRecognizedContent(session.state.value.activeSearch, snapshot)
        }
        val row = stillMatchRows(result).single()

        suspend fun copy(
            ui: StillMatchCopySession,
            opening: Any?,
            payload: StillMatchCopyPayload = StillMatchCopyPayload.MatchedText,
            current: () -> Boolean = { true },
            beforeWrite: suspend () -> Unit = {},
            write: suspend () -> Unit = {},
        ) {
            ui.copy(opening, current, "Copied", "Could not copy") { requestIsCurrent ->
                beforeWrite()
                StillMatchCopyCoordinator(
                    importedOwner, frozenOwner, session, { row.index },
                    writeText = {
                        writes.add(it)
                        write()
                    },
                    contextIsCurrent = requestIsCurrent,
                ).copy(
                    row, result, "import:1", result.spec.identity, row.index, snapshot, payload,
                )
            }
        }

        override fun close() {
            importedOwner.dispose()
            frozenOwner.stop()
        }
    }
}
