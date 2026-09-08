# Combat decision readiness

Update: `LOCAL-OBSERVER-TEST.md` now provides a non-VM human-play observation profile.
The original launch/action holds mentioned below apply to legacy entry points;
the new passive transport is wired for testing. Actual gameplay acceptance and
v2 mutation dispatch remain unfinished.

## Approved design / implementation plan

The user approved waiting for the entire hand, rechecking at dispatch, and reporting
the stable hand's positions/Dead On indication. Apply this to every character, not
just Hermit. Use completed frames and actual pending-work flags, not a fixed delay.
Keep global launch/automation/online holds, fork-only operation and sequential tests.

1. Add a pure decision gate and RED tests for partial draws, two identical completed
   frames, stale tokens, changes between observation and dispatch, duplicate actions,
   and forced selections while other actions are paused. Implement and rerun GREEN.
2. Bind an allowlisted sample to the actual runtime: action/current/turn-start/card/
   monster queues, turn flags, transitions, hand identity/order/values and settled
   card positions. Patch frame completion with contained observer failures. Ordinary
   combat hands remain withheld until ready; selection screen cards stay available.
3. Offer versioned play/end/selection actions with a decision token and card UUID.
   Consume before mutation and revalidate live. Unversioned combat mutations (also
   raw clicks/keys/potions) are rejected, not silently queued for later execution.
4. Compile all sources and inspect actual patch bindings without game initialization;
   run the full headless regression script. Review, commit and push only the fork.

The stable fingerprint is local validation data, never a dump of hidden cards, RNG
or queued future effects. Queues supply only busy/idle status. V2's session/state/
request guards remain an additional layer. A live v2 transport bridge and actual
offline play are still separate, unverified acceptance work.

## Implemented contract

`combat_decision` is added at the communication-envelope level. It contains
`ready`, `hand_complete`, `mode` (`play`, `selection`, `none`), `decision_id`,
`settled_frames`, `reason`, and an explicitly partial `support_status`.
Readiness means two matching completed game render cycles and a currently matching
live sample; repeated polling does not count as completed frames. Pending work,
turn changes, manual dragging, popup/fade, unsupported screens, an existing
state-update block, or observation failure remove eligibility immediately. Normal
play also checks the current action, turn-start current action, pre-turn/actions/
card/monster queues, using-card flag and limbo, plus turn/end-turn flags. Queue
contents are never projected. The internal key contains only ordered card identities,
common public values, public powers/energy/stance/monster state and selector state.

`combat_state.hand` is empty with **`hand_complete=false`** while a normal full-hand
decision is unavailable. This is withheld information, not a claim the player has
zero cards. Do not choose a card from an old cached hand. Ordinary card
`is_playable` flags are also false in an unstable combat state. Standard GRID and
HAND_SELECT keep their own `screen_state` card observations; this does not unlock
ordinary card play. The final response is checked again after description/tooltip
getters and loses its ready hand if those getters changed the sampled state.

Stable hand rows retain their actual hand order and UUID, with a zero-based
`hand_index`. Tagged Hermit cards additionally expose `dead_on_position_active`,
using the installed `isDeadOnPos` UI predicate (including its Concentration rule),
not the state-mutating `isDeadOn` latch. This is the current positional/UI indication,
not a prediction of a future combo or all ways of triggering the effect. Reflection
is limited to the known Hermit superclass/method; base-game loading has no symbolic
Hermit dependency. A failed optional binding withholds the decision.

## V2 actions and integration boundary

`CombatActions.offer()` supplies `play:<card-uuid>:<target-index>`, `end_turn`, and
standard selector `select:<index>`, `confirm_selection`, `cancel_selection` actions
when permitted. Every action requires the exact `decision_id` in its arguments,
as well as ProtocolSession's session/state/request IDs. A play action fixes the
specific card object/UUID and target when offered, not a later hand slot. The
dispatcher rechecks live readiness, the card/target/choice legality, and readiness
again, then consumes the token **before** modifying the game's existing queue/UI.
Failure does not authorize replay. Action acceptance and the next stable state are
separate; no command is held for execution after a later draw finishes.

Forced GRID/HAND_SELECT decisions can settle while the combat action queue is paused
waiting for input. Only those selection/confirm/cancel actions are then eligible,
and another completed stable selection snapshot is required for each selection.
Manual controls are not intercepted. Unrecognized selection screens remain blocked.

Legacy combat text commands, including `play`, `end`, `choose`, potion, confirmation
aliases and raw key/click commands, are deliberately denied even when stable: they
cannot identify which snapshot the client used. Only state/wait remain. No legacy
compatibility shim or implicit “use the newest snapshot” fallback was added.

**This is source-level integration, not a running game connection.** The main live
v2 transport/screen bridge is still unwired and the global AutomationSafety hold
remains false. `CombatActions.offer` therefore returns no actions in the held
development build, and its mutation dispatcher checks the same hold first. Stable
observation readiness is not permission to bypass the hold. V2 combat potion actions
and custom selection screens are outside this milestone and remain unavailable.

## Verification and remaining limits

`devtools/verify-combat-readiness.ps1` runs fresh JDK-only gate tests, full source
compilation, actual installed frame-hook insertion and static binding checks. A
parentless-loader fixture executes a copy of the actual mutation dispatcher body
with the real pure gate and only JDK test boundaries. It verifies hold-first order,
rejection during draw, changes during legality validation, consumption before UI
mutation and duplicate refusal. It loads no game/Steam classes. Actual Hermit
predicate bytecode is checked for the audited read-only operations.

The script runs sequentially in `verify-compatibility-foundation.ps1`, alongside the
existing protocol, dialogue, draw privacy, tooltip and safety regressions. No game
launch, install, Steam settings, online submission or save changes are performed.

Still unverified: live patch order and v2 dispatch; rendered hand/screenshot matching
in base/standalone/workshop; custom modifiers that schedule work outside the known
queues; extra custom card/monster state; custom overlays/selectors. The position
tolerances (0.5 coordinate/angle units, 0.005 scale) only check ordinary settled card
layout, not every pixel or render-only text effect. Native/process/save isolation
and full Downfall compatibility remain incomplete. No AI comprehension guarantee
is implied: the mod controls observations and accepted actions, not a client's
private reasoning.

Verification result (2026-09-08): the full headless foundation script completed
successfully, including 30 pure combat assertions, the actual-dispatcher fixture,
installed-bytecode binding checks, and the existing regression suites. This is not
three-runtime gameplay acceptance and did not activate the held development build.
