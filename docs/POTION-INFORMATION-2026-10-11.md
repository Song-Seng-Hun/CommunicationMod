# Potion information repair

The reported episode is uncertain: an agent reportedly called two potions full or said an unused potion was used. The exact Gemini utterance has not been established. Its configured server path was verified during release. This repair addresses a demonstrated ambiguity, not a claim to have reproduced those statements.

## Evidence and behavior

- Recording `target/local-test-20260917-232037-466affe1/recordings/a9998fa2-c58a-4f4d-966b-3e89b4d6c0b6/observations.jsonl`: all 878 lines inspected; 802 potion states contain Ancient Potion, SpeedPotion, and the native Potion Slot placeholder. There was no inventory identity transition in these states. This log does not establish the reported use incident.
- Before: default MCP decision omitted potions; player/potions reported count/total 3 without identifying that these are slots, including an empty placeholder.
- After: default decisions (also returned by sts_act) expose player.potion_inventory with capacity, held_count, empty_slots, full, and at most six named slot identities with exact detail refs. Directories explicitly identify slot counts, and potion rows carry empty. Native slot order and refs are preserved.
- Exact pinned native ID `Potion Slot` identifies an empty slot. Usability, discardability, reward eligibility, names and localized descriptions never determine occupancy. Two potions can legitimately fill a two-slot inventory. Sozu can block acquisition despite a free slot. Absent inventory is not zero; unidentified rows produce null counts/full with information_complete false.
- Inventory is a snapshot, not usage history. An applied input receipt does not establish consumption; current slot IDs remain available after the action. A slot becoming empty alone also does not identify whether it was used or discarded. No native action handling, reward policy, save or game runtime was changed.

## Verification

- Regression first: all five focused inventory tests failed against the old projection, then passed after repair. Cases cover 2/3 versus 2/2, empty/zero/missing/unknown, Sozu and can_use false, index stability, pagination, bounded summaries and changed view identities.
- Existing real stdio test extended: applied receipt plus unchanged native inventory still returns the same two potion IDs. A later native empty slot appears only after refreshing the stale pin. This uses a synthetic backend, not live gameplay.
- Targeted projection/identity tests: 18 passed; extended stdio test: 1 passed. Independent exact-diff review found no actionable blocker. Required package workflow passed MCP 214/214, launcher contract checks and isolated installer scenarios; log `target/potion-package.log`.
- Installed version `0.1.0+codex.20261011120106.67b0aa12`: 3,686 installed files match the package, module fingerprints and all six tool registrations verified. Installed code replays the recorded 2/3 example correctly. All 557 baseline source files were preserved except four intended modifications, with two intended new files added.
- Read-only runtime status was stopped; no game was launched, controlled or interrupted. Gemini configuration `C:/Users/meleb/.gemini/config/mcp_config.json` points communicationmod directly at `E:/dev/gamemcp/sts/mcp-server/dist/index.js`, which this release rebuilt. Its existing matching process PID 3812 started on October 9; reconnecting CommunicationMod MCP is required to load the new code. The process and Gemini settings were left untouched. Adoption and live gameplay remain unverified.

Local evidence: `C:/Users/meleb/OneDrive/문서/ChatGPT/stsmod/POTION-TASKS.md`, `potion-before-after.json`, `potion-recording-evidence.json`, `potion-installed-verification.json`, `potion-source-preservation.json`, `potion-gemini-route.json`. The specific unused-potion narration remains inconclusive; this work verifies inventory delivery, not future model narration or live native consumption.
