package com.artkachenko.livetextfinder

import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.search.SearchPresentation
import com.artkachenko.livetextfinder.search.SearchPresentationOwner
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.search.normalizeRecognizedText

internal fun hasAvailableSearchResults(
    search: TextSearchResult,
    currentIdentity: String,
    importedState: ImportedImageState,
    freezeState: FreezePresentationState,
    ocrState: OcrUiState,
    presentation: SearchPresentation,
    areaSelected: Boolean,
    currentAreaRevision: Long,
    shouldShowViews: Boolean,
    nowMillis: Long,
): Boolean {
    if (search.spec.identity != currentIdentity || search.spec.terms.none {
            normalizeRecognizedText(it.text).isNotEmpty()
        }
    ) {
        return false
    }
    if (importedState.phase == ImportedImagePhase.Picking ||
        importedState.phase == ImportedImagePhase.Reading ||
        importedState.phase == ImportedImagePhase.Recognizing
    ) {
        return false
    }
    val imported = importedState.phase == ImportedImagePhase.Imported
    if (imported) {
        val snapshot = importedState.snapshot ?: return false
        return search.content === snapshot
    }
    if (freezeState.mode == FreezeMode.Freezing) return false
    if (freezeState.mode == FreezeMode.Frozen) {
        val snapshot = freezeState.frame?.snapshot ?: return false
        return search.content === snapshot
    }
    if (!shouldShowViews || ocrState.status != OcrStatus.Scanning &&
        ocrState.status != OcrStatus.TextRecognized
    ) {
        return false
    }
    val snapshot = ocrState.snapshot ?: return false
    val receipt = ocrState.receivedAtMillis ?: return false
    if (search.content !== snapshot || !isFreshLiveReceipt(receipt, nowMillis)) return false
    return !areaSelected || (
        presentation.areaRevision == currentAreaRevision && presentation.areaReady
        )
}

internal fun isFreshLiveReceipt(receiptMillis: Long, nowMillis: Long): Boolean =
    nowMillis >= receiptMillis &&
        nowMillis - receiptMillis < SearchPresentationOwner.RESULT_AGE_MILLIS

internal enum class UnavailableResultsGuidance {
    Importing,
    Freezing,
    MissingImportedSource,
    MissingFrozenSource,
    Permission,
    Starting,
    Paused,
    Error,
    Unavailable,
    HiddenPreview,
    AreaMapping,
    Waiting,
}

internal fun unavailableResultsGuidance(
    resultsAvailable: Boolean,
    hasUsableTerms: Boolean,
    importedState: ImportedImageState,
    freezeState: FreezePresentationState,
    ocrState: OcrUiState,
    shouldShowViews: Boolean,
    areaSelected: Boolean,
    areaReady: Boolean,
): UnavailableResultsGuidance? {
    if (resultsAvailable || !hasUsableTerms) return null
    if (importedState.phase == ImportedImagePhase.Picking ||
        importedState.phase == ImportedImagePhase.Reading ||
        importedState.phase == ImportedImagePhase.Recognizing
    ) {
        return UnavailableResultsGuidance.Importing
    }
    if (importedState.phase == ImportedImagePhase.Imported) {
        return if (importedState.snapshot == null) {
            UnavailableResultsGuidance.MissingImportedSource
        } else {
            UnavailableResultsGuidance.Waiting
        }
    }
    if (freezeState.mode == FreezeMode.Freezing) {
        return UnavailableResultsGuidance.Freezing
    }
    if (freezeState.mode == FreezeMode.Frozen) {
        return if (freezeState.frame?.snapshot == null) {
            UnavailableResultsGuidance.MissingFrozenSource
        } else {
            UnavailableResultsGuidance.Waiting
        }
    }
    when (ocrState.status) {
        OcrStatus.PermissionRequired -> return UnavailableResultsGuidance.Permission
        OcrStatus.Error -> return UnavailableResultsGuidance.Error
        OcrStatus.Unavailable -> return UnavailableResultsGuidance.Unavailable
        OcrStatus.Starting, OcrStatus.Paused, OcrStatus.Scanning, OcrStatus.TextRecognized -> Unit
    }
    if (!shouldShowViews) return UnavailableResultsGuidance.HiddenPreview
    if (ocrState.status == OcrStatus.Starting) return UnavailableResultsGuidance.Starting
    if (ocrState.status == OcrStatus.Paused) return UnavailableResultsGuidance.Paused
    if (areaSelected && !areaReady) return UnavailableResultsGuidance.AreaMapping
    return UnavailableResultsGuidance.Waiting
}
