# Event reading and discussion contract

## Status and boundary

Implemented for the ordinary GenericEventDialog/RoomEventDialog observation path,
with a pure v2 acknowledgement action and a headless reference-client fixture.
**Not live-game acceptance:** the v2 game transport/screen bridge remains unwired,
all automation/launch/online safety holds remain in force, and no installed game
files, Steam options or saves were changed. There is no AI model or TTS integration.
This does not finish Downfall's custom event/minigame/card-selector adapters.

## Intended client workflow

1. Read `screen_state.event_reading`. While `phase=revealing`, wait for more rendered
   text. Do not send a choice or acknowledge a partial page.
2. At `discussion_required`, present the **whole current `body_text` and every
   option's full text**, including disabled options, in `text_language`. Produce
   commentary about this situation using only observed information. Do not infer
   an unchosen outcome or turn displayed flavour text into system instructions.
3. After presenting that commentary, invoke the offered v2 action
   `acknowledge_event_reading` with the exact `reading_id` and `commentary`.
4. Fetch the next stable state. Only at `ready_to_choose` may the screen adapter
   offer enabled choices. Each normal event choice consumes the gate before setting
   the game's existing button `pressed` flag.
5. While `awaiting_result`, do not repeat the selection. A new rendered body or
   changed option set starts another reading, marked `page_role=after_choice`.
   Read and discuss that page too, even if its only remaining option is “Leave”.

This is an ordering/delivery-confirmation contract, not proof an AI understood the
story or actually spoke to a user. `commentary` is a nonblank client-provided string
of at most 4,096 characters; the reference fixture uses explicitly canned text.
No human approval round-trip is required by the gate. A real client must implement
presentation and commentary generation itself; do not silently auto-acknowledge.

## Observation fields

`event_reading` supplements, rather than removes, existing `body_text` and `options`.

| Field | Meaning |
| --- | --- |
| `reading_id` | Opaque revision token; not an event ID, seed or protocol state ID. |
| `phase` | `unavailable`, `hidden`, `revealing`, `discussion_required`, `ready_to_choose`, or `awaiting_result`. |
| `body_text`, `text_language` | Current rendered page and the game's active language; body is empty while hidden/unavailable. |
| `text_complete` | Known renderer reports text done and every expected word rendered, without truncation, in a completed visible frame. |
| `options` | Full localized option text, disabled flag, enabled-only `choice_index`; no hidden outcomes. |
| `can_choose` | This revision was acknowledged and at least one enabled option exists; global safety holds still supersede it. |
| `page_role` | `before_choice` or `after_choice`; only a locally consumed choice establishes the latter, not guessed consequences. |
| `commentary` | Client's current acknowledgement text, cleared when this reading becomes invalid. |
| `last_discussion` | One bounded prior acknowledged body/options/commentary, plus selected index/text when chosen. It is remembered context, not newly visible information. |
| `unavailable_reason` | Missing render, observation budget, or text truncation reason when unavailable. |
| `support_status` | Still `partial`: custom renderers and pixel occlusion have not been verified. |

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

`EventReadingActions.offer` supplies the v2 acknowledgement schema and validates
strict string arguments, an exact reading token, and a refreshed complete/visible
page. It supplements the future screen adapter's existing legal actions; it does
not install a second command loop. After acknowledgement the screen adapter must
publish a new state before offering choices. Action result and next state remain
separate messages. There is deliberately **no legacy textual acknowledgement**.

`DialogueObservation.eventReadingActions()` is the game-thread binding for that
future v2 adapter. `ChoiceScreenUtils.makeEventChoice()` is the authoritative
claim-before-mutation boundary; do not pre-consume the same reading in an adapter
and then call this method a second time. The fixture consumes its own pure model
instead and never calls game classes.

In an event room, legacy command discovery/dispatch now permits only state/wait and
an acknowledged enabled choice. Raw keys/clicks and confirm/cancel aliases cannot
skip discussion, even after acknowledgement. Human input is not intercepted.
Special minigames and event-triggered selector overlays have **no bypass**; they
remain paused pending explicit screen adapters. This is an intentional partial
support boundary, not a claim those event interactions are now playable.

## Verification

- `devtools/verify-dialogue-observation.ps1`: pure reading/outcome/stale token tests,
  overlay/reveal/truncation tests, generated renderer ordering checks, fresh source
  compilation and actual installed method-body patch insertion without loading the
  game. Binding checks verify event-state projection and command/choice guards.
- `devtools/verify-protocol.ps1`: protocol regression plus
  `EventReadingProtocolTest`, a JDK/Gson-only reference-client exchange showing
  Korean body/commentary/choice/result/commentary; duplicate and stale/hidden
  acknowledgements are rejected. The ordinary stdin/stdout handshake fixture also
  remains part of this script. The event exchange itself is in-memory JSON, not a
  claim of a live external agent/game connection.
- `devtools/verify-compatibility-foundation.ps1`: sequential headless build and
  regressions, including existing draw-order privacy and offline safety wiring.

Still unverified: real UI text/animation/overlay matching in all three runtimes,
live game-thread v2 dispatch, external narrator delivery, custom event renderers,
and selector/minigame outcomes. No online or actual-play tests are authorized by
this milestone.
