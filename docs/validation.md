# Validation

## Camera preview stream feedback correction — 2026-11-04

Camera waiting/interruption feedback follows the preview stream state; this is state-based
feedback, not a camera startup-latency optimization. Camera session creation, retry and teardown
remain owned by the existing Android camera composition. Host tests exercise the production
LiveData observer across lifecycle stop/start/destroy and retry stream replacement, including stale
source detachment, fixed viewport bounds and the actual 48dp Retry layout. The focused tests and
full host suite passed: **325 tests across 47 suites, zero failures, errors or skips**. Android
debug compilation, Android lint and iOS simulator shared compilation also passed. These checks do
not establish physical-device camera behavior; that remains pending. Logs and renders are in the
local `work/camera-wait/correction/` scratch directory.

## Repeated saved-list selection and save feedback — 2026-10-31

Selecting the active list is now a no-op, so duplicate card taps do not enqueue writes or invalidate transfer context. The App selection dispatcher validates live loaded state and only advances transfer ownership for a valid changed selection. Routine save progress occupies a reserved single line in the search-context row; loading, write errors, retry and recovery remain in the status region.

A native Android host integration fixture composes the production controls, status region, list cards and `AppContentColumn` around a real controller and controlled asynchronous store. It clicks production cards, checks invalid/unloaded/null and duplicate selections, confirms serialized queued writes and retry after failure, and compares control, card and preview bounds across saving and completion. The 320dp/1.4× dark and 360dp/1.25× light renders were inspected. The full host suite passed with **317 tests and zero failures, errors or skips**. Android compile, lint and iOS simulator shared compile also passed; lint reported **14 warnings**. The aggregate command had 17 tasks execute and 56 up-to-date.

`git diff --check`, changed-line length and import-order checks pass. No Kotlin formatter is configured; modified Kotlin was reviewed manually. Logs and native renders remain under the local `work/save-reflow/` scratch directory. Physical-device behavior was not tested.

## Retained still-image inspection layout — 2026-10-31

Frozen and imported results now keep a compact context header, reserved Saving caption, source return action, Results menu and More menu. Edit expands the existing search controls; the manager and its Close action remain reachable. Zoom/reset and selected-match navigation/copy sit below the weighted image viewport, while the hidden-preview scroll policy remains in place. Live-search controls keep their existing `Manual search` label; the retained header includes the current manual query.

The baseline and retained branches use one shared 360×800, default-font production-component fixture with the same frozen/imported source state, query, result count, status, image target, selection and insets. Measured viewport heights were 252→528dp for frozen and imported unselected cases, and 184→460dp for frozen and imported selected cases: a 276dp gain in each comparison. Saving on/off preserved the measured bounds. Paired native renders and narrow, enlarged-text, dark and landscape hidden-preview fixtures were inspected.

Robolectric tests click the actual separate dropdown roots and exercise Results mutual exclusion, occurrence-entry focus/keyboard dismissal, Options/Share/Lists routing, manager open/close, preparing cancellation, share-error feedback and both frozen/imported return callbacks through the production action dispatcher. Existing status/recovery and image-share boundary tests remain in the host suite. The focused still-inspection class passed **11 tests**; the final aggregate passed **321 host tests across 46 suites, zero failures/errors/skips**, Android debug compilation, Android lint (**0 errors, 14 warnings**) and iOS simulator shared compilation. The final aggregate ran 9 tasks with 64 up-to-date. `git diff --check`, import sorting and the 100-character changed-Kotlin-line limit pass; no Kotlin formatter is configured. Logs and renders are in local `work/frozen-canvas/`. No APK or device test was run.


## Still-image inspection and matched-text copying — 2026-10-29

Scope 3 adds retained-image zoom/pan and a selectable occurrence dialog with explicit matched-text copying. Image and highlights share one transformed subtree inside the fixed clip; source/request and viewport changes reset the view. Matching and rows share normalized element text, preserving OCR casing and occurrence identities. Clipboard writes revalidate source/content ownership, search and selection; annotated sharing still uses the whole source.

The pre-correction full run passed in 1m18s: **246 host tests, zero failures/errors/skips across 40 suites**, Android compilation, iOS simulator shared compilation and lint with **zero errors/14 warnings**. Native pixel checks exercise the actual frozen renderer at 0/90/180/270 degrees and imported renderer with zoom/pan, outlines and viewport clipping. Early fixture failures came from Activity relayout changing the test viewport and landmark samples overlapping the thick highlight stroke; fixed fixture dimensions and separated landmark/highlight positions retain exact colour/alignment assertions. Tests use fake clipboard writes and no external clipboard.

Independent review requested additional host coverage for production UI action wiring, stale callbacks, rendered reset/resize, compact/enlarged-text access and match-row edge cases. The accepted correction adds a production copy coordinator used by App and tested with real imported/frozen owners and fake Compose Clipboard. Isolated search, selection, source-cancel and stop cases write nothing; valid selected payloads write exactly once, and clipboard failure produces a failure outcome. App maps outcomes to localized success/failure by source inspection. Actual dialog controls verify disabled Copy, selection, scrolling and selected writes; 220dp-wide controls at 1.5× font scale verify 48dp targets and reachable Zoom/Reset. Frozen image/outline renders additionally check reset and resize for all rotations. Pure row tests cover OCR casing, repeated/overlapping term identities, malformed offsets and tampered payloads.

Final correction checks passed in **1m1s: 251 host tests, zero failures/errors/skips across 41 suites**, Android compilation, iOS shared compilation and lint with **zero errors/14 warnings**. Independent correction verification: **PASS WITH LIMITATIONS**, no remaining actionable finding. Three unused test imports were removed after the passing run; test compilation was checked separately. Scoped Kotlin inspection, limited static style audit and whitespace checks pass; no configured formatter exists. AGENTS.md and all 41 historical evidence files remain unchanged. An earlier interrupted `allTests` attempt compiled iOS test sources and linked a test target but ran no native test binary; it is not counted as passing evidence. Device pinch/pan, TalkBack, IME, real clipboard, full App composition and iOS application runtime remain untested. Production coordinator/component testing plus App wiring inspection satisfies this bounded scope. No APK was generated. Logs and rendered fixtures remain outside Git.

## Compact controls and list-entry preview — 2026-10-29

Main controls retain search, list context and Freeze/Resume while Options groups highlight colour, available live area, image import and Help. Source cancellation/Return remains directly available. List drafts now show one-entry-per-line guidance, an example, count against 32 terms, the first five normalized entries and any remaining count. Blank lines are flagged and duplicates stay visible; save validation and source/search ownership are preserved.

Full checks passed in 41s: **235 host tests with zero failures/errors/skips across 36 suites**, Android compilation, iOS simulator shared compilation and lint with **zero errors/14 warnings**. The identical 360×700, font-scale-1.25 combined production-component fixture gives its weighted placeholder preview **170px instead of 118px**, recovering 52px without a forced preview minimum. These are host component fixtures, not full-device camera acceptance.

Native tests open actual light/dark Options windows, constrain the compact dialog to 320×360, verify scrolling and 48dp Help bounds, callback counts/closure, disabled highlight pointer behavior and Back dismissal. Focused production controls test direct Cancel freeze/import and Return to camera. The editor tests use shared page scrolling and verify live empty/duplicate/overflow/blank previews, invalid-save draft retention, Cancel and normalized successful save. Pure cases cover CRLF, whitespace, commas as part of a phrase, duplicates and blank/trailing-line behavior.

