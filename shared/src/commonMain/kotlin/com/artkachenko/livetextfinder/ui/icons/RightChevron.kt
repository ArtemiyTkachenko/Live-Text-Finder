package com.artkachenko.livetextfinder.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun RightChevron(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.375f, size.height * 0.25f)
            lineTo(size.width * 0.625f, size.height * 0.5f)
            lineTo(size.width * 0.375f, size.height * 0.75f)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}