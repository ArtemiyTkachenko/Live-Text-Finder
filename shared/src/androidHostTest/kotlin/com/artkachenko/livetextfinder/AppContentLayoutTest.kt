package com.artkachenko.livetextfinder

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppContentLayoutTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null

    @After
    fun disposeComposition() {
        composeView?.disposeComposition()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun hiddenViewportScrollsSearchListActionsIntoViewAtShortHeight() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        this.activity = activity
        this.composeView = composeView
        val scrollState = ScrollState(0)
        val viewportSize = mutableStateOf(IntSize.Zero)
        val queryCoordinates = mutableStateOf<LayoutCoordinates?>(null)
        val saveCoordinates = mutableStateOf<LayoutCoordinates?>(null)
        val cancelCoordinates = mutableStateOf<LayoutCoordinates?>(null)

        composeView.setContent {
            MaterialTheme {
                Surface {
                    AppContentColumn(
                        scrollState = scrollState,
                        scrollable = true,
                        modifier = Modifier.fillMaxSize().onSizeChanged {
                            viewportSize.value = it
                        },
                    ) {
                        OutlinedTextField(
                            value = "query",
                            onValueChange = { _ ->
                            },
                            label = { Text("Find in recognized text") },
                            modifier = Modifier.onGloballyPositioned {
                                queryCoordinates.value = it
                            },
                        )
                        TextButton(
                            onClick = {
                            },
                        ) {
                            Text("Search lists")
                        }
                        Column(Modifier.padding(12.dp)) {
                            repeat(12) { index ->
                                TextButton(
                                    onClick = {
                                    },
                                ) {
                                    Text("List $index")
                                }
                            }
                            Button(
                                onClick = {
                                },
                                modifier = Modifier.onGloballyPositioned {
                                    saveCoordinates.value = it
                                },
                            ) {
                                Text("Save list")
                            }
                            TextButton(
                                onClick = {
                                },
                                modifier = Modifier.onGloballyPositioned {
                                    cancelCoordinates.value = it
                                },
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
        activity.setContentView(composeView)
        measureAt(composeView, activity, widthDp = 360, heightDp = 160)
        shadowOf(Looper.getMainLooper()).idle()
        measureAt(composeView, activity, widthDp = 360, heightDp = 160)

        assertEquals(composeView.height, viewportSize.value.height)
        assertTrue(scrollState.maxValue > 0)
        val initialSaveBounds = checkNotNull(saveCoordinates.value)
            .boundsInRootWithoutClipping()
        assertTrue(
            initialSaveBounds.bottom > viewportSize.value.height,
            "save=$initialSaveBounds, viewport=$viewportSize, scroll=${scrollState.value}",
        )

        runBlocking { scrollState.scrollTo(scrollState.maxValue) }
        Snapshot.sendApplyNotifications()
        measureComposeLayout(composeView)

        val saveBounds = checkNotNull(saveCoordinates.value).boundsInRootWithoutClipping()
        val cancelBounds = checkNotNull(cancelCoordinates.value).boundsInRootWithoutClipping()
        assertTrue(saveBounds.top >= 0f)
        assertTrue(
            saveBounds.bottom <= viewportSize.value.height.toFloat(),
            "save=$saveBounds, viewport=$viewportSize, scroll=${scrollState.value}, " +
                "max=${scrollState.maxValue}",
        )
        assertTrue(cancelBounds.top >= 0f)
        assertTrue(cancelBounds.bottom <= viewportSize.value.height.toFloat())

        runBlocking { scrollState.scrollTo(0) }
        Snapshot.sendApplyNotifications()
        measureComposeLayout(composeView)
        assertEquals(0, scrollState.value)
        val queryBounds = checkNotNull(queryCoordinates.value).boundsInRootWithoutClipping()
        assertTrue(queryBounds.top >= 0f)
        assertTrue(queryBounds.bottom <= viewportSize.value.height.toFloat())
    }

    @Test
    fun visibleColumnKeepsWeightedPreviewInRemainingHeight() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        this.activity = activity
        this.composeView = composeView
        val scrollState = ScrollState(0)
        val previewSize = mutableStateOf(IntSize.Zero)

        composeView.setContent {
            MaterialTheme {
                AppContentColumn(
                    scrollState = scrollState,
                    scrollable = false,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.height(40.dp))
                    Box(
                        Modifier.weight(1f).onSizeChanged { previewSize.value = it },
                    )
                    Box(Modifier.height(40.dp))
                }
            }
        }
        activity.setContentView(composeView)
        measureAt(composeView, activity, widthDp = 360, heightDp = 200)
        shadowOf(Looper.getMainLooper()).idle()
        measureAt(composeView, activity, widthDp = 360, heightDp = 200)

        val fixedChildHeightPx =
            (40.dp.value * activity.resources.displayMetrics.density).roundToInt()
        val expectedPreviewHeight = composeView.height - (2 * fixedChildHeightPx)
        assertEquals(expectedPreviewHeight, previewSize.value.height)
        assertEquals(0, scrollState.value)
    }

    private fun measureAt(
        composeView: ComposeView,
        activity: Activity,
        widthDp: Int,
        heightDp: Int,
    ) {
        val density = activity.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        composeView.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, widthPx, heightPx)
    }

    private fun measureComposeLayout(composeView: ComposeView) {
        val composeRoot = composeView.getChildAt(0) as ViewRootForTest
        composeRoot.measureAndLayoutForTest()
    }

    private fun LayoutCoordinates.boundsInRootWithoutClipping(): Rect {
        val position = positionInRoot()
        return Rect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height,
        )
    }
}
