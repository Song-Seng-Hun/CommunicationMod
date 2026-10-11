# Downfall public mechanic gaps — source handoff

Scope: existing `master` checkout, source and isolated/headless verification only. No installation, game launch, save mutation, commit, push or model evaluation. Backups: `target/downfall-gap-fix-baseline-20260926-091630` and `target/downfall-completion-baseline-20260926-102022`. The two pre-existing modified tests (`agent-context.test.mjs`, `prose.test.mjs`) remain byte-for-byte identical to the backups (SHA256 rechecked).

**Current result (2026-10-03):** the pinned native reward inventory is now connected: 26 exact classes, including 22 newly supported paths. Fresh MCP verification is **201/201**. Older counts below are historical. No installation or live-game acceptance is implied.

## 2026-10-03 native reward coverage repair

Baseline: `target/native-rewards-baseline-20261003` preserves the immediately preceding source, tests, documentation and guidance contract. No staging, commit, installation, game launch or save mutation. Protected pre-existing `agent-context.test.mjs` and `prose.test.mjs` still match this baseline byte-for-byte (SHA256 checked).

`CollectorRewardUi` retains its compatibility name and native input path. It now matches **both exact runtime class and native enum** for all 26 concrete RewardItem descendants in the pinned EvilWithin jar. Unknown subclasses and mismatched enums remain unsupported; pending/ignored/click guards and current reward identity/detail checks remain in force.

| Native family | Added handling |
| --- | --- |
| Guardian | GemReward, GemRewardButRelicRng and GemRewardAllRarities open native CARD_REWARD |
| Automaton | DazingPulse, DecaBeam, DonuBeam, Explode and Spike rewards open native CARD_REWARD |
| Snecko | Colorful card/power/rare/upgraded-uncommon, DiceRollPotion and UpgradedUnknown rewards open native CARD_REWARD |
| Downfall | BossCard, Jax and ThirdSeal open native CARD_REWARD; remove/transform/upgrade open native GRID; BossRelicReward creates its native GRID choices only during claim |
| Hermit | BountyGold displays and pays inherited `goldAmt`, not its unused `amount` field |

Existing EssenceReward, CollectibleCardReward, RareCardReward and SealSealReward behavior is retained. Pre-open observations never inspect candidate arrays or roll RNG. GRID details expose only `selection_operation`, `selection_count:1` and deferred-choice status; a claim is not automatic target selection or confirmation. Bounty amount and collectible price/balance participate in the existing current-detail dispatch check. Native screen/update handlers continue to own reward removal and effects.

**Inventory gap closed:** `devtools/native-rewards.json` is checked against a class-file-only hierarchy scan of the installed jar, not merely our own providers or classes whose names contain `CustomReward`. The first scan caught a previously missed direct RewardItem subclass, BossRelicReward. New, removed or unclassified native descendants fail verification; a changed JAR hash requires explicit review. This inventory is then joined to MCP semantic coverage and correctly typed normal/read/stop fixtures. No new runtime dependency or runtime inventory scan is introduced.

Fresh verification:

- Adapter regression failed on unsupported DazingPulseReward before production changes: `target/native-rewards-red.log`. New MCP tests also first failed on missing native coverage and missing separate deck-selection guidance.
- `target/native-rewards-final-ui.log`: fresh production compilation; **reward 423 assertions**, tutorial 92, wheel 43, evil Match 34, base Match 39; three existing event/reward/input guards and unknown-group rejection passed. Claims run copied installed method bodies with isolated UI dependencies; no game classes are initialized from the installed JAR.
- `target/native-rewards-npm-test.log`: full build and **201/201**, zero failures/cancellations/skips. Six tools/input schemas, 53 capabilities and 159 examples unchanged. Existing SEALCARD deferred-choice assertion remains intact.
- Build budgets remain 2,400 JSON characters per capsule including wrapper and 160 tokens for inline eligibility. Reward normal is **314 o200k tokens**, so it remains lookup-only; optional hook text is 53 tokens. No whole-workflow token/latency improvement claim.
- Contract v12, 110 production source files; fingerprint `efaed15476bffb8619eefb7214882ce5fa16368f9cb66ba97fa3d0b4300b0b07`. `git diff --check` passed.

