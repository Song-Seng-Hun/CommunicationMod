# Local menu control implementation plan

> Execute inline with executing-plans and TDD, in the user's existing fork checkout; no workers or extra worktree. This is the menu portion of the approved v2 compatibility plan.

**Goal:** Operate visible Play/Standard/Slay-the-Spire-or-Downfall panels and select an unlocked character, stopping before Embark.

**Architecture:** Keep passive recording as the default. Explicit `-MenuControl` enables a separate v2 menu session. The game thread captures allowlisted UI text/buttons, waits for two stable frames, validates the same UI again, then calls existing menu handlers. A local request-file adapter forwards JSONL over the existing child stdin/stdout; it never creates actions automatically. Unknown screens, overlays, locked choices, resume/abandon, daily/custom, Embark and combat remain unavailable. The copied-runtime submission protections and hash checks are unchanged.

**Tech stack:** Java 8, Gson, existing ProtocolSession, PowerShell, installed Downfall bytecode for API verification.

## Tasks and acceptance

- [x] Add `devtools/MenuControlSessionTest.java`: handshake, two-frame readiness, stable state IDs, stale/manual changes, duplicate rejection, empty arguments, failed mutation consumption, unsupported screen. Run against the old JAR and require a missing-menu-session assertion; then implement `protocol/MenuControlSession.java` and rerun.
- [x] Add `observation/MenuUi.java`: read explicit labels, panel text and unlocked character rows; no broad reflection dump. Bind public MenuButton.buttonEffect, virtual panel buttonEffect after panel hide, and existing CharacterOption.updateHitbox selection. Check installed signatures and actual dispatch branches; return unsupported on unknown menu/popup or capture failure.
- [x] Add `devclient/MenuRequestInbox.java` and extend `ObservationClient` only with explicit `--menu-control`; real subprocess test proves one-request forwarding, no replay, EOF exit and local reply recording; retain passive client regressions. Command helper reads an offered action from the current snapshot, includes session/state/request IDs and never retries a mutation automatically.
- [x] Wire `LocalObserver` opt-in dispatch before normal passive capture, `LocalObserverLaunch --menu-control`, `start-local-test.ps1 -MenuControl` and preparation tests. Run the full existing foundation and copied-runtime checks.
- [x] Preserve the current test profile, rebuild and relaunch (no previous test JVM remained at the process check). Use the real connection to reach character selection and select Hermit; record result and selected row. Do not start a run. Document actual results and remaining screens; publish only to Song-Seng-Hun/CommunicationMod.

Protocol wire remains `{type:"act",session_id,state_id,request_id,action_id,arguments:{}}`.
An applied result acknowledges handler execution; only a subsequent stable state proves the destination screen/selection.

## Results

Full preparation passed in `target/menu-control-final-verification.log`. The real
Korean-language menu route reached Hermit selected at state 24: main 4 → play panels
10 → Downfall mode panels 16 → character selection 20 → Hermit selected 24. Four
explicit actions returned applied. No Embark or gameplay action was sent. See
`docs/DOWNFALL-IMPLEMENTATION.md` for the support boundary.
