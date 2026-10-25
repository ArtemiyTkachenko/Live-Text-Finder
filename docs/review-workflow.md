# Independent review workflow

## Independence

Before closing an implementation review, launch a separate, strict reviewer agent with fresh context (`fork_turns="none"`). Do not pass conversation history, implementation rationale, an implementation summary, anticipated findings or suggested conclusions. Give it only the repository path, review criteria, reporting requirements and operational constraints (for example, no APK generation). It may inspect the repository, its instructions, documentation and relevant official references independently. Do not request lenience or omit changed areas.

## Scope and criteria

Inspect the complete proposed change set, including staged, unstaged and untracked files, against the existing codebase. For an initial repository with no HEAD, review the entire proposed tree and separately account for differences between the index and working tree.

Assess:

1. Technical soundness and correctness.
2. Bugs, regressions, edge cases, concurrency and resource ownership.
3. CLEAN architecture appropriate to the feature: dependency direction, platform boundaries, separation of responsibilities and testability; avoid adding layers merely to satisfy a pattern.
4. Code smells, duplication, needless complexity and unnecessary abstractions.
5. Formatting and project conventions.
6. Unused imports, unreachable/dead code and unused declarations/dependencies.
7. Unused or unrelated files in the Git diff, including staged files already deleted from the working tree.

## Findings report

The reviewer produces a report with severity, file/line references, concrete evidence, user/developer impact, and a suggested correction or verification for each actionable finding. Separate confirmed issues from uncertainty. Do not invent findings to sound harsh. State review coverage, checks actually run and limitations, including unavailable physical-device validation. Produce a report even if no findings are identified.

## Resolution

The implementation agent tracks every finding and either:

- Implements the correction immediately, runs appropriate checks and records the resolution; or
- Challenges it with evidence and involves the user in deciding the outcome. A challenge remains open until the user resolves it. Continue unrelated accepted corrections while awaiting that decision.

Do not quietly dismiss or defer findings. Send the corrected code back to the reviewer for verification. Record verification and any unresolved issues in the final report. If verification discovers additional actionable issues, repeat the resolution process.

## Validation constraint

Use compilation-only tasks, host tests and lint. Do not generate or export APK files unless the user explicitly requests it. Keep historical validation records accurate rather than rewriting earlier checks as if they had not occurred.
