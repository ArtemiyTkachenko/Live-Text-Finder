package com.artkachenko.livetextfinder

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlinx.coroutines.runBlocking
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_options
import livetextfinder.shared.generated.resources.finder_options_help
import livetextfinder.shared.generated.resources.finder_options_search_group
import livetextfinder.shared.generated.resources.help_title
import livetextfinder.shared.generated.resources.inspection_lists
import livetextfinder.shared.generated.resources.search_lists_heading
import livetextfinder.shared.generated.resources.status_permission
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenScreenshotTest {
    @Before
    fun denyCamera() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
    }

    @After
    fun tearDown() {
        drainMainLooper()
    }

    @Test
    fun permissionRequired() {
        for (variant in listOf(ScreenshotVariant.LIGHT, ScreenshotVariant.DARK)) {
            assertAppScreenshot(
                "camera-permission-required",
                variant,
                steps = listOf(expect(Res.string.status_permission)),
            ) {
                App()
            }
        }
    }

    @Test
    fun listManagerOpen() = capture(
        "list-manager-open",
        click(Res.string.inspection_lists),
        expect(Res.string.search_lists_heading),
    )

    @Test
    fun optionsOpen() = capture(
        "options-open",
        click(Res.string.finder_options),
        expect(Res.string.finder_options_search_group),
    )

    @Test
    fun helpOpen() = capture(
        "help-open",
        click(Res.string.finder_options),
        ::expandOptions,
        ::scrollOptionsToBottom,
        click(Res.string.finder_options_help),
        expect(Res.string.help_title),
    )

    private fun capture(state: String, vararg steps: (SemanticsNode) -> Unit) {
        assertAppScreenshot(state, ScreenshotVariant.LIGHT, steps.toList()) { App() }
    }

    private fun click(resource: StringResource): (SemanticsNode) -> Unit = { root ->
        val label = runBlocking { getString(resource) }
        val action = nodes(root).filter { it.hasText(label) }
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsActions.OnClick)?.action }
        checkNotNull(action) { "No screenshot action for '$label'" }
        check(action()) { "Screenshot action '$label' did not run" }
    }

    private fun expect(resource: StringResource): (SemanticsNode) -> Unit = { root ->
        val label = runBlocking { getString(resource) }
        check(nodes(root).any { it.hasText(label) }) { "Expected screenshot text '$label'" }
    }

    private fun expandOptions(root: SemanticsNode) {
        val expand = nodes(root).firstNotNullOfOrNull {
            it.config.getOrNull(SemanticsActions.Expand)?.action
        }
        if (expand != null) {
            check(expand())
        }
    }

    private fun scrollOptionsToBottom(root: SemanticsNode) {
        val scroll = nodes(root).firstNotNullOf {
            it.config.getOrNull(SemanticsActions.ScrollBy)?.action
        }
        check(scroll(0f, 10_000f))
    }

    private fun SemanticsNode.hasText(label: String): Boolean =
        config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true

    private fun nodes(root: SemanticsNode): Sequence<SemanticsNode> = sequence {
        yield(root)
        root.children.forEach { child -> yieldAll(nodes(child)) }
    }
}
