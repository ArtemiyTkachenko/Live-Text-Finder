package com.artkachenko.livetextfinder

import android.graphics.Matrix
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.artkachenko.livetextfinder.camera.LiveSearchAreaLifecycle
import com.artkachenko.livetextfinder.ocr.FrameInfo
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.TextBounds
import com.artkachenko.livetextfinder.ocr.TextElement
import com.artkachenko.livetextfinder.ocr.TextLine
import com.artkachenko.livetextfinder.ocr.TextPoint
import com.artkachenko.livetextfinder.search.FrameIdentity
import com.artkachenko.livetextfinder.search.FrameProjection
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.PreviewTarget
import com.artkachenko.livetextfinder.search.filterOccurrencesToArea
import com.artkachenko.livetextfinder.search.searchRecognizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveSearchAreaPlatformTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null

    @After
    fun disposeComposition() {
        composeView?.disposeComposition()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun nativeMatrixProjectsAsymmetricCornersAcrossEveryFrameRotation() {
        val rawCorners = floatArrayOf(80f, 60f, 120f, 60f, 120f, 100f, 80f, 100f)
        val sensorToBuffer = Matrix().apply {
            setValues(floatArrayOf(2f, 0f, 10f, 0f, 3f, 20f, 0f, 0f, 1f))
        }
        val rawToView = Matrix().apply {
            setValues(floatArrayOf(1.7f, 0f, -80f, 0f, 1.7f, 35f, 0f, 0f, 1f))
        }
        val sensorToView = Matrix().apply {
            setConcat(rawToView, sensorToBuffer)
        }
        val expected = rawCorners.copyOf().also(rawToView::mapPoints)
        val left = expected.filterIndexed { index, _ -> index % 2 == 0 }.min()
        val right = expected.filterIndexed { index, _ -> index % 2 == 0 }.max()
        val top = expected.filterIndexed { index, _ -> index % 2 == 1 }.min()
        val bottom = expected.filterIndexed { index, _ -> index % 2 == 1 }.max()
        val containing = NormalizedSearchArea(
            (left - 1f) / 600f,
            (top - 1f) / 500f,
            (right + 1f) / 600f,
            (bottom + 1f) / 500f,
        )
        val crossing = containing.copy(right = (right - 1f) / 600f)

        for (rotation in listOf(0, 90, 180, 270)) {
            val rotate = Matrix().apply { setRotate(rotation.toFloat()) }
            val rotatedFrame = RectF(0f, 0f, 400f, 240f)
            rotate.mapRect(rotatedFrame)
            rotate.postTranslate(-rotatedFrame.left, -rotatedFrame.top)
            val uprightCorners = rawCorners.copyOf().also(rotate::mapPoints)
            val element = TextElement(
                "target",
                null,
                uprightCorners.toList().chunked(2).map { (x, y) ->
                    TextPoint(x.toInt(), y.toInt())
                },
            )
            val snapshot = OcrSnapshot(
                "target",
                listOf(TextLine("target", null, emptyList(), listOf(element))),
                FrameInfo(
                    1,
                    400,
                    240,
                    rotation,
                    TextBounds(20, 10, 380, 230),
                    sensorToBuffer.values(),
                ),
            )
            val projection = FrameProjection(
                snapshot,
                FrameIdentity(1, 1),
                PreviewTarget(600, 500, sensorToView.values()),
            )
            val search = searchRecognizedText(
                "target",
                OcrUiState(OcrStatus.TextRecognized, snapshot = snapshot),
            )
            assertEquals(
                1,
                assertNotNull(
                    filterOccurrencesToArea(search, projection, containing, 600, 500),
                ).occurrences.size,
                "rotation=$rotation",
            )
            assertTrue(
                assertNotNull(
                    filterOccurrencesToArea(search, projection, crossing, 600, 500),
                ).occurrences.isEmpty(),
                "rotation=$rotation",
            )
        }
    }

    @Test
    fun appScopedLifecycleResetsOnStopAndDisposalWithoutCameraRenderer() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        this.activity = activity
        val owner = LiveSearchAreaOwner()
        val composeView = ComposeView(activity)
        this.composeView = composeView
        composeView.setContent {
            LiveSearchAreaLifecycle(owner::reset)
        }
        activity.setContentView(composeView)
        composeView.layoutParams = FrameLayout.LayoutParams(400, 400)
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, 400, 400)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(owner.apply(owner.revision, NormalizedSearchArea.CENTERED_DEFAULT))
        val oldDraft = owner.revision
        controller.pause().stop()
        assertNull(owner.area)
        assertTrue(owner.revision > oldDraft)
        assertFalse(owner.apply(oldDraft, NormalizedSearchArea.CENTERED_DEFAULT))

        controller.start().resume()
        assertTrue(owner.apply(owner.revision, NormalizedSearchArea.CENTERED_DEFAULT))
        val beforeDisposal = owner.revision
        composeView.disposeComposition()
        assertNull(owner.area)
        assertTrue(owner.revision > beforeDisposal)
    }

    private fun Matrix.values(): List<Float> {
        val values = FloatArray(9)
        getValues(values)
        return values.toList()
    }
}
