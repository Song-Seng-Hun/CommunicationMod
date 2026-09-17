# Null keyword crash diagnostics

The guard addresses the observed `TreeMap.containsKey(null)` crash in BaseMod's
`TipHelper.FakeKeywords.Prefix`. It filters nulls from a copy of the rendering
argument before that prefix runs, preserving the original card keywords and
all non-null keywords (including unknown ones). It does not repair missing
translations or determine where the invalid keyword was originally inserted.

On detection, search `sendToDevs/logs/SlayTheSpire.log` under the game's working
directory for `[COMM-KEYWORD-GUARD]`. The existing Downfall development launcher
uses the Steam Downfall installation as its working directory. Older game logs
are rotated into ZIP files alongside this file.

Each record includes card ID/name/class/source JAR when available, upgrades,
raw description, original card and render keywords, language, seed, floor,
room class, screen, character and a detection stack. The stack identifies where
the bad data was consumed, not where it was created. Missing source-JAR metadata
is recorded as unknown and does not prevent logging.

Identical incidents (seed, floor, card ID, description and render keywords) are
logged at most once per minute, with cumulative detection counts on subsequent
records. Up to 128 incident keys are retained; evicted incidents can log again.
Counts represent render calls, not separate shop visits. A failure in gathering
diagnostics emits `[COMM-KEYWORD-DIAGNOSTIC]` once while filtering continues.

## Verification and development artifact update

From the repository root:

```powershell
.\devtools\verify-keyword-guard.ps1 -UpdateDevelopmentRuntime
```

This builds the mod, checks malformed/normal/null-list inputs and original-data
preservation, verifies diagnostic fields and repeated-call suppression, and
compiles the raw patch against the installed prepackaged TipHelper. A bytecode
check verifies that the guard precedes the existing BaseMod prefix. Headless
unit tests omit graphics initialization and unrelated card gameplay methods;
they do not constitute gameplay testing.

The optional update changes only the existing development overlay and
`target/downfall-dev/mods/CommunicationMod.jar`. A copy of the previous overlay
is retained under `target/keyword-overlay/before-keyword-guard-*.jar`.
The source patch is also included in future CommunicationMod builds.

Steam's normal Play button still uses the original installation without this
guard. The updated development runtime must be used for the guard to take effect;
an already-running game must be restarted. In-game shop reproduction remains
to be validated. This change does not claim that other crashes are prevented.
