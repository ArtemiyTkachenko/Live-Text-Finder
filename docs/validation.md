# Validation

## Constrained editor reachability — correction, 2026-10-27

Source audit identified a short-height failure path: the non-scrolling controls could consume the entire post-IME viewport before the weighted list editor. Hidden-preview mode now scrolls the controls and editor together, with finite viewport measurement and system/IME insets outside scrolling. Normal visible camera layout and the user's preview-hiding policy remain unchanged. No further defect in highlight timing, retention or coordinate admission was established by this audit; device observations remain necessary for those behaviours.

Final checks passed in 21s: Android compilation, iOS simulator shared compilation, **165 host tests with zero failures/errors/skips**, and lint with **zero errors, 13 warnings**. The two added regressions measure the actual production Compose container in a real ComposeView: Save/Cancel scroll fully into a 160dp viewport, scrolling back reaches the query, and normal mode preserves the weighted preview's exact remaining height. They use Compose's existing synchronous test-layout API after real scroll/snapshot updates, reading live coordinates without manually substituting scroll offsets. Earlier failed fixture/compiler/scheduling attempts are not passing regression evidence. No dependencies were added.

Fresh independent source-first review: **PASS WITH LIMITATIONS**, no actionable findings. Scoped manual Kotlin inspection, limited static audit and whitespace checks passed; no configured Kotlin formatter exists. The fixture does not exercise the full App editor fields, mode transitions, large-font/error content or focus. Physical-device keyboard, landscape, large-font and accessibility checks remain pending. No APK/device/external share was performed. Raw logs and generated reports are excluded from commits; historical evidence remains unchanged.

## Manual in-app guidance — 2026-10-27

The reopenable Help dialog explains existing search/list matching, hidden preview, frozen/imported image retention, global colour, sharing and platform limits. Opening/dismissing it changes only ephemeral UI state. No first-run flag, new dependency or persistence change was added. The related reset teaser now mentions Yellow.

Checks passed in 22s: Android compilation, iOS simulator shared compilation, **163 host tests with zero failures/errors/skips**, and lint with **zero errors, 13 warnings**. Fresh source-first independent review: **PASS WITH LIMITATIONS**, no actionable findings. Source inspection traces help statements to matching, source/lifecycle and platform implementations, and verifies scrollable content and separate dismissal wiring. Static copy did not require a new test framework or mirrored string tests. Scoped manual Kotlin inspection, a limited static audit and whitespace checks passed; no configured Kotlin formatter exists.

Device layout, large-font scrolling, screen-reader behaviour, keyboard preservation and Back/outside dismissal remain unobserved. No APK, device run or external sharing was performed. Logs and per-run reports remain outside the committed tree; historical evidence is unchanged.

## Global highlight appearance — 2026-10-27

Yellow, Cyan, Magenta and White are global highlight choices across live, frozen and imported previews and newly shared PNGs. Android schema v1 preferences load as Yellow without an eager rewrite; schema v2 persists the choice. Invalid preferences remain protected by the existing recovery flow. Colour changes preserve matching identity and cancel pending sharing before handoff.

Final checks passed in 13s: Android compilation, iOS simulator shared compilation, **163 host tests with zero failures/errors/skips**, and Android lint with **zero errors, 13 warnings**. All 153 previous tests remain. New evidence covers actual storage migration/strict rejection, asynchronous preference ordering/reset, all palette foreground and black under-stroke pixels through production preview helpers, production PNG encoding/provider reads, exact source pixel preservation, and actual colour changes before rendering or before handoff. An earlier failed fixture setup is not passing evidence.

Independent correction verification: **PASS WITH LIMITATIONS**, no remaining actionable findings. The accepted corrections added missing black/source-pixel assertions, reconciled standing documentation and fixed scoped test indentation. No configured Kotlin formatter exists; scoped manual inspection and a limited static audit found no remaining introduced issues. Raw logs and per-run reports remain outside the committed tree; historical evidence is preserved.

Device acceptance remains pending: dialog visibility/selection/accessibility, appearance across camera/frozen/imported modes, preference restoration, and chooser/recipient behaviour after colour changes. Host rendering does not prove physical camera alignment or device accessibility. No APK, device run or real external share was performed.

## Android annotated still sharing — 2026-10-27

The bounded increment prepares one upright, highlighted PNG from a retained frozen/imported source and opens Android's chooser through a narrow FileProvider read grant. Only explicit sharing creates a temporary derivative; retained source images and OCR remain memory-only. No APK, device run or real external share was performed.

