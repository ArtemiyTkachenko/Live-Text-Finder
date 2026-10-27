package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Decodes one bounded image and applies its EXIF orientation before OCR. */
internal class ImportedImageDecoder(
    private val maxEncodedBytes: Int = MAX_ENCODED_BYTES,
    private val maxPixels: Long = MAX_DECODED_PIXELS,
) {
    init {
        require(maxEncodedBytes > 0)
        require(maxPixels > 0)
        require(maxPixels <= Long.MAX_VALUE / BYTES_PER_PIXEL)
    }

    fun decode(input: InputStream): Bitmap {
        val bytes = input.use(::readBounded)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "The selected image is unreadable."
        }
        require(bounds.outMimeType in SUPPORTED_MIME_TYPES) {
            "Choose a JPEG, PNG, or WebP image."
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = safeSampleSize(bounds.outWidth, bounds.outHeight, maxPixels)
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalArgumentException("The selected image could not be decoded.")
        if (decoded.width <= 0 || decoded.height <= 0 ||
            decoded.width.toLong() * decoded.height > maxPixels ||
            decoded.allocationByteCount.toLong() > maxPixels * BYTES_PER_PIXEL ||
            decoded.config != Bitmap.Config.ARGB_8888
        ) {
            decoded.recycle()
            throw IllegalArgumentException("The selected image is too large to process safely.")
        }

        return try {
            val orientation = try {
                ByteArrayInputStream(bytes).use { exifStream ->
                    ExifInterface(exifStream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            } catch (error: Exception) {
                throw IllegalArgumentException(
                    "The selected image has unreadable orientation data.",
                    error,
                )
            }
            val matrix = orientationMatrix(orientation) ?: return decoded
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { upright -> if (upright !== decoded) decoded.recycle() }
        } catch (error: Throwable) {
            decoded.recycle()
            if (error is OutOfMemoryError || error is IllegalArgumentException) throw error
            throw IllegalArgumentException("The selected image could not be oriented.", error)
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxEncodedBytes, 64 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) {
                break
            }
            require(output.size() <= maxEncodedBytes - count) {
                "The selected image exceeds the encoded size limit."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun orientationMatrix(orientation: Int): Matrix? = when (orientation) {
        ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> null
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
            setRotate(90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
            setRotate(-90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
        else -> throw IllegalArgumentException("The selected image has invalid orientation data.")
    }

    internal companion object {
        const val MAX_ENCODED_BYTES = 16 * 1024 * 1024
        const val MAX_DECODED_PIXELS = 4_000_000L
        const val BYTES_PER_PIXEL = 4L
        val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

        fun safeSampleSize(width: Int, height: Int, pixelLimit: Long): Int {
            require(width > 0 && height > 0 && pixelLimit > 0)
            var sample = 1
            while (sample < Int.MAX_VALUE / 2) {
                val sampledWidth = (width.toLong() + sample - 1) / sample
                val sampledHeight = (height.toLong() + sample - 1) / sample
                if (sampledWidth * sampledHeight <= pixelLimit) {
                    return sample
                }
                sample *= 2
            }
            throw IllegalArgumentException(
                "The selected image dimensions cannot be sampled safely.",
            )
        }
    }
}
