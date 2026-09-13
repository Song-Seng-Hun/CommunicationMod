# Card upgrade comparison implementation plan

> Execute inline with executing-plans and test-driven-development. Preserve the existing map changes, fork, original Steam installation and saves. The user asked to proceed autonomously after the missing capability was established.

**Goal:** Supply the next standard upgrade of the actual offered/owned card before acquisition or upgrade decisions, including repeatable upgrades, without upgrading the original card.

**Architecture:** A bounded pure preview service calls a game-thread adapter which uses the game's `makeStatEquivalentCopy`, one `upgrade`, and preview description/display initialization. Compare the current instance, not a card-library template. Screen bindings supply reward/shop/grid candidates, event button card previews, and noncombat event/rest/grid deck context. Export native confirmation/branch previews separately from a standard single-step prediction. Never simulate an event's random target, acquisition relic effects, or an unresolved branching upgrade.

**Tech stack:** Java 8-compatible code; installed Downfall bytecode and Javassist headless tests; existing TypeScript MCP view and node:test.

## Safety and output contract

- Gate hidden/locked/flipped cards before invoking any preview callbacks.
- Reject alias/null/wrong-card copies, mismatched current upgrade/stat state, unresolved StSLib branch/multi-upgrade interfaces, unreasonable copy replay counts and exceptions. A failure is explicit `unavailable`, not a missing field or `cannot upgrade`.
- `upgrade_preview` includes status, `scope=next_standard_upgrade`, one step, current count, after snapshot, changed fields/numeric deltas and whether another upgrade is possible. Never include a generated clone UUID or recurse into previews.
- Cache within one public decision context with a finite entry limit. Invalidate on context/card state changes; exclude only animation/history provenance. Native preview snapshots are reread, never cached or mutated.
- Keep description/cost/tooltip incompleteness flags. A standard preview is not a promise about event-specific effects, modifier purity, on-obtain effects, or exact render-only costs.
- No gameplay actions, action enablement, arbitrary event execution, new tool, tunnel, or remote service.

## Tasks

- [x] Add `devtools/UpgradePreviewTest.java`: test next step at +0/+3, nonlinear growth, limit reached, cost/effect change, hidden gate, branch gate, throwing/alias/wrong-state copies, unchanged originals, bounded caching and invalidation. Run reflection-based test against existing classes and observe the missing-feature assertion.
- [x] Add `observation/UpgradePreview.java`: implement `Adapter<T>` and `describe(T, String, Adapter<T>)`, using an identity-keyed bounded cache. Test all contracts headlessly, without game initialization.
- [x] Add `observation/CardUpgradeObservation.java`: game adapter, decision candidate collection, public-context invalidation, native grid preview and event option preview binding. Modify only targeted portions of `GameStateConverter.java` and clarify `CardObservation.java` scope. Add actual-binding/installed-bytecode checks and representative real upgrade method execution in the headless harness.
- [x] Extend `mcp-server/test/view.test.mjs`: reward/shop/grid comparison survives default and detailed views; event preview survives event prose deduplication; after flags and repeat count survive; event/rest deck comparisons remain available on demand. Observe failing event projection test, then update `view.ts` if needed.
- [x] Wire `devtools/verify-card-upgrades.ps1` into the existing foundation regression. Run focused Java/MCP tests, full preparation regression, review the diff and document actual evidence and remaining live acceptance. Refresh the existing local plugin only if its server bundle changed.

## Validation commands

`powershell -NoProfile -File devtools/verify-card-upgrades.ps1` must report model/real-method/binding passes. `npm test --prefix mcp-server` must include preview preservation tests. `powershell -NoProfile -File devtools/prepare-local-test.ps1` builds a fresh isolated runtime, validates original hashes and does not launch the game. `git diff --check` must pass.

Live acquisition, campfire/event UI alignment and extension-specific behavior remain separate acceptance unless actually exercised. Do not call the work full Downfall compatibility merely because headless tests pass.

## Execution evidence (2026-09-12)

- Observed RED assertions for missing service/binding, lost event previews, repeated failed-copy retries, inherited event buttons, missing native multi-upgrade tree, lost source effects and stale branch previews. All now pass.
- Added `NativeUpgradeTree.java` after inspecting the installed StSLib implementation: its current UI uses a separate graph, not the legacy grid preview list. Added source/lock/dependency tests rather than guessing the field source.
- Final `verify-card-upgrades.ps1`: all three model/binding/real-method groups pass. `npm test --prefix mcp-server`: 15 tests pass. Review retained the user's prior map changes and corrected copy-effect equivalence and stale-branch association; no subagents or branch operations.
- Final whole-runtime preparation exited 0; log `target/card-upgrade-release-verification.log`. Prepared `target/local-test-20260912-051708-b7329a33`, 24 transformed game classes, original Steam hashes unchanged, fresh mod and manifest verified. Launcher SHA256 `1278CC73342D826368192ED83469010B414422A26974104C0264FF886F66E5AE`.
- Existing personal plugin updated to `0.1.0+codex.20260911200800`. `verify-upgrade-plugin.mjs` verifies installed server bytes, event projection, six actual MCP tools and their descriptions; read-only game status was `stopped`, gameplay actions 0. No marketplace rewrite, new tunnel, game launch or save migration.
- Live shop/campfire/event comparisons and arbitrary extension copy/description purity remain unverified. New tasks pick up the updated local plugin.
