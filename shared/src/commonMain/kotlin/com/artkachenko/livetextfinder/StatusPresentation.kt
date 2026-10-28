package com.artkachenko.livetextfinder

import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.ocr.ImportedTextSnapshot
import com.artkachenko.livetextfinder.ocr.OcrSnapshot
import com.artkachenko.livetextfinder.ocr.RecognizedTextContent

internal enum class StatusSource {
    ImportProgress,
    ImportedImage,
    FrozenFrame,
    FreezingFrame,
    LiveCamera,
}

internal enum class SearchFeedbackKind {
    EnterQuery,
    WaitingForFreshText,
    WaitingForContent,
    WaitingForAreaMapping,
    NoReadableText,
    NoMatches,
    NoMatchesInArea,
    Matches,
    WaitingForHighlights,
}

internal data class SearchFeedback(
    val kind: SearchFeedbackKind,
    val matchCount: Int = 0,
    val retainedOutlines: Boolean = false,
)

internal data class RecognitionDetails(
    val source: StatusSource,
    val recognizedLineCount: Int?,
    val outlinedElementCount: Int?,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val rotationDegrees: Int?,
    val textSample: String?,
    val textUnavailable: Boolean,
    val retainedOutlines: Boolean,
    val sourceMessage: String?,
)

internal fun recognitionDetails(
    source: StatusSource,
    content: RecognizedTextContent?,
    outlinedElementCount: Int?,
    retainedOutlines: Boolean,
    sourceMessage: String? = null,
): RecognitionDetails {
    val imported = content as? ImportedTextSnapshot
    val camera = content as? OcrSnapshot
    return RecognitionDetails(
        source = source,
        recognizedLineCount = content?.lines?.size,
        outlinedElementCount = outlinedElementCount,
        imageWidth = imported?.width ?: camera?.frame?.uprightWidth,
        imageHeight = imported?.height ?: camera?.frame?.uprightHeight,
        rotationDegrees = camera?.frame?.rotationDegrees,
        textSample = content?.text?.takeIf(String::isNotBlank)?.take(240),
        textUnavailable = content == null || content.text.isBlank(),
        retainedOutlines = retainedOutlines,
        sourceMessage = sourceMessage,
    )
}

internal fun resolveStatusSource(
    importPhase: ImportedImagePhase,
    freezeMode: FreezeMode,
): StatusSource = when {
    importPhase == ImportedImagePhase.Picking ||
        importPhase == ImportedImagePhase.Reading ||
        importPhase == ImportedImagePhase.Recognizing -> StatusSource.ImportProgress
    importPhase == ImportedImagePhase.Imported -> StatusSource.ImportedImage
    freezeMode == FreezeMode.Frozen -> StatusSource.FrozenFrame
    freezeMode == FreezeMode.Freezing -> StatusSource.FreezingFrame
    else -> StatusSource.LiveCamera
}

internal fun searchFeedback(
    query: String,
    content: RecognizedTextContent?,
    matchCount: Int,
    hasLiveSnapshot: Boolean = true,
    areaSelected: Boolean = false,
    areaReady: Boolean = true,
    highlightsReady: Boolean = true,
    retainedOutlines: Boolean = false,
): SearchFeedback {
    if (query.isEmpty()) {
        return SearchFeedback(SearchFeedbackKind.EnterQuery)
    }
    if (!hasLiveSnapshot) {
        return SearchFeedback(SearchFeedbackKind.WaitingForFreshText)
    }
    if (content == null) {
        return SearchFeedback(SearchFeedbackKind.WaitingForContent)
    }
    if (areaSelected && !areaReady) {
        return SearchFeedback(SearchFeedbackKind.WaitingForAreaMapping)
    }
    if (content.text.isBlank() || content.lines.isEmpty()) {
        return SearchFeedback(
            SearchFeedbackKind.NoReadableText,
            retainedOutlines = retainedOutlines,
        )
    }
    if (matchCount == 0) {
        return SearchFeedback(
            if (areaSelected) {
                SearchFeedbackKind.NoMatchesInArea
            } else {
                SearchFeedbackKind.NoMatches
            },
            retainedOutlines = retainedOutlines,
        )
    }
    if (!highlightsReady) {
        return SearchFeedback(
            SearchFeedbackKind.WaitingForHighlights,
            matchCount = matchCount,
        )
    }
    return SearchFeedback(
        SearchFeedbackKind.Matches,
        matchCount = matchCount,
        retainedOutlines = retainedOutlines,
    )
}
