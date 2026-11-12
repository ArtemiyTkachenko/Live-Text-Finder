package com.artkachenko.livetextfinder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.artkachenko.livetextfinder.ui.LiveTextFinderTheme
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

internal enum class ScreenshotTier(val directory: String, val heightDp: Int) {
    ELEMENT("element", 112),
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
    val filename = "${subject}__${state}__${variant.suffix}.png"
    val bitmap = captureScreenshot(tier, variant, content)
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
    content: @Composable () -> Unit,
): Bitmap {
    val previousQualifiers = RuntimeEnvironment.getQualifiers()
    val previousFontScale = RuntimeEnvironment.getApplication().resources.configuration.fontScale
    RuntimeEnvironment.setQualifiers("en-rUS-w360dp-h640dp-mdpi")
    RuntimeEnvironment.setFontScale(variant.fontScale)
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val view = ComposeView(activity)
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + clock)
    val recomposer = Recomposer(scope.coroutineContext)
    try {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.setParentCompositionContext(recomposer)
        view.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, variant.fontScale)) {
                LiveTextFinderTheme(darkThemeOverride = variant.dark, dynamicColor = false) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp),
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
        repeat(32) { frame ->
            clock.sendFrame(1_000_000_000L + frame * 16_000_000L)
            drainMainLooper()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(tier.heightDp, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            root.measureAndLayoutForTest()
            if (recomposer.currentState.value == Recomposer.State.Idle) {
                return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    .also { view.draw(Canvas(it)) }
            }
        }
        error("Screenshot composition did not settle")
    } finally {
        view.disposeComposition()
        recomposer.cancel()
        scope.cancel()
        controller.pause().stop().destroy()
        RuntimeEnvironment.setQualifiers(previousQualifiers)
        RuntimeEnvironment.setFontScale(previousFontScale)
    }
}
