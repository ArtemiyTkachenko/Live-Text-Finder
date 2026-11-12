package com.artkachenko.livetextfinder.scanarea

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.liveSearchAreaAvailable
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.ui.FinderPrimaryAction
import com.artkachenko.livetextfinder.ui.FinderSecondaryAction
import com.artkachenko.livetextfinder.ui.FinderSpacing
import kotlin.math.roundToInt
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.area_bounds_description
import livetextfinder.shared.generated.resources.area_guide_caption
import livetextfinder.shared.generated.resources.area_guide_description
import livetextfinder.shared.generated.resources.area_guide_title
import livetextfinder.shared.generated.resources.area_height
import livetextfinder.shared.generated.resources.area_horizontal_position
import livetextfinder.shared.generated.resources.area_position_group
import livetextfinder.shared.generated.resources.area_position_help
import livetextfinder.shared.generated.resources.area_size_group
import livetextfinder.shared.generated.resources.area_slider_value
import livetextfinder.shared.generated.resources.area_vertical_position
import livetextfinder.shared.generated.resources.area_width
import livetextfinder.shared.generated.resources.scan_area_apply
import livetextfinder.shared.generated.resources.scan_area_cancel
import livetextfinder.shared.generated.resources.scan_area_dialog_title
import livetextfinder.shared.generated.resources.scan_area_full_frame
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun LiveSearchAreaDialog(
    area: NormalizedSearchArea?,
    revision: Long,
    owner: LiveSearchAreaOwner,
    onDismiss: () -> Unit,
    onDraftApply: ((NormalizedSearchArea?) -> Unit)? = null,
    startFullFrame: Boolean = false,
) {
    val initial = if (startFullFrame) {
        NormalizedSearchArea(0f, 0f, 1f, 1f)
    } else {
        area ?: NormalizedSearchArea.CENTERED_DEFAULT
    }
    val widthState = remember(revision, area, startFullFrame) {
        mutableIntStateOf(((initial.right - initial.left) * 100).roundToInt().coerceIn(20, 100))
    }
    val heightState = remember(revision, area, startFullFrame) {
        mutableIntStateOf(((initial.bottom - initial.top) * 100).roundToInt().coerceIn(20, 100))
    }
    val horizontalState = remember(revision, area, startFullFrame) {
        mutableIntStateOf(
            if (initial.right - initial.left >= 1f) {
                50
            } else {
                ((initial.left / (1f - (initial.right - initial.left))) * 100)
                    .roundToInt().coerceIn(0, 100)
            },
        )
    }
    val verticalState = remember(revision, area, startFullFrame) {
        mutableIntStateOf(
            if (initial.bottom - initial.top >= 1f) {
                50
            } else {
                ((initial.top / (1f - (initial.bottom - initial.top))) * 100)
                    .roundToInt().coerceIn(0, 100)
            },
        )
    }
    var width by widthState
    var height by heightState
    var horizontal by horizontalState
    var vertical by verticalState

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.scan_area_dialog_title))
        },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FinderSpacing.large),
            ) {
                AreaGuide(width, height, horizontal, vertical)
                AreaGroup(title = stringResource(Res.string.area_size_group)) {
                    AreaSlider(
                        stringResource(Res.string.area_width),
                        width,
                        20,
                        100,
                    ) { width = it }
                    AreaSlider(
                        stringResource(Res.string.area_height),
                        height,
                        20,
                        100,
                    ) { height = it }
                }
                AreaGroup(title = stringResource(Res.string.area_position_group)) {
                    Text(
                        stringResource(Res.string.area_position_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AreaSlider(
                        stringResource(Res.string.area_horizontal_position),
                        horizontal,
                        0,
                        100,
                    ) { horizontal = it }
                    AreaSlider(
                        stringResource(Res.string.area_vertical_position),
                        vertical,
                        0,
                        100,
                    ) { vertical = it }
                }
            }
        },
        confirmButton = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FinderPrimaryAction(
                    label = stringResource(Res.string.scan_area_apply),
                    enabled = true,
                    onClick = {
                        val next = NormalizedSearchArea.fromSizeAndPosition(
                                width,
                                height,
                                horizontal,
                                vertical,
                            )
                        if (onDraftApply != null) {
                            onDraftApply(next)
                        } else {
                            owner.apply(revision, next)
                        }
                        onDismiss()
                    },
                )
                FinderSecondaryAction(
                    label = stringResource(Res.string.scan_area_cancel),
                    onClick = onDismiss,
                )
                FinderSecondaryAction(
                    label = stringResource(Res.string.scan_area_full_frame),
                    onClick = {
                        if (onDraftApply != null) {
                            onDraftApply(null)
                        } else {
                            owner.apply(revision, null)
                        }
                        onDismiss()
                    },
                )
            }
        },
    )
}

@Composable
private fun AreaGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FinderSpacing.small)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun AreaGuide(width: Int, height: Int, horizontal: Int, vertical: Int) {
    val bounds = NormalizedSearchArea.fromSizeAndPosition(width, height, horizontal, vertical)
    val boundsDescription = stringResource(
        Res.string.area_bounds_description,
        (bounds.left * 100).roundToInt(),
        (bounds.top * 100).roundToInt(),
        (bounds.right * 100).roundToInt(),
        (bounds.bottom * 100).roundToInt(),
    )
    val label = stringResource(
        Res.string.area_guide_description,
        width,
        height,
        horizontal,
        vertical,
        boundsDescription,
    )
    Column(verticalArrangement = Arrangement.spacedBy(FinderSpacing.small)) {
        Text(
            stringResource(Res.string.area_guide_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(Res.string.area_guide_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(112.dp)
                .padding(FinderSpacing.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .testTag("area-guide-frame")
                .semantics { contentDescription = label },
        ) {
            Box(
                modifier = Modifier.offset(
                    x = maxWidth * bounds.left,
                    y = maxHeight * bounds.top,
                )
                    .fillMaxWidth(bounds.right - bounds.left)
                    .height(maxHeight * (bounds.bottom - bounds.top))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary)
                    .testTag("area-guide-region")
            )
        }
        Text(
            boundsDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AreaSlider(
    label: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    onValueChange: (Int) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val sliderColors = SliderDefaults.colors()
    Column {
        Text(stringResource(Res.string.area_slider_value, label, value))
        Slider(
            value = value.toFloat(),
            onValueChange = {
                onValueChange(it.toInt().coerceIn(minimum, maximum))
            },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = maximum - minimum - 1,
            interactionSource = interactionSource,
            thumb = {
                Box(
                    modifier = Modifier.height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        colors = sliderColors,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics {
                contentDescription = label
            },
        )
    }
}

/** Shows the precision dialog for the committed area or the direct-edit draft, when requested. */
@Composable
internal fun LiveSearchAreaDialogHost(liveArea: LiveSearchAreaState) {
    val request = liveArea.dialogRequest
    if (request == null || !liveSearchAreaAvailable()) return
    val usesDraft = request.usesDraft
    val draftArea = liveArea.editorDraft?.area
    LiveSearchAreaDialog(
        area = if (usesDraft) draftArea else liveArea.area,
        revision = request.revision,
        owner = liveArea.owner,
        startFullFrame = usesDraft && draftArea == null,
        onDismiss = { liveArea.dismissDialog(request) },
        onDraftApply = { nextArea -> liveArea.applyDialogArea(request, nextArea) },
    )
}
