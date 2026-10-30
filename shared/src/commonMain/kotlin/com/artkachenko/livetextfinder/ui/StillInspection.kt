package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.StillViewTransform
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.SearchTermSummary
import com.artkachenko.livetextfinder.search.StillMatchRow
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_result_count_description
import livetextfinder.shared.generated.resources.search_results_done
import livetextfinder.shared.generated.resources.search_results_title
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.still_copy_match
import livetextfinder.shared.generated.resources.still_matches
import livetextfinder.shared.generated.resources.still_matches_title
import livetextfinder.shared.generated.resources.still_match_next
import livetextfinder.shared.generated.resources.still_match_previous
import livetextfinder.shared.generated.resources.still_match_show
import livetextfinder.shared.generated.resources.still_match_position
import livetextfinder.shared.generated.resources.still_match_position_description
import livetextfinder.shared.generated.resources.still_match_copy_short
import livetextfinder.shared.generated.resources.search_results_locate_hint
import livetextfinder.shared.generated.resources.still_reset_view
import livetextfinder.shared.generated.resources.still_zoom_in
import livetextfinder.shared.generated.resources.still_zoom_out
import org.jetbrains.compose.resources.stringResource

/** Remembers view state only for one retained source in one measured viewport. */
@Composable
internal fun rememberStillViewTransform(
    sourceIdentity: String?,
    viewportSize: IntSize,
): MutableState<StillViewTransform> = remember(sourceIdentity, viewportSize) {
    mutableStateOf(StillViewTransform())
}

/** One clipped viewport applies a single transform to its complete still-image subtree. */
@Composable
internal fun StillInspectionViewport(
    sourceIdentity: String?,
    fit: FrozenFrameFit?,
    viewportSize: IntSize,
    transform: StillViewTransform,
    onTransformChange: (StillViewTransform) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentTransform by rememberUpdatedState(transform)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    Box(
        modifier = modifier
            .then(if (sourceIdentity != null) Modifier.clipToBounds() else Modifier)
            .pointerInput(sourceIdentity, fit, viewportSize) {
                if (sourceIdentity != null && fit?.isValid == true &&
                    viewportSize.width > 0 && viewportSize.height > 0
                ) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        currentOnTransformChange(
                            currentTransform.zoomBy(
                                factor = zoom,
                                centroid = PreviewPoint(centroid.x, centroid.y),
                                viewportWidth = viewportSize.width.toFloat(),
                                viewportHeight = viewportSize.height.toFloat(),
                                fit = fit,
                                panX = pan.x,
                                panY = pan.y,
                            ),
                        )
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier.matchParentSize().then(
                if (sourceIdentity != null) {
                    Modifier.graphicsLayer {
                        scaleX = if (currentTransform.isRenderable) currentTransform.zoom else 1f
                        scaleY = if (currentTransform.isRenderable) currentTransform.zoom else 1f
                        translationX = if (currentTransform.isRenderable) currentTransform.panX
                        else 0f
                        translationY = if (currentTransform.isRenderable) currentTransform.panY
                        else 0f
                    }
                } else {
                    Modifier
                },
            ),
        ) {
            content()
        }
    }
}

@Composable
internal fun StillInspectionControls(
    matchCount: Int,
    zoom: Float,
    canZoom: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onMatches: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onZoomIn, enabled = canZoom && zoom < 4f) {
            Text(stringResource(Res.string.still_zoom_in))
        }
        TextButton(onClick = onZoomOut, enabled = canZoom && zoom > 1f) {
            Text(stringResource(Res.string.still_zoom_out))
        }
        TextButton(onClick = onReset, enabled = canZoom && zoom != 1f) {
            Text(stringResource(Res.string.still_reset_view))
        }
        TextButton(onClick = onMatches, enabled = matchCount > 0) {
            Text(stringResource(Res.string.still_matches, matchCount))
        }
    }
}

@Composable
internal fun StillMatchesDialog(
    rows: List<StillMatchRow>,
    selectedIndex: Int?,
    copyMessage: String?,
    onSelect: (Int) -> Unit,
    onCopy: () -> Unit,
    onShowOnImage: () -> Unit = {},
    canLocate: Boolean = true,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.still_matches_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                if (!canLocate) {
                    Text(
                        stringResource(Res.string.search_results_locate_hint),
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).semantics {
                        selectableGroup()
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(rows, key = StillMatchRow::index) { row ->
                        StillMatchRowItem(
                            row = row,
                            selected = selectedIndex == row.index,
                            onSelect = { onSelect(row.index) },
                        )
                    }
                }
                copyMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIndex != null && rows.any { row -> row.index == selectedIndex },
                onClick = onCopy,
            ) {
                Text(stringResource(Res.string.still_copy_match))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = canLocate && selectedIndex != null &&
                        rows.any { it.index == selectedIndex },
                    onClick = onShowOnImage,
                ) { Text(stringResource(Res.string.still_match_show)) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.status_cancel)) }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
internal fun SearchTermResultsDialog(
    terms: List<SearchTermSummary>,
    canLocate: Boolean = true,
    onSelect: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.search_results_title)) },
        text = {
            Column {
                if (!canLocate) {
                    Text(
                        stringResource(Res.string.search_results_locate_hint),
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp).semantics {
                        selectableGroup()
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(terms, key = SearchTermSummary::termId) { term ->
                        val countDescription = stringResource(
                            Res.string.search_result_count_description,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = false,
                                enabled = canLocate && term.occurrenceCount > 0,
                                role = Role.Button,
                                onClick = { onSelect(term.termId) },
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(20.dp)
                                    .background(
                                        term.color.composeColor,
                                        MaterialTheme.shapes.extraSmall,
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.extraSmall,
                                    ),
                            )
                            Text(term.label, modifier = Modifier.weight(1f))
                            Text(
                                term.occurrenceCount.toString(),
                                modifier = Modifier.semantics {
                                    contentDescription = "$countDescription ${term.occurrenceCount}"
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.search_results_done))
            }
        },
    )
}

@Composable
internal fun StillMatchNavigationControls(
    position: Int,
    count: Int,
    matchedTerm: String,
    matchedText: String,
    canLocate: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCopy: () -> Unit,
) {
    val positionDescription = stringResource(
        Res.string.still_match_position_description,
        position,
        count,
        matchedTerm,
        matchedText,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onPrevious, enabled = canLocate && position > 1) {
                Text(stringResource(Res.string.still_match_previous))
            }
            Text(
                stringResource(Res.string.still_match_position, position, count),
                modifier = Modifier.semantics {
                    contentDescription = positionDescription
                },
            )
            TextButton(onClick = onNext, enabled = canLocate && position in 1 until count) {
                Text(stringResource(Res.string.still_match_next))
            }
            TextButton(onClick = onCopy, enabled = position > 0) {
                Text(stringResource(Res.string.still_match_copy_short))
            }
        }
        Text(
            text = "$matchedTerm: $matchedText",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StillMatchRowItem(
    row: StillMatchRow,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium,
            ),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(16.dp)
                        .background(
                            row.termColor.composeColor,
                            MaterialTheme.shapes.extraSmall,
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.extraSmall,
                        ),
                )
                Text(row.termLabel, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = row.matchedText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = row.lineContext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
