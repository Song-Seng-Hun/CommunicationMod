# MCP local verification — 2026-09-08

## Implemented scope

Persistent stdio MCP server with focused snapshots, single-action/next-stable-state calls, on-demand public context, and receipt inspection. The authenticated loopback bridge replaces command-file polling in explicit `-McpControl` mode. Existing JSON v2, native game actions, offline copied runtime, hidden-pile and complete-hand guards remain authoritative.

This milestone is **not full Downfall compatibility or a Hermit clear**. The pre-existing hand-selection and standard-reward expansion work is preserved and included in the tested runtime; those new interactions have not yet been exercised in gameplay. Unsupported shop/rest/grid/boss/endgame screens remain unsupported.

## Automated evidence

- `devtools/prepare-mcp-test.ps1`: exit 0 with the final Java sources, including full existing privacy/draw/description/event/shop protection/transport/menu/run checks and new bridge/recording checks.
- Java bridge: wrong token rejected, UTF-8/Windows CRLF accepted, live state delivery, one pending command, applied receipt, count/memory-bounded output queue.
- Java recorder: unchanged states suppressed; size cap and simulated disk failure stop recording without stopping transport. Actual JSONL retained applied receipts during live play (82 records at inspection). Windows metadata reported length 0 while the file was open; reading its contents confirmed the records.
- Final `mcp-server` `npm test`: 8 tests passed, including real SDK stdio discovery/calls, delayed reconnect receipt-before-state regression, stale/duplicate/concurrent/timeout cases, localized focused views, hidden partial hand, extension preservation, current/history narration separation, and ten fixed read-only evaluation answers through the tools.
- The ten-question `evaluation/evaluation.xml` targets a synthetic frozen backend, never the current game. Answer reachability was checked deterministically; a separate LLM evaluation was **not** run. No additional agents/API charges.
- Failures were reproduced before fixing the missing bridge/view/recorder, CRLF parsing, queue memory limit, dropped extension fields, duplicated historical narration, and reconnect's premature empty snapshot.

## Actual game verification

- Runtime: `target/local-test-20260908-205155-5d6795ee`.
- CommunicationMod SHA-256: `3de5454bef9974d8c4a2da7c006c14b75f1c2db245334e87e1d2e8fb67b0c97a`.
- Recording: `recordings/1f55d912-b4c6-49b3-b173-2bf05eedcb65` (local only; token/logs/saves not committed).
- Protocol session: `7e7c9315-cc56-468d-a525-e02172775401`.
- Game observation reports `downfall_standalone`, Korean, `test_submissions: disabled_in_test_copy`.
- 46 existing test-profile files copied to the fresh runtime and hash checked. Previous runtime/profile retained. Normal window-close left an owned, windowless test JVM; only that exact process was stopped. No original Steam installation or cloud save changed.
- Resume returned applied + EVENT ready state 120 in one MCP call. Save checkpoint was before World of Goop's choice (74/75 HP, 113 gold), so the previously chosen option was repeated unchanged; no alternative result sought.
- After presenting the full event situation/options, acknowledgement returned state 124. `run.event.0` returned applied + stable result state 154, **63/75 HP and 188 gold**, matching +75 gold/-11 HP exactly.
- Result prose and the new required reading acknowledgement were present; no automatic acknowledgement was sent. The run is left on this result page, not advanced to another room.
- Read-only deck context returned total 11, three distinct card UUIDs on page one, Korean descriptions and displayed damage, and `next_offset: 3`. Upstream incomplete tooltip/cost-render flags remained visible rather than being falsely marked complete.
- `sts_get_request` returned the applied receipt with no pending request and no replay.

## Measured latency and size

Official SDK client + actual game bridge, one warm persistent connection. Model reasoning, Codex tool orchestration, cold process startup and user-facing narration are **excluded**. Different screens/machines will differ.

| Operation | Observed result |
| --- | --- |
| 50 read-only focused-state calls at stable event state 154 | median 1.054 ms; p95 1.976 ms |
| Focused response JSON including IDs/actions/runtime | 4,163 UTF-8 bytes |
| Same state's full public observation JSON | 18,715 UTF-8 bytes |
| Event reading acknowledgement → next ready state | 49.26 ms |
| Gold choice → next fully displayed result page | 483.20 ms |
| Resume → fully displayed event page | 5,626.48 ms (game loading/text included) |
| Paginated deck context | 1.16 ms |

The byte figures compare the focused envelope to public observation data, not entire duplicated MCP wire envelopes. They show reduced agent input, not a claim of identical fields or universal 78% savings. Read timing does not promise gameplay or model decision speed.

## Host registration and remaining checks

`communicationmod` was added through the local Codex CLI with absolute Node/server paths; `mcp get communicationmod --json` confirmed `enabled: true`. Other MCP entries were not edited. Actual SDK calls verified the server. Tool availability after reloading Codex's active MCP catalog remains a separate host check; adding config does not hot-load this already-running conversation.

Keep a single active controller. Disconnect the manual verifier before the host connects. The test game can remain open at state 154. No public network listener or new Steam integration was added. The existing test-only online blocks remain, but this milestone is not a universal account-safety guarantee.

Remaining gameplay tests: MCP combat/card use, hand deselection/confirmation, reward expansion, potion constraints, all later progression screens, and eventual Hermit/Downfall coverage. Existing headless tests do not substitute for these. See `mcp-server/README.md` for setup and timeout/reconnection rules.