Initial review required repairing three test failures and adding light Options/direct-action coverage. The correction retains strict reachability, target-size and nonoverlap assertions. Owned test recomposition uses zero-duration motion for deterministic static-state checks; animation behavior is not accepted by these tests. Integration-only missing imports/helpers, exact-resource comparisons and live-position reference comparisons were corrected before the final passing run. Independent correction verification: **PASS WITH LIMITATIONS**, no remaining actionable finding. Scoped manual Kotlin inspection, limited added-line style audit and whitespace checks pass; no configured Kotlin formatter exists. AGENTS.md and all 41 historical evidence files remain unchanged. No dependencies, APK or device run were added. Physical touch/TalkBack/IME/camera transitions and iOS application runtime remain pending; logs/renders are outside Git.

## Custom highlight picker — 2026-10-29

Implemented preset-compatible opaque RGB values, local slider drafts and strict Android schema v3 with legacy v1/v2 reads. Final local checks passed in 57s: **228 host tests with zero failures/errors/skips**, Android compilation, iOS simulator shared compilation and lint with **zero errors/14 warnings**. Fresh source-first independent review completed with **PASS WITH LIMITATIONS**, no actionable findings. The earlier interrupted review provided no verdict; it was not treated as acceptance.

Earlier native checks exposed a 44dp inner slider target despite an outer minimum-height modifier. User-authorized reassessment of pinned Material 1.9.0 measurement led to a standard thumb inside a centred noninteractive 48dp-high container, preserving the standard thumb width. Native tests now assert actual layout and touch dimensions and send pointer events at both outer vertical margins for every RGB channel. Each changes only the intended draft channel without committing. Exact endpoint checks use native taps; the prior one-step test drag did not reliably reach the endpoint. Strict target assertions were retained. Apply-once, disabled Apply, Cancel/re-entry reset, selected custom reopening, Back dismissal and constrained scrolling now pass.

Model, strict-store legacy/custom roundtrips and malformed-byte preservation, controller guards/save ordering, live/frozen/imported rendering and decoded shared PNG/stale-share tests pass. Light/dark enlarged-text editor renders were inspected; fixed dialog actions remain visible while content scrolls. A scoped Kotlin audit and whitespace check pass; no configured Kotlin formatter exists. AGENTS.md and all 41 historical evidence files remain unchanged. Raw logs/renders stay outside Git. No APK, device run or real external share was performed. Physical touchscreen/TalkBack, camera/process restoration, actual recipient sharing and iOS application runtime remain deferred. The reviewer independently checked all 35 host-test suites, execution log, lint XML and light/dark renders. Explicit accessibility SetProgress and outside-touch dismissal lack dedicated assertions; their standard Material implementations remain in use.

## Screenshot-driven spacing and rounded controls — 2026-10-29

Main controls now share a 16dp outer gutter with the list/editor regions, and query/list text fields and selected-list text align at a measured 32dp inset. Highlight/Help actions wrap from the start. Fields use the theme medium shape; the production highlight dialog uses separated 48dp selectable rows and rounded outlined swatches. Presets, immediate selection, ready/recovery gates and Done/dismiss behaviour are preserved. No camera viewport clipping or source/search behaviour changed.

Final full checks passed in 37s: Android and iOS simulator shared compilation, **219 host tests with zero failures/errors/skips**, and lint with **zero errors, 14 warnings**. All 215 baseline tests remain. Four added tests cover unmerged visual text geometry, all exact preset callback payloads/selection states, disabled real touch prevention, and an actual 240dp dialog's scrolling and reachable 48dp Done action. Production fixtures now use the actual 16dp gutter. Window-recomposer timing made several existing status tests fail during integration; a test-owned public Recomposer/BroadcastFrameClock with bounded idle checks and teardown now drives these fixtures. Assertions were retained. Two redundant type/null checks were removed after the successful run; host-test compilation was checked separately without changing behaviour or assertions.

Root inspected the corrected control/editor and palette renders in light/dark, enlarged text and constrained layouts. The independent reviewer inspected fresh renders and verified one correction cycle: **PASS WITH LIMITATIONS**, no remaining actionable finding. These are production-component fixtures with a placeholder camera, not full-device acceptance. Scoped manual Kotlin inspection, limited static style audit and whitespace checks passed; no configured formatter exists. AGENTS.md and all 41 historical evidence files are unchanged. No dependencies, persistence changes, APK, device run or external share. Real camera/IME/TalkBack/touchscreen and iOS runtime checks remain deferred. Logs and PNGs remain outside Git. Custom colour selection is a separate next stage.

## Status and contextual feedback — 2026-10-28

Design scope 4 replaces fragmented count text and the fixed raw camera diagnostic footer with a compact source-aware summary and explicit Recognition details. Imported/frozen results describe their own content; ordinary live feedback distinguishes waiting, unreadable text, no matches and current matches. Temporary outline retention is qualified separately from current counts. Source dimensions, line/outline totals and the bounded text sample move into a scrollable dialog. Storage, freeze/import and share feedback use consistent message groups with existing callbacks and conditions. Camera permission/settings/retry ownership, OCR/search/projection/retention and hidden-preview behaviour are preserved.

Final full checks passed in 38s: Android compilation, iOS simulator shared compilation, **215 host tests with zero failures/errors/skips**, and lint with **zero errors, 14 warnings**. All 203 baseline tests and their 33 source files remain unchanged. Twelve new tests exercise source priority, missing versus empty recognition, query/area readiness, current counts versus retained outlines, source-only bounded diagnostics, real resource-backed singular/plural copy, Details opening/Close/Back dismissal, callback counts, density-aware 48dp targets, text wrapping and measured compact-page scrolling. The actual production status region used by App is mounted to verify nonactive camera states, import-error/live coexistence, imported/frozen sample isolation, hidden-details closure and loading/saving/retry/reset-request conditions. A combined production controls/status fixture measures a weighted placeholder preview and visible recovery actions; it does not run the camera or full App.

Root and independent reviewer inspected eight fresh native host renders: combined light controls/status/recovery with placeholder preview, imported-image Details, unavailable-content dark Details, enlarged-text error, scrolled recovery, inline camera/import errors, selected frozen-source summary and recovery-confirmation request. Initial integration exposed incorrect resource imports and test-harness setup errors; a blank grouped-feedback render led to a wrapping column/action row. The host harness drains bounded virtual frames and lays out mounted dialogs before assertions. Failed attempts are not passing evidence. Initial independent review required inline camera recovery guidance/error styling, focused production wiring/gate coverage and scoped resource/indentation corrections. One correction cycle resolved all findings; independent verification: **PASS WITH LIMITATIONS**, no remaining actionable finding.

Scoped manual Kotlin inspection, limited added-line style audit and whitespace checks passed; no configured Kotlin formatter exists. A final unused test import was removed after the full run and host-test compilation was checked separately; no test assertions or production behaviour changed in that cleanup. No dependencies, schema, APK, device run or real external share were added. AGENTS.md and all 41 historical evidence files are unchanged. Pixel camera/source transitions, real permission/settings/retry, IME/focus, touchscreen/TalkBack and iOS application resource/runtime checks remain deferred for the user's device review. Logs, PNGs and per-run reports remain outside Git.

## Search-list selection and editor — 2026-10-28

Completed design scope 3: saved lists have wrapping names, pluralized term counts, a visible and semantic selected state, and separate Edit/Delete actions. The tonal New/Edit region groups labelled inputs, validation and Save/Cancel. App retains controller, draft and deletion-confirmation ownership; existing persistence gates, one-per-line terms, whole-page scrolling and hidden-preview behaviour are preserved.

Final checks passed in 40s: Android compilation, iOS simulator shared compilation, **203 host tests with zero failures/errors/skips**, and lint with **zero errors, 14 warnings**. All 198 prior tests and their 32 source files remain unchanged. Five native host tests exercise real resources/plurals, exact callback payloads/counts, disabled New/Save, controller/draft integration, density-aware 48dp touch bounds, nonoverlap, long-name wrapping and actual short-landscape scrolling. Unclipped layout bounds establish full viewport visibility of Save/Cancel, the returned query/Close lists controls and the enlarged-font selected group.

