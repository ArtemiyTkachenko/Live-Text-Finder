# Validation

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
