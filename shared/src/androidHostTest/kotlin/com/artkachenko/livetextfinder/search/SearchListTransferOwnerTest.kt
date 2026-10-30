package com.artkachenko.livetextfinder.search

import android.net.Uri
import android.os.Looper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class SearchListTransferOwnerTest {
    @Test
    fun importPickerStopDefersUriAndAdoptsAnEditableDraftWithoutPersistence() {
        val session = healthySession()
        val original = session.state.value
        val owner = owner(FakeDocuments("term one\nphrase".encodeToByteArray()))
        val drafts = mutableListOf<Pair<String, List<String>>>()
        owner.updateContext(
            original,
            managerOpen = false,
            editor = SearchListEditorState(),
            contextRevision = 0L,
            copy = transferCopy(),
            isContextCurrent = { true },
            onImportDraft = { name, terms -> drafts += name to terms },
        )
        owner.onStart()

        owner.importList()
        owner.onStop()
        owner.onImportResult(Uri.parse("content://test/lists"))
        owner.onImportResult(null)
        assertEquals(0, lastDocuments.openInputCount)
        assertTrue(drafts.isEmpty())

        owner.onStart()
        awaitOwner(owner) { drafts.isNotEmpty() }
        assertEquals("Imported notes", drafts.single().first)
        assertEquals(listOf("term one", "phrase"), drafts.single().second)
        assertEquals(original, session.state.value)
        assertEquals("Text list loaded. Review it before saving.", owner.state.value.message)
        owner.dispose()
    }

    @Test
    fun latePickerCallbackCannotBecomeANewerImportRequest() {
        val documents = FakeDocuments("term".encodeToByteArray())
        val owner = owner(documents)
        var launchCount = 0
        owner.attach(RuntimeEnvironment.getApplication(), { launchCount++ }, {})
        owner.setDocumentAccessForTest(documents)
        val session = healthySession()
        val drafts = mutableListOf<Pair<String, List<String>>>()
        owner.updateContext(
            session.state.value,
            managerOpen = false,
            editor = SearchListEditorState(),
            contextRevision = 0L,
            copy = transferCopy(),
            isContextCurrent = { true },
            onImportDraft = { name, terms -> drafts += name to terms },
        )
        owner.onStart()

        owner.importList()
        owner.cancel()
        owner.importList()
        assertEquals(1, launchCount)
        owner.onImportResult(Uri.parse("content://test/old-picker"))
        assertEquals(0, documents.openInputCount)
        assertTrue(drafts.isEmpty())

        owner.importList()
        assertEquals(2, launchCount)
        owner.onImportResult(Uri.parse("content://test/new-picker"))
        awaitOwner(owner) { drafts.isNotEmpty() }
        assertEquals(1, documents.openInputCount)
        assertEquals(listOf("term"), drafts.single().second)
        owner.dispose()
    }

    @Test
    fun deliveredDeferredResultCanBeInvalidatedWithoutOrphaningThePickerGate() {
        listOf("cancel", "context", "dispose").forEach { invalidation ->
            val firstDocuments = FakeDocuments("old term".encodeToByteArray())
            val firstOwner = owner(firstDocuments)
            val session = healthySession()
            val nextDocuments = FakeDocuments("new term".encodeToByteArray())
            val drafts = mutableListOf<List<String>>()
            configureOwner(firstOwner, session.state.value) { _, terms -> drafts += terms }
            firstOwner.onStart()
            firstOwner.importList()
            firstOwner.onStop()
            firstOwner.onImportResult(Uri.parse("content://test/deferred-old"))

            val nextOwner = when (invalidation) {
                "cancel" -> {
                    firstOwner.cancel()
                    firstOwner.setDocumentAccessForTest(nextDocuments)
                    firstOwner
                }
                "context" -> {
                    firstOwner.contextChanged()
                    firstOwner.setDocumentAccessForTest(nextDocuments)
                    configureOwner(firstOwner, session.state.value) { _, terms -> drafts += terms }
                    firstOwner
                }
                else -> {
                    firstOwner.dispose()
                    val replacement = owner(nextDocuments)
                    configureOwner(replacement, session.state.value) { _, terms -> drafts += terms }
                    replacement
                }
            }
            assertEquals(0, firstDocuments.openInputCount)
            nextOwner.onStart()
            nextOwner.importList()
            nextOwner.onImportResult(Uri.parse("content://test/deferred-new"))
            awaitOwner(nextOwner) { drafts.isNotEmpty() }
            assertEquals(1, nextDocuments.openInputCount)
            assertEquals(listOf("new term"), drafts.single())
            nextOwner.dispose()
        }
    }

    @Test
    fun recreatedOwnerIgnoresUndeliveredPickerResultBeforeStartingItsOwnTransfer() {
        val oldOwner = owner(FakeDocuments("old".encodeToByteArray()))
        configureOwner(oldOwner, healthySession().state.value) { _, _ -> error("Stale draft") }
        oldOwner.onStart()
        oldOwner.importList()
        oldOwner.dispose()

        val newDocuments = FakeDocuments("new term".encodeToByteArray())
        val newOwner = owner(newDocuments)
        var launchCount = 0
        newOwner.attach(RuntimeEnvironment.getApplication(), { launchCount++ }, {})
        newOwner.setDocumentAccessForTest(newDocuments)
        val drafts = mutableListOf<List<String>>()
        configureOwner(newOwner, healthySession().state.value) { _, terms -> drafts += terms }
        newOwner.onStart()
        newOwner.importList()
        assertEquals(0, launchCount)

        newOwner.onImportResult(Uri.parse("content://test/restored-old-picker"))
        assertEquals(0, newDocuments.openInputCount)
        newOwner.importList()
        assertEquals(1, launchCount)
        newOwner.onImportResult(Uri.parse("content://test/new-picker"))
        awaitOwner(newOwner) { drafts.isNotEmpty() }
        assertEquals(listOf("new term"), drafts.single())
        newOwner.dispose()
    }

    @Test
    fun queuedWorkersCheckLiveEditorAndSessionBeforeAnyProviderOpen() {
        val session = healthySession()
        var editor = SearchListEditorState()
        val importDocuments = FakeDocuments("term".encodeToByteArray())
        val importOwner = owner(importDocuments)
        var queuedImport: (() -> Unit)? = null
        val importSnapshot = session.state.value
        importOwner.workerDispatcherForTest = { worker -> queuedImport = worker }
        configureOwner(importOwner, importSnapshot, editor) { _, _ -> error("Stale import") }
        importOwner.setLiveContextPredicateForTest {
            session.state.value == importSnapshot && editor == SearchListEditorState()
        }
        importOwner.onStart()
        importOwner.importList()
        importOwner.onImportResult(Uri.parse("content://test/stale-queued-import"))
        editor = editor.startNew()
        Thread(checkNotNull(queuedImport)).start()
        awaitOwner(importOwner) { !importOwner.isTransferInFlightForTest() }
        assertEquals(0, importDocuments.displayNameCount)
        assertEquals(0, importDocuments.openInputCount)
        importOwner.dispose()

        val exportSession = healthySession(withList = true)
        val exportDocuments = FakeDocuments()
        val exportOwner = owner(exportDocuments)
        var queuedExport: (() -> Unit)? = null
        val exportSnapshot = exportSession.state.value
        exportOwner.workerDispatcherForTest = { worker -> queuedExport = worker }
        configureOwner(exportOwner, exportSnapshot) { _, _ -> error("Stale export") }
        exportOwner.setLiveContextPredicateForTest { exportSession.state.value == exportSnapshot }
        exportOwner.onStart()
        exportOwner.exportSelectedList()
        exportOwner.onExportResult(Uri.parse("content://test/stale-queued-export"))
        exportSession.setManualQuery("changed before composition")
        Thread(checkNotNull(queuedExport)).start()
        awaitOwner(exportOwner) { !exportOwner.isTransferInFlightForTest() }
        assertEquals(0, exportDocuments.openOutputCount)
        assertEquals(0, exportDocuments.output.size())
        exportOwner.dispose()
    }

    @Test
    fun directEditorChangeDuringBlockedInputOpenClosesWithoutAdoptingDraft() {
        val documents = FakeDocuments(
            inputBytes = "term".encodeToByteArray(),
            inputOpenLatch = CountDownLatch(1),
            releaseInputOpen = CountDownLatch(1),
        )
        val owner = owner(documents)
        val session = healthySession()
        val originalSession = session.state.value
        var editor = SearchListEditorState()
        val drafts = mutableListOf<List<String>>()
        owner.updateContext(
            originalSession,
            managerOpen = false,
            editor = editor,
            contextRevision = 0L,
            copy = transferCopy(),
            isContextCurrent = {
                session.state.value == originalSession && editor == SearchListEditorState()
            },
            onImportDraft = { _, terms -> drafts += terms },
        )
        owner.onStart()
        owner.importList()
        owner.onImportResult(Uri.parse("content://test/blocked-live-context-input"))
        assertTrue(awaitLatchOnMain(documents.inputOpenLatch!!))

        editor = editor.startNew()
        documents.releaseInputOpen!!.countDown()
        awaitOwner(owner) { !owner.isTransferInFlightForTest() }
        assertTrue(documents.inputClosed)
        assertTrue(drafts.isEmpty())
        assertEquals(0, session.state.value.persisted.lists.size)
        owner.dispose()
    }

    @Test
    fun invalidatedBlockedOutputOpenClosesReturnedStreamWithoutWriting() {
        val documents = FakeDocuments(
            outputOpenLatch = CountDownLatch(1),
            releaseOutputOpen = CountDownLatch(1),
        )
        val owner = owner(documents)
        configureOwner(owner, healthySession(withList = true).state.value) { _, _ ->
            error("Export must not import a draft.")
        }
        owner.onStart()
        owner.exportSelectedList()
        owner.onExportResult(Uri.parse("content://test/blocked-cancelled-output"))
        assertTrue(awaitLatchOnMain(documents.outputOpenLatch!!))

        owner.cancel()
        documents.releaseOutputOpen!!.countDown()
        awaitOwner(owner) { !owner.isTransferInFlightForTest() }
        assertEquals(0, documents.output.size())
        assertEquals(1, documents.outputCloseCount)
        assertFalse(owner.state.value.message == "Exported list.")
        owner.dispose()
    }

    @Test
    fun duplicateWorkingCallbackAndWorkerDispatchFailureDoNotLeakTheGate() {
        val documents = FakeDocuments(
            inputBytes = "term".encodeToByteArray(),
            inputOpenLatch = CountDownLatch(1),
            releaseInputOpen = CountDownLatch(1),
        )
        val owner = owner(documents)
        val drafts = mutableListOf<List<String>>()
        configureOwner(owner, healthySession().state.value) { _, terms -> drafts += terms }
        owner.onStart()
        owner.importList()
        owner.onImportResult(Uri.parse("content://test/working"))
        assertTrue(awaitLatchOnMain(documents.inputOpenLatch!!))
        owner.onImportResult(Uri.parse("content://test/duplicate-working"))
        documents.releaseInputOpen!!.countDown()
        awaitOwner(owner) { drafts.isNotEmpty() }
        assertEquals(1, documents.openInputCount)
        owner.dispose()

        val failedOwner = owner(FakeDocuments("term".encodeToByteArray()))
        configureOwner(failedOwner, healthySession().state.value) { _, _ -> error("No draft") }
        failedOwner.workerDispatcherForTest = { error("Worker launch failed") }
        failedOwner.onStart()
        failedOwner.importList()
        failedOwner.onImportResult(Uri.parse("content://test/worker-failure"))
        assertEquals("Import failed.", failedOwner.state.value.message)
        assertFalse(failedOwner.isTransferInFlightForTest())
        failedOwner.dispose()
    }

    @Test
    fun ownerGatesHealthyStorageCapacityManualSelectionAndEditorState() {
        val owner = owner(FakeDocuments())
        val available = SearchSessionState(loaded = true)
        configureOwner(owner, available) { _, _ -> error("No import") }
        assertTrue(owner.state.value.importEnabled)
        assertFalse(owner.state.value.exportEnabled)

        val saved = SavedSearchList("selected", "Selected", listOf("term"))
        val selected = available.copy(
            persisted = available.persisted.copy(
                lists = listOf(saved),
                activeListId = saved.id,
            ),
        )
        configureOwner(owner, selected) { _, _ -> error("No import") }
        assertTrue(owner.state.value.importEnabled)
        assertTrue(owner.state.value.exportEnabled)

        configureOwner(owner, selected, SearchListEditorState().startNew()) { _, _ ->
            error("No import")
        }
        assertFalse(owner.state.value.importEnabled)
        assertTrue(owner.state.value.exportEnabled)

        configureOwner(owner, selected.copy(saving = true)) { _, _ -> error("No import") }
        assertFalse(owner.state.value.importEnabled)
        assertFalse(owner.state.value.exportEnabled)
        configureOwner(owner, selected.copy(loaded = false)) { _, _ -> error("No import") }
        assertFalse(owner.state.value.importEnabled)
        assertFalse(owner.state.value.exportEnabled)
        configureOwner(owner, selected.copy(recoveryRequired = true)) { _, _ -> error("No import") }
        assertFalse(owner.state.value.importEnabled)
        assertFalse(owner.state.value.exportEnabled)
        configureOwner(owner, selected.copy(error = "Storage error")) { _, _ -> error("No import") }
        assertFalse(owner.state.value.importEnabled)
        assertFalse(owner.state.value.exportEnabled)

        val fullLists = (0 until MAX_LISTS).map { index ->
            SavedSearchList("list-$index", "List $index", listOf("term-$index"))
        }
        val fullState = selected.copy(
            persisted = selected.persisted.copy(
                lists = fullLists,
                activeListId = fullLists.last().id,
            ),
        )
        configureOwner(owner, fullState) { _, _ -> error("No import") }
        assertFalse(owner.state.value.importEnabled)
        assertTrue(owner.state.value.exportEnabled)
        owner.dispose()
    }

    @Test
    fun importDraftUsesProductionBindingAndOnlyOrdinarySavePersistsIt() {
        val session = healthySession()
        assertNull(session.saveList(null, "Parts", "existing term"))
        val existing = checkNotNull(
            session.state.value.activeList ?: session.state.value.persisted.lists.single(),
        )
        session.setManualQuery("preserved manual")
        session.setHighlightColor(HighlightColor.Cyan)
        session.setMatchMode(SearchMatchMode.WholeWord)
        session.selectList(existing.id)
        val beforeImport = session.state.value
        var editor = SearchListEditorState()
        var managerOpen = false
        val documents = FakeDocuments("new term\nphrase".encodeToByteArray())
        val owner = owner(documents)
        configureOwner(owner, beforeImport, editor) { name, terms ->
            editor = editor.importedDraft(name, terms)
            managerOpen = true
        }
        owner.onStart()
        owner.importList()
        owner.onImportResult(Uri.parse("content://test/draft"))
        awaitOwner(owner) { editor.isOpen }
        assertEquals(null, editor.editingId)
        assertEquals("Imported notes", editor.name)
        assertEquals("new term\nphrase", editor.terms)
        assertTrue(managerOpen)
        assertEquals(beforeImport, session.state.value)

        editor = editor.close()
        managerOpen = false
        assertFalse(editor.isOpen)
        assertEquals(beforeImport, session.state.value)

        editor = editor.importedDraft("Imported notes", listOf("new term", "phrase"))
        managerOpen = true
        val beforeSave = session.state.value
        editor = editor.copy(name = existing.name)
        assertTrue(session.saveList(editor.editingId, editor.name, editor.terms) != null)
        assertEquals(beforeSave, session.state.value)
        editor = editor.copy(name = "Imported fresh")
        assertNull(session.saveList(editor.editingId, editor.name, editor.terms))
        val saved = checkNotNull(session.state.value.persisted.lists.firstOrNull {
            it.name == "Imported fresh"
        })
        assertEquals(beforeSave.persisted.manualQuery, session.state.value.persisted.manualQuery)
        assertEquals(beforeSave.persisted.activeListId, session.state.value.persisted.activeListId)
        assertEquals(
            beforeSave.persisted.highlightColor,
            session.state.value.persisted.highlightColor,
        )
        assertEquals(beforeSave.persisted.matchMode, session.state.value.persisted.matchMode)
        assertEquals(listOf("new term", "phrase"), saved.terms)
        assertEquals(2, saved.paletteSlots.size)
        assertEquals(2, saved.paletteSlots.toSet().size)
        assertTrue(managerOpen)
        owner.dispose()
    }

    @Test
    fun importRejectsOversizeMalformedNullAndStreamCloseFailure() {
        val invalidInputs = listOf(
            ByteArray(MAX_SEARCH_LIST_TRANSFER_BYTES + 1) { 'a'.code.toByte() },
            byteArrayOf(0xC3.toByte(), 0x28),
        )
        invalidInputs.forEach { bytes ->
            val owner = owner(FakeDocuments(bytes))
            var adopted = false
            startImport(owner) { _, _ -> adopted = true }
            awaitOwner(owner) { !owner.state.value.busy && owner.state.value.message != null }
            assertFalse(adopted)
            assertEquals(0, lastDocuments.mainThreadCalls)
            owner.dispose()
        }

        val nullDocuments = FakeDocuments(null)
        val nullOwner = owner(nullDocuments)
        var adopted = false
        startImport(nullOwner) { _, _ -> adopted = true }
        awaitOwner(nullOwner) {
            !nullOwner.state.value.busy && nullOwner.state.value.message != null
        }
        assertFalse(adopted)
        nullOwner.dispose()

        val closeOwner = owner(FakeDocuments("term".encodeToByteArray(), failInputClose = true))
        startImport(closeOwner) { _, _ -> adopted = true }
        awaitOwner(closeOwner) {
            !closeOwner.state.value.busy && closeOwner.state.value.message != null
        }
        assertFalse(adopted)
        closeOwner.dispose()
    }

    @Test
    fun exportWritesOnlySelectedTermsAndWaitsForFlushAndClose() {
        val owner = owner(FakeDocuments())
        val session = healthySession(withList = true)
        owner.updateContext(
            session.state.value,
            managerOpen = true,
            editor = SearchListEditorState(),
            contextRevision = 1L,
            copy = transferCopy(),
            isContextCurrent = { true },
            onImportDraft = { _, _ -> error("Export must not import a draft.") },
        )
        owner.onStart()
        owner.exportSelectedList()
        owner.onExportResult(Uri.parse("content://test/output"))
        awaitOwner(owner) { !owner.state.value.busy && owner.state.value.message != null }
        assertContentEquals(
            "term one\nphrase\n".encodeToByteArray(),
            lastDocuments.output.toByteArray(),
        )
        assertTrue(lastDocuments.outputFlushed)
        assertTrue(lastDocuments.outputClosed)
        assertEquals("Exported list.", owner.state.value.message)
        owner.dispose()
    }

    @Test
    fun outputOpenWriteFlushAndCloseFailuresNeverReportExportSuccess() {
        val failures = listOf(
            FakeDocuments(failOutputOpen = true),
            FakeDocuments(failOutputWrite = true),
            FakeDocuments(failOutputFlush = true),
            FakeDocuments(failOutputClose = true),
        )
        failures.forEach { documents ->
            val owner = owner(documents)
            val session = healthySession(withList = true)
            owner.updateContext(
                session.state.value,
                managerOpen = true,
                editor = SearchListEditorState(),
                contextRevision = 2L,
                copy = transferCopy(),
                isContextCurrent = { true },
                onImportDraft = { _, _ -> error("Export must not import a draft.") },
            )
            owner.onStart()
            owner.exportSelectedList()
            owner.onExportResult(Uri.parse("content://test/output-failure"))
            awaitOwner(owner) {
                !owner.isTransferInFlightForTest() && !owner.state.value.busy
            }
            assertEquals("Export failed.", owner.state.value.message)
            owner.dispose()
        }
    }

    @Test
    fun changedLiveContextAfterBlockedOpenPreventsTheFirstExportWriteWithoutRecomposition() {
        val documents = FakeDocuments(
            outputOpenLatch = CountDownLatch(1),
            releaseOutputOpen = CountDownLatch(1),
        )
        val owner = owner(documents)
        val session = healthySession(withList = true)
        var liveContextCurrent = true
        owner.updateContext(
            session.state.value,
            managerOpen = true,
            editor = SearchListEditorState(),
            contextRevision = 4L,
            copy = transferCopy(),
            isContextCurrent = { liveContextCurrent },
            onImportDraft = { _, _ -> error("Export must not import a draft.") },
        )
        owner.onStart()
        owner.exportSelectedList()
        owner.onExportResult(Uri.parse("content://test/blocked"))
        assertTrue(awaitLatchOnMain(documents.outputOpenLatch!!))

        liveContextCurrent = false
        documents.releaseOutputOpen!!.countDown()
        awaitOwner(owner) { !owner.isTransferInFlightForTest() }
        assertEquals(0, documents.output.size())
        assertTrue(documents.outputClosed)
        assertFalse(owner.state.value.busy)
        assertFalse(owner.state.value.message == "Exported list.")
        owner.dispose()
    }

    @Test
    fun contextRevisionChangeWhileOpenIsBlockedInvalidatesPickerAndWorkerResult() {
        val documents = FakeDocuments(
            inputBytes = "term".encodeToByteArray(),
            inputOpenLatch = CountDownLatch(1),
            releaseInputOpen = CountDownLatch(1),
        )
        val owner = owner(documents)
        val session = healthySession()
        val original = session.state.value
        val drafts = mutableListOf<List<String>>()
        owner.updateContext(
            session.state.value,
            managerOpen = false,
            editor = SearchListEditorState(),
            contextRevision = 0L,
            copy = transferCopy(),
            isContextCurrent = { true },
            onImportDraft = { _, terms -> drafts += terms },
        )
        owner.onStart()
        owner.importList()
        owner.onImportResult(Uri.parse("content://test/blocked-input"))
        assertTrue(awaitLatchOnMain(documents.inputOpenLatch!!))

        owner.contextChanged()
        documents.releaseInputOpen!!.countDown()
        awaitOwner(owner) { !owner.isTransferInFlightForTest() }
        assertTrue(documents.inputClosed)
        assertTrue(drafts.isEmpty())
        assertEquals(original, session.state.value)
        owner.dispose()
    }

    private lateinit var lastDocuments: FakeDocuments

    private fun owner(documents: FakeDocuments): SearchListTransferOwner {
        lastDocuments = documents
        val owner = SearchListTransferOwner()
        owner.attach(RuntimeEnvironment.getApplication(), {}, {})
        owner.setDocumentAccessForTest(documents)
        return owner
    }

    private fun configureOwner(
        owner: SearchListTransferOwner,
        sessionState: SearchSessionState,
        editor: SearchListEditorState = SearchListEditorState(),
        onDraft: (String, List<String>) -> Unit,
    ) {
        owner.updateContext(
            sessionState,
            managerOpen = false,
            editor = editor,
            contextRevision = 0L,
            copy = transferCopy(),
            isContextCurrent = { true },
            onImportDraft = onDraft,
        )
    }

    private fun startImport(
        owner: SearchListTransferOwner,
        onDraft: (String, List<String>) -> Unit,
    ) {
        val session = healthySession()
        owner.updateContext(
            session.state.value,
            managerOpen = false,
            editor = SearchListEditorState(),
            contextRevision = 0L,
            copy = transferCopy(),
            isContextCurrent = { true },
            onImportDraft = onDraft,
        )
        owner.onStart()
        owner.importList()
        owner.onImportResult(Uri.parse("content://test/input"))
    }

    private fun healthySession(withList: Boolean = false): SearchSessionController {
        val session = inMemorySearchSessionController()
        if (withList) {
            assertNull(session.saveList(null, "Exported list", "term one\nphrase"))
        }
        return session
    }

    private fun transferCopy() = SearchListTransferCopy(
        fallbackName = "Imported list",
        chooseFile = "Choose a file.",
        readingFile = "Reading file.",
        chooseDestination = "Choose a destination.",
        savingFile = "Saving file.",
        importCanceled = "Import canceled.",
        exportCanceled = "Export canceled.",
        importReady = "Text list loaded. Review it before saving.",
        exportComplete = "Exported list.",
        anotherTransfer = "Another transfer is finishing.",
        importUnavailable = "Import unavailable.",
        exportUnavailable = "Export unavailable.",
        listCannotExport = "List cannot export.",
        importFailed = "Import failed.",
        exportFailed = "Export failed.",
    )

    private fun awaitLatchOnMain(latch: CountDownLatch): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (latch.await(5, TimeUnit.MILLISECONDS)) return true
        }
        return latch.count == 0L
    }

    private fun awaitOwner(owner: SearchListTransferOwner, done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            Thread.sleep(5)
        }
        assertTrue(done(), "Timed out waiting for transfer owner work")
    }

    private class FakeDocuments(
        private val inputBytes: ByteArray? = null,
        private val failInputClose: Boolean = false,
        val outputOpenLatch: CountDownLatch? = null,
        val releaseOutputOpen: CountDownLatch? = null,
        val inputOpenLatch: CountDownLatch? = null,
        val releaseInputOpen: CountDownLatch? = null,
        private val failInputOpen: Boolean = false,
        private val failOutputOpen: Boolean = false,
        private val failOutputWrite: Boolean = false,
        private val failOutputFlush: Boolean = false,
        private val failOutputClose: Boolean = false,
    ) : SearchListTransferOwner.TransferDocumentAccess {
        val output = ByteArrayOutputStream()
        @Volatile var outputFlushed = false
        @Volatile var outputClosed = false
        @Volatile var inputClosed = false
        @Volatile var openInputCount = 0
        @Volatile var displayNameCount = 0
        @Volatile var openOutputCount = 0
        @Volatile var outputCloseCount = 0
        @Volatile var mainThreadCalls = 0

        override fun displayName(uri: Uri): String? {
            recordThread()
            displayNameCount++
            return "Imported notes.txt"
        }

        override fun openInput(uri: Uri): InputStream? {
            recordThread()
            openInputCount++
            inputOpenLatch?.countDown()
            releaseInputOpen?.await(3, TimeUnit.SECONDS)
            if (failInputOpen) error("open input failure")
            val bytes = inputBytes ?: return null
            return object : ByteArrayInputStream(bytes) {
                override fun close() {
                    inputClosed = true
                    if (failInputClose) error("close failure")
                    super.close()
                }
            }
        }

        override fun openOutput(uri: Uri): OutputStream? {
            recordThread()
            openOutputCount++
            outputOpenLatch?.countDown()
            releaseOutputOpen?.await(3, TimeUnit.SECONDS)
            if (failOutputOpen) error("open output failure")
            return object : OutputStream() {
                override fun write(value: Int) {
                    if (failOutputWrite) error("write failure")
                    output.write(value)
                }

                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    if (failOutputWrite) error("write failure")
                    output.write(bytes, offset, length)
                }

                override fun flush() {
                    if (failOutputFlush) error("flush failure")
                    outputFlushed = true
                }

                override fun close() {
                    outputClosed = true
                    outputCloseCount++
                    if (failOutputClose) error("close failure")
                }
            }
        }

        private fun recordThread() {
            if (Looper.myLooper() == Looper.getMainLooper()) mainThreadCalls++
        }
    }
}