The final corrected implementation passed Android/iOS compilation, all **153 host tests with zero failures/errors/skips**, and lint with **zero errors, 13 warnings** in 10s. Production rendering/PNG encoding and real FileProvider URI reads exercise asymmetric pixels, all frozen rotations, zero/multiple matches, fallback/clipping/deduplication and merged manifest/path metadata. Other tests cover search/source invalidation, failure cleanup/retry, intent grants and cache expiry/quota/reservation/encoded-size bounds. Robolectric provider setup attaches the real provider to each test context to reset AndroidX's static path cache; it does not replace the provider or bypass URI access.

Initial independent review identified allocation failure escaping main publication and missing ownership/capture coverage. The worker now retains cleanup ownership through a main-thread completion signal, catches launch allocation failure and releases the gate after safe cleanup. After the ordinary correction cycle, a planner reassessment closed three remaining evidence gaps without changing production scope: pending owner disposal/recreation, independent frozen-source resume/replacement and actual manual/list mutation before rendering. Real PNG pixels preserve captured geometry while stale handoff is rejected. Final independent verification: **PASS WITH LIMITATIONS**, no remaining material findings. All 133 pre-sharing tests remain, with 20 sharing boundary tests. No configured Kotlin formatter exists; scoped manual review and a limited static audit found no remaining introduced issues. Earlier failed test setup/landmark attempts are not passing evidence.

Pending device acceptance: chooser appearance/cancellation, recipient access, image orientation/outlines, background/return behavior, preparation responsiveness and memory use. A successful chooser launch does not establish recipient delivery or saving. Temporary cache files are eligible for cleanup after 24 hours during initialization or subsequent preparation; handed-off files survive chooser return and ordinary owner disposal. Raw logs, generated stage reports and coordinator state stay local and untracked.

## Android single-image import — 2026-10-27

Import uses the system single-image picker, a bounded JPEG/PNG/WebP decoder with all EXIF orientations, bundled on-device OCR and a separate normalized imported snapshot. The imported bitmap remains memory-only and is searchable with manual queries or named lists. No APK or device run was performed.

The final corrected implementation passed the required Android compilation, full host suite, Android lint and iOS simulator compilation in 12s: **133 tests, zero failures/errors/skips**, lint **zero errors and 13 warnings**. Decoder fixtures execute actual JPEG EXIF orientations 1–8, PNG/WebP decoding, unsupported/truncated/oversized rejection and conservative sampling. SDK boundary tests inspect the exact bitmap and rotation zero and copy real SDK result objects. Native host graphics exercise decoded/adopted pixels and production drawing across viewport aspects. These do not execute native OCR inference or prove device behavior.

Initial independent review identified two required coverage gaps: pending SDK task ownership through the production processor, and actual App outline eligibility across query/list edits and hidden retention. Both were corrected and independently verified: **PASS WITH LIMITATIONS**, no remaining material findings. Production-processor tests now retain a real decoded bitmap and recognizer while a controllable SDK task is pending, verify cancellation/background rejection and cleanup, and prove gate reuse after success/failure. App and tests share the draw-time import eligibility helper; tests cover manual edits/clear, multi-term lists, deduplication, stale identities, viewport changes and hidden retention. Earlier failed test setup attempts are not passing evidence. No configured Kotlin formatter exists; scoped manual inspection and a limited static audit found no remaining introduced issues.

Pending user device checks: permission-free import, system picker/provider variants and cancellation, EXIF/mirrored image alignment, native OCR quality/latency, memory use, keyboard/list editing, background/foreground and recreation. Imported content is discarded on ordinary backgrounding or recreation; only a pending picker ticket can survive the external picker stop. No image, recognized text or URI history is persisted. Raw logs and per-run reports remain local and untracked.

## Memory-only freeze/resume — 2026-10-26

