# Live Text Finder

Android-first Kotlin Multiplatform / Compose Multiplatform camera and OCR foundation.

The shared screen shows a rear camera preview, a manual query field with Android save/restore and named multi-term search lists, recognized match/line status, frame dimensions/rotation and a short text sample. Recognition uses bundled on-device Latin-script ML Kit. Shared literal matching counts recognized occurrences; matched OCR elements receive clipped yellow outlines with a black under-stroke using the actual Android preview transform. Missing/invalid mapping suppresses outlines, and results expire after 1,500ms without a fresh receipt. Individual missing match regions can remain briefly (up to 300ms after first miss, capped by original result freshness), while fresh detections update immediately. The camera preview and secondary views are hidden while the keyboard is open or the layout is compact. Preview shrinking is deferred. Automated/source evidence is recorded; real-device alignment and runtime acceptance remain pending. See [matching requirements](docs/search-requirements.md) for exact behavior and pending manual validation.

Android saves only the manual query, named lists and active selection in app-private storage. Each list entry is an independent literal term or phrase. Open **Search lists** to create, edit, select or delete a list; typing returns to the preserved manual search. Saving and recovery feedback reflects storage completion. iOS keeps these preferences in memory. OCR text and camera images are never saved.

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

## Project agent workflow

Project roles are `planner`, `implementer` and independent `reviewer`, configured in `.codex/agents/`. The [workflow protocol](docs/agent-workflow/protocol.md) defines root approval, one bounded correction cycle and a stop after acceptance; the coordinating chat owns dispatch and local persistent state (`docs/agent-workflow/state.json`). CS-003 independent source review completed with runtime limitations. Later product actions require explicit bounded scope; physical-device validation remains distinct. Generated stage evidence and persistent state are local, Git-ignored records, and may be absent from a fresh checkout. The versioned requirements, architecture and validation summary carry durable project context.
