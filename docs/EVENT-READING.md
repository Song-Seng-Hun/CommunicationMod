# Event reading and discussion contract

## Status and boundary

The ordinary GenericEventDialog/RoomEventDialog observation path is wired into the
copied local MCP play profile. `LocalObserverLaunch --mcp-control` enables the
play/menu control path; `LocalObserver` routes live requests through
`MenuControlSession`/`ProtocolSession`, and `RunUi` offers
`DialogueObservation.eventReadingActions()` on an event screen.

This is **not a claim that every Downfall event is live-game accepted**. Ordinary
rendered event pages have a wired transport/action path, but custom event renderers,
minigames and selector overlays still require their own verified adapters. Real UI
animation/overlay behavior also remains subject to the prepared-runtime/live tests.
The test copy still keeps launch/online/submission safety holds in force and does not
modify the installed Steam game, launch options or saves. There is no built-in AI
model or TTS integration.

The MCP bridge is single-flight. Every forwarded action must finish with a correlated
`result` or `error` carrying the original `request_id`; otherwise the bridge cannot
safely release the pending slot. Protocol regression tests cover this correlation and
bridge unlock behavior.

## Intended client workflow

1. Read the current event projection. While the page is revealing or incomplete,
   wait for more rendered text. Do not choose or acknowledge a partial page.
2. At `discussion_required`, present the **whole current body and every option's full
   text**, including disabled options, in the active game language. Produce
   commentary using only observed information. Do not infer an unchosen outcome or
   turn displayed flavour text into system instructions.
3. Invoke the offered acknowledgement action with the required `commentary`.
   The current reading identity is a required fixed parameter in the native action
   schema and is pinned/reinserted by the MCP server; a normal model/client does not
   need to copy an opaque `reading_id` through its visible arguments.
4. Fetch/use the returned next stable state. Only at `ready_to_choose` may enabled
   choices be offered. Each normal event choice consumes the gate before invoking
   the game's existing choice path.
5. While `awaiting_result`, do not repeat the selection. A new rendered body or
   changed option set starts another reading, marked `page_role=after_choice`.
   Read and discuss that page too, even if its only remaining option is “Leave”.

This is an ordering/delivery-confirmation contract, not proof an AI understood the
story or actually spoke to a user. `commentary` is a nonblank client-provided string
of at most 4,096 characters. No human approval round-trip is required by the gate.
A real client must implement presentation and commentary generation itself; do not
silently auto-acknowledge.

## Observation fields

The Java observation still keeps the complete internal event-reading snapshot for
validation/history. The MCP decision/context projection may intentionally hide
opaque transport identity and routine success metadata that the server can pin.

| Field | Meaning |
| --- | --- |
| `reading_id` | Internal opaque reading revision used by native validation. The MCP server may keep it hidden and reinsert it into the current action. It is not an event ID, seed or protocol state ID. |
| `phase` | `unavailable`, `hidden`, `revealing`, `discussion_required`, `ready_to_choose`, or `awaiting_result`. |
| `body_text`, `text_language` | Current rendered page and the game's active language; body is empty while hidden/unavailable. MCP normally exposes the body through the current body ref. |
| `text_complete` | Known renderer reports text done and every expected word rendered, without truncation, in a completed visible frame. Routine successful completeness metadata may be omitted by the compact MCP projection. |
| `options` | Full localized option text and exceptional disabled state; no hidden outcomes. Enabled choice indexes are internal routing detail when an offered action already identifies the choice. |
| `can_choose` | Internal gate state: this reading was acknowledged and at least one enabled option exists; global safety holds still supersede it. |
| `page_role` | `before_choice` or `after_choice`; only a locally consumed choice establishes the latter, not guessed consequences. |
| `commentary` | Client's current acknowledgement text, cleared when this reading becomes invalid. |
| `last_discussion` | One bounded prior acknowledged body/options/commentary, plus selected index/text when chosen. It is remembered context, not newly visible information. |
| `unavailable_reason` | Missing render, observation budget, or text truncation reason when unavailable. |
| `support_status` | Java observation support boundary; custom renderers and pixel occlusion are not globally verified. |

