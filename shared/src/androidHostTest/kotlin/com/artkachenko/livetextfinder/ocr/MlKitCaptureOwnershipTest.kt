package com.artkachenko.livetextfinder.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.media.SyntheticMediaImage
import android.os.Looper
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class MlKitCaptureOwnershipTest {
    private val ticket = FrameIdentity(14, 6)
    private val directExecutor = Executor { command -> command.run() }
    private val matrix = Matrix()

    private class QueueExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }

    @Test
    fun requestedProxyIsCopiedBeforeSameProxyOcrAndPairedWithCapturedMetadata() {
        val copyExecutor = QueueExecutor()
        val image = SyntheticMediaImage()
        var proxyCloses = 0
        var copies = 0
        var detectorCalls = 0
        var released = 0
        val proxy = imageProxy(image) { proxyCloses++ }
        val recognizer = recognizer(
            process = { input ->
                detectorCalls++
                assertSame(image, input)
                Tasks.forResult(Text("frozen text", emptyList<Text.TextBlock>()))
            },
            close = { released++ },
        )
        var capture: CapturedCameraFrame? = null
        var deliveredIdentity: FrameIdentity? = null
        val analyzer = MlKitTextAnalyzer(
            executor = directExecutor,
            captureIdentity = { ticket },
            recognizer = recognizer,
            captureExecutor = copyExecutor,
            copyImage = { received ->
                copies++
                assertSame(proxy, received)
                Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
            },
            onCaptured = {
                capture = it
                true
            },
        ) { _, identity -> deliveredIdentity = identity }

        assertTrue(analyzer.requestCapture(21))
        assertFalse(analyzer.requestCapture(22))
        analyzer.analyze(proxy)

        assertEquals(0, copies)
        assertEquals(0, detectorCalls)
        assertEquals(0, proxyCloses)
        copyExecutor.runNext()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val accepted = assertNotNull(capture)
        assertEquals(1, copies)
        assertEquals(1, detectorCalls)
        assertEquals(1, proxyCloses)
        assertEquals(21L, accepted.requestId)
        assertEquals(ticket, accepted.identity)
        assertEquals(42L, accepted.snapshot.frame.timestampNanos)
        assertEquals(90, accepted.snapshot.frame.rotationDegrees)
        assertEquals(400, accepted.bitmap.width)
        assertSame(ticket, deliveredIdentity)
        analyzer.close()
        assertEquals(1, released)
        accepted.bitmap.recycle()
    }

    @Test
    fun ordinaryFramesNeverInvokePixelCopy() {
        val copyExecutor = QueueExecutor()
        var copies = 0
        var proxyCloses = 0
        val image = SyntheticMediaImage()
        val analyzer = analyzer(
            copyExecutor = copyExecutor,
            copy = {
                copies++
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            },
        )

        analyzer.analyze(imageProxy(image) { proxyCloses++ })
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, copies)
        assertTrue(copyExecutor.tasks.isEmpty())
        assertEquals(1, proxyCloses)
        analyzer.close()
    }

    @Test
    fun cancellationBeforeHandoffClosesProxyAndDiscardsCopiedBitmap() {
        val copyExecutor = QueueExecutor()
        val image = SyntheticMediaImage()
        var proxyCloses = 0
        var detectorCalls = 0
        var copied: Bitmap? = null
        var delivered: CapturedCameraFrame? = null
        val analyzer = analyzer(
            copyExecutor = copyExecutor,
            copy = {
                Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).also { copied = it }
            },
            process = {
                detectorCalls++
                Tasks.forResult(Text("ignored", emptyList<Text.TextBlock>()))
            },
            onCaptured = {
                delivered = it
                true
            },
        )
        val proxy = imageProxy(image) { proxyCloses++ }
        assertTrue(analyzer.requestCapture(31))
        analyzer.analyze(proxy)
        analyzer.cancelCapture(31)
        copyExecutor.runNext()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, detectorCalls)
        assertEquals(1, proxyCloses)
        assertTrue(assertNotNull(copied).isRecycled)
        assertEquals(null, delivered)
        analyzer.close()
    }

    @Test
    fun pixelConversionFailureClosesProxyAndFailsCapture() {
        val copyExecutor = QueueExecutor()
        var proxyCloses = 0
        var failures = 0
        val analyzer = analyzer(
            copyExecutor = copyExecutor,
            copy = { throw IllegalStateException("conversion failed") },
            onCaptureFailure = { id, _ ->
                assertEquals(32L, id)
                failures++
            },
        )

        assertTrue(analyzer.requestCapture(32))
        analyzer.analyze(imageProxy(SyntheticMediaImage()) { proxyCloses++ })
        copyExecutor.runNext()

        assertEquals(1, proxyCloses)
        assertEquals(1, failures)
        analyzer.close()
    }

    @Test
    fun recognitionTaskFailureDiscardsCopiedPixelsAndFailsCapture() {
        val copyExecutor = QueueExecutor()
        val recognition = TaskCompletionSource<Text>()
        var copied: Bitmap? = null
        var proxyCloses = 0
        var failures = 0
        val analyzer = analyzer(
            copyExecutor = copyExecutor,
            copy = {
                Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).also { copied = it }
            },
            process = { recognition.task },
            onCaptureFailure = { id, _ ->
                assertEquals(33L, id)
                failures++
            },
        )

        assertTrue(analyzer.requestCapture(33))
        analyzer.analyze(imageProxy(SyntheticMediaImage()) { proxyCloses++ })
        copyExecutor.runNext()
        recognition.setException(IllegalStateException("recognition failed"))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, proxyCloses)
        assertEquals(1, failures)
        assertTrue(assertNotNull(copied).isRecycled)
        analyzer.close()
    }

    @Test
    fun rejectedMainHandoffUsesHandlerToCloseProxyAndFinishLifetime() {
        val copyExecutor = QueueExecutor()
        var copied: Bitmap? = null
        var proxyCloses = 0
        var failures = 0
        var released = 0
        val analyzer = MlKitTextAnalyzer(
            executor = Executor { throw RejectedExecutionException() },
            captureIdentity = { ticket },
            recognizer = recognizer(close = { released++ }),
            captureExecutor = copyExecutor,
            copyImage = {
                Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).also { copied = it }
            },
            onCaptureFailure = { id, _ ->
                assertEquals(34L, id)
                failures++
            },
        ) { _, _ ->
        }

        assertTrue(analyzer.requestCapture(34))
        analyzer.analyze(imageProxy(SyntheticMediaImage()) { proxyCloses++ })
        copyExecutor.runNext()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, proxyCloses)
        assertEquals(1, failures)
        assertTrue(assertNotNull(copied).isRecycled)
        assertEquals(0, released)
        analyzer.close()
        assertEquals(1, released)
    }

    @Test
    fun timeoutAfterRecognizerHandoffInvalidatesPairAndDefersRecognizerRelease() {
        val copyExecutor = QueueExecutor()
        val source = TaskCompletionSource<Text>()
        val image = SyntheticMediaImage()
        var proxyCloses = 0
        var released = 0
        var copied: Bitmap? = null
        var delivered: CapturedCameraFrame? = null
        val analyzer = analyzer(
            copyExecutor = copyExecutor,
            copy = {
                Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).also { copied = it }
            },
            process = { source.task },
            close = { released++ },
            onCaptured = {
                delivered = it
                true
            },
        )
        assertTrue(analyzer.requestCapture(41))
        analyzer.analyze(imageProxy(image) { proxyCloses++ })
        copyExecutor.runNext()
        analyzer.cancelCapture(41)
        assertTrue(assertNotNull(copied).isRecycled)
        analyzer.close()

        assertEquals(0, released)
        assertTrue(assertNotNull(copied).isRecycled)
        source.setResult(Text("late", emptyList<Text.TextBlock>()))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, proxyCloses)
        assertEquals(1, released)
        assertEquals(null, delivered)
    }

    @Test
    fun overLimitAndRejectedCopyExecutorsCloseTheOwnedProxyAndFailRequest() {
        var oversizedCopies = 0
        var oversizedCloses = 0
        var failure: Pair<Long, String>? = null
        val oversized = analyzer(
            copy = {
                oversizedCopies++
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            },
            onCaptureFailure = { id, message -> failure = id to message },
        )
        assertTrue(oversized.requestCapture(51))
        oversized.analyze(
            imageProxy(SyntheticMediaImage(), width = 4_001, height = 1_000) {
                oversizedCloses++
            },
        )

        assertEquals(0, oversizedCopies)
        assertEquals(1, oversizedCloses)
        assertEquals(51L, failure?.first)
        oversized.close()

        var rejectedCloses = 0
        failure = null
        val rejected = MlKitTextAnalyzer(
            executor = directExecutor,
            captureIdentity = { ticket },
            recognizer = recognizer(),
            captureExecutor = Executor { throw RejectedExecutionException() },
            copyImage = { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) },
            onCaptureFailure = { id, message -> failure = id to message },
        ) { _, _ ->
        }
        assertTrue(rejected.requestCapture(52))
        rejected.analyze(imageProxy(SyntheticMediaImage()) { rejectedCloses++ })

        assertEquals(1, rejectedCloses)
        assertEquals(52L, failure?.first)
        rejected.close()
    }

    @Test
    fun invalidDimensionsAndNonQuarterRotationFailBeforeCopy() {
        var copies = 0
        var closes = 0
        var failures = 0
        val analyzer = analyzer(
            copy = {
                copies++
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            },
            onCaptureFailure = { _, _ -> failures++ },
        )

        assertTrue(analyzer.requestCapture(61))
        analyzer.analyze(
            imageProxy(
                SyntheticMediaImage(),
                width = -2,
                height = -2,
                close = { closes++ },
            ),
        )
        assertTrue(analyzer.requestCapture(62))
        analyzer.analyze(
            imageProxy(
                SyntheticMediaImage(),
                rotation = 45,
                close = { closes++ },
            ),
        )

        assertEquals(0, copies)
        assertEquals(2, closes)
        assertEquals(2, failures)
        analyzer.close()
    }

    private fun analyzer(
        copyExecutor: Executor = directExecutor,
        copy: (ImageProxy) -> Bitmap,
        process: (android.media.Image) -> Task<Text> = {
            Tasks.forResult(Text("ordinary", emptyList<Text.TextBlock>()))
        },
        close: () -> Unit = {
        },
        onCaptured: (CapturedCameraFrame) -> Boolean = { false },
        onCaptureFailure: (Long, String) -> Unit = { _, _ -> },
    ): MlKitTextAnalyzer {
        return MlKitTextAnalyzer(
            executor = directExecutor,
            captureIdentity = { ticket },
            recognizer = recognizer(process, close),
            captureExecutor = copyExecutor,
            copyImage = copy,
            onCaptured = onCaptured,
            onCaptureFailure = onCaptureFailure,
        ) { _, _ -> }
    }

    private fun recognizer(
        process: (android.media.Image) -> Task<Text> = {
            Tasks.forResult(Text("ordinary", emptyList<Text.TextBlock>()))
        },
        close: () -> Unit = {
        },
    ): TextRecognizer = Proxy.newProxyInstance(
        TextRecognizer::class.java.classLoader,
        arrayOf(TextRecognizer::class.java),
    ) { instance, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === args?.get(0)
            "toString" -> "Capture test recognizer"
            "getDetectorType" -> Detector.TYPE_TEXT_RECOGNITION
            "process" -> process(args!![0] as android.media.Image)
            "close" -> close()
            else -> null
        }
    } as TextRecognizer

    private fun imageProxy(
        image: android.media.Image,
        width: Int = 400,
        height: Int = 200,
        rotation: Int = 90,
        close: () -> Unit,
    ): ImageProxy {
        val info = Proxy.newProxyInstance(
            ImageInfo::class.java.classLoader,
            arrayOf(ImageInfo::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getTimestamp" -> 42L
                "getRotationDegrees" -> rotation
                "getSensorToBufferTransformMatrix" -> matrix
                else -> null
            }
        } as ImageInfo
        return Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java),
        ) { instance, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.get(0)
                "toString" -> "Capture test proxy"
                "getImage" -> image
                "getWidth" -> width
                "getHeight" -> height
                "getImageInfo" -> info
                "getCropRect" -> Rect(0, 0, width, height)
                "close" -> close()
                else -> null
            }
        } as ImageProxy
    }
}
