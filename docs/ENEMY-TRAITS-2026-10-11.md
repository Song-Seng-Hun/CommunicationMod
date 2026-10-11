# Enemy-specific combat rule visibility

The user reported that Gemini could not reason about enemy-specific battle rules: card-triggered Strength, increasing damage taken, hit-triggered intent changes, and linked revival. Live gameplay testing was unavailable. This repair improves the existing native-power/MCP path, without adding an enemy encyclopedia, predicting private AI, launching a game or restarting another client's MCP process.

## Findings and change

- Pinned native `AngerPower` (Enrage), `SlowPower`, `ReactivePower` (ID Compulsive) and `RegrowPower` (ID Life Link) contain the public trigger/effect text. Those four monster classes use the inherited public power tooltip. Current native capture already includes this data. Darklings can clear powers while half-dead; `half_dead`, `is_gone` and every monster remain observable, and this repair does not recreate cleared effects.
- Recorded Giant Head state 1392 in the September 17 test recording carries Slow's current 30% description. Its small default summary is complete, but the power-directory response previously gave names alone. It does not reproduce the user's exact Gemini episode.
- Large decisions silently discarded power descriptions and their truncation markers. Each summarized power now has a direct ref, with `details_required` for missing, clipped, omitted, incomplete or additional native effect details. Budget trimming removes only as many descriptions as needed. Negative native amounts, including visible Strength -1, are preserved.
- Paged power directories now expose the current description, type and amount beside the existing ref/title; clipping and native incompleteness remain explicit. A long description in a small batch page is deferred to its exact ref instead of dropping the power row. Full text remains accessible with normal fragment paging.
- The tool description explains that powers include innate battle rules and tells the consumer to read required details before choosing card order or kill targets. No action schema or dispatch changes.
- Native conversion now mirrors the pinned tooltip's `InvisiblePower` exclusion. Internal effects such as FakeOrRealPower are not public enemy traits. Detection follows inherited interfaces without adding a mandatory StSLib dependency.

## Verification and limits

MCP regression baseline: 16 passed, 3 failed (existing inline-power case plus two new omission/directory cases). First repaired focused run: 49/49 passed. Native visibility verifier failed before the filter and passed after it, including current-JAR bindings and existing pure checks. These are offline checks, not live gameplay. Required release checks, installation and independent review are recorded below when completed.

Existing API, replay/reconnect protections, potion inventory, native indexing and prior user changes must remain intact. Old running MCP processes need a reconnect to load a newly installed implementation. Model strategy quality and the exact original omission remain unverified.

## Completed release evidence

- Independent review found a long Korean power name plus metadata could still exceed an 8-ref page budget. The existing directory regression reproduced it (9 passed/1 failed); a minimal exact-ref fallback retaining incomplete flags repaired it. Affected checks then passed 31/31. Reviewer approved the final diff with no remaining actionable blocker.
- Full package suite: 221/221 MCP tests, zero skipped, plus mandatory launcher and isolated installer checks passed. Logs: `target/enemy-package.log`; all required native/runtime checks passed in `target/enemy-prepare.log`. The first v16 build rejected a missed matching JSON revision; the declaration was corrected, not the gate disabled.
- Installed plugin: `0.1.0+codex.20261011125032.d973d939`, guidance contract `2026-10-11-enemy-traits-v16`, fingerprint `2d4409bf52c5ba8df178555651ac400ee1ce4cba1798e5581cb82fc2c70b4c68` (115 source files). All 3,686 installed files match the checked package; module digests match; six tools register.
- Real installed `scripts/start.mjs` was exercised with an isolated synthetic backend: recorded Giant Head state1392 retains exact descriptions; Enrage/Slow/Reactive/Life Link fixtures reach state and detail replies; half-dead state persists without reconstructing cleared powers. Zero game commands sent. This validates the transport/installed entrypoint, not a live enemy encounter.
- New unlaunched runtime: `target/local-test-20261011-124641-817056ad`; CommunicationMod.jar SHA256 `7A2323A714D9625FD8A470DAB1AD02C238DB7135CB6E0CD52F776171878ADCBC` matches the build. All 53 previous test-profile files were copied with matching original/copy hashes. Original files and recordings remain intact; no Steam save imports.
- Baseline contained 560 source/config/doc files: all 551 untouched files retained their hashes; nine intended existing-file changes and one new report. Promotion backup: `target/repair-backup-20261011-124626-6f5d228c`. No unrelated edits, commits or pushes.
- Workspace evidence: `ENEMY-POWERS-TASKS.md`, `enemy-before.json`, `enemy-after.json`, `enemy-installed-verification.json`, `enemy-profile-preservation.json`, `enemy-runtime-verification.json`, `enemy-source-preservation.json`. Existing Gemini MCP PID3812 was still the October9 process at final inspection; it was not restarted. Reconnect before the next play session to load the updated server.

Verification scope is closed: authorized source, package, installed entrypoint and preservation checks passed. Live gameplay and Gemini's resulting strategic decisions remain unverified. Two focused regressions were added and existing inline/prose checks extended; no tests were removed or skipped, and no new framework or mutation tool was introduced.
