package com.artkachenko.livetextfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.artkachenko.livetextfinder.camera.FreezePresentationState
import com.artkachenko.livetextfinder.camera.ImportedImagePhase
import com.artkachenko.livetextfinder.camera.ImportedImageState
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ocr.RecognizedTextContent
import com.artkachenko.livetextfinder.search.Highlights
import com.artkachenko.livetextfinder.search.SearchPresentation
import com.artkachenko.livetextfinder.search.SearchSessionState
import com.artkachenko.livetextfinder.search.TextSearchResult
import com.artkachenko.livetextfinder.ui.ContextMessage
import com.artkachenko.livetextfinder.ui.StatusDetailsDialog
import com.artkachenko.livetextfinder.ui.StatusSummary
import com.artkachenko.livetextfinder.ui.sourceStatusMessage
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.status_camera_error_recovery
import livetextfinder.shared.generated.resources.status_details_title
import livetextfinder.shared.generated.resources.status_freezing
import livetextfinder.shared.generated.resources.status_import_choosing
import livetextfinder.shared.generated.resources.status_import_error
import livetextfinder.shared.generated.resources.status_import_reading
import livetextfinder.shared.generated.resources.status_import_recognizing
import livetextfinder.shared.generated.resources.status_loading_searches
import livetextfinder.shared.generated.resources.status_paused
import livetextfinder.shared.generated.resources.status_permission
import livetextfinder.shared.generated.resources.status_recovery
import livetextfinder.shared.generated.resources.status_reset_explanation
import livetextfinder.shared.generated.resources.status_reset_saved_data
import livetextfinder.shared.generated.resources.status_retry
import livetextfinder.shared.generated.resources.status_saving
import livetextfinder.shared.generated.resources.status_starting
import livetextfinder.shared.generated.resources.status_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FinderStatusRegion(
    state: OcrUiState,
    importedState: ImportedImageState,
    freezeState: FreezePresentationState,
    sessionState: SearchSessionState,
    search: TextSearchResult,
    presentation: SearchPresentation,
    highlights: Highlights,
    areaSelected: Boolean,
    shouldShowViews: Boolean,
    onRetrySavedData: () -> Unit,
    onRequestReset: () -> Unit,
) {
    var detailsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(shouldShowViews) {
        if (!shouldShowViews) {
            detailsOpen = false
        }
    }

    val source = resolveStatusSource(importedState.phase, freezeState.mode)
    val selectedContent = when (source) {
        StatusSource.ImportedImage -> importedState.snapshot
        StatusSource.FrozenFrame -> freezeState.frame?.snapshot
        StatusSource.ImportProgress,
        StatusSource.FreezingFrame -> null
        StatusSource.LiveCamera -> if (
            (state.status == OcrStatus.Scanning || state.status == OcrStatus.TextRecognized) &&
            search.snapshot === state.snapshot
        ) {
            state.snapshot
        } else {
            null
        }
    }
    val selectedDetails = when (source) {
        StatusSource.ImportedImage,
        StatusSource.FrozenFrame,
        StatusSource.LiveCamera -> {
            val outlinedCount = when (source) {
                StatusSource.ImportedImage,
                StatusSource.FrozenFrame -> highlights.outlines.size
                else -> presentation.outlinedElements.size
            }
            val retainedOutlines = source == StatusSource.LiveCamera &&
                presentation.outlinedElements.isNotEmpty() &&
                presentation.occurrences.any { it.firstMissingAtMillis != null }
            val sourceMessage = if (
                source == StatusSource.LiveCamera &&
                state.status != OcrStatus.Scanning &&
                state.status != OcrStatus.TextRecognized
            ) {
                state.message.takeIf(String::isNotBlank)
            } else {
                null
            }
            recognitionDetails(
                source = source,
                content = selectedContent,
                outlinedElementCount = outlinedCount.takeIf { selectedContent != null },
                retainedOutlines = retainedOutlines,
                sourceMessage = sourceMessage,
            )
        }
        StatusSource.ImportProgress,
        StatusSource.FreezingFrame -> null
    }
    val statusMessage = when (source) {
        StatusSource.ImportProgress -> when (importedState.phase) {
            ImportedImagePhase.Picking -> stringResource(Res.string.status_import_choosing)
            ImportedImagePhase.Reading -> stringResource(Res.string.status_import_reading)
            else -> stringResource(Res.string.status_import_recognizing)
        }
        StatusSource.ImportedImage,
        StatusSource.FrozenFrame -> sourceStatusMessage(
            source,
            statusFeedback(
                source,
                search,
                selectedContent,
                presentation,
                highlights,
                areaSelected,
            ),
        )
        StatusSource.FreezingFrame -> stringResource(Res.string.status_freezing)
        StatusSource.LiveCamera -> liveStatusMessage(
            state,
            search,
            selectedContent,
            presentation,
            highlights,
            areaSelected,
        )
    }
    val statusVisible = shouldShowViews || source == StatusSource.ImportProgress ||
        source == StatusSource.ImportedImage

    freezeState.error?.let { error ->
        ContextMessage(message = error, isError = true)
    }
    when {
        sessionState.recoveryRequired -> ContextMessage(
            message = listOfNotNull(
                sessionState.error ?: stringResource(Res.string.status_recovery),
                stringResource(Res.string.status_reset_explanation),
            ).joinToString("\n"),
            actionLabel = stringResource(Res.string.status_reset_saved_data),
            onAction = onRequestReset,
            isError = true,
        )
        sessionState.error != null -> ContextMessage(
            message = sessionState.error + if (sessionState.saving) {
                " · ${stringResource(Res.string.status_saving)}"
            } else {
                ""
            },
            actionLabel = stringResource(Res.string.status_retry),
            onAction = onRetrySavedData,
            isError = true,
        )
        !sessionState.loaded -> ContextMessage(
            stringResource(Res.string.status_loading_searches),
        )
        sessionState.saving -> ContextMessage(stringResource(Res.string.status_saving))
    }
    if (importedState.phase == ImportedImagePhase.Error) {
        ContextMessage(
            message = importedState.message ?: stringResource(Res.string.status_import_error),
            isError = true,
        )
    }
    if (statusVisible) {
        StatusSummary(
            message = statusMessage,
            details = selectedDetails.takeIf { shouldShowViews },
            onDetails = { detailsOpen = true },
            isError = source == StatusSource.LiveCamera && state.status == OcrStatus.Error,
        )
    }
    if (detailsOpen && shouldShowViews && selectedDetails != null) {
        StatusDetailsDialog(
            title = stringResource(Res.string.status_details_title),
            details = selectedDetails,
            onDismissRequest = { detailsOpen = false },
        )
    }
}

