# Camera / OCR foundation

## Boundaries

`androidApp` hosts shared `App`. `commonMain` owns the screen, diagnostic state, and immutable OCR result types. An expect/actual camera composable supplies a native preview and reports state; all camera and OCR implementation details stay in `androidMain`. `iosMain` renders an explicit Android-only notice.

Android owns a session containing PreviewView, camera use cases, and one bundled Latin recognizer. Use CameraX MlKitAnalyzer in original image coordinates, wrapped only to capture matching frame metadata. Copy text, line/element bounds and corner points before publishing; do not leak ML Kit/Android objects. Geometry uses the rotated input image coordinate system, never preview coordinates. Record raw size, rotation, crop, timestamp and sensor-to-buffer matrix. Future highlights will require a separately tested preview transform.

## Resource and concurrency decisions

Bind rear preview and analysis together to the Compose lifecycle owner. KEEP_ONLY_LATEST retains at most the newest waiting frame; MlKitAnalyzer owns closing submitted ImageProxy instances after asynchronous recognition. Do not launch per-frame coroutines or queue recognition tasks. Ignore results after disposal or when the owner is not started. Unbind only owned use cases and close the recognizer after the current analysis completes.

Permissions are requested through Android Activity Result APIs following an explicit button tap. Denial keeps an explanation and retry; permanent denial offers system settings. Recheck permission on resume. Surface setup, camera and recognition errors with recovery.

No image/text persistence, text logging, search, overlays or iOS camera functionality.

## Primary references

- https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html
- https://developer.android.com/media/camera/camerax/mlkitanalyzer
- https://developer.android.com/media/camera/camerax/analyze
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://developer.android.com/training/permissions/requesting
