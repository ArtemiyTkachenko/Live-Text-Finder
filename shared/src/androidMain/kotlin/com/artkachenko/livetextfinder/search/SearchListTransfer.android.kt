package com.artkachenko.livetextfinder.search

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Composable
internal actual fun rememberSearchListTransferOwner(): SearchListTransferOwner {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val owner = remember { SearchListTransferOwner() }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> owner.onImportResult(uri) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> owner.onExportResult(uri) }
    owner.attach(context, importLauncher::launch, exportLauncher::launch)
    DisposableEffect(owner, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> owner.onStart()
                Lifecycle.Event.ON_STOP -> owner.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            owner.onStart()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            owner.dispose()
        }
    }
    return owner
}

internal actual class SearchListTransferOwner actual constructor() {
    private val mutableState = mutableStateOf(SearchListTransferState())
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context
    private var launchImport: ((Array<String>) -> Unit)? = null
    private var launchExport: ((String) -> Unit)? = null
    private var currentContext: TransferContext? = null
    private var currentCopy: SearchListTransferCopy? = null
    private var importDraft: ((String, List<String>) -> Unit)? = null
    private var liveContextIsCurrent: () -> Boolean = { false }
    private var documentAccess: TransferDocumentAccess? = null
    internal var workerDispatcherForTest: (((() -> Unit) -> Unit))? = null
    @Volatile private var generation = 0L
    @Volatile private var ticket: TransferTicket? = null
    private var expectedPickerStop = false
    private var deferredResult: DeferredResult? = null
    @Volatile private var disposed = false
    private var foreground = false

    internal actual val state: State<SearchListTransferState> = mutableState

    internal fun attach(
        context: Context,
        openDocument: (Array<String>) -> Unit,
        createDocument: (String) -> Unit,
    ) {
        appContext = context.applicationContext
        documentAccess = ResolverTransferDocumentAccess(context.contentResolver)
        launchImport = openDocument
        launchExport = createDocument
    }

    internal actual fun updateContext(
        sessionState: SearchSessionState,
        managerOpen: Boolean,
        editor: SearchListEditorState,
        contextRevision: Long,
        copy: SearchListTransferCopy,
        isContextCurrent: () -> Boolean,
        onImportDraft: (String, List<String>) -> Unit,
    ) {
        checkMainThread()
        val nextContext = TransferContext(sessionState, managerOpen, editor, contextRevision)
        currentCopy = copy
        liveContextIsCurrent = isContextCurrent
        importDraft = onImportDraft
        if (currentContext != nextContext) {
            currentContext = nextContext
            invalidateActive()
        }
        mutableState.value = mutableState.value.copy(
            supported = true,
            importEnabled = canImportNow(),
            exportEnabled = canExportNow(),
        )
    }

    internal fun onImportResult(uri: Uri?) {
        receivePickerResult(TransferKind.Import, uri)
    }

    internal fun onExportResult(uri: Uri?) {
        receivePickerResult(TransferKind.Export, uri)
    }

    private fun receivePickerResult(kind: TransferKind, uri: Uri?) {
        checkMainThread()
        val current = ticket
        if (current == null) {
            TransferGate.consumeOrphanPickerCallback(kind)
            return
        }
        if (current.kind != kind || current.phase != TransferPhase.Picking ||
            !isTicketCurrent(current)
        ) {
            return
        }
        expectedPickerStop = false
        current.pickerResultDelivered = true
        if (!foreground) {
            if (deferredResult == null) {
                deferredResult = DeferredResult(kind, uri)
            }
            return
        }
        processPickerResult(current, uri)
    }

    private fun processPickerResult(current: TransferTicket, uri: Uri?) {
        if (current.phase != TransferPhase.Picking) return
        if (!isCurrent(current)) {
            discardTicket(current)
            return
        }
        if (uri == null) {
            finishTicket(current)
            val copy = current.copy
            showMessage(
                if (current.kind == TransferKind.Import) {
                    copy.importCanceled
                } else {
                    copy.exportCanceled
                },
            )
            return
        }
        when (current.kind) {
            TransferKind.Import -> runImport(current, uri)
            TransferKind.Export -> runExport(current, uri)
        }
    }

    internal fun onStart() {
        checkMainThread()
        foreground = true
        expectedPickerStop = false
        val deferred = deferredResult
        deferredResult = null
        if (deferred != null) {
            val current = ticket
            if (current != null && current.kind == deferred.kind && isTicketCurrent(current)) {
                processPickerResult(current, deferred.uri)
            }
        }
    }

    internal fun onStop() {
        checkMainThread()
        foreground = false
        if (ticket?.phase == TransferPhase.Picking && expectedPickerStop) {
            expectedPickerStop = false
            return
        }
        invalidateActive()
    }

    internal actual fun contextChanged() {
        checkMainThread()
        invalidateActive()
        currentContext = null
        liveContextIsCurrent = { false }
        mutableState.value = SearchListTransferState(supported = true)
    }

    internal actual fun importList() {
        checkMainThread()
        val context = currentContext ?: return
        if (!liveContextIsCurrent()) {
            contextChanged()
            return
        }
        if (!canImport(context)) {
            showMessage(checkNotNull(currentCopy).importUnavailable, true)
            return
        }
        if (launchImport == null) return
        if (beginPicker(TransferKind.Import) == null) return
        mutableState.value = SearchListTransferState(
            supported = true,
            busy = true,
            message = checkNotNull(currentCopy).chooseFile,
        )
        expectedPickerStop = true
        try {
            launchImport?.invoke(arrayOf("text/plain"))
            ticket?.pickerLaunched = true
        } catch (_: RuntimeException) {
            ticket?.let { failTicket(it, checkNotNull(currentCopy).importFailed) }
        }
    }

    internal actual fun exportSelectedList() {
        checkMainThread()
        val context = currentContext ?: return
        if (!liveContextIsCurrent()) {
            contextChanged()
            return
        }
        if (!canExport(context)) {
            showMessage(checkNotNull(currentCopy).exportUnavailable, true)
            return
        }
        val selected = context.sessionState.activeList ?: return
        val bytes = try {
            serializeSearchListText(selected.terms)
        } catch (error: IllegalArgumentException) {
            showMessage(checkNotNull(currentCopy).listCannotExport, true)
            return
        }
        if (launchExport == null) return
        val current = beginPicker(TransferKind.Export) ?: return
        current.exportBytes = bytes
        mutableState.value = SearchListTransferState(
            supported = true,
            busy = true,
            message = checkNotNull(currentCopy).chooseDestination,
        )
        expectedPickerStop = true
        try {
            launchExport?.invoke(searchListExportFileName(selected.name))
            current.pickerLaunched = true
        } catch (_: RuntimeException) {
            failTicket(current, checkNotNull(currentCopy).exportFailed)
        }
    }

    internal actual fun cancel() {
        checkMainThread()
        invalidateActive()
        mutableState.value = SearchListTransferState(
            supported = true,
            importEnabled = canImportNow(),
            exportEnabled = canExportNow(),
        )
    }

    internal actual fun dispose() {
        checkMainThread()
        disposed = true
        foreground = false
        invalidateActive()
    }

    private fun beginPicker(kind: TransferKind): TransferTicket? {
        if (isBusy) {
            showMessage(checkNotNull(currentCopy).anotherTransfer, true)
            return null
        }
        val context = currentContext ?: return null
        val current = TransferTicket(++generation, kind, context, checkNotNull(currentCopy))
        if (!TransferGate.acquire(current)) {
            showMessage(checkNotNull(currentCopy).anotherTransfer, true)
            return null
        }
        ticket = current
        return current
    }

    private fun runImport(current: TransferTicket, uri: Uri) {
        current.phase = TransferPhase.Working
        mutableState.value = SearchListTransferState(
            supported = true,
            busy = true,
            message = current.copy.readingFile,
        )
        executeWorker {
            val result = runCatching {
                check(isCurrentOffMain(current))
                check(isLiveContextCurrentOnMain(current))
                val displayName = readDisplayName(uri)
                check(isCurrentOffMain(current))
                check(isLiveContextCurrentOnMain(current))
                val input = openInput(uri)
                    ?: error("The selected file could not be opened.")
                val terms = input.use { stream ->
                    check(isCurrentOffMain(current))
                    check(isLiveContextCurrentOnMain(current))
                    val parsed = parseSearchListText(
                        readBounded(stream, current),
                    ).getOrElse { throw it }
                    check(isLiveContextCurrentOnMain(current))
                    parsed
                }
                check(isLiveContextCurrentOnMain(current))
                check(isCurrentOffMain(current))
                searchListImportName(displayName, current.copy.fallbackName) to terms
            }
            mainHandler.post {
                if (!isCurrent(current)) {
                    discardTicket(current)
                } else {
                    result.fold(
                        onSuccess = { (name, terms) ->
                            if (currentContext?.let(::canImport) != true) {
                                failTicket(current, current.copy.importUnavailable)
                            } else {
                                finishTicket(current)
                                showMessage(current.copy.importReady)
                                importDraft?.invoke(name, terms)
                            }
                        },
                        onFailure = {
                            failTicket(current, current.copy.importFailed)
                        },
                    )
                }
            }
        }
    }

    private fun runExport(current: TransferTicket, uri: Uri) {
        current.phase = TransferPhase.Working
        mutableState.value = SearchListTransferState(
            supported = true,
            busy = true,
            message = current.copy.savingFile,
        )
        executeWorker {
            val result = runCatching {
                check(isCurrentOffMain(current))
                check(isLiveContextCurrentOnMain(current))
                val output = openOutput(uri)
                    ?: error("The destination file could not be opened.")
                output.use { stream ->
                    check(isCurrentOffMain(current))
                    check(isLiveContextCurrentOnMain(current))
                    stream.write(checkNotNull(current.exportBytes))
                    check(isLiveContextCurrentOnMain(current))
                    stream.flush()
                    check(isLiveContextCurrentOnMain(current))
                }
                check(isLiveContextCurrentOnMain(current))
            }
            mainHandler.post {
                if (!isCurrent(current)) {
                    discardTicket(current)
                } else {
                    result.fold(
                        onSuccess = {
                            finishTicket(current)
                            showMessage(current.copy.exportComplete)
                        },
                        onFailure = {
                            failTicket(current, current.copy.exportFailed)
                        },
                    )
                }
            }
        }
    }

    internal fun setDocumentAccessForTest(documentAccess: TransferDocumentAccess) {
        this.documentAccess = documentAccess
    }

    internal fun setLiveContextPredicateForTest(isCurrent: () -> Boolean) {
        liveContextIsCurrent = isCurrent
    }

    private fun executeWorker(block: () -> Unit) {
        val testDispatcher = workerDispatcherForTest
        try {
            if (testDispatcher == null) {
                Thread(block).start()
            } else {
                testDispatcher(block)
            }
        } catch (_: RuntimeException) {
            ticket?.let { current ->
                failTicket(
                    current,
                    if (current.kind == TransferKind.Import) {
                        current.copy.importFailed
                    } else {
                        current.copy.exportFailed
                    },
                )
            }
        }
    }

    private fun readDisplayName(uri: Uri): String? = runCatching {
        documentAccess?.displayName(uri)
    }.getOrNull()

    private fun openInput(uri: Uri): InputStream? = documentAccess?.openInput(uri)

    private fun openOutput(uri: Uri): OutputStream? = documentAccess?.openOutput(uri)

    private fun readBounded(
        stream: InputStream,
        current: TransferTicket,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (total <= MAX_SEARCH_LIST_TRANSFER_BYTES) {
            val count = stream.read(
                buffer,
                0,
                minOf(buffer.size, MAX_SEARCH_LIST_TRANSFER_BYTES + 1 - total),
            )
            check(isCurrentOffMain(current))
            check(isLiveContextCurrentOnMain(current))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    private fun isCurrent(current: TransferTicket): Boolean =
        foreground && isTicketCurrent(current) && liveContextIsCurrent()

    private fun isTicketCurrent(current: TransferTicket): Boolean = !disposed &&
        ticket === current && current.id == generation && currentContext == current.context

    private fun isCurrentOffMain(current: TransferTicket): Boolean = !disposed &&
        ticket === current && current.id == generation

    private fun isLiveContextCurrentOnMain(current: TransferTicket): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return isCurrent(current)
        }
        val result = booleanArrayOf(false)
        val completed = CountDownLatch(1)
        if (!mainHandler.post {
                result[0] = isCurrent(current)
                completed.countDown()
            }
        ) {
            return false
        }
        return try {
            completed.await(30, TimeUnit.SECONDS) && result[0]
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun invalidateActive() {
        val current = ticket ?: return
        ticket = null
        generation++
        expectedPickerStop = false
        deferredResult = null
        if (current.phase == TransferPhase.Picking) {
            if (current.pickerLaunched && !current.pickerResultDelivered) {
                TransferGate.orphanPicker(current, current.kind)
            } else {
                TransferGate.release(current)
            }
        }
        mutableState.value = SearchListTransferState(
            supported = true,
            importEnabled = canImportNow(),
            exportEnabled = canExportNow(),
        )
    }

    private fun finishTicket(current: TransferTicket) {
        if (ticket === current) {
            ticket = null
            generation++
            expectedPickerStop = false
        }
        TransferGate.release(current)
        if (!disposed) {
            mutableState.value = mutableState.value.copy(
                importEnabled = canImportNow(),
                exportEnabled = canExportNow(),
            )
        }
    }

    private fun discardTicket(current: TransferTicket) {
        finishTicket(current)
        if (liveContextIsCurrent().not()) currentContext = null
        if (!disposed) {
            mutableState.value = SearchListTransferState(
                supported = true,
                importEnabled = canImportNow(),
                exportEnabled = canExportNow(),
            )
        }
    }

    internal fun isTransferInFlightForTest(): Boolean = isBusy

    private fun failTicket(current: TransferTicket, message: String) {
        finishTicket(current)
        showMessage(message, true)
    }

    private fun showMessage(message: String, isError: Boolean = false) {
        mutableState.value = SearchListTransferState(
            supported = true,
            importEnabled = canImportNow(),
            exportEnabled = canExportNow(),
            message = message,
            isError = isError,
        )
    }

    private fun canImport(context: TransferContext): Boolean =
        context.sessionState.loaded && !context.sessionState.saving &&
            !context.sessionState.recoveryRequired && context.sessionState.error == null &&
            !context.editor.isOpen && context.sessionState.persisted.lists.size < MAX_LISTS

    private fun canExport(context: TransferContext): Boolean =
        context.sessionState.loaded && !context.sessionState.saving &&
            !context.sessionState.recoveryRequired && context.sessionState.error == null &&
            context.sessionState.activeList != null

    private fun canImportNow(): Boolean = currentContext?.let(::canImport) == true && !isBusy

    private fun canExportNow(): Boolean = currentContext?.let(::canExport) == true && !isBusy

    private val isBusy: Boolean
        get() = ticket != null || TransferGate.isHeld()

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper())
    }

    private data class TransferContext(
        val sessionState: SearchSessionState,
        val managerOpen: Boolean,
        val editor: SearchListEditorState,
        val contextRevision: Long,
    )

    internal enum class TransferKind {
        Import,
        Export,
    }

    private enum class TransferPhase {
        Picking,
        Working,
    }

    private data class DeferredResult(val kind: TransferKind, val uri: Uri?)

    internal interface TransferDocumentAccess {
        fun displayName(uri: Uri): String?

        fun openInput(uri: Uri): InputStream?

        fun openOutput(uri: Uri): OutputStream?
    }

    private class TransferTicket(
        val id: Long,
        val kind: TransferKind,
        val context: TransferContext,
        val copy: SearchListTransferCopy,
        var phase: TransferPhase = TransferPhase.Picking,
        var exportBytes: ByteArray? = null,
        var pickerLaunched: Boolean = false,
        var pickerResultDelivered: Boolean = false,
    )
}

