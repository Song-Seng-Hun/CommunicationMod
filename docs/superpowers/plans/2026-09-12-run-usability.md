# Run usability implementation plan

> **For agentic workers:** Use executing-plans to implement these tasks in order, in the existing user-owned checkout. Preserve the preceding map and upgrade work. No push, upstream PR, live gameplay or original-install changes.

**Goal:** Address the six findings in the user's approved gameplay-usability audit.

**Architecture:** Keep versioned one-action dispatch and the shared public-observation filter. Add small screen-specific adapters that inspect native UI controls, then revalidate identity/readiness and invoke the installed UI input handler. Never reopen the legacy executor, synthesize rewards, or simulate hidden effects. Publish explicit unsupported/incomplete reasons for unaudited extension modes.

**Tech Stack:** Java 8, prepatched Downfall, Javassist binding/actual-method tests, PowerShell verification, TypeScript MCP projection.

## Design and acceptance boundaries

Native UI adapters are preferred over restoring legacy commands (which bypass decision safety) or adding only explanations (which leaves progression blocked). The user requested direct implementation after the audit; execute inline without additional approval menus or worker dispatch. Existing dirty changes are preserved, not staged wholesale.

One offered action performs one UI decision. Old session/state/decision tokens, changed item identities/prices, hidden or moving buttons, pending selections and human input invalidate the action. Unknown extension screens remain unavailable. Expose only public rendered data; draw order, RNG and internal enemy moves stay private. Headless verification is not a live-play acceptance claim.

## Task 1: Reward and screen-control policies

- [x] Add failing behavioral tests for visible Proceed eligibility, larger/native special card selectors and safe empty/disabled modes. Actual elite/boss/event transitions remain live acceptance work.
- [x] Verify the installed ProceedButton, CardRewardScreen, GridCardSelectScreen and shop/campfire/potion method bodies before selecting input hooks.
- [x] Add bounded screen adapters and shared input helpers, preserving native handlers and live revalidation.
- [x] Route GRID and in-combat CARD_REWARD before the generic combat branch; retain whole-decision tokens.
- [x] Add rest/shop/chest/boss-relic actions and public selection prompts, counts, prices and cancellation state.
- [x] Verify installed handler wiring, scoped input negative cases and existing reward/hand/menu tests. Added actual installed branch/normal/tree selection-body execution with field-only fixtures; other room handlers still require live acceptance.

Files: `src/main/java/communicationmod/observation/RunUi.java`, `RewardUi.java`, new focused UI/policy helpers, `devtools/RunUsability*Test.java`, `BuildLocalObserver.java` and `LocalObserverBuildTest.java` if input hooks are required.

## Task 2: Potion controls and decision information

- [ ] Finish end-to-end potion slot replacement/target dispatch tests. Eligibility/target policy and native method bindings pass; exact slot/target revalidation is implemented but not yet exercised in a live popup.
- [x] Bind potion operations to audited native input paths, claim combat decisions, and revalidate slot/target immediately before dispatch.
- [x] Add per-card target availability and localized cannot-use messages, preserving fields modified by canUse probes.
- [x] Observe audited effective costs without modifying live cards; retain unavailable flags for unsupported render overrides.
- [x] Add orb descriptions and bounded public character-mechanic observations; explicitly mark unsupported character-specific panels.
- [x] Test published information, stale cost invalidation, source/message preservation and public visibility gates. Overall protocol stale-state/decision tests also pass.

Files: new `PotionUi.java`, card/player observation helpers, `GameStateConverter.java`, `CombatObservation.java`, `devtools/RunUsability*Test.java`.

## Task 3: MCP integration and release verification

- [x] Add MCP projection tests proving controls, costs, target restrictions and incompleteness survive the default decision view.
- [x] Update tool descriptions and user-facing support documentation with exact supported and unsupported modes.
- [x] Integrate `devtools/verify-run-usability.ps1` into the compatibility foundation.
- [x] Run focused Java tests, MCP suite and the complete isolated-runtime preparation pipeline.
- [x] Verify original-install hashes and prepared-runtime helper bindings; refresh the existing local plugin because its bundled server changed.
- [x] Review the final diff and record evidence and remaining live-UI acceptance gaps here.

## Commands

Fresh production build uses the existing `devtools/verify-keyword-guard.ps1` (scoped repository artifacts only). Focused regressions use `devtools/verify-run-usability.ps1`; MCP uses `npm test` in `mcp-server`; final integrated release uses `devtools/prepare-local-test.ps1`. Each script must exit 0 before claiming that layer passed.

## Execution evidence

Initial audit: six reproducible code-path gaps, not a live gameplay run. Starting branch is `codex/communication-dev`; preceding map and upgrade changes remain uncommitted and in scope for preservation.

### 2026-09-12 implementation verification

- `verify-run-usability.ps1`: fresh production compile and all 12 groups PASS; latest output `target/run-usability-tests/run-a1fe7a1390a7461a842486b5940daba5`. Added native StSLib branch/normal/tree handler execution with real scoped input after the integrated build; production sources did not change after that build.
- RED/GREEN review fixes: pending human potion popup must block room actions; Inferno observation must not call its logging getter; branch confirmation must show the chosen branch instead of the normal copy. Native selection was corrected from hover-only to `AbstractCard.update`; copied input hooks now run immediately before both installed StSLib selection postfixes.
- `prepare-local-test.ps1`: exit 0, complete foundation and runtime checks; 27 patched classes, original four JAR hashes preserved, startup/integrity and no-overwrite checks passed. Log: `target/run-usability-release.log`. Promoted runtime: `target/local-test-20260912-061407-09e12e3e`.
- `npm test`: 16/16 PASS. Existing plugin updated using the cachebuster/reinstall flow to `downfall-agent@personal`, version `0.1.0+codex.20260911211644`; previous source/settings backed up by the installer.
- Installed-plugin verification: bundle bytes match current build, six tools discovered, event previews and new controls preserved, updated descriptions present, read-only status `stopped`, gameplay actions `0`. Windows CIM status check needed the scoped read-only escalation, then passed.
- Final whitespace check passed. No commit/push, original-install write, save edit, game launch or gameplay performed.

Live room/potion/grid/reward acceptance, full-run tests and complete character/alternate-resource coverage remain open. See `docs/RUN-USABILITY.md`; this milestone is not full Downfall compatibility.
