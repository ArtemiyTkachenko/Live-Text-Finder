# Live Text Finder

Android-first Kotlin Multiplatform / Compose Multiplatform camera and OCR foundation.

The shared screen shows a rear camera preview, a manual query field with Android save/restore and named multi-term search lists, source-aware match/status feedback and optional Recognition details with line/outline counts, dimensions/rotation and a short text sample. Recognition uses bundled on-device Latin-script ML Kit. Shared literal matching counts recognized occurrences; manual matches use the selected colour (Yellow by default), while each saved-list entry gets a deterministic, separately labelled colour. Coincident matches show each owner's colour around the same OCR polygon over a black under-stroke. Missing/invalid mapping suppresses outlines, and results expire after 1,500ms without a fresh receipt. Individual missing match regions can remain briefly (up to 300ms after first miss, capped by original result freshness), while fresh detections update immediately. The camera preview and secondary views are hidden while the keyboard is open or the layout is compact. Preview shrinking is deferred. Automated/source evidence is recorded; real-device alignment and runtime acceptance remain pending. See [matching requirements](docs/search-requirements.md) for exact behavior and pending manual validation.

Android saves the manual query, named lists, active selection and manual highlight colour in app-private storage. Each list entry is an independent literal term or phrase. Open **Search lists** to create, edit, select or delete a list. Enter one term or phrase per line; commas stay inside an entry. The editor shows a live count and a preview of the first five normalized entries before saving. Typing in the manual query returns to the preserved manual search. **Search results** shows every active term and its current count. A deterministic 32-colour palette assigns one labelled colour per list entry; assignments survive adding/removing other entries, reordering, rename/case/whitespace edits and reopening. Saving and recovery feedback reflects storage completion. iOS keeps these preferences in memory. OCR text and retained source images are never persisted. Explicit sharing creates a temporary annotated PNG as described below.

Open **Options → Matching** to choose **Substring** (the default) or **Whole words**. Both modes search literal text within each normalized OCR line. Whole words requires the match's outer neighbors to be absent or punctuation, whitespace or symbols; letters, all Unicode number categories, combining marks and underscore join a word. Android saves this choice with the search session; iOS keeps it in memory. Help describes the boundary rule.

Freeze captures one next analyzed image with that same frame’s OCR result. The full captured buffer is fitted upright, so its framing can differ from the live preview. Queries and lists remain editable against the frozen image; **Resume live** returns to scanning. Frozen pixels stay in memory while editing and are discarded on resume, backgrounding or Activity recreation. The retained source stays in memory; explicit sharing can create a temporary annotated derivative. Device capture/color/alignment checks remain pending.

Use **Options → Import image** to import one JPEG, PNG or WebP image through the system picker, including without camera permission. Imported pixels are sampled to at most 4,000,000 pixels, normalized for EXIF orientation and recognized on-device. Queries and lists search the retained image until **Return to camera**; backgrounding or Activity recreation discards it. Imported source images and their text are never persisted; explicit sharing may create a temporary annotated PNG. Image-provider, picker and native OCR device checks remain pending.

On a retained frozen/imported image, use **Zoom in**, **Zoom out**, pinch and drag to inspect details, or **Reset view** to return to the fitted image. The image and its highlights move together. Source replacement or viewport resizing resets the view; annotated sharing still uses the full image. **Matches** lets you select an occurrence and **Copy text**. The copied text preserves OCR casing with normalized whitespace. Changing the image or search clears the selection. Matches remains available when the existing compact/keyboard layout hides the preview.

Use **Share image** on a frozen/imported image to prepare an upright PNG with current per-term colours (or the selected manual colour) over black highlights and open Android’s share sheet. Changing the image, search or colour, or backgrounding before handoff, cancels preparation. The app confirms only that the share sheet opened. Shared PNGs use a bounded private temporary cache and read-only content URI; handed-off files survive chooser return and become eligible for cleanup after 24 hours. No gallery saving or share history is added. Device chooser/receiver checks remain pending.

Open **Options → Highlight color** to choose Yellow, Cyan, Magenta or White, or open **Custom color** to adjust red, green and blue with a swatch and hex preview. The selected colour controls manual search; saved-list entries use their own assigned colours. **Apply** commits the custom choice; Cancel or dismissal leaves the current colour unchanged. Android remembers the manual colour alongside saved searches; iOS keeps it in memory. Preview outlines update immediately, and new shared images use the active search colours. Changing the effective colour cancels a share that is still being prepared.

Use **Options → Live area: Full frame** in live camera mode to restrict matches to fully contained words or phrases in an adjustable rectangle. **Full frame** restores normal searching. The area does not crop captured images; starting Freeze/Import or leaving the app resets it, and still images use their whole source.

Use **Options → Help** for search/list instructions, preview visibility, still-image sharing and platform limitations. The dialog is manual and can be reopened without changing your search.

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

Project roles are `planner`, `implementer` and independent `reviewer`, configured in `.codex/agents/`. The [workflow protocol](docs/agent-workflow/protocol.md) defines root approval, one bounded correction cycle and a stop after acceptance. The coordinating chat owns dispatch and task context; handoffs and session state stay in chats, outside the repository. Product work requires explicit bounded scope, and physical-device validation remains distinct. Versioned requirements, architecture and validation summaries document the product; historical stage evidence remains local and Git-ignored.
