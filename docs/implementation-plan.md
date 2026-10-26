# Initial implementation plan

Documentation created before application changes. The product handoff is context; the current request limits this increment to preview and OCR.

1. Preserve the existing generated Android/iOS project and unrelated staged files.
2. Verify official dependency guidance; pin CameraX 1.6.2 and bundled Latin text recognition 16.0.1; use stable UI dependencies.
3. Add platform-neutral OCR snapshots and a shared diagnostic screen, plus the platform camera composable contract.
4. Implement Android permission recovery, lifecycle-bound rear preview, latest-frame ML Kit analysis, immutable result mapping and deterministic cleanup.
5. Retain iOS with a clear placeholder.
6. Build Android, run focused boundary/concurrency tests and lint, inspect device availability, document exact results and pending manual checks.

Deferred at foundation completion: matching, highlights, saved queries, freeze/resume, image import/export and monetization. The separately approved core-live-search milestone begins with CS-001 below.

## Completion

All implementation steps are complete. Android assembly, four host tests, lint and iOS simulator shared compilation passed. Physical-device checks remain pending because ADB reports no connected devices. See [validation](validation.md) for exact results and manual acceptance checks.

## CS-001 — shared literal matching and query/count

Implement one transient single-line query, a pure common matcher over normalized per-line OCR elements, and recognized match status derived from the latest eligible snapshot. Preserve frame/element provenance for a subsequent aligned-highlight stage. [Requirements](search-requirements.md) define substring, whitespace, case and occurrence rules. Thirteen new common tests cover matching/provenance and state eligibility/replacement. Android/iOS compilation, 17 total host tests and lint pass; see [validation](validation.md). Initial independent CS-001 review identified CS-R1 (constrained permission action reachability). Permission/settings content now scrolls; independent correction verification is pending.

Highlights, actual preview mapping and physical-device acceptance remain open. Saved queries/lists, persistence, freeze/resume, import/export, monetization and iOS camera remain excluded. CS-001 does not complete the approved core live search milestone.

## CS-002 — actual-preview mapping and outlines

Add tested shared projection/selection, matched-element Canvas outlines and frame/session/generation freshness. Android supplies actual native sensor-to-view mapping; retain the CameraProvider/KEEP_ONLY_LATEST pipeline and lifetime cleanup invariants. One 1,500ms monotonic receipt-age expiry suppresses stalled results. Twenty-four new deterministic tests extend the prior 17; Android/iOS shared compilation and lint pass (see validation). Independent CS-002 review remains pending.

After implementation/review closure, the remaining finite gate is actual Android device observation of matching outlines, query/blank replacement, rotations/aspect/keyboard resize, permission/error/background recovery, stale clearing and large-font action reachability. User builds/runs their APK; do not invent another feature/polishing stage while waiting. Offline-first-launch remains pending. Full core-live-search runtime acceptance is not complete without these observations.

## CS-003 — device-reported coordinate admission correction

User feedback reopened alignment after CS-002's external gate: stationary highlights were displaced/quarter-turned while text/counts worked. Trace the actual bundled SDK route before changing projection. Source and SDK-adapter execution identify pre-upright corners alongside already-upright bounds. Normalize line/element corners once at production admission; preserve SDK bounds, downstream view math, OCR/matching and ownership/freshness. Six new adapter tests demonstrate a genuine old-conversion red and corrected green; all47 tests/compilation/lint pass. Independent CS-003 review is pending. User rebuilt-APK centre/edge horizontal/vertical/rotation/resize retest remains necessary; no device fix is claimed.

## Highlight stability and keyboard correction — 2026-10-26

User feedback clarified individual box dropouts, delayed highlighting and IME reachability. Completed bounded per-occurrence grace, stable Column/weighted preview and commit hygiene; independent review/correction verification passed with device limitations. Final host suite: 62 tests, zero failures; Android/iOS compilation and lint passed. See current [requirements](search-requirements.md) and [validation](validation.md). User rebuilt-device retest remains the next gate; no feature continuation or APK task is authorized.

User follow-up: device-tested section accepted for commit/push. Current keyboard/compact behavior hides camera/secondary views; preview shrinking is deferred. No exhaustive device benchmark is inferred.

## Last query and named lists — 2026-10-26

The user approved Android query retention and named multi-term lists together, then authorized repeated reviewed stages and commits with device checks deferred. This supersedes the earlier per-stage stop instructions for this run. The bounded implementation adds safe asynchronous app-private persistence, list create/edit/select/delete and simultaneous independent matching while preserving the manual query and existing camera/keyboard policy. Independent review and correction verification are required before commit; source/host checks do not establish device relaunch or live OCR behavior. Freeze/resume and image import/export remain outside this increment.
