package com.artkachenko.livetextfinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.ScreenshotTier
import com.artkachenko.livetextfinder.ScreenshotVariant
import com.artkachenko.livetextfinder.assertScreenshot
import com.artkachenko.livetextfinder.drainMainLooper
import com.artkachenko.livetextfinder.status.SearchFeedback
import com.artkachenko.livetextfinder.status.SearchFeedbackKind
import com.artkachenko.livetextfinder.status.StatusSource
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.finder_options
import livetextfinder.shared.generated.resources.inspection_lists
import livetextfinder.shared.generated.resources.scan_area_badge
import livetextfinder.shared.generated.resources.scanner_camera
import livetextfinder.shared.generated.resources.scanner_content_copy
import livetextfinder.shared.generated.resources.scanner_crop_free
import livetextfinder.shared.generated.resources.scanner_freeze
import livetextfinder.shared.generated.resources.scanner_image
import livetextfinder.shared.generated.resources.scanner_import
import livetextfinder.shared.generated.resources.scanner_list
import livetextfinder.shared.generated.resources.scanner_pause
import livetextfinder.shared.generated.resources.scanner_photo_camera
import livetextfinder.shared.generated.resources.scanner_settings
import livetextfinder.shared.generated.resources.scanner_share
import livetextfinder.shared.generated.resources.still_match_copy_short
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
class FloatingScannerScreenshotTest {
    @After
    fun tearDown() {
        drainMainLooper()
    }

    @Test
    fun roundIconButton() = capture("floating-scanner-icon-button") {
        FloatingScannerIconButton(
            icon = Res.drawable.scanner_settings,
            label = stringResource(Res.string.finder_options),
            onClick = ::ignore,
        )
    }

    @Test
    fun statusPill() = capture("floating-scanner-status-pill") {
        FloatingScannerStatusPill(
            label = sourceStatusMessage(
                StatusSource.LiveCamera,
                SearchFeedback(SearchFeedbackKind.Matches, matchCount = 2),
                scanning = true,
            ),
            onClick = ::ignore,
        )
    }

    @Test
    fun actionItem() = capture("floating-scanner-action-item") {
        // A bar item inherits the common scrim from the bar.
        Box(
            Modifier.width(82.dp).background(FinderCameraOverlay.controlScrim, CircleShape),
        ) {
            FloatingScannerActionItem(listsAction(), Modifier.fillMaxWidth())
        }
    }

    @Test
    fun actionBar() = capture("floating-scanner-action-bar", largeText = true) {
        FloatingScannerActionBar(
            actions = listOf(listsAction(), scanAreaAction(), freezeAction(), importAction()),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Test
    fun retainedActionBar() = capture(
        "floating-scanner-action-bar",
        largeText = true,
        state = "retained",
    ) {
        FloatingScannerActionBar(
            actions = listOf(
                listsAction(),
                FloatingScannerAction(
                    Res.drawable.scanner_content_copy,
                    stringResource(Res.string.still_match_copy_short),
                    ::ignore,
                ),
                FloatingScannerAction(
                    Res.drawable.scanner_share,
                    stringResource(Res.string.scanner_share),
                    ::ignore,
                ),
                FloatingScannerAction(
                    Res.drawable.scanner_photo_camera,
                    stringResource(Res.string.scanner_camera),
                    ::ignore,
                ),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Test
    fun searchField() = capture("floating-scanner-search-field", largeText = true) {
        FloatingScannerSearchField(
            query = "invoice",
            onQueryChange = {},
            wholeWords = true,
            onWholeWordsChange = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Test
    fun searchFieldSubstring() = capture("floating-scanner-search-field", state = "substring") {
        FloatingScannerSearchField(
            query = "",
            onQueryChange = {},
            wholeWords = false,
            onWholeWordsChange = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Test
    fun zoomPill() = capture("floating-scanner-zoom-pill") {
        FloatingScannerZoomPill(
            zoomLabel = "1.0×",
            zoomOutEnabled = false,
            zoomInEnabled = true,
            onZoomOut = ::ignore,
            onZoomIn = ::ignore,
        )
    }

    @Test
    fun matchPill() = capture("floating-scanner-match-pill") {
        FloatingScannerMatchPill(
            position = 1,
            count = 2,
            previousEnabled = false,
            nextEnabled = true,
            onPrevious = ::ignore,
            onNext = ::ignore,
        )
    }

    private fun capture(
        subject: String,
        largeText: Boolean = false,
        state: String? = null,
        content: @Composable () -> Unit,
    ) {
        val variants = if (largeText) ScreenshotVariant.entries else {
            listOf(ScreenshotVariant.LIGHT, ScreenshotVariant.DARK)
        }
        for (backdrop in Backdrop.entries) {
            for (variant in variants) {
                val name = listOfNotNull(state, backdrop.suffix).joinToString("-")
                assertScreenshot(ScreenshotTier.ELEMENT, subject, name, variant) {
                    val horizontalPadding = if (largeText || state == "substring") 0.dp else 8.dp
                    Box(
                        Modifier.background(backdrop.color)
                            .padding(horizontal = horizontalPadding, vertical = 8.dp),
                    ) {
                        content()
                    }
                }
            }
        }
    }

    @Composable
    private fun listsAction() = FloatingScannerAction(
        Res.drawable.scanner_list,
        stringResource(Res.string.inspection_lists),
        ::ignore,
    )

    @Composable
    private fun scanAreaAction() = FloatingScannerAction(
        Res.drawable.scanner_crop_free,
        stringResource(Res.string.scan_area_badge),
        ::ignore,
    )

    @Composable
    private fun freezeAction() = FloatingScannerAction(
        Res.drawable.scanner_pause,
        stringResource(Res.string.scanner_freeze),
        ::ignore,
    )

    @Composable
    private fun importAction() = FloatingScannerAction(
        Res.drawable.scanner_image,
        stringResource(Res.string.scanner_import),
        ::ignore,
    )

    private fun ignore() = Unit

    private enum class Backdrop(val suffix: String, val color: Color) {
        White("white-backdrop", Color.White),
        Dark("dark-backdrop", Color(0xFF30373C)),
    }
}
