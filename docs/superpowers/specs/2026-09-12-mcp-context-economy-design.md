# State-scoped MCP context economy

User approved: current-state relevance, a small table of contents plus short retrievable fragments, and measured end-to-end token economy. Implement in the existing workspace without commits, gameplay, save edits or Java/runtime changes.

## Contract

- Default decision: session/state IDs, readiness/connection, essential player and current decision facts, offered action summaries, and a dynamic `toc` of `{ref,title,count}` entries. No all-section catalog, full observation, arbitrary extension dump or unrelated screen controls.
- Current combat exposes hand/enemies/piles and combat mechanics. Map details exist only at the map decision. Rest/shop/reward/selection controls use their current-screen or offered-action gates. Deck and persistent resources remain relevant run context. Unknown top-level extension bodies are not automatically published; a short unsupported-information marker prevents false completeness claims.
- Each root/subpath is a state-bound reference. Context reads return shallow field fragments or paged directories rather than recursive dumps. Longer strings have resumable character offsets. Batch up to eight references per call; bound each response. Preserve exact source text across chunks, incomplete flags, and explicit continuation. No retrieval of non-advertised roots or prototype properties.
- `known_view` is an opt-in conditional poll. Return unchanged only for the same digest including state identity, ready status and pending/connection semantics, excluding incidental age. Omitting it always returns a recoverable full decision summary. Action receipts still accompany the next decision; no automatic actions/retries.
- Keep six MCP tools with short static descriptions. Move scenario-specific instructions into small, relevant rule fragments. Keep universal one-action, stale-state and incomplete-information safeguards in the tool descriptions.
- Existing full-view functions can remain as local diagnostic/baseline APIs, but are not a bypass on the exposed MCP context route. No unbounded `full` section on that route.

## Measurement and limits

Capture the pre-change projection and tool catalog. Compare deterministic combat, event, map, shop and collection read workflows, counting request payloads, tool replies, conditional polls and shared tool definitions with a real local tokenizer. Verify the same required facts remain reachable. Report single-representation and duplicated-wire bounds separately; host-specific model injection/billing and model reasoning tokens are not measurable from MCP alone. Do not call synthetic scripted checks live AI/gameplay acceptance. Test Korean and English text and no provider/API spend.

MCP response budgets do not change upstream whole-state transport or Java capture cost. Context fragments reflect the current immutable snapshot, not saves or hidden state. No heuristic inference of missing card effects.
