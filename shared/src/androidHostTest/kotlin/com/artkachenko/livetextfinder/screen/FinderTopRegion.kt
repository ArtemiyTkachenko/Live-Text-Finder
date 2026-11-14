package com.artkachenko.livetextfinder.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.artkachenko.livetextfinder.search.SearchListTransferState
import com.artkachenko.livetextfinder.ui.ContextMessage
import com.artkachenko.livetextfinder.ui.FinderLiveAppBar
import com.artkachenko.livetextfinder.ui.FinderSearchBar
import com.artkachenko.livetextfinder.ui.FinderSearchControls
import com.artkachenko.livetextfinder.ui.FinderSpacing
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_list_transfer_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Everything above the viewport: the app bar or inline search bar, a running list transfer
 * with its Cancel action, and the status region.
 *
 * On the live camera screen [status] is a full-width band below the other content and is hidden
 * while the scan area is edited; in every other mode it sits inside the padded column.
 */
@Composable
internal fun FinderTopRegion(
    layoutMode: FinderLayoutMode,
    shouldShowViews: Boolean,
    searchBar: FinderSearchBar,
    inlineResultsLabel: String?,
    transferState: SearchListTransferState,
    onCancelTransfer: () -> Unit,
    onOptions: () -> Unit,
    status: @Composable () -> Unit,
) {
    val usesDockChrome = layoutMode != FinderLayoutMode.ListManager
    val liveScreen = shouldShowViews && layoutMode.isLiveCamera
    if (usesDockChrome) {
        FinderLiveAppBar(onOptions = onOptions)
    }
    Column(
        Modifier.fillMaxWidth().then(
            if (usesDockChrome) {
                Modifier
            } else {
                Modifier.padding(
                    horizontal = FinderSpacing.large,
                    vertical = FinderSpacing.extraSmall,
                )
            },
        ),
        verticalArrangement = Arrangement.spacedBy(FinderSpacing.extraSmall),
    ) {
        if (!usesDockChrome) {
            FinderSearchControls(
                showTitle = shouldShowViews,
                search = searchBar,
                resultsLabel = inlineResultsLabel,
                onOptions = onOptions,
            )
        }
        if (transferState.busy && transferState.message != null) {
            ContextMessage(
                message = transferState.message,
                actionLabel = stringResource(Res.string.search_list_transfer_cancel),
                onAction = onCancelTransfer,
            )
        }
        if (!liveScreen) {
            status()
        }
    }
    if (liveScreen && layoutMode != FinderLayoutMode.AreaEditing) {
        status()
    }
}
