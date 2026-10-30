package com.artkachenko.livetextfinder

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.ProcessedImportedImage
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.search.SearchMatchMode
import com.artkachenko.livetextfinder.search.inMemorySearchSessionController
import com.artkachenko.livetextfinder.search.searchRecognizedContent
import com.artkachenko.livetextfinder.search.stillMatchRows
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class StillMatchNavigationCoordinatorTest {
    @Test
    fun currentImportedRowRequiresVisibleCurrentViewportAndSelectionForLocate() {
        val importedOwner = ImportedImageOwner()
        val frozenOwner = FrozenFrameOwner()
        val session = inMemorySearchSessionController()
        session.setManualQuery("foo")
        val snapshot = snapshot(4L)
        adopt(importedOwner, snapshot)
        val result = searchRecognizedContent(session.state.value.activeSearch, snapshot)
        val row = stillMatchRows(result).single()
        val viewport = IntSize(100, 80)
        var currentViewport = viewport
        var selectedIndex: Int? = row.index
        var visible = true
        val coordinator = StillMatchNavigationCoordinator(
            importedOwner,
            frozenOwner,
            session,
            selectedIndex = { selectedIndex },
            viewport = { currentViewport },
            visible = { visible },
        )

        assertTrue(coordinator.accepts(
            row, result, "import:4", result.spec.identity, snapshot, row.index, viewport,
        ))
        visible = false
        assertFalse(coordinator.accepts(
            row, result, "import:4", result.spec.identity, snapshot, row.index, viewport,
        ))
        assertTrue(coordinator.accepts(
            row, result, "import:4", result.spec.identity, snapshot, row.index, viewport,
            requireLocateContext = false,
        ))
        visible = true
        currentViewport = IntSize(120, 80)
        assertFalse(coordinator.accepts(
            row, result, "import:4", result.spec.identity, snapshot, row.index, viewport,
        ))
        currentViewport = viewport
        selectedIndex = null
        assertFalse(coordinator.accepts(
            row, result, "import:4", result.spec.identity, snapshot, row.index, viewport,
        ))
        selectedIndex = row.index
        assertTrue(coordinator.accepts(
            row, result, "import:4", result.spec.identity, snapshot, row.index, viewport,
        ))
        session.setMatchMode(SearchMatchMode.WholeWord)
        assertFalse(coordinator.accepts(
            row, result, "import:4", result.spec.identity, snapshot, row.index, viewport,
        ))
        importedOwner.dispose()
    }

    @Test
    fun replacementWithSameLogicalIdentityAndImportTransitionRejectOldToken() {
        val importedOwner = ImportedImageOwner()
        val frozenOwner = FrozenFrameOwner()
        val session = inMemorySearchSessionController()
        session.setManualQuery("foo")
        val oldSnapshot = snapshot(4L)
        adopt(importedOwner, oldSnapshot)
        val result = searchRecognizedContent(session.state.value.activeSearch, oldSnapshot)
        val row = stillMatchRows(result).single()
        val viewport = IntSize(100, 80)
        val coordinator = StillMatchNavigationCoordinator(
            importedOwner,
            frozenOwner,
            session,
            selectedIndex = { row.index },
            viewport = { viewport },
            visible = { true },
        )

        val replacement = snapshot(4L)
        adopt(importedOwner, replacement)
        assertFalse(coordinator.accepts(
            row, result, "import:4", result.spec.identity, oldSnapshot, row.index, viewport,
        ))
        importedOwner.beginPicker()
        assertFalse(coordinator.accepts(
            row, result, "import:4", result.spec.identity, oldSnapshot, row.index, viewport,
        ))
        importedOwner.dispose()
    }

    private fun adopt(owner: ImportedImageOwner, snapshot: ImportedTextSnapshot) {
        val ticket = checkNotNull(owner.beginPicker())
        check(
            owner.adoptProcessedImage(
                ticket,
                ProcessedImportedImage(
                    Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888),
                    snapshot,
                ),
            ),
        )
    }

    private fun snapshot(identity: Long) = ImportedTextSnapshot(
        text = "foo",
        lines = listOf(
            TextLine("foo", null, emptyList(), listOf(TextElement("foo", null, emptyList())))
        ),
        width = 20,
        height = 10,
        sourceIdentity = identity,
    )
}
