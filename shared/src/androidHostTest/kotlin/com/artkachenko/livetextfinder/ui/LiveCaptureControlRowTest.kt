package com.artkachenko.livetextfinder.ui

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import com.artkachenko.livetextfinder.camera.LiveCaptureActions
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.writeVisualScreenshot
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveCaptureControlRowTest {
    private var activity: ComponentActivity? = null
    private var view: ComposeView? = null
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private var frameTime = 1_000_000_000L

    @After
    fun close() {
        view?.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun supportedControlsFit320DpWithLargeTextExposeStateAndKeepTransientBounds() {
        val enabled = mutableStateOf(true)
        val actions = Actions()
        val controls = LiveCaptureControls({ enabled.value }, { enabled.value })
        val lease = controls.attach(actions) {
        }
        controls.update(lease) {
            it.copy(ready = true, minZoom = 1f, maxZoom = 4f, hasFlash = true, hasFocus = true)
        }
        mount(1.5f) {
            LiveTextFinderTheme(darkThemeOverride = false, dynamicColor = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LiveCaptureControlRow(controls, enabled.value)
                }
            }
        }
        val initial = nodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
        assertEquals(4, initial.size)
        initial.forEach { node ->
            val density = node.layoutInfo.density.density
            assertTrue(node.size.width / density >= 48f)
            assertTrue(node.size.height / density >= 48f)
            assertTrue(node.positionInRoot.x >= 0f)
            assertTrue(node.positionInRoot.x + node.size.width <= checkNotNull(view).width)
        }
        assertTrue(disabled(node("Zoom out")))
        assertFalse(disabled(node("Zoom in")))
        assertEquals(
            ToggleableState.Off,
            node("Light off").config[SemanticsProperties.ToggleableState],
        )
        assertTrue(nodes().any {
            it.config.getOrNull(SemanticsProperties.Text)
                ?.any { text -> text.text == "1.0×" } == true
        })
        render("capture-controls-light-320-font150.png")
        click(node("Zoom in"))
        assertEquals(listOf(1.25f), actions.zooms)
        click(node("Light off"))
        assertEquals(listOf(true), actions.torches)
        controls.update(lease) { it.copy(torchOn = true, zoom = 4f) }
        settle()
        assertEquals(
            ToggleableState.On,
            node("Light on").config[SemanticsProperties.ToggleableState],
        )
        assertTrue(disabled(node("Zoom in")))
        val bounds = nodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null }
            .map { it.positionInRoot to it.size }
        controls.update(lease) { it.copy(ready = false) }
        settle()
        assertEquals(bounds, nodes().filter {
            it.config.getOrNull(SemanticsActions.OnClick) != null
        }.map { it.positionInRoot to it.size })
        assertTrue(disabled(node("Focus")))
        render("capture-controls-disabled-320-font150.png")
    }

    @Test
    fun unsupportedControlsAreAbsentAndOldUiLeaseOrLatestContextCannotDispatch() {
        val context = mutableStateOf(true)
        val controls = LiveCaptureControls({ context.value }, { context.value })
        val oldActions = Actions()
        val first = controls.attach(oldActions) {
        }
        mount(1f) {
            LiveTextFinderTheme(darkThemeOverride = true, dynamicColor = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LiveCaptureControlRow(controls, context.value)
                }
            }
        }
        assertTrue(nodes().none { it.config.getOrNull(SemanticsActions.OnClick) != null })
        controls.update(first) { it.copy(ready = true, minZoom = 1f, maxZoom = 2f) }
        settle()
        assertTrue(nodes().none {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Focus") == true
        })
        val staleClick = checkNotNull(node("Zoom in").config[SemanticsActions.OnClick].action)
        // Source, editor, freeze and visibility changes are supplied as latest context by App.
        context.value = false
        staleClick()
        assertTrue(oldActions.zooms.isEmpty())
        context.value = true
        val replacementActions = Actions()
        val second = controls.attach(replacementActions) {
        }
        controls.update(second) { it.copy(ready = true, minZoom = 1f, maxZoom = 3f) }
        staleClick()
        assertTrue(replacementActions.zooms.isEmpty())
        settle()
        click(node("Zoom in"))
        assertEquals(listOf(1.25f), replacementActions.zooms)
        controls.update(second) { it.copy(zoomPending = true) }
        settle()
        assertTrue(disabled(node("Zoom in")))
        render("capture-controls-dark-pending.png")
    }

    private fun mount(fontScale: Float, content: @Composable () -> Unit) {
        RuntimeEnvironment.setFontScale(fontScale)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val compose = ComposeView(host)
        compose.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        compose.setParentCompositionContext(recomposer)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(host.resources.displayMetrics.density, fontScale),
            ) {
                content()
            }
        }
        activity = host
        view = compose
        host.setContentView(compose)
        val density = host.resources.displayMetrics.density
        compose.measure(
            View.MeasureSpec.makeMeasureSpec(
                (320 * density).roundToInt(), View.MeasureSpec.EXACTLY,
            ),
            View.MeasureSpec.makeMeasureSpec(
                (100 * density).roundToInt(), View.MeasureSpec.EXACTLY,
            ),
        )
        compose.layout(0, 0, compose.measuredWidth, compose.measuredHeight)
        settle()
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        repeat(32) {
            clock.sendFrame(frameTime)
            frameTime += 16_000_000L
            shadowOf(Looper.getMainLooper()).idle()
            root().measureAndLayoutForTest()
            if (recomposer.currentState.value == Recomposer.State.Idle) return
        }
        error("Capture controls did not settle")
    }

    private fun root(): ViewRootForTest = checkNotNull(view).getChildAt(0) as ViewRootForTest

    private fun nodes(): List<SemanticsNode> {
        val nodes = mutableListOf<SemanticsNode>()
        fun visit(node: SemanticsNode) {
            nodes += node
            node.children.forEach(::visit)
        }
        visit(root().semanticsOwner.rootSemanticsNode)
        return nodes
    }

    private fun node(description: String): SemanticsNode = nodes().single {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true
    }

    private fun disabled(node: SemanticsNode): Boolean =
        node.config.contains(SemanticsProperties.Disabled)

    private fun click(node: SemanticsNode) {
        assertTrue(checkNotNull(node.config[SemanticsActions.OnClick].action)())
        settle()
    }

    private fun render(name: String) {
        assertTrue(writeVisualScreenshot(checkNotNull(view), name))
    }

    private class Actions : LiveCaptureActions {
        val zooms = mutableListOf<Float>()
        val torches = mutableListOf<Boolean>()
        override fun zoom(ratio: Float) {
            zooms += ratio
        }
        override fun torch(enabled: Boolean) {
            torches += enabled
        }
        override fun focus() = Unit
    }
}
