package com.artkachenko.livetextfinder.ocr

import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.media.SyntheticMediaImage
import android.os.Looper
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.FrameProjection
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.PreviewTarget
import com.artkachenko.livetextfinder.search.projectHighlights
import com.artkachenko.livetextfinder.search.searchRecognizedText
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.internal.ImageUtils
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Execute pinned SDK geometry construction + real production adaptation, not OCR native inference. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class MlKitGeometryAdapterTest {
    private val identity = listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val sensor = listOf(.5f, 0f, -50f, 0f, .5f, -25f, 0f, 0f, 1f)
    private val horizontal = listOf(Point(80, 40), Point(280, 40), Point(280, 60), Point(80, 60))
    private val vertical = listOf(Point(220, 70), Point(240, 70), Point(240, 170), Point(220, 170))
    private val uprightHorizontal = listOf(
        listOf(TextPoint(80, 40), TextPoint(280, 40), TextPoint(280, 60), TextPoint(80, 60)),
        listOf(TextPoint(160, 80), TextPoint(160, 280), TextPoint(140, 280), TextPoint(140, 80)),
        listOf(TextPoint(320, 160), TextPoint(120, 160), TextPoint(120, 140), TextPoint(320, 140)),
        listOf(TextPoint(40, 320), TextPoint(40, 120), TextPoint(60, 120), TextPoint(60, 320)),
    )
    private val uprightVertical = listOf(
        listOf(TextPoint(220, 70), TextPoint(240, 70), TextPoint(240, 170), TextPoint(220, 170)),
        listOf(TextPoint(130, 220), TextPoint(130, 240), TextPoint(30, 240), TextPoint(30, 220)),
        listOf(TextPoint(180, 130), TextPoint(160, 130), TextPoint(160, 30), TextPoint(180, 30)),
        listOf(TextPoint(70, 180), TextPoint(70, 160), TextPoint(170, 160), TextPoint(170, 180)),
    )
    private val landscape = PreviewTarget(300, 240, listOf(.75f, 0f, -225f, 0f, .75f, -67.5f, 0f, 0f, 1f))
    private val portrait = PreviewTarget(120, 300, listOf(0f, -.46875f, 177.1875f, .46875f, 0f, -84.375f, 0f, 0f, 1f))

    @Test fun pinnedBundledGeometryHelperRotatesBoundsButPublicResultKeepsRawCorners() {
        val text = sdkResult(90)
        val element = text.textBlocks.single().lines.single().elements.first()
        assertEquals(Rect(140, 80, 160, 280), element.boundingBox)
        assertEquals(horizontal, element.cornerPoints!!.toList())
        // Non-null identity passed by ORIGINAL mode does not normalize those corners.
        val normalizedBounds = Rect(140, 80, 160, 280)
        assertEquals(normalizedBounds, bundledBounds(horizontal, 90))
    }

    @Test fun productionSnapshotNormalizesBothLineAndElementCornersAtEveryQuarterRotation() {
        listOf(0, 90, 180, 270).forEachIndexed { index, rotation ->
            val snapshot = sdkResult(rotation).toSnapshot(frame(rotation))
            assertEquals(uprightHorizontal[index], snapshot.lines.single().corners)
            assertEquals(uprightHorizontal[index], snapshot.lines.single().elements[0].corners)
            assertEquals(uprightVertical[index], snapshot.lines.single().elements[1].corners)
            val expectedBounds = listOf(TextBounds(80, 40, 280, 60), TextBounds(140, 80, 160, 280),
                TextBounds(120, 140, 320, 160), TextBounds(40, 120, 60, 320))
            assertEquals(expectedBounds[index], snapshot.lines.single().elements[0].bounds)
            assertEquals(2, searchRecognizedText("target", OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot)).occurrences.size)
        }
    }

    @Test fun sdkThroughProductionAdapterProjectsHorizontalAndVerticalAtCorrectLandscapePositions() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val outlines = projected(sdkResult(rotation).toSnapshot(frame(rotation)), landscape)
            assertPoints(listOf(-30f to 30f, 270f to 30f, 270f to 60f, -30f to 60f), outlines[0])
            assertPoints(listOf(180f to 75f, 210f to 75f, 210f to 225f, 180f to 225f), outlines[1])
        }
    }

    @Test fun sdkThroughProductionAdapterUsesActualPortraitRotationCropAndTranslation() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val outlines = projected(sdkResult(rotation).toSnapshot(frame(rotation)), portrait)
            assertPoints(listOf(116.25f to 37.5f, 116.25f to 225f, 97.5f to 225f, 97.5f to 37.5f), outlines[0])
            assertPoints(listOf(88.125f to 168.75f, 88.125f to 187.5f, -5.625f to 187.5f, -5.625f to 168.75f), outlines[1])
        }
    }

    @Test fun sdkBoundsFallbackAndNormalizedCornersHaveSamePositionAndDimensions() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val corners = projected(sdkResult(rotation).toSnapshot(frame(rotation)), landscape)
            val fallback = projected(sdkResult(rotation, includeCorners = false).toSnapshot(frame(rotation)), landscape)
            corners.zip(fallback).forEach { (polygon, rectangle) ->
                assertEquals(envelope(polygon), envelope(rectangle))
            }
            assertEquals(listOf(-30f, 30f, 270f, 60f), envelope(fallback[0]))
            assertEquals(listOf(180f, 75f, 210f, 225f), envelope(fallback[1]))
        }
    }

    @Test fun actualProductionAnalyzerUsesOriginalIdentityOverloadCopiesMetadataAndClosesProxy() {
        val image = SyntheticMediaImage()
        var closes = 0
        val imageInfo = proxy<ImageInfo> { name, _ -> when (name) {
            "getTimestamp" -> 42L
            "getRotationDegrees" -> 90
            "getSensorToBufferTransformMatrix" -> Matrix().apply { setValues(sensor.toFloatArray()) }
            else -> null
        } }
        val proxy = proxy<ImageProxy> { name, _ -> when (name) {
            "getImage" -> image
            "getWidth" -> 400
            "getHeight" -> 200
            "getImageInfo" -> imageInfo
            "getCropRect" -> Rect(40, 20, 360, 180)
            "close" -> { closes++; null }
            else -> null
        } }
        var calls = 0
        var released = 0
        val recognizer = proxy<TextRecognizer> { name, args -> when (name) {
            "getDetectorType" -> Detector.TYPE_TEXT_RECOGNITION
            "process" -> {
                calls++
                assertEquals(3, args!!.size)
                assertTrue(args[0] === image)
                assertEquals(90, args[1])
                assertTrue((args[2] as Matrix).isIdentity)
                Tasks.forResult(sdkResult(90))
            }
            "close" -> { released++; null }
            else -> null
        } }
        var delivered: OcrUiState? = null
        val ticket = FrameIdentity(7, 3)
        val analyzer = MlKitTextAnalyzer(Executor { it.run() }, { ticket }, recognizer) { state, identity ->
            assertEquals(ticket, identity)
            delivered = state
        }
        analyzer.analyze(proxy)
        shadowOf(Looper.getMainLooper()).idle()
        val snapshot = assertNotNull(delivered?.snapshot)
        assertEquals(1, calls)
        assertEquals(1, closes)
        assertEquals(42L, snapshot.frame.timestampNanos)
        assertEquals(TextBounds(40, 20, 360, 180), snapshot.frame.crop)
        assertEquals(sensor, snapshot.frame.sensorToBufferMatrix)
        assertEquals(uprightHorizontal[1], snapshot.lines[0].elements[0].corners)
        analyzer.close()
        assertEquals(1, released)
    }

    // This calls the pinned bundled bridge's real bounds adapter. Native inference is not executed.
    private fun bundledBounds(points: List<Point>, rotation: Int): Rect {
        val matrix = ImageUtils.getInstance().getUprightRotationMatrix(400, 200, rotation / 90)
        val helper = Class.forName("com.google.mlkit.vision.text.pipeline.zbf")
            .getDeclaredMethod("zba", List::class.java, Matrix::class.java).apply { isAccessible = true }
        return helper.invoke(null, points, matrix) as Rect
    }
    private fun sdkResult(rotation: Int, includeCorners: Boolean = true): Text {
        val caller = Matrix() // Actual CameraX ORIGINAL mode argument, not the public InputImage null route.
        fun element(points: List<Point>) = Text.Element("target", bundledBounds(points, rotation),
            if (includeCorners) points else emptyList<Point>(), "en", caller, 1f, 0f, emptyList<Text.Symbol>())
        val bounds = bundledBounds(horizontal, rotation)
        val line = Text.Line("target target", bounds, if (includeCorners) horizontal else emptyList<Point>(), "en", caller,
            listOf(element(horizontal), element(vertical)), 1f, 0f)
        val block = Text.TextBlock("target target", bounds, horizontal, "en", caller, listOf(line))
        return Text("target target", listOf(block))
    }
    private fun frame(rotation: Int) = FrameInfo(42, 400, 200, rotation, TextBounds(40, 20, 360, 180), sensor)
    private fun projected(snapshot: OcrSnapshot, target: PreviewTarget): List<List<PreviewPoint>> {
        val search = searchRecognizedText("target", OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot))
        return projectHighlights(search, FrameProjection(snapshot, FrameIdentity(1, 1), target), target.width, target.height).outlines.map { it.points }
    }
    private fun assertPoints(expected: List<Pair<Float, Float>>, actual: List<PreviewPoint>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedPoint, actualPoint) ->
            assertEquals(expectedPoint.first, actualPoint.x, .001f)
            assertEquals(expectedPoint.second, actualPoint.y, .001f)
        }
    }
    private fun envelope(points: List<PreviewPoint>) = listOf(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.get(0)
                "toString" -> "Synthetic ${T::class.java.simpleName}"
                else -> call(method.name, args)
            }
        } as T
}
