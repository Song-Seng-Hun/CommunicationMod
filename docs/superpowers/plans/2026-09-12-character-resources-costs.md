# Character resources and special costs implementation plan

> **For agentic workers:** Use executing-plans inline in the existing user-owned checkout. The user explicitly requested filling these gaps before other work and previously requested direct implementation without repeated approval menus. Preserve earlier uncommitted work. Do not launch/play the game, edit saves, commit or push as part of this milestone.

**Goal:** Fill public character-mechanic and special-card-cost observation gaps for every player character and native modifier in the pinned installed Downfall build.

**Architecture:** Extend the existing game-thread public projector with bounded, audited native UI bindings. Use already computed fields and passive completed-render-frame observations for getters that produce side effects. Activate mechanisms by their displayed state, not exclusively the chosen character, so mixed/custom runs retain their information. Never execute costs, update game resources, query RNG, expose future draw order or declare unknown renderers complete.

**Tech Stack:** Java 8, Javassist installed-bytecode audits/actual-method tests, PowerShell, TypeScript MCP.

## Scope and design

The prior general approach (public fields plus explicit incompleteness) is retained and extended. Directly invoking arbitrary getters could change state; copying gameplay formulas would drift from the pinned implementation. Prefer audited read-only getters for stable public counters and passive hooks for rendered text, backed by installed-method tests.

1. Inventory all ten Downfall player classes plus the four base classes, native public panels, card/orb extensions, alternative-cost providers and render overrides. Record each source/activation rule in a support matrix. `AuditMechanicReferences` inspects bytecode only.
2. Cover Gremlin formation/HP, Collector reserves/essence/collection, Awakened spell slots/progress, Champ stance/technique/finisher, Guardian stasis/socketed cards, Hexaghost flames including Inferno, Automaton encode/function display, Slimebound summons, Snecko public unknown-card provenance and Hermit Dead On. Base resources remain mapped to powers, stance, orbs and existing counters. No unrelated hidden fields are exported.
3. Publish resource-aware cost components: rendered energy/free/X/unknown text, Collector reserves and reserve-only costs, Pyre's extra card sacrifice, and any additional resource renderers found in the pinned audit. Distinguish additional play costs from HP-loss/card effects described by the card. Preserve custom/unknown semantics instead of labeling every numeric text as energy.
4. Verify the public projection on actual installed methods with field-only graphics dependencies, state/visibility gates, mixed mechanics and unchanged game objects. Add each regression before its implementation and observe RED then GREEN.
5. Extend `BuildLocalObserver` and its bytecode tests only where passive hooks are needed; existing human input and packaged initialization remain intact. Run fresh production compilation, focused tests, prior suites and complete isolated runtime preparation.
6. Preserve new fields in the default MCP decision and on-demand context, update scope/tool descriptions, refresh the existing local plugin only if its server changed, then verify its actual read-only responses.

## Work checklist

- [x] Read-only native inventory and renderer/cost audit; evidence-linked coverage table.
- [x] Character/resource/card/orb regression tests (missing cases fail first).
- [x] Implement audited character and mixed-mechanic observations.
- [x] Cost semantics/frame-cache regression tests (missing cases fail first).
- [x] Implement special cost components and passive rendering hooks.
- [x] Verify installed bindings and actual native method behavior, public visibility, stale-frame/resource invalidation and no mutations.
- [x] Run `devtools/verify-run-usability.ps1`, new focused suite, `npm test` and `devtools/prepare-local-test.ps1` successfully.
- [x] Update support documentation, installed plugin when required, and final evidence. Distinguish implementation/automated coverage from unperformed live-play acceptance.

## Verification commands

`devtools/verify-run-usability.ps1` freshly compiles all production Java sources into an isolated target directory. New regressions will use the same extracted-Gson Java 8 test pattern. Installed native bodies come from the pinned four JARs, with graphics/constructor fixtures only; production projectors and actual renderer calculations are not replaced by test doubles. `npm test` rebuilds the MCP server. `devtools/prepare-local-test.ps1` runs the complete regression foundation, creates a fresh runtime, verifies the original hashes and promotes only a passing copy. Maven cache access may require the existing scoped escalation.

## Execution evidence

Starting point: branch `codex/communication-dev`, prior run-usability changes uncommitted; prepared runtime `local-test-20260912-061407-09e12e3e`, installed plugin `0.1.0+codex.20260911211644`, game stopped. This milestone fills the character/cost gaps, not the separate live-play acceptance work.

Implementation: `CharacterResources`, `NativeMechanicAccess`, `SpecialCost`, extended player/orb/card observation, and final post-BaseMod `renderEnergy` hook. See `docs/CHARACTER-RESOURCES-AND-COSTS.md` for the complete fourteen-character binding/visibility table and payment semantics. Default MCP output retains resources; collection contents are an on-demand `mechanics` section.

RED/GREEN evidence included missing mixed-character encode visibility, native encode-slot cap, private Inferno localization access, missing special-cost projection, missing noncombat MCP mechanic projection, and unknown `?` text on an underlying X-cost card. Character-resource tests additionally execute native getters with field fixtures and compare 36 Inferno states against the installed method. Forty getter bodies and all fourteen player types are checked against installed signatures/access levels.

One bounded read-only reviewer inspected the source and relevant installed bytecode. Its unknown-X classification finding was reproduced, fixed and confirmed; no remaining actionable findings were reported within that review scope. The reviewer did not launch the game or write files. Native field-access verification also caught Inferno's private localization cache, now reflected by its exact pinned field name only.

Final full preparation passed after the review fix: `target/local-test-20260912-070947-48b5b5fc`, 27 patched classes, original hashes unchanged, copied launch integrity passed. The final fresh focused class directory was `target/run-usability-tests/run-33713e0514f64d9ba686c2b927bb6e37`. The full foundation includes prior upgrade/map/input/transport/description tests. `SpecialCostTest` also passed with the final `target/CommunicationMod.jar` first on the classpath. MCP tests passed 17/17, including a real SDK request for the new section. `git diff --check` passed (line-ending warnings only).

Environment notes: the restricted initial Maven run failed on dependency/cache access. A preparation concurrent with the review fix correctly failed the new hidden-X regression and did not promote a runtime. After all production edits were finished, a fresh permission-scoped full pipeline passed. The initial plugin package attempt likewise failed on npm cache access; no failed package was installed. No game was launched, no gameplay action taken, no save edited, and no commit/push performed.

Installed bundle: `downfall-agent@personal`, version `0.1.0+codex.20260911221416`. The existing installer backed up previous source/settings, validated manifest and skill, and reinstalled from the unchanged personal marketplace entry. `verify-upgrade-plugin.mjs` passed against that exact cache: matching server bundle, event/room controls retained, mechanics retained, actual MCP tool schema includes the new section, game phase `stopped`, gameplay actions `0`. Windows CIM process status required a read-only permission-scoped retry. Use a new Codex task to pick up the updated tool schemas. No marketplace entry, game files or saves were manually rewritten.
