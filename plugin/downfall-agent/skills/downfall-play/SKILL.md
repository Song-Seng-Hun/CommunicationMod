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

## Efficient play

Use `sts_act` for one offered action and consume its returned next stable state. Read full localized card/keyword descriptions and current resources. Fetch deck/map/piles/history with `sts_get_context` only when relevant, using matching session/state IDs. `narrative.history_count` signals off-screen dialogue available in history. An `event_reading_ref` entry refers to the current event body, not missing text.

Never play from an unready or incomplete hand. Preserve hidden draw order unless the public observation says it is visible. Unsupported screens and incomplete information require stopping/diagnosis, not guessed clicks or console mutations.

Present event body, situation and choices before `acknowledge_event_reading`, with its observed `reading_id` and meaningful commentary; discuss result pages too. Do not acknowledge silently or fabricate missing text.

`unknown` / `applied_waiting` does not mean failure: inspect `sts_get_request` and current state; never replay an uncertain action. Keep the same request ID for inspection. A new game session may require reconnecting the MCP server after resolving an old pending outcome; never restart the game to obtain different results.

## User's current verification run

When asked to continue the existing Hermit verification: normal play only, stop at first death, no save editing/retry/reroll. Heart only if legitimately unlocked; otherwise report Act 3 fallback separately. Launching or connecting alone does not authorize choosing a new run or abandoning the saved run. Keep online-submission blocks enabled in this test copy. Do not claim whole Downfall compatibility or account-safety guarantees.
