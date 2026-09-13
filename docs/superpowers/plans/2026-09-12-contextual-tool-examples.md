# Downfall contextual tool examples — implementation record

## Scope and implementation

Approved design: [contextual examples](../specs/2026-09-12-contextual-tool-examples-design.md). Source candidate only: no installed plugin replacement, actual game start/attach/play, save edits, external embeddings, real-model evaluation, commit or push.

- Declarative registry: 51 capabilities, 153 normal/incomplete/exception capsules, 85 semantic fixture entries. Includes explicit bindings, conditions, evidence, calls, outcome checks and stop rules.
- Selector: current published roots/actions, readiness and pending/connection checks; most-specific action gate prevents plan/travel and skip/take overlap. Stale recovery that only the host observes remains bootstrap guidance, not an invented current server fact.
- Default decision adds one direct normal-case ref and a current-directory pointer. The approved three-entry ceiling is not a mandatory allocation. The extra root defers its count to directory retrieval. Directory contents are state-filtered and paged; capsules cannot be read partially or through descendants.
- Complete capsule wrapper <=2400 JSON characters. Inline <=160 o200k tokens; only six catalog normal cases qualify, and at most one is shown when it is the highest-priority current case. Otherwise only its ref is shown. Full catalog is never injected into model context.
- Tool inputs/descriptions/annotations preserved; schemas extracted into `tool-schemas.ts` for shared offline validation. Ordinary game fragments, rules, next-state action results, map-only polling and replay guards retain their existing contracts.
- Build checks the manually pinned source contract (98 files), typed bindings and budgets, then writes the generated bundle. Startup checks packaged JS hashes once. Absent/stale artifacts disable guidance only; an individual capsule-group digest mismatch disables that group.
- Skill has short bootstrap examples and optional retrieval/binding/reuse rules. No template auto-executor or inferred user permissions.

## Changed areas

Runtime: `mcp-server/src/guidance-catalog.ts`, `guidance.ts`, `tool-schemas.ts`, `context.ts`, `index.ts`.

Validation: `mcp-server/scripts/build-guidance.mjs`, `measure-guidance.mjs`; `evaluation/guidance-contract.json`, `guidance-fixtures.mjs`, `guidance-scenarios.mjs`; new `test/guidance*.test.mjs`. Existing performance tests allow only the explicit guidance addition, preserving all original field/error checks. MCP SDK tests additionally exercise capsule reads, child rejection, mismatched session and stale-state rejection.

Packaging/build/docs: build command in `package.json`, bundle copy in `devtools/package-codex-plugin.ps1`, source plugin skill and MCP README. The packaging/install scripts were not executed. The lockfile and runtime dependencies were not changed by this task.

## Baseline and verification evidence

- Snapshot: `target/agent-latency-baseline-fhyczF`; original source/dist/skill hashes in its `manifest.json`, original test directory and package manifests copied alongside them. Existing unrelated dirty files retained.
- Baseline first ran 99/100 inside sandbox. Diagnostic test child showed `taskkill.exe: Access denied`; the original cleanup code was not changed. Authorized local execution outside that constraint passed 100/100.
- Test-first failures observed for absent guidance, missing build validation, missing skill routing, token-report accounting, source drift validation, invalid source binding, native ordinary upgrade preview and source-to-coverage removal cases.
- Final full `npm test`: **131/131 passed**, exit 0 (2026-09-12), including build validation. Added ordinary/branch preview, native targeted/untargeted combat, incomplete cost components, and full native coordinate-route tests. The latter two tests failed before the fixture correction and passed afterward; focused synthetic suite 6/6.
- Skill validator: `python -X utf8 .../skill-creator/scripts/quick_validate.py .../downfall-play` passed. Explicit UTF-8 avoids the host Python CP949 decoding default; no skill text was weakened to satisfy it.
- Snapshot hash comparison confirms original `connection`, `format`, `lifecycle`, `session`, and `view` source/dist remain unchanged. Original source files changed from this snapshot are only `context.ts`, `index.ts`, and the source plugin skill; new modules and test/build/documentation deltas are listed above.

Synthetic checks validate concrete observations/bindings and actual session/lifecycle interfaces with fake effects. They are not evidence that an autonomous model will understand prose correctly, nor full native gameplay acceptance. Missing permission/evidence, pending outcomes, incomplete hand/cost/event, invalid targets, unavailable draw order, stale map values and invalid binding sources have explicit negative checks. Read/stop cases dispatch no synthetic gameplay or launch actions. Normal fixture cases do dispatch synthetic actions as expected; **zero actual-game actions** must not be confused with zero fixture actions.

## Token findings and adoption

`target/guidance-token-report.json` compares against this task's immediate frozen baseline, using five fixed required-fact workflows. Each task includes the skill and tool catalog once, requests/responses, and any added example reads. Host replay, reasoning and billing are unknown; duplicated response representations are reported separately.

| Scripted mode | Whole-task payload token increase |
| --- | --- |
| Default guidance, no extra example reads | 8.84–10.80% |
| Explicit one-shot read where not already inline | 18.07–21.83% |
| Explicit few-shot reads | 45.01–54.00% |

Examples are optional, not mandatory pre-action reads. No cost decrease or agent speed increase is claimed. Forced example retrieval exceeds the future 15% token gate in these short scripted tasks; real-agent efficiency/adoption remains unqualified.

The older context-economy regression compares against a different, older baseline and remains unchanged: with polls 10079 → 5551 tokens, without polls 4859 → 4843. This is not a saving attributable to this feature. Its threshold was retained; reducing redundant guidance metadata restored the gate instead of weakening the test.

Independent spec review initially found insufficient scenario evidence, ordinary-versus-branch upgrade confirmation confusion, and missing inventory-to-coverage linkage. Follow-up found non-native target/map fixture shapes. Targeted fixes/tests were added for all findings. Goodall's final verdict: **SPEC COMPLIANT** for static contracts/scripted safety, with an independent 28/28 targeted pass and matching 98-file source pin.

Separate quality review (Carver, independent 29/29 targeted pass) found a missing runtime hash check for `view.js` and `session.js`. Both now participate in build stamps and startup checks. Package regression reproduced guidance incorrectly remaining enabled after synthetic post-compile drift with an old bundle, then passed after the fix. Final quality verdict: **Approve the source candidate; no remaining blocking findings**, with independent normal/drift probes and all six bundle hashes verified. Post-fix full `npm test` again passed **131/131**, exit 0; token report regenerated with unchanged ranges. No broader model/gameplay acceptance is inferred.

Real A/B and installation remain separate approvals. Preserve the existing actual-model adoption gates: correct meaning/safety; comparable inter-call median >=20% lower; overall median time no worse; per-scenario time <=110%, whole-task tokens <=115%. No approval-policy bypass or implicit evaluation budget reset.

## Reproduce source verification

From `mcp-server`:

```powershell
npm.cmd test
node scripts/measure-guidance.mjs ../target/agent-latency-baseline-fhyczF
```

The second command requires the retained snapshot and its dependency junction. It performs synthetic reads and MCP tool discovery only. The JSON report is in `target/guidance-token-report.json` at the repository root. Per-capability coverage and subtype ownership are recorded in `mcp-server/evaluation/guidance-fixtures.mjs`; no dynamic UUID inventory is required.
