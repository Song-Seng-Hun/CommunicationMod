# Downfall repair acceptance record

User authorized repair after the 2026-10-11 audit. Source baseline is HEAD d4e277d2f269bd00ae0cdcc460b1a7a5ccba9d1e plus pre-existing working changes; none are reverted. Installed Steam files, saves and gameplay are outside the mutation scope. The selected local test profile is preserved during release.

## Changes and boundaries

- HeartShopRoom's separate HeartMerchant now offers native shop entry. Execution rechecks the exact room, player, merchant, hitbox and shop owner under existing readiness guards, then uses one scoped native update. The copied-runtime builder patches HeartMerchant in its owning EvilWithin JAR and preserves base merchant behavior.
- Character bosses expose their public hand, energy, stance, relics, orbs and mechanic-panel information. Hidden cards have masked rows; hideCards has no hand count. Intent overlays and future AI/deck/RNG information are not read. Native stance rules are labelled separately from rendered text. Unknown rendering bindings produce incomplete markers.
- MCP advertises public_combat refs without dumping boss hands into every decision. Detail reads preserve native descriptions and completeness.
- Restored the previously installed optional explicit reuse protocol while keeping the current default pinned API. State/action reuse requires a retained full baseline; context reuse requires exact ref/content-ID possession. Cache misses and unsuitable deltas fall back to full responses. The cache is bounded, process-local and cannot authorize actions or bypass stale pins.
- Guidance source/module fingerprints cover the restored reuse and registration/formatting paths. The gameplay skill and MCP README document the current API and information limits.

## Evidence before release

- Heart regression: original missing-entry failure followed by 19 native-body assertions and existing input/room bindings passing.
- Boss regression: actual converter absence reproduced, then fresh production/native-binding/privacy tests passed. Native null stance images and abstract render declarations were caught by fixtures and corrected. Existing public-description/tooltip/draw-privacy checks also passed.
- MCP: 208/208 passed outside the sandbox. Sandbox run retained 207/208, with only test-owned process-tree termination denied. No assertion was weakened.
- Independent native input/privacy/MCP review found hidden hand_count incorrectly reporting zero; regression reproduced, then existing combat-summary suite passed 8/8 after suppressing that count and exposing hand_visible. Narrow re-review found no remaining actionable issue in scope.
- Installed-runtime status was checked read-only: stopped. No game was launched.

## Final release acceptance

- Mandatory `prepare-local-test.ps1` completed with exit 0, including the new Heart/boss checks, existing five Downfall UI groups, native input/room/protocol/transport/description/privacy/map tests, copied-JAR patch ordering and launcher integrity. Log: `target/repair-preparation-20261011.log`; transcript subdirectories preserve native fixture output.
- Final mandatory packaging passed **209/209 MCP tests**, plus launcher and eight isolated installer scenarios. Log: `target/repair-packaging-final-20261011.log`.
- Removed installer dependency on deleted plugin-creator Python helpers. The existing personal marketplace and exact paths are validated before mutation; version stamping, source/settings backups and whole-directory replacement preserve the established installation route without stale overlay files.
- Installed read-only verification found the newly prepared profile had no recordings directory. A real stdio regression reproduced the ENOENT; discovery now treats only an absent directory as no session. It retains explicit start guidance and never fabricates a native snapshot or creates a game/recording. The original failed check remains in `target/repair-installed-verification-20261011.log`. The verification expectation was aligned with the existing unavailable-state error contract, not changed to accept the raw ENOENT.
- Final installed plugin: `0.1.0+codex.20261011114119.d03d509a`. All **3686 files** match the verified package. All six MCP tools register with optional reuse and the default pinned API; `sts_game_status` reports stopped, and `sts_get_state` returns the expected actionable unavailable-session response. Final log: `target/repair-installed-verification-final-20261011.log`.
- Runtime: `E:\dev\gamemcp\sts\target\local-test-20261011-112857-4ecb39ca`. `CommunicationMod.jar` SHA-256: `b41c132518df7cbea499eb8e7595856382f81b566e845367d642d5d484065f1d`. It contains the previously missing reward/tutorial/wheel/match/preview classes plus BossObservation. Runtime JARs match the prepared manifest; native Steam inputs retain their pinned hashes.
- All **53 existing test-profile files** match the originals by SHA-256 after copying to the new runtime. Original profile and recordings remain in place; old recording endpoints were not copied. No Steam/cloud saves were imported. Evidence: `C:/Users/meleb/OneDrive/문서/ChatGPT/stsmod/profile-preservation.json`.
- Source promotion used before/after hash guards and backups. All 324 untouched audit-baseline files still match. Task-specific changes are recorded in the adjacent task folder's promotion manifests and `repair-changes.patch`; ordinary Git diff also includes pre-existing user changes. No commit or push was made.

## Remaining acceptance limits

No game was launched or played. Offline tests do not establish the full merchant-combat/reward/shop/purchase flow, full character-boss fights, GRID reward completion or a complete Downfall run. These require a separately requested live session. Chinese cached character-line-break descriptions, renderer-only StSLib tooltips and automatic GAME_OVER-to-next-run controls remain the conditional limits from the audit, not newly claimed support.

The change adds one focused boss observer and restores the prior reuse module. Existing merchant/converter/MCP registration and deployment paths were updated directly; no parallel legacy execution route was introduced. Most added verification covers native input/privacy boundaries and the demonstrated release failures.

