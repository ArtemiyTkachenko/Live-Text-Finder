package com.artkachenko.livetextfinder.ocr

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer

/** Runs the bundled recognizer against the exact normalized imported bitmap. */
internal class ImportedTextRecognizer(
    private val recognizer: TextRecognizer,
) {
    fun recognize(bitmap: Bitmap, sourceIdentity: Long): Task<ImportedTextSnapshot> {
        require(!bitmap.isRecycled)
        val image = InputImage.fromBitmap(bitmap, 0)
        return recognizer.process(image).continueWith { task ->
            task.result.toImportedSnapshot(bitmap.width, bitmap.height, sourceIdentity)
        }
    }
}

internal fun Text.toImportedSnapshot(
    width: Int,
    height: Int,
    sourceIdentity: Long,
) = ImportedTextSnapshot(
    text = text,
    lines = textBlocks.flatMap { block ->
        block.lines.map { line ->
            TextLine(
                text = line.text,
                bounds = line.boundingBox?.toBounds(),
                corners = line.cornerPoints.orEmpty().map { point -> point.toTextPoint() },
                elements = line.elements.map { element ->
                    TextElement(
                        text = element.text,
                        bounds = element.boundingBox?.toBounds(),
                        corners = element.cornerPoints.orEmpty().map { point ->
                            point.toTextPoint()
                        },
                    )
                },
            )
        }
    },
    width = width,
    height = height,
    sourceIdentity = sourceIdentity,
)

private fun Rect.toBounds() = TextBounds(left, top, right, bottom)

private fun Point.toTextPoint() = TextPoint(x, y)
