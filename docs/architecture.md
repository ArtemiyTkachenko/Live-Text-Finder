# Camera / OCR foundation

## Boundaries

`androidApp` hosts shared `App`. `commonMain` owns the screen, diagnostic state, and immutable OCR result types. An expect/actual camera composable supplies a native preview and reports state; all camera and OCR implementation details stay in `androidMain`. `iosMain` renders an explicit Android-only notice.

Android owns a session containing PreviewView, camera use cases, and one bundled Latin recognizer. Use CameraX MlKitAnalyzer in original image coordinates, wrapped only to capture matching frame metadata. Copy text, line/element bounds and corner points before publishing; do not leak ML Kit/Android objects. Common snapshots use adapter-normalized full-buffer upright coordinates, never preview coordinates. The pinned bundled SDK supplies upright bounds but pre-upright corners; CS-003 normalizes corners once during result copying. Record raw size, rotation, crop, timestamp and sensor-to-buffer matrix. Shared projection now reverses full-buffer OCR rotation and the frame sensor-to-buffer transform, then applies the actual PreviewView sensor-to-view transform. See [search requirements](search-requirements.md) for coordinate conventions and limits.

## Resource and concurrency decisions

Bind rear preview and analysis together to the Compose lifecycle owner. KEEP_ONLY_LATEST retains at most the newest waiting frame; MlKitAnalyzer owns closing submitted ImageProxy instances after asynchronous recognition. Do not launch per-frame coroutines or queue recognition tasks. Ignore results after disposal or when the owner is not started. Unbind only owned use cases and close the recognizer after the current analysis completes.

Permissions are requested through Android Activity Result APIs following an explicit button tap. Denial keeps an explanation and retry; permanent denial offers system settings. Recheck permission on resume. Surface setup, camera and recognition errors with recovery.

The foundation added no image/text persistence, text logging, search, overlays or iOS camera functionality. CS-001 adds a pure common matcher and transient shared query/count UI, described in [matching requirements](search-requirements.md). It retains the original eligible snapshot and element indices as provenance; CS-002 adds shared projected element polygons and Canvas outlines, supplied by Android actual-view mapping. Use-case/analyzer/recognizer ownership invariants remain unchanged.

## Saved search sessions

Common code owns immutable saved-list/session values, validation, asynchronous state ordering and the list editor. The Android host retains one application-scoped controller across Activity recreation. Its store lives in `shared/androidMain` and uses versioned app-private JSON through `AtomicFile`, a process-owned serialized background worker and main-thread callbacks. Reads complete before startup edits can be saved; newer manual typing or explicit clear wins over loaded text without discarding the stored library. Pending writes coalesce to the latest state. Load failures keep recovery visible and prevent writes until a confirmed reset; reset completion preserves manual edits made while it was running. The iOS placeholder uses an explicit in-memory store.

The saved preference document contains manual query, list IDs/names/entries, active selection and global highlight colour. Shared validation applies on Android reads and writes, with strict JSON primitive/version checks. OCR results/images remain transient. Multi-term matching counts independent entries separately, deduplicates shared element outlines and scopes retention association to term identity. Draw-time eligibility reads the live session identity to reject outlines composed before a search change.

## Memory-only freeze

An app-scope Android owner retains a copied bitmap and immutable source snapshot independently of the visible camera renderer. A request captures the next accepted analysis proxy's metadata/ticket, copies its full raw pixels off the main thread, then hands that same proxy to ML Kit. Only its OCR callback may publish the pair. A process-wide single-flight worker rejects concurrent copies rather than queuing stale work. Conversion is bounded to 4,000,000 pixels; ordinary live frames never copy bitmaps. Candidate cancellation releases copied pixels while ML Kit retains responsibility for its original proxy; recognizer release remains deferred until analysis finishes. Lifetime synchronization covers cleanup if UI handoff fails.

Common state coordinates request identity, cancellation and the five-second deadline. Android keeps all bitmap/proxy types native and owns lifecycle cleanup. Frozen acceptance closes live camera composition; hiding its frozen renderer for keyboard/list editing retains the pair. Resume, lifecycle stop and owner disposal discard it. Frozen matching uses its exact snapshot without live receipt expiry or dropout retention. Shared full-buffer upright Fit geometry and native raw-buffer rendering use the captured rotation once; the frozen path does not reuse PreviewView mapping. Draw-time guards reject changed owner/frame/search state. Retained source images/content are memory-only. Explicit annotated sharing uses the separate temporary cache boundary below.

## Single-image import

Android registers the system image picker independently of camera permission and preview visibility. A request ticket distinguishes the original picker callback from cancelled or replaced requests. Only an outstanding picker request may survive the external picker stop; a returned URI waits for the foreground lifecycle before processing. Processing and retained pixels are invalidated on ordinary backgrounding, disposal or recreation.

