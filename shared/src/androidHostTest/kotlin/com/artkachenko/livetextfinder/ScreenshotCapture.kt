package com.artkachenko.livetextfinder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.ui.LiveTextFinderTheme
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog

internal enum class ScreenshotTier(
    val directory: String,
    val heightDp: Int,
    val horizontalPaddingDp: Int,
) {
    ELEMENT("element", 112, 12),
    REGION("region", 560, 0),
    SCREEN("screen", 800, 0),
}

internal enum class ScreenshotVariant(val suffix: String, val dark: Boolean, val fontScale: Float) {
    LIGHT("light", false, 1f),
    DARK("dark", true, 1f),
    LIGHT_FONT_130("light-font130", false, 1.3f),
}

internal fun assertScreenshot(
    tier: ScreenshotTier,
    subject: String,
    state: String,
    variant: ScreenshotVariant,
    content: @Composable () -> Unit,
) {
    assertScreenshot(tier, subject, state, variant, false, emptyList(), content)
}

internal fun assertDialogScreenshot(
    subject: String,
    state: String,
    variant: ScreenshotVariant,
    onDialogReady: (SemanticsNode) -> Unit = {},
    content: @Composable () -> Unit,
) {
    assertScreenshot(
        ScreenshotTier.REGION, subject, state, variant, true, listOf(onDialogReady), content,
    )
}

internal fun assertAppScreenshot(
    state: String,
    variant: ScreenshotVariant,
    steps: List<(SemanticsNode) -> Unit>,
    content: @Composable () -> Unit,
) {
    assertScreenshot(ScreenshotTier.SCREEN, "app", state, variant, false, steps, content)
}

private fun assertScreenshot(
    tier: ScreenshotTier,
    subject: String,
    state: String,
    variant: ScreenshotVariant,
    requireDialog: Boolean,
    steps: List<(SemanticsNode) -> Unit>,
    content: @Composable () -> Unit,
) {
    val filename = "${subject}__${state}__${variant.suffix}.png"
    val bitmap = captureScreenshot(tier, variant, requireDialog, steps, content)
    try {
        compareScreenshot(bitmap, "${tier.directory}/$filename")
    } finally {
        bitmap.recycle()
    }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun compareScreenshot(bitmap: Bitmap, relativePath: String) {
    val baselines = File(checkNotNull(System.getProperty("ltf.screenshot.baselines")))
    val baseline = baselines.resolve(relativePath)
    val recording = System.getProperty("ltf.screenshot.record") == "true"
    if (recording) {
        checkNotNull(baseline.parentFile).mkdirs()
    } else if (!baseline.isFile) {
        throw AssertionError("Missing screenshot baseline: $baseline")
    }
    val output = File(checkNotNull(System.getProperty("ltf.screenshot.diffs")))
    bitmap.captureRoboImage(
        baseline.absolutePath,
        RoborazziOptions(
            taskType = if (recording) RoborazziTaskType.Record else RoborazziTaskType.Verify,
            compareOptions = RoborazziOptions.CompareOptions(
                outputDirectoryPath = output.absolutePath,
                imageComparator = SimpleImageComparator(maxDistance = 0f),
            ),
            recordOptions = RoborazziOptions.RecordOptions(resizeScale = 1.0),
        ),
    )
}

private fun captureScreenshot(
    tier: ScreenshotTier,
    variant: ScreenshotVariant,
    requireDialog: Boolean,
    steps: List<(SemanticsNode) -> Unit>,
    content: @Composable () -> Unit,
): Bitmap {
    val previousQualifiers = RuntimeEnvironment.getQualifiers()
    val previousFontScale = RuntimeEnvironment.getApplication().resources.configuration.fontScale
    val fullScreen = tier == ScreenshotTier.SCREEN
    val captureWindows = requireDialog || steps.isNotEmpty()
    val qualifiers = if (fullScreen) {
        val night = if (variant.dark) "night" else "notnight"
        "en-rUS-w360dp-h${tier.heightDp}dp-$night-mdpi"
    } else {
        "en-rUS-w360dp-h640dp-mdpi"
    }
    RuntimeEnvironment.setQualifiers(qualifiers)
    RuntimeEnvironment.setFontScale(variant.fontScale)
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val view = ComposeView(activity)
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
    val recomposer = Recomposer(scope.coroutineContext)
    var backgroundColor = 0
    try {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.setParentCompositionContext(recomposer)
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, variant.fontScale)) {
                LiveTextFinderTheme(darkThemeOverride = variant.dark, dynamicColor = false) {
                    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
                    SideEffect { backgroundColor = surfaceColor }
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.padding(horizontal = tier.horizontalPaddingDp.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            content()
                        }
                    }
                }
            }
        }
        activity.setContentView(view)
        Snapshot.sendApplyNotifications()
        val root = view.getChildAt(0) as ViewRootForTest
        var previousWindow: Window? = null
        var stepIndex = 0
        var nextActionFrame = if (steps.isEmpty()) 0 else SETTLE_FRAMES
        repeat((steps.size + 3) * SETTLE_FRAMES) { frame ->
            clock.sendFrame(1_000_000_000L + frame * 16_000_000L)
            drainMainLooper()
            layoutScreenshotView(view, tier.heightDp)
            root.measureAndLayoutForTest()
            val window = if (captureWindows) {
                ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window
            } else {
                null
            }
            val dialogRoot = window?.let {
                if (previousWindow != window) {
                    window.setLayout(360, tier.heightDp)
                    previousWindow = window
                }
                layoutScreenshotView(window.decorView, tier.heightDp)
                findComposeRoot(window.decorView)?.apply { measureAndLayoutForTest() }
            }
            if ((requireDialog || window != null) && dialogRoot == null) return@repeat
            // Advance the test clock between steps so window transitions and layout can settle.
            if (frame < nextActionFrame) return@repeat
            if (stepIndex < steps.size) {
                val activeRoot = dialogRoot ?: root
                steps[stepIndex++](activeRoot.semanticsOwner.rootSemanticsNode)
                Snapshot.sendApplyNotifications()
                nextActionFrame = frame + SETTLE_FRAMES
                return@repeat
            }
            if (steps.isNotEmpty() && clock.hasAwaiters) return@repeat
            if (recomposer.currentState.value == Recomposer.State.Idle) {
                val bitmap = captureScreenshotBitmap(view) { canvas ->
                    if (window != null) {
                        if (fullScreen) canvas.drawWindowDim(window)
                        window.decorView.draw(canvas)
                    }
                }
                if (fullScreen) return bitmap
                try {
                    return bitmap.cropToContent(backgroundColor)
                } finally {
                    bitmap.recycle()
                }
            }
        }
        error("Screenshot composition did not settle")
    } finally {
        if (captureWindows) {
            ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.dismiss()
        }
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        controller.pause().stop().destroy()
        RuntimeEnvironment.setQualifiers(previousQualifiers)
        RuntimeEnvironment.setFontScale(previousFontScale)
    }
}

