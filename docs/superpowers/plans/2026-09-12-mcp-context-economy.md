# MCP context economy implementation plan

> Execute inline with TDD and scoped review, following the user-approved design. Preserve dirty work and do not commit/push or launch the game.

**Goal:** Small current-state decisions, state-bound fragment retrieval, explicit conditional polling, and measured token savings.

**Architecture:** New `src/context.ts` handles scoped roots, shallow fragments and compact decisions above the existing immutable `GameSession`. `src/index.ts` binds six short tools to this contract. Legacy `view.ts` remains a diagnostic baseline, not an MCP full-dump escape hatch.

**Tech Stack:** TypeScript, Node MCP SDK, Node tests, local tokenizer used only for evaluation.

- [x] Freeze pre-change view/catalog in `evaluation/baseline` for a reproducible comparison.
- [x] RED: `test/context.test.mjs` checks current-screen roots, no extension dumps, direct/batched fragments, long Korean text reconstruction, bounds and invalid paths, complete flags, conditional polling recovery and disconnect/pending changes.
- [x] Implement `context.ts`: `decision`, `readContext`, `conditionalDecision`; context reads use advertised roots and own-property traversal, never arbitrary object traversal or game calls.
- [x] RED then GREEN SDK routing/schema tests in `test/mcp.test.mjs`; update `index.ts` and short descriptions. Stale requests cannot dispatch or resolve old fragments; full diagnostics are not exposed.
- [x] Add deterministic workflows and tokenizer report in `evaluation`/`scripts`. Count definitions + every request/reply, including extra reads and polls. Assert required facts and reduced totals without claiming live AI equivalence.
- [x] Scoped review, full `npm test`, token report, README/installed verifier updates.
- [x] Back up and reinstall the existing local plugin; verify installed files, tool schema and read-only stopped status. Java build/runtime remains unchanged.

## Execution evidence

- Packaging reran `npm test`: 31 passed, 0 failed. Includes actual SDK read-only answer checks and the tokenizer regression.
- First review findings fixed with regression coverage: missing current combat metadata/card routes, oversized field cursor progress, and native selection hand visibility. Final independent static review found no blockers; it did not substitute for tests or live gameplay.
- Local `o200k_base` measurement: definitions 1,902 to 1,048 tokens; five required-fact workflows plus one catalog and two repeat polls each 10,079 to 5,305 (47.4% lower). Without polls, catalog-inclusive total 4,859 to 4,609 (5.1% lower). Individual no-poll payload-only detail traces increased. No actual model billing or autonomous gameplay claim.
- Existing dirty Java/map/cost/upgrade work preserved. No commit, push, game launch, save edit, or Java runtime rebuild.
- Installed `downfall-agent@personal` version `0.1.0+codex.20260912043022` through the normal backup/cachebuster/CLI workflow. Installed verifier confirmed matching `index.js`, `view.js`, `context.js`, six-tool discovery, refs schema, conditional reads, pagination and completeness flags. Read-only status was `stopped`, gameplay actions 0. Sandbox CIM access was denied; the same scoped read-only verification passed with permission escalation.
- Prepared runtime remains `target/local-test-20260912-124552-af471f87`. A newly opened Codex task can discover the updated plugin schema; no assertion that an already-running task has hot-reloaded it.