private class ResolverTransferDocumentAccess(
    private val resolver: android.content.ContentResolver,
) : SearchListTransferOwner.TransferDocumentAccess {
    override fun displayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    }

    override fun openInput(uri: Uri): InputStream? = resolver.openInputStream(uri)

    override fun openOutput(uri: Uri): OutputStream? = resolver.openOutputStream(uri)
}

private object TransferGate {
    private val heldBy = AtomicReference<GateEntry?>(null)

    fun acquire(token: Any): Boolean = heldBy.compareAndSet(null, GateEntry(token))

    fun release(token: Any) {
        while (true) {
            val current = heldBy.get() ?: return
            if (current.token !== token) return
            if (heldBy.compareAndSet(current, null)) return
        }
    }

    fun orphanPicker(token: Any, kind: SearchListTransferOwner.TransferKind) {
        while (true) {
            val current = heldBy.get() ?: return
            if (current.token !== token) return
            if (heldBy.compareAndSet(current, GateEntry(token, kind))) return
        }
    }

    fun consumeOrphanPickerCallback(kind: SearchListTransferOwner.TransferKind): Boolean {
        while (true) {
            val current = heldBy.get() ?: return false
            if (current.orphanKind != kind) return false
            if (heldBy.compareAndSet(current, null)) return true
        }
    }

    fun isHeld(): Boolean = heldBy.get() != null

    private class GateEntry(
        val token: Any,
        val orphanKind: SearchListTransferOwner.TransferKind? = null,
    )
}