The Android freeze increment pairs one requested analysis proxy's copied pixels with that same proxy's OCR snapshot, keeps searching while frozen and explicitly resumes live scanning. Required checks use the existing compilation-only command below; no APK or device run was performed.

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
```

The final compilation/test run exited 0 in 10s and passed Android/iOS shared compilation, **115 host tests with 0 failures/errors/skips**, and lint with **0 errors, 13 warnings**. It retains all 86 saved-search/core tests. Added tests exercise actual production analyzer ownership with controlled executors/recognizer tasks, failure/cancel/late-result paths, process-wide copy rejection, controller deadlines, retained-owner lifecycle boundaries, frozen query/list matching, deduplicated outlines and rotation/viewport geometry.

A concrete JPEG ImageProxy executes the real CameraX `toBitmap()` path with asymmetric pixel landmarks and nontrivial rotation/crop metadata. Separate tests invoke the drawing helper used by the actual frozen composable through CanvasDrawScope and Robolectric native graphics: real asymmetric pixels across all four rotations, two viewport shapes, hidden/re-enabled rendering, wrong bitmap and resume invalidation. These establish host conversion/render behavior, not native camera YUV conversion or physical-device correctness. No shadowed converter is presented as native-YUV proof.

Initial independent review found stale frozen viewport/visibility eligibility, missing production-render/failure coverage and introduced Kotlin brace violations. Corrections project outlines using the current draw viewport, read live visibility/mode/frame/search state, exercise the actual renderer and missing failures, and apply scoped style fixes. Final independent verification: **PASS WITH LIMITATIONS**, with no remaining actionable findings. No configured Kotlin formatter exists; changed-code manual review and a limited static audit are separate from Android lint. Historical untouched style is not certified. No compiler/style checks were suppressed.

Pending user device checks: same-frame capture correspondence and colors, all orientations/aspects, frozen query/list editing with keyboard and manager, cancel/resume/background/recreation, real YUV conversion, capture latency and memory behavior. The user defers these checks until the next usage limit. Frozen images remain memory-only, with a 4,000,000-pixel bound and no image persistence/export. Raw execution logs and coordinator state remain local and untracked.

## Saved queries and multi-term lists — 2026-10-26

Android and iOS shared compilation, Android lint and the host suite passed on the corrected saved-search implementation:

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
```

Final execution exited 0: BUILD SUCCESSFUL in 11s. Fresh XML confirms **86 host tests, 0 failures/errors/skips**; lint reports **0 errors, 13 warnings**. The tests retain the previous 62 regressions and cover independent overlapping terms, shared-outline deduplication, term-aware retention, live session draw invalidation, editor close/stale-ID handling, startup typing/clear/load ordering, serialized saves/retry, reset races and strict actual Android JSON/AtomicFile round-trips/recovery. Boundary tests include the exact 10,000-character manual query limit, invalid schema/version/types/names/term totals and preserving the previous file after rejected writes.

Independent initial review found six issues, corrected and independently verified. Correction review exposed one additional accepted-edit/rejected-input startup combination; a bounded reassessment separated dirty accepted state from validation feedback and added three deterministic regressions. Final independent verification: **PASS WITH LIMITATIONS**, with no remaining actionable findings in the reviewed scope. Earlier failed compiler attempts and intermediate passing suites are local execution history, not substitutes for this final result.

`git diff --check` passes. No configured Kotlin formatter exists; manual changed-code inspection and a limited static audit of added/modified Kotlin lines found no remaining scoped findings. This is not a full-file formatter certification. Raw run logs, generated stage Markdown and machine-specific state remain local and untracked. No APK was generated.

The user explicitly defers device testing until the next usage limit. Pending checks: relaunch/process restart after save completion; rapid input/backgrounding and Activity recreation; list CRUD/selection with keyboard, landscape, large fonts and long names; simultaneous live OCR outlines and existing permission/rotation/freshness behavior. Host tests do not establish camera alignment, recognition latency or device UI acceptance. iOS storage remains in-memory and camera remains unsupported.

## Final user-adjusted tree — 2026-10-26

The user tested the section, changed keyboard behavior to hide camera/secondary views, and accepted it for commit/push. Shrinking the preview is deferred. This is user acceptance of the current section, not an exhaustive benchmark or completion of every historical device checklist.

The four compilation/host-test/lint tasks listed below passed again on this final user-adjusted tree: BUILD SUCCESSFUL in 6s; 62 tests, zero failures/errors/skips; lint 0 errors and 13 warnings. Android and iOS shared compilation passed. No APK task ran. Commit candidates exclude raw execution logs, per-run stage reports, XML/inventories and machine-specific coordinator state; those remain local.

## Current source verification — 2026-10-26

