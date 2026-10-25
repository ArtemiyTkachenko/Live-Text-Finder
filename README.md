# Live Text Finder

Android-first Kotlin Multiplatform / Compose Multiplatform camera and OCR foundation.

The shared screen shows a rear camera preview, OCR status, recognized line count, frame dimensions/rotation and a short text sample. Recognition uses bundled on-device Latin-script ML Kit. This increment does not implement queries, matching or highlighting.

## Structure

- `androidApp`: Activity and Android manifest.
- `shared/commonMain`: Compose application UI and platform-neutral text/geometry/frame snapshots.
- `shared/androidMain`: camera permission UI, CameraX session ownership, ML Kit analysis and result conversion.
- `shared/iosMain`: existing framework entry point and explicit unsupported-camera placeholder.
- `docs`: [architecture](docs/architecture.md), [implementation plan](docs/implementation-plan.md), [dependency versions](docs/dependencies.md), [validation](docs/validation.md), [independent reviews](docs/review-workflow.md).

## Build and check

Use Android Studio with the Android SDK installed and `local.properties` pointing to it. The existing wrapper uses JDK 21 daemon criteria. Run:

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

These checks compile the application without generating an APK. APK builds are left to the user.

User-built debug APK location: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

For a connected authorized Android phone, install and launch:

```sh
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.artkachenko.livetextfinder/.MainActivity
```

Tap **Allow camera**, grant access and point the rear camera at readable printed Latin text. See the validation checklist for denial/settings recovery, rotation and background/foreground checks. Build and host tests cannot prove camera behavior or OCR quality on a physical phone.
