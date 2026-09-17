# Render-observed dialogue and situation

## Approved design and scope

Extend player-visible text observations to Neow, enemy/player speech, merchant
speech and ordinary event bodies. Capture only words which traversed the normal
word-rendering path with positive finite alpha and scale. Do not capture complete
constructor messages, future action queues, message catalogues or text scanners.
Keep unknown speakers unknown instead of matching text, positions or future actions.

The game-state converter now adds `narrative` and obtains event `body_text` from
rendered dialog words instead of `UpdateBodyTextPatch.bodyText`. The latter legacy
cache is no longer read by the converter. `body_text_source=rendered_dialog_words`
identifies the changed semantics: an initial/covered event can have empty body text,
and a typing animation can expose a partial body. Options still use the existing
option converter; this milestone is not a complete audit of all legacy state fields.

## Payload

`narrative` contains:

- `session_id`: local observation-history UUID, reset at start-over or player-object
  replacement (new/resumed run). It is not a game seed or a persisted save identifier.
- `revision`: increases on observed text/visibility changes; `render_frame` identifies
  the last completed observation frame, not the protocol-v2 state ID.
- `entries`: oldest-to-newest bounded recent utterances. Each has a monotonic `id`,
  `speaker_type`, nullable `speaker_name`, `text_language`, first-observation
  `context`, `text`, `visible_text`, `currently_displayed`, and `text_truncated`.
- `situation`: current public room type/phase, screen, floor, act, game's turn counter,
  and a local room-visit counter. The turn counter can retain its last value outside
  combat; room phase identifies whether combat is active. These are facts, not an
  inferred explanation of why an enemy acted or what will happen next.
- `dropped_entries`: history/frame overflow count, not a complete transcript promise.
- `support_status=partial`, `visibility_basis`, and `unavailable_reasons` explicitly
  retain unverified custom rendering and pixel occlusion limitations.

`text` is the longest rendered snapshot so far for that utterance, preserving words
when the tail of a bubble fades. `visible_text` is the latest visible snapshot, or
empty after disappearance. This is not a reconstruction of words that never drew.
The journal does not promise that the full sentence has finished revealing.
Ordinary event pages now additionally expose the completion/discussion gate in
[`EVENT-READING.md`](EVENT-READING.md). Normal rerenders
update the same ID; another bubble saying the identical sentence receives a new ID.
Event body updates receive new observation identities without inspecting new text.

The selected language's already-localized rendered words are copied. Word line
numbers preserve visible line breaks. Word-based rendering uses spaces, while
character-break rendering joins characters. This speech/dialog implementation does
not change the separately unsupported CN *card-description cache* encoding.

## Binding and privacy

- `TalkAction.update` supplies executed speaker identity only; it never supplies its
  message. `NeowEvent.talk` and `ShopScreen.createSpeech` similarly supply origin
  labels. A SpeechTextEffect constructor stores only origin metadata, never text.
- `SpeechTextEffect.render`, `GenericEventDialog.render`, and `RoomEventDialog.render`
  establish observation groups. Both SpeechWord/DialogWord render overloads report
  their already-rendered `word`, `line`, `color.a`, and `scale` afterward.
- Capture publishes only after normal frame completion. An original rendering
  exception remains an original exception; partial pending observations are discarded
  at the next frame. Observer RuntimeException/LinkageError is contained and discards
  the pending frame, with one local diagnostic stack per run; VM-fatal errors are not
  swallowed. Origin/render context cleanup executes in finally blocks.
- Known overlay protection permits only ordinary NONE screens without a raised
  screen, or SHOP, with no popup and zero global/dungeon fade alpha. Unknown screens,
  map/settings/card selectors, popup previews, and fades suppress new words. Final
  frame visibility is checked again to discard pending text behind a final overlay.
- Custom overlays not represented by these flags, clipping/occlusion by other draw
  calls, and custom text renderers remain unverified. Positive alpha alone is not a
  screenshot proof. The support status must not be promoted to full based on static
  or fixture checks. Some otherwise visible dialogue is deliberately withheld on
  unrecognized screens.
- Unrecognized speech origins use `speaker_type=unknown`, `speaker_name=null`.
  Merchant uses the stable role `merchant` and no invented localized name. The Neow
  origin uses the game's localized event NAME; actor origin uses the executing
  creature's name, never the next queued action. Direct/custom Downfall speech that
  uses the common text renderer is collected but can lack a known speaker.

The published frame reflects the latest completed render. A situation snapshot made
during the next update may already show a new room/screen: use each entry's context
for its occurrence and do not treat remembered text as a new utterance. Event-body
projection additionally uses the current body-generation token and rejects a changed
room. Thus new options cannot accidentally receive the previous page's text before
the new page first renders. Room identity refresh occurs at render/body entry as
well as frame start, since a room can change during that frame's update.

## Bounds and verification

History retains at most 64 utterances of 8,192 characters each. A frame accepts up to
64 utterance groups. Word gathering accepts up to 512 identities and 8,193 characters
(one extra character signals text truncation). The binding's 256-owner weak registry
can evict metadata, reported explicitly. Capture budgets can omit text; clients must
check truncation/unsupported reasons. Internal getter/render execution time is not
controlled by these limits. Dialogue-originated state notification is capped at one
per 250 ms, with changes retained between notifications; it never executes an action.
The separate event-reading gate now notifies readiness boundaries and restricts
event choices. History is local in-memory data, not written to saves.

`devtools/verify-dialogue-observation.ps1` runs:

1. JDK-only journal tests: completed/aborted/covered frames, partial reveal, duplicate
   frames vs new utterances, disappearance, detached snapshots, retention limits,
   unknown speakers, notification throttling and reset.
2. JDK-only word tests: alpha/scale exclusion, duplicate word identity, Korean/English
   word spacing, CJK joining, line breaks, bounds, known overlays and unknown screens.
3. Fresh full-source compilation against installed JAR definitions, then Javassist
   insertion into actual methods without defining/initializing game classes. Checks
   reject menu-null-unsafe room lookup and use of the unrevealed legacy body cache.
4. A generated JDK-only renderer fixture in a parentless loader verifies normal
   capture ordering, finally cleanup, preservation of original exceptions and
   containment of observer failures. It refuses non-JDK/non-fixture classes.

The script is included in `verify-compatibility-foundation.ps1`. These checks do not
prove real-game patch registration/loading or a screenshot match. Full three-runtime
verification, custom Downfall dialogue screens, clipping/overlay cases, intro and
resume timing remain open acceptance work. No game launch, Steam settings/save
changes, online submission or safety-hold removal accompanies this implementation.
Changes are committed/pushed solely to the user fork, without upstream contribution.
