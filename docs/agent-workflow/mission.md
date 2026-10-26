# Live Text Finder mission and handoff

## Current handoff

On 2026-10-26 the user tested the current section, made their own keyboard adjustment, and accepted the section for committing and pushing to main. The camera preview and secondary views are hidden while the keyboard is open or the layout is compact. Preview shrinking did not meet the user's needs and is deferred; do not restore it without a new request.

Independent source review of coordinate correction and per-occurrence highlight retention passed with runtime limitations. Fresh matching geometry updates immediately; missing individual occurrences have independent bounded grace and exact source provenance. See versioned [requirements](../search-requirements.md), [architecture](../architecture.md) and [validation](../validation.md). User feedback accepts the section but does not supply a measured latency/accuracy benchmark or a completed exhaustive device checklist.

Historical stage evidence and machine-specific coordinator state remain local and Git-ignored. Durable guidance, requirements and validation summaries are versioned. Current authorization is limited to auditing necessary commit contents, validating the final user-adjusted tree, committing and pushing to main. No new feature, APK generation or automatic continuation is authorized. Stop after delivery.

## Approved next product milestone

**Core live search: query → matching → aligned highlights** was approved by the user on 2026-10-25. The interrupted CS-003 source review was completed by a separately authorized request on 2026-10-26. Physical-device acceptance remains pending; no further feature scope is authorized.

Excluded: saved queries/lists and query persistence, freeze/resume, image import/export, monetization, publishing and iOS camera/OCR. Historical proposals do not extend scope. Matching defaults and alignment validation must be stated in any future product specification, with material ambiguity escalated rather than silently invented.

## Direction and foundation history

The chosen product is “Ctrl+F for the real world”: enter text and find recognized matches on a live camera preview. The project targets a practical KMP/CMP Android-first prototype, with shared code where useful and local bundled Latin-script OCR. No backend or paid inference is required. Commercial value, market exclusivity, latency, battery use and recognition accuracy have not been established. Printed readable Latin text is the foundation's supported starting script, not universal language support. No-match feedback must never imply a safety guarantee or physical absence of text.

The completed, independently reviewed foundation provides rear CameraX preview, bundled ML Kit Latin OCR, status/line count/frame diagnostics and text sample, permission recovery, lifecycle binding, bounded latest-frame processing, immutable copied OCR results and deferred detector cleanup. Its foundation-only description is historical; current source state and unfinished CS-003 acceptance are authoritative. See [architecture](../architecture.md), [dependencies](../dependencies.md) and [validation](../validation.md).

`shared/commonMain` owns Compose UI and platform-neutral immutable text/geometry/frame snapshots. `shared/androidMain` owns permissions, CameraX/ML Kit, lifecycle, image buffers and session resources. `androidApp` is the minimal host. `shared/iosMain` retains the framework entry point and explicit unsupported-camera placeholder. No Kotlin `data object`.

Android binds its own preview/analysis use cases, uses KEEP_ONLY_LATEST, discards disposed/stopped-session results, unbinds only owned use cases and closes the recognizer after in-flight analysis. ML Kit owns submitted proxy closure; no app analysis executor exists. OCR geometry refers to rotated input-buffer pixels, not preview pixels. Frame metadata records raw/upright dimensions, rotation, crop, timestamp and sensor-to-buffer mapping. Highlights require a separately verified transform to the actual preview crop/scale/orientation.

Historical checks include Android assembly, four host tests, lint and iOS shared compilation; correction checks used compilation-only tasks and release dependency resolution, with 0 lint errors and 13 warnings. These are prior evidence, not migration executions. Physical-device camera/OCR, first-launch offline behavior, UI, accuracy/performance and iOS application runtime remain unverified. No connected device was available during foundation validation. APK generation is left to the user; do not repeat historical assembly without explicit authorization.

## Provenance and authorization history

The original discovery handoff selected the direction and proposed reusable queries, freeze and export as later possibilities. Its competitor review research was user-review evidence, not reproduced defects, validated demand or current authorization. Its statement that implementation had not started is historical and superseded. This document summarizes needed context so those external files are optional provenance, not prerequisites for understanding the mission.

Historical discovery handoffs, delivery reports and review ledgers are optional local provenance. The repository [validation summary](../validation.md) preserves the durable checks and limitations; a fresh checkout does not depend on machine-specific source paths.

## Decision log

Append dated user decisions with source, exact scope and exclusions; recommendations/reviewer findings are not decisions. The coordinator records accepted decisions and mirrors active authorization in state.

| Date | Source | Decision and scope |
| --- | --- | --- |
| 2026-10-25 | Historical discovery handoff, positive overall response | Chosen live text-finding direction; individual proposed later features were not approved acceptance criteria. |
| 2026-10-25 | User workflow request in current coordinating chat; preserved in WF-001 prompt (local evidence: `stages/WF-001/prompt.md`) | Configure project prompter → implementer → independent review → corrections → final report cycle. Setup only; coordinator owns dispatch/state/recovery scheduling. (Historical workflow request, superseded by the bounded workflow migration.) |
| 2026-10-25 | User answer to “What may the autonomous cycle work through next? The approved OCR foundation is complete; later handoff features were proposals.” | “Core live search: query → matching → aligned highlights”. Only this finite next milestone was approved; exclusions above remain. Source relayed by the current coordinator. |
| 2026-10-26 | Current user request and explicit workaround approval | Complete CS-003 independent review and assess generated Markdown/log necessity; use fresh-context gpt-6.1-sol / medium reviewer via explicit dispatch workaround. Preserve user edits and historical evidence; no APK generation or unrelated feature work. |
| 2026-10-26 | Current user feedback and direct clarifications | Correct highlight stability for individual missing occurrences with short grace, maintain first-match immediacy, shrink preview/keep query visible with IME, and exclude generated local evidence/state from commits while preserving them on disk. No new features, dependencies, APK or Git index changes. |
| 2026-10-26 | User device feedback and commit request | Accept this section with user-adjusted keyboard/compact layouts hiding camera and secondary views; defer preview shrinking. Audit necessary files, commit and push to main; preserve local evidence and do not start a new feature. |