Root and independent reviewer inspected six native host renders covering light/dark, populated/new/error states, enlarged text and combined controls/manager scrolling with 50 lists. Component fixtures and combined manager-open fixtures are not full-device camera evidence. Initial independent review required stronger callback/gating and viewport assertions plus scoped brace corrections. One correction cycle resolved all findings; independent verification: **PASS WITH LIMITATIONS**, no remaining material finding.

Added only Compose Multiplatform resources 1.12.1, matching the existing Compose version, for scoped manager/editor strings and pluralization. Actual Android host resource resolution and iOS shared compilation pass. The installed Compose plugin connects resource synchronization to the existing Xcode embed/sign task, but iOS application resource packaging/runtime was not executed. The additional lint warning is a newer-version notice for this dependency; no version upgrade or suppression was introduced.

Scoped manual Kotlin inspection, limited added-line style audit and whitespace checks passed; no configured Kotlin formatter exists. AGENTS.md and all 41 historical evidence files remain unchanged. No APK, device run or external share was performed. Real IME/focus, TalkBack, touchscreen and iOS application resource/runtime checks remain deferred. Generated logs, PNGs and per-run reports stay outside Git; only durable documentation, source/resources, dependency declarations and meaningful tests are versioned.

## Material theme and main controls — 2026-10-27

Implemented design scopes 1–2: one shared light/dark theme with Android 12+ dynamic colour and older-Android/iOS fallbacks, shared spacing and Material shapes/typography. The main query/control region is grouped on one tonal surface with aligned active-search context and stronger Freeze/Resume emphasis. Existing actions, inset ownership, keyboard/list/compact preview hiding, actual preview measurements, OCR palette/geometry and source behaviour are preserved. Lists/editor and status redesign remain separate work.

Final checks passed in 23s: Android compilation, iOS simulator shared compilation, **198 host tests with zero failures/errors/skips**, and lint with **zero errors, 13 warnings**. All 193 previous tests and their 31 source files remain unchanged. New tests exercise Android dynamic/fallback resolution, used fallback role contrast, query/list/capture semantics and enabled states, 48dp actual touch bounds, nonoverlap, weighted remaining preview height, long-context wrapping and short-landscape scroll reachability. Early blank captures, fixture compile errors and failed harness assertions were corrected; those attempts are not passing evidence.

Root and independent reviewer inspected native host renders of production controls in light/dark 360×800, enlarged-text 360×520 and short-landscape 600×240 fixtures. They use a placeholder camera and omit unchanged status/footer; they do not prove full App or device behaviour. Independent review required correction of the semantics lookup and additional constrained-layout evidence. Correction verification: **PASS WITH LIMITATIONS**, no remaining actionable findings.

Scoped manual Kotlin inspection, limited added-line style audit and scoped whitespace checks passed; no configured Kotlin formatter exists. The user's pre-existing AGENTS.md edit is unchanged and excluded from staging; its trailing blank-line warning is outside the agent target. All 41 historical evidence hashes are preserved. No dependencies, schema, APK, device run or external share were added. Pixel 10 camera contrast, TalkBack, real wallpaper dynamic colours and full-device visual behaviour remain deferred user checks. Raw logs, generated PNGs and per-run reports stay outside Git.

## Adjustable live search area — 2026-10-27

Android live mode now offers an ephemeral normalized area with labelled percentage sliders, atomic Apply, Cancel and Full frame, plus a dashed labelled preview border. Only occurrences with every raw projected participant fully inside the selected area qualify. Default Full frame preserves legacy matching, including counts without geometry; Custom suppresses counts while its fresh, exact preview mapping is unavailable. Area edits and invalid mapping clear retention/smoothing, and revision checks suppress stale drawing. Background/disposal and starting Freeze/Import reset the area; frozen/imported/shared images remain full-source.

Final checks passed in 23s: Android compilation, iOS simulator shared compilation, **193 host tests with zero failures/errors/skips**, and Android lint with **zero errors, 13 warnings**. All 183 previous tests remain unchanged. New evidence covers inclusive boundaries, crossing and incomplete phrases, multi-term counts, invalid mapping/viewport/freshness, grace/smoothing resets, stale drafts/revisions, independent Android Matrix rotation fixtures and the actual lifecycle binding. The production dialog is mounted in a real ComposeView/Android dialog window and operated through slider/click/scroll semantics, including Apply, edited Cancel, Full frame, labels and constrained actions.

Initial independent review required two test corrections: the existing smoothing midpoint expectation and a meaningful pre-existing phrase whose missing participant geometry excludes the whole occurrence. Production code was unchanged by correction. Independent correction verification: **PASS WITH LIMITATIONS**, no remaining actionable findings. Scoped manual Kotlin inspection, a limited added-line style audit and whitespace checks passed; no configured formatter exists. Raw logs and per-run reports remain outside the repository, and all 41 historical evidence files are unchanged. No dependencies, persisted schema, APK, device run or real external share were added. Physical camera alignment, touchscreen/TalkBack, compact-window behaviour and source transitions remain device checks; host/source evidence does not establish them.

## Existing-feature validation — 2026-10-27

Fresh current-tree checks passed in 10s: Android and iOS simulator compilation, **183 host tests with zero failures/errors/skips**, and Android lint with **zero errors, 13 warnings**. A bounded source/test audit found no verified defect or critical missing boundary test in freeze/resume ownership, EXIF-normalized image import, captured-source sharing/cancellation/cache/provider access, or asynchronous saved-list/colour restoration. No application changes were needed for this audit.

Existing regressions execute all eight EXIF orientations with asymmetric pixels, retained-owner lifecycle/cancellation and worker leases, real PNG/provider reads, sharing invalidation/failure cleanup and strict persisted-state migration/restore/reset ordering. Physical camera rebind/capture timing, real gallery/provider availability, Sharesheet recipient behaviour and process-restart restoration remain user device checks; no APK, device run or external share was performed. This is host/source validation rather than exhaustive runtime acceptance.

## Live jitter and foreground-edge correction — 2026-10-27

Live display geometry is now separate from exact raw OCR/projection data. A conservative, uniquely associated small-motion filter bounds every displayed vertex within 3 preview pixels of its current raw vertex, converges to constant input and resets on larger/ambiguous/context changes. First detections remain immediate; raw counts, source guards and existing retention deadlines are unchanged. Preview and PNG renderers now draw every black under-stroke before foreground edges, preventing neighbouring black borders from erasing coloured sides. Viewport clipping remains intentional; still-image geometry remains raw.

The genuine red checkpoint ran against default-to-raw display API scaffolding before either correction: **22 tests, 3 expected assertion failures** in 26s. Native Canvas and actual rendered/encoded/decoded PNG overlap pixels were black instead of foreground, and the small-translation display still followed raw motion exactly. Isolated four-side rendering passed. The native test file and exact red jitter-test body are preserved unchanged in the final target. A later fixture incorrectly reused a retired generation; correcting that fixture preserved the production stale-generation guard.

Final checks passed in 34s: Android compilation, iOS simulator shared compilation, **183 host tests with zero failures/errors/skips**, and Android lint with **zero errors, 13 warnings**. All 165 previous tests remain. Evidence covers all palette colours and higher-density preview strokes, constant-input convergence, thresholds/shape/correspondence resets, source/target/search invalidation, omission/reappearance, paired display eligibility and unchanged grace/expiry. Initial independent review required one missing alternating-jitter regression. That test now drives the production owner and draw guard across six alternating x/y frames, verifies exact current raw quads/snapshots, checks every displayed vertex's Euclidean error and confirms reduced motion. Independent correction verification: **PASS WITH LIMITATIONS**, no remaining material findings.

