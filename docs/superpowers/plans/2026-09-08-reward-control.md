# Local reward control implementation plan

> Execute inline with executing-plans and test-driven-development. Keep the existing user-requested checkout, no workers or upstream publication.

**Goal:** Continue the saved Hermit test run through standard gold/card rewards, card choice or skip, and return to the map.

**Architecture:** Add RewardUi beside RunUi. Reuse the v2 stable-state guard and public card text; invoke ordinary game input handlers. A scoped hover hook exists only during the original card-selection method, cleared in finally. Only opt-in PlayControl can resume a saved test run. Keep special reward/card modes unsupported.

**Tech Stack:** Java 8 target, Maven/Javassist, PowerShell, pinned offline copied Downfall runtime.

- [x] Run `RewardUiBindingTest` against existing target/classes: expected missing scoped adapter failure.
- [x] Add `RewardUi.capture(view,status)` / `actions()` and dispatch from RunUi for COMBAT_REWARD/CARD_REWARD outside combat. Ordinary GOLD/CARD rewards only; preserve labels, disallow pending/ignored items, recheck object identity before choosing. Leave custom types visibly unsupported.
- [x] Bind scoped `RewardUi.hover(card)` after updateHoverLogic in cardSelectUpdate. Execute the original cardSelectUpdate with one selected card; no direct acquisition/currency calls. Reject discovery/draft/codex/choose-one/voting/touchscreen modes; expose skip only when its actual button is enabled and visible. Restrict proceed to ordinary MonsterRoom, not boss/event progression.
- [x] Add opt-in RESUME_GAME to MenuUi; preserve menu-only default. Extend preparation with binding and injected-hook tests, run complete preparation regression.
- [x] Close only own test JVM, copy its preferences/saves without altering the prior copy, launch new PlayControl runtime and resume. Read each actual state before action. Verify currency/deck effects, card descriptions, return-to-map and unchanged passive/whole-hand invariants. Explicitly record any skip/other environment not live-tested.
- [x] Update LOCAL-PLAY-CONTROL and implementation ledger; review only related sources/tests/docs. Publication is tracked by the fork branch's Git history.

Live result: resume -> 14 gold -> Quickdraw selected -> map. Skip was offered but
not executed; special rewards, full runs and base/workshop environments remain
unverified. See the implementation ledger for recording and state evidence.
