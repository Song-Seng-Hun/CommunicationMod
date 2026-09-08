# Downfall compatibility implementation and acceptance ledger

## Approved constraints

- Changes and non-force pushes go only to Song-Seng-Hun/CommunicationMod, never upstream/PRs.
- Target base game plus Downfall standalone/workshop; all registered characters,
  normal/custom/daily/endless modes and resume. Unrelated mods/future versions are excluded.
- New JSON-lines protocol v2, not legacy compatibility; include player-visible
  descriptions/numbers/state/actions. Never expose hidden draw order or arbitrary internal fields.
- **Online recording permission was revoked.** No agent/test achievement, statistic,
  leaderboard submission or account-connected automatic play before isolation is verified.
  Daily challenges are local functionality tests only, not online submission tests.
- Test saves must be separate from real saves and cloud synchronization. Merely
  changing LOCALAPPDATA, a game profile, or a JVM offline flag is not proof.
- Steam Play integration is conditional on this safety gate. No replacement of
  original executables/JARs, forced Steam shutdown, global Java downgrade, or bypass switch.
- Do not upload game/dependency JARs, saves, logs or personal configuration.

## Current milestone

| Area | Implemented | Verification / remaining work |
| --- | --- | --- |
| Launch safety hold | Legacy PowerShell and Java launchers refuse before game loading; subprocess/command entry held off | Headless guard checks; this is a hold, NOT an offline sandbox |
| Runtime inventory | ZIP-only hashes, root metadata, sorted package-name candidates | Fixture tests; candidates are NOT actual registered/verified content |
| Protocol v2 core | Version/session/state/action IDs, validation, replay rejection, invalidation, local-only failures | Headless unit and fixture pipe tests; not connected to gameplay |
| Transport | UTF-8, LF/CRLF, 1 MiB input bound, EOF termination, invalid-input/output failure shutdown | Real reader/writer thread tests; legacy command dispatch remains held off |
| Existing fixes | Hidden draw order policy and null keyword tooltip guard retained | Existing focused headless regressions; gameplay not verified |
| Public descriptions | Cached card text, dynamic variable values, localized keyword/standard extra hover tooltips, language marker, relic/potion/power text and player stance | Pure text/tooltip/privacy tests and installed-bytecode binding checks; character-break/CN cache, StSLib render-only tooltip extensions and displayed cost explicitly incomplete; custom rendering/getter purity and gameplay unverified |
| Downfall map coordinates | Optional adapter calls Downfall's actual first-node and boss UI helpers; graph connections remain the game's own normal/Flight/boot checks | Pure graph and installed-bytecode binding checks; real reverse-map/boss/act/endless play not yet verified |
| Offline bytecode preparation | Pinned four JAR hashes; new nonlaunchable copies; Steam/native entrypoints and LibGDX HTTP/socket/browser boundaries disabled; active metrics senders disabled | Java 8 transformation and artifact checks; original packaged bootstrap preserved byte-for-byte; NOT full native/OS/save/cloud isolation |
| Native Steam/cloud isolation | Storage/direct-upload bytecode audit completed; runtime isolation not verified | Still blocks game/agent launch and Steam Play integration |
| Base/workshop environments | Missing in the examined Steam library | Need legitimate installed game and normal MTS/BaseMod/StSLib/Downfall artifacts |

## Remaining ordered work

1. Use the static findings in `OFFLINE-STORAGE-AUDIT.md` and prepared offline artifacts
   to implement and verify the remaining process-wide network/native-library boundary,
   separate writable storage/configuration, and actual cloud exclusion.
   Implement a separate verified offline runtime with game and library hashes bound
   to verification. Keep the unconditional launch/automation hold until it passes.
   Never interpret an environment property or a user-editable "verified" JSON as proof.
2. Remove duplicate core patching of standalone; preserve its packaged metadata and
   original initializer behavior. Normal game/workshop use normal ModTheSpire.
3. Connect bounded v2 transport and game-thread snapshot/action adapters; retire
   legacy wire protocol. Add observation privacy filters, rendered dynamic descriptions,
   target validation, manual-transition invalidation and diagnostic context/throttling.
4. Implement menu/mode/resume, reversed map/boss/act/endless transitions; then
   character features (Guardian stasis/sockets, Hexaghost flames, Gremlin mob,
   Automaton sequence/functions, Collector collection, remaining registered characters).
   Activate by actual mechanics, including modifiers, not only character identity.
5. Complete special events/minigames/shop/rest/rewards/bosses; inventory every registered
   item as observed/actionable/tested separately. Unknown interactions pause automation.
6. Verify every character/mechanic and modifier individually, pairwise modifier cases,
   three runtimes, normal/custom/local-daily/endless/resume, and retained regressions.
   Only verified offline fixtures may synthesize gameplay state.
7. After safety and gameplay gates pass, implement backed-up per-app Steam launch options,
   latest-verified artifact activation, hash mismatch rejection, and conditional rollback.
   Do not claim whole-plan completion until the ledger has no required unverified rows.

## Safe commands now

- `devtools/verify-compatibility-foundation.ps1`: build and all headless checks;
  `-UpdateDevelopmentRuntime` refreshes only the blocked local mod/launchers with a backup.