Scoped manual Kotlin inspection, limited added-line static audit and whitespace checks passed; no configured Kotlin formatter exists. No new dependency, APK, agent device run or real external share. The user subsequently tested d30f403 and accepted stationary jitter, and clarified missing sides occur only near preview edges (intentional clipping). Movement responsiveness and broader camera behaviour remain unmeasured; host evidence proves the overlap mechanism without attributing every prior device symptom. Raw logs/reports remain outside commits and all 41 historical evidence files are unchanged.

## Latest user device feedback — 2026-10-27

The user confirms review checks 1–3 passed: keyboard/list action reachability, small-layout checks and state preservation. The saved-list complaint was withdrawn after clarifying that entries go one per line; commas remain literal. This is user-reported acceptance, not an agent device run or an exhaustive accessibility result.

Before d30f403, the user reported solid boxes shifting slightly while held still and occasional missing sides. After testing d30f403, the user says jitter is acceptable. Missing sides occur only near preview edges and are expected clipping. No device geometry trace or latency benchmark is available.

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

## Per-entry highlight colours and results — 2026-10-30

Compact presets and RGB controls are covered by production-component host checks, including three simultaneously visible 48dp sliders and reachable Apply/Cancel at 360×700 with enlarged text. Rendered light/dark picker and RGB captures and both ends of the scrollable 32-entry result list were visually inspected. Pure and native graphics tests cover deterministic per-term assignment, black-border contrast, repeated/phrase/zero counts, overlapping source projections, independent live expiry, matched-text payload preservation, and preview/decoded-PNG colors.

Android debug compilation, **261 host tests across 42 suites with zero failures/errors/skips**, Android lint (**zero errors, 14 existing warnings**) and shared iOS simulator compilation passed. The iOS task checks shared-source compatibility only; iOS introduction remains deferred. Whitespace and scoped added-line/import checks passed; no configured Kotlin formatter exists. Independent source-first review returned **PASS WITH LIMITATIONS**, with no actionable functional defects. The final cleanup uses the WCAG 2.4 luminance exponent and wraps the fixture and palette signature. Independent re-review verified the corrections and retained **PASS WITH LIMITATIONS**. The corrected-test aggregate passed with all 261 tests; subsequent signature formatting changes no behavior.

Physical camera/touchscreen behavior, TalkBack, real clipboard/share-sheet delivery and full App/device runtime remain pending. No APK was generated or installed and no device test was run. Source images and OCR remain memory-only; the saved-state schema is unchanged.

## Durable list-entry colour slots — 2026-10-30

Schema 4 stores one unique palette slot per list entry. Existing v1–v3 lists retain their previous visible colours and remain unchanged on disk until an ordinary save. Focused checks cover preserving survivor colours through edits, Unicode case matching, explicit-slot summaries and still rows, migration/reopening, malformed v4 assignments and byte preservation on invalid loads/saves. A production PNG check rejects a stale style-only share and confirms that a fresh share uses the updated slot colour.

The decoder enforces the 1–32 term limit immediately after the JSON array type check, before constructing terms or migrating slots. Otherwise valid v1–v3 fixtures with 33 unique terms verify original bytes remain intact, the real controller enters recovery and blocks writes, and persistence resumes after explicit reset. Independent source-first review identified this bound; independent correction verification returned **PASS WITH LIMITATIONS**, with no remaining actionable findings.

The final aggregate command passed:

```sh
./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain
```

Exit 0, **BUILD SUCCESSFUL in 37s**, 73 tasks (16 executed/57 up-to-date): **269 host tests across 42 suites, zero failures/errors/skips**, Android compilation, Android lint (**zero errors, 14 warnings**) and shared iOS simulator compilation. The iOS task checks shared-source compatibility only; iOS introduction remains deferred. Scoped Kotlin line-length/import/braces inspection and whitespace checks passed; no configured Kotlin formatter exists. No APK was generated or installed. Physical device/runtime, TalkBack and actual external clipboard/share delivery remain pending.

## Still-image result navigation — 2026-10-30

Focused result-dialog/navigation, selected-marker, large-text compact-control and query-control host tests passed. Saved rendered fixtures for the marker, selected Matches dialog and light/dark compact large-text controls were inspected at `work/result-navigation/renders/`.

The required aggregate command passed after review corrections: Android debug compilation, **283 host tests across 44 suites with zero failures/errors/skips**, Android lint and shared iOS simulator compilation (26s, 73 tasks; 6 executed). A Compose-hosted production action-binding regression covers stale Show A rejection after selecting B, current Show B success, out-of-filter B restoring all-row navigation/copy without focus or filter change on selection/dismiss, filtered boundaries and original row indices, immediate selection/viewport changes, replacement state holders, same-logical-ID token replacement, query and visibility rejection, and hidden zero-viewport copy with current owner validation. Compact controls visibly render matched term/text with equivalent semantic context; updated light/dark large-text render was inspected. The isolated existing query-control semantics test passed on rerun after an intervening host-test compilation failure; an earlier aggregate had recorded an assertion failure for that test, so its original cause is unproven. iOS compilation checks shared-source compatibility only. No APK or device test was run; runtime ownership/touchscreen and external clipboard delivery remain unverified.

## Optional literal whole-word matching — 2026-10-30

The required aggregate command passed: Android debug compilation, **288 host tests across 44 suites with zero failures/errors/skips**, Android lint (**zero errors, 14 warnings**) and shared iOS simulator compilation (47s, 73 tasks; 18 executed). Common matcher tests cover unchanged substring behavior, Unicode letter/number/mark/underscore boundaries including supplementary scalars, punctuation, phrase boundaries, rejected overlaps and UTF-16 offsets. Controller tests cover load/recovery gates, mode identity, queued saves/reset and preserving a stored mode when manual typing precedes load. Android storage checks schema-5 roundtrip, v1–v4 substring migrations without rewrite, slot preservation, malformed mode/extra-field byte preservation. Existing copy/share/navigation/projection guards are exercised with mode-only identity changes. Native Options tests verify selected/disabled radio semantics, selector callbacks, measured 48dp rows and compact scrolling; updated light and dark compact renders are saved under `work/whole-word/`. The aggregate log is in `work/whole-word/final-checks.log`.

The iOS task checks shared-source compilation only; no iOS application runtime or device test was run. No APK was generated. No Kotlin formatter is configured; changed Kotlin imports, modified line lengths and whitespace were manually checked, and `git diff --check` passes.

## Android plain-text saved-list transfer — 2026-10-30

The final aggregate command passed: Android debug Kotlin compilation, **315 Android host tests across 46 suites with zero failures/errors/skips**, Android lint (**zero errors, 14 findings**) and shared iOS simulator compilation. The 14 lint findings are existing tool/dependency update, SDK_INT and monochrome-icon warnings. Focused transfer suites include 15 Android owner tests and 11 common-format tests. Native Options coverage verifies the actual Import/Export action node bounds meet 48dp at 320×360 with 1.4× font scale, and scroll reachability of the terms-only help. The rendered fixture and aggregate logs are in the local scratch directory `work/list-transfer/`; they are not product files.

The iOS task confirms shared-source compatibility only; iOS transfer actions remain unsupported. No APK or device/provider run was performed. Actual system-picker/provider metadata, permissions, stream stalls and external destination behavior remain device acceptance checks. Exports contain terms only; no provider-specific behavior or external-file completion is claimed beyond fake resolver/stream host coverage. Correction regressions cover delivered-result invalidation after STOP/URI, picker-result consumption across owner recreation, duplicate callbacks, worker-dispatch failure, direct editor/session changes before provider open and while input/output open is blocked, returned-stream closure, actual owner gates, and the production import-draft helper through Cancel, name collision and ordinary Save. Correction aggregate log: `work/list-transfer/correction-checks.log`.

