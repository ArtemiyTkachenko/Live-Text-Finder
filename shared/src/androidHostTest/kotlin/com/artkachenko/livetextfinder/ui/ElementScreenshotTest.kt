package com.artkachenko.livetextfinder.ui

import androidx.compose.runtime.Composable
import com.artkachenko.livetextfinder.ScreenshotTier
import com.artkachenko.livetextfinder.ScreenshotVariant
import com.artkachenko.livetextfinder.assertScreenshot
import com.artkachenko.livetextfinder.camera.LiveCaptureActions
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.drainMainLooper
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_find_text
import livetextfinder.shared.generated.resources.search_list_delete
import livetextfinder.shared.generated.resources.status_camera_error_recovery
import livetextfinder.shared.generated.resources.status_cancel
import livetextfinder.shared.generated.resources.status_permission
import livetextfinder.shared.generated.resources.status_retry
import livetextfinder.shared.generated.resources.status_scanning
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElementScreenshotTest {
    @After
    fun tearDown() {
        drainMainLooper()
    }

    @Test
    fun primaryActionEnabled() = capture("finder-primary-action", "enabled") {
        FinderPrimaryAction(stringResource(Res.string.finder_find_text), true, onClick = ::ignore)
    }

    @Test
    fun primaryActionDisabled() = capture("finder-primary-action", "disabled") {
        FinderPrimaryAction(stringResource(Res.string.finder_find_text), false, onClick = ::ignore)
    }

    @Test
    fun primaryActionDestructive() = capture("finder-primary-action", "destructive") {
        FinderPrimaryAction(
            label = stringResource(Res.string.search_list_delete),
            enabled = true,
            destructive = true,
            onClick = ::ignore,
        )
    }

    @Test
    fun secondaryAction() = capture("finder-secondary-action", "enabled") {
        FinderSecondaryAction(stringResource(Res.string.status_cancel), onClick = ::ignore)
    }

    @Test
    fun contextMessageNormal() = capture("context-message", "normal") {
        ContextMessage(stringResource(Res.string.status_permission))
    }

    @Test
    fun contextMessageError() = capture("context-message", "error") {
        ContextMessage(stringResource(Res.string.status_camera_error_recovery), isError = true)
    }

    @Test
    fun contextMessageWithAction() = capture("context-message", "with-action") {
        ContextMessage(
            message = stringResource(Res.string.status_camera_error_recovery),
            actionLabel = stringResource(Res.string.status_retry),
            onAction = ::ignore,
        )
    }

    @Test
    fun statusSummaryWithStageBand() = capture("status-summary", "stage-band") {
        StatusSummary(
            stringResource(Res.string.status_scanning),
            details = null,
            onDetails = ::ignore,
            stageBand = true,
        )
    }

    @Test
    fun statusSummaryWithoutStageBand() = capture("status-summary", "standard") {
        StatusSummary(
            stringResource(Res.string.status_scanning),
            details = null,
            onDetails = ::ignore,
        )
    }

    @Test
    fun captureControlRow() {
        val controls = LiveCaptureControls({ true }, { true })
        val lease = controls.attach(UnusedCaptureActions, onDetach = ::ignore)
        controls.update(lease) {
            it.copy(ready = true, maxZoom = 4f, zoom = 2f, hasFlash = true, hasFocus = true)
        }
        capture("live-capture-control-row", "ready") {
            LiveCaptureControlRow(controls, contextEnabled = true)
        }
    }

    private fun capture(subject: String, state: String, content: @Composable () -> Unit) {
        assertScreenshot(ScreenshotTier.ELEMENT, subject, state, ScreenshotVariant.LIGHT, content)
    }

    private fun ignore() = Unit

    private object UnusedCaptureActions : LiveCaptureActions {
        override fun zoom(ratio: Float) = Unit

        override fun torch(enabled: Boolean) = Unit

        override fun focus() = Unit
    }
}
