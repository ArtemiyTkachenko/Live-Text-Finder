package com.artkachenko.livetextfinder.ocr

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ImportedTextRecognizerTest {
    @Test
    fun productionAdapterUsesExactBitmapAtZeroRotationAndCopiesSdkGeometry() {
        MlKit.initialize(RuntimeEnvironment.getApplication())
        val bitmap = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        val corners = listOf(Point(20, 30), Point(90, 30), Point(90, 50), Point(20, 50))
        var observedImage: Any? = null
        val recognizer = proxy<TextRecognizer> { name, args ->
            when (name) {
                "getDetectorType" -> Detector.TYPE_TEXT_RECOGNITION
                "process" -> {
                    observedImage = args!![0]
                    Tasks.forResult(sdkResult(corners))
                }
                else -> null
            }
        }

        val task = ImportedTextRecognizer(recognizer).recognize(bitmap, 83L)
        val result = awaitTask(task)

        val image = checkNotNull(observedImage)
        val imageClass = image.javaClass
        assertSame(bitmap, imageClass.getMethod("getBitmapInternal").invoke(image))
        assertEquals(0, imageClass.getMethod("getRotationDegrees").invoke(image))
        assertEquals(83L, result.sourceIdentity)
        assertEquals(240, result.width)
        assertEquals(120, result.height)
        assertEquals(corners.map { TextPoint(it.x, it.y) }, result.lines.single().corners)
        assertEquals(
            corners.map { TextPoint(it.x, it.y) },
            result.lines.single().elements.single().corners,
        )
        assertEquals(TextBounds(20, 30, 90, 50), result.lines.single().elements.single().bounds)
        bitmap.recycle()
    }

    @Test
    fun emptySdkResultRemainsAnEmptyImportedSnapshot() {
        val snapshot = sdkResult(emptyList()).toImportedSnapshot(10, 20, 9L)

        assertEquals("", snapshot.text)
        assertEquals(emptyList(), snapshot.lines)
        assertEquals(9L, snapshot.sourceIdentity)
    }

    private fun sdkResult(corners: List<Point>): Text {
        if (corners.isEmpty()) {
            return Text("", emptyList<Text.TextBlock>())
        }
        val bounds = Rect(20, 30, 90, 50)
        val element = Text.Element(
            "target", bounds, corners, "en", null, 1f, 0f, emptyList<Text.Symbol>(),
        )
        val line = Text.Line("target", bounds, corners, "en", null, listOf(element), 1f, 0f)
        val block = Text.TextBlock("target", bounds, corners, "en", null, listOf(line))
        return Text("target", listOf(block))
    }

    private fun awaitTask(task: Task<ImportedTextSnapshot>): ImportedTextSnapshot {
        val latch = CountDownLatch(1)
        var result: ImportedTextSnapshot? = null
        var failure: Exception? = null
        task.addOnCompleteListener(Executor { command -> command.run() }) { completed ->
            if (completed.isSuccessful) {
                result = completed.result
            } else {
                failure = completed.exception
            }
            latch.countDown()
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(latch.await(3, TimeUnit.SECONDS))
        failure?.let { throw it }
        return checkNotNull(result)
    }

    private inline fun <reified T> proxy(
        crossinline call: (String, Array<out Any?>?) -> Any?,
    ): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { instance, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === args?.get(0)
            "toString" -> "Synthetic ${T::class.java.simpleName}"
            else -> call(method.name, args)
        }
    } as T
}