- `devtools/verify-launch-safety.ps1`: no game classpath; ensures launch remains blocked.
- `devtools/runtime-inventory.ps1`: read-only ZIP inventory as JSON stdout.
- `devtools/verify-runtime-inventory.ps1`: temporary fixture archives under target.
- `devtools/verify-offline-bytecode.ps1`: Steam and HTTP transformation checks plus
  path/hash rejection fixtures, no game execution. `-PrepareArtifacts` additionally
  creates NEW nonlaunchable copies under `target/offline-prepared-<id>` and validates
  their executable bytecode. It does not activate them or lift any safety hold.
- `devtools/verify-protocol.ps1`: protocol core and fixture pipe tests, no Steam.
- `devtools/verify-transport.ps1`: actual reader/writer tests; invalid-input errors in the log are expected test cases.
- `devtools/verify-public-descriptions.ps1`: pure Java 8 text/privacy checks and static
  installed-renderer/binding verification; never initializes game objects.
- `devtools/verify-map-compatibility.ps1`: pure graph fixtures and static optional
  adapter/dispatch checks against the built mod and installed Downfall helper signatures.
- Existing keyword and draw-pile verification scripts: headless build/regression only.

The old `build-and-run-downfall.ps1`, including `-SmokeTest`, now deliberately
fails with `COMM-SAFETY-BLOCK`. Older already-running games/previously copied JARs
are not hot-patched by source edits. Steam settings and real saves are unchanged.

## Offline preparation evidence and limits (2026-09-08)

- Against the pinned standalone desktop JAR, regression first rejected the original
  SteamIntegration constructor's `SteamAPI.loadLibraries` call, then the original
  native method and active two-argument `Metrics.sendPost` method. Transformed checks pass.
- 347 Steam native entrypoints are removed from the prepared desktop JAR (6 in the
  neutral SteamAPI and 341 further native methods). Steam initialization remains false;
  other native writes throw. The shared Steam library loader is disabled too.
- SteamIntegration cloud deletion, achievements, stats, scores and presence methods
  no longer call Steam. LibGDX HTTP reports failure, sockets throw, URI opening returns
  false. Both metrics sendPost overloads, upload workers and BotDataUploader are disabled.
  Existing local-history collection/saving is preserved.
- Prepared manifests have no Main-Class or Class-Path. Original packaged launcher
  class bytes are preserved, including embedded mod metadata and initializer order;
  no MTS core patching or game initialization runs during preparation.
- These are known Java-boundary protections, not an OS sandbox. Direct Java networking
  outside LibGDX, other native libraries, actual save/config write paths, Steam Auto-Cloud,
  additional mods, and actual runtime classpath loading still require verification.
  None of this authorizes game launch; the unconditional holds remain.
- The inspected system identifies as Windows 11 Home Insider Preview. No callable
  WindowsSandbox/Get-VM/VBoxManage/vmrun was found. Optional-feature inspection requires
  Windows administrator elevation and was not completed; no OS feature was enabled.
- Base game desktop JAR and workshop directory remain absent at the inspected paths.
  All three-environment and actual gameplay acceptance rows therefore remain incomplete.

## Observation and map scope (2026-09-08)

Card descriptions come from existing `DescriptionLine.text`, not a rerun of card
description/modifier hooks or powers. Variables present in these cached lines are
resolved with BaseMod's `isModified ? value : modifiedBaseValue` policy. Unknown
variables remain literal with `description_complete=false`; missing/hidden/flipped
card descriptions do not expose new text/values. Existing legacy identity/cost fields
are unchanged; this is not a complete hidden-information audit.

`Settings.lineBreakViaCharacter` selects a different cached encoding: damage can be
bare `D`, while block/magic can be `!B!!`/`!M!!`. This encoding is currently withheld
with `cn_cached_encoding_unsupported`, not falsely marked decoded. Displayed cost is
also explicitly unavailable. Custom render-time hooks, cache freshness, upgrade popups,
and arbitrary extension getter purity remain unverified. Description completeness
only concerns cached text/token resolution, not complete/pixel-identical UI support.

The map adapter calls `FlipMap$FirstRoom.isValidFirstNode` and
`FlipMap$BossStuff.compatibleGetARealY` when EvilWithin is loaded. This reuses evil-mode,
invalid-act, actual startY and ending-map rules rather than hardcoding reversed rows.
Displayed choices and actual boss dispatch share `bossNodeAvailable`. Missing/failing
optional bindings disable map choices and emit a single local diagnostic; no base-map
fallback is used when a loaded Downfall adapter fails. Base play has no symbolic
Downfall dependency. Real gameplay and adapter failure injection remain unverified.

## Resource discipline after interrupted run

The user requested fewer subagents after PC overload. Resume work locally without
new worker fan-out; run builds/tests sequentially. Map and description verification
JVMs use bounded heaps. Do not assume other user-owned sessions may be terminated.
Full compatibility remains the task, not the status of this partial milestone.

## Card and keyword text milestone (2026-09-08)

See `CARD-TEXT-OBSERVATIONS.md` for payload fields, source contracts, bounds and
remaining coverage. Exact card keyword keys now resolve against the game's current
localized dictionary. Standard CustomCard and modifier hover providers add detached
text; missing/null/linkage failures remain explicit without leaking exception detail.
Hidden cards never enter these getters. StSLib render-time additions remain marked
unsupported, and CN card-cache decoding is still withheld. New tooltip fields are
included in draw-order permutation regressions. Runtime/online safety holds stay in
place; code verification does not constitute installed or live-game acceptance.
