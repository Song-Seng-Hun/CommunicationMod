# Local play connection continuation

> Execute inline with executing-plans and TDD in the existing user fork; no workers, upstream writes or VM.

**Goal:** Continue the approved compatibility plan from Hermit selection into a real run, connecting only audited visible actions and verifying each boundary in the copied offline test profile.

**Architecture:** Explicit `--play-control`/`-PlayControl`, separate from passive/menu-only defaults. Keep legacy AutomationSafety blocked. Add a run UI adapter to the same state-versioned session/JSONL transport, reuse privacy filtering and the full-hand decision gate. Add only the map UI hook needed by its existing game input path. Unknown screens and incomplete event text stop actions rather than bypassing reading gates.

- [x] Failing installed-bytecode test for play adapter and real Embark handler; implement opt-in launch/transport plus menu Embark with visible/enabled button checks.
- [x] Add filtered run observations; bind read/discussion acknowledgements and standard event options, map nodes, fully settled combat cards/end turn. Revalidate captured targets and consume combat decision before queuing a card. Do not expose unsupported special selectors as supported.
- [x] Run session/privacy/transport and full copied-runtime regression, preserving prior test settings. Launch one test game at a time and follow the actual observed action IDs.
- [x] Validate start → first map node → first combat where supported. First combat won at state 385; Neow absent in this fresh profile and explicitly unverified. Diagnose runtime mismatches with recorded evidence; never call a run cleared without winning the run.
- [x] Record implementation/live boundaries and review locally. Publication must use only Song-Seng-Hun/CommunicationMod; the resulting Git commit/push is the publication evidence.

Runtime findings: render_frame is diagnostic, not a changing decision; regression failed before excluding only that key. First local profile starts at the map without Neow, so Neow is not validated. First combat is interrupted by a paginated FTUE screen, not the ordinary FtueTip dialog. Added an explicit current-page binding (no future text, no illustration claim) after reproducing the missing handler; do not remove the whole-hand gate to progress.
