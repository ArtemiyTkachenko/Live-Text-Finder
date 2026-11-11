package com.artkachenko.livetextfinder

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.artkachenko.livetextfinder.ui.FinderCaptureActions
import com.artkachenko.livetextfinder.ui.FinderCaptureControls
import com.artkachenko.livetextfinder.ui.FinderLiveDock
import com.artkachenko.livetextfinder.ui.FinderSearchBar
import com.artkachenko.livetextfinder.ui.RetainedInspectionActionDispatcher

/**
 * Everything below the viewport for the current [layoutMode]: the scan-area draft actions, the
 * list manager's capture controls or the still-inspection controls, followed by the dock when
 * the mode shows it.
 *
 * The dock is always called from the same place in this function, so its query field keeps
 * focus when the mode-specific controls above it change.
 */
@Composable
internal fun FinderFooter(
    layoutMode: FinderLayoutMode,
    searchBar: FinderSearchBar,
    captureActions: FinderCaptureActions,
    labels: FinderLabels,
    actions: FinderSourceActions,
    dialogs: FinderDialogsState,
    inspection: StillInspectionState,
    liveArea: LiveSearchAreaState,
    context: FinderSourceContext,
    shouldShowViews: Boolean,
    resultsAvailable: Boolean,
    sharePreparing: Boolean,
) {
    when (layoutMode) {
        FinderLayoutMode.AreaEditing -> {
            ScanAreaEditBar(
                liveArea = liveArea,
                editorToken = liveArea.editorDraft?.token ?: 0L,
                context = context,
            )
        }
        FinderLayoutMode.ListManager -> {
            FinderCaptureControls(captureActions)
        }
        FinderLayoutMode.StillInspection -> {
            StillInspectionFooter(
                inspection = inspection,
                shouldShowViews = shouldShowViews,
                resultsAvailable = resultsAvailable,
            )
        }
        FinderLayoutMode.Live -> Unit
    }
    if (layoutMode.showsDock) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        FinderLiveDock(
            search = searchBar,
            capture = captureActions,
            onOccurrences = {
                RetainedInspectionActionDispatcher.showOccurrences(
                    clearFocus = focusManager::clearFocus,
                    hideKeyboard = { keyboardController?.hide() },
                    setTermsOpen = { dialogs.termResultsOpen = it },
                    setOccurrencesOpen = { inspection.copySession.updateDialogOpen(it) },
                )
            },
            resultsLabel = labels.results,
            occurrencesEnabled = resultsAvailable && inspection.rows.isNotEmpty(),
            returnLabel = labels.returnToCamera,
            onReturn = actions::returnToCamera,
            onShare = actions::shareStillImage,
            onCancelShare = actions::cancelShare,
            sharePreparing = sharePreparing,
        )
    }
}
