# Native character resources and special costs

Scope: the four hash-pinned installed JARs accepted by `BuildLocalObserver`. This is public observation support, not a simulation of card effects or a claim that every character has passed live-play acceptance. Original Steam files, saves and unrelated Java processes are untouched.

API update: the current MCP uses state-scoped `refs` and a dynamic toc, not the older `section` calls below. Native projection/completeness semantics are unchanged. See [MCP context economy](MCP-CONTEXT-ECONOMY.md) for the active retrieval contract; full-projection examples below remain diagnostic/history only.

## Coverage matrix

All fourteen registered player classes are checked against the installed `AbstractPlayer` hierarchy by `NativeMechanicsBindingTest`.

| Character / mixed mechanic | Public observation | Installed source |
| --- | --- | --- |
| Ironclad, Silent, Defect, Watcher | HP, block, energy, powers, stance, orbs; temporary HP and maximum orb slots | Existing converter; StSLib `TempHPField.tempHp`; player `maxOrbs` |
| Gremlins | Active gremlin, Nob form, formation HP, dead versus enslaved | `GremlinCharacter.currentGremlin/mobState/nob`; native `getGremlinHP/isEnslaved`; current player and standby-orb HP during combat |
| Collector | Reserves, essence, collection and combat collection | `NewReserves.reserveCount`, `EssenceSystem.essenceCount`, `CollectorCollection`; combat collection only during combat |
| Awakened One | Public spell groups and counts, next-spell tag, awakening progress/threshold/state | `RenderSpellCardsPost` display gates; `OrbitingSpells.spellCards`; `UP_NEXT`; native progress cap |
| Champ | Current stance text, remaining technique charges, technique/finisher text | Cached stance description; `AbstractChampStance.getRemainingChargeCount`; audited `StanceHelper` text getters |
| Guardian | Stasis card and existing orb countdown; socket capacity, colors and gem type | `StasisOrb.stasisCard/passiveAmount`; `AbstractGuardianCard.socketCount/sockets/thisGemsType` |
| Hexaghost | Five native flame types, charge/trigger counters, current localized descriptions including Inferno | Native flame getters; exact private Inferno localization cache plus side-effect-free formatter |
| Automaton | Visible encode slots up to native capacity, existing function preview, compile-panel text | `FunctionHelper.doStuff/max/held/secretStorage`; existing `CompileDisplayPanel`, with audited compile-text implementations only |
| Slimebound | Puddle form, existing summon descriptions/amounts, upgraded/debuff indicators | `puddleForm`, `SpawnedSlime`; `noRender` suppresses orb details |
| Snecko | Current transformed card's public unknown origin; native `?` cost remains unknown | `UnknownExtraUiPatch.parentCard`; no replacement pools or future rolls |
| Hermit | Existing hand-position Dead On predicate and target-aware playability | `CombatObservation` / `CardPlayObservation`; no independent invented resource |

Encode, flame and spell panels use their native activation conditions and also work on mixed-character runs. Reserves are reported for Collector or whenever the public reserve counter is positive. Each optional binding fails independently; one unavailable panel does not erase the others.

`scope=pinned_native_public_character_panels` and `character_supported` describe this catalog. `panel_bindings_complete` describes native panel access, separately from `information_complete`, which recursively propagates false `*_complete`, unavailable reasons/status, and truncation flags from the projected panels and cards. `audited_panels_complete` requires both; `character_specific_complete` additionally requires a known character. `information_issues` gives up to 64 exact snapshot paths, with the full `information_issue_count`. Unrendered costs or masked collection/spell cards therefore cannot produce a blanket complete result. These fields do not claim arbitrary-mod support or live UI acceptance. Spell groups remain bounded at 256 source entries, flame groups at 12 and encode/socket rows at 16.

Visible collection cards are copied and sorted by card ID/UUID before projection; hidden and null entries are excluded before sorting. Their internal draw order is neither returned nor modified (`order_visible=false`). The collection-only 128-row cutoff is removed. Default MCP decisions retain counts and `cards_context`; contents are available via `sts_get_context(section="collection")` or `section="combat_collection"`, with `offset`, `limit` (1–100), `items`, `total` and `next_offset`. `section="mechanics"` also pages each nested collection's `cards` using the same offset/limit and independent next offsets. Noncombat resources appear under the default player's `mechanics`, combat resources under `combat.player.mechanics`.

