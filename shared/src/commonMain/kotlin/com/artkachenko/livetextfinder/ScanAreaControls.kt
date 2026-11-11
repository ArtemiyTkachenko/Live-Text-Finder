package com.artkachenko.livetextfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.artkachenko.livetextfinder.camera.FreezeMode
import com.artkachenko.livetextfinder.camera.allowsLiveCamera
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import com.artkachenko.livetextfinder.ui.FinderCameraOverlay
import com.artkachenko.livetextfinder.ui.FinderPrimaryAction
import com.artkachenko.livetextfinder.ui.FinderSecondaryAction
import com.artkachenko.livetextfinder.ui.FinderSpacing
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.scan_area_apply
import livetextfinder.shared.generated.resources.scan_area_badge
import livetextfinder.shared.generated.resources.scan_area_cancel
import livetextfinder.shared.generated.resources.scan_area_editing
import livetextfinder.shared.generated.resources.scan_area_full_frame
import livetextfinder.shared.generated.resources.scan_area_precise
import org.jetbrains.compose.resources.stringResource

/** Labels the committed scan area on the live preview. */
@Composable
internal fun ScanAreaBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.scan_area_badge),
        modifier = modifier
            .padding(FinderSpacing.small)
            .background(FinderCameraOverlay.labelScrim)
            .padding(
                horizontal = FinderCameraOverlay.labelPaddingHorizontal,
                vertical = FinderCameraOverlay.labelPaddingVertical,
            ),
        color = FinderCameraOverlay.onLabelScrim,
    )
}

/**
 * Draft actions shown in place of the dock while the scan area is edited on the preview.
 *
 * Each action carries [editorToken] and rechecks the live camera context when tapped, so a tap
 * that lands after a freeze, an import or a hidden preview does nothing.
 */
@Composable
internal fun ScanAreaEditBar(
    liveArea: LiveSearchAreaState,
    editorToken: Long,
    context: FinderSourceContext,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(
            horizontal = FinderSpacing.large,
            vertical = FinderSpacing.extraSmall,
        ),
        verticalArrangement = Arrangement.spacedBy(
            FinderSpacing.extraSmall,
        ),
    ) {
        Text(
            stringResource(Res.string.scan_area_editing),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                FinderSpacing.small,
            ),
            verticalArrangement = Arrangement.spacedBy(
                FinderSpacing.small,
            ),
        ) {
            FinderPrimaryAction(
                label = stringResource(Res.string.scan_area_apply),
                enabled = true,
                onClick = {
                    val latestPhase = context.importedOwner.state.value.phase
                    val latestFreeze = context.frozenOwner.state.value
                    liveArea.applyEditor(editorToken) {
                        latestFreeze.mode == FreezeMode.Live &&
                            context.importedOwner.state.value.phase ==
                                latestPhase &&
                            context.isContentVisible() &&
                            liveSearchAreaAvailable() &&
                            latestPhase.allowsLiveCamera
                    }
                },
            )
            FinderSecondaryAction(stringResource(Res.string.scan_area_cancel), {
                liveArea.cancelEditor(editorToken)
            })
            FinderSecondaryAction(stringResource(Res.string.scan_area_full_frame), {
                liveArea.updateEditor(editorToken, null)
            })
            FinderSecondaryAction(
                stringResource(Res.string.scan_area_precise),
                liveArea.precisionAction(editorToken, context::isLiveAreaContextCurrent),
            )
        }
    }
}
