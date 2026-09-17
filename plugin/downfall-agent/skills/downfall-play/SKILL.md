---
name: downfall-play
description: Launch the locally installed Downfall test game, connect its CommunicationMod MCP tools, and inspect or play the user's run. Use for Downfall game launch, connection, state inspection, or requested agent gameplay.
---

# Downfall Agent

This plugin controls a prepared local test copy, not the original Steam executable. Game saves live in the configured workspace, outside the plugin cache. Do not infer full game support from plugin availability.

## Launch and connect

- For a launch/play request, call `sts_start_game`. It reuses the same game if already running. Do not use a shell to start another game, resume automatically, or alter Steam launch options.
- If it returns `starting`, allow loading and use `sts_get_state` or `sts_game_status`; the launch is already in progress. `launch_in_progress` means another caller holds the launch lock. Query status rather than running another launcher.
- For an inspection-only request, use `sts_game_status` / `sts_get_state`; do not launch without a launch/play request.
- A different runtime/control mode or second controller is a conflict, not permission to kill processes. Report the exact conflict. Never replace or close a game to force connection.
- Missing/changed runtime means run the repository's preparation workflow only when setup/update is requested. Rebuilding is not part of ordinary launch. Preserve the chosen test saves and do not auto-copy Steam/cloud saves.

| Before a session | Example → judgment |
| --- | --- |
| Inspection only | `sts_game_status({})` → report process/connection; stopped is not permission to launch. |
| Explicit launch/play request | `sts_start_game({wait_ms:15000})` → starting: `sts_game_status({})`; ready: inspect state. Conflict: stop, no relaunch/kill. |

## Optional situation examples

`guidance` may appear in the current toc. It is not permission or a game fact. Read it only when the current situation is unclear. `normal`, `incomplete`, and `exception` examples are conditional examples, not assertions about the current game. Follow bindings to actual observations. Do not send `$bind` objects or copy illustrative IDs. Do not retrieve examples already understood or turn their call sequences into automatic execution.

## Efficient play

`sts_get_state` pins the exact state shown to this MCP client. Session/state identity, delta hashes, native action IDs, and fixed action constants stay inside the server. `sts_get_context` and `sts_act` automatically use that pin. A stale pin is rejected and requires a fresh `sts_get_state`.

Use `sts_act` for one offered action. Send only its short `action_id` and gameplay `arguments` that remain in the public parameter schema. The server restores pinned constants such as current map identity/revision or event-reading identity, creates request IDs, and owns action timeouts internally. Its returned next state replaces a redundant `sts_get_state` when sufficient. Read full localized card/keyword descriptions and current resources only when needed. Fetch deck/map/piles/history only when relevant. `event_reading.body_ref` points to the current event body; follow `next_offset` only when it is present.

| Situation | Next call rule |
| --- | --- |
| Needed facts already present in the pinned state | Reuse them. No duplicate lookup. |
| Same state check | `sts_get_state({})` may return only `unchanged:true`. |
| Wait for change | `sts_get_state({wait_ms:15000})`; the server compares with the last shown view. After two consecutive unchanged waits, stop and report. |
| Full summary needed again | `sts_get_state({refresh:true})`. |
| State changed or stale pin rejected | Refresh state, then reconfirm dynamic cost, resources, targets, playability and action availability. |
| Action has no `parameters_ref` | Call it with empty/default `arguments`; this does not mean gameplay evidence is sufficient. |
| `parameters_ref` present | Read that ref and send only the remaining public gameplay arguments. Fixed constants omitted there are supplied by the server. |
| Several needed refs | Batch 1–8 refs in `sts_get_context`. Different `offset` values require separate calls. Multiple card refs may return summaries; read one card ref alone for full detail. |
| Selected card | Use its explicit ref. A single-card read can return full descriptions, keywords and upgrade details. |
| Format | State/action/context responses automatically use lossless TOON only when it is materially smaller; otherwise they remain JSON. Do not request a format just to optimize size. Use `response_format:"json"` only when literal JSON syntax is specifically needed. |

Routine reads need no repeated plan or narration. Preserve required progress updates, event explanations and risky-action confirmations. Reduced calls never justify guessed facts, automatic destructive approval or uncertain-command replay.

Never play from an unready or incomplete hand. Preserve hidden draw order unless the public observation says it is visible. Unsupported screens and incomplete information require stopping/diagnosis, not guessed clicks or console mutations.

Present the current event body, situation and choices before `acknowledge_event_reading`, then send meaningful commentary. The server binds the acknowledgement to the pinned event page. Discuss result pages too; do not acknowledge silently or fabricate missing text.

`unknown` / `applied_waiting` does not mean failure: use the returned `request_id` with `sts_get_request`; never replay an uncertain action. Normal successful actions do not expose request IDs. A new game session may require reconnecting the MCP server after resolving an old pending outcome; never restart the game to obtain different results.

## User's current verification run

When asked to continue the existing Hermit verification: normal play only, stop at first death, no save editing/retry/reroll. Heart only if legitimately unlocked; otherwise report Act 3 fallback separately. Launching or connecting alone does not authorize choosing a new run or abandoning the saved run. Keep online-submission blocks enabled in this test copy. Do not claim whole Downfall compatibility or account-safety guarantees.
