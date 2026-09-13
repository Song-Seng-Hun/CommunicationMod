# Run controls and decision information

These additions target the hash-pinned copied Downfall runtime. They are not a claim of full Downfall compatibility or live UI acceptance. Original Steam files and saves are not modified.

## Supported adapters

| Area | Implemented behavior | Boundary |
| --- | --- | --- |
| Rewards | Native card selectors, including more than three choices and special native selection modes; Singing Bowl; visible Proceed even with unclaimed rewards | Check `reward_navigation` before abandoning rewards; unknown replacement screens are withheld |
| Grid | Native selection, deselection, confirm/cancel; branch and multi-upgrade choices | Choose a preview and inspect `selection_controls.upgrade_choice.selected_after` before a separate confirmation; locked/tree-ineligible cards are not offered |
| Rooms | Rest options, merchant entry/exit, card/relic/potion purchases, removal selector, chests and boss relic selection/skip, visible room Proceed | Revalidate room, screen, item identity, price, funds and input readiness; no synthetic rewards or direct wallet writes |
| Potions | Explicit use/discard, native target-required enemy selection | Preserve native popup/relic/mod hooks; no auto-discard/replacement; user-opened potion UI blocks automation |
| Cards | Target-specific playability; final rendered energy/reserves/X/Pyre cost components | Unknown providers, unknown text and stale/unrendered costs remain incomplete; payment components do not simulate card effects |
| Character panels | Public native resources for all fourteen player classes, including mixed encode/flame/spell panels, Gremlin HP, Collector collection, Guardian sockets and Inferno prose | Pinned binding coverage only; preserve nested completeness flags and hidden data; see [resource and cost matrix](CHARACTER-RESOURCES-AND-COSTS.md) |

## Safety and observation

Every gameplay request uses the current session/state and one offered action. Shared room adapters revalidate native object identities/readiness; combat actions also claim a decision token. Native input flags are scoped to the synchronous handler and restored even after exceptions. Pending physical clicks, popup/targeting modes, transitions, touch and controller modes withhold controls. The copied builder verifies and installs input hooks; normal human updates are unchanged while no automation input is armed.

Only public card/panel data is projected. No future draws, RNG, hidden enemy moves, save editing, reward synthesis or legacy executor is added. `selected_after` is the actual native preview object, not a guessed upgrade. Standard preview cards do not predict on-obtain or extra event effects.

## Validation boundaries

`devtools/verify-run-usability.ps1` freshly compiles production sources and runs input-scope, eligibility, cost, target-reason, public-panel and installed-signature/wiring checks. `devtools/verify-card-upgrades.ps1` additionally executes installed card-upgrade bodies with graphics fixtures and validates native branch/tree observation. `devtools/prepare-local-test.ps1` runs the complete foundation and copied-runtime bytecode/launch checks. MCP projection tests preserve controls and incomplete flags.

The usability suite also executes the installed StSLib branch/normal/tree selection handler bodies with real production scoped input and field-only graphics fixtures. This verifies native dispatch but is not a complete live grid-screen test. On 2026-09-12 the full preparation pipeline passed (27 patched classes), focused usability checks passed (12 groups), MCP tests passed (16/16), and the refreshed installed plugin passed read-only tool/bundle verification with the game stopped and zero gameplay actions.

The later collection/completeness improvement reran the full pipeline and passed 18/18 MCP tests. It removes the collection-only 128-row cutoff, pages public collection contents, and propagates nested information gaps independently of native panel bindings. See [the execution record](superpowers/plans/2026-09-12-collection-pagination.md) for the newer prepared runtime and installed plugin. Whole-state transport limits and live gameplay verification remain outside this improvement.

The paragraph above records the preceding usability release. The subsequent character/resource implementation and its verification scope are tracked in [the resource and cost matrix](CHARACTER-RESOURCES-AND-COSTS.md). These checks do not execute every live shop, rest, potion, grid, boss or character panel in an actual game window. Full-run acceptance, touch/controller input, arbitrary third-party replacement screens/cost providers, and non-pinned base/workshop installations remain unverified. No gameplay is performed by the release-verification scripts.
