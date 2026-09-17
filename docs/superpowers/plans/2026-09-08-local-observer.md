# Local observer test preparation

> Execute inline with executing-plans; no workers or VM. This implements the approved local test preparation, not complete Downfall automation.

**Goal:** Let the user play a separate Downfall test copy while a JSONL client records localized observations and combat readiness.

**Architecture:** Reuse the packaged launcher's metadata, enum handling and initializers exactly once. Build a copy with the existing raw dialogue/combat/keyword hooks plus read-only frame transport. Do not enable the legacy command executor. A passive v2 session rejects actions and never claims full screen support. Disable Steam recording integration and the audited metrics senders in this test copy, without OS, Steam-client or VM changes.

**Tech stack:** Java 8 runtime, Java 11 compiler, existing Maven/Javassist/Gson, PowerShell.

## Tasks

- [x] Test passive handshake, legacy rejection, mutation refusal, player-data filtering and latest-state capture. Add `ObserverSession` and a bounded game-thread `LocalObserver` bridge. Use existing `ProtocolSession` without legacy action adapters.
- [x] Test a recording client against a real piped subprocess; UTF-8 JSONL only on stdout, bounded local logs, no automatic `act` messages. Add the client in the mod JAR.
- [x] Test builder output against installed bytecode: preserve bootstrap enum/initializer calls, add frame observation after both completed-frame hooks, block test submissions, preserve original input hashes. Add a NEW-output-only `BuildLocalObserver` tool and preparation script.
- [x] Prepare the runtime with copied dependencies, separate working directory and mod configuration directory. Add hash-checked launch/check commands. Leave existing legacy launchers blocked; the new observer path has no VM prerequisite.
- [x] Run baseline/regressions, packaged initialization smoke check and process protocol round trip. Document the exact launch entry, evidence files, and still-unverified actual play. Publish only to the user fork after staged-file review.

## Results

Full one-command preparation passed (`target/local-observer-final-verification.log`),
including existing regressions and the new protocol/client/bytecode/manifest checks.
The final copy also passed packaged initialization (`target/local-observer-smoke-latest.log`).
No game window was opened. User gameplay and UI/JSON comparison are the next acceptance step.

## Acceptance boundaries

No game window is opened during preparation. Smoke initialization is distinct from gameplay. Main-menu selection, event choices and cards are controlled by the human in this first test. Only listed observer hooks are installed, not legacy click/hover hooks. Unsupported screen capture fails explicitly without stopping normal human play. Steam Play integration and live v2 mutation dispatch are separate work.
