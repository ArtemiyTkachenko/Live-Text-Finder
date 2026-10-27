# Live Text Finder mission and handoff

## Current handoff

Completed issue-only correction: bounded live display stabilization reduces small positional noise while exact raw OCR/projection, source guards and existing grace/expiry remain intact. Preview and PNG painting now preserve foreground edges over neighbouring black under-strokes. Independent correction verification passed with limitations; all 183 host tests and Android/iOS compilation/lint checks passed. User accepts keyboard/list/small-layout/state-preservation checks 1–3 and withdrew the comma-list complaint. Device retest remains necessary for jitter quality, responsiveness, clipping and isolated incomplete sides; no new functionality is authorized.

Completed bounded correction: controls and the list editor now scroll together in constrained keyboard/compact/list-manager layouts, preventing the header from starving the editor. The user-selected hidden preview policy is preserved; no preview shrinking, OCR changes or new functionality. Independent review passed with limitations; all 165 host tests and Android/iOS compilation/lint checks passed. The user reports checks 1–3 passed; exhaustive focus/accessibility testing is not inferred. Current authorization prioritizes existing issues before new functionality.

Completed bounded increment: manual reopenable in-app Help explains actual search/list, preview, retained-image, colour, sharing and platform behaviour. It adds no onboarding flag or session/source changes. Fresh independent review passed with device limitations; all 163 host tests and Android/iOS compilation/lint checks passed. Device layout/accessibility and keyboard/dismissal checks remain pending.

Completed bounded work: selectable global highlight colours across live, frozen and imported previews and annotated sharing. Yellow remains the default; colour changes preserve matching and geometry. Independent correction verification passed with device limitations; all 163 host tests and required Android/iOS compilation/lint checks passed. Device UI/accessibility and external sharing remain pending.

Completed and independently reviewed with runtime limitations: saved queries/lists (`5bc726c`, 86 tests), freeze/resume (`baa5302`, 115 tests), single-image import (`a99dc20`, 133 tests) and annotated sharing (`5ba1d09`, 153 tests). The user reports these commits, global colour (`8179107`) and Help (`fa4bc4e`) pushed to main. Retained source images/OCR stay memory-only; explicit Share creates bounded temporary PNG cache files with read-only provider access. Device checks remain deferred.

The user authorized remembering the last manual query and named reusable search lists together, and clarified that all terms/phrases in a selected list are searched simultaneously. Android data stays app-private; no recognized OCR text or images are persisted. Manual query and active list selection restore across launches. The completed bounded implementation includes list create/edit/select/delete, safe asynchronous persistence, independent multi-term matching and existing geometry/retention safeguards. Independent review passed with device limitations; all 86 host tests and required compilation/lint checks passed. See the current user request and local state for execution ownership.

The prior core live-search section was accepted by the user and published to main. Their chosen keyboard/compact behavior hides camera and secondary views; preview shrinking is deferred. Preserve that behavior. Historical stage evidence and machine-specific state remain local/Git-ignored; version only durable requirements, implementation and meaningful tests. Do not generate another per-run report collection.

The user now authorizes autonomous reviewed stages and commits, with main pushes when possible and local commits/continued work if push authentication fails. Device testing is deferred until the next usage limit; retain pending checks without blocking source development. Complete independent review/correction verification and relevant checks for each stage. No APK generation, background scheduler or worker delegation is authorized. Native role discovery/routing remains unverified; explicit configured-model dispatch is the disclosed workaround.

## Completed saved-search milestone

**Restore last query and manage named multi-term search lists** was approved on 2026-10-26. This extends the accepted core live-search section. Entries are independent literal terms/phrases, with spaces inside an entry treated as a phrase; matching remains per OCR line. Preserve latest recognition counts, distinct-element outline deduplication, query/search identity invalidation and per-occurrence short retention.

Excluded from the saved-list increment: freeze/resume, import/export, list colors/categories/sorting, backend/cloud/accounts, OCR-result or image persistence, monetization, publishing, iOS camera and durable iOS storage, and revisiting preview shrinking. iOS retains its unsupported-camera placeholder and explicit in-memory saved-search boundary.

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
| 2026-10-26 | User selected both next-feature options and several terms/phrases at once | Add Android last-query persistence plus named reusable multi-term lists with local create/edit/select/delete; no new feature expansion, APK or Git publication. |
| 2026-10-26 | User autonomous-run instruction and explicit resume | Continue prompt/implement/review/fix/report/next-task cycles, commit reviewed stages and push when possible; continue with local commits if push fails. Defer device checks until usage limit and apply new AGENTS Kotlin rules to encountered scoped code. |

Coordinator-selected next increment, 2026-10-27: annotated sharing of one retained frozen/imported image through the Android Sharesheet, under the current autonomous-run authorization. This adds only explicit temporary PNG cache files, narrow read-only FileProvider access and bounded preparation/cleanup; permanent saving/history and iOS sharing remain excluded. Independent review and required host checks precede commit; no actual external share or device run is part of development validation.
