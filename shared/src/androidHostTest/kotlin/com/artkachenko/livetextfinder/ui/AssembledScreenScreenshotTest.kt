package com.artkachenko.livetextfinder.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.artkachenko.livetextfinder.ScreenshotTier
import com.artkachenko.livetextfinder.ScreenshotVariant
import com.artkachenko.livetextfinder.assertScreenshot
import com.artkachenko.livetextfinder.camera.FrozenFrameOwner
import com.artkachenko.livetextfinder.camera.ImportedImageOwner
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.drainMainLooper
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaEditorOverlay
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaState
import com.artkachenko.livetextfinder.scanarea.ScanAreaEditBar
import com.artkachenko.livetextfinder.screen.FinderLayoutMode
import com.artkachenko.livetextfinder.screen.FinderSourceContext
import com.artkachenko.livetextfinder.screen.FinderTopRegion
import com.artkachenko.livetextfinder.search.ElementOutline
import com.artkachenko.livetextfinder.search.InMemorySearchSessionStore
import com.artkachenko.livetextfinder.search.MatchOutlineBounds
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.SearchListTransferState
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.drawSelectedOccurrenceMarker
import com.artkachenko.livetextfinder.status.RecognitionDetails
import com.artkachenko.livetextfinder.status.SearchFeedback
import com.artkachenko.livetextfinder.status.SearchFeedbackKind
import com.artkachenko.livetextfinder.status.StatusSource
import kotlin.math.roundToInt
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.capture_freeze
import livetextfinder.shared.generated.resources.capture_return_to_camera
import livetextfinder.shared.generated.resources.inspection_lists
import livetextfinder.shared.generated.resources.inspection_results
import livetextfinder.shared.generated.resources.inspection_return_frozen
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
class AssembledScreenScreenshotTest {
    @After
    fun tearDown() {
        drainMainLooper()
    }

    @Test
    fun liveFrameWithMatches() = capture(AssemblyState.LiveMatches)

    @Test
    fun frozenFrame() = capture(AssemblyState.Frozen)

    @Test
    fun importedImage() = capture(AssemblyState.Imported)

    @Test
    fun scanAreaEditing() = capture(AssemblyState.AreaEditing)

    private fun capture(state: AssemblyState) {
        val receipt = createReceipt()
        try {
            assertScreenshot(
                ScreenshotTier.SCREEN,
                "assembled-finder",
                state.fixtureName,
                ScreenshotVariant.LIGHT,
            ) {
                AssembledFinder(state, receipt)
            }
        } finally {
            receipt.bitmap.recycle()
        }
    }

