package com.artkachenko.livetextfinder.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Composable
internal actual fun rememberImportedImageOwner(): ImportedImageOwner {
    val context = LocalContext.current.applicationContext
    val owner = remember { ImportedImageOwner() }
    owner.attachResolver(context.contentResolver)
    DisposableEffect(owner) {
        onDispose(owner::dispose)
    }
    return owner
}

internal actual class ImportedImageOwner actual constructor() {
    private val mutableState = mutableStateOf(ImportedImageState())
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var currentRequestId: Long? = null
    @Volatile private var disposed = false
    private var foreground = false
    private var pendingUri: Uri? = null
    private var bitmap: Bitmap? = null
    private var contentResolver: ContentResolver? = null
    private var outstandingPickerTicket: Long? = null
    private var processor: ImportedImageProcessor = AndroidImportedImageProcessor()

    internal actual val state: State<ImportedImageState> = mutableState

    internal actual fun beginPicker(): Long? {
        checkMainThread()
        if (outstandingPickerTicket != null) {
            mutableState.value = ImportedImageState(
                phase = ImportedImagePhase.Error,
                message = "Finish the current photo picker before starting another.",
            )
            return null
        }
        clearBitmap()
        pendingUri = null
        val requestId = nextImportRequestId.incrementAndGet()
        currentRequestId = requestId
        disposed = false
        mutableState.value = ImportedImageState(
            phase = ImportedImagePhase.Picking,
            requestId = requestId,
        )
        return requestId
    }

    internal fun attachResolver(resolver: ContentResolver) {
        contentResolver = resolver
    }

    internal fun setProcessorForTest(
        resolver: ContentResolver,
        processor: ImportedImageProcessor,
    ) {
        checkMainThread()
        contentResolver = resolver
        this.processor = processor
    }

    internal fun pickerWillLaunch(requestId: Long): Boolean {
        checkMainThread()
        if (outstandingPickerTicket != null || currentRequestId != requestId || disposed) {
            return false
        }
        outstandingPickerTicket = requestId
        return true
    }

    internal fun acceptPickerResult(requestId: Long, uri: Uri?) {
        checkMainThread()
        if (outstandingPickerTicket == requestId) {
            outstandingPickerTicket = null
        }
        if (disposed || currentRequestId != requestId ||
            mutableState.value.phase != ImportedImagePhase.Picking
        ) {
            return
        }
        if (uri == null) {
            invalidateToLive()
            return
        }
        pendingUri = uri
        if (foreground) {
            pendingUri = null
            startImport(requestId, uri)
        }
    }

    internal actual fun cancel() {
        checkMainThread()
        invalidateToLive()
    }

    internal actual fun stop() {
        checkMainThread()
        foreground = false
        if (mutableState.value.phase != ImportedImagePhase.Picking) {
            invalidateToLive()
        }
    }

    internal actual fun dispose() {
        checkMainThread()
        disposed = true
        invalidateToLive()
    }

    internal fun start() {
        checkMainThread()
        foreground = true
        val requestId = currentRequestId ?: return
        val uri = pendingUri ?: return
        pendingUri = null
        startImport(requestId, uri)
    }

    internal fun pickerLaunchFailed(requestId: Long) {
        checkMainThread()
        if (outstandingPickerTicket == requestId) {
            outstandingPickerTicket = null
        }
        if (currentRequestId == requestId) {
            showError(requestId, "The photo picker could not be opened. Try again.")
        }
    }

    internal fun currentBitmap(): Bitmap? = bitmap

    internal fun isImportWorkInFlightForTest(): Boolean = ImportedImageWorkGate.isBusy()

    private fun startImport(requestId: Long, uri: Uri) {
        if (currentRequestId != requestId || disposed) {
            return
        }
        mutableState.value = ImportedImageState(
            phase = ImportedImagePhase.Reading,
            requestId = requestId,
        )
        if (!ImportedImageWorkGate.acquire()) {
            showError(requestId, "Another image operation is still finishing. Try again.")
            return
        }
        try {
            ImportedImageWorkGate.executor.execute {
                processImport(requestId, uri)
            }
        } catch (error: RuntimeException) {
            ImportedImageWorkGate.release()
            showError(requestId, "The selected image could not be opened. Try again.")
        }
    }

    private fun processImport(requestId: Long, uri: Uri) {
        var processed: ProcessedImportedImage? = null
        var adopted = false
        try {
            val resolver = checkNotNull(contentResolver)
            val sourceIdentity = nextSourceIdentity.incrementAndGet()
            processed = processor.process(resolver, uri, sourceIdentity) {
                mainHandler.post {
                    if (isCurrent(requestId)) {
                        mutableState.value = ImportedImageState(
                            phase = ImportedImagePhase.Recognizing,
                            requestId = requestId,
                        )
                    }
                }
            }
            val result = checkNotNull(processed)
            val posted = mainHandler.post {
                adoptProcessedImage(requestId, result)
                ImportedImageWorkGate.release()
            }
            if (!posted) {
                result.bitmap.recycle()
                ImportedImageWorkGate.release()
            } else {
                adopted = true
            }
        } catch (error: Exception) {
            postProcessingFailure(requestId)
        } catch (error: OutOfMemoryError) {
            postProcessingFailure(requestId)
        } finally {
            if (!adopted) {
                processed?.bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
            }
        }
    }

    internal fun adoptProcessedImage(requestId: Long, result: ProcessedImportedImage): Boolean {
        checkMainThread()
        if (!isCurrent(requestId)) {
            result.bitmap.recycle()
            return false
        }
        bitmap = result.bitmap
        mutableState.value = ImportedImageState(
            phase = ImportedImagePhase.Imported,
            requestId = requestId,
            snapshot = result.snapshot,
        )
        return true
    }

    private fun postProcessingFailure(requestId: Long) {
        val postedError = mainHandler.post {
            if (isCurrent(requestId)) {
                showError(
                    requestId,
                    "The selected image could not be processed. Choose another.",
                )
            }
            ImportedImageWorkGate.release()
        }
        if (!postedError) {
            ImportedImageWorkGate.release()
        }
    }

    private fun showError(requestId: Long, message: String) {
        if (currentRequestId != requestId || disposed) {
            return
        }
        clearBitmap()
        mutableState.value = ImportedImageState(
            phase = ImportedImagePhase.Error,
            requestId = requestId,
            message = message,
        )
    }

    private fun invalidateToLive() {
        currentRequestId = null
        pendingUri = null
        clearBitmap()
        mutableState.value = ImportedImageState()
    }

    private fun clearBitmap() {
        bitmap = null
    }

    private fun isCurrent(requestId: Long) = !disposed && currentRequestId == requestId

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper())
    }
}