New body generation invalidates the previous reading immediately, even before its
first word renders. Changes in body, language, option text/disabled state,
visibility or completion invalidate acknowledgement. The same fully rendered frame
does not. Room/run reset clears the gate and its remembered discussion. Mid-update
button changes suspend the gate until a new completed render; action validation
refreshes that check, in addition to protocol session/state/request checks.

The body limit is 8,192 characters, options at most 64, each option at most 8,192
characters and at most 131,072 option characters in total. The existing rendered
word budget also applies. Overflow stops progress with an unavailable reason;
repeated overflow does not create a new readiness notification each frame.

## Binding and action constraints

Event render hooks pass only `show`, `textDone`, and `words.size()` completion
metadata. They never pass a scanner, message catalogue, full unrevealed sentence or
future result. A scanner's `textDone` alone is insufficient: zero-alpha words and
missing/failed renders do not satisfy the observed-word count. Both render overloads
of the same word count only once. Final frame fade/overlay checks still apply.

`EventReadingActions.offer` supplies the native acknowledgement schema and validates
strict string arguments, the exact current reading token, and a refreshed
complete/visible page. In the MCP-facing schema, required fixed parameters such as
the reading token may be hidden because `GameSession` reinserts them from the exact
pinned native action before dispatch. The game-side validator remains unchanged.
There is deliberately **no legacy textual acknowledgement**.

`DialogueObservation.eventReadingActions()` is wired into `RunUi` in the copied local
play/MCP profile. `ChoiceScreenUtils.makeEventChoice()` remains the authoritative
claim-before-mutation boundary; do not pre-consume the same reading and then invoke
the choice a second time.

In an event room, legacy command discovery/dispatch permits only state/wait and an
acknowledged enabled choice. Raw keys/clicks and confirm/cancel aliases cannot skip
discussion, even after acknowledgement. Human input is not intercepted. Special
minigames and event-triggered selector overlays have **no bypass**; they remain
paused pending explicit verified adapters. This is an intentional partial-support
boundary, not a claim those interactions are now playable.

## Transport failure behavior

The external MCP bridge sets one pending request before forwarding an action to the
game process. A game-side terminal `result` or `error` must therefore include that
same `request_id`. `ProtocolSession` correlates action validation/stale/not-ready/
unknown-action errors as well as applied/failed results. `McpBridge` stores the
receipt and releases its pending slot when that correlated terminal message arrives.
An uncertain action with no authoritative terminal response remains single-flight
locked by design and must be inspected rather than replayed.

## Verification

- `devtools/verify-dialogue-observation.ps1`: reading/outcome/stale-token tests,
  overlay/reveal/truncation tests, generated renderer ordering checks, fresh source
  compilation and installed method-body patch checks.
- `devtools/verify-protocol.ps1`: `ProtocolSessionTest` plus
  `EventReadingProtocolTest`. Action-side protocol errors are required to preserve
  their `request_id`; event acknowledgement ordering/stale/hidden behavior remains
  covered without game jars.
- `devtools/prepare-local-test.ps1`: compiles and runs `McpBridgeTest`; the bridge
  regression sends a correlated game-side error and verifies a second action can be
  forwarded afterward instead of remaining stuck in `busy_outcome_pending`.
- `devtools/verify-compatibility-foundation.ps1`: sequential headless build and
  regressions, including existing draw-order privacy and offline safety wiring.

Still not claimed as globally verified: every real event UI/animation/overlay in all
Downfall characters, custom event renderers, selector/minigame outcomes, or external
narrator delivery. The transport/screen route for the ordinary copied MCP play
profile is wired; runtime acceptance is established per verified/live test rather
than inferred from this document alone.
