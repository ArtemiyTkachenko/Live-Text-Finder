package com.artkachenko.livetextfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.SearchAreaCorner
import com.artkachenko.livetextfinder.search.moveSearchArea
import com.artkachenko.livetextfinder.search.resizeSearchArea
import com.artkachenko.livetextfinder.search.resizeSearchAreaByStep
import com.artkachenko.livetextfinder.search.searchAreaCornerAt
import kotlin.math.roundToInt

@androidx.compose.runtime.Composable
internal expect fun LiveAreaBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Direct editor over the measured live preview. Bounds stay normalized across viewport changes. */
@Composable
internal fun LiveSearchAreaEditorOverlay(
    area: NormalizedSearchArea,
    viewportSize: IntSize,
    onAreaChange: (NormalizedSearchArea) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentArea by rememberUpdatedState(area)
    val currentOnAreaChange by rememberUpdatedState(onAreaChange)
    val density = LocalDensity.current
    val hitRadiusPx = with(density) { 24.dp.toPx() }
    val corners = listOf(
        SearchAreaCorner.TopLeft to Offset(
            area.left * viewportSize.width, area.top * viewportSize.height,
        ),
        SearchAreaCorner.TopRight to Offset(
            area.right * viewportSize.width, area.top * viewportSize.height,
        ),
        SearchAreaCorner.BottomLeft to Offset(
            area.left * viewportSize.width, area.bottom * viewportSize.height,
        ),
        SearchAreaCorner.BottomRight to Offset(
            area.right * viewportSize.width, area.bottom * viewportSize.height,
        ),
    )
    fun moveBy(dx: Int, dy: Int): Boolean {
        val next = moveSearchArea(
            area, dx * viewportSize.width * 0.05f, dy * viewportSize.height * 0.05f,
            viewportSize.width.toFloat(), viewportSize.height.toFloat(),
        ) ?: return false
        onAreaChange(next)
        return true
    }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val left = area.left * size.width
            val top = area.top * size.height
            val right = area.right * size.width
            val bottom = area.bottom * size.height
            val dash = PathEffect.dashPathEffect(
                floatArrayOf(10.dp.toPx(), 6.dp.toPx()),
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = Stroke(5.dp.toPx(), pathEffect = dash),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = Stroke(3.dp.toPx(), pathEffect = dash),
            )
            corners.forEach { (_, point) ->
                val radius = 10.dp.toPx()
                val visibleCenter = Offset(
                    point.x.coerceIn(radius, (size.width - radius).coerceAtLeast(radius)),
                    point.y.coerceIn(radius, (size.height - radius).coerceAtLeast(radius)),
                )
                drawCircle(Color.Black, radius = radius, center = visibleCenter)
                drawCircle(Color.White, radius = 7.dp.toPx(), center = visibleCenter)
            }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(viewportSize, hitRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startArea = currentArea
                    val startWidth = size.width.toFloat()
                    val startHeight = size.height.toFloat()
                    val nearest = searchAreaCornerAt(
                        startArea, down.position.x, down.position.y,
                        startWidth, startHeight, hitRadiusPx * 2f,
                    )
                    val inside = down.position.x in
                        (startArea.left * startWidth)..(startArea.right * startWidth) &&
                        down.position.y in
                            (startArea.top * startHeight)..(startArea.bottom * startHeight)
                    if (nearest == null && !inside) return@awaitEachGesture
                    fun applyAt(position: Offset) {
                        val total = position - down.position
                        val updated = nearest?.let { corner ->
                            resizeSearchArea(
                                startArea, corner, total.x, total.y, startWidth, startHeight,
                            )
                        } ?: moveSearchArea(startArea, total.x, total.y, startWidth, startHeight)
                        updated?.let(currentOnAreaChange)
                    }
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        if (!dragging) {
                            if ((change.position - down.position).getDistance() <=
                                viewConfiguration.touchSlop
                            ) {
                                continue
                            }
                            dragging = true
                        }
                        applyAt(change.position)
                        change.consume()
                    }
                }
            },
        )
        corners.forEach { (corner, point) ->
            val handle = with(density) { 48.dp.toPx() }
            val x = (point.x - handle / 2f).coerceIn(
                0f, (viewportSize.width - handle).coerceAtLeast(0f),
            )
            val y = (point.y - handle / 2f).coerceIn(
                0f, (viewportSize.height - handle).coerceAtLeast(0f),
            )
            val isLeft = corner == SearchAreaCorner.TopLeft ||
                corner == SearchAreaCorner.BottomLeft
            val isTop = corner == SearchAreaCorner.TopLeft ||
                corner == SearchAreaCorner.TopRight
            fun resizeAction(direction: String, dx: Int, dy: Int) = CustomAccessibilityAction(
                "Resize $corner $direction by 5%",
            ) {
                val next = resizeSearchAreaByStep(
                    area, corner, dx, dy,
                    viewportSize.width.toFloat(), viewportSize.height.toFloat(),
                ) ?: return@CustomAccessibilityAction false
                onAreaChange(next)
                true
            }
            val regionLeft = with(density) { (area.left * viewportSize.width).toDp() }
            val regionTop = with(density) { (area.top * viewportSize.height).toDp() }
            val regionWidth = with(density) {
                ((area.right - area.left) * viewportSize.width).toDp()
            }
            val regionHeight = with(density) {
                ((area.bottom - area.top) * viewportSize.height).toDp()
            }
            if (corner == SearchAreaCorner.TopLeft) {
                Box(
                    Modifier.align(Alignment.TopStart)
                        .offset(x = regionLeft, y = regionTop)
                        .size(regionWidth, regionHeight)
                        .focusable()
                        .semantics {
                            contentDescription = "Movable scan area"
                            stateDescription = boundsDescription(area)
                            customActions = listOf(
                                CustomAccessibilityAction("Move left by 5%") { moveBy(-1, 0) },
                                CustomAccessibilityAction("Move right by 5%") { moveBy(1, 0) },
                                CustomAccessibilityAction("Move up by 5%") { moveBy(0, -1) },
                                CustomAccessibilityAction("Move down by 5%") { moveBy(0, 1) },
                            )
                        },
                )
            }
            Box(
                Modifier.align(Alignment.TopStart)
                    .offset(x = with(density) { x.toDp() }, y = with(density) { y.toDp() })
                    .size(48.dp).background(Color.Transparent).focusable().semantics {
                        contentDescription = "Resize scan area $corner handle"
                        stateDescription = boundsDescription(area)
                        customActions = listOf(
                            resizeAction("left", -1, 0),
                            resizeAction("right", 1, 0),
                            resizeAction("up", 0, -1),
                            resizeAction("down", 0, 1),
                        )
                    },
            )
        }
    }
}

private fun percent(value: Float) = (value * 100).roundToInt()

private fun boundsDescription(area: NormalizedSearchArea) =
    "Bounds ${percent(area.left)}%, ${percent(area.top)}%, " +
        "${percent(area.right)}%, ${percent(area.bottom)}%"

