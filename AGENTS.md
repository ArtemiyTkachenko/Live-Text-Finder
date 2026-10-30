# Project guidance

Follow the [workflow protocol](docs/agent-workflow/protocol.md) for roles, sequencing and stop conditions. The coordinating chat owns dispatch, task context and final acceptance; roles do not enforce permissions or dispatch work. Keep handoffs, session state, current agendas and user-authorization history in chats, outside the repository. Do not create repository mission/handoff documents or coordinator state files. Durable product requirements, architecture and validation summaries stay in the project documentation. Existing historical stage evidence remains local and Git-ignored.

## Project constraints

- Keep application UI and platform-neutral OCR snapshots in `shared/commonMain`.
- Keep CameraX, ML Kit, Android permissions, lifecycle and image buffers in `shared/androidMain`. `androidApp` is only the host.
- Keep the existing iOS framework and explicit unsupported-camera placeholder. Do not implement iOS OCR without approval.
- Prefer small classes/functions, narrow visibility, immutable snapshots and explicit ownership. No Kotlin data objects.
- Do not add DI, navigation, storage, backend or ARCore without a demonstrated authorized need.
- Use the version catalog and existing official KMP Android library plugin. Avoid build customizations unless a demonstrated problem needs one.
- Run checks required by the approved product scope. Record what actually ran; compilation does not prove device behavior. APK generation is left to the user unless explicitly requested.
- Preserve historical verification evidence. Do not commit, push or publish without explicit authorization.


## Kotlin Style Guide (Mandatory for Coding Agents)

