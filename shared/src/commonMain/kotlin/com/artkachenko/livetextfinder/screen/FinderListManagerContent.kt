package com.artkachenko.livetextfinder.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.artkachenko.livetextfinder.search.SearchListTransferState
import com.artkachenko.livetextfinder.ui.AppContentColumn
import com.artkachenko.livetextfinder.ui.ContextMessage
import com.artkachenko.livetextfinder.ui.FinderCaptureActions
import com.artkachenko.livetextfinder.ui.FinderCaptureControls
import com.artkachenko.livetextfinder.ui.FinderSearchBar
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.FinderSpacing
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_list_transfer_cancel
import org.jetbrains.compose.resources.stringResource

/** Scrollable saved-list manager and inline controls. */
@Composable
internal fun FinderListManagerContent(
    searchBar: FinderSearchBar,
    captureActions: FinderCaptureActions,
    resultsLabel: String?,
    onOptions: () -> Unit,
    snackbarHostState: SnackbarHostState,
    transferState: SearchListTransferState,
    onCancelTransfer: () -> Unit,
    status: @Composable () -> Unit,
    manager: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AppContentColumn(
            scrollState = rememberScrollState(),
            scrollable = true,
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(
                    horizontal = FinderSpacing.large,
                    vertical = FinderSpacing.extraSmall,
                ),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
            ) {
                FinderSearchControls(
                    showTitle = false,
                    search = searchBar,
                    resultsLabel = resultsLabel,
                    onOptions = onOptions,
                )
                if (transferState.busy && transferState.message != null) {
                    ContextMessage(
                        message = transferState.message,
                        actionLabel = stringResource(Res.string.search_list_transfer_cancel),
                        onAction = onCancelTransfer,
                    )
                }
                status()
            }
            manager()
            FinderCaptureControls(captureActions)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().imePadding(),
        )
    }
}
