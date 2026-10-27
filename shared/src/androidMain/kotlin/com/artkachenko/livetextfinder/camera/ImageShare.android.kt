package com.artkachenko.livetextfinder.camera

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.artkachenko.livetextfinder.search.ElementOutline
import com.artkachenko.livetextfinder.search.HighlightColor
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.search.projectFrozenHighlights
import com.artkachenko.livetextfinder.search.projectImportedHighlights
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal actual fun rememberImageShareOwner(): ImageShareOwner {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val owner = remember { ImageShareOwner() }
    owner.attachContext(context)
    DisposableEffect(owner, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> owner.start()
                Lifecycle.Event.ON_STOP -> owner.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            owner.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            owner.dispose()
        }
    }
    return owner
}

internal actual class ImageShareOwner actual constructor() {
    private val _state = mutableStateOf(ImageShareState())
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context
    @Volatile private var disposed = false
    @Volatile private var foreground = false
    @Volatile private var currentSourceIdentity: String? = null
    @Volatile private var currentSearchIdentity: String = ""
    @Volatile private var currentHighlightColor: HighlightColor = HighlightColor.Yellow
    @Volatile private var requestId = 0L
    internal actual val state: State<ImageShareState> = _state

    internal fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

    internal actual fun cancel() {
        checkMainThread()
        requestId++
        if (_state.value.status == ImageShareStatus.Preparing) {
            _state.value = ImageShareState()
        }
    }

    internal actual fun dispose() {
        checkMainThread()
        disposed = true
        foreground = false
        cancel()
    }

    internal actual fun updateCurrent(
        sourceIdentity: String?,
        searchIdentity: String,
        highlightColor: HighlightColor,
    ) {
        checkMainThread()
        currentSourceIdentity = sourceIdentity
        currentSearchIdentity = searchIdentity
        currentHighlightColor = highlightColor
        if (_state.value.status == ImageShareStatus.Preparing &&
            (pendingSourceIdentity != sourceIdentity || pendingSearchIdentity != searchIdentity ||
                pendingHighlightColor != highlightColor)
        ) {
            cancel()
        }
    }

    private var pendingSourceIdentity: String? = null
    private var pendingSearchIdentity: String? = null
    private var pendingHighlightColor: HighlightColor? = null
    internal var beforeHandoffForTest: (() -> Unit)? = null
    internal var shareLauncherForTest: ((Intent) -> Unit)? = null
    internal var renderBitmapForTest: (() -> Bitmap)? = null
    internal var encodeForTest: ((ShareOutput, Bitmap) -> Unit)? = null
    internal var postToMainForTest: ((Runnable) -> Boolean)? = null
    internal var dispatchWorkerForTest: ((Runnable) -> Unit)? = null

    internal fun start() {
        checkMainThread()
        disposed = false
        foreground = true
        if (ShareWorkGate.acquire()) {
            try {
                ShareWorkGate.executor.execute {
                    try {
                        ShareCache.cleanup(appContext.cacheDir)
                    } finally {
                        ShareWorkGate.release()
                    }
                }
            } catch (error: RuntimeException) {
                ShareWorkGate.release()
            }
        }
    }

    internal fun stop() {
        checkMainThread()
        foreground = false
        if (_state.value.status == ImageShareStatus.Preparing) {
            cancel()
        }
    }

    internal fun requestFrozen(
        owner: FrozenFrameOwner,
        search: TextSearchResult,
        readSourceIdentity: () -> String?,
        readSearchIdentity: () -> String,
        readHighlightColor: () -> HighlightColor,
    ) {
        val freezeState = owner.state.value
        if (freezeState.mode != FreezeMode.Frozen) {
            return showError("Freeze an image before sharing.")
        }
        val frame = freezeState.frame ?: return showError("Freeze an image before sharing.")
        val bitmap = owner.currentBitmap() ?: return showError("The frozen image is unavailable.")
        if (bitmap.isRecycled) return showError("The frozen image is unavailable.")
        val sourceIdentity = "frozen:${frame.identity}"
        val snapshot = frame.snapshot
        if (search.snapshot !== snapshot || readSourceIdentity() != sourceIdentity ||
            readSearchIdentity() != search.spec.identity
        ) return showError("The image or search changed. Try sharing again.")
        currentSourceIdentity = sourceIdentity
        currentSearchIdentity = search.spec.identity
        val highlightColor = readHighlightColor()
        currentHighlightColor = highlightColor
        if (bitmap.width != snapshot.frame.bufferWidth ||
            bitmap.height != snapshot.frame.bufferHeight ||
            snapshot.frame.rotationDegrees !in VALID_ROTATIONS
        ) return showError("The frozen image does not match its OCR frame.")
        val uprightWidth = if (snapshot.frame.rotationDegrees % 180 == 0) {
            bitmap.width
        } else {
            bitmap.height
        }
        val uprightHeight = if (snapshot.frame.rotationDegrees % 180 == 0) {
            bitmap.height
        } else {
            bitmap.width
        }
        val outlines = projectFrozenHighlights(
            search,
            uprightWidth.toFloat(),
            uprightHeight.toFloat(),
        )
        begin(
            bitmap,
            sourceIdentity,
            search.spec.identity,
            uprightWidth,
            uprightHeight,
            outlines.outlines,
            readSourceIdentity,
            readSearchIdentity,
            readHighlightColor,
            highlightColor,
            isSourceCurrent = {
                val current = owner.state.value
                current.mode == FreezeMode.Frozen &&
                    owner.currentBitmap() === bitmap && current.frame === frame
            },
        ) {
            AndroidImageShareRenderer.renderFrozen(
                bitmap,
                snapshot.frame.rotationDegrees,
                outlines.outlines,
                highlightColor,
            )
        }
    }

    internal fun requestImported(
        owner: ImportedImageOwner,
        search: TextSearchResult,
        readSourceIdentity: () -> String?,
        readSearchIdentity: () -> String,
        readHighlightColor: () -> HighlightColor,
    ) {
        val state = owner.state.value
        val snapshot = state.snapshot ?: return showError("Import an image before sharing.")
        val bitmap = owner.currentBitmap() ?: return showError("The imported image is unavailable.")
        if (bitmap.isRecycled) return showError("The imported image is unavailable.")
        val sourceIdentity = "import:${snapshot.sourceIdentity}"
        if (state.phase != ImportedImagePhase.Imported || search.content !== snapshot ||
            readSourceIdentity() != sourceIdentity ||
            readSearchIdentity() != search.spec.identity
        ) return showError("The image or search changed. Try sharing again.")
        currentSourceIdentity = sourceIdentity
        currentSearchIdentity = search.spec.identity
        val highlightColor = readHighlightColor()
        currentHighlightColor = highlightColor
        if (bitmap.width != snapshot.width || bitmap.height != snapshot.height) {
            return showError("The imported image does not match its OCR result.")
        }
        val outlines = projectImportedHighlights(
            search,
            snapshot,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
        )
        begin(
            bitmap,
            sourceIdentity,
            search.spec.identity,
            bitmap.width,
            bitmap.height,
            outlines.outlines,
            readSourceIdentity,
            readSearchIdentity,
            readHighlightColor,
            highlightColor,
            isSourceCurrent = {
                owner.currentBitmap() === bitmap &&
                    owner.state.value.snapshot === snapshot &&
                    owner.state.value.phase == ImportedImagePhase.Imported
            },
        ) {
            AndroidImageShareRenderer.renderImported(
                bitmap,
                outlines.outlines,
                highlightColor,
            )
        }
    }

    private fun begin(
        source: Bitmap,
        sourceIdentity: String,
        searchIdentity: String,
        width: Int,
        height: Int,
        outlines: List<ElementOutline>,
        readSourceIdentity: () -> String?,
        readSearchIdentity: () -> String,
        readHighlightColor: () -> HighlightColor,
        highlightColor: HighlightColor,
        isSourceCurrent: () -> Boolean,
        render: () -> Bitmap,
    ) {
        checkMainThread()
        if (disposed || !foreground ||
            _state.value.status == ImageShareStatus.Preparing
        ) return
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) {
            showError("This image exceeds the sharing size limit.")
            return
        }
        if (!ShareWorkGate.acquire()) {
            showError("Another image operation is finishing. Try again.")
            return
        }
        val ticket = ++requestId
        pendingSourceIdentity = sourceIdentity
        pendingSearchIdentity = searchIdentity
        pendingHighlightColor = highlightColor
        _state.value = ImageShareState(ImageShareStatus.Preparing, "Preparing image…")
        try {
            val worker = Runnable {
                var rendered: Bitmap? = null
                var output: ShareOutput? = null
                try {
                    output = ShareCache.reserve(appContext.cacheDir)
                    rendered = renderBitmapForTest?.invoke() ?: render()
                    val encoder = encodeForTest
                    if (encoder == null) {
                        output.encode(rendered)
                    } else {
                        encoder(output, rendered)
                    }
                    rendered.recycle()
                    rendered = null
                    val result = output.publish()
                    beforeHandoffForTest?.invoke()
                    val handoffCompleted = CountDownLatch(1)
                    var handedOff = false
                    val handoff = Runnable {
                        try {
                            if (isCurrent(
                                    ticket,
                                    sourceIdentity,
                                    searchIdentity,
                                    readSourceIdentity,
                                    readSearchIdentity,
                                    readHighlightColor,
                                    highlightColor,
                                    isSourceCurrent,
                                )
                            ) {
                                shareIntent(result)
                                handedOff = true
                                _state.value = ImageShareState(
                                    ImageShareStatus.Opened,
                                    "Share sheet opened.",
                                )
                            }
                        } catch (error: Exception) {
                            _state.value = ImageShareState(
                                ImageShareStatus.Error,
                                "The share sheet could not be opened. Try again.",
                            )
                        } catch (error: OutOfMemoryError) {
                            _state.value = ImageShareState(
                                ImageShareStatus.Error,
                                "The share sheet could not be opened. Try again.",
                            )
                        } finally {
                            handoffCompleted.countDown()
                        }
                    }
                    val poster = postToMainForTest
                    val posted = if (poster == null) {
                        mainHandler.post(handoff)
                    } else {
                        poster(handoff)
                    }
                    if (!posted) {
                        try {
                            result.delete()
                        } finally {
                            ShareWorkGate.release()
                        }
                    } else {
                        try {
                            var interrupted = false
                            while (true) {
                                try {
                                    handoffCompleted.await()
                                    break
                                } catch (error: InterruptedException) {
                                    interrupted = true
                                }
                            }
                            if (interrupted) {
                                Thread.currentThread().interrupt()
                            }
                            if (!handedOff) {
                                result.delete()
                            }
                        } finally {
                            ShareWorkGate.release()
                        }
                    }
                } catch (error: Exception) {
                    output?.delete()
                    rendered?.takeUnless(Bitmap::isRecycled)?.recycle()
                    rendered = null
                    postFailure(
                        ticket,
                        sourceIdentity,
                        searchIdentity,
                        readSourceIdentity,
                        readSearchIdentity,
                        readHighlightColor,
                        highlightColor,
                        isSourceCurrent,
                        if (error is ShareQuotaExceededException) {
                            error.message.orEmpty()
                        } else {
                            "Could not prepare image. Try again."
                        },
                    )
                } catch (error: OutOfMemoryError) {
                    output?.delete()
                    rendered?.takeUnless(Bitmap::isRecycled)?.recycle()
                    rendered = null
                    postFailure(
                        ticket,
                        sourceIdentity,
                        searchIdentity,
                        readSourceIdentity,
                        readSearchIdentity,
                        readHighlightColor,
                        highlightColor,
                        isSourceCurrent,
                        "Could not prepare image. Try again.",
                    )
                } finally {
                    rendered?.takeUnless(Bitmap::isRecycled)?.recycle()
                }
            }
            val dispatcher = dispatchWorkerForTest
            if (dispatcher == null) {
                ShareWorkGate.executor.execute(worker)
            } else {
                dispatcher(worker)
            }
        } catch (error: RuntimeException) {
            ShareWorkGate.release()
            _state.value = ImageShareState(ImageShareStatus.Error, "Could not prepare image.")
        }
    }

    private fun isCurrent(
        ticket: Long,
        sourceIdentity: String,
        searchIdentity: String,
        readSourceIdentity: () -> String?,
        readSearchIdentity: () -> String,
        readHighlightColor: () -> HighlightColor,
        highlightColor: HighlightColor,
        isSourceCurrent: () -> Boolean,
    ): Boolean =
        ticket == requestId && !disposed && foreground &&
            currentSourceIdentity == sourceIdentity && currentSearchIdentity == searchIdentity &&
            currentHighlightColor == highlightColor && pendingHighlightColor == highlightColor &&
            pendingSourceIdentity == sourceIdentity && pendingSearchIdentity == searchIdentity &&
            readSourceIdentity() == sourceIdentity && readSearchIdentity() == searchIdentity &&
            readHighlightColor() == highlightColor &&
            isSourceCurrent()

    private fun postFailure(
        ticket: Long,
        sourceIdentity: String,
        searchIdentity: String,
        readSourceIdentity: () -> String?,
        readSearchIdentity: () -> String,
        readHighlightColor: () -> HighlightColor,
        highlightColor: HighlightColor,
        isSourceCurrent: () -> Boolean,
        message: String,
    ) {
        val posted = mainHandler.post {
            if (isCurrent(
                    ticket,
                    sourceIdentity,
                    searchIdentity,
                    readSourceIdentity,
                    readSearchIdentity,
                    readHighlightColor,
                    highlightColor,
                    isSourceCurrent,
                )
            ) {
                _state.value = ImageShareState(
                    ImageShareStatus.Error,
                    message,
                )
            }
            ShareWorkGate.release()
        }
        if (!posted) {
            ShareWorkGate.release()
        }
    }

    private fun shareIntent(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.shared-images",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(appContext.contentResolver, "Shared image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val testLauncher = shareLauncherForTest
        if (testLauncher == null) {
            appContext.startActivity(chooser)
        } else {
            testLauncher(chooser)
        }
    }

    private fun showError(message: String) {
        checkMainThread()
        _state.value = ImageShareState(ImageShareStatus.Error, message)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper())
    }

    private companion object {
        const val MAX_PIXELS = 4_000_000L
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
    }
}

internal actual fun ImageShareOwner.shareFrozen(
    owner: FrozenFrameOwner,
    search: TextSearchResult,
    currentSourceIdentity: () -> String?,
    currentSearchIdentity: () -> String,
    currentHighlightColor: () -> HighlightColor,
) = requestFrozen(
    owner,
    search,
    currentSourceIdentity,
    currentSearchIdentity,
    currentHighlightColor,
)

internal actual fun ImageShareOwner.shareImported(
    owner: ImportedImageOwner,
    search: TextSearchResult,
    currentSourceIdentity: () -> String?,
    currentSearchIdentity: () -> String,
    currentHighlightColor: () -> HighlightColor,
) = requestImported(
    owner,
    search,
    currentSourceIdentity,
    currentSearchIdentity,
    currentHighlightColor,
)

internal object ShareWorkGate {
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "image-sharing").apply { isDaemon = true }
    }
    private val active = AtomicBoolean(false)

    fun acquire() = active.compareAndSet(false, true)

    fun release() = active.set(false)

    fun isBusy() = active.get()
}