**Authority:** [Android Kotlin style guide](https://developer.android.com/kotlin/style-guide).
All newly written or modified Kotlin code (`.kt` and `.kts`) must follow that guide and the
agent-specific rules below. Where this summary is incomplete, follow the linked guide. Where
an established project convention or checked-in formatter configuration explicitly conflicts
with a rule here, do not silently invent a third style: follow the project configuration and
flag the conflict in the final report.

### Agent responsibilities

- Treat formatting as a requirement, not a suggestion. Produce correctly formatted code on the
  first pass rather than expecting a reviewer to fix it.
- Apply these rules to **all newly written code** and to the portions of existing files that you
  actually modify. Do not reformat unrelated lines, rename unrelated symbols, or create
  formatting-only churn in feature/fix PRs.
- Before finishing, inspect the diff for indentation, line lengths, imports, braces, spacing,
  naming, and multiline declarations. Run the repository's configured Kotlin formatter/linter
  if one exists; fix any violations you introduce.
- Do not suppress, disable, or reconfigure formatting checks just to make a change pass.
- Preserve established public APIs and externally referenced names unless renaming is part of
  the task. If you encounter pre-existing violations outside the changed scope, do not expand
  the diff merely to fix them.

### Files and imports

- Save source files as UTF-8. Indent with **4 spaces** per block level; never indent with tabs.
- File order: optional license header, file annotations, `package`, `import`s, declarations.
  Separate present sections with exactly one blank line.
- Name a file containing one top-level class after that class (for example,
  `UserRepository.kt`). For multiple top-level declarations, use a descriptive file name,
  normally in PascalCase (plural camelCase is also allowed).
- Keep each file focused on one theme. Place unrelated top-level declarations in separate files.
- Put all imports in **one ASCII-sorted list**; do not group imports by origin or add blank lines
  within the list. **Never use wildcard (`*`) imports.** Do not wrap package or import lines.
- Keep members in a logical, readable order; do not simply append everything to the bottom.

### Layout, spacing, and braces

- **Maximum line length: 100 columns**, except unbreakable content (such as a URL in KDoc),
  package/import declarations, and copyable shell commands in comments.
- Use one statement per line. **Never use semicolons** to combine statements.
- Use K&R braces: opening `{` on the same line as its declaration/control flow, content on
  subsequent indented lines, and closing `}` on its own line. Write `} else {` and
  `} catch (e: Exception) {` on a single line at the transition.
- Use braces for multi-line bodies and ordinary control-flow blocks, including single-statement
  `for`, `while`, and `if/else` bodies. Official exceptions: a standalone, single-line
  `if (condition) return`, a one-line `if/else` expression, and a one-line `when` branch
  may omit braces. Do not use these exceptions to compress long or complex logic.
- For an empty block, put `{` and `}` on separate lines; do not use `catch (...) {}`.
- Put one space after `if`, `for`, `while`, and `catch` before `(`; one space before `{`;
  one space on each side of binary operators and `->`; one space after `,` and `:`.
- For type annotations use `name: Type` (no space before `:`); for supertypes and generic
  constraints use `class Example : Parent` and `<T : Comparable<T>>`.
- Do **not** insert spaces around `.`, `?.`, `::`, or the range operator `..`.
- Separate methods, nested classes, and other members with one blank line. Consecutive
  property declarations may be grouped without blank lines. Avoid excessive blank lines.
- Prefer legibility to compressing code into the smallest number of lines.

### Wrapping and multiline declarations

- Prefer breaking lines at a higher syntactic level, rather than splitting expressions at
  arbitrary positions.
- When a function signature does not fit on one line, put **each parameter on its own line**,
  indented 4 spaces. Put the closing `)` and return type on a new line at the declaration's
  original indentation level:

  ```kotlin
  fun createSession(
      userId: String,
      expiresAt: Instant,
      isPersistent: Boolean
  ): Session {
      return Session(userId, expiresAt, isPersistent)
  }
  ```

- When a property initializer does not fit, break **after `=`** and indent its value:

  ```kotlin
  private val visibleAccounts =
      accountRepository.getAccounts().filter { account -> account.isVisible }
  ```

- When wrapping chained calls, place `.` or `?.` at the **beginning** of the continuation line:

  ```kotlin
  repository.observeAccounts()
      .filter { accounts -> accounts.isNotEmpty() }
      .distinctUntilChanged()
  ```

- If wrapping at a binary operator, break **after** the operator. Keep commas with the
  preceding token, method names with `(`, and lambda arrows with their parameter lists.
- Put multi-line property accessors on their own lines, indented 4 spaces.
- Enum constants may appear on separate lines without blank lines; constants with bodies
  need separation. A trivial undocumented enum may also be written on one line.
- Single-expression functions may use `=`; do not force complex multi-statement functions
  into expression bodies. Return types may be inferred when obvious, but preserve explicit
  types on public library APIs.
- Follow the project's existing formatter convention for trailing commas; the linked Android
  guide does not establish a general trailing-comma requirement. Do not mix conventions.

### Naming

- Packages: lowercase words concatenated, **no underscores** (for example,
  `com.example.accountdetails`).
- Classes, interfaces, objects, enums, and type aliases: **PascalCase** (`AccountRepository`).
- Ordinary functions: **camelCase**, usually verbs (`loadAccounts`).
- **`@Composable` functions returning `Unit`: PascalCase**, usually nouns (`AccountCard`).
  Other functions, including non-`Unit` composables, follow ordinary function naming.
- Parameters, local variables, and non-constant properties: **camelCase** (`accountId`).
- True constants: **UPPER_SNAKE_CASE** (`MAX_RETRIES`). Only deeply immutable `val`s
  defined at top level or inside `object` qualify; mutable objects and class instance
  properties do not. Use `const val` for eligible scalar constants.
- Backing properties use exactly the public property name with a leading `_`:
  `_uiState` for `uiState`. Do not invent `m`, `s`, or `k` member prefixes.
- Type parameters: `T`, `E`, `T2`, or descriptive PascalCase ending in `T` (`RequestT`).
- Test classes end with `Test` (or a specific suffix such as `IntegrationTest`). Test
  functions may use underscores to separate logical parts, but **never spaces in backticks**.
- Normalize most acronyms as words: `userId`, `XmlParser`, `loadHttpResponse` rather than
  `userID`, `XMLParser`, `loadHTTPResponse`.

### Annotations, comments, and documentation

- Place annotations immediately before the declaration they annotate. Prefer one annotation
  per line for consistency; the Android guide also allows certain no-argument annotations
  to share a line.
- Use standard escape sequences (`\n`, `\t`, `\\`, etc.) instead of Unicode escape codes for
  those characters. Prefer readable literal Unicode for printable non-ASCII characters.
- Write normal `//` comments with a space after `//`; separate end-of-line comments from code
  with a space. Do not add comments merely to narrate obvious code.
- Add KDoc to new public types and public/protected members, except genuinely
  self-explanatory properties/functions or overrides whose inherited documentation suffices.
- Begin KDoc with a concise, capitalized summary fragment; use `/** ... */` for short KDoc
  without tags, and `/**` / ` *` / ` */` for multiline KDoc.
- Keep KDoc block tags, when present, in this order: `@constructor`, `@receiver`, `@param`,
  `@property`, `@return`, `@throws`, `@see`. Never write an empty tag description.

### Examples to follow

```kotlin
@Composable
fun AccountCard(
    account: Account,
    onClick: () -> Unit
) {
    if (account.isHidden) {
        return
    }

    Row(modifier = Modifier.clickable(onClick = onClick)) {
        Text(text = account.displayName)
        Text(text = account.balanceLabel)
    }
}

private val _uiState = MutableStateFlow(AccountUiState())
val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()
```

### Examples to reject

```kotlin
// WRONG: two-space indentation, cramped spacing, braces omitted in multiline flow,
// multiple statements on a line, and a composable named like an ordinary function.
@Composable fun accountCard(account: Account, onClick: () -> Unit) {
  if(account.isHidden)
    return
  val name=account.displayName; Text(name)
}
```

**Final check:** Before reporting completion, inspect every changed Kotlin file and verify
that new/modified code follows these rules. When a configured formatter/linter is available,
report whether it passed. Do not claim style validation if no such check was run.


# Material Design 3 — UI/UX Guidelines

## 1. Design Philosophy

Live Text Finder follows Google's Material Design 3 (M3) design system.

The goal is a polished, modern, intuitive Android experience that feels native to the platform while maintaining a distinct, consistent visual identity.

Official references:
- https://m3.material.io/
- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/design/ui/mobile

### Core Principles

1. **Clarity over decoration.** Every visual element must serve a purpose. Avoid unnecessary borders, shadows, gradients, animations, or decorative components.
2. **Strong visual hierarchy.** The user's attention should naturally move toward the most important content and primary action.
3. **Consistency.** Reuse established colors, typography, shapes, spacing, and interaction patterns throughout the application.
4. **Content-first design.** Prioritize the camera preview and recognized text over surrounding UI.
5. **Predictability.** Components should behave according to standard Android and Material conventions.
6. **Accessibility by default.** Accessible colors, touch targets, typography, and semantics are mandatory.
7. **Purposeful motion.** Animation must communicate changes, hierarchy, or feedback, not merely decorate the interface.
8. **Progressive disclosure.** Keep the primary screen simple. Reveal secondary controls only when relevant.

Prefer a restrained, refined visual style over excessive visual expressiveness.

Material 3 Expressive components may be used when they demonstrably improve usability, but should not be introduced solely for novelty.

---

## 2. Design System Architecture

All UI styling must originate from a centralized design system.

Use Material 3 through `androidx.compose.material3`, or its compatible Compose Multiplatform implementation in shared UI code.

### Theme

The application theme must define:

- `ColorScheme`
- `Typography`
- `Shapes`

Use `MaterialTheme` as the single source of truth.

Requirements:

- Never define arbitrary screen-specific color palettes.
- Never repeat hardcoded styling values across composables.
- Never introduce a separate design language for individual screens.
- Prefer existing M3 components over custom implementations.
- Extend the theme with semantic tokens only when standard Material roles cannot express a genuine application-specific requirement.

Create reusable application components only when their behavior or styling is shared or when a Material component cannot adequately represent the intended interaction.

Do not introduce an extensive custom design-system framework for a small application.

### Theme Modes

Support light and dark themes.

On Android 12+, support Material You dynamic color where appropriate, using `dynamicLightColorScheme` and `dynamicDarkColorScheme`.

Provide consistent fallback color schemes for earlier Android versions and other platforms.

Dynamic colors must never compromise the visibility of OCR highlights, camera controls, or text overlays.

---

## 3. Color System

Follow Material 3's semantic color roles.

Use colors based on their meaning, not simply their visual appearance.

### Primary Roles

| Role | Purpose |
|------|---------|
| `primary` | High-emphasis actions and selected states |
| `onPrimary` | Foreground content on primary surfaces |
| `primaryContainer` | Highlighted or selected containers |
| `onPrimaryContainer` | Content inside primary containers |
| `secondary` | Lower-emphasis accents and supporting actions |
| `secondaryContainer` | Supporting selected surfaces |
| `tertiary` | Complementary accent where justified |
| `surface` | Main UI surfaces |
| `surfaceContainer` | Grouped or elevated content surfaces |
| `onSurface` | Primary text and icons |
| `onSurfaceVariant` | Secondary labels and supporting information |
| `outline` | Borders and outlined components |
| `outlineVariant` | Subtle separators |
| `error` | Error and destructive states |

Use corresponding `on*` color roles for foreground elements.

Use M3 surface container roles when visually grouping content. Avoid introducing shadowed cards unnecessarily.

### Color Hierarchy

- Reserve strong accent colors for primary actions and meaningful active states.
- Use neutral surfaces for supporting content.
- Avoid applying primary colors to every button, icon, and container.
- Avoid using more than one prominent accent treatment within the same interaction group.
- Do not use error colors to indicate ordinary "no matches" states.

### Contrast

Target WCAG AA contrast requirements:

- At least 4.5:1 for normal text.
- At least 3:1 for large text.
- At least 3:1 for meaningful non-text UI elements and state indicators.

Check contrast in both light and dark themes.

Camera overlays need additional scrutiny because their backgrounds are unpredictable.

A foreground color that works on a normal Material surface may not remain readable over live video.

### Prohibited Patterns

Do not:

- Hardcode colors directly inside feature composables.
- Use arbitrary opacity to simulate disabled states when M3 component defaults already handle them.
- Use color alone to communicate a state.
- Add gradients without a functional reason.
- Mix unrelated accent colors across the interface.

---

## 4. Typography

Use the Material 3 typography scale.

Reference:
https://m3.material.io/styles/typography/overview

### Recommended Roles

| Typography | Usage |
|------------|-------|
| `headlineSmall` | Prominent screen or section headings |
| `titleLarge` | Screen titles |
| `titleMedium` | Section titles and important labels |
| `titleSmall` | Compact group headings |
| `bodyLarge` | Primary readable content |
| `bodyMedium` | Supporting text and descriptions |
| `bodySmall` | Minor contextual information |
| `labelLarge` | Prominent button labels |
| `labelMedium` | Chips, compact controls, status labels |

### Rules

1. Prefer `MaterialTheme.typography` styles.
2. Use the default Material type scale unless there is a clear reason to customize it.
3. Maintain a consistent hierarchy across screens.
4. Prefer font weight and semantic typography roles over arbitrary text-size differences.
5. Avoid excessive bold text.
6. Limit the number of typography roles within a single UI region.
7. Respect system font scaling.
8. Never artificially constrain text to a single line if this would make important information inaccessible.
9. Use scalable `sp` text units through theme typography.
10. Support longer localized strings without layout breakage.

Use readable, concise labels.

Examples:

Prefer:
- "Find text"
- "Scanning"
- "3 matches"
- "No matches"
- "Resume scanning"

Avoid:
- "Initialize text recognition"
- "Text detection operation is executing"
- "Recognition successfully completed"

---

## 5. Spacing and Layout

Use a consistent spacing system based on multiples of 4dp.

Recommended application spacing tokens:

| Token | Value | Usage |
|-------|-------|-------|
| `xxs` | 4dp | Very small internal separation |
| `xs` | 8dp | Closely related elements |
| `sm` | 12dp | Compact component groups |
| `md` | 16dp | Standard padding and spacing |
| `lg` | 24dp | Section separation |
| `xl` | 32dp | Large structural separation |

These are application-level defaults, not replacements for the dimensions built into Material components.

Use component-provided dimensions when applicable.

### Layout Rules

- Prefer 16dp horizontal margins on compact layouts.
- Increase margins appropriately on larger screens.
- Prefer consistent spacing over isolated positioning adjustments.
- Group related elements spatially.
- Give important elements room to breathe.
- Avoid unnecessary nested containers.
- Avoid excessive vertical padding that reduces usable camera space.
- Align related controls to a common visual grid.
- Preserve logical reading order and visual hierarchy.

### Responsive Layout

Do not assume every device has identical dimensions.

Use responsive Compose layouts and window size classes when structural adaptation is necessary.

Avoid hardcoded screen dimensions and absolute positions for ordinary UI.

Ensure that:

- Controls remain usable on compact screens.
- Large font settings do not cause clipping.
- Landscape layouts remain functional.
- Display cutouts are respected.
- Keyboard appearance does not obscure focused controls.
- Camera preview aspect-ratio changes do not distort overlay positioning.

The Pixel 10 is the initial validation device, not a justification for hardcoding its dimensions.

---

## 6. Shapes and Corners

Use `MaterialTheme.shapes` wherever possible.

Material 3 provides a shape scale including:

- Extra small
- Small
- Medium
- Large
- Extra large
- Full / pill-shaped

### Shape Guidelines

- Use the component's default Material shape when appropriate.
- Use small or medium shapes for compact controls and containers.
- Use larger shapes for prominent grouped surfaces.
- Use pill-shaped treatment for chips and controls when consistent with their Material component.
- Use consistent shapes for equivalent components.

Avoid:

- Inventing a different corner radius for every component.
- Combining square, moderately rounded, and extremely rounded containers without a clear hierarchy.
- Applying large rounded corners indiscriminately.
- Overriding M3 component shapes merely to create visual variation.

The application should feel cohesive rather than assembled from unrelated components.

---

## 7. Elevation and Surfaces

Material 3 uses surfaces, tonal variation, and elevation to express hierarchy.

Prefer tonal surface separation over heavy shadows.

### Requirements

- Use `surface` for ordinary backgrounds.
- Use appropriate `surfaceContainer*` roles for grouped surfaces.
- Use elevation when an element genuinely needs to appear above another.
- Prefer existing Material component elevation defaults.
- Use `outlineVariant` for subtle dividers when needed.

Do not place every group of elements inside a card.

Do not create nested card hierarchies without a meaningful interaction or content relationship.

On camera screens, distinguish controls from the video using a consistent surface or scrim strategy instead of heavy shadows.

---

## 8. Material Components

Prefer official Material 3 components.

### Component Selection

| Requirement | Preferred Component |
|-------------|---------------------|
| Screen structure | `Scaffold` |
| Screen navigation/title | `TopAppBar` |
| Primary action | `Button` or `FilledTonalButton`, depending on emphasis |
| Secondary action | `OutlinedButton` or `TextButton` |
| Icon action | `IconButton` |
| Text input | `TextField` or `OutlinedTextField` |
| Search text editing | Material text field with suitable keyboard actions |
| User-entered search tokens | `InputChip`, when appropriate |
| Selectable filters | `FilterChip` |
| Grouped information | `Card` or `Surface` |
| Temporary feedback | `Snackbar` |
| Blocking confirmation | `AlertDialog` |
| Secondary controls/options | `ModalBottomSheet` |
| Loading with unknown duration | `CircularProgressIndicator`, when appropriate |
| Binary preference | `Switch` |

Use component APIs supported by the project's current dependencies.

Do not adopt experimental APIs simply because they are newer.

### Component Rules

1. Do not reimplement standard Material components without a documented need.
2. Preserve built-in interaction feedback.
3. Provide appropriate pressed, focused, selected, disabled, and error states.
4. Avoid icon-only controls when the meaning is not immediately obvious.
5. Prefer text labels for important actions.
6. Never add decorative controls with no actual functionality.
7. Keep component behavior consistent throughout the app.

### Navigation

Do not introduce navigation components unless the information architecture requires them.

A single-screen utility should not have a bottom navigation bar merely to resemble a conventional application.

Use a top app bar, simple supporting actions, and contextual surfaces where appropriate.

---

## 9. Live Text Finder — Application-Specific Design

The application's principal interaction is:

1. Enter text to search for.
2. Point the camera at printed text.
3. Observe matching text highlighted in the live preview.
4. Inspect results, including using freeze/resume once implemented.

The interface must optimize this workflow.

### Primary Screen Hierarchy

Organize the main experience around three visual regions:

**A. Search controls**

- Prominent, accessible query input.
- Clear indication of the active query.
- Quick editing without navigating to another screen.
- Clear/remove action where useful.
- Multiple queries represented clearly when supported.

**B. Camera preview**

- Primary visual content.
- Maximum practical usable area.
- Matching text highlighted directly over the camera feed.
- Minimal visual obstruction.
- No unnecessary camera-style controls unrelated to the application's purpose.

**C. Status and contextual actions**

- Clear scanning status.
- Match count when available.
- Freeze/resume action when supported.
- Secondary controls kept visually subordinate.

These regions are conceptual; implementation may adjust their arrangement based on screen size and keyboard visibility.

### Search Input

- Use a Material text input.
- Make the input's purpose clear without relying solely on a placeholder.
- Show its active/focused state.
- Support keyboard dismissal.
- Preserve query state appropriately.
- Avoid disruptive layout shifts when editing.
- Use appropriate keyboard IME actions.
- Do not force users to repeatedly confirm unchanged queries.
- Avoid unnecessarily hiding the camera when the keyboard opens.

When multiple independent query terms are supported, consider `InputChip` for displaying removable terms.

Do not use chips when they make query editing unnecessarily complex or consume too much preview space.

### Camera Preview

The live camera preview is the focal point.

Rules:

- Give the preview priority over decorative UI.
- Avoid persistent large opaque panels.
- Keep controls out of the most useful text-scanning area when practical.
- Do not add decorative camera frames or simulated viewfinder elements unless they provide meaningful guidance.
- Preserve the correct camera aspect ratio.
- Never stretch or distort the preview to fill an arbitrary container.
- Respect system bars and display cutouts.
- Ensure control placement remains usable during device rotation.

Avoid creating a photography-editor interface.

Live Text Finder is a search utility, not primarily a photo capture application.

---

## 10. OCR Highlight Design

Text highlights are central to the product and require their own consistent visual language.

### Highlight Appearance

- Use a consistent, semantically defined highlight style.
- Make matches distinguishable from ordinary recognized text.
- Ensure highlights remain visible over light, dark, and colorful backgrounds.
- Prefer translucent fills, clear outlines, or an appropriate combination.
- Preserve the readability of the original text whenever possible.
- Avoid overly thick outlines and excessive saturation.
- Do not use Material error colors for ordinary matches.
- Avoid unnecessary glow or shadow effects.
- Do not create full-screen visual effects when a localized highlight is sufficient.

The highlight color may be an application-specific semantic token rather than a standard Material component color.

Centralize it in the design system, including any necessary light/dark or camera-overlay variants.

### Highlight Behavior

- Match highlights must align accurately with detected text geometry.
- Coordinate transformations must correctly account for preview scaling, cropping, and rotation.
- Do not animate every OCR bounding-box update.
- Avoid flickering caused by repeated recognition.
- Prefer stable visual updates over decorative movement.
- Do not display stale match coordinates as if they represent current live detections.
- Clearly distinguish live scanning from frozen inspection.

### Frozen Results

When freeze/resume is implemented:

- Retain a stable visual representation of the frozen result.
- Clearly indicate that scanning is paused.
- Make the resume action obvious.
- Preserve visible highlights and relevant result information.
- Do not silently continue updating the visual result while the UI indicates a frozen state.
- Avoid large transitions that disrupt inspection.

A frozen frame should feel like a stable workspace rather than a temporarily stalled camera.

---

## 11. Status, Empty States, and Feedback

Every important application state must have clear visual feedback.

### Relevant States

Support appropriate representations for:

- No search query entered.
- Camera initializing.
- Camera permission required.
- Actively scanning.
- Text detected but no query matches.
- One or more matches found.
- No readable text currently detected.
- Scanning frozen or paused.
- Camera or OCR failure.

Do not conflate these states.

In particular:

- "No matches" is not an error.
- "No text detected" is not equivalent to "No matches."
- "Scanning" should not appear as a completed state.
- A frozen frame must not appear to be live.

### Feedback Guidelines

- Show concise, understandable status text.
- Avoid rapidly changing or flickering labels.
- Keep status information in a stable area whenever possible.
- Do not cause layout jumps every time the match count changes.
- Avoid presenting a loading spinner during ordinary continuous scanning.
- Use progress indicators for meaningful waiting states.
- Use snackbars for temporary feedback.
- Use persistent contextual messages for states that require user action.
- Provide explicit recovery actions for failures.

Examples:

- "Point your camera at text"
- "Scanning…"
- "2 matches"
- "No matches found"
- "Scanning paused"
- "Camera access required"
- "Couldn't start the camera"

Never imply a successful operation before it has actually completed.

---

## 12. Motion and Animation

Follow Material 3 motion principles.

Animation should communicate:

- A change in state.
- Appearance or disappearance of an element.
- Movement between related interfaces.
- Completion or acknowledgement of an action.

### Recommended Practices

Use standard Compose animation APIs where appropriate:

- `AnimatedVisibility`
- `AnimatedContent`
- `animate*AsState`
- `updateTransition`

Prefer Material-compatible motion patterns and restrained durations.

Do not invent independent animation curves for every composable.

Avoid unnecessary animation on a screen that already contains a moving camera feed.

### Restrictions

- Never animate text highlights continuously simply because OCR is active.
- Avoid continuous pulsing, bouncing, or shimmering effects.
- Avoid large-scale transitions for minor state changes.
- Do not animate high-frequency OCR updates unless required for comprehension.
- Avoid motion that impairs text readability.
- Respect system accessibility preferences related to reduced motion where available.

Animations must never delay the primary scanning interaction.

---

## 13. Camera Overlay Readability

Controls rendered over live camera imagery require additional treatment.

The image underneath may contain any combination of colors, brightness levels, and textures.

### Rules

1. Use readable Material surfaces or carefully designed scrims behind text and controls.
2. Maintain adequate contrast over unpredictable camera content.
3. Prefer localized backgrounds rather than unnecessarily dimming the entire preview.
4. Keep overlay styling consistent.
5. Do not use naked white or black text over video without an adequate contrast strategy.
6. Avoid placing important labels directly over OCR highlights.
7. Ensure icons remain recognizable in bright and dark scenes.
8. Recheck contrast when the theme or dynamic color scheme changes.

Keep the distinction between camera content and application controls visually clear.

Do not make the overlay visually heavier than the information being searched.

---

## 14. Accessibility

Accessibility is a release requirement, not an optional enhancement.

Reference:
https://developer.android.com/develop/ui/compose/accessibility

### Touch Targets

- Every interactive control must have a minimum accessible touch target of 48dp × 48dp.
- Small icons may have smaller visual dimensions but must retain accessible hit areas.
- Avoid overlapping touch targets.
- Maintain sufficient space between adjacent controls.

### Semantics

Use Compose accessibility semantics correctly.

- Provide meaningful `contentDescription` values for icon-only actions.
- Set decorative icons' descriptions to `null` when their meaning is already conveyed by nearby text.
- Use appropriate roles, state descriptions, and click actions.
- Mark meaningful headings where needed.
- Ensure focus order follows the visual interaction order.
- Announce important state changes without overwhelming the user.
- Use polite live-region announcements sparingly.

Do not announce every camera frame or OCR update.

For recognized matches, provide an accessible way to obtain meaningful result information when those interactions are implemented; do not rely exclusively on visual bounding boxes.

### Font Scaling

- Support system font scaling.
- Avoid fixed-height text containers that clip content.
- Allow labels to wrap when appropriate.
- Verify that enlarged text does not obscure primary actions.

### Other Requirements

- Do not communicate meaning exclusively with color.
- Provide readable contrast in both themes.
- Preserve predictable keyboard and screen-reader navigation.
- Test with TalkBack.
- Avoid interactions that rely exclusively on difficult precision gestures.

---

## 15. Edge-to-Edge and System UI

Follow modern Android edge-to-edge design conventions.

Reference:
https://developer.android.com/develop/ui/compose/system/insets

### Requirements

- Support edge-to-edge layouts.
- Allow suitable backgrounds and camera content to extend behind system bars.
- Apply insets to interactive content.
- Keep controls clear of navigation gestures and display cutouts.
- Use Material 3's built-in inset handling where appropriate.
- Avoid applying the same system inset multiple times.
- Ensure the on-screen keyboard does not cover query editing controls.
- Keep system bar icons legible against their actual backgrounds.

Do not rely on arbitrary fixed padding to accommodate device-specific system bars.

---

## 16. Error and Permission Experience

System permissions and failures are part of the application's UI design.

### Camera Permission

When camera access is required:

- Explain why the permission is needed.
- Use direct, concise language.
- Show a clear action to continue.
- Handle denial gracefully.
- Provide a path to system settings when necessary.
- Do not display a permanently empty camera preview without explanation.

### Error States

Errors must:

- Explain what went wrong in understandable terms.
- Offer recovery when possible.
- Preserve existing user input.
- Avoid blaming the user.
- Use appropriate Material error roles.
- Avoid displaying raw exception messages.

Do not show intrusive dialogs for every recoverable error.

Prefer inline or contextual feedback when the user can remain on the current screen.

---

## 17. Content and Microcopy

Use straightforward, natural language.

### Writing Rules

- Prefer short, active labels.
- Use consistent terminology.
- Avoid unnecessary technical explanations.
- Avoid redundant descriptions.
- Do not use exclamation marks for ordinary success feedback.
- Keep instructional text contextual and brief.
- Write messages that explain what the user can do next.
- Use proper pluralization and localization resources.

Examples:

Good:
- "Search text"
- "Add search term"
- "No matches"
- "Pause scanning"
- "Resume scanning"

Avoid:
- "Initiate recognition process"
- "Zero OCR matches currently available"
- "Camera recognition functionality unavailable!"

Keep copy in string resources rather than scattering literal strings throughout composables.

---

## 18. Compose Implementation Rules

The design system must remain compatible with the project's Kotlin Multiplatform architecture.

### Architecture

- Keep theme definitions separate from feature-specific composables.
- Keep reusable UI components focused and composable.
- Prefer state hoisting and unidirectional data flow.
- Render UI from explicit state.
- Keep camera and OCR processing outside ordinary presentation composables.
- Avoid unnecessary recomposition during high-frequency OCR updates.
- Do not put frame processing logic inside composables.
- Keep custom drawing logic isolated from theme and business logic.

### Styling

Prefer:

- `MaterialTheme.colorScheme`
- `MaterialTheme.typography`
- `MaterialTheme.shapes`
- Standard Material components
- Centralized spacing and application-specific semantic tokens

Avoid:

- Feature-local color constants
- Repeated arbitrary `RoundedCornerShape` values
- Hardcoded font sizes without justification
- Copy-pasted styling code
- Unnecessary custom buttons or text fields
- Material 2 / Material 3 component mixing

For platform-specific UI behavior, preserve the same semantic design language even when the implementation differs between platforms.

---

## 19. Preview and Visual Testing

New or substantially modified UI should be visually validated.

### Compose Previews

Where practical, provide previews covering:

- Light theme.
- Dark theme.
- Empty/default state.
- Populated/result state.
- Error state.
- Large font scale.

Use representative preview data without requiring live camera access.

### Real-Device Validation

Verify the actual camera interface on the Pixel 10.

Test:

- Bright scenes.
- Dark scenes.
- Busy backgrounds.
- Small printed text.
- Multiple highlighted matches.
- Empty results.
- Keyboard open and closed.
- Light and dark theme.
- Portrait and landscape where supported.
- Increased system font size.
- Camera permission denied.

Inspect actual rendered UI for clipped content, incorrect alignment, unreadable controls, and unexpected layout shifts.

A successful build is not evidence of correct visual behavior.

---

## 20. UI Review Checklist

Before considering any UI-related task complete, verify the following.

### Visual Consistency

- [ ] Material 3 components are used where appropriate.
- [ ] Colors come from semantic theme roles.
- [ ] Typography follows the Material type scale.
- [ ] Spacing follows the established layout system.
- [ ] Equivalent components use consistent shapes.
- [ ] Visual hierarchy is clear.
- [ ] Unnecessary decoration has been avoided.

### Functionality

- [ ] Primary actions are obvious.
- [ ] Secondary controls do not dominate the screen.
- [ ] All visible controls are functional.
- [ ] Loading, empty, success, and error states are appropriate.
- [ ] Keyboard and focus behavior are correct.
- [ ] Existing functionality is preserved.

### Accessibility

- [ ] Touch targets meet minimum dimensions.
- [ ] Interactive elements have meaningful semantics.
- [ ] Contrast requirements are satisfied.
- [ ] Important text supports font scaling.
- [ ] Meaning is not conveyed by color alone.
- [ ] Important status changes are accessible without excessive announcements.

### Camera-Specific Quality

- [ ] Camera preview remains the visual priority.
- [ ] Controls remain readable over real-world imagery.
- [ ] OCR highlights are clearly distinguishable.
- [ ] Highlight geometry aligns with recognized text.
- [ ] High-frequency updates do not introduce unnecessary flicker.
- [ ] Status feedback remains stable.
- [ ] Frozen and live states are clearly differentiated when applicable.

### Validation

- [ ] Relevant Compose previews have been inspected where available.
- [ ] Light and dark themes have been considered.
- [ ] Relevant UI and accessibility tests have been run.
- [ ] Device-specific issues have been investigated where device testing is available.
- [ ] Any unavailable verification is explicitly reported rather than assumed successful.

---

## 21. Instructions for Coding Agents

When implementing or modifying UI:

1. **Inspect existing UI and theme code first.**
   Reuse the existing design system rather than introducing competing styles.

2. **Identify the primary user task.**
   Ensure the layout supports that task before adding decorative elements.

3. **Consult Material 3 guidance.**
   Prefer official Material components and interaction conventions.

4. **Choose semantic styling.**
   Select appropriate color, typography, shape, and spacing roles before introducing custom values.

5. **Reuse existing components.**
   Create new shared components only when necessary.

6. **Implement all relevant UI states.**
   Do not design exclusively for the ideal/default state.

7. **Preserve accessibility.**
   Include semantics, touch targets, contrast, and responsive text behavior from the beginning.

8. **Avoid unnecessary visual complexity.**
   Do not add cards, shadows, gradients, tabs, animations, or buttons simply to make the interface look more elaborate.

9. **Preserve application behavior and scope.**
   A visual improvement must not silently change OCR semantics, camera behavior, navigation, or persistence. Do not implement unapproved future features as part of styling work.

10. **Review the rendered result.**
    Assess the full screen, not only individual components.

11. **Check consistency.**
    Ensure the change still looks like part of the same application.

12. **Report validation accurately.**
    State which UI states and devices were actually checked, and identify any remaining visual or accessibility concerns.

### Acceptance Standard

A UI implementation is not complete merely because it compiles and uses Material 3 components.

It must also:

- Exhibit clear visual hierarchy.
- Follow consistent design tokens.
- Respect standard platform interactions.
- Remain usable across relevant states.
- Satisfy accessibility requirements.
- Avoid unnecessary complexity.
- Preserve the app's primary workflow.
- Feel like a coherent, deliberately designed product.

**Final design rule:**

When choosing between a visually impressive solution and a simpler, clearer, more consistent Material 3 solution, prefer the latter unless the more elaborate design provides a demonstrable usability improvement.

