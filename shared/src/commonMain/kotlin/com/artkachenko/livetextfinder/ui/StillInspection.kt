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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.camera.FrozenFrameFit
import com.artkachenko.livetextfinder.camera.StillViewTransform
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.SearchTermSummary
import com.artkachenko.livetextfinder.search.StillMatchRow
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.search_context_count_unavailable
import livetextfinder.shared.generated.resources.search_result_count_description
import livetextfinder.shared.generated.resources.search_results_done
import livetextfinder.shared.generated.resources.search_results_locate_hint
import livetextfinder.shared.generated.resources.search_results_matched_terms
import livetextfinder.shared.generated.resources.search_results_occurrences
import livetextfinder.shared.generated.resources.search_results_title
import livetextfinder.shared.generated.resources.search_results_unavailable_explanation
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.status_match_count
import livetextfinder.shared.generated.resources.still_copy_match
import livetextfinder.shared.generated.resources.still_match_copy_short
import livetextfinder.shared.generated.resources.still_match_next
import livetextfinder.shared.generated.resources.still_match_position
import livetextfinder.shared.generated.resources.still_match_position_description
import livetextfinder.shared.generated.resources.still_match_previous
import livetextfinder.shared.generated.resources.still_match_show
import livetextfinder.shared.generated.resources.still_matches
import livetextfinder.shared.generated.resources.still_matches_title
import livetextfinder.shared.generated.resources.still_reset_view
import livetextfinder.shared.generated.resources.still_zoom_in
import livetextfinder.shared.generated.resources.still_zoom_out
import org.jetbrains.compose.resources.pluralStringResource
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
    showMatchesAction: Boolean = true,
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
        TextButton(
            onClick = onZoomIn,
            enabled = canZoom && zoom < 4f,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.still_zoom_in))
        }
        TextButton(
            onClick = onZoomOut,
            enabled = canZoom && zoom > 1f,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.still_zoom_out))
        }
        TextButton(
            onClick = onReset,
            enabled = canZoom && zoom != 1f,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(Res.string.still_reset_view))
        }
        if (showMatchesAction) {
            TextButton(
                onClick = onMatches,
                enabled = matchCount > 0,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.still_matches, matchCount))
            }
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
            val selectedItemIndex = rows.indexOfFirst { row -> row.index == selectedIndex }
            val listState = rememberLazyListState()
            LaunchedEffect(selectedItemIndex) {
                if (selectedItemIndex >= 0) {
                    listState.scrollToItem(selectedItemIndex)
                }
            }
            Column(modifier = Modifier.heightIn(max = 180.dp)) {
                Text(
                    pluralStringResource(
                        Res.plurals.status_match_count,
                        rows.size,
                        rows.size,
                    ),
                    modifier = Modifier.padding(bottom = FinderSpacing.small),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!canLocate) {
                    Text(
                        stringResource(Res.string.search_results_locate_hint),
                        modifier = Modifier.padding(bottom = FinderSpacing.small),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 140.dp)
                        .semantics { selectableGroup() },
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
            Column(horizontalAlignment = Alignment.End) {
                FinderPrimaryAction(
                    label = stringResource(Res.string.still_copy_match),
                    enabled = selectedIndex != null &&
                        rows.any { row -> row.index == selectedIndex },
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCopy,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        FinderSpacing.extraSmall,
                        Alignment.End,
                    ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    FinderSecondaryAction(
                        label = stringResource(Res.string.still_match_show),
                        onClick = onShowOnImage,
                        enabled = canLocate && selectedIndex != null &&
                            rows.any { it.index == selectedIndex },
                    )
                    FinderSecondaryAction(
                        label = stringResource(Res.string.status_cancel),
                        onClick = onDismiss,
                    )
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
internal fun SearchTermResultsDialog(
    terms: List<SearchTermSummary>,
    resultsAvailable: Boolean = true,
    canLocate: Boolean = true,
    onSelect: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.search_results_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (resultsAvailable) {
                    val occurrenceTotal = terms.sumOf(SearchTermSummary::occurrenceCount)
                    val matchedTermCount = terms.count { term -> term.occurrenceCount > 0 }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(FinderSpacing.large),
                            horizontalArrangement = Arrangement.spacedBy(FinderSpacing.medium),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(Res.string.search_results_occurrences),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    occurrenceTotal.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                pluralStringResource(
                                    Res.plurals.search_results_matched_terms,
                                    matchedTermCount,
                                    matchedTermCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!resultsAvailable) {
                    Text(
                        stringResource(Res.string.search_results_unavailable_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (resultsAvailable && !canLocate) {
                    Text(
                        stringResource(Res.string.search_results_locate_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 220.dp)
                        .semantics { selectableGroup() },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(terms, key = SearchTermSummary::termId) { term ->
                        val countDescription = stringResource(
                            Res.string.search_result_count_description,
                        )
                        val positive = resultsAvailable && term.occurrenceCount > 0
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (positive) MaterialTheme.colorScheme.surfaceContainer
                                    else MaterialTheme.colorScheme.surface,
                                )
                                .selectable(
                                    selected = false,
                                    enabled = resultsAvailable && canLocate &&
                                        term.occurrenceCount > 0,
                                    role = Role.Button,
                                    onClick = { onSelect(term.termId) },
                                )
                                .padding(
                                    horizontal = FinderSpacing.medium,
                                    vertical = FinderSpacing.small,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Box(
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
                            Text(
                                term.label,
                                modifier = Modifier.weight(1f),
                                style = if (positive) MaterialTheme.typography.bodyLarge
                                else MaterialTheme.typography.bodyMedium,
                                color = if (positive) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Surface(
                                color = if (positive) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = if (resultsAvailable) term.occurrenceCount.toString()
                                    else stringResource(
                                        Res.string.search_context_count_unavailable,
                                    ),
                                    modifier = Modifier.semantics {
                                        if (resultsAvailable) {
                                            contentDescription =
                                                "$countDescription ${term.occurrenceCount}"
                                        }
                                    }.padding(
                                        horizontal = FinderSpacing.small,
                                        vertical = FinderSpacing.extraSmall,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (positive) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FinderSecondaryAction(
                label = stringResource(Res.string.search_results_done),
                onClick = onDismiss,
            )
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
            TextButton(
                onClick = onPrevious,
                enabled = canLocate && position > 1,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.still_match_previous))
            }
            Text(
                stringResource(Res.string.still_match_position, position, count),
                modifier = Modifier.semantics {
                    contentDescription = positionDescription
                },
            )
            TextButton(
                onClick = onNext,
                enabled = canLocate && position in 1 until count,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(Res.string.still_match_next))
            }
            TextButton(
                onClick = onCopy,
                enabled = position > 0,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
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
                .padding(
                    horizontal = FinderSpacing.medium,
                    vertical = FinderSpacing.small,
                ),
        ) {
            Text(
                text = row.matchedText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(16.dp)
                        .background(row.termColor.composeColor, MaterialTheme.shapes.extraSmall)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.extraSmall,
                        ),
                )
                Text(
                    text = "${row.termLabel} · ${row.lineContext}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