## Search context and result availability — 2026-11-04

The required aggregate command passed: Android debug Kotlin compilation, **332 Android host tests
across 48 suites with zero failures/errors/skips**, Android lint (**zero errors, 14 existing
warnings**) and shared iOS simulator compilation. Focused context, status, controller and source
availability tests passed (70 tests). Host captures for the light/dark large-text context and
unavailable Terms dialog were inspected; they are local evidence under
`work/search-context/renders/`.

The retained-only viewport fixture uses immutable Stage 2 reference heights from HEAD `a015704`
(252dp unselected, 184dp selected), not the changed full controls. Its current fixture measured
476dp unselected and 408dp selected for frozen and imported sources, a 224dp increase in both
cases. The earlier Stage 2 report's 276dp gain is historical and is reported separately. The
iOS task checks shared-source compatibility only. No APK was generated or device runtime test was
run; real-device, TalkBack and camera behavior remain unverified. Aggregate log and host captures are under `work/search-context/`.

## Stage 5 status microcopy — 2026-11-04

Status labels now use concise wording while preserving source prefixes and distinct waiting, empty,
zero-match, highlight-waiting and retained-outline meanings. Unavailable camera summaries show
“Camera unavailable”; the original technical reason remains in Recognition Details. The focused
production status UI suite passed, including the summary-to-Details assertion. Light unavailable,
dark error/details, light combined-preview and dark large-text renders were inspected from the
local `work/status-words/` captures. No status rows or layout policy changed.

The final aggregate command passed: Android debug compilation, **332 host tests across 48 suites
with zero failures/errors/skips**, Android lint (**zero errors, 14 warnings**) and shared iOS
simulator compilation. The focused UI suite passed after the final test edit. `git diff --check`
passed; no configured Kotlin formatter exists. iOS validation checks shared-source compatibility
only. No APK or device/runtime check was run; screen-reader and physical-device behavior remain
unverified.

## Phase 2 design foundation — 2026-11-04

The shared theme now uses a branded charcoal and mint dark palette, coordinated light palette, explicit Material typography/shape tokens, and the added 20dp screen-margin and 32dp section-spacing values. System light/dark selection and explicit overrides remain supported; Android dynamic color remains available by opt-in while the default uses the branded palette. The primary action is filled with a 52dp minimum height and 48dp minimum width. App root Surface explicitly paints `colorScheme.background`.

Focused `LiveTextFinderThemeTest` passed on Android API 35: **6 tests, 0 failures/errors/skips**, including dynamic-color opt-in, branded fallback roles, contrast, primary-action measurement/click, and native light/dark controls. Render captures saved under `work/redesign/foundation/` were inspected. The final aggregate validation is recorded in the appended follow-up after it runs. No APK/device run was performed; this foundation stage does not complete the screen redesign.

The final aggregate command `./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain` passed in 30s (73 tasks; 21 executed). Host XML confirms **333 tests across 48 suites, zero failures/errors/skips**. Android lint reported **14 warnings, zero errors**. Shared iOS simulator compilation passed. Full log: `work/redesign/foundation/final-checks.log`.

## Phase 3 Main scanner — 2026-11-05

The Main scanner groups title and Options, editable query and active search context in one header;
Terms, Lists and Results share one wrapping action band. Capture, pending-freeze cancellation and
import cancellation sit below the weighted viewport. Retained sources use one Return/Resume entry;
Edit search lives in their context band and Done remains available while editing. Existing owners,
callbacks, preview-hide policy and the single root insets owner are preserved. Active-list mode
emphasizes the list name, keeps the manual query visible as inactive guidance and places Use manual
beside that query. Shared secondary actions use neutral colours and 48dp targets; Freeze remains a
filled 52dp primary action. Live unavailable Results uses the explicit “Results unavailable” label
and opens the existing Terms/results dialog without showing a zero count.

The production-equivalent 360×800 fixture measured 744px of safe content and a 376px manual preview
(50.5%, largest region); saved-list preview measured 324px in both themes and remained larger than
the configuration card with the long list name, query guidance, count, mode, and actions visible.
Retained frozen/imported viewport measured 520dp unselected / 452dp selected in both light and dark
themes, against 476dp / 408dp floors. Saving false→true→false kept viewport bounds equal in every
fixture. The host preview is an opaque layout fixture, not a live camera runtime claim. Dark/light
manual and saved-list renders, the unavailable-results route, light/dark retained frozen/imported
selected/unselected renders, and short/large-text fixtures were inspected under
`work/redesign/scanner/correction/` and `work/redesign/scanner/`.

The final aggregate command passed: Android debug Kotlin compilation, **334 Android host tests across
48 suites with zero failures/errors/skips**, Android lint (**14 warnings, zero errors**) and shared iOS
simulator compilation. The correction aggregate log and full matrix measurements are under
`work/redesign/scanner/correction/`. `git diff --check` passed; changed Kotlin lines meet the
100-column limit. iOS validation checks shared-source compilation only. No APK or device run was
performed.

## Phase 4 Search lists and editor — 2026-11-05

List cards now group a radio-selectable body with separate Edit/Delete actions inside the same
surface. Selected lists use the primary container role; Delete uses the shared secondary action's
semantic error color. The heading and New action wrap together, an empty list keeps a working New
action with one concise hint, and New becomes neutral while the editor is open. The editor now
orders name and terms before muted guidance and a nonempty parsed preview; empty terms retain a
short hint and count, with validation messages before Save/Cancel. Existing state gates, parser,
callbacks, warnings, and first-five-plus-remainder preview behavior are unchanged.

The native host suite verifies callback isolation, selected-radio semantics, distinct nonoverlapping
48dp Edit/Delete targets, actual measured 52dp primary New/Save buttons, and scroll-reachable fields
and actions in a 320×420 `AppContentColumn` fixture. The existing 320×640, 1.5× font editor case
continues to verify viewport-contained Save/Cancel. Light populated/empty/selected and dark
invalid-editor/short-viewport renders were captured and inspected under `work/redesign/lists/`.
The final aggregate command passed: Android debug compilation, **336 host tests across 48 suites
with zero failures/errors/skips**, Android lint (**14 warnings, zero errors**) and shared iOS
simulator compilation. The warnings are the existing AGP (3), library update (8), SDK_INT (1), and
monochrome icon (2) findings. `git diff --check` and the 100-column changed-Kotlin line check passed;
no Kotlin formatter is configured. The iOS task verifies shared-source compilation only. No APK or
device/runtime test was run. Full aggregate log: `work/redesign/lists/final-checks.log`.

## Phase 5 Results and options — 2026-11-05

Search results now summarize the raw occurrence total from per-term counts and the number of terms
with matches, while unavailable results retain their explanation and unavailable markers without
numeric totals. Term rows remain in original order, retain every term/color/count, and give positive
counts restrained emphasis. Matches now surfaces its occurrence total, keeps matched text primary,
and presents term/line context secondarily. Its selected occurrence scrolls into view; Copy is the
52dp primary action, with reachable 48dp Show and Cancel actions. Options are grouped by search,
camera/images, and import/export gates, with left-aligned Material action rows and a local radio
group. Existing callbacks, enablement, visibility, and dismissal paths remain intact.

Focused production fixtures passed for 32-term scrolling, available zero counts, unavailable
results, options visibility/enablement/dismissal, transfer actions, and selected occurrence copy and
locate behavior. Native 320dp large-text captures cover results at the first and last terms, dark
positive/zero and unavailable results, selected Matches in light and dark, and Options in light,
dark, and transfer states. Captures were visually inspected in `work/redesign/results-options/`.
The final aggregate passed Android debug Kotlin compilation, **338 host tests across 48 suites with
zero failures/errors/skips**, Android lint (**14 warnings, zero errors**) and shared iOS simulator
compilation. `git diff --check` passed; no Kotlin formatter is configured. The iOS task verifies
shared-source compilation only. No APK or device/runtime test was run. Full aggregate log:
`work/redesign/results-options/final-checks.log`.