`count` is the native pile size; `total` is the captured visible row count; `cards_complete` says whether masking omitted rows; `page_complete` says whether this page contains the entire captured pile (not whether its card details are complete). Snapshot-level completeness and issue paths remain unchanged across pages. Follow `next_offset` until null using the same session/state IDs; a stale rejection requires a new state and paging from the beginning. No live card references cross the bridge.

This is MCP response pagination, not lazy game-thread fetching. Like the existing deck, all visible collection projections are captured upstream; existing whole-state transport limits still apply. Extremely large/text-heavy states can exceed those limits, and this change does not claim unlimited deck support or bounded upstream capture time. Full observation remains an explicitly requested unpaginated snapshot.

## Cost semantics

The passive hook records the final `renderEnergy` text **after** BaseMod's alternate-cost text modification and native `RenderEnergySwitch.Insert`, not the earlier private `getCost()` return. Only the current completed frame and matching public context can supply `displayed_cost_text`; detached/unrendered previews cannot reuse another card's cost.

| Case | Observation |
| --- | --- |
| Numeric energy | Fixed displayed amount and energy component |
| Positive reserves | `energy_or_reserves`, energy-first / reserve-shortfall payment policy |
| Collector Finger of Death | `reserves`, never mislabeled as energy |
| X | Explicit native `X` text, energy-plus-reserves budget before card-effect bonuses; reserve payment distinguishes `freeToPlayOnce` |
| Pyre modifier | Additional other-hand-card sacrifice/exhaust component and required selection, even when energy is free |
| Unknown `?`, unrendered frame, unknown provider | Explicit incomplete result; never infer X from a hidden underlying `cost=-1`, nor energy from an unknown numeric provider |

The cache fingerprint includes current energy/reserves, card identity/cost/free/autoplay context, hand identities, power amounts, relic counters and modifier identities. No observer calls `getCost`, `freeToPlay`, alternate-resource evaluators, cost-spending hooks or selection actions. `cost_components_complete` concerns payment components in the pinned native renderer, not damage/HP loss in a card effect, Chemical X or arbitrary effect outcomes. Unknown external/alternate-cost providers remain incomplete.

The installed audit found no Downfall `AlternateCardCostModifier` implementors or card `getCost/renderEnergy` overrides. Champ's unused `myHpLossCost` field is not treated as a payment resource; Slime self-damage remains a card effect. The new code does not manufacture a generic HP payment from either.

## Evidence and limits

- `NativeMechanicsBindingTest`: fourteen native player classes, actual public/private field access, forty audited getter bodies (no direct field writes, stdout calls or game RNG calls).
- `CharacterResourcesTest`: production projection with field-only graphics fixtures; real installed reserve/essence, Gremlin and Champ counter methods; hidden/mixed/noncombat behavior; source collection order preservation; 36 combinations matching the actual installed Inferno description method.
- `PlayerMechanicsTest`: mixed-character encode visibility, native slot cap, hidden cards/stasis and private Inferno localization access; no invocation of Inferno's logging getter during observation.
- `MechanicsCompletenessTest`: independent binding/data status, nested card/description/cost flags, unavailable and truncation flags, unsupported characters and bounded issue paths.
- `NativeCostObservationTest` and `SpecialCostTest`: production passive observer, free/X/reserve-only/mixed/Pyre cases, invalidation, unknown provider/text, hidden-X regression and no cost/free-play evaluator invocation.
- `LocalObserverBuildTest`: final post-modifier renderer hook and no raw-getCost observer hook.
- MCP tests: resource counters and warnings preserved in default views, collection details on demand, real SDK section discovery/calls. Installed-bundle verification only calls read-only process status; it does not launch or play.

These headless checks do not replace screenshots or a normal run with each character. Future rolls, hidden draw order, graphics/animation fidelity, arbitrary third-party mechanics, controller/touch workflows and non-pinned installations are not covered by this milestone.

## Release verification

See the execution evidence in `docs/superpowers/plans/2026-09-12-character-resources-costs.md` for the final prepared runtime, installed bundle and command results. A failing preparation does not replace `target/local-test-ready.json`; only a complete passing pipeline promotes a new copy.
