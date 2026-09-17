# Downfall agent-call latency — execution record

Scope: implement the user-approved plan in the current dirty checkout; preserve unrelated edits. No game launch/connection/play, save changes, global model changes, commit, push, or public release.

## Gates

- Preserve actual task model/effort; no CLI-default substitution.
- Synthetic backend only; abort unexpected tools/access.
- 6 scenarios × 2 variants × 2 repetitions. Alternate order; fresh context.
- Maximum 24 model starts, 20 minutes from first start, 90 seconds/run. Missing data or exhausted budget never counts as success.
- Meaning/types/required facts/safety all pass; inter-call median at least 20% lower; overall total-time median no worse; per-scenario median time at most 110%; per-scenario tokens at most 115%. p90 descriptive only.
- Install ONLY after all gates and independent safety review pass. Otherwise preserve installed version.

## Sequence / status

1. [x] Baseline suite 48/48, 2026-09-12. Frozen source, build and skill in `target/agent-latency-baseline-8Krjx8`; SHA-256 manifest retained.
2. [x] Actual current task rollout `turn_context` at 2026-09-12T05:53:24.226Z: `gpt-6-astra`, effort `high`. Global defaults were not used.
3. [x] Measurement unit/integration tests before A/B. Separate host events from MCP proxy; separate initial connection and server processing. All six golden paths exercise actual MCP against the synthetic backend; forbidden lifecycle calls stop before forwarding.
4. [x] Regression tests then context and skill changes. Exact empty arguments, direct parameters references, full-fragment 2400-character card bound, pagination fallback, current event field. Budget-aware cleaning resolves the independently reported large-card allocation regression.
5. [ ] Full suite and independent reviews passed; real A/B attempted but blocked (see outcome below). Acceptance is NOT established.
6. [ ] Conditional local install and synthetic installed-cache verification — intentionally withheld. Never infer deployed state from source changes.

## Ownership

Main: evaluation harness, skill, execution evidence. Response worker: `context.ts`, `agent-context.test.mjs`, intentional-delta handling in `performance.test.mjs`. No overlapping edits. No Git worktree creation from stale HEAD: approved work targets the existing source with pre-existing uncommitted changes; baseline snapshot isolates comparison.

## Measurement contract

Clock source is explicit per run and never mixed in an acceptance comparison. Overlapping tool calls form one outstanding group: measure from its final response to the next request, not artificial gaps within a batch. Record failures/timeouts, missing usage, duplicate reads, invalid arguments and retries. Correct final judgment and required evidence both matter. Synthetic test timings do not establish agent speed.

Compare the median of per-task gap medians so tasks retain equal weight when a variant eliminates extra calls. Pooled p90 is descriptive only. Tokens include input + output; cached input is not counted twice. Per-repetition token growth is additionally checked so an expensive repetition cannot hide behind a scenario median.

## Independent review findings resolved before model use

- Response spec/safety: independent 33-test pass against frozen source.
- Response quality: whole-card recursive copying before overflow fallback regressed large-card handling; budget-aware cleaning requested, with deterministic traversal tests.
- Harness: rejected action attempts must count as unsafe replay; successful unknown → receipt → pending-state evidence required. Added failing negative traces, then fixes.
- Harness: next-state result evidence and meaningful event description required; keyword-only acknowledgement fails.
- Harness: live proxy violation monitoring, campaign abort, absolute deadlines including preparation, bounded process-tree cleanup, and explicit cleanup verification.
- Windows normal sandbox denies `taskkill /T`; approved evaluation-host context passes owned-child cleanup. The child Codex evaluation still runs read-only with other tool families disabled. No lingering matching synthetic test child found in the subsequent read-only process check.

CLI isolation settings checked against local CLI help/features and [official configuration reference](https://learn.chatgpt.com/docs/config-file/config-reference). No global settings written.

## Commands (manual; actual model execution is never part of npm test)

1. `node mcp-server/scripts/freeze-agent-latency.mjs` — preserve current candidate; retain printed path and manifest.
2. `npm test` from `mcp-server` — Windows process-tree cleanup test needs a host context permitted to terminate its own child tree. Never silently weaken this check.
3. `node mcp-server/evaluation/agent-latency/runner.mjs <codex-exe> <baseline-snapshot> <candidate-snapshot> <actual-task-rollout> <thread-id> <new-output-directory>` — consumes the approved bounded real-model budget; do not rerun to fish for passing measurements.

Reports contain host JSONL, proxy JSONL, per-run arguments/prompts/results, verified task settings, and exclusive campaign start ledger. Snapshot node_modules junctions point only to the existing pinned development dependencies; no runtime or save is copied into the evaluation.

## Outcome — 2026-09-12

Implementation: source changes complete; final `npm test` **100/100 passed**, including the original 48 tests. Response spec review passed independently; quality re-review passed 36 targeted tests plus 3,000 boundary cases. Evaluation safety review approved the bounded synthetic campaign after negative-path fixes. Source and skill behavior are not proof of real-agent speed.

Frozen candidate: `target/agent-latency-baseline-zFQPhh`. Earlier intermediate snapshot `target/agent-latency-baseline-AJY0Ix` is not the final candidate. Baseline remains `target/agent-latency-baseline-8Krjx8`.

Actual task setting confirmed and used: `gpt-6-astra / high`.

- Campaign: `target/agent-latency-ab-20260912`.
- **5 consumed starts; 4 result records; 0 qualified runs; 0 synthetic backend actions.**
- First start stopped before MCP calls: startup warning was misclassified as an unexpected tool, and Code Mode host was disabled. Runtime warning classification and per-invocation Code Mode host availability were corrected; global settings were untouched.
- Diagnostic continuation preserved the failed row and original budget; did not rerun it. Three scenario-1 executions reached `sts_act`, but the host rejected it: `MCP tool call requires approval, but approval policy is never`.
- Fifth start was interrupted to avoid more incomparable model consumption. Owned runner PID 22440 and its evaluation descendants were terminated by exact-PID `taskkill /T /F` (exit 0); subsequent read-only check found no matching runner. The interrupted attempt remains consumed and is not silently retried.
- Confirmed usage lower bound: **233,859 input+output tokens** across the three completed model runs. First/interrupted usage unavailable; do not treat it as zero. No savings or latency acceptance claim is valid.
- Original ledger/results remain unchanged. `audit-summary.json` includes all five starts, including the interrupted one; `report.json` only contains the four completed result records and must not be mistaken for the final start count.
- The runner now aborts immediately on host approval denial, without changing approval policy. Host-denied action attempts also count for retry/safety checks. Resume refuses unresolved starts or any unverified cleanup.

**Decision: no installation.** Verified existing installed manifest remains `0.1.0+codex.20260912053309`. No source-cache replacement, global model changes, game launch/attach/play, save changes, commit, push, or public release.

Final independent boundary review passed: raw ledger/host/proxy evidence, lower-bound usage, approval-denial termination, host-denied replay counting, and unchanged cached version were verified; nine selected regressions passed independently. A fresh hash audit against the frozen baseline found production changes only in `src/context.ts`, its generated `dist/context.js`, and the plugin skill.

Next authority needed: explicitly permit an evaluation-only approval policy for the synthetic server's `sts_act` before a new comparison campaign. Do not approve real-game actions, enable other tools, weaken stale/replay guards, or reset the consumed budget implicitly. A fresh complete comparison needs a separately approved budget; existing incomplete evidence cannot qualify installation.