Independent safety review found no additional operational defect within the reviewed reward scope. Installed bytecode connects `CardCrawlGame` through BaseMod `publishPostUpdate()` to `downfallMod.receivePostUpdate()`; the copied builder preserves that hook. Native completion owns removal/transformation/upgrades/boss-relic acquisition, clears its selection flags and returns the room to COMPLETE. This completion wiring was inspected, **not executed end-to-end** by the new tests.

Two explicit test limits remain: GRID fixtures execute the native claim through screen opening but not the full native selection/completion loop; BossRelicReward uses the base input fixture for its inherited `RewardItem.update()` rather than copying that update body (its `claimReward()` is copied and executed). CustomReward subclasses execute the installed CustomReward update body. Isolated claim/open tests and synthetic MCP workflows are not full live selection-loop, rendering, reconnect or per-character acceptance. Native constructors and unselected future candidates are not invoked by observation. Source repair does not update an installed plugin or game copy.

## 2026-10-03 base-game minigames follow-up

Baseline: `target/base-minigames-baseline-20261003` preserves the immediately preceding source, tests, documentation and guidance contract. Existing user edits were retained; `agent-context.test.mjs` and `prose.test.mjs` still match this baseline byte-for-byte (SHA256 checked). No staging, commit, installation or game execution.

- `RestWheelUi` now allows the exact base `GremlinWheelGame` alongside rest/evil variants. It uses the same acknowledged-intro guard and native scoped press. The sampled outcome is neither inspected nor predicted.
- `EvilMatchUi` now allows exact base `GremlinMatchGame`. Base authorization requires the acknowledged **RULE_EXPLANATION** page; its first INTRO alone does not authorize flips. One native position click, stable public positions, concealed identities, animation/pending guards and stale/replay rejection remain unchanged. Compatibility class names and action IDs are retained.
- Integration testing found that the base Match observation path entered the uninitialized legacy `GremlinMatchGamePatch` cache before the new adapter. Empty-button events under `communicationmod.play_control=true` now bypass legacy mini-game choices. Ordinary dialog buttons and the non-v2 path remain unchanged. The regression executes the copied **actual compiled choice method**, not a replacement implementation.
- MCP semantic fixtures explicitly include base Wheel and Match, reusing existing examples instead of duplicating capabilities. Match guidance distinguishes the rules page and retains a single `sts_act`, conditional detail queries and no automatic second flip/replay. Contract v11 covers the same 110 source files, 53 capabilities and 159 cases; six tool schemas are unchanged.

Fresh evidence:

| Check | Result / log |
| --- | --- |
| Unsupported base mini-games regression | Failed before the allowlist implementation: `target/base-minigames-red.log` |
| Legacy Match cache integration regression | Failed before the guard, then base Match 39 assertions passed: `target/base-minigames-observation-red.log`, `target/base-minigames-observation-green.log` |
| Explicit base MCP coverage regression | Failed before fixtures/catalog edits; focused catalog/synthetic tests then **23/23** passed: `target/base-minigames-guidance-red.log`, `target/base-minigames-guidance-green.log` |
| Fresh production Java compile / native-body UI | Tutorial 92, rewards 37, three wheel variants 43, evil Match 34, base Match 39 assertions; three existing guards and unknown-group rejection: `target/base-minigames-final-ui.log` |
| Dialogue regression | History, rendered words, event acknowledgement and frame/render hook binding passed: `target/base-minigames-dialogue.log` |
| Full MCP build and regression | **199/199**, zero failures/cancellations/skips: `target/base-minigames-npm-test.log` |

