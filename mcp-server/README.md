# CommunicationMod MCP

Persistent, local-only MCP control for the **verified copied Downfall test runtime**. This does not install a Steam launcher or expand the set of supported game screens.

For the integrated Codex plugin (game launch through MCP), see `docs/CODEX-PLUGIN.md` in the repository. It packages this server and adds `sts_start_game` and read-only `sts_game_status`. Prefer the plugin over a duplicate manual MCP registration.

## Build and launch (PowerShell, repository root)

```powershell
.\devtools\prepare-mcp-test.ps1
.\devtools\start-local-test.ps1 -McpControl
```

Requires Node.js 22+, the existing Maven/JDK build prerequisites, and the installed standalone Downfall runtime. Preparation runs Node and complete Java regression checks before promoting a fresh copied runtime. Java 11 and original Steam files are unchanged; the test game uses bundled Java 8 and existing online-submission blocking. A newly prepared runtime does **not** automatically migrate saved games. Preserve/copy only the chosen test profile before resuming; never replace Steam/cloud saves.

Register once in the MCP host (replace absolute paths):

```powershell
codex mcp add communicationmod -- C:\absolute\path\node.exe C:\absolute\workspace\mcp-server\dist\index.js
```

The host starts the stdio server; do not start a second controller manually. Reload the host's MCP connections after registration. `COMMUNICATIONMOD_WORKSPACE` optionally overrides the workspace inferred from `dist/index.js`. Game startup remains explicit. Registration alone does not prove tools are active in an already-running conversation.

## Agent workflow

| Tool | Purpose |
| --- | --- |
| `sts_get_state` | Current screen, complete localized descriptions/keywords, resources, and offered actions. Optional bounded wait for readiness. |
| `sts_act` | Exactly one offered action, its receipt, and the next stable state in one call. |
| `sts_get_context` | On-demand deck/map/piles/history/screen/full public observation. Same session/state required; arrays are paginated. |
| `sts_get_request` | Inspect an uncertain request without replaying it. |

1. Call `sts_get_state` with `{"wait_ms":8000}`. When `ready` is false, no action is permitted. Inspect `support`, `decision`, and `connection` before deciding.
2. Copy an action ID and its constraints from `actions`; call `sts_act` with the returned `session_id`, `state_id`, `action_id`, and `arguments`. Do not construct unoffered action IDs. Supplying a unique `request_id` is recommended (the server generates one if omitted).
3. Use the returned stable state for the next decision. Fetch `sts_get_context` only when needed, e.g. `{"session_id":"<observed>","state_id":123,"section":"deck","offset":0,"limit":10}`. A changed state requires rereading; do not combine mismatched observations.

`outcome: applied` means an authoritative applied receipt and a later ready state arrived. `applied_waiting` means applied but the next ready state is still pending; `unknown` means the outcome is uncertain. Both return `retry_allowed:false`. Read state/request instead of resending. Mutations remain locked until receipt + stable state reconcile. After a game-process/session replacement, an unresolved old request may require restarting the MCP server **after inspecting the saved outcome**; no automatic retry occurs. Missing receipts are not proof of failure. The server retains 128 receipts and at most 10,000 request IDs per MCP process.

Event acknowledgement still requires `reading_id` and meaningful `commentary` in action arguments. Read the entire body and options aloud/explain before acknowledging; never silently auto-acknowledge. Card animation/whole-hand, stale-state, duplicate-request, and hidden-information protections remain in the Java v2 contract.

## Focus and performance boundaries

- Default responses omit the full deck, map, off-screen card piles, narrative history and repeated provenance. Full current card/event text and keywords remain; no token truncation or names-only cards.
- Additional character/modifier fields are retained rather than silently discarded. Unrecognized content is not treated as supported merely because it can be displayed.
- This first version returns a focused snapshot, not a fragile delta. Full public context remains available on demand, including upstream information-completeness flags.
- A persistent Node process and loopback stream replace per-choice PowerShell spawn, file inbox polling, and log rereading. `sts_act` waits for game events rather than requiring the agent to poll after every action.
- Game animations and model reasoning still take time. Cached reads are not evidence that gameplay/LLM decisions take the same time.

## Local transport and records

The observation child binds only `127.0.0.1` on an ephemeral port. Each launch gets a random 256-bit token in its ignored `recordings/<id>/mcp-bridge.json`. Never commit, print, or share that descriptor. This protects against unauthenticated local connections, not another process with access to your user files. One controller is supported; no HTTP/public/Steam network endpoint is added.

Only offered `menu.*`, `run.*`, and event-reading actions enter the existing Java validation/normal game-input path. Output queues have both count and memory limits and disconnect slow peers. Disconnect/stale observations suppress actions. The bridge does not expose reflection, filesystem writes, arbitrary shell commands, or save editing.

MCP mode does not maintain `latest-state.json` or watch command inboxes. A diagnostic JSONL records changed states/receipts only, capped at 64 MiB per launch. Reaching the cap or a later disk failure stops recording with one diagnostic but does not stop live control. No old records are deleted. Retention cleanup remains manual; initial recording-directory creation must succeed. Legacy file-control modes remain available for existing tests.

## Verification

`npm test` inside this directory builds and tests focused observations, state/timeout guards, and real MCP SDK stdio calls against a synthetic loopback backend. `prepare-mcp-test.ps1` also runs real Java loopback/auth/queue tests and all prior game regression checks. These do not substitute for actual gameplay tests.

`evaluation/evaluation.xml` contains ten independent read-only tasks against `evaluation/server.mjs` (a frozen synthetic backend that rejects every mutation). An evaluation host may launch `node <absolute>/evaluation/server.mjs` as its stdio MCP command; this never attaches to your game. The test suite verifies all ten expected answers through real tool calls. A separate model-based evaluation is not implied. Temporary fixture directories contain synthetic data only and are not auto-deleted.

For manual actual-game verification (close the host controller first):

```powershell
node .\mcp-server\scripts\live-client.mjs
```

Enter `{"name":"sts_get_state","arguments":{"wait_ms":8000}}`. Each further JSON line is exactly one MCP request. `{"benchmark":true}` performs 50 read-only cached-state calls and measures response sizes; it makes no gameplay decision. Never run this client concurrently with another controller. See `docs/MCP-LIVE-VERIFICATION.md` for measured scope and remaining limitations.