One process-wide operation gate spans provider reading, bounded decoding and recognition completion. Cancellation invalidates publication immediately while blocked provider/native work drains before another operation starts. The decoder reads at most 16 MiB once, checks actual JPEG/PNG/WebP format and dimensions, samples conservatively before allocation and applies all EXIF orientations. OCR receives that same upright bitmap with rotation zero. Imported snapshots carry normalized dimensions and a source identity, without camera metadata or the camera corner correction. Shared literal matching and Fit projection retain separate camera and imported provenance. No URI grant, image or recognized text is persisted.

## Annotated still sharing

Android captures one immutable source/search request only after validating the current retained owner and session. One process-wide worker renders the full upright source with existing matched-element projection and selected-colour outlines over a black under-stroke, then encodes a size-capped PNG directly to a unique partial cache file. Cancellation invalidates publication immediately; the worker retains the source while native work drains and recycles only its own rendered output. Main-thread handoff rechecks current source, search, colour and lifecycle before launching the chooser.

Only explicit Share creates a temporary derivative in cacheDir/shared-images. A narrow FileProvider exposes that directory with read-only grants. Successfully handed-off files become cache-owned and survive stop/disposal/chooser return; unhanded files are removed on failure or cancellation. Cleanup and capacity reservation share operation ownership, with 24-hour expiry, 128 MiB total, 16-file and 20 MiB output limits. Unexpired handed-off files are preserved when quota is full. There is no gallery save, persistent history, recipient selection or delivery acknowledgement. iOS sharing remains unsupported.

## Primary references

- https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html
- https://developer.android.com/media/camera/camerax/mlkitanalyzer
- https://developer.android.com/media/camera/camerax/analyze
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://developer.android.com/training/permissions/requesting

## Preview projection and freshness

Android captures copied row-major matrix values on the main thread and associates each result with its own frame plus a unique camera-session/generation ticket. Layout/pre-draw, stream/lifecycle events, frame submission and result delivery inspect actual native target mapping; target rotation/size/matrix changes immediately invalidate projection and reject old tickets. Retries create a new native PreviewView and session identity. No per-frame rebinding occurs. IDLE, zero/unattached view and lifecycle stop clear current results. Errors/permission/session disposal clear geometry; old callbacks cannot revive it.

A session owns one reusable Handler expiry runnable, rescheduled to the current result's receipt deadline and removed on disposal. Receipt time and UI presentation deadlines use one common process-wide monotonic clock origin, not CameraX sensor timestamps or wall-clock time. After 1,500ms without a fresh result, snapshot/projection/count stop being current; query stays in Compose memory. Pure injected-clock tests exercise timing without sleeps.

Common code retains immutable projection data, validates/maps/deduplicates element polygons and draws in preview-local physical pixels. Native preview and shared Canvas fill the same Box; exact pixel size must agree. Invalid mapping suppresses outlines without discarding otherwise-current recognized counts. Clip the draw viewport rather than clamping polygon vertices. This is newest-recognized-frame display with a freshness limit, not object tracking or a runtime alignment guarantee.

## CS-003 adapter correction

User device feedback exposed systematic displaced/quarter-turned outlines despite correct text/counts. Earlier pure projection fixtures began with assumed-upright points, leaving actual SDK result admission untested. Pinned bundled bridge bytecode and host-executed SDK geometry helpers show that its element/line bounds are rotated to upright while its separate corner list is not. `toSnapshot` now applies the captured full-buffer quarter rotation to corners only; SDK bounds remain unchanged. Downstream sensor/view projection is unchanged. See [search requirements](search-requirements.md) for boundary table and [validation](validation.md) for real red/green adapter tests and pending device retest. No native OCR/device correction success is inferred from host execution.

## Bounded presentation stability

Shared presentation state associates nearby current matched occurrences conservatively and one-to-one. Each occurrence retains exact source snapshot/projection/element provenance; missing regions may remain briefly while other matches update. Each missing deadline is min(first missing receipt + 300ms, last observed receipt + 1,500ms), without renewal by repeated misses or recomposition. Latest OCR diagnostics/counts remain distinct. Query/invalidation/session/error/expiry clears retained regions. Current observations replace raw geometry immediately; overlapping/displaced old copies are retired. Live display geometry is separate: only a uniquely matched, continuously observed element in the same search/context/target may blend its previous displayed quad with current raw vertices. The bounded filter below reduces small positional noise; it never alters raw OCR, matching, association bounds or source eligibility. No synthetic mixed OCR snapshot or image-based motion tracking is introduced. A common monotonic clock connects Android receipt metadata to the cancellable Compose deadline. Compact status rows separate routine results from optional recognition details; IME/compact layouts prioritize input and hide the camera preview and secondary views. The user chose this behavior after device testing; preview shrinking is deferred.