## Phase 6 Dialogs and recovery actions — 2026-11-06

The live-area editor now groups Size and Position controls and shows a schematic region guide
computed from the same normalized bounds as the sliders. Its boundary description explains that
position percentages measure travel through the remaining space. Apply reads current slider state;
Full frame retains the null-area callback. The color editor groups presets and RGB controls with a
fixed footer and scrollable body. Help and status details use shared spacing and actions; Delete and
Reset confirmations use the shared coral destructive action, and the two camera recovery actions
use the shared primary style. Search Results presents its total and matched-term summary vertically
so both values remain readable at 320dp and 1.5× text.

Native fixtures verify live-area center/edge/full-frame geometry, current-state apply and stale
revision behavior; color slider and footer reachability; Help's last iOS section and Details
metadata; Matches selection/copy and final-row scrolling; and action dimensions. Dialog fixtures
set font scale before Activity creation and assert the density inside the native dialog. Actual
320×420 captures cover the compact live-area editor, Help, Details and RGB editor at 1.5×; Results
use 320×470 at 1.5×, and Matches uses 320×480 at 1.5×. Light/dark Matches captures and the other
updated captures were regenerated and visually inspected in work/redesign/dialogs/.

Font-scale accuracy note: earlier validation entries described large-text previews based on a
ComposeView density provider, but native dialogs did not inherit that provider; their actual font
scale was 1.0. Those historical entries remain unchanged. The current fixtures set Android's
runtime font scale before creating the Activity, assert 1.5 inside the dialog, and the captures
listed above reflect that verified scale.

The final aggregate passed Android debug Kotlin compilation, **344 host tests across 48 suites with
zero failures/errors/skips**, Android lint (**14 warnings, zero errors**) and shared iOS simulator
compilation. git diff --check and the changed-Kotlin 100-column check passed. The iOS task verifies
shared-source compilation only. No APK or device/runtime test was run. Full aggregate log:
work/redesign/dialogs/final-checks.log.


## Phase 7 Android camera timing diagnostics — 2026-11-06

Debug builds now report bounded, mode-separated camera timing summaries under `LTF.CameraTiming`.
The 64-record buffer records delegate submission to SDK callback (recognition plus callback
scheduling), callback entry through snapshot adaptation, and only the synchronous `onState`
delivery interval. Live and capture statistics and cadence are separate. Tracker deliveries record
accepted/rejected counts, observed identity differences, actual frame dimensions and rotation;
unavailable rejection reasons remain unspecified. Summaries are event-driven and limited to one
per two seconds, with no recognized text, query or image logging. Release builds use no observer.
Diagnostic clock and logging failures are isolated from publication and capture ownership.

Three diagnostic host tests verify bounds, classified statistics, rate limiting and fresh instances.
Ten production capture-ownership tests include throwing observers and exact fake-clock stage
boundaries. Capture pixel copy, Compose scheduling and drawing are not measured; these diagnostics
do not establish full freeze-to-highlight latency or a recognition speed improvement. A device
trace from the delayed case is still needed before selecting a performance correction.

## Phase 8 Live scanner composition — 2026-11-06

The live scanner now uses a compact app bar, an opaque status band, the weighted camera viewport,
and one raised bottom dock for search configuration and capture. Manual query entry and list
identity no longer occupy the large top form card. The query remains at the same composition site
when IME, window height or font scaling hides the preview, retaining focus and edits. Options stays
reachable; compact content scrolls. Manager and retained-source controls keep their prior paths.

Native light/dark manual and list fixtures measured **744dp safe content and 400dp image viewports**
at 360×800 and density 1.0. Tests verify 48dp secondary and 52dp capture targets, action routing,
saving/cancel-capture stability, and unchanged image bounds across ordinary status messages,
including full unavailable/area/retained-outline wording. An actual App fixture verifies the
viewport disappears with IME insets and 1.5 font scale while the query remains focused and editable;
scrolling exposes Options and capture. Nine theme/layout host tests passed. Native captures use an
opaque camera placeholder and do not establish physical camera compositing or device IME behavior.

Final checks passed Android debug Kotlin compilation, **351 host tests across 49 suites with zero
failures/errors/skips**, Android lint (**14 warnings, zero errors**) and shared iOS simulator
compilation. `git diff --check` and changed-Kotlin import/100-column checks passed; no Kotlin
formatter is configured. The iOS task verifies shared-source compatibility only. No APK or device
run was performed. Native captures and the final aggregate log are in
`work/demo-2/live-dock/`; final aggregate: `final-checks.log`.

## Phase 9 Thin OCR outlines — 2026-11-06

Live, frozen, imported and exported OCR outlines now share one adaptive stroke rule: the black
outer stroke is capped at 2dp in Compose and 2 source pixels in exported images, and shrinks to
12% of the quad's minimum perpendicular span. The foreground stroke is half that computed width.
The shared geometry helper uses Double projections and rejects invalid or degenerate quads. Both
renderers compute each grouped width once and retain the existing all-black-then-color paint order.

Geometry tests cover the maximum width, 6px text height, rotated and vertical quads, skew, subpixel
geometry and invalid input. Native graphics tests render a 6px-high text-like glyph with a white
counter through density-2 Compose and the PNG renderer; both preserve the black stem and white
counter. A half-pixel PNG overlap checks the earlier foreground color against an exact production
reference. Existing renderer tests now account for antialiasing with fixed source-color blend
expectations and independent Paint/Stroke references. Negative checks reject missing foregrounds
and known wrong colors; source pixels, ownership, rotation and stale-share checks remain covered.

Final checks passed Android debug Kotlin compilation, **357 host tests across 50 suites with zero
failures/errors/skips**, Android lint (**14 warnings, zero errors**) and shared iOS simulator
compilation. `git diff --check` and the changed-line 100-column audit passed. The iOS task verifies
shared-source compatibility only. Density-2 Compose, PNG, and overlap captures were visually
inspected in `work/thin-outlines/`. No APK or device run was performed.


## Acceptance follow-up — 2026-11-07

The canonical aggregate command `./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain --rerun-tasks` completed successfully (73 tasks, 1m20s). Android host XML reports **357 tests across 50 suites, zero failures/errors/skips**. Android lint reports **14 warnings and zero errors**; shared iOS simulator compilation passed. The full run log is `work/acceptance-2026-11-07/baseline-checks.log`.

Reviewed native captures include density-2 Compose/PNG outline and overlap pixels, live manual/list docks, compact/large-font controls, landscape controls, retained hidden viewport, combined dark long-list content, still navigation, and light/dark large-text Matches. No actionable host-capture issue was identified. These are host fixtures; they do not establish physical display readability, runtime touch/focus behavior, camera composition, or assistive-technology semantics. No phone was connected. The device interaction, scaling, TalkBack, and camera-timing checklist remains pending at `outputs/acceptance-2026-11-07/README.md`.

The timing filter is `adb logcat -v threadtime -s LTF.CameraTiming:D '*:S'`. The diagnostic stages are delegate submission to SDK callback (`ocr`), callback-to-snapshot adaptation (`snapshot`), and synchronous `onState` delivery (`delivery`); pixel-copy, Compose scheduling/drawing, and end-to-end freeze-to-visible latency are outside coverage. No device timing values are claimed.

## Direct live-area editing — 2026-11-07