Independent Java review identified the legacy-cache blocker and confirmed the regression-backed fix; no remaining actionable finding within the inspected native observation/dispatch scope. The final independent MCP review also found no actionable issue: rule-page acknowledgement, ready/pending checks, concealed-card privacy, one-action/no-replay requirements and execution of both base fixtures were retained. The reviewer inspected source and logs without editing or rerunning tests. `git diff --check` passed. Final source fingerprint: `5faffaa4a666eeb41fa570bede03e08753cb3592380c93590c19f2fe4e357379`.

Build-time budgets remain unchanged: each public capsule including its fragment wrapper is at most 2,400 JSON characters; inline examples require at most 160 o200k tokens. Normal Match is 255 tokens and event choice is 231, both lookup-only. Optional hook text is 53 tokens; no hook was registered. These are static size checks, not an agent-speed or whole-workflow token improvement claim.

Limits: isolated fixtures copy installed native method bodies but do not initialize the game or prove full live graphics/event-loop/reconnect behavior. Unknown subclasses and mid-mini-game reconnect without local acknowledged authorization intentionally fail closed. This closes the two identified **base-game source gaps**, not an exhaustive all-events acceptance audit.

## 2026-10-03 follow-up implementation and verification

Baseline: `target/downfall-match-completion-baseline-20261003` preserves source, tests, guidance contract and documentation before this implementation. The two pre-existing user-edited tests above still match that baseline byte-for-byte (SHA256 checked). No staging, commit, installation, game launch or gameplay.

| Follow-up finding | Implemented boundary |
| --- | --- |
| Random Conjure advertised a next spell | `up_next` exists only when native DeterministicConjure is enabled; random mode still exposes its public spell groups, never a predicted next result |
| Event relic hover preview absent | Exact base/Guardian event buttons expose existing cached public relic tips, with completeness bounds; no renderer, constructor or RNG call |
| Evil wheel and Seal reward missing | Exact evil/rest wheel native paths share acknowledged-intro guards; SEALCARD claims through its native handler, with choice details deferred until CARD_REWARD |
| Evil Match flips absent | New `EvilMatchUi` delegates one current position press to installed `updateMatchGameLogic`; native code owns match resolution, timers, attempt decrement and awards. Authorization requires the acknowledged intro of the same exact event/room. Board/face/attempt/selection/hitbox changes reject stale actions. Positions remain stable after pair removal. Face-down identities are never read for observation |
| Match test could falsely pass Wheel | Explicit test-group dispatch and unknown-group negative check. Match now executes its own native-body fixture; missing adapter first failed as intended |
| MCP discovery/contract incomplete | EVENT-only `match_controls`, aliased actions, no hidden-card child refs, three lookup-only Match examples; semantic coverage includes evil wheel, SEALCARD and optional event relic previews. Reviewed contract v10 has 110 source files, 53 capabilities and 159 cases; six tools/input schemas and existing budgets unchanged |

The Match normal example performs one `sts_act`, reuses a returned fresh state and reads omitted detail via current `sts_get_context` refs. It refreshes state only when absent/stale/transitioning; unknown receipts still require recovery, never replay or automatic second flips. A regression first rejected the old unconditional second state call, then passed after this correction.

Fresh verification logs (all isolated; no game initialization):

- `target/downfall-match-ui-all.log`: tutorial 92, reward 37, wheel 31, **Match 34** assertions; event-reading, reward-policy and native-input guards; unknown dispatcher group rejected.
- `target/downfall-match-resources.log`: production compilation, all fourteen native player types and resource/special-cost/projection/input/binding regressions, including random/deterministic Conjure.
- `target/downfall-match-previews.log`: 3,445 native bytecode assertions plus public projection fixtures, event relic tips, privacy and mutation-negative checks. Pinned EvilWithin SHA256: `148c422dd26e869fbdc9a63f9ab072373c3d791d3e3fcbe8d87b55ae75a29226`.
- `target/downfall-match-dialogue.log` and `target/downfall-match-public-descriptions.log`: fresh dialogue/acknowledgement/render binding and text/tooltip/draw privacy/launcher-refusal passes.
- `target/downfall-match-guidance-red.log`: redundant state-call regression failed; `target/downfall-match-guidance-green.log`: **30/30** focused tests passed after capsule and explicit revision changes.
- Final full MCP result after guidance correction and review metadata update: **198/198 passed**, zero failures/cancellations/skips; `target/downfall-match-npm-test.log`. The full command includes TypeScript build, guidance-contract/capsule-budget validation and all test files; no older count is substituted.

