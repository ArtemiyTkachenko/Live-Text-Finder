package com.artkachenko.livetextfinder.camera

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.UUID

internal class ShareQuotaExceededException : IllegalStateException(
    "Temporary share storage is full. Try again after older shares expire.",
)

internal class ShareOutput internal constructor(
    private val partial: File,
    private val destination: File,
) {
    fun encode(bitmap: Bitmap) {
        FileOutputStream(partial).use { stream ->
            val encoded = bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                EncodedSizeLimitOutputStream(stream, MAX_ENCODED_BYTES),
            )
            if (!encoded) {
                throw IllegalStateException("PNG encoding failed.")
            }
        }
    }

    fun publish(): File {
        if (!partial.renameTo(destination)) {
            throw IllegalStateException("Share image could not be published.")
        }
        destination.setLastModified(System.currentTimeMillis())
        return destination
    }

    fun delete() {
        partial.delete()
        destination.delete()
    }

    private companion object {
        const val MAX_ENCODED_BYTES = 20L * 1024 * 1024
    }
}

internal class EncodedSizeLimitOutputStream(
    output: OutputStream,
    private val maxBytes: Long,
) : FilterOutputStream(output) {
    private var count = 0L

    override fun write(value: Int) {
        if (++count > maxBytes) {
            throw IllegalStateException("PNG exceeds the size limit.")
        }
        out.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        count += length
        if (count > maxBytes) {
            throw IllegalStateException("PNG exceeds the size limit.")
        }
        out.write(buffer, offset, length)
    }
}

internal object ShareCache {
    private const val TTL_MILLIS = 24L * 60 * 60 * 1000
    private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
    private const val RESERVATION_BYTES = 20L * 1024 * 1024
    private const val MAX_FILES = 16

    @Synchronized
    fun reserve(cacheDir: File): ShareOutput {
        val directory = File(cacheDir, "shared-images")
        check(directory.exists() || directory.mkdirs()) {
            "Temporary share storage is unavailable."
        }
        cleanupDirectory(directory)
        val files = directory.listFiles().orEmpty().filter { file -> file.isFile }
        if (
            files.size < MAX_FILES &&
            files.sumOf(File::length) + RESERVATION_BYTES <= MAX_TOTAL_BYTES
        ) {
            val id = UUID.randomUUID().toString()
            return ShareOutput(File(directory, "$id.partial"), File(directory, "$id.png"))
        }
        throw ShareQuotaExceededException()
    }

    @Synchronized
    fun cleanup(cacheDir: File) {
        val directory = File(cacheDir, "shared-images")
        if (directory.exists()) {
            cleanupDirectory(directory)
        }
    }

    private fun cleanupDirectory(directory: File) {
        val now = System.currentTimeMillis()
        directory.listFiles().orEmpty().forEach { file ->
            if (file.extension == "partial" || now - file.lastModified() >= TTL_MILLIS) {
                file.delete()
            }
        }
    }
}
