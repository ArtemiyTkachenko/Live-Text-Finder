package com.artkachenko.livetextfinder.lists

import androidx.compose.runtime.Composable
import com.artkachenko.livetextfinder.search.SearchListTransferCopy
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_list_import_fallback_name
import livetextfinder.shared.generated.resources.search_list_transfer_another_busy
import livetextfinder.shared.generated.resources.search_list_transfer_choose_destination
import livetextfinder.shared.generated.resources.search_list_transfer_choose_file
import livetextfinder.shared.generated.resources.search_list_transfer_export_canceled
import livetextfinder.shared.generated.resources.search_list_transfer_export_complete
import livetextfinder.shared.generated.resources.search_list_transfer_export_failed
import livetextfinder.shared.generated.resources.search_list_transfer_export_unavailable
import livetextfinder.shared.generated.resources.search_list_transfer_import_canceled
import livetextfinder.shared.generated.resources.search_list_transfer_import_failed
import livetextfinder.shared.generated.resources.search_list_transfer_import_ready
import livetextfinder.shared.generated.resources.search_list_transfer_import_unavailable
import livetextfinder.shared.generated.resources.search_list_transfer_list_invalid
import livetextfinder.shared.generated.resources.search_list_transfer_reading
import livetextfinder.shared.generated.resources.search_list_transfer_saving
import org.jetbrains.compose.resources.stringResource

/** Resolves the localized messages the list transfer owner shows for import and export. */
@Composable
internal fun searchListTransferCopy(): SearchListTransferCopy {
    return SearchListTransferCopy(
        fallbackName = stringResource(Res.string.search_list_import_fallback_name),
        chooseFile = stringResource(Res.string.search_list_transfer_choose_file),
        readingFile = stringResource(Res.string.search_list_transfer_reading),
        chooseDestination = stringResource(
            Res.string.search_list_transfer_choose_destination,
        ),
        savingFile = stringResource(Res.string.search_list_transfer_saving),
        importCanceled = stringResource(Res.string.search_list_transfer_import_canceled),
        exportCanceled = stringResource(Res.string.search_list_transfer_export_canceled),
        importReady = stringResource(Res.string.search_list_transfer_import_ready),
        exportComplete = stringResource(Res.string.search_list_transfer_export_complete),
        anotherTransfer = stringResource(Res.string.search_list_transfer_another_busy),
        importUnavailable = stringResource(
            Res.string.search_list_transfer_import_unavailable,
        ),
        exportUnavailable = stringResource(
            Res.string.search_list_transfer_export_unavailable,
        ),
        listCannotExport = stringResource(Res.string.search_list_transfer_list_invalid),
        importFailed = stringResource(Res.string.search_list_transfer_import_failed),
        exportFailed = stringResource(Res.string.search_list_transfer_export_failed),
    )
}
