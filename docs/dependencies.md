# Dependency decisions

Checked 2026-10-25 against official documentation and publisher Maven artifacts. Versions are pinned in `gradle/libs.versions.toml`.

| Component | Version | Decision |
| --- | --- | --- |
| Kotlin / Compose compiler | 2.4.20 | Retain the existing stable template toolchain; both plugins use the same version. |
| Compose Multiplatform | 1.12.1 | Retain the existing stable template. |
| Material 3 Multiplatform | 1.9.0 | Latest non-prerelease in publisher Maven metadata; replace template 1.12.0-alpha03. Material 3 has an independent release track. |
| Android Gradle Plugin | 9.1.1 | Retain official KMP Android library plugin and separate Android application shell. Supports API 37. |
| Gradle wrapper | 9.5.1 | Retain existing checked-in wrapper/checksum. |
| Build JDK | 21 | Existing daemon criteria; JVM bytecode remains 11. |
| Android compile / target SDK | 37 / 37 | Retain installed stable template SDK. |
| Android minimum SDK | 24 | Retain template; meets ML Kit API 23 minimum. |
| Activity Compose | 1.13.0 | Existing template version; Android-only permission launcher. |
| JetBrains Lifecycle Runtime Compose | 2.11.0 | Existing template version; Android source set consumes lifecycle owner. |
| CameraX camera2, lifecycle, view, mlkit-vision | 1.6.2 | Explicit requested stable release, one version for all artifacts. Core arrives transitively. |
| ML Kit bundled Latin OCR | 16.0.1 | `com.google.mlkit:text-recognition`; model ships in the APK. No Play Services model download required. |
| AndroidX Core | 1.19.1 | Android-only direct dependency for ContextCompat and ActivityCompat; no unused core-ktx dependency. |
| Kotlin test / JUnit | 2.4.20 / 4.13.2 (transitive JUnit) | kotlin-test-junit supplies host assertions and JUnit runner integration; tests cover request lifetime and rotation metadata. |

No dependency injection, navigation, storage or backend dependencies were added. Camera coordination uses the main executor, while ML Kit runs recognition asynchronously on its own workers. There is no app-owned executor to leak or shut down prematurely.

## Sources

- [Recommended KMP structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html)
- [Compose 1.12.1 release](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.1)
- [Kotlin releases](https://kotlinlang.org/docs/releases.html)
- [Material 3 publisher metadata](https://repo.maven.apache.org/maven2/org/jetbrains/compose/material3/material3/maven-metadata.xml)
- [AGP 9.1.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [CameraX stable releases](https://developer.android.com/jetpack/androidx/releases/camera)
- [ML Kit Analyzer](https://developer.android.com/media/camera/camerax/mlkitanalyzer)
- [ML Kit bundled model and image analysis guidance](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)

This increment extends the already generated official project rather than replacing it. A newer Kotlin patch exists; changing the working toolchain is outside this camera/OCR increment.

## Independent review corrections

Compose UI tooling is declared only as `debugImplementation` in the Android host for debug inspection. Shared production configurations contain no tooling, preview-annotation or unused Compose resources dependency. Unused template catalog aliases/version entries were removed. AndroidX Core remains as an explicit Android dependency because permission and main-executor code directly uses its Java APIs.

## CS-003 host adapter regression dependency

`org.robolectric:robolectric:4.17` is declared only in `androidHostTest` via the version catalog. [Official setup](https://robolectric.org/getting-started/) checked 2026-10-25 lists this stable release; it supports the existing JDK/SDK without an app toolchain upgrade. Tests use API35 to execute the pinned ML Kit bounds helper, public result constructors, Android geometry operations and the real production admission path. Plain host Android stubs cannot execute these operations; a fake-only geometry test would repeat the missing boundary. Robolectric is a test runner/environment, not native OCR/device validation.

Fresh `:androidApp:dependencies --configuration releaseRuntimeClasspath` succeeds and includes the same bundled ML Kit versions but no Robolectric artifact. No production/runtime dependency was added or upgraded. Full graph: CS-003 release-dependencies.log (local evidence: `agent-workflow/stages/CS-003/release-dependencies.log`). Existing CameraX1.6.2/bundled Latin16.0.1 remain unchanged.