The user reported delayed highlighting, individual boxes disappearing while others remain, and keyboard/layout reachability problems. The bounded correction uses independent short-lived occurrence presentation, immediate fresh geometry, conservative association, draw-time stale/expiry guards, a single inset-aware Column and bounded preview layout. After device testing, the user changed the UI to hide the camera and secondary views while the keyboard is open or the layout is compact; preview shrinking is deferred. Each missing occurrence expires at min(first missing receipt + 300ms, last observed receipt + 1,500ms); repeated misses do not extend it. Latest OCR/counts remain distinct from retained outlines. Native OCR runtime latency is unmeasured; stable panels remove content-driven preview invalidation but do not prove a device latency improvement.

Final command (exit 0, BUILD SUCCESSFUL in 9s; 66 actionable tasks: 21 executed, 45 up-to-date):

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
```

Fresh XML confirms **62 host tests, 0 failures/errors/skips**, including **15 presentation regressions** plus all 47 prior tests. Android and iOS shared compilation passed. Lint: **0 errors, 13 warnings**. Independent source-first review and correction re-review: **PASS WITH LIMITATIONS**, no remaining actionable findings. Review corrected an overlapping same-text ghost-box case and incorrect test expectations; the exact regression now passes. Earlier failed compilation/test attempts are local execution history, not successful validation evidence. No APK task, staging, commit, push or publication ran.

Generated stage artifacts and machine-specific state are retained locally and ignored by Git; none was tracked at entry. Durable requirements/results stay in project documentation. Physical camera alignment, actual recognition latency, individual-box stability during scene motion and IME/landscape/large-font behavior require the user's rebuilt-device retest. Host tests/source review do not establish these observations. User-owned KeyboardUtil content and the existing Git index were preserved; App edits were integrated for the requested layout change.

## Executed on 2026-10-25

Final command (exit 0, BUILD SUCCESSFUL):

```sh
./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
```

- Android debug APK assembled successfully.
- Four host tests passed with zero failures/skips: three analysis-lifetime tests (overlap rejection, deferred cleanup, idempotent cleanup) and one rotation metadata test covering 0/90/180/270 degrees.
- Android lint passed: **0 errors, 15 warnings**. Warnings concern newer toolchain/dependency versions, the template's redundant drawable-v24 qualifier and missing monochrome launcher icons. Dependency lint checking is enabled because application code lives in the shared library.
- iOS simulator shared Kotlin compilation passed. No iOS app launch or camera implementation was performed.
- APK archive inspection confirmed bundled Latin OCR model assets and native OCR libraries. This does not prove runtime offline behavior.
- `git diff --check` passed. No Android/CameraX/ML Kit APIs or Kotlin data objects were found in common source code.
- `adb devices -l` returned no connected devices, including on the final check. **No physical-device installation, preview inspection or OCR runtime test was performed.**

Debug APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk` (universal debug build, approximately 63 MiB).

## Automated

- Android debug compilation without APK generation (`:androidApp:compileDebugKotlin`). APK builds are left to the user.
- Focused tests of frame metadata/result conversion and lifecycle/concurrency ownership where practical.
- Android lint.
- Shared iOS compilation if available without unrelated setup.

## Physical device checklist

Check `adb devices -l`; install/run on a connected authorized Pixel 10 only if available. Build success does not validate camera output.

- Grant camera access; confirm rear preview and readable Latin text produces updating line counts/sample.
- Move to a blank surface; confirm count/text clears.
- Deny permission, retry, permanently deny and recover through Settings.
- Background/foreground and rotate repeatedly; confirm recovery and no camera-in-use leak.
- Check camera privacy toggle, other camera use, and camera/recognition failure recovery.
- Confirm continued OCR in airplane mode on first launch after installation.
- Check large font sizes and landscape; verify diagnostics remain reachable.

Do not claim accuracy, latency, battery or overlay alignment validation without measurements.

## Independent review corrections verified on 2026-10-25

A separate reviewer with fresh context reported three findings: production Compose tooling, stale staged template files, and unused resource/catalog declarations. All were accepted and corrected; none was challenged.

Fresh correction checks (exit 0, BUILD SUCCESSFUL):

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
./gradlew :androidApp:dependencies --configuration releaseRuntimeClasspath --console=plain
```

- Android Kotlin compilation and iOS simulator shared compilation passed.
- All four host tests passed, with zero failures/errors/skips.
- Lint: **0 errors, 13 warnings**, limited to dependency update notices and existing template icon/resource warnings.
- Release runtime dependency report resolved successfully and contains no UI tooling or Compose components-resources dependency. Android host UI tooling remains intentionally debug-only for inspection.
- No APK assembly or application packaging task was run. APK generation remains the user's responsibility.
- Removed the six intentionally deleted template paths from the Git index and kept the new review workflow paired with AGENTS guidance. No unrelated staged content was reset or discarded.
- Whitespace checks passed for changed files and the staged tree excluding the conventional generated Windows wrapper CRLF lines. No unused imports, dead application code or additional unused files were identified by the reviewer.

Physical-device validation remains pending. Independent review and compilation do not establish device runtime correctness.

## CS-001 query/matching/count — 2026-10-25

Fresh implementation-agent command (exit 0, BUILD SUCCESSFUL in 5s):

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
```

