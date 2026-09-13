# Card upgrade comparisons

Card acquisition and upgrade decisions now carry the next **standard single upgrade of the current card instance**, not a lookup of a generic `Card+` template.

## Where to read

| Situation | MCP location |
|---|---|
| Card rewards and shop offers | Default decision `screen_state.cards[].upgrade_preview` |
| Grid selection, including event upgrade selectors | `screen_state.cards[].upgrade_preview` and selected cards |
| Rest/event/grid deck planning outside combat | `sts_get_context(section="deck")`, on each card |
| Event option that has an actual card preview | Default `screen_state.option_card_previews[]`, associated by `option_index` and, when enabled, `choice_index` |
| Native upgrade confirmation | `screen_state.upgrade_selection_preview`, including the current normal/branch choice or the active native multi-upgrade tree |

The detailed `screen`/`full` contexts retain the original event option/card association. The default response removes repeated event prose, not its card information. No new MCP tool or game action was added; unsupported shop/grid control remains unsupported rather than being enabled by an observation feature.

## Preview contract

`upgrade_preview.status` is one of:

- `available`: `after` contains the next card's name, description, keyword tooltips, base cost/stats, effect flags and upgrade count. `changed_fields` identifies differences; `numeric_changes` carries from/to/delta for changed numeric fields. `can_upgrade_after` reports whether another ordinary upgrade is possible.
- `not_upgradable`: the card's native `canUpgrade()` returned false. There is no fabricated next card.
- `unavailable`: the result is unknown, with a reason. This does **not** mean upgrading is impossible.

Every computed preview says `scope=next_standard_upgrade`, `steps=1`, `event_effects_predicted=false`, and `on_obtain_effects_predicted=false`. Acquisition relics, random event target selection, event-specific multi-card effects and arbitrary upgrade overrides are not inferred from option prose.

For example, the installed Searing Blow upgrade body changes the current +3 instance from 27 damage to +4 / 34 damage; it still permits another upgrade. The service does not expand an unbounded chain of future upgrades.

## Safety and limits

- The game-thread adapter follows native preview semantics: `makeStatEquivalentCopy()`, one `upgrade()`, `displayUpgrades()` and description initialization on the detached copy. It never invokes an event or acquires/upgrades the source card as a gameplay action.
- Hidden/locked/flipped cards are gated before preview callbacks. Copies must be non-null, distinct, same-class and preserve identity/base stats/upgrade count, existing effect flags, description and displayed variables. Observable source changes, lost effects, no-op upgrades and exceptions produce explicit unavailable results.
- Generated preview UUIDs are not exported. A finite 256-entry cache is invalidated by changed public decision context or the current card snapshot. Failed copy/upgrade attempts are cached within that context. More than 256 prior upgrades or decision preview candidates gets an explicit budget reason.
- StSLib `BranchingUpgradesCard` and `MultiUpgradeCard` are not arbitrarily upgraded, since that could choose a branch or consume randomness. Their computed preview says `upgrade_choice_required_use_native_preview`. Actual native confirmation previews/alternatives are read without upgrading or initializing them. Branch data is gated by the current card type and active selection flag, so stale fields cannot attach to an ordinary card. This does not claim every alternative is available before the game's selector opens.
- Active multi-upgrade selection reads `MultiUpgradeTree` and its visible graph, not the obsolete grid preview list. It supplies `nodes` with upgrade indices, card snapshots, taken/locked/UI-selectable state, parent/exclusion indices and native strict-dependency flags. A source mismatch suppresses the cached tree. Enumeration is bounded to 64 nodes/edges per node with completeness/error reporting. `ui_selectable` describes the native UI, not an offered MCP action.
- Description, tooltip and effective-cost incompleteness flags remain intact. `base_cost` is not a claim about a custom rendered cost, and base damage is not a prediction of combat target damage.
- Custom card copy/upgrade/description hooks are game/mod code, not a sandbox. Their contract is the same as a normal native UI preview; arbitrary extension side effects and rendering-specific behavior still require live compatibility validation. The headless tests do not certify every Downfall card or third-party modifier.
- No saves or Steam files are edited. No game launch or gameplay occurs during the verifier.

## Verification

`devtools/verify-card-upgrades.ps1` checks:

1. Real generic preview service: nonlinear repeat growth, cap, cost/text differences, invalid copies, exceptions, hidden/branch gates, source preservation, stable/invalidated/bounded caches, returned-result mutation isolation.
2. Installed API and compiled production wiring: reward/shop/grid conversions, game adapter methods, native event/grid fields.
3. Real adapter and installed Searing Blow, Strike and Hermit Snapshot **upgrade method bodies** using field-only engine/localization/graphics fixtures. Native grid source validation, stale-branch suppression, active branch selection, multi-upgrade graph/locks/dependencies/source matching and inherited event buttons are also checked. Constructors/copy dependencies in this harness are fixtures, not proof of their full live behavior.

MCP view tests preserve the comparisons in default/detailed responses and retain event card previews after prose deduplication. Full runtime preparation includes these checks alongside the existing regression suite. Actual shop/campfire/event gameplay acceptance remains pending until exercised in the isolated game.

Latest verified build (2026-09-12): all three upgrade test groups and 15 MCP tests passed; full preparation exited 0 in `target/card-upgrade-release-verification.log`. The ready runtime is `target/local-test-20260912-051708-b7329a33`; original Steam files remained unchanged. The existing plugin `0.1.0+codex.20260911200800` was reinstalled and checked through actual SDK discovery/status without starting the game. Use a new Codex task to pick up that installed update.
