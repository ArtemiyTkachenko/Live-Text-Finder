package com.artkachenko.livetextfinder

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.artkachenko.livetextfinder.search.LiveSearchAreaOwner
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    private fun mountDialog(
        initialArea: NormalizedSearchArea?,
        heightDp: Int = 480,
    ): Triple<LiveSearchAreaOwner, ComposeView, androidx.compose.runtime.MutableState<Boolean>> {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(host)
        activity = host
        composeView = view
        val owner = LiveSearchAreaOwner()
        if (initialArea != null) {
            owner.apply(owner.revision, initialArea)
        }
        val show = mutableStateOf(true)
        view.setContent {
            MaterialTheme {
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
        host.setContentView(view)
        val density = host.resources.displayMetrics.density
        val widthPx = (360 * density).toInt()
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
        val decor = checkNotNull(ShadowDialog.getLatestDialog()).window?.decorView
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
}