    @Composable
    private fun AssembledFinder(state: AssemblyState, receipt: ReceiptFixture) {
        val layoutMode = state.layoutMode
        val areaEditor = remember {
            LiveSearchAreaState().apply {
                if (layoutMode == FinderLayoutMode.AreaEditing) {
                    beginEditor(NormalizedSearchArea(0.13f, 0.15f, 0.87f, 0.61f))
                }
            }
        }
        val searchBar = FinderSearchBar(
            query = "invoice",
            onQueryChange = { _ -> Unit },
            searchListsLabel = stringResource(Res.string.inspection_lists),
            onSearchLists = ::ignore,
        )
        val resultsLabel = stringResource(Res.string.inspection_results, receipt.matches.size)
        FinderContentAssembly(
            scrollState = rememberScrollState(),
            scrollable = false,
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            topContent = {
                FinderTopRegion(
                    layoutMode = layoutMode,
                    shouldShowViews = true,
                    searchBar = searchBar,
                    inlineResultsLabel = resultsLabel,
                    transferState = SearchListTransferState(),
                    onCancelTransfer = ::ignore,
                    onOptions = ::ignore,
                    status = { FixtureStatus(state, receipt) },
                )
            },
            managerContent = { Unit },
            viewportContent = {
                val draft = areaEditor.editorDraft
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth()
                        .background(FinderCameraOverlay.viewportBackground),
                ) {
                    ReceiptPreview(
                        image = receipt.bitmap.asImageBitmap(),
                        matches = if (draft == null) receipt.matches else emptyList(),
                        isStill = layoutMode == FinderLayoutMode.StillInspection,
                        selected = state == AssemblyState.Imported,
                    )
                    if (draft != null) {
                        LiveSearchAreaEditorOverlay(
                            area = draft.area ?: NormalizedSearchArea(0f, 0f, 1f, 1f),
                            viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight),
                            onAreaChange = { areaEditor.updateEditor(draft.token, it) },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            },
            footerContent = {
                if (layoutMode == FinderLayoutMode.AreaEditing) {
                    val context = remember {
                        FinderSourceContext(
                            session = SearchSessionController(InMemorySearchSessionStore()),
                            importedOwner = ImportedImageOwner(),
                            frozenOwner = FrozenFrameOwner(),
                            liveCaptureControls = LiveCaptureControls({ true }, { false }),
                            isContentVisible = { true },
                            isCaptureContextCurrent = { false },
                        )
                    }
                    ScanAreaEditBar(areaEditor, checkNotNull(areaEditor.editorDraft).token, context)
                } else {
                    FixtureDock(state, searchBar, resultsLabel, receipt.matches.size)
                }
            },
        )
    }

    @Composable
    private fun FixtureStatus(state: AssemblyState, receipt: ReceiptFixture) {
        val source = state.source
        StatusSummary(
            message = sourceStatusMessage(
                source = source,
                feedback = SearchFeedback(SearchFeedbackKind.Matches, receipt.matches.size),
                scanning = source == StatusSource.LiveCamera,
            ),
            details = RecognitionDetails(
                source = source,
                recognizedLineCount = RECEIPT_LINE_COUNT,
                outlinedElementCount = receipt.matches.size,
                imageWidth = receipt.bitmap.width,
                imageHeight = receipt.bitmap.height,
                rotationDegrees = 0.takeUnless { source == StatusSource.ImportedImage },
                textSample = "INVOICE 0427",
                textUnavailable = false,
                retainedOutlines = false,
                sourceMessage = null,
            ),
            onDetails = ::ignore,
            stageBand = state.layoutMode.isLiveCamera,
        )
    }

    @Composable
    private fun FixtureDock(
        state: AssemblyState,
        searchBar: FinderSearchBar,
        resultsLabel: String,
        matchCount: Int,
    ) {
        val isStill = state.layoutMode == FinderLayoutMode.StillInspection
        if (isStill) {
            Column(Modifier.padding(horizontal = FinderSpacing.large)) {
                StillInspectionControls(
                    matchCount = matchCount,
                    showMatchesAction = false,
                    zoom = 1f,
                    canZoom = true,
                    onZoomIn = ::ignore,
                    onZoomOut = ::ignore,
                    onReset = ::ignore,
                    onMatches = ::ignore,
                )
                if (state == AssemblyState.Imported) {
                    StillMatchNavigationControls(
                        position = 1,
                        count = matchCount,
                        matchedTerm = "invoice",
                        matchedText = "INVOICE",
                        canLocate = true,
                        onPrevious = ::ignore,
                        onNext = ::ignore,
                        onCopy = ::ignore,
                    )
                }
            }
        }
        val returnLabel = when (state) {
            AssemblyState.Frozen -> stringResource(Res.string.inspection_return_frozen)
            AssemblyState.Imported -> stringResource(Res.string.capture_return_to_camera)
            else -> null
        }
        FinderLiveDock(
            search = searchBar,
            capture = FinderCaptureActions(
                captureLabel = if (isStill) null else stringResource(Res.string.capture_freeze),
                captureEnabled = !isStill,
                onCapture = ::ignore,
                pendingCaptureLabel = null,
                onCancelCapture = ::ignore,
                secondaryActionLabel = null,
                onSecondaryAction = ::ignore,
            ),
            resultsLabel = resultsLabel,
            returnLabel = returnLabel,
            onCancelShare = ::ignore,
        )
    }

    @Composable
    private fun ReceiptPreview(
        image: ImageBitmap,
        matches: List<MatchOutlineBounds>,
        isStill: Boolean,
        selected: Boolean,
    ) {
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            val sourceSize = Size(image.width.toFloat(), image.height.toFloat())
            val scaleMode = if (isStill) ContentScale.Fit else ContentScale.Crop
            val scale = scaleMode.computeScaleFactor(sourceSize, size)
            val width = (sourceSize.width * scale.scaleX).roundToInt()
            val height = (sourceSize.height * scale.scaleY).roundToInt()
            val left = ((size.width - width) / 2f).roundToInt()
            val top = ((size.height - height) / 2f).roundToInt()
            drawImage(image, dstOffset = IntOffset(left, top), dstSize = IntSize(width, height))
            val projected = matches.map { bounds ->
                MatchOutlineBounds(
                    left = left + bounds.left * scale.scaleX,
                    top = top + bounds.top * scale.scaleY,
                    right = left + bounds.right * scale.scaleX,
                    bottom = top + bounds.bottom * scale.scaleY,
                )
            }
            drawHighlightOutlines(
                projected.mapIndexed { index, bounds ->
                    ElementOutline(
                        lineIndex = index,
                        elementIndex = 0,
                        points = listOf(
                            PreviewPoint(bounds.left, bounds.top),
                            PreviewPoint(bounds.right, bounds.top),
                            PreviewPoint(bounds.right, bounds.bottom),
                            PreviewPoint(bounds.left, bounds.bottom),
                        ),
                    )
                },
            )
            if (selected) {
                drawSelectedOccurrenceMarker(projected.first())
            }
        }
    }