Independent Java review inspected native bytecode, authorization, concealment, stale guards and fixture validity: no actionable blocker found in that scope. Main reviewed MCP visibility/aliases/coverage; guidance review identified and removed the redundant query. Static wiring and copied-handler fixtures do **not** establish full live event-loop/graphics/reconnect acceptance. Mid-Match reconnect without locally acknowledged intro intentionally fails closed; unknown subclasses remain unsupported.

Fresh static economy/format reports remain in `target/context-economy-report.json` and `target/context-format-report.json`. Their historical full-dump/API and same-API JSON/compact comparisons are **not** an incremental benchmark of this patch or a model-speed measurement. Existing capsule wrapper/inline/hook budgets are verified at build time; Match normal is 243 o200k tokens and therefore lookup-only (160-token inline ceiling). The unchanged optional hook text is 53 tokens. Final source fingerprint: `032efc9262ce50505ab363bccf1402f3cc51d9ce08e9e509386672869fca9e07`.

## Implemented / bounded

| Finding | Source behavior | Limit |
| --- | --- | --- |
| Custom rewards rejected | Exact EssenceReward, CollectibleCardReward and RareCardReward adapters; original native input/claim lifecycle; current essence, identity and visible contents rechecked; collectible next-standard-upgrade comparison uses the current decision cache | Unopened rare choices are **not public**: banner only until CARD_REWARD. No acquisition/event effects are predicted. Missing current context or unsupported upgrade remains explicit |
| Downfall tutorial confirmation missing | Ten exact two-page tutorial classes; current localized page/confirm text, transition and stale-page guards | Illustrations unavailable; unknown classes still refused |
| Campfire wheel spin missing | Separate `run.wheel.spin`, offered only after acknowledged intro on the same event/room; native press/update, no repeat | Mid-wheel reconnect without local authorization fails closed |
| Collector monster hover preview missing | Existing public catalog mapping, fifteen native boss/act mappings, localized collectible prefix/descriptor and Exhaust projected on detached maps | No native card factory or modifier execution; existing description/cost/tooltip completeness markers retained |
| Native card previews missing | Existing cardsToPreview, private SplitSpecialist renderer cache, 25 audited Unknown public candidate getters; upgraded variants finalized through existing bounded detached-copy preview queue | Never choiceList (consumes RNG), lastUnknownRoll or a native renderer. At most 256 candidates. Hidden/missing/nested/branch/multi/non-base/unsupported upgrades explicitly incomplete; unavailable upgraded rows omit base stats. Uncaptured catalogs remain labelled base_catalog |
| Oversized card detail dead end | Existing atomic fast path; over-budget cards retain lean header plus child refs and continuation, including routine metadata | Whole-fragment byte budget includes cursor; full facts recovered only via advertised refs/pages |

MCP keeps six tools and existing schemas. `wheel_controls` is current-context gated. Nested card IDs survive control aliasing; action IDs remain aliases. Examples distinguish custom rewards, preview variants and wheel acknowledgement. Integration also found a **pre-existing** missing inventory entry for `run.map.return`; its example now explicitly distinguishes map dismissal from travel. No map gameplay change.

## Safety review

Independent review identified three issues and prompted regression-backed repairs:

1. Already generated RareCardReward choices were incorrectly treated as public. Removed them from pre-open reward observations; native card selection exposes them later.
2. Recursive control sanitization erased nested card IDs. Card payload identities now remain reachable; actual control identities still use aliases.
3. Collectible purchase snapshot lacked upgrade evidence. The first pass explicitly reported it unavailable. The completion pass connects this late adapter to the already-finished current decision's standard upgrade cache; unfinished/changed-owner contexts fail closed. No new upgrade algorithm is introduced.

