package com.artkachenko.livetextfinder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun LiveSearchAreaDialog(
    area: NormalizedSearchArea?,
    revision: Long,
    owner: LiveSearchAreaOwner,
    onDismiss: () -> Unit,
) {
    val initial = area ?: NormalizedSearchArea.CENTERED_DEFAULT
    val widthState = remember(revision, area) {
        mutableIntStateOf(((initial.right - initial.left) * 100).roundToInt().coerceIn(20, 100))
    }
    val heightState = remember(revision, area) {
        mutableIntStateOf(((initial.bottom - initial.top) * 100).roundToInt().coerceIn(20, 100))
    }
    val horizontalState = remember(revision, area) {
        mutableIntStateOf(
            if (initial.right - initial.left >= 1f) {
                50
            } else {
                ((initial.left / (1f - (initial.right - initial.left))) * 100)
                    .roundToInt().coerceIn(0, 100)
            },
        )
    }
    val verticalState = remember(revision, area) {
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
            Text("Live search area")
        },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AreaSlider("Width", width, 20, 100) {
                    width = it
                }
                AreaSlider("Height", height, 20, 100) {
                    height = it
                }
                AreaSlider("Horizontal position", horizontal, 0, 100) {
                    horizontal = it
                }
                AreaSlider("Vertical position", vertical, 0, 100) {
                    vertical = it
                }
            }
        },
        confirmButton = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        owner.apply(
                            revision,
                            NormalizedSearchArea.fromSizeAndPosition(
                                width,
                                height,
                                horizontal,
                                vertical,
                            ),
                        )
                        onDismiss()
                    },
                ) {
                    Text("Apply")
                }
                TextButton(
                    onClick = onDismiss,
                ) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        owner.apply(revision, null)
                        onDismiss()
                    },
                ) {
                    Text("Full frame")
                }
            }
        },
    )
}

@Composable
private fun AreaSlider(
    label: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Text("$label: $value%")
        Slider(
            value = value.toFloat(),
            onValueChange = {
                onValueChange(it.toInt().coerceIn(minimum, maximum))
            },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = maximum - minimum - 1,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
            },
        )
    }
}
