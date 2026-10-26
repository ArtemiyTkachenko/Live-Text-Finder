# Project guidance

Follow the [workflow protocol](docs/agent-workflow/protocol.md) and persistent [mission/state handoff](docs/agent-workflow/mission.md) for roles, sequencing and stop conditions. The coordinating chat owns dispatch and canonical state; roles do not enforce permissions or dispatch work. CS-003 independent source review completed with runtime limitations; current user authorization governs further product work. Generated stage evidence and coordinator state are retained locally and ignored by Git; durable requirements and validation summaries stay in the project documentation.

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