The reviewer also checked native event acknowledgement/current-room guards and the oversized-card fallback. A separate completion review found no introduced P1/P2 in the gallery/reward integration; it checked lifecycle, visibility, bounded queue/cache and native recapture before reward dispatch. The reviewer explicitly noted that headless copy/constructor fixtures do not prove the purity of every transitive third-party modifier callback. Source-level review is not live-game acceptance.

Independent MCP review found a P2 after the first 189-test green run: long advertised refs plus surrogate-pair text could return an empty page with an unchanged cursor. Regression-first repair now explicitly instructs a single-ref read if a batch cannot fit even one code point. Final wrapper checks cover scalar/array/object paths too. The reviewer rechecked exact single-ref recovery (including null/false/0), and reported no remaining blocker for this defect. Final 191-test integration includes both new regressions; the earlier 189-test log is preserved separately.

## Initial verification record (superseded by completion below)

- TDD: baseline lacked the three UI adapters and preview observers; corresponding tests failed before implementation. The original oversized-card lookup failed discovery tests. Private Split cache, cursor budget, hidden rare choices, unavailable upgrade evidence and nested card IDs have focused regression checks.
- `verify-run-usability.ps1`: fresh Java compilation and all existing resource/input/policy/selection/binding groups passed (`target/run-usability-tests/run-14228b85677d449ca422754cca390c5f`).
- `verify-public-descriptions.ps1`: text/tooltip/draw privacy, launcher refusal and full-source/native binding checks passed.
- `verify-dialogue-observation.ps1`: dialogue history, event acknowledgement, render-hook ordering and actual-method insertion checks passed.
- `verify-native-previews.ps1`: actual installed class-file inspection and graphics-free value fixtures passed (`target/native-preview-tests/run-9cc86c88f2584f8f88f0fd70cc7f6cf0/verification.log`). SHA-pinned EvilWithin, all 25 read-only getters, private Split cache, fifteen boss mappings, modifier chain, production callbacks and mutation-negative controls. Native classes are never loaded/initialized by the bytecode verifier.
- `verify-downfall-ui.ps1`: original installed tutorial/update/claim bodies run with isolated dependencies; unknown/pending/stale/animation guards included. Final run: tutorial 92, reward 27, wheel 16 assertions plus three existing guard groups (`target/downfall-ui-tests/run-300c6c8cea8a4eaa9e34d8bfe34a206b/verification.log`). This does not execute a real CombatRewardScreen/game loop.
- Focused MCP bundle: **49/49 passed**; includes new ten tests, existing agent-context tests, inventory, schema, examples, synthetic normal/read/stop executions and packaging checks.
- Full baseline `npm test`: **169 total, 149 pass, 20 fail** (`target/downfall-gap-baseline-npm.log`).
- Full updated `npm test`: **179 total, 164 pass, 15 fail** (`target/downfall-gap-npm-final.log`). All remaining failing names also fail in the preserved baseline. Five prior failures were repaired (two missing inventory obligations; three old fixture binding failures). **Full regression is not green.** No assertions were disabled; both user-edited tests remain unchanged.

These fifteen failures were subsequently investigated and repaired; they were not all stale tests. Their original failures remain in the logs.

## Completion verification

