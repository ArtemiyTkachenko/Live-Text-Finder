package com.artkachenko.livetextfinder.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.artkachenko.livetextfinder.ocr.OcrStatus
import com.artkachenko.livetextfinder.ocr.OcrUiState
import com.artkachenko.livetextfinder.ui.FinderPrimaryAction
import livetextfinder.shared.generated.resources.Res
import livetextfinder.shared.generated.resources.camera_preview_paused
import livetextfinder.shared.generated.resources.camera_preview_starting
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun LiveSearchAreaLifecycle(onReset: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestReset = rememberUpdatedState(onReset)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                latestReset.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            latestReset.value()
        }
    }
}

internal actual fun liveSearchAreaAvailable(): Boolean = true

@Composable
internal actual fun CameraPreview(
    modifier: Modifier,
    owner: FrozenFrameOwner,
    isContentVisible: () -> Boolean,
    onState: (OcrUiState) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val report by rememberUpdatedState(onState)
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var permissionRevision by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        requested = true
        permissionRevision++
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.hasCameraPermission()
                permissionRevision++
            }
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val activity = context.findActivity()
    val settingsRequired = remember(granted, requested, activity, permissionRevision) {
        !granted && requested && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.CAMERA
        )
    }
    LaunchedEffect(granted, foreground, settingsRequired) {
        if (!granted) report(
            OcrUiState(
                OcrStatus.PermissionRequired,
                if (settingsRequired) "Camera access is disabled. Enable it in app settings."
                else "Allow camera access to scan printed Latin text.",
            )
        ) else if (!foreground) report(
            OcrUiState(
                OcrStatus.Paused, "Camera paused while the app is in the background."
            )
        )
    }
    when {
        owner.state.value.mode == FreezeMode.Frozen -> {
            FrozenBitmapPreview(owner, modifier, isContentVisible)
        }

        !granted -> Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Camera access is needed for live text recognition.", color = Color.White)
                FinderPrimaryAction(
                    label = if (settingsRequired) "Open app settings" else "Allow camera",
                    enabled = true,
                    onClick = {
                        if (settingsRequired) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        } else {
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    },
                )
            }
        }

        foreground -> AndroidCameraFeed(modifier, owner, onState)
        else -> Box(modifier)
    }
}

@Composable
private fun AndroidCameraFeed(
    modifier: Modifier,
    frameOwner: FrozenFrameOwner,
    onState: (OcrUiState) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val report by rememberUpdatedState(onState)
    var attempt by remember { mutableIntStateOf(0) }
    // A retried/replaced session gets a fresh native transform and stream state.
    val preview = remember(context, lifecycleOwner, attempt) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(preview, lifecycleOwner, attempt) {
        failed = false
        report(OcrUiState(OcrStatus.Starting, "Starting rear camera and bundled text recognizer…"))
        val session = AndroidCameraSession(
            context = context,
            owner = lifecycleOwner,
            view = preview,
            onCaptured = frameOwner::accept,
            onCaptureFailure = frameOwner::fail,
        ) { state ->
            failed = state.status == OcrStatus.Error || state.status == OcrStatus.Unavailable
            report(state)
        }
        frameOwner.attachSession(session::requestCapture, session::cancelCapture)
        session.start()
        onDispose {
            frameOwner.detachSession()
            session.close()
        }
    }
    Box(modifier) {
        key(preview) {
            AndroidView(factory = { preview }, modifier = Modifier.matchParentSize())
        }
        CameraPreviewFeedbackLayer(preview.previewStreamState, failed) { attempt++ }
    }
}

@Composable
internal fun CameraPreviewFeedbackLayer(
    streamState: LiveData<PreviewView.StreamState>,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var feedback by remember(streamState) {
        mutableStateOf(PreviewStreamFeedback.Starting)
    }
    DisposableEffect(streamState, lifecycleOwner) {
        val streamObserver = PreviewStreamFeedbackObserver { feedback = it }
        streamObserver.observe(streamState, lifecycleOwner)
        onDispose { streamObserver.close() }
    }
    Box(Modifier.fillMaxSize()) {
        if (shouldShowStreamFeedback(feedback, failed)) {
            CameraStreamFeedback(feedback)
        }
        if (failed) {
            FinderPrimaryAction(
                label = "Retry camera",
                enabled = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .heightIn(min = CAMERA_RETRY_MIN_HEIGHT_DP.dp),
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun CameraStreamFeedback(feedback: PreviewStreamFeedback) {
    val message = when (feedback) {
        PreviewStreamFeedback.Starting -> stringResource(Res.string.camera_preview_starting)
        PreviewStreamFeedback.Paused -> stringResource(Res.string.camera_preview_paused)
        PreviewStreamFeedback.Streaming -> return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(message, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