@Composable
internal actual fun ImportedImagePicker(owner: ImportedImageOwner) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var launchedTicket by remember(owner) { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        launchedTicket?.let { requestId ->
            launchedTicket = null
            owner.acceptPickerResult(requestId, uri)
        }
    }
    DisposableEffect(owner, lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            owner.start()
        } else {
            owner.stop()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> owner.start()
                Lifecycle.Event.ON_STOP -> owner.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val state = owner.state.value
    LaunchedEffect(state.phase, state.requestId) {
        val requestId = state.requestId ?: return@LaunchedEffect
        if (state.phase == ImportedImagePhase.Picking && launchedTicket == null &&
            owner.pickerWillLaunch(requestId)
        ) {
            launchedTicket = requestId
            runCatching {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }.onFailure {
                launchedTicket = null
                owner.pickerLaunchFailed(requestId)
            }
        }
    }
}

@Composable
internal actual fun ImportedImagePreview(
    owner: ImportedImageOwner,
    modifier: Modifier,
    isVisible: () -> Boolean,
) {
    val state = owner.state.value
    val bitmap = owner.currentBitmap()
    if (state.phase != ImportedImagePhase.Imported || bitmap == null || state.snapshot == null) {
        return
    }
    Canvas(modifier) {
        drawImportedBitmap(owner, state.snapshot.sourceIdentity, bitmap, isVisible)
    }
}

internal fun DrawScope.drawImportedBitmap(
    owner: ImportedImageOwner,
    sourceIdentity: Long,
    bitmap: Bitmap,
    isVisible: () -> Boolean,
) {
    if (!isVisible()) {
        return
    }
    val state = owner.state.value
    if (state.phase != ImportedImagePhase.Imported ||
        state.snapshot?.sourceIdentity != sourceIdentity || owner.currentBitmap() !== bitmap
    ) {
        return
    }
    val fit = FrozenFrameFit(bitmap.width, bitmap.height, 0, size.width, size.height)
    if (!fit.isValid || !isVisible()) {
        return
    }
    val center = Offset(size.width / 2f, size.height / 2f)
    withTransform({
        translate(center.x, center.y)
        rotate(0f, Offset.Zero)
        scale(fit.scale, fit.scale, Offset.Zero)
        translate(-bitmap.width / 2f, -bitmap.height / 2f)
    }) {
        drawImage(bitmap.asImageBitmap())
    }
}

private object ImportedImageWorkGate {
    val executor = Executors.newSingleThreadExecutor()
    private val active = AtomicBoolean(false)

    fun acquire() = active.compareAndSet(false, true)

    fun release() {
        active.set(false)
    }

    fun isBusy() = active.get()
}

private val nextImportRequestId = AtomicLong(0L)
private val nextSourceIdentity = AtomicLong(0L)
