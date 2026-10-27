package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportedImageDecoderTest {
    @Test
    fun decodesSupportedReportedFormatsAndClosesSource() {
        listOf(
            Bitmap.CompressFormat.JPEG,
            Bitmap.CompressFormat.PNG,
            Bitmap.CompressFormat.WEBP_LOSSLESS,
        ).forEach { format ->
            val bytes = encodedBitmap(format)
            var closed = false
            val source = object : ByteArrayInputStream(bytes) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }

            val decoded = ImportedImageDecoder().decode(source)

            assertEquals(24, decoded.width)
            assertEquals(12, decoded.height)
            assertTrue(closed)
            decoded.recycle()
        }
    }

    @Test
    fun rejectsTruncatedUnsupportedAndOversizedEncodedData() {
        assertFailsWith<IllegalArgumentException> {
            ImportedImageDecoder().decode(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        }
        val png = encodedBitmap(Bitmap.CompressFormat.PNG)
        assertFailsWith<IllegalArgumentException> {
            ImportedImageDecoder().decode(ByteArrayInputStream(png.copyOf(png.size / 2)))
        }
        assertFailsWith<IllegalArgumentException> {
            ImportedImageDecoder().decode(ByteArrayInputStream(unsupportedGif()))
        }
        assertFailsWith<IllegalArgumentException> {
            ImportedImageDecoder(maxEncodedBytes = png.size - 1)
                .decode(ByteArrayInputStream(png))
        }
    }

    @Test
    fun samplesToThePixelLimitBeforeReturningPixels() {
        val source = Bitmap.createBitmap(2_400, 1_200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        val output = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.PNG, 100, output)
        source.recycle()

        val decoded = ImportedImageDecoder(maxPixels = 400_000)
            .decode(ByteArrayInputStream(output.toByteArray()))

        assertTrue(decoded.width.toLong() * decoded.height <= 400_000)
        decoded.recycle()
    }

    @Test
    fun allExifOrientationsTransformAsymmetricPixelsAndDimensions() {
        val jpeg = asymmetricJpeg()
        val sourcePoints = listOf(15 to 10, 45 to 10, 15 to 30, 45 to 30)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        (1..8).forEach { orientation ->
            val decoded = ImportedImageDecoder().decode(
                ByteArrayInputStream(withExifOrientation(jpeg, orientation)),
            )
            val expectedDimensions = if (orientation in 5..8) 40 to 60 else 60 to 40
            assertEquals(expectedDimensions.first, decoded.width)
            assertEquals(expectedDimensions.second, decoded.height)
            sourcePoints.zip(colors).forEach { (point, color) ->
                val transformed = orient(point.first, point.second, orientation)
                assertColorNear(color, decoded.getPixel(transformed.first, transformed.second))
            }
            decoded.recycle()
        }
    }

    @Test
    fun rejectsInvalidExifOrientationAndBoundsIrregularAndMaximumDimensions() {
        assertFailsWith<IllegalArgumentException> {
            ImportedImageDecoder().decode(
                ByteArrayInputStream(withExifOrientation(asymmetricJpeg(), 9)),
            )
        }
        val skinny = Bitmap.createBitmap(1, 10_001, Bitmap.Config.ARGB_8888)
        val skinnyBytes = ByteArrayOutputStream().also { output ->
            skinny.compress(Bitmap.CompressFormat.PNG, 100, output)
            skinny.recycle()
        }.toByteArray()
        val sampled = ImportedImageDecoder(maxPixels = 4_000)
            .decode(ByteArrayInputStream(skinnyBytes))
        assertTrue(sampled.width.toLong() * sampled.height <= 4_000)
        sampled.recycle()

        listOf(1 to 10_000_000, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (width, height) ->
            val sample = ImportedImageDecoder.safeSampleSize(width, height, 4_000_000)
            val sampledWidth = (width.toLong() + sample - 1) / sample
            val sampledHeight = (height.toLong() + sample - 1) / sample
            assertTrue(sampledWidth * sampledHeight <= 4_000_000)
            assertTrue(sample > 0 && sample and (sample - 1) == 0)
        }
    }

    private fun encodedBitmap(format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 12, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val output = ByteArrayOutputStream()
        assertTrue(bitmap.compress(format, 100, output))
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun unsupportedGif(): ByteArray = (
        "47494638396101000100800000000000ffffff" +
            "21f90401000000002c00000000010001000002024401003b"
        )
        .chunked(2)
        .map { chunk -> chunk.toInt(16).toByte() }
        .toByteArray()

    private fun asymmetricJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(60, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                bitmap.setPixel(
                    x,
                    y,
                    when {
                        x < 30 && y < 20 -> Color.RED
                        x >= 30 && y < 20 -> Color.GREEN
                        x < 30 -> Color.BLUE
                        else -> Color.YELLOW
                    },
                )
            }
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val payload = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0, 0,
            0x49, 0x49, 0x2A, 0, 8, 0, 0, 0,
            1, 0,
            0x12, 0x01, 3, 0, 1, 0, 0, 0,
            orientation.toByte(), 0, 0, 0,
            0, 0, 0, 0,
        )
        val segmentLength = payload.size + 2
        return byteArrayOf(
            jpeg[0], jpeg[1], 0xFF.toByte(), 0xE1.toByte(),
            (segmentLength shr 8).toByte(), segmentLength.toByte(),
        ) + payload + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun orient(x: Int, y: Int, orientation: Int): Pair<Int, Int> = when (orientation) {
        1 -> x to y
        2 -> 59 - x to y
        3 -> 59 - x to 39 - y
        4 -> x to 39 - y
        5 -> y to x
        6 -> 39 - y to x
        7 -> 39 - y to 59 - x
        else -> y to 59 - x
    }

    private fun assertColorNear(expected: Int, actual: Int) {
        val expectedChannels = listOf(
            Color.red(expected),
            Color.green(expected),
            Color.blue(expected),
        )
        val actualChannels = listOf(Color.red(actual), Color.green(actual), Color.blue(actual))
        expectedChannels.zip(actualChannels).forEach { (expectedChannel, actualChannel) ->
            assertTrue(kotlin.math.abs(expectedChannel - actualChannel) < 100)
        }
    }
}
