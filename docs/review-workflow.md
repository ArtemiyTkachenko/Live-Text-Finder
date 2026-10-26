# Independent review workflow

## Independence and input

Use a separate reviewer with fresh context for each initial review. Provide the original neutral approved specification and acceptance criteria, actual change target, review criteria and operational constraints. Do not provide implementation rationale, summaries, anticipated findings or conclusions. The reviewer independently inspects the target and relevant project context before reporting.

## Scope and criteria

Review the complete authorized target. Attribute pre-existing staged, unstaged and untracked changes separately. Assess:

1. Technical soundness and correctness.
2. Bugs, regressions, edge cases, concurrency and resource ownership.
3. Architecture and platform boundaries appropriate to the feature.
4. Duplication, needless complexity and unnecessary abstractions.
5. Formatting and project conventions.
6. Unused imports, dead code and unused declarations/dependencies.
7. Unused or unrelated files in the target.

For configuration-only changes, assess the configuration and active workflow target; do not re-review unrelated application correctness.

## Findings and resolution

Each actionable finding states severity, file/location, concrete problem, evidence, trigger, expected behavior and correction direction. Distinguish verified defects, plausible risks, missing tests and external validation. Avoid low-value style findings. State coverage, checks actually run and limitations.

The coordinator resolves challenged findings with the user. The implementer corrects accepted findings and records evidence; the independent reviewer verifies each accepted correction. Keep findings open until verified or actually resolved by the user. After one bounded correction cycle, reassess root cause and scope before further work; do not ignore a defect to meet the cycle limit.

Return PASS, PASS WITH LIMITATIONS, CHANGES REQUIRED or BLOCKED. A source-level pass does not establish runtime or physical-device behavior. Preserve historical evidence and never claim checks that did not run.