- `npm test`: fresh build plus **191/191**, zero failures/skips, exit 0, `target/downfall-completion-npm-final.log`. Includes real MCP stdio with synthetic lifecycle/backend only. Reviewed contract `2026-09-26-downfall-public-ui-v9`, 108 source files, 52 capabilities / 156 examples.
- `verify-card-upgrades.ps1`: now freshly compiles production instead of using stale target/classes. All three groups passed in `target/upgrade-preview-tests/run-dc846b564c5c4e6ea6e2ad0272a8ec87`. Tests first failed on missing integration and on false canUpgrade/no-op inference, then passed after implementation. Installed Searing Blow/Strike/Hermit upgrade bodies run with isolated dependencies; original card, repeat-upgrade, context/cache, unavailable-branch and gallery results checked.
- `verify-native-previews.ps1`: 2,951 bytecode assertions plus projection fixtures, `target/native-preview-tests/run-3e2d3a9d783f44a692d40534ef3b6546/verification.log`. Direct catalog projection is factory/RNG-free; upgraded results deliberately delegate to the separately tested standard preview service.
- `verify-downfall-ui.ps1`: tutorial 92, reward 28, wheel 16 assertions plus three existing guard groups, `target/downfall-ui-tests/run-2d1882a7bd6b437eba82adf3330038bb/verification.log`.
- `verify-run-usability.ps1`: fresh full Java compilation and all groups passed, including fourteen native player types, forty audited read-only getter bodies, mixed/hidden resources, rendered costs and actual selector handlers. Log: `target/downfall-completion-run-usability.log`.
- `verify-public-descriptions.ps1` and `verify-dialogue-observation.ps1`: fresh passes, logs `target/downfall-completion-public-descriptions.log` and `target/downfall-completion-dialogue.log`. Include draw privacy, launcher refusal, event acknowledgement and render-hook binding.
- Actual MCP fixes: reject absent transformed roots and encoded action-path bypasses; include continuation metadata in fragment budgets; expose oversized scalar rows via child refs instead of stuck pagination; avoid duplicate page-array reads and huge evidence-table traversal during hand deduplication. New negative tests failed before these repairs.
- Review follow-up: long-ref text never returns a stalled surrogate cursor; all ordinary fragment wrappers fit their batch budget or explicitly request single-ref recovery. Final focused pagination/card/performance run **36/36**, `target/downfall-completion-pagination.log`.
- Stale test/evaluation assumptions corrected: current alias/pinned-identity API, omitted terminal cursor, shallow control directories, deduplicated hand and exact evidence budgets. Required facts remain checked via counted detail reads. The uncertain-action evaluator accepts unchanged only after previously observed pending evidence and ordered receipt recovery; missing evidence, early recovery and replay remain failures. No budget or safety assertion was relaxed to force a pass.

## Current static reports

`target/context-economy-report.json` and `target/context-format-report.json` were regenerated by the passing full suite. The economy report compares the historical full-dump fixture API with the current public API, **not just this completion patch**: five fixed fact-equivalent workflows, requests/replies/catalog and repeated reads total 10,127 → 3,252 o200k tokens; without repeated polls 4,875 → 3,082. The format report compares JSON-only versus automatic compact on the same current requests: 4,455 → 4,345 text/request/catalog tokens, and 5,080 → 4,859 serialized-result/request/catalog tokens. All discovery/pages/details count; no duplicate structured-content savings are invented. These synthetic reports establish contract/cost behavior, not actual model speed, billed usage or per-character gameplay acceptance.

## Narrow static cost check

o200k_base, identical fixed request/state fixtures; response JSON only, not model inference:

| Read | Request tokens | Old response tokens/bytes | New response tokens/bytes |
| --- | ---: | ---: | ---: |
| One small card | 16 | 30 / 97 | 30 / 97 |
| Four large cards (initial fragment only) | 28 | 406 / 1459 | 206 / 659 |

The large-card response now advertises eight child refs instead of duplicating truncated prose. Reading those children costs additional calls/tokens. This comparison is **not equivalent full-workflow savings or agent-speed evidence**. Ordinary small-card decision/response equality was checked. Build-time capsule size/token and hook budget checks still apply; no expanded default inline example body.

## Remaining acceptance boundaries (not source-repair failures)

- Arbitrary external card constructors/modifier callbacks are not proven side-effect-free by the existing standard preview service. Known unsupported upgrade families fail closed; their base values are never advertised as upgraded results.
- No plugin/game installation was changed during this repair; the former test-failure blocker is now resolved, but no live-release claim follows automatically.
- Actual per-character play, graphics, reconnect/resume, native full reward-loop behavior and LLM latency remain unverified.