Live stabilization blends at 0.5 only when corresponding displayed/raw vertices are at most 6 preview pixels apart, raw quads retain winding and unambiguous element/order correspondence, edge-angle changes are at most 5 degrees, and width/height changes are at most 10%. Validate the blended quad and cap displayed error at 3 pixels from current raw geometry; snap to raw below 0.125 pixel error. First observations, larger changes, ambiguity, reordered/segmented content and reappearance use raw immediately. Missing groups hold their existing display only within the unchanged grace/expiry limits. Frozen/imported previews and shared images retain raw geometry.

## Highlight appearance

Draw all black under-strokes before any foreground edges, so a later neighbouring outline cannot erase an earlier coloured side. Keep the same order in preview and PNG rendering; retain viewport clipping rather than manufacturing offscreen borders.

A canonical common palette supplies stable preference keys, labels and opaque ARGB values to shared preview drawing and native PNG rendering. Colour is independent of semantic search identity, geometry and retention. Android extends its existing strict AtomicFile schema from version 1 to version 2; valid version 1 data defaults to Yellow without an eager rewrite. The existing session controller serializes colour changes with query/list writes; edits are gated until healthy load completion. iOS uses its existing in-memory store.

App reads current colour inside the Canvas drawing callback. Share requests capture a colour value and separately validate current colour before handoff, preserving immutable rendering and rejecting stale publication. Existing source/search/lifecycle/cache ownership remains intact. No extra store, renderer mapping table or dependency is introduced.

## Live search area

A small common owner holds an optional normalized preview rectangle and revision independently of persisted search identity. Common filtering reuses validated raw element projection, checks every occurrence participant and rejects partial phrases. Full frame bypasses filtering; Custom has explicit readiness when its actual preview mapping is unavailable. The live presentation captures area revision and filters before retaining/smoothing occurrences; draw-time current revision checks complement existing exact raw source guards.

The common control uses a cancellable draft and labelled sliders. Background/source resets invalidate stale drafts before Apply and reset area independently of camera renderer visibility. Android lifecycle binding sits at App scope, while iOS keeps a no-op binding and unsupported controls. Explicit source handlers reset before Freeze/Import; full-source frozen/imported search and shared PNG requests do not carry region state. Area selection affects match eligibility, never OCR buffers, camera generations, transforms or persisted preferences.

## Material theme and primary controls

A small shared `LiveTextFinderTheme` supplies fallback light/dark colour schemes, Material typography/shapes and shared spacing. A narrow platform colour provider uses Android 12+ dynamic colours and fallback schemes on older Android and iOS. Android context and dynamic-colour APIs remain in Android sources. OCR palette values, camera backgrounds/scrims, projection and exported-image colours remain independent of theme personalisation.

The main search/control region uses one restrained tonal surface, consistent content alignment and supporting typography for the active search. The main card and list/editor regions use a shared 16dp outer gutter with deliberately aligned content insets; text fields use the shared medium shape. Highlight/Help actions wrap from the start, and selectable highlight rows use 48dp targets with rounded outlined swatches. Freeze/Resume use a stronger tonal action while secondary actions stay directly available. The existing inset owner, conditional preview policy, actual measured preview bounds and source callbacks remain in place. List-manager and status presentation are described below.

## Search-list presentation

The list manager is a shared stateless presentation component. It receives saved-list, selected-ID and editor snapshots plus callbacks; App retains controller calls, draft ownership and delete-confirmation state. Selection targets, Edit/Delete actions and the tonal editor are separate interaction regions. The existing AppContentColumn owns whole-page scrolling, so manager visibility continues to hide the preview without introducing nested section scrolling.

Scoped shared string resources provide list/editor labels, list-specific accessible action labels and term-count plurals. This presentation layer does not change validation, persistence, list activation, search semantics or source ownership.

## Status and contextual feedback

Shared presentation derives feedback from the displayed source: an imported image, a frozen frame, an import/freeze operation, or eligible live camera state. Current search occurrences remain separate from temporarily retained outlines. Missing recognition, unreadable content and a readable zero-match result have distinct meanings. This layer consumes existing immutable states without changing recognition, freshness, matching, projection or source ownership.

An explicit Details dialog holds source-specific recognition diagnostics and the bounded text sample. It reads the selected source rather than falling back to a previous live camera snapshot. Diagnostic disclosure is secondary and follows the existing hidden-preview policy. Operational messages retain their existing recovery/cancel/reset callbacks and conditions; Android continues to own camera permission/settings and retry actions. No new persistence, image/text logging or automatic transitions are introduced.