    private fun createReceipt(): ReceiptFixture {
        val bitmap = Bitmap.createBitmap(360, 560, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0xff485c58.toInt())
        paint.color = 0xff293c37.toInt()
        canvas.drawRoundRect(31f, 92f, 337f, 478f, 4f, 4f, paint)
        paint.color = 0xfffbf6e9.toInt()
        canvas.drawRoundRect(27f, 86f, 330f, 470f, 4f, 4f, paint)
        paint.color = 0xff26352f.toInt()
        paint.textSize = 24f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        canvas.drawText("INVOICE 0427", 48f, 150f, paint)
        val heading = matchBounds(paint, "INVOICE", 150f)
        paint.textSize = 17f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText("North Market Stationery", 48f, 181f, paint)
        paint.strokeWidth = 1f
        canvas.drawLine(48f, 200f, 309f, 200f, paint)
        canvas.drawText("Notebook", 48f, 239f, paint)
        canvas.drawText("12.00", 256f, 239f, paint)
        canvas.drawText("Desk pad", 48f, 272f, paint)
        canvas.drawText("8.50", 266f, 272f, paint)
        canvas.drawLine(48f, 292f, 309f, 292f, paint)
        canvas.drawText("Total", 48f, 326f, paint)
        canvas.drawText("20.50", 256f, 326f, paint)
        canvas.drawText("Invoice reference: 0427", 48f, 412f, paint)
        return ReceiptFixture(bitmap, listOf(heading, matchBounds(paint, "Invoice", 412f)))
    }

    private fun matchBounds(paint: Paint, word: String, baseline: Float): MatchOutlineBounds =
        MatchOutlineBounds(
            left = 45f,
            top = baseline + paint.ascent(),
            right = 50f + paint.measureText(word),
            bottom = baseline + paint.descent(),
        )

    private fun ignore() = Unit

    private class ReceiptFixture(val bitmap: Bitmap, val matches: List<MatchOutlineBounds>)

    private enum class AssemblyState(
        val fixtureName: String,
        val layoutMode: FinderLayoutMode,
        val source: StatusSource,
    ) {
        LiveMatches("live-matches", FinderLayoutMode.Live, StatusSource.LiveCamera),
        Frozen("frozen-frame", FinderLayoutMode.StillInspection, StatusSource.FrozenFrame),
        Imported("imported-image", FinderLayoutMode.StillInspection, StatusSource.ImportedImage),
        AreaEditing("scan-area-editing", FinderLayoutMode.AreaEditing, StatusSource.LiveCamera),
    }

    private companion object {
        const val RECEIPT_LINE_COUNT = 6
    }
}