Actual build log (local evidence: `agent-workflow/stages/CS-001/checks.log`). Android debug Kotlin and iOS simulator shared compilation passed. Host XML at `shared/build/test-results/testAndroidHostTest/` confirms **17 tests, 0 failures/errors/skips**: 13 new `TextSearchTest` tests (timestamp `2026-10-25T18:07:03.063Z`), existing 1 frame-rotation test and 3 analysis-lifetime tests. Lint XML: **0 errors, 13 warnings**. No APK assembly, application packaging, export or installation task ran; host-test packaging/resource tasks in the log are not APK generation. These executions belong to the implementer; independent CS-001 review is pending.

[Matching requirements](search-requirements.md) describe implemented defaults and provenance. UI/source inspection confirms direct `remember(query, state)` invalidation and eligible-state gating; software-keyboard/live-device behavior has not been observed. No device installation, camera/OCR runtime, accuracy/performance/battery measurements, preview mapping or highlight alignment validation occurred in CS-001.

Manual checks pending: live query edit/clear and repeated identifier/phrase counts; blank surface replacement; keyboard/landscape/large-font access; permission denial/recovery, errors and background/foreground clearing/recovery. Full milestone later requires real-device aligned outlines across rotation/aspect/layout changes. Offline-first-launch remains pending. Next external action when device validation is due: user builds/installs their APK on an authorized Android device and performs/records these observations; no device availability is inferred from historical evidence.

### CS-R1 correction validation

Initial independent CS-001 review identified one P2 finding, CS-R1: the keyboard-adjusted camera viewport could compress an unscrollable permission/settings column and hide its trailing action. Accepted and corrected by applying vertical scrolling before the existing content padding, preserving the action's content height and a scroll route. Camera/session lifecycle and permission behavior are unchanged. Manual constrained keyboard/landscape/large-font and ordinary portrait observations remain pending; no device reproduction or screenshot was obtained.

Fresh implementation correction command: same four compilation/test/lint tasks above, exit 0, BUILD SUCCESSFUL in 3s (66 tasks: 19 executed, 47 up-to-date). Correction log (local evidence: `agent-workflow/stages/CS-001/correction-checks.log`). Android/iOS shared compilation passed; host XML still confirms 17 tests, zero failures/errors/skips, and lint XML has 13 warnings/no errors. No APK task ran. Test task executed on this correction run; matching test code is unchanged. Source inspection confirms scrolling wraps padding and the permission/settings button. Independent correction verification is pending; compilation does not establish measured runtime reachability.

## CS-002 projection/outlines — 2026-10-25

