package com.artkachenko.livetextfinder

import android.graphics.Bitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ProcessedImportedImage
import com.artkachenko.livetextfinder.camera.plainTextClipEntry
import com.artkachenko.livetextfinder.ocr.CapturedCameraFrame
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.artkachenko.livetextfinder.search.stillMatchRows
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class StillMatchCopyCoordinatorTest {
    @Test
    fun productionCoordinatorReadsAnActiveFrozenOwner() {
        val importedOwner = ImportedImageOwner()
        val frozenOwner = FrozenFrameOwner()
        val session = inMemorySearchSessionController()
        session.setManualQuery("MATCH")
        val snapshot = OcrSnapshot(
            text = "MATCH",
            lines = listOf(
                TextLine(
                    "MATCH",
                    null,
                    emptyList(),
                    listOf(
                        TextElement("MATCH", TextBounds(0, 0, 40, 20), listOf(TextPoint(0, 0))),
                    ),
                ),
            ),
            frame = FrameInfo(
                1,
                40,
                20,
                0,
                TextBounds(0, 0, 40, 20),
                listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            ),
        )
        var requestId: Long? = null
        frozenOwner.attachSession(
            requestCapture = { id ->
                requestId = id
                true
            },
            cancelCapture = {
            },
        )
        check(frozenOwner.freeze(cameraReady = true))
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        check(
            frozenOwner.accept(
                CapturedCameraFrame(
                    checkNotNull(requestId),
                    bitmap,
                    snapshot,
                    FrameIdentity(1, 1),
                ),
            ),
        )
        val result = searchRecognizedText(
            session.state.value.activeSearch,
            OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
        )
        val row = stillMatchRows(result).single()
        val frame = checkNotNull(frozenOwner.state.value.frame)
        val writes = mutableListOf<String>()
        val coordinator = StillMatchCopyCoordinator(
            importedOwner = importedOwner,
            frozenOwner = frozenOwner,
            session = session,
            selectedIndex = { row.index },
            writeText = { writes.add(it) },
        )

        try {
            assertEquals(
                StillMatchCopyOutcome.Copied,
                runBlocking {
                    coordinator.copy(
                        row,
                        result,
                        "frozen:${frame.identity}",
                        result.spec.identity,
                        row.index,
                        frame,
                    )
                },
            )
            assertEquals(listOf("MATCH"), writes)
        } finally {
            importedOwner.dispose()
            frozenOwner.stop()
            bitmap.recycle()
        }
    }

    @Test
    fun productionCoordinatorUsesRealOwnerStateAndReportsCopyAndFailureOutcomes() {
        val owner = ImportedImageOwner()
        val frozenOwner = FrozenFrameOwner()
        val session = inMemorySearchSessionController()
        session.setManualQuery("MATCH")
        val snapshot = ImportedTextSnapshot(
            text = "MATCH",
            lines = listOf(
                TextLine(
                    "MATCH",
                    null,
                    emptyList(),
                    listOf(
                        TextElement(
                            "MATCH",
                            TextBounds(0, 0, 40, 20),
                            listOf(TextPoint(0, 0)),
                        ),
                    ),
                ),
            ),
            width = 40,
            height = 20,
            sourceIdentity = 1,
        )
        val requestId = checkNotNull(owner.beginPicker())
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        check(owner.adoptProcessedImage(requestId, ProcessedImportedImage(bitmap, snapshot)))
        val result = searchRecognizedContent(session.state.value.activeSearch, snapshot)
        val row = stillMatchRows(result).single()
        var selectedIndex: Int? = row.index
        val writes = mutableListOf<String>()
        val clipboard = object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = null

            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                writes.add(checkNotNull(clipEntry).clipData.getItemAt(0).text.toString())
            }
        }
        val coordinator = StillMatchCopyCoordinator(
            importedOwner = owner,
            frozenOwner = frozenOwner,
            session = session,
            selectedIndex = { selectedIndex },
            writeText = { clipboard.setClipEntry(plainTextClipEntry(it)) },
        )

        try {
            assertEquals(
                StillMatchCopyOutcome.Copied,
                runBlocking {
                    coordinator.copy(
                        row,
                        result,
                        "import:${snapshot.sourceIdentity}",
                        result.spec.identity,
                        row.index,
                        snapshot,
                    )
                },
            )
            assertEquals(listOf("MATCH"), writes)

            writes.clear()
            session.setManualQuery("OTHER")
            assertEquals(
                StillMatchCopyOutcome.Stale,
                runBlocking {
                    coordinator.copy(
                        row,
                        result,
                        "import:${snapshot.sourceIdentity}",
                        result.spec.identity,
                        row.index,
                        snapshot,
                    )
                },
            )
            assertEquals(emptyList(), writes)
            session.setManualQuery("MATCH")

            selectedIndex = null
            assertEquals(
                StillMatchCopyOutcome.Stale,
                runBlocking {
                    coordinator.copy(
                        row,
                        result,
                        "import:${snapshot.sourceIdentity}",
                        result.spec.identity,
                        row.index,
                        snapshot,
                    )
                },
            )
            assertEquals(emptyList(), writes)
            selectedIndex = row.index

            owner.cancel()
            assertEquals(
                StillMatchCopyOutcome.Stale,
                runBlocking {
                    coordinator.copy(
                        row,
                        result,
                        "import:${snapshot.sourceIdentity}",
                        result.spec.identity,
                        row.index,
                        snapshot,
                    )
                },
            )
            assertEquals(emptyList(), writes)

            val currentRequest = checkNotNull(owner.beginPicker())
            val replacement = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
            check(
                owner.adoptProcessedImage(
                    currentRequest,
                    ProcessedImportedImage(replacement, snapshot),
                ),
            )
            val failingCoordinator = StillMatchCopyCoordinator(
                importedOwner = owner,
                frozenOwner = frozenOwner,
                session = session,
                selectedIndex = { selectedIndex },
                writeText = { throw IllegalStateException("fake clipboard failure") },
            )
            assertEquals(
                StillMatchCopyOutcome.Failed,
                runBlocking {
                    failingCoordinator.copy(
                        row,
                        result,
                        "import:${snapshot.sourceIdentity}",
                        result.spec.identity,
                        row.index,
                        snapshot,
                    )
                },
            )

            owner.stop()
            assertEquals(
                StillMatchCopyOutcome.Stale,
                runBlocking {
                    coordinator.copy(
                        row,
                        result,
                        "import:${snapshot.sourceIdentity}",
                        result.spec.identity,
                        row.index,
                        snapshot,
                    )
                },
            )
            assertEquals(emptyList(), writes)
        } finally {
            owner.dispose()
            frozenOwner.stop()
        }
    }
}
