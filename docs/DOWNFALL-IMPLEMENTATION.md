# Downfall compatibility implementation and acceptance ledger

## Approved constraints

- Changes and non-force pushes go only to Song-Seng-Hun/CommunicationMod, never upstream/PRs.
- Target base game plus Downfall standalone/workshop; all registered characters,
  normal/custom/daily/endless modes and resume. Unrelated mods/future versions are excluded.
- New JSON-lines protocol v2, not legacy compatibility; include player-visible
  descriptions/numbers/state/actions. Never expose hidden draw order or arbitrary internal fields.
- **Revised local test policy (2026-09-08):** no VM requirement or blanket OS/native
  isolation prerequisite. Use a separate local test copy, keep manipulated test
  records out of online submissions, and address concrete risks in scope.
- The first human-play observer profile disables audited Steam integration/metrics
  recording in its copied JARs. This is not an OS security sandbox for arbitrary mods.
- No replacement of original executables/JARs, forced Steam shutdown, or global Java
  downgrade. Steam Play integration remains separate future work.
- Do not upload game/dependency JARs, saves, logs or personal configuration.

## Current milestone

| Area | Implemented | Verification / remaining work |
| --- | --- | --- |
| Launch safety hold | Legacy PowerShell and Java launchers refuse before game loading; subprocess/command entry held off | Headless guard checks; this is a hold, NOT an offline sandbox |
| Runtime inventory | ZIP-only hashes, root metadata, sorted package-name candidates | Fixture tests; candidates are NOT actual registered/verified content |
| Protocol v2 core | Version/session/state/action IDs, validation, replay rejection, invalidation, local-only failures; passive observer bridge | Unit and real child-process tests; game observation hooks installed in test copy; live mutation dispatch and gameplay acceptance unfinished |
| Transport | UTF-8, LF/CRLF, 1 MiB input bound, EOF termination, invalid-input/output failure shutdown | Real reader/writer thread tests; legacy command dispatch remains held off |
| Existing fixes | Hidden draw order policy and null keyword tooltip guard retained | Existing focused headless regressions; gameplay not verified |
| Public descriptions | Cached card text, dynamic variable values, localized keyword/standard extra hover tooltips, language marker, relic/potion/power text and player stance | Pure text/tooltip/privacy tests and installed-bytecode binding checks; character-break/CN cache, StSLib render-only tooltip extensions and displayed cost explicitly incomplete; custom rendering/getter purity and gameplay unverified |
| Dialogue/situation | Rendered speech/event words, known-origin speaker labels, bounded recent history and public situation; final-frame publication and known overlay filtering | JDK tests and actual-method insertion plus generated render-order fixture; custom renderers, pixel occlusion, actual patch loading/gameplay unverified |
| Downfall map coordinates | Optional adapter calls Downfall's actual first-node and boss UI helpers; graph connections remain the game's own normal/Flight/boot checks | Pure graph and installed-bytecode binding checks; real reverse-map/boss/act/endless play not yet verified |
| Offline bytecode preparation | Pinned four JAR hashes; new nonlaunchable copies; Steam/native entrypoints and LibGDX HTTP/socket/browser boundaries disabled; active metrics senders disabled | Java 8 transformation and artifact checks; original packaged bootstrap preserved byte-for-byte; NOT full native/OS/save/cloud isolation |
| Local observer profile | Separate hash-checked copy, passive v2 transport, human controls, test submission paths disabled | No VM required; initialization and process round trip checked, actual gameplay unverified |
| Base/workshop environments | Missing in the examined Steam library | Need legitimate installed game and normal MTS/BaseMod/StSLib/Downfall artifacts |

## Remaining ordered work

1. Validate human gameplay and observations with `LOCAL-OBSERVER-TEST.md`. The earlier
   VM/process-wide isolation prerequisite was withdrawn by the user. Keep test records
   separate and verify the relevant copied submission paths; do not claim OS isolation.
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
- `devtools/verify-dialogue-observation.ps1`: pure history/word visibility tests,
  installed-method patch insertion and generated JDK render-order fixture; no game execution.
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

## Dialogue milestone

`DIALOGUE-OBSERVATIONS.md` describes the new `narrative` contract. Capture excludes
constructor/action message text and records only successfully rendered words through
known paths. Event body output no longer reads the pre-reveal update cache. Known
overlays/fades suppress new capture; custom occlusion remains explicitly unverified.
History and notification rates are bounded. Existing action/online launch gates are
unchanged. The milestones above remain partial until actual offline UI acceptance.

## Event reading/discussion milestone

`EVENT-READING.md` adds the full-read -> client commentary -> choice -> result-read
contract. Ordinary dialog completion uses rendered word counts as well as textDone;
unread/hidden/truncated pages cannot be acknowledged or chosen. A v2 acknowledgement
action and headless Korean reference exchange are present; event choice and raw-input
guards are wired in source. No strategy AI is implemented. Live v2 dispatch, actual
UI verification and special event selectors/minigames remain incomplete. Existing
global automation/launch/online holds are unchanged; no game deployment is claimed.

## Local observer preparation (2026-09-08)

`LOCAL-OBSERVER-TEST.md` and `Start-Downfall-Test.cmd` define the new human-play
observation profile. Historical global-hold notes below refer to old entry points,
not a requirement for a VM. The new copy preserves the packaged initialization path,
installs only observer hooks, disables audited Steam integration/metrics recording,
and does not alter Steam Play, original JARs or original saves.

Validated: 14 passive v2 protocol/privacy assertions, a real child-process UTF-8
exchange using the packaged client, copied-bytecode hook ordering/submission checks,
launch manifest checks, full foundation regression and packaged initialization
without a game window. First clean initialization logged missing BaseMod console
history but completed successfully. Evidence: local `target/local-observer-*.log`.

