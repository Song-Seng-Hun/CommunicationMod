# Downfall mechanic gap repairs

User approved fixing the six findings of the current-source audit. Work stays on the existing checkout; no commit, installation, game launch, model evaluation, or save mutation.

## Design and boundaries

Keep the existing offered-action and pinned-state validation. Add exact native tutorial/reward/wheel adapters, never permit arbitrary custom subclasses. Invoke original UI handlers rather than simulating game outcomes. Enumerate public card/monster catalogs without rolling, rendering, or mutating native caches. Completion refinement: upgraded Unknown and collectible reward comparisons use the existing bounded detached-copy upgrade service, not a new native renderer/factory path; hidden/non-base/branch/multi/unverified candidates remain incomplete. This inherits the documented arbitrary-extension-hook purity limit of standard upgrade previews. Retain current-context refs, bounded fragments and hidden-information rules. Large cards fall back to the existing field directory rather than a dead-end summary.

## Execution

- [x] Preserve baseline and the two user-modified MCP tests under target.
- [x] Add failing regressions for exact Downfall tutorial/reward/wheel adapters; implement and pass isolated policy/binding/native-handler tests. Independent review additionally removed unopened rare choices and required explicit unavailable upgrade evidence.
- [x] Add failing preview regressions for displayed/known candidates, hidden entries, bounds, cycles, and absence on unrelated entities; verify installed bytecode. Completion pass adds upgraded Unknown variants and late collectible reward comparisons, cache/source-preservation tests, and refusal to treat canUpgrade=false as proof of a native gallery no-op.
- [x] Add failing oversized-card discovery regression; retain the full-card fast path and restore child refs with budgets/pagination intact.
- [x] Integrate fresh Java compilation, six affected headless scripts, and full MCP tests (191/191, no skips). Revalidate guidance fixtures against upgraded catalogs/rewards and explicitly advance the reviewed contract to v9; do not silently refresh the old revision.
- [x] Independent review of native dispatch, visibility, and lossless ref recovery. Three findings repaired and re-reviewed; actual results and unresolved live-play limits recorded.
- [x] Independent completion reviews: Java upgrade integration has no verified new P1/P2; MCP long-ref/surrogate pagination P2 reproduced first, fixed with explicit single-ref recovery, and independently rechecked. No remaining source-repair blocker.

## Ownership and checks

UI adapter work owns RunUi/RewardUi and new focused adapter/test files. Preview work owns CardObservation/GameStateConverter and focused preview/test files. MCP directory work owns context.ts and a new regression test; pre-existing dirty tests stay untouched. Main integrates verification scripts and contract/docs.

Headless checks: devtools/verify-run-usability.ps1, devtools/verify-public-descriptions.ps1, devtools/verify-dialogue-observation.ps1, targeted new adapter/preview checks, mcp-server npm test. Run new assertions against the baseline first and record expected failure. No check may be weakened solely to make a build pass.

Baseline: target/downfall-gap-fix-baseline-20260926-091630 (source, devtools, plugin, docs, MCP source/tests/evaluation/scripts/hooks/config).

Completion baseline: target/downfall-completion-baseline-20260926-102022. Execution evidence and explicit acceptance limits: [source handoff](../../DOWNFALL-MECHANIC-GAPS-2026-09-26.md). The previous fifteen baseline failures are resolved, not waived. No installation/live acceptance follows from headless passes.
