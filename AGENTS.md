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