Added normalized move and four-corner resize helpers with anchored opposite corners, 20% minimum bounds, edge clamping and deterministic clamped handle hit targets. The production preview overlay uses down-position hit testing, total preview-local displacement, slop-crossing movement, multi-pointer cancellation and 48dp accessible corner controls with directional resize/move actions. An editor session keeps draft/full-frame changes temporary, rejects stale tokens/revisions and commits only through the existing area owner. Precision-slider Apply returns a draft when nested; its standalone fallback remains guarded by the owner revision.

Host coverage includes all-corner geometry, invalid viewport/delta inputs, minimum/clamp behavior, edge/overlap hit selection, editor Apply/Cancel/Back-equivalent invalidation/full-frame and stale-session behavior, slider draft-only return, and Android MotionEvent down/move/up for interior move, corner resize, minimum clamp, outside no-op and multi-pointer cancel. Five editor captures (light, dark, paper background, minimum and full frame) were inspected; the paper capture confirms the black under-stroke preserves the dashed boundary on white. The canonical command `./gradlew :androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 --console=plain` passed: 363 host tests in 50 suites with zero failures/errors/skips, Android lint 14 warnings and zero errors, and shared iOS simulator compilation. `git diff --check` and the changed-Kotlin 100-column audit passed. The final log and captures are in `work/direct-area/`. Host tests do not validate a physical camera feed, actual device density/input timing, or TalkBack traversal; device testing remains pending.

### Accepted review correction cycle — 2026-11-07

The editor now tracks the original pointer manually from down through touch slop; any second pointer cancels before draft mutation or event consumption, and lifting the original before slop leaves the draft unchanged. Each named corner exposes left/right/up/down resize actions; their production step helper is tested for inward/outward changes and opposite-edge anchoring. The movable-region accessibility node now follows the selected bounds. Precision dialogs use an opening token for updates and dismissal, including standalone Apply/Full frame callbacks; stale Apply, Cancel and dismiss callbacks cannot affect a reopened dialog. Opening Precise from a pending Full frame starts at 100%×100%, and the untouched result remains the null draft.

The full `:shared:testAndroidHostTest` suite passed after these corrections. Added regressions cover a custom-area down/move/up drag, multi-touch before slop, original-pointer lift before slop, all 16 corner/cardinal resize cases, bounds and labels for the movable region and four handles, stale dialog actions across reopen and editor-session changes, and Full frame → Precise → Apply without edits. Refreshed overlay and precision screenshots are in `work/direct-area/` and were inspected. The coordinator owns the final canonical aggregate run after this source revision; physical-device, real camera and TalkBack checks remain pending.


Final verification also guards the Precise opener itself against captured callbacks from an older
editor. The App uses the production session's tested callback, checking its current token, owner
revision and live source/visibility before opening a dialog. Independent correction verification
passed with device limitations. The final canonical aggregate passed in 43s: **368 host tests in
50 suites, zero failures/errors/skips**, Android lint **14 warnings and zero errors**, Android debug
compilation and shared iOS simulator compilation. No APK or device run was performed.


## Documentation, release and performance review — 2026-11-07

Current architecture/requirements and the implementation record now describe the live dock,
schema-5 preferences, per-entry colours, branded theme defaults and direct area editing. The user
reported positive testing feedback for the preceding build; this is not a completed historical
scenario matrix and does not validate the new editor. One post-commit import-order correction is
included; the current import/whitespace checks passed.

Release preparation adds explicit legacy and modern saved-search backup allowlists, keeps the
foreground vector byte-identical when moving it out of the obsolete v24 directory, and adds a
monochrome layer to both adaptive icons. The merged release manifest retains the existing app ID,
version and non-exported share provider, with release debugging disabled by default. Release Kotlin,
manifest/resource processing and lint passed together with the canonical debug/shared checks.
The final host task was UP-TO-DATE; its current XML contains **368 tests across 50 suites, zero
failures/errors/skips**. Both debug and release lint report **11 warnings and zero errors**; the
remaining warnings concern tool/dependency updates. No dependency update or lint suppression was
introduced. Moving the foreground initially exposed stale debug incremental resources; a forced
rerun resolved that build-cache issue. Final log: `work/release-readiness/final-checks.log`.

The source-based performance review records per-operation resource limits and existing ownership,
bounds and quota coverage in architecture.md. It establishes no whole-process memory, throughput,
thermal or battery measurement and introduces no speculative optimization. User device checks remain
pending for the new gestures, TalkBack, themed launcher, backup/restore and sustained performance.
No APK/bundle, signing, installation, publishing or device run was performed.

## Shared live and retained search dock — 2026-11-08

Live, frozen and imported search now use one editable query and primary-action dock at the same
App composition site. Freeze frame, Resume scanning and Return to camera occupy the same full-width
52dp slot. Retained-image zoom, occurrence navigation/copy, Terms, Results and Lists remain
available through the existing controls. More contains Lists/share, Options stays in the app bar,
and share cancellation calls the current share owner. The retired collapsing inspection header
was removed; its source-return dispatcher remains in use. Direct area editing keeps its own footer.

The source-equivalent 360x800 native ComposeView fixtures use the same query, font scale, window
insets, app bar, status placement, retained tools and dock siblings for live, frozen and imported
states. Query bounds were x=16..344dp, y=576..640dp; primary-action bounds were x=16..344dp,
y=744..796dp for all three states in light/dark themes, with and without a selected occurrence.
Viewport heights measured 400dp live, 344dp frozen/imported by default, and 276dp frozen/imported
with an occurrence selected. The earlier retained-only fixture measured 528dp / 460dp before the
shared dock. Every current viewport remained positive. The compact 320x720 font 1.4, 360x640 font
1.25 and 800x360 font 1.25 fixtures use the production sibling hierarchy and insets and show the
query, contextual actions and primary slot through the existing scrolling path. The actual App
host fixture preserved query focus across IME visibility and large-font/hidden-preview changes.
These are host-rendered checks, not physical-device camera checks.

Validation after the dock changes: the focused dock, retained source and App IME tests passed. The
canonical `:androidApp:compileDebugKotlin :shared:testAndroidHostTest :androidApp:lintDebug
:shared:compileKotlinIosSimulatorArm64` run passed after the final source revision: 368 host tests
in 50 suites, zero failures/errors/skips, Android lint 11 warnings and zero errors, and iOS
simulator compilation. The stage log and inspected captures are held in the ignored coordinator
workspace. Physical-device camera, keyboard and accessibility checks remain deferred to the user.

## Feedback language and camera recovery — 2026-11-08

Options now says “Contains text” and explains whole words with the cat/catalog example. Both query
fields say “Find text”; visible live-area labels and resize handles say “Scan area”. Custom colors
use a localized “Custom color” label with the existing swatch, while the advanced RGB editor keeps
the exact hexadecimal preview and Apply/Cancel behavior. Help now describes the bundled on-device
recognizer, system photo picker, clipboard, share sheet and saved Android settings without claiming
that system handoffs or backups never leave the device.

Search-result availability logic is unchanged. A separate resolver supplies import, permission,
startup, pause, error, cause-neutral unavailable, hidden-preview, area-mapping and current-text guidance;
it returns no explanation for available results or a search with no usable active terms. The
results action remains “Search results”, and unavailable counts remain hidden. Camera preview
feedback offers its existing Retry camera action after a preview stream pauses, as well as after an
error. The retry callback is guarded by the preview attempt it belongs to. Paused feedback and Retry are
stacked with non-overlapping bounds and a minimum 48dp recovery target. Host tests combine the
production live frame tracker, camera feedback layer and status region through Starting, stream,
pause, retry, replacement attempt, hidden teardown/fresh start and error states; startup produces no
paused status or fabricated match. Stale stream and stale retry callbacks cannot advance a newer
attempt. Importing/freezing take priority over residual camera feedback; missing retained sources get
recovery guidance, while existing retained content waiting to be recognized remains Waiting.

