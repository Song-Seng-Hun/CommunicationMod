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

`guidance` shows current examples, not permissions or game facts. Use its inline example when relevant; otherwise read only needed toc refs through `sts_get_context`. `more` points to the remaining current directory. No guidance: keep using ordinary state/rules. `map_plan` view stays map-only.

`normal`, `incomplete`, `exception` are conditional examples, not assertions about the current game. Follow `bindings` to actual observations. Do not send $bind objects or copy illustrative IDs. Reuse static examples only while revision matches; rebind dynamic facts after state changes. An example is atomic: offset 0, no child reads. Do not retrieve examples already understood or turn their call sequences into automatic execution.

## Efficient play

Use `sts_act` for one offered action. Its returned ready next state replaces a redundant `sts_get_state` when sufficient. Read full localized card/keyword descriptions and current resources. Fetch deck/map/piles/history only when relevant. `narrative.history_count` signals off-screen dialogue. `event_reading.body_ref` points to the current event body; follow its text pages to completion.

| Situation | Next call rule |
| --- | --- |
| Same session/state; needed facts already read | Reuse them. No duplicate lookup. |
| State changed | Reconfirm dynamic cost, resources, targets, playability and action availability. Stale rejection: refresh state and rediscover refs. |
| `parameters:{}` | No arguments needed; not proof that gameplay evidence is sufficient. Still read required card cost, targets, effects and warnings. |
| `parameters_ref` | Read that ref for arguments; follow its child refs if incomplete. Other `details_required` still applies. |
| Several needed refs | Batch 1–8 `refs` in `sts_get_context`, matching session/state IDs. Different `offset` values require separate calls. |
| Waiting for change | `sts_get_state({known_view:previous_view_id,wait_ms:15000})`. After two consecutive unchanged waits, stop and report; no automatic heartbeat. |
| Selected card | Use its explicit ref; complete card fragment includes descriptions, keywords and upgrade comparison. If paged, follow relevant child refs and `next_offset`. |
| Format | Default JSON for small reads. Use `response_format:"compact"` only for large list/directory pages (10+ rows requested). Keep this rule; do not reconsider format each call. |

Routine reads need no repeated plan or narration. Preserve required progress updates, event explanations and risky-action confirmations. Reduced calls never justify guessed facts, automatic destructive approval or uncertain-command replay.

Never play from an unready or incomplete hand. Preserve hidden draw order unless the public observation says it is visible. Unsupported screens and incomplete information require stopping/diagnosis, not guessed clicks or console mutations.

Present event body, situation and choices before `acknowledge_event_reading`, with its observed `reading_id` and meaningful commentary; discuss result pages too. Do not acknowledge silently or fabricate missing text.

`unknown` / `applied_waiting` does not mean failure: inspect `sts_get_request` and current state; never replay an uncertain action. Keep the same request ID for inspection. A new game session may require reconnecting the MCP server after resolving an old pending outcome; never restart the game to obtain different results.

## User's current verification run

When asked to continue the existing Hermit verification: normal play only, stop at first death, no save editing/retry/reroll. Heart only if legitimately unlocked; otherwise report Act 3 fallback separately. Launching or connecting alone does not authorize choosing a new run or abandoning the saved run. Keep online-submission blocks enabled in this test copy. Do not claim whole Downfall compatibility or account-safety guarantees.
