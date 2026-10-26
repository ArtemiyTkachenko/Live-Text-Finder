package com.artkachenko.livetextfinder.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real CameraX JPEG conversion, without claiming native YUV coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FrameBitmapConversionTest {
    @Test
    fun cameraXConversionPreservesFullRawBufferPixelsBeforeCropAndRotation() {
        val source = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val pixels = IntArray(80 * 48) { index ->
            val x = index % 80
            val y = index / 80
            colors[(if (y < 24) 0 else 2) + if (x < 40) 0 else 1]
        }
        source.setPixels(pixels, 0, 80, 0, 0, 80, 48)
        val encoded = ByteArrayOutputStream()
        assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 100, encoded))
        val proxy = JpegImageProxy(encoded.toByteArray())

        val copied = proxy.toBitmap()

        assertEquals(90, proxy.imageInfo.rotationDegrees)
        assertEquals(Rect(7, 5, 61, 37), proxy.cropRect)
        assertEquals(80, copied.width)
        assertEquals(48, copied.height)
        assertColorNear(Color.RED, copied.getPixel(20, 12))
        assertColorNear(Color.GREEN, copied.getPixel(60, 12))
        assertColorNear(Color.BLUE, copied.getPixel(20, 36))
        assertColorNear(Color.YELLOW, copied.getPixel(60, 36))
        assertFalse(proxy.closed)
        proxy.close()
        source.recycle()
        copied.recycle()
    }

    private fun assertColorNear(expected: Int, actual: Int) {
        // JPEG compression is lossy; landmarks lie well inside each asymmetric color region.
        assertTrue(abs(Color.red(expected) - Color.red(actual)) <= 12)
        assertTrue(abs(Color.green(expected) - Color.green(actual)) <= 12)
        assertTrue(abs(Color.blue(expected) - Color.blue(actual)) <= 12)
    }

    private class JpegImageProxy(bytes: ByteArray) : ImageProxy {
        var closed = false
            private set
        private var crop = Rect(7, 5, 61, 37)
        private val data = ByteBuffer.wrap(bytes)
        private val info = Proxy.newProxyInstance(
            ImageInfo::class.java.classLoader,
            arrayOf(ImageInfo::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getTimestamp" -> 42L
                "getRotationDegrees" -> 90
                "getSensorToBufferTransformMatrix" -> Matrix()
                else -> null
            }
        } as ImageInfo

        override fun close() {
            closed = true
        }

        override fun getCropRect(): Rect = crop

        override fun setCropRect(rect: Rect?) {
            crop = rect ?: Rect(0, 0, width, height)
        }

        override fun getFormat(): Int = ImageFormat.JPEG

        override fun getHeight(): Int = 48

        override fun getWidth(): Int = 80

        override fun getImageInfo(): ImageInfo = info

        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun getImage(): Image? = null

        override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(
            object : ImageProxy.PlaneProxy {
                override fun getRowStride(): Int = data.capacity()

                override fun getPixelStride(): Int = 1

                override fun getBuffer(): ByteBuffer = data
            },
        )
    }
}
