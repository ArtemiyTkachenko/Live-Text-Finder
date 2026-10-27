package com.artkachenko.livetextfinder.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.artkachenko.livetextfinder.ocr.ImportedTextRecognizer
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

internal data class ProcessedImportedImage(
    val bitmap: Bitmap,
    val snapshot: ImportedTextSnapshot,
)

/** Boundary for one stream decode followed by OCR of the adopted normalized bitmap. */
internal fun interface ImportedImageProcessor {
    fun process(
        resolver: ContentResolver,
        uri: Uri,
        sourceIdentity: Long,
        onRecognizing: () -> Unit,
    ): ProcessedImportedImage
}

internal class AndroidImportedImageProcessor(
    private val recognizerFactory: () -> TextRecognizer = {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    },
) : ImportedImageProcessor {
    override fun process(
        resolver: ContentResolver,
        uri: Uri,
        sourceIdentity: Long,
        onRecognizing: () -> Unit,
    ): ProcessedImportedImage {
        val bitmap = resolver.openInputStream(uri)?.let(ImportedImageDecoder()::decode)
            ?: throw IllegalArgumentException("The selected image could not be opened.")
        var recognizer: TextRecognizer? = null
        try {
            onRecognizing()
            recognizer = recognizerFactory()
            val snapshot = Tasks.await(
                ImportedTextRecognizer(checkNotNull(recognizer)).recognize(
                    bitmap,
                    sourceIdentity,
                ),
            )
            return ProcessedImportedImage(bitmap, snapshot)
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        } finally {
            runCatching { recognizer?.close() }
        }
    }
}
