# MCP prose compression implementation plan

User approved six-tool scope and meaning-preserving compression. Execute inline with TDD and independent review. Do not commit/push, launch the game, change saves, or install Caveman code.

**Goal:** Shorter server-authored prose without changing protocol or game evidence.

**Architecture:** Edit literal titles/descriptions in `mcp-server/src/index.ts`, current rules in `src/context.ts`, and the startup waiting hint in `src/lifecycle.ts`. No runtime string transformer, extra model calls, schema renaming, or native-text rewriting. Error strings remain exact.

**Tech Stack:** Existing TypeScript, Node tests, MCP SDK, local o200k_base tokenizer.

- [x] Freeze actual current tool catalog/rules/waiting hint in `evaluation/baseline/prose-v2.json`.
- [x] Add `test/prose.test.mjs`: actual SDK catalog must retain all schema/annotations; per-tool prose and each rule must use fewer tokens than baseline. Assert safety clauses and exact native evidence. Run `npm run build` then `node --test test/prose.test.mjs`; expect token regression failure before edits.
- [x] Compress only the reviewed literals. Preserve permission, negation, timing/order, uncertainty, causal meaning and precise API names. Run the focused test until green; never weaken safety checks for savings.
- [x] Run `npm test` and `node scripts/measure-context-economy.mjs`. Record scoped prose counts separately from full workflow counts. Review meaning against baseline; token counts alone cannot prove semantic equivalence.
- [x] Independent read-only review; fix concrete findings. Reinstall through `devtools/install-codex-plugin.ps1` with backup/cachebuster, verify exact installed bundle and read-only stopped status.

## Safety decisions

Caveman SKILL.md was read from JuliusBrussee/caveman in the preceding turn. Apply only its terse-style principles. No skill installation, hooks, scripts, uploads or global MCP interception. Persistent documentation remains normal prose. Original English MCP literals remain English; Korean game text is not translated. Titles/descriptions, six current rules and one waiting hint are the only production changes. Exact errors and external/native receipts stay unchanged even when verbose.

## Execution evidence

- RED confirmed: all 13 prose units initially had equal before/after counts. GREEN: each unit is shorter; exact protocol schema/annotations and required safety clauses remain intact.
- `npm test`: 33 passed, 0 failed, including the five required-fact workflows and actual SDK read-only answer tests. Packaging repeated this successfully.
- Local o200k_base: edited units counted once 523 to 473 tokens (9.6%); full catalog 1,048 to 1,020 (2.7%). Existing five-workflow benchmark 5,305 to 5,277 with polls; 4,609 to 4,581 without. No billing or autonomous-model equivalence claim.
- Independent static review against the frozen wording found no blockers: permission, negation, current/same IDs, ordering, uncertainty and retry prohibitions preserved. Static review did not replace tests or installation verification.
- Installed `downfall-agent@personal` version `0.1.0+codex.20260912044645` using the existing backup/cachebuster/CLI workflow. Installed verifier passed byte equality for index/view/context/lifecycle modules, actual six-tool discovery and read-only status (`stopped`, gameplay actions 0). Windows process inspection used scoped permission escalation. Java runtime and saves were not changed; no commit or push.
