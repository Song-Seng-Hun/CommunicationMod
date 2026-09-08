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
| Native Steam/cloud isolation | Not implemented | Blocks game/agent launch and Steam Play integration |
| Base/workshop environments | Missing in the examined Steam library | Need legitimate installed game and normal MTS/BaseMod/StSLib/Downfall artifacts |

## Remaining ordered work

1. Audit native Steam initialization/callbacks, achievement/stat/score writes,
   remote storage, game save paths, and prepackaged initializer/patch ordering.
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
- `devtools/verify-protocol.ps1`: protocol core and fixture pipe tests, no Steam.
- `devtools/verify-transport.ps1`: actual reader/writer tests; invalid-input errors in the log are expected test cases.
- Existing keyword and draw-pile verification scripts: headless build/regression only.

The old `build-and-run-downfall.ps1`, including `-SmokeTest`, now deliberately
fails with `COMM-SAFETY-BLOCK`. Older already-running games/previously copied JARs
are not hot-patched by source edits. Steam settings and real saves are unchanged.
