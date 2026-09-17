# Hermit heart attempt implementation

Execute inline in the current fork checkout; no workers. Goal: continue the
current ascension-zero Hermit test run to the Heart, or Act 3 if locked. Stop
at first death. No rerolls, save edits, artificial unlocks or upstream changes.

- [x] Verify pinned runtime, submission boundary and final-act availability.
- [ ] Hand/grid selection with descriptions, constraints, deselect and confirmation.
- [ ] Potion use/discard and full-slot reward handling.
- [ ] Shop entry, purchases, purge and exit; campfire options.
- [ ] Relic/potion/key/chest/boss rewards and skip/proceed.
- [ ] Boss entry, act transitions, final-act entry and terminal observation.
- [ ] Full preparation regression before launching changed artifacts.
- [ ] Continue the same run using only public observations; document each act.
- [ ] Report victory/death and verified versus unverified features; fork-only commits.

Current checkpoint: Act 1 floor 2 map, HP 63/75, gold 188; runtime
local-test-20260908-193529-83d00d0f. Prior reward/event checkpoints are in the
implementation ledger. Each missing feature requires a failing check before
implementation and live validation separately from bytecode/binding checks.

Read-only diagnostic: saved `is_final_act_on=false`; the installed load path
copies this flag into Settings.isFinalActAvailable. Follow the approved Act 3
fallback without modifying unlocks. Hand selection and standard reward type
extensions passed the complete preparation suite; their live checks remain open.
