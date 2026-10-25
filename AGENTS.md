# Implementation guidance

Read [architecture](docs/architecture.md), [implementation plan](docs/implementation-plan.md), and [validation](docs/validation.md) before changing code.

- Keep application UI and platform-neutral OCR snapshots in `shared/commonMain`.
- Keep CameraX, ML Kit, Android permissions, lifecycle and image buffers in `shared/androidMain`. `androidApp` is only the host.
- Keep the existing iOS framework and an explicit unsupported-camera placeholder. Do not implement iOS OCR in this increment.
- Prefer small classes/functions, narrow visibility, immutable snapshots, and explicit ownership. No Kotlin data objects.
- Do not add DI, navigation, storage, backend, ARCore, matching or highlights for this foundation.
- Use the version catalog and existing official KMP Android library plugin. Avoid build customizations unless needed for a demonstrated problem.
- Run compilation-only checks, meaningful tests and lint. Leave APK generation to the user unless explicitly requested. Record what was actually executed; never imply device validation from compilation success.

## Required independent review

For future implementation reviews, follow [the independent review workflow](docs/review-workflow.md). Launch a strict independent reviewer agent with a fresh context (`fork_turns="none"`), giving it the repository path and review criteria, without implementation summaries, reasoning, explanations or proposed conclusions. It may inspect the codebase and repository documentation for context.

The reviewer must produce an evidence-based findings report covering correctness, bugs/regressions, proportionate CLEAN architecture, code smells, formatting, unused imports/dead code, and unused files in the complete staged/unstaged/untracked change set. The implementation agent must address each finding. Implement accepted findings and verify the fixes. Any challenged finding must be brought to the user for resolution; do not silently dismiss, defer or mark it resolved. Ask the reviewer to verify corrections before closing the review.
