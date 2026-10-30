# Agent workflow protocol

The coordinating chat owns scope approval, dispatch, task context and final acceptance. Keep handoffs, current agendas, session state and user-authorization history in chats, outside the repository; do not create repository mission/handoff documents or coordinator state files. Existing historical stage evidence under `docs/agent-workflow/stages/` remains local and Git-ignored. Version concise product requirements, validation outcomes and standing guidance rather than raw logs or per-run agent reports. Role instructions guide behavior but do not enforce a sandbox or role allowlist.

## Sequence

For a substantive request: bounded planner analysis → root-approved specification → implementer → independent reviewer → at most one bounded correction cycle → independent re-review → final checks and root acceptance → stop. A trivial change may be handled directly. Product work requires a current, explicit approved scope; historical proposals and stage artifacts do not grant authorization.

The planner reports verified facts separately from hypotheses, relevant files/symbols, approach, scope/non-goals, risks, measurable acceptance and validation. The implementer writes only approved scope, preserves user changes, runs required meaningful checks and reports actual evidence and limits. The reviewer receives the neutral original specification and actual target, reviews independently from source and reports evidence-based findings. Verdicts are PASS, PASS WITH LIMITATIONS, CHANGES REQUIRED or BLOCKED. The coordinator resolves challenged findings with the user.

Allow at most one ordinary correction cycle before reassessing scope, root cause and escalation. This is a reassessment checkpoint, never permission to accept a defect. Verify accepted corrections independently. If defects remain, stop and return a concise diagnosis and next decision.

## Dispatch and stop rules

Default to sequential work. At most two child agents may run concurrently, excluding root; parallelize only genuinely independent work and state why. Workers do not delegate, create additional custom roles or continue automatically to another stage. No scheduler, heartbeat, polling or recovery cycle starts by this protocol. Stop after final acceptance or when blocked, scope is missing or a decision is required.

Use only `planner`, `implementer` and `reviewer`. Root remains Sol Medium. Role files set their own model/effort; unspecified children default to Luna High. Escalation to Sol High requires an explicit root switch or explicitly authorized temporary session, with a compact diagnostic handoff. Never silently substitute models or escalate because a check failed.

## Review and handoff

Initial review is fresh-context and source-first: provide the original neutral approved specification, acceptance criteria and actual target; omit implementation rationale, summaries, anticipated findings and conclusions. Review the complete authorized target and attribute pre-existing changes separately. Correction review may use the findings checklist. Findings give severity, location, evidence, trigger, expected behavior and correction direction. Preserve historical reports and interrupted review facts; never create empty or pre-passed reports.

The coordinator records the next action, evidence, unresolved findings and ownership in the chat. At handoff confirm the prior writer stopped and reconcile current repository facts. Do not infer worker liveness from old identifiers. Keep reports concise; workers receive only the relevant task context.

## User-authorized autonomous continuation

For the current run the user authorizes repeated bounded prompt/plan → implementation → independent review → accepted correction/re-review → checks → results → commit stages, then the next planned task with minimal questions. Try pushing reviewed commits to main; if authentication blocks pushing, retain local commits and continue. Device tests are explicitly deferred until the user tests at the next usage limit. Track those checks as pending; source/host results do not substitute for them. This overrides earlier per-stage stop-for-device instructions for this run, while preserving independent review, defect reassessment, platform boundaries, local evidence hygiene and no APK generation. No background scheduler or worker delegation is authorized.
