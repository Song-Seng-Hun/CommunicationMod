# Player-visible card text and hover tooltips

## Scope and data contract

This implements the approved public-information part of the Downfall plan. It does
not activate the held-off game connection. Card observations now include:

| Field | Meaning |
| --- | --- |
| `text_language` | Current `Settings.language` enum name, e.g. `KOR`; `unknown` if unavailable |
| `description` | Existing card UI-cache text, with supported dynamic variables resolved |
| `description_complete` | Cached text/tokens were available and resolved; NOT proof of pixel-identical rendering or fresh UI state |
| `displayed_values` | Values of dynamic variables appearing in that public description |
| `tooltips` | Detached ordered entries with `source`, optional `keyword`, `title`, `description`, and text completeness/truncation metadata |
| `tooltips_complete` | All covered tooltip sources succeeded without truncation or known missing extensions |
| `tooltips_unavailable_reasons` | Stable failure/unsupported reason codes, not exception messages or internal state |
| `unresolved_keywords` | Public keyword keys whose lookup was missing or failed |
| `tooltips_truncated` | A tooltip/text collection limit prevented complete output |

The language marker describes the selected game language, not a promise that every
mod translation exists. Text comes from the currently loaded game/mod localization;
the game's own fallback language remains intact. There is no external translation,
name-based inference, permanent language cache, dictionary dump, or recursive scan
of terms that the card UI did not expose.

Dictionary lookup follows `TipHelper.renderKeywords`: the exact `card.keywords` key
is used with `GameDictionary.keywords`, and `TipHelper.capitalize` supplies the
patched/localized display title. Namespaced keys are identifiers, not fallback words.
Duplicate entries and source order are preserved. Standard extra text sources are
`CustomCard.getCustomTooltipsTop`, `getCustomTooltips`, and each card modifier's
`additionalTooltips`. The returned lists are copied and never sorted or modified.
Source labels describe groups, not exact screen pixel position/hover ordering.

The observer runs on the game thread under the existing observation caller contract.
Only seen, unlocked, non-flipped cards enter text/tooltip getters. Hidden cards yield
empty tooltip/text/value collections; language metadata is not secret. This does
not audit or change legacy card identity/cost fields elsewhere in the converter.
No description initialization, power calculation, rendering, card use, or arbitrary
field reflection occurs. Extension getters are invoked under the same read-only
contract expected by the UI; purity of every installed override is not yet proven.

Color markup is removed for plain text; line boundaries remain, and `[E]`/custom
icon tokens remain symbolic, not invented translations. Missing values remain
explicitly unresolved. Limits: 32 keyword/tooltip entries through the pure
projector, 1,024 title characters, 8,192 description characters per entry, and 32,768
combined title/body characters per card. The binding inspects at most 32 modifiers
and copies at most 33 entries from a getter to detect truncation. Getter-internal
work is outside these bounds. A failing provider is isolated from other providers;
modifier-source failure may omit that source's remaining text and is reported.

## Explicit remaining limitations

- Character-break/CN card caches are still withheld with
  `cn_cached_encoding_unsupported`. Their localized keyword tooltips are independent
  of that cache and can still be observed.
- StSLib adds common-keyword icons, custom-icon and damage/block/power-related tips
  during rendering. These render extensions are not replayed here. When `stslib` is
  loaded, `tooltips_complete=false` and `stslib_render_extensions_not_evaluated`
  prevent claiming all Downfall tips were collected.
- Upgrade previews, render-only text refresh, alternate costs, icon expansion and
  per-card extension purity still need implementation/audit and actual UI comparison.
- New fields are wired into the existing card converter (hand, rewards, grid, piles,
  etc.). Protocol v2 live integration and runtime activation remain separate work.

## Verification and operating constraints

Use `devtools/verify-public-descriptions.ps1` for pure Java 8 text/tooltip tests,
installed renderer/static binding checks, full-source compilation, draw-pile privacy
permutations including tooltip fields, and unchanged launch gates. `-PureOnly` needs
no game initialization or installed-game execution classpath.

The test-first cases cover Korean/English snapshots, namespaced keys, duplicates,
hidden callbacks, null/missing text, getter/optional-linkage failures, independent
provider recovery, size limits, color markup, icons, and source preservation. Static
binding checks are not an in-game screenshot comparison or a purity proof.

Follow with `devtools/verify-compatibility-foundation.ps1` for the complete headless
regression set. Keep bounded JVM heaps and sequential execution, no agent fan-out.
No game launch, Steam option changes, real-save writes or online submissions are
authorized by this observation milestone. Commit/push only to the user fork; no PR.
