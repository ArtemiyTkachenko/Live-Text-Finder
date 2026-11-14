package com.artkachenko.livetextfinder.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.artkachenko.livetextfinder.camera.LiveCaptureActions
import com.artkachenko.livetextfinder.camera.LiveCaptureControls
import com.artkachenko.livetextfinder.drainMainLooper
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaEditorOverlay
import com.artkachenko.livetextfinder.scanarea.LiveSearchAreaState
import com.artkachenko.livetextfinder.scanarea.ScanAreaEditBar
import com.artkachenko.livetextfinder.screen.FinderLayoutMode
import com.artkachenko.livetextfinder.screen.FinderSourceContext
import com.artkachenko.livetextfinder.screen.FloatingLiveScanner
import com.artkachenko.livetextfinder.screen.FloatingRetainedControls
import com.artkachenko.livetextfinder.search.ElementOutline
import com.artkachenko.livetextfinder.search.InMemorySearchSessionStore
import com.artkachenko.livetextfinder.search.MatchOutlineBounds
import com.artkachenko.livetextfinder.search.NormalizedSearchArea
import com.artkachenko.livetextfinder.search.PreviewPoint
import com.artkachenko.livetextfinder.search.SearchSessionController
import com.artkachenko.livetextfinder.search.drawHighlightOutlines
import com.artkachenko.livetextfinder.search.drawSelectedOccurrenceMarker
import com.artkachenko.livetextfinder.status.SearchFeedback
import com.artkachenko.livetextfinder.status.SearchFeedbackKind
import com.artkachenko.livetextfinder.status.StatusSource
import kotlin.math.roundToInt
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.inspection_lists
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

    @Test
    fun preparingShare() = capture(AssemblyState.Imported, sharePreparing = true)

    private fun capture(state: AssemblyState, sharePreparing: Boolean = false) {
        val receipt = createReceipt()
        try {
            assertScreenshot(
                ScreenshotTier.SCREEN,
                "assembled-finder",
                if (sharePreparing) "share-preparing" else state.fixtureName,
                ScreenshotVariant.LIGHT,
            ) {
                AssembledFinder(state, receipt, sharePreparing)
            }
        } finally {
            receipt.bitmap.recycle()
        }
    }

    @Composable
    private fun AssembledFinder(
        state: AssemblyState,
        receipt: ReceiptFixture,
        sharePreparing: Boolean,
    ) {
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
            onQueryChange = {},
            searchListsLabel = stringResource(Res.string.inspection_lists),
            onSearchLists = ::ignore,
        )
        if (layoutMode.isLiveCamera) {
            val controls = remember {
                LiveCaptureControls({ true }, { true }).apply {
                    val lease = attach(NoCaptureActions, ::ignore)
                    update(lease) { it.copy(ready = true, maxZoom = 4f, hasFlash = true) }
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val draft = areaEditor.editorDraft
                ReceiptPreview(
                    receipt.bitmap.asImageBitmap(),
                    if (draft == null) receipt.matches else emptyList(),
                    isStill = false,
                    selected = false,
                )
                if (draft != null) {
                    LiveSearchAreaEditorOverlay(
                        area = checkNotNull(draft.area),
                        viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight),
                        onAreaChange = { areaEditor.updateEditor(draft.token, it) },
                        modifier = Modifier.matchParentSize(),
                    )
                    Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) {
                        FixtureScanAreaBar(areaEditor)
                    }
                } else {
                    FloatingLiveScanner(
                        searchBar = searchBar,
                        liveControls = controls,
                        captureEnabled = true,
                        freezePending = false,
                        onFreeze = ::ignore,
                        onCancelFreeze = ::ignore,
                        areaEnabled = true,
                        onArea = ::ignore,
                        importEnabled = true,
                        importPending = false,
                        onImport = ::ignore,
                        onCancelImport = ::ignore,
                        onMatchMode = {},
                        onOptions = ::ignore,
                        statusLabel = sourceStatusMessage(
                            StatusSource.LiveCamera,
                            SearchFeedback(SearchFeedbackKind.Matches, receipt.matches.size),
                            scanning = true,
                        ),
                        onStatus = ::ignore,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
            return
        }
        val controls = remember { LiveCaptureControls({ false }, { false }) }
        Box(Modifier.fillMaxSize().background(FinderCameraOverlay.viewportBackground)) {
            FloatingLiveScanner(
                searchBar = searchBar,
                liveControls = controls,
                captureEnabled = false,
                freezePending = false,
                onFreeze = ::ignore,
                onCancelFreeze = ::ignore,
                areaEnabled = false,
                onArea = ::ignore,
                importEnabled = false,
                importPending = false,
                onImport = ::ignore,
                onCancelImport = ::ignore,
                onMatchMode = {},
                onOptions = ::ignore,
                statusLabel = sourceStatusMessage(
                    state.source,
                    SearchFeedback(SearchFeedbackKind.Matches, receipt.matches.size),
                ),
                onStatus = ::ignore,
                snackbarHostState = remember { SnackbarHostState() },
                retained = FloatingRetainedControls(
                    zoom = 1f,
                    canZoom = true,
                    position = 1,
                    matchCount = receipt.matches.size,
                    canNavigate = true,
                    canCopy = true,
                    sharePreparing = sharePreparing,
                    onZoom = {},
                    onPrevious = ::ignore,
                    onNext = ::ignore,
                    onCopy = ::ignore,
                    onShare = ::ignore,
                    onCancelShare = ::ignore,
                    onCamera = ::ignore,
                ),
                retainedViewport = { modifier ->
                    Box(modifier) {
                        ReceiptPreview(
                            image = receipt.bitmap.asImageBitmap(),
                            matches = receipt.matches,
                            isStill = true,
                            selected = state == AssemblyState.Imported,
                        )
                    }
                },
            )
        }
    }

    @Composable
    private fun FixtureScanAreaBar(areaEditor: LiveSearchAreaState) {
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
        ScanAreaEditBar(areaEditor, checkNotNull(areaEditor.editorDraft).token, context, floating = true)
    }

    private object NoCaptureActions : LiveCaptureActions {
        override fun zoom(ratio: Float) = Unit

        override fun torch(enabled: Boolean) = Unit

        override fun focus() = Unit
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