Fresh implementer command (same authorized compilation-only command as CS-001):

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
```

Final execution: exit 0, BUILD SUCCESSFUL in 5s, 66 tasks (20 executed/46 up-to-date). Actual final log (local evidence: `agent-workflow/stages/CS-002/checks.log`). Android debug/shared compilation, iOS shared simulator compilation, host-test compilation/execution and lint passed. Fresh XML confirms **41 tests, zero failures/errors/skips**: HighlightsTest 13 (timestamp `2026-10-25T20:15:55.659Z`), LiveFrameTrackerTest 11 (`2026-10-25T20:15:55.652Z`), retained TextSearchTest 13, FrameInfoTest 1 and AnalysisLifetimeTest 3. Lint: **0 errors, 13 warnings**. No APK assembly/application packaging/export/install command ran; internal JAR/AAR/resource/host-test packaging tasks are compilation/lint infrastructure.

Earlier CS-002 implementation checkpoints also succeeded: core compilation log (local evidence: `agent-workflow/stages/CS-002/checks-initial.log`), 7s/24 executed tasks before new geometry tests; new-tests checkpoint (local evidence: `agent-workflow/stages/CS-002/checks-tests.log`), 2s/7 executed tasks. The geometry/state safeguard checkpoint (local evidence: `agent-workflow/stages/CS-002/checks-geometry.log`) passed in 4s/21 executed tasks. The native retry-view checkpoint (local evidence: `agent-workflow/stages/CS-002/checks-native-key.log`) passed in 4s/19 executed tasks after keying AndroidView composition to the recreated PreviewView. Final run added draw-time current query/source/native-size suppression for state/layout changes between composition and drawing; iOS shared compilation executed successfully again with that common-source change. No repeated execution after the final successful unchanged target. All executions above belong to the implementer, not an independent reviewer. Independent CS-002 review remains pending.

[Neutral requirements](search-requirements.md) document matrix origins/order, element selection, invalidity and expiry. Pure tests use independently hand-derived landmark coordinates and injected monotonic clocks without sleeps. Native PreviewView/CameraX adapter integration is source-inspected against pinned publisher sources, not platform-executed by host tests; no new Android test runtime/dependency was added. Common sources remain free of Android/CameraX/ML Kit objects/data objects.

**External runtime gate still pending:** user builds/runs their own APK on an authorized Android device and records model/Android version, viewport/orientation and observations: repeated printed identifiers and a multi-element phrase at centre/edges; query edit/clear; blank page; portrait/landscape/rotation; keyboard opening/closing and preview resize; background/foreground; denied permission/settings/retry/error recovery; large-font action reachability; no systematic offset/crop/mirror error; no stale/invalid outlines or live counts. First-launch offline OCR also remains unobserved. No device was installed/launched or measured by this stage. No accuracy/latency/battery or motion-tracking claim. Once code/review is complete, stop for this external action rather than inventing more product scope.

## CS-003 coordinate adapter regression — 2026-10-25

Actual user report (not agent reproduction): persistent stationary highlight displacement/reciprocal quarter turns with correct recognized text/counts. The previous41 tests assumed upright copied geometry and missed the SDK boundary. Pinned artifact bytecode shows bundled bounds are rotated while separate corners are not; actual SDK-helper/result-constructor tests execute that difference. See [requirements boundary table](search-requirements.md).

Real red run before normalization: `./gradlew :shared:testAndroidHostTest --console=plain`, exit1 after18s, **47 tests,5 failures**. All41 prior tests and the pinned bridge proof passed;5 new production-adapter/projection tests failed. Red log (local evidence: `agent-workflow/stages/CS-003/checks-red.log`), actual red XML (local evidence: `agent-workflow/stages/CS-003/red-adapter-results.xml`), source/test fingerprint (local evidence: `agent-workflow/stages/CS-003/red-provenance.json`). At90°, old copied corners remained(80,40)… instead of expected upright(160,80)…; portrait x41.25 differed from116.25, and bounds/corner envelopes diverged. Two earlier setup attempts failed compilation (constructor callback placement, then package-private media.Image fixture); those were setup failures, not geometry-red claims. Their actual logs are preserved in the stage directory.

After normalization, fresh implementer execution:

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit0, **BUILD SUCCESSFUL in12s**,66 tasks(45 executed/21 up-to-date). Final log (local evidence: `agent-workflow/stages/CS-003/checks.log`). Fresh host XML: **47 tests,0 failures/errors/skips**, including6 MlKitGeometryAdapterTest (`2026-10-25T21:54:35.400Z`) plus all41 existing tests unchanged. Green adapter XML (local evidence: `agent-workflow/stages/CS-003/green-adapter-results.xml`). The new test-source hash matches the genuine red run. Android compilation, iOS shared compilation and lint passed; **0 lint errors,13 warnings**. No APK generation/export/install.

These six tests execute SDK Java geometry helpers/result constructors with Android operations supplied by Robolectric, real production `toSnapshot` and a fake-detector invocation of the actual analyzer. They do not run native OCR inference or establish physical alignment. All source math/state tests are retained. Release dependency resolution also succeeds and has no Robolectric production artifact. No tests repeated after the successful unchanged source target. Independent CS-003 source-first review remains pending.

**Required user retest after review:** rebuild/run your own APK on the same authorized device; record model/Android version/build/layout. Hold readable horizontal and vertical targets stationary at centre and edges; check orientation and position in portrait/landscape/all rotations and keyboard resize. Check query edit/clear, blank replacement and clearing/recovery after rotation/background/retry. Record pass/fail/not-tested observations. First-launch offline remains pending unless actually observed. The prior user report is evidence of failure; it is not replaced by an agent runtime-fix claim or a completed acceptance checklist.