internal fun captureScreenshotBitmap(view: View, drawOverlay: (Canvas) -> Unit = {}): Bitmap {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        drawOverlay(canvas)
        return bitmap
    } catch (failure: Throwable) {
        bitmap.recycle()
        throw failure
    }
}

internal fun visualOutputDirectory(): File? = System.getenv("LTF_VISUAL_OUTPUT_DIR")?.let(::File)

internal fun writeVisualScreenshot(bitmap: Bitmap, fileName: String): Boolean {
    val directory = visualOutputDirectory() ?: return true
    val output = directory.resolve(fileName)
    output.parentFile?.mkdirs()
    return output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

internal fun writeVisualScreenshot(view: View, fileName: String): Boolean {
    val bitmap = captureScreenshotBitmap(view)
    try {
        return writeVisualScreenshot(bitmap, fileName)
    } finally {
        bitmap.recycle()
    }
}

private fun Canvas.drawWindowDim(window: Window) {
    val attributes = window.attributes
    if (attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
        drawColor(Color.argb((attributes.dimAmount * 255).roundToInt(), 0, 0, 0))
    }
}

private fun layoutScreenshotView(view: View, heightDp: Int) {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightDp, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
}

private fun findComposeRoot(view: View): ViewRootForTest? = when (view) {
    is ViewRootForTest -> view
    is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { index ->
        findComposeRoot(view.getChildAt(index))
    }
    else -> null
}

private const val SETTLE_FRAMES = 32
private const val SCREENSHOT_GUTTER_PX = 4

private fun Bitmap.cropToContent(backgroundColor: Int): Bitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (pixels[y * width + x] != backgroundColor) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
    }
    check(right >= left && bottom >= top) { "Screenshot contains only its background" }

    // Remove equal margins so a shifted component still differs from its baseline.
    val trimX = (minOf(left, width - 1 - right) - SCREENSHOT_GUTTER_PX).coerceAtLeast(0)
    val trimY = (minOf(top, height - 1 - bottom) - SCREENSHOT_GUTTER_PX).coerceAtLeast(0)
    val cropWidth = width - 2 * trimX
    val cropHeight = height - 2 * trimY
    if (cropWidth == width && cropHeight == height) {
        return checkNotNull(copy(Bitmap.Config.ARGB_8888, false))
    }
    return Bitmap.createBitmap(this, trimX, trimY, cropWidth, cropHeight)
}
