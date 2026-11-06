package com.artkachenko.livetextfinder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.ui.LiveTextFinderTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveSearchAreaDialogTest {
    private var activity: ComponentActivity? = null
    private var composeView: ComposeView? = null

    @After
    fun disposeComposition() {
        composeView?.disposeComposition()
        activity?.finish()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun productionDialogAppliesSliderValuesAndCancelKeepsCurrentArea() {
        val fixture = mountDialog(NormalizedSearchArea.CENTERED_DEFAULT)
        val root = semanticsRoot()

        assertTrue(texts(root).containsAll(listOf("Width: 60%", "Height: 60%")))
        assertTrue(texts(root).contains("Horizontal position: 50%"))
        assertTrue(texts(root).contains("Vertical position: 50%"))
        assertNotNull(find(root) { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Width")
        })

        setProgress(root, "Width", 80f)
        Snapshot.sendApplyNotifications()
        dialogRoot().measureAndLayoutForTest()
        click(semanticsRoot(), "Apply")
        val appliedArea = checkNotNull(fixture.first.area)
        assertEquals(0.8f, appliedArea.right - appliedArea.left, 0.0001f)
        val appliedTop = appliedArea.top

        fixture.third.value = false
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
        fixture.third.value = true
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
        dialogRoot().measureAndLayoutForTest()
        setProgress(semanticsRoot(), "Vertical position", 30f)
        Snapshot.sendApplyNotifications()
        dialogRoot().measureAndLayoutForTest()
        click(semanticsRoot(), "Cancel")
        val canceledArea = checkNotNull(fixture.first.area)
        assertEquals(0.8f, canceledArea.right - canceledArea.left, 0.0001f)
        assertEquals(appliedTop, canceledArea.top)
    }

    @Test
    fun fullFrameActionAndConstrainedDialogKeepActionsSemanticallyReachable() {
        val fixture = mountDialog(NormalizedSearchArea.CENTERED_DEFAULT, heightDp = 240)
        val root = semanticsRoot()
        assertTrue(hasScrollAction(root))
        val scrollNode = checkNotNull(find(root) { node ->
            node.config.getOrNull(SemanticsActions.ScrollBy) != null
        })
        assertTrue(
            checkNotNull(scrollNode.config.getOrNull(SemanticsActions.ScrollBy)?.action)
                .invoke(0f, 320f),
        )
        Snapshot.sendApplyNotifications()
        dialogRoot().measureAndLayoutForTest()
        listOf("Width", "Height", "Horizontal position", "Vertical position").forEach { label ->
            assertNotNull(find(semanticsRoot()) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(label)
            })
        }
        setProgress(semanticsRoot(), "Vertical position", 70f)
        Snapshot.sendApplyNotifications()
        dialogRoot().measureAndLayoutForTest()
        assertTrue(
            buttonBounds(semanticsRoot(), "Full frame").bottom <=
                (dialogRoot() as View).height.toFloat(),
        )
        click(semanticsRoot(), "Full frame")
        assertNull(fixture.first.area)
        assertEquals(2L, fixture.first.revision)
    }

    @Test
    fun schematicGuideFillsFrameAtFullSizeUsingProductionNormalizedArea() {
        mountDialog(NormalizedSearchArea.CENTERED_DEFAULT)
        var root = semanticsRoot()
        setProgressAndWait("Width", 100f)
        setProgressAndWait("Height", 100f)
        setProgressAndWait("Horizontal position", 0f)
        setProgressAndWait("Vertical position", 100f)

        root = semanticsRoot()
        val frame = checkNotNull(find(root) { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == "area-guide-frame"
        })
        val region = checkNotNull(find(root) { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == "area-guide-region"
        })
        assertEquals(frame.positionInRoot.x, region.positionInRoot.x, 1f)
        assertEquals(frame.positionInRoot.y, region.positionInRoot.y, 1f)
        assertEquals(
            frame.positionInRoot.x + frame.size.width,
            region.positionInRoot.x + region.size.width,
            1f,
        )
        assertEquals(
            frame.positionInRoot.y + frame.size.height,
            region.positionInRoot.y + region.size.height,
            1f,
        )
        val description = checkNotNull(
            frame.config.getOrNull(SemanticsProperties.ContentDescription),
        ).single()
        assertTrue(description.contains("Schematic search area: 100%"))
        val expectedBounds = "Left 0%; top 0%; right 100%; bottom 100%"
        assertTrue(texts(root).contains(expectedBounds))
        assertTrue(description.endsWith(expectedBounds))
        captureDialog("live-area-guide-light-full-frame.png")
    }

    @Test
    fun schematicGuideMeasuresTwentyPercentAreaAtOppositeEdges() {
        mountDialog(NormalizedSearchArea.CENTERED_DEFAULT, darkTheme = true)
        var root = semanticsRoot()
        setProgressAndWait("Width", 20f)
        setProgressAndWait("Height", 20f)
        setProgressAndWait("Horizontal position", 0f)
        setProgressAndWait("Vertical position", 100f)

        root = semanticsRoot()
        val frame = checkNotNull(find(root) { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == "area-guide-frame"
        })
        val region = checkNotNull(find(root) { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == "area-guide-region"
        })
        assertEquals(frame.positionInRoot.x, region.positionInRoot.x, 1f)
        assertEquals(
            frame.positionInRoot.y + frame.size.height,
            region.positionInRoot.y + region.size.height,
            1f,
        )
        assertEquals(frame.size.width * 0.2f, region.size.width.toFloat(), 1.5f)
        assertEquals(frame.size.height * 0.2f, region.size.height.toFloat(), 1.5f)
        val expectedBounds = "Left 0%; top 80%; right 20%; bottom 100%"
        assertTrue(texts(root).contains(expectedBounds))
        val description = checkNotNull(
            frame.config.getOrNull(SemanticsProperties.ContentDescription),
        ).single()
        assertTrue(description.endsWith(expectedBounds))
        captureDialog("live-area-guide-dark-edge-frame.png")
    }

    @Test
    fun applyUsesCurrentSliderStateAtInvocation() {
        val currentFixture = mountDialog(NormalizedSearchArea.CENTERED_DEFAULT)
        setProgressAndWait("Width", 80f)
        setProgressAndWait("Horizontal position", 0f)
        setProgressAndWait("Vertical position", 100f)
        click(semanticsRoot(), "Apply")
        val applied = checkNotNull(currentFixture.first.area)
        assertEquals(0f, applied.left, 0.0001f)
        assertEquals(0.8f, applied.right - applied.left, 0.0001f)
        assertEquals(1f, applied.bottom, 0.0001f)

    }

    @Test
    fun staleRevisionCannotOverwriteNewerArea() {
        val fixture = mountDialog(NormalizedSearchArea.CENTERED_DEFAULT)
        val staleRevision = fixture.first.revision
        val winner = NormalizedSearchArea.fromSizeAndPosition(40, 50, 25, 60)
        assertTrue(fixture.first.apply(staleRevision, winner))
        click(semanticsRoot(), "Apply")
        assertEquals(winner, fixture.first.area)
    }

    @Test
    fun compactLargeTextDialogKeepsSlidersAndActionsAtAccessibleHeights() {
        mountDialog(
            NormalizedSearchArea.CENTERED_DEFAULT,
            heightDp = 420,
            widthDp = 320,
            fontScale = 1.5f,
        )
        val viewRoot = dialogRoot()
        val density = checkNotNull(activity).resources.displayMetrics.density
        val title = checkNotNull(find(semanticsRoot()) { node ->
            node.config.getOrNull(SemanticsProperties.Text)
                ?.any { it.text == "Live search area" } == true
        })
        assertEquals(1.5f, title.layoutInfo.density.fontScale)
        listOf("Width", "Height", "Horizontal position", "Vertical position").forEach { label ->
            scrollNodeIntoViewport(label)
            val slider = checkNotNull(find(semanticsRoot()) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(label)
            })
            assertTrue(
                slider.size.height / density >= 48f,
                "Slider $label measures ${slider.size.height}px at density $density.",
            )
            assertTrue(slider.positionInRoot.y >= 0f)
            assertTrue(
                slider.positionInRoot.y + slider.size.height <= (viewRoot as View).height,
            )
        }
        val actions = listOf("Apply", "Cancel", "Full frame").map { label ->
            checkNotNull(find(semanticsRoot()) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            })
        }
        actions.forEach { action ->
            assertTrue(action.size.height / density >= 48f)
            assertTrue(action.positionInRoot.y >= 0f)
            assertTrue(
                action.positionInRoot.y + action.size.height <= (viewRoot as View).height,
            )
        }
        assertTrue(actions.first().size.height / density >= 52f)
        actions.forEachIndexed { index, action ->
            actions.drop(index + 1).forEach { other ->
                assertTrue(
                    action.positionInRoot.x + action.size.width <= other.positionInRoot.x ||
                        other.positionInRoot.x + other.size.width <= action.positionInRoot.x ||
                        action.positionInRoot.y + action.size.height <= other.positionInRoot.y ||
                        other.positionInRoot.y + other.size.height <= action.positionInRoot.y,
                )
            }
        }
        captureDialog("live-area-compact-large-text.png")
    }

    private fun scrollNodeIntoViewport(label: String) {
        repeat(8) {
            val root = semanticsRoot()
            val slider = checkNotNull(find(root) { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(label)
            })
            val viewport = checkNotNull(find(root) { node ->
                node.config.getOrNull(SemanticsActions.ScrollBy) != null
            })
            val top = viewport.positionInRoot.y
            val bottom = top + viewport.size.height
            val sliderTop = slider.positionInRoot.y
            val sliderBottom = sliderTop + slider.size.height
            if (sliderTop >= top && sliderBottom <= bottom) {
                return
            }
            val delta = if (sliderBottom > bottom) sliderBottom - bottom else sliderTop - top
            checkNotNull(viewport.config.getOrNull(SemanticsActions.ScrollBy)?.action)
                .invoke(0f, delta)
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            dialogRoot().measureAndLayoutForTest()
        }
        val root = semanticsRoot()
        val slider = checkNotNull(find(root) { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(label)
        })
        val viewport = checkNotNull(find(root) { node ->
            node.config.getOrNull(SemanticsActions.ScrollBy) != null
        })
        error(
            "Could not scroll '$label'; slider y=${slider.positionInRoot.y}, " +
                "viewport y=${viewport.positionInRoot.y}, height=${viewport.size.height}.",
        )
    }

    private fun setProgressAndWait(label: String, value: Float) {
        setProgress(semanticsRoot(), label, value)
        Snapshot.sendApplyNotifications()
        repeat(8) {
            shadowOf(Looper.getMainLooper()).idle()
            dialogRoot().measureAndLayoutForTest()
            val expectedText = "$label: ${value.toInt()}%"
            if (texts(semanticsRoot()).contains(expectedText)) {
                return
            }
        }
        error("Slider '$label' did not update to ${value.toInt()}% in the dialog.")
    }

    private fun mountDialog(
        initialArea: NormalizedSearchArea?,
        heightDp: Int = 480,
        widthDp: Int = 360,
        fontScale: Float = 1f,
        darkTheme: Boolean = false,
    ): Triple<LiveSearchAreaOwner, ComposeView, androidx.compose.runtime.MutableState<Boolean>> {
        RuntimeEnvironment.setFontScale(fontScale)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(host)
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        activity = host
        composeView = view
        val owner = LiveSearchAreaOwner()
        if (initialArea != null) {
            owner.apply(owner.revision, initialArea)
        }
        val show = mutableStateOf(true)
        view.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale),
            ) {
                LiveTextFinderTheme(darkThemeOverride = darkTheme, dynamicColor = false) {
                    if (show.value) {
                        LiveSearchAreaDialog(
                            area = owner.area,
                            revision = owner.revision,
                            owner = owner,
                            onDismiss = { show.value = false },
                        )
                    }
                }
            }
        }
        host.setContentView(view)
        val density = host.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        view.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = checkNotNull(ShadowDialog.getLatestDialog())
        dialog.window?.setLayout(widthPx, heightPx)
        shadowOf(Looper.getMainLooper()).idle()
        dialogRoot().measureAndLayoutForTest()
        return Triple(owner, view, show)
    }

    private fun semanticsRoot(): SemanticsNode = dialogRoot().semanticsOwner.rootSemanticsNode

    private fun dialogRoot(): ViewRootForTest {
        val decor = checkNotNull(checkNotNull(ShadowDialog.getLatestDialog()).window?.decorView)
        return checkNotNull(findViewRoot(decor))
    }

    private fun findViewRoot(view: View?): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findViewRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun nodes(root: SemanticsNode): Sequence<SemanticsNode> = sequence {
        yield(root)
        root.children.forEach { child -> yieldAll(nodes(child)) }
    }

    private fun texts(root: SemanticsNode): Set<String> = nodes(root).flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().asSequence().map { it.text }
    }.toSet()

    private fun find(root: SemanticsNode, predicate: (SemanticsNode) -> Boolean): SemanticsNode? =
        nodes(root).firstOrNull(predicate)

    private fun click(root: SemanticsNode, text: String) {
        val node = checkNotNull(find(root) { current ->
            current.config.getOrNull(SemanticsProperties.Text)
                ?.any { it.text == text } == true
        })
        checkNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action).invoke()
    }

    private fun setProgress(root: SemanticsNode, label: String, value: Float) {
        val node = checkNotNull(find(root) { current ->
            current.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(label)
        })
        checkNotNull(node.config.getOrNull(SemanticsActions.SetProgress)?.action).invoke(value)
    }

    private fun hasScrollAction(root: SemanticsNode): Boolean = nodes(root).any { node ->
        node.config.getOrNull(SemanticsActions.ScrollBy) != null
    }

    private fun buttonBounds(root: SemanticsNode, text: String) = checkNotNull(find(root) { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }).boundsInRoot

    private fun captureDialog(fileName: String) {
        val outputDirectory = System.getenv("LTF_VISUAL_OUTPUT_DIR") ?: return
        val decor = checkNotNull(
            checkNotNull(ShadowDialog.getLatestDialog()).window?.decorView,
        )
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val output = File(outputDirectory, fileName)
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        bitmap.recycle()
    }
}