@Composable
private fun statusFeedback(
    source: StatusSource,
    search: TextSearchResult,
    content: RecognizedTextContent?,
    presentation: SearchPresentation,
    highlights: Highlights,
    areaSelected: Boolean,
): SearchFeedback = searchFeedback(
    query = search.query,
    content = content,
    matchCount = search.occurrences.size,
    hasLiveSnapshot = source != StatusSource.LiveCamera || search.snapshot != null,
    areaSelected = source == StatusSource.LiveCamera && areaSelected,
    areaReady = presentation.areaReady,
    highlightsReady = highlights.ready,
    retainedOutlines = source == StatusSource.LiveCamera &&
        presentation.outlinedElements.isNotEmpty() &&
        presentation.occurrences.any { it.firstMissingAtMillis != null },
)

@Composable
private fun liveStatusMessage(
    state: OcrUiState,
    search: TextSearchResult,
    content: RecognizedTextContent?,
    presentation: SearchPresentation,
    highlights: Highlights,
    areaSelected: Boolean,
): String {
    if (state.status != OcrStatus.Scanning && state.status != OcrStatus.TextRecognized) {
        return when (state.status) {
            OcrStatus.PermissionRequired -> stringResource(Res.string.status_permission)
            OcrStatus.Starting -> stringResource(Res.string.status_starting)
            OcrStatus.Paused -> stringResource(Res.string.status_paused)
            OcrStatus.Unavailable -> state.message.ifBlank {
                stringResource(Res.string.status_unavailable)
            }
            OcrStatus.Error -> stringResource(Res.string.status_camera_error_recovery)
            OcrStatus.Scanning,
            OcrStatus.TextRecognized -> error("Handled above")
        }
    }
    return sourceStatusMessage(
        source = StatusSource.LiveCamera,
        feedback = statusFeedback(
            StatusSource.LiveCamera,
            search,
            content,
            presentation,
            highlights,
            areaSelected,
        ),
        scanning = true,
    )
}
