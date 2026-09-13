# Context performance implementation plan

User approved three bounded optimizations; execute inline with TDD and independent review. Preserve existing work. No cache, Java changes, game launch, save edits, commit or push.

**Goal:** Remove unnecessary MCP projection work without changing serialized results, safety or token counts.

**Architecture:** `mcp-server/src/context.ts` remains the only production file edited. Array fragments build indexed pairs for the requested page only. Map-only polling constructs the same identity/connection/map summary directly. Fixed observation-key membership and shared projection keys become module constants; no mutable state cache.

**Tech Stack:** TypeScript, Node tests, existing MCP SDK and local synthetic benchmarks.

- [x] Freeze pre-change compiled implementation in `evaluation/baseline/context-v3.mjs`; relocate only its view utility import.
- [x] Add `test/performance.test.mjs`. RED: a 10,000-card collection reading three rows touches only three elements; map-only reads do not inspect narrative/combat/controls. Differential tests compare exact serialized output, hashes, errors, current-state changes, page offsets/budgets and synthetic JSON fixtures with the frozen implementation. Run `npm run build` and `node --test test/performance.test.mjs` before implementation.
- [x] Replace full-array `map` with bounded page pairs. Preserve global indices, total, offsets, cursor/budget behavior and empty/tail pages. GREEN the focused tests.
- [x] Construct map-only identity/connection/plan directly; share projection key constants with the regular decision path. Keep regular header allocation inline after benchmark comparison. Hoist fixed membership Set. No caching; every request reads current safety fields. GREEN focused tests, then run `npm test`.
- [x] Add `scripts/measure-context-performance.mjs`: alternating warmed baseline/current runs, median/p95, small and 10,000-entry fixtures, exact-output checks, deterministic read counts. Timing is diagnostic, never a flaky test threshold or live-game claim.
- [x] Independent review, full tests and benchmark; update docs. Reinstall existing plugin via backup/cachebuster script and verify byte-identical installed modules, actual SDK discovery and read-only status.

## Compatibility contract

Supported native inputs are parsed JSON snapshots. Differential comparisons cover dense arrays including nulls and mixed values, Korean/escaped text, long refs, invalid paths and paging boundaries. Getter/Proxy fixtures are test instrumentation for work counts, not a new accepted wire format. No response wording/schema changes. The previous implementation is an oracle only, not an MCP full-dump route.

## Execution evidence

- RED: array page accessed 10,000 elements instead of 3; map-only polling read 10,000 narrative entries instead of 0. Differential baseline cases already passed. GREEN: 3 and 0 accesses respectively; exact serialized output and view hashes retained.
- Full suite after the final production edit: 38 passed, 0 failed, including token-economy and actual SDK routing tests.
- Performance investigation: initial shared header-object helper slowed the ordinary decision path. Increasing warmup from 100 to 2,000 reduced JIT warmup skew but did not eliminate the small-state regression. Restoring inline header allocation in that path removed most of the observed overhead. This is empirical evidence, not a claim about specific V8 internals.
- Final recorded microbenchmark: large array page 177.51 to 10.27 microseconds (17.28x); large map-only query 54.89 to 7.53 (7.29x). Normal decisions were essentially flat/slightly slower in this sample: small 23.34 to 24.46; large 58.68 to 60.39. No claim that every path improved. Full median/p95 batch-mean results are in `target/context-performance-report.json`.
- No token savings claimed for this pass: responses remain identical. These timings exclude MCP transport, game rendering, model reasoning and billing.
- Independent review found a malformed JSON action-ID conversion error bypass. Reproduced RED, then added a fallback to the original projection for non-string ready-state action IDs. Added short-circuit/error parity coverage across screens and readiness. Normal native string-ID snapshots keep the direct path.
- Follow-up independent static review confirmed the blocker closed with no remaining findings. Packaging reran all 38 tests successfully. Installed `downfall-agent@personal` version `0.1.0+codex.20260912050258`; verifier confirmed byte-identical index/view/context/lifecycle modules, six-tool SDK discovery, scoped fragments and read-only game phase `stopped`, gameplay actions 0. Windows process inspection used scoped permission escalation. Prepared Java runtime remains `target/local-test-20260912-124552-af471f87`; saves and other Java processes were not changed.
