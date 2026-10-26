# Agent workflow protocol

The coordinating chat owns scope approval, dispatch, canonical state and final acceptance. `docs/agent-workflow/state.json` is the local, Git-ignored persistent handoff; it may be absent in a fresh checkout. Historical stage evidence is also kept locally under `docs/agent-workflow/stages/` and ignored by Git. Version concise requirements, current validation outcomes and standing guidance rather than raw logs or per-run agent reports. Workers propose state changes in reports; only the coordinator edits canonical state. Role instructions guide behavior but do not enforce a sandbox or role allowlist.

## Sequence

For a substantive request: bounded planner analysis → root-approved specification → implementer → independent reviewer → at most one bounded correction cycle → independent re-review → final checks and root acceptance → stop. A trivial change may be handled directly. Product work requires a current, explicit approved scope; historical proposals and stage artifacts do not grant authorization.

The planner reports verified facts separately from hypotheses, relevant files/symbols, approach, scope/non-goals, risks, measurable acceptance and validation. The implementer writes only approved scope, preserves user changes, runs required meaningful checks and reports actual evidence and limits. The reviewer receives the neutral original specification and actual target, reviews independently from source and reports evidence-based findings. Verdicts are PASS, PASS WITH LIMITATIONS, CHANGES REQUIRED or BLOCKED. The coordinator resolves challenged findings with the user.

Allow at most one ordinary correction cycle before reassessing scope, root cause and escalation. This is a reassessment checkpoint, never permission to accept a defect. Verify accepted corrections independently. If defects remain, stop and return a concise diagnosis and next decision.

## Dispatch and stop rules

Default to sequential work. At most two child agents may run concurrently, excluding root; parallelize only genuinely independent work and state why. Workers do not delegate, create additional custom roles or continue automatically to another stage. No scheduler, heartbeat, polling or recovery cycle starts by this protocol. Stop after final acceptance or when blocked, scope is missing or a decision is required.

Use only `planner`, `implementer` and `reviewer`. Root remains Sol Medium. Role files set their own model/effort; unspecified children default to Luna High. Escalation to Sol High requires an explicit root switch or explicitly authorized temporary session, with a compact diagnostic handoff. Never silently substitute models or escalate because a check failed.

## Review and handoff

Initial review is fresh-context and source-first: provide the original neutral approved specification, acceptance criteria and actual target; omit implementation rationale, summaries, anticipated findings and conclusions. Review the complete authorized target and attribute pre-existing changes separately. Correction review may use the findings checklist. Findings give severity, location, evidence, trigger, expected behavior and correction direction. Preserve historical reports and interrupted review facts; never create empty or pre-passed reports.

The coordinator records exact next action, evidence, unresolved findings and ownership in `state.json`. At handoff confirm the prior writer stopped and reconcile current repository facts. Do not infer worker liveness from old identifiers. Keep reports concise; workers need not reread the whole conversation history.