Final canonical validation passed: `:androidApp:compileDebugKotlin`, `:shared:testAndroidHostTest`
(372 tests, 0 failures, 0 errors, 0 skipped), `:androidApp:lintDebug` (11 warnings, 0 errors) and
`:shared:compileKotlinIosSimulatorArm64`. The exact command log is
`/Users/artemii/Documents/Codex/2026-10-30/rea/work/journey-refresh/feedback/final-checks.log`.
Host-rendered Compose checks cover the updated options/help/results/camera feedback UI at compact
and large text sizes. No physical-device, camera/OCR accuracy or timing, TalkBack, system picker,
clipboard, share destination, or backup/restore checks were performed.


The correction-cycle Compose captures are in `/Users/artemii/Documents/Codex/2026-10-30/rea/work/journey-refresh/feedback/`: `camera-feedback-paused-retry-light.png`, `camera-feedback-paused-retry-dark-large-text.png`, `search-results-importing-dark.png`, `search-results-freezing-dark.png`, `search-results-missing-import-dark.png`, `search-results-missing-frame-dark.png`, `options-sheet-dark-compact.png`, `options-sheet-light.png`, `highlight-palette-dark-custom-label-large-text.png`, `highlight-palette-light-ready-gated.png` and `custom-highlight-editor-dark-large-text.png`. The paused captures show the message and Retry with a visible gap; missing/import/freezing dialogs show recovery text without stale numeric counts; options/palette captures show friendly labels, and the advanced editor retains its exact hex preview. The final host XML totals 372 tests across 50 suites, with zero failures, errors or skips. `git diff --check`, changed Kotlin import-order audit, 100-column audit and case-insensitive visible “live area” wording audit passed. No physical device, OCR accuracy/timing, camera permission/system-picker, TalkBack or share/backup handoff verification was performed.

## Live capture assistance — 2026-11-08

Hardware capture controls now use production common lease ownership and a narrow CameraX adapter.
Focused ownership/adapter/rendered-control/tracker/frozen-owner/lifecycle checks passed
**27 tests**, covering
stale UI leases, updates, detach and delayed completion; observed zoom/torch capabilities; AF-only
focus and actual result flags; synchronous, failed and cancelled futures; optimistic zoom state;
rejected completion executors including background-to-main recovery; snapshot/generation and
capture gates at zoom start/end; freeze illumination; cleanup failures; and unsupported controls.
Production control-row renders at 320dp and 1.5× text were inspected, including 48dp targets,
zoom-boundary disabling, observed light toggle semantics and transient-readiness bounds.
Lifecycle tests verify permission refresh without feed replacement across pause/resume, and one
restart epoch plus retired-session teardown across a stop/start coalesced before a Compose frame.

After the final source changes, the focused command passed again and the canonical aggregate
passed Android debug Kotlin compilation, the full host suite (**382 tests across 54 suites, zero
failures/errors/skips**), Android lint (**zero errors, 11 warnings**) and iOS simulator shared
compilation. The final aggregate completed in 26s, with 17 tasks executed and 56 up-to-date.
Whitespace, changed/new Kotlin 100-column checks and one ASCII-sorted explicit import-block checks
pass. No Kotlin formatter is configured. Existing validation entries remain unchanged. Logs and
native control renders are outside the checkout in the capture scratch directory. A generated-output
hashing stall was diagnosed, its daemon trace/log and generated outputs preserved there, and one
clean-output recovery succeeded. Final counts use only clean regenerated XML reports.

These are production owner/adapter/component host checks with controlled camera futures and real
LiveData, plus session wiring inspection. They do not establish physical camera behavior or a full
App/device render. Hardware zoom/crop/rotation alignment, autofocus effectiveness, flashlight
behavior through real capture/retry/background transitions, TalkBack, and rapid device lifecycle
transitions remain pending. Retained-image transforms, OCR engine and analyzer execution were not
changed; existing source/projection/area/copy/recognizer-lifetime tests remain in the passing suite.
No APK or device test was run; iOS camera support remains explicitly unavailable.


### Integrated camera controls and feedback — 2026-11-08

The accepted camera patch was integrated with the accepted language/recovery changes. The resolved
camera feed preserves the tested Starting-before-session and attempt-bound Retry helpers, spaced
paused recovery UI, and the stop-only feed restart key while passing live control ownership/context.
The complete prior validation history is preserved. Independent integration review passed with
device limitations. The canonical four-task run passed on the merged source: **386 tests across
54 suites, zero failures/errors/skips**, Android lint **11 warnings and zero errors**, Android
compilation and shared iOS simulator compilation. Log: `work/journey-refresh/integration/final-checks.log`.
The isolated worktree had older generated duplicate report copies; canonical report filenames
correspond to its recorded 382 tests/54 suites. Merged MAIN reports are the integration evidence.
No APK, installation, device OCR/alignment/focus/torch, or TalkBack check was performed.

## Still contextual copying and dialog ownership — 2026-11-08

The occurrence dialog now offers Copy line alongside Copy matched text, with Show on image as its
primary action. Both copy payloads validate the selected original occurrence and its line context
against reconstructed OCR elements, preserving normalized whitespace and original case. The current
source ID, exact snapshot, owner token, search/mode, selection and pre-write context guards remain
in force; copying remains available when locating the image is unavailable. Dialog-opening and
request identities prevent an earlier suspended clipboard completion or retained callback from
changing a reopened dialog. Disposed remembered sessions are invalidated, including replacement
with the same keys. A platform clipboard write that has already started cannot be revoked; tests
separately verify zero writes before a stale start and suppressed UI completion after a valid start.

The production content assembly reproduced caption-dependent viewport resizing when moving from a
short navigation caption to a long saved-list term. A one-line ellipsized caption now
keeps the viewport and focused transform stable across previous/next navigation; its full semantic
description is retained. This reproduction established resizing, not a downstream transform reset.
The dialog actions scroll into full view at 320×480dp/1.5× and 320×360dp/2× font, with at least 48dp
hit targets; short windows require scrolling. Native actions exercise matched and line clipboard
entries, selection, Show on image and cancellation, including line copy with locating unavailable.

Focused coordinator/session/row/native-Compose checks passed **27 tests**. The expanded frozen-owner
coordinator suite then passed **3 tests**, including exact normalized line context from OCR elements
and zero writes for a replaced owner sharing the same snapshot and frame identity. The final
canonical command passed Android debug Kotlin compilation, the complete host suite (**396 tests across
55 suites; zero failures, errors or skips**), Android lint (**11 warnings, zero errors**) and shared
iOS simulator compilation. Explicit ASCII-sorted contiguous imports, changed/new Kotlin 100-column
and tab checks, `git diff --check`, and preservation of this document's historical prefix passed.
No repository Kotlin formatter is configured. Logs and 117 regenerated native PNGs are outside the
checkout in `/Users/artemii/Documents/Codex/2026-10-30/rea/work/journey-refresh/context-copy/`.
The final log is `correction-final-checks.log`; XML counts are preserved in
`correction-results-summary.txt` and `correction-host-results/`. Representative captures are
`retained-dark-live-selected-false.png`, `retained-light-frozen-selected-true.png`,
`still-matches-dialog-dark-tall-large-text-all-actions.png` and
`still-matches-dialog-dark-short-double-text-actions.png`. Dock captures show current Find text and
Contains text language. These are host fixtures and production workflow checks, not a full App or
physical-device render. Physical clipboard behavior, TalkBack, camera/OCR accuracy, capture-control
hardware behavior and real crop/rotation/zoom alignment remain pending. No APK, device query,
installation, network OCR, alerts, persistence or engine changes were performed.