At initial preparation, a real game window and captured gameplay were not yet
validated. The live startup follow-up below supersedes the window/transport part
only; UI/JSON comparisons, special screens and base/workshop variants remain open.
This is not live v2 action dispatch or completed Downfall support.

The first actual window launch exposed a `VerifyError` in the copied SteamIntegration:
the bundled Javassist compiled `return 0` as `ireturn` for `getGlobalStat(): long`.
Use type-specific long/float/double zero literals in the shared transformer. The
regression now checks the typed return instructions on the actual transformed methods;
the full preparation pipeline passed after this fix. This was not exercised by
the earlier initialization-only smoke check.

The next window launch reached the game's `create()` path and exposed an unconditional
`new SteamUtils(callback)` even though SteamAPI was disabled. The copied profile now
skips that native callback allocation; SteamInputHelper already skips its initialization
when Steam is unavailable, and clientUtils disposal is null-checked. Mouse/keyboard
remain the intended controls for this profile. `SteamUtilsStartupTest` reproduces the
old allocation and checks its removal as part of the full preparation command.

### Live recording and snapshot-cache recovery (2026-09-08)

The game reached a responding `Modded Slay the Spire` window and exchanged real v2
observations. The first recording client then exited when Windows denied replacing
`latest-state.json`; the process holding that file was not identified. A subprocess
regression using `NOSHARE_DELETE` reproduced the same AccessDeniedException before
the fix. Snapshot write/move IOException is now contained separately from the
authoritative JSONL write: no blocking retries, next-state recovery, bounded stderr.
Transcript write failures and the 64 MiB cap still stop the recorder explicitly.

Both actual Windows locking and temporary-file write failure tests pass through 40
consecutive failed updates without transcript loss, then recover to the newest
state. The complete `prepare-local-test.ps1` regression passed with the packaged
client (`target/observer-cache-fix-verification.log`). The previous test window was
closed; its remaining windowless JVM alone was stopped after a graceful-exit timeout.
The test profile was copied to the fresh runtime, with all 36 preference files
hash-verified unchanged; original Steam files/saves were not changed.

Live follow-up in `local-test-20260908-143445-9e80238b`: a 3-second Windows no-delete
lock on the actual recording cache preserved state 121 while JSONL grew from 76,575
to 83,527 bytes. Releasing the lock recovered to state 133 after 11 failed cache
updates, with one warning and one recovery line. Both game and recording client
remained alive; the game window responded. This verifies real menu observation
transport (`ENG`, `in_game=false`, `observation_only`), not combat readiness,
localized gameplay/UI correspondence or full Downfall compatibility.

## Menu control live milestone (2026-09-08)

`LOCAL-MENU-CONTROL.md` documents the explicit opt-in `-MenuControl` path. Default
launch remains passive. The menu session uses the existing v2 core, two completed
stable frames, same-state heartbeats, pre-dispatch manual-change validation, and
consumption before invoking the original UI handlers. Allowlisted menu fields are
read individually; unavailable characters do not expose their hidden identities.
No legacy executor, Embark, resume, abandon, daily/custom or combat action is enabled.

Full preparation passed (`target/menu-control-final-verification.log`), including
pure session guards/failed-action consumption, real subprocess request/reply tests,
installed menu method/field bindings, original UI post-click hiding, existing passive
recording/locked-cache regressions and copied runtime submission/hash checks. The
initial full build exposed the game's older bundled Gson lacking JsonObject.size();
argument checking uses the compatible entrySet().isEmpty() API. Local review found
and regression-tested MenuButton's post-effect hideMenuButtons call before deployment.

Actual runtime: `local-test-20260908-150305-0e1881fe`, language `KOR`. All 36 prior
test preference files were hash-verified unchanged after copying, including the gpt
profile and language setting. No previous test JVM remained when preparing the launch.
The four explicit live actions and resulting stable states were:

| From | Action | Verified next state |
| --- | --- | --- |
| MAIN_MENU, 4 | menu.play (게임 시작) | PANEL_MENU/PLAY, 10 |
| PANEL_MENU/PLAY, 10 | menu.panel.PLAY_NORMAL (일반) | PANEL_MENU/EVIL, 16; 일반/몰락 labels and descriptions |
| PANEL_MENU/EVIL, 16 | menu.panel.PLAY_NORMAL (일반) | CHAR_SELECT, 20; unlocked Hermit offered |
| CHAR_SELECT, 20 | menu.character.HERMIT (허밋) | CHAR_SELECT, 24; Hermit selected=true |

Every action returned `status=applied`; completion was verified from the next stable
state, not just that acknowledgement. The run was not started (`in_game=false`).
The Downfall/몰락 choice was observed and offered, but that alternative branch was
not executed in this check. Menus in base/workshop variants, other character choices,
arbitrary overlays, Embark and actual combat remain unverified or unimplemented.
This is a verified standalone Hermit menu path, not whole-game automation or full
Downfall support. Game/transport evidence is in the local launch log and the session's
`observations.jsonl`; game JARs, personal settings and logs are not committed.

## Whole-hand decision milestone (implementation boundary)

`COMBAT-READINESS.md` records the common full-hand gate: two matching completed
frames, actual pending-work/turn/visibility checks, versioned UUID-based actions,
live revalidation and consumption before mutation. Unstable ordinary hands are
withheld; forced standard selectors have a separate decision mode. Hermit position
indication uses the read-only UI predicate, only for stable hand rows. Unversioned
combat mutations are denied instead of bypassing the snapshot token. Global safety
holds remain active, and the live v2 bridge and actual gameplay are still unverified.
