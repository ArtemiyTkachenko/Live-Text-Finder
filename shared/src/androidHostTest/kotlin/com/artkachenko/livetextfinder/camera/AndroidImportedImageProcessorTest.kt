package com.artkachenko.livetextfinder.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImportedImageProcessorTest {
    @Test
    fun cancellationAndBackgroundKeepRealDecodedBitmapRecognizerAndGateUntilOcrDrains() {
        MlKitContext.initializeIfNeeded(RuntimeEnvironment.getApplication())
        listOf(false, true).forEach { backgrounded ->
            val owner = ImportedImageOwner()
            owner.start()
            val resolver = RuntimeEnvironment.getApplication().contentResolver
            val sourceName = if (backgrounded) "bg" else "cancel"
            val firstUri = Uri.parse("content://import.test/$sourceName")
            val secondUri = Uri.parse("$firstUri-next")
            val encoded = encodedPng()
            val sourceClosed = AtomicInteger()
            registerBytes(resolver, firstUri, encoded, sourceClosed)
            registerBytes(resolver, secondUri, encoded, sourceClosed)
            val pendingTasks = LinkedBlockingQueue<TaskCompletionSource<Text>>()
            val recognizerCloseCount = AtomicInteger()
            val observedImages = CopyOnWriteArrayList<Any>()
            val processor = AndroidImportedImageProcessor {
                val source = TaskCompletionSource<Text>()
                pendingTasks.add(source)
                recognizer(source.task, recognizerCloseCount) { image ->
                    observedImages += image
                }
            }
            owner.setProcessorForTest(resolver, processor)
            val firstTicket = checkNotNull(owner.beginPicker())
            assertTrue(owner.pickerWillLaunch(firstTicket))
            owner.acceptPickerResult(firstTicket, firstUri)
            awaitPhase(owner, ImportedImagePhase.Recognizing)
            awaitImageCount(observedImages, 1)

            val firstBitmap = imageBitmap(observedImages.single())
            assertEquals(8, firstBitmap.width)
            assertEquals(6, firstBitmap.height)
            assertFalse(firstBitmap.isRecycled)
            assertEquals(1, sourceClosed.get())
            assertEquals(0, recognizerCloseCount.get())
            assertTrue(owner.isImportWorkInFlightForTest())

            if (backgrounded) {
                owner.stop()
                assertEquals(ImportedImagePhase.Live, owner.state.value.phase)
            } else {
                owner.cancel()
                assertEquals(ImportedImagePhase.Live, owner.state.value.phase)
            }
            val blockedTicket = checkNotNull(owner.beginPicker())
            assertTrue(owner.pickerWillLaunch(blockedTicket))
            owner.acceptPickerResult(blockedTicket, secondUri)
            if (backgrounded) {
                assertEquals(ImportedImagePhase.Picking, owner.state.value.phase)
                owner.start()
            }
            assertEquals(ImportedImagePhase.Error, owner.state.value.phase)
            assertFalse(firstBitmap.isRecycled)
            assertEquals(0, recognizerCloseCount.get())
            assertTrue(owner.isImportWorkInFlightForTest())

            checkNotNull(pendingTasks.poll(3, java.util.concurrent.TimeUnit.SECONDS))
                .setResult(Text("done", emptyList<Text.TextBlock>()))
            awaitWorkGate(owner)
            assertTrue(firstBitmap.isRecycled)
            assertEquals(1, recognizerCloseCount.get())
            assertEquals(ImportedImagePhase.Error, owner.state.value.phase)

            val finalTicket = checkNotNull(owner.beginPicker())
            assertTrue(owner.pickerWillLaunch(finalTicket))
            owner.acceptPickerResult(finalTicket, secondUri)
            awaitPhase(owner, ImportedImagePhase.Recognizing)
            awaitImageCount(observedImages, 2)
            val finalBitmap = imageBitmap(observedImages.last())
            assertFalse(finalBitmap.isRecycled)
            checkNotNull(pendingTasks.poll(3, java.util.concurrent.TimeUnit.SECONDS))
                .setResult(Text("found", emptyList<Text.TextBlock>()))
            awaitPhase(owner, ImportedImagePhase.Imported)
            assertSame(finalBitmap, owner.currentBitmap())
            assertFalse(finalBitmap.isRecycled)
            assertEquals(2, sourceClosed.get())
            assertEquals(2, recognizerCloseCount.get())
            owner.dispose()
            finalBitmap.recycle()
        }
    }

    @Test
    fun failedSdkTaskRecyclesDecodedBitmapClosesRecognizerAndAllowsAnotherImport() {
        MlKitContext.initializeIfNeeded(RuntimeEnvironment.getApplication())
        val owner = ImportedImageOwner()
        owner.start()
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val uri = Uri.parse("content://import.test/failure")
        val encoded = encodedPng()
        registerBytes(resolver, uri, encoded, AtomicInteger())
        val tasks = LinkedBlockingQueue<TaskCompletionSource<Text>>()
        val closed = AtomicInteger()
        val images = CopyOnWriteArrayList<Any>()
        owner.setProcessorForTest(
            resolver,
            AndroidImportedImageProcessor {
                val source = TaskCompletionSource<Text>()
                tasks.add(source)
                recognizer(source.task, closed) { image -> images += image }
            },
        )
        val ticket = checkNotNull(owner.beginPicker())
        assertTrue(owner.pickerWillLaunch(ticket))
        owner.acceptPickerResult(ticket, uri)
        awaitPhase(owner, ImportedImagePhase.Recognizing)
        awaitImageCount(images, 1)
        val rejectedBitmap = imageBitmap(images.single())
        checkNotNull(tasks.poll(3, java.util.concurrent.TimeUnit.SECONDS))
            .setException(IllegalStateException("fixture OCR failure"))
        awaitPhase(owner, ImportedImagePhase.Error)
        awaitWorkGate(owner)
        assertTrue(rejectedBitmap.isRecycled)
        assertEquals(1, closed.get())
        assertFalse(owner.isImportWorkInFlightForTest())

        val retryTicket = checkNotNull(owner.beginPicker())
        assertTrue(owner.pickerWillLaunch(retryTicket))
        owner.acceptPickerResult(retryTicket, uri)
        awaitPhase(owner, ImportedImagePhase.Recognizing)
        awaitImageCount(images, 2)
        val retryBitmap = imageBitmap(images.last())
        checkNotNull(tasks.poll(3, java.util.concurrent.TimeUnit.SECONDS))
            .setResult(Text("retry", emptyList<Text.TextBlock>()))
        awaitPhase(owner, ImportedImagePhase.Imported)
        assertSame(retryBitmap, owner.currentBitmap())
        assertFalse(retryBitmap.isRecycled)
        assertEquals(2, closed.get())
        retryBitmap.recycle()
        owner.dispose()
    }

    private fun recognizer(
        task: Task<Text>,
        closeCount: AtomicInteger,
        onImage: (Any) -> Unit,
    ): TextRecognizer = Proxy.newProxyInstance(
        TextRecognizer::class.java.classLoader,
        arrayOf(TextRecognizer::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "getDetectorType" -> Detector.TYPE_TEXT_RECOGNITION
            "process" -> {
                onImage(args!![0])
                task
            }
            "close" -> {
                closeCount.incrementAndGet()
                null
            }
            "toString" -> "PendingTextRecognizer"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> null
        }
    } as TextRecognizer

    private fun imageBitmap(image: Any): Bitmap = checkNotNull(
        image.javaClass.getMethod("getBitmapInternal").invoke(image) as Bitmap?,
    )

    private fun registerBytes(
        resolver: ContentResolver,
        uri: Uri,
        bytes: ByteArray,
        closeCount: AtomicInteger,
    ) {
        Shadows.shadowOf(resolver).registerInputStreamSupplier(uri) {
            object : ByteArrayInputStream(bytes) {
                override fun close() {
                    closeCount.incrementAndGet()
                    super.close()
                }
            }
        }
    }

    private fun encodedPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun awaitPhase(owner: ImportedImageOwner, phase: ImportedImagePhase) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (owner.state.value.phase == phase) return
            Thread.sleep(5)
        }
        assertEquals(phase, owner.state.value.phase)
    }

    private fun awaitWorkGate(owner: ImportedImageOwner) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (!owner.isImportWorkInFlightForTest()) return
            Thread.sleep(5)
        }
        assertFalse(owner.isImportWorkInFlightForTest())
    }

    private fun awaitImageCount(images: List<Any>, count: Int) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < deadline) {
            if (images.size >= count) return
            Thread.sleep(5)
        }
        assertTrue(images.size >= count)
    }
}
