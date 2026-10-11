# Lossless context reuse guidance

The current MCP already supports explicit retained-full-response reuse, but Gemini's tool catalog did not explain its optional arguments. The three existing schema descriptions now state when reuse is valid, which full baseline a delta requires, which fragment page its content ID identifies, and how to recover after context loss. No input type, default, bound, tool, formatter, projection, delta algorithm, action dispatch, native observation, skill or AI setting changes. Default calls retain their existing behavior.

Clients can use the existing recipe only while retaining complete fully emitted responses. First call `sts_get_state({reuse:true})` and retain that full response. Later state calls may send `{reuse:true,known_view:full.view_id}`; apply returned deltas to that exact full response. A delta is not a new full-emission baseline. State/action/session safety headers and current pin checks remain effective. For context, retain the complete full fragment at its exact ref/offset/limit and send its ref/content_id pair in `known`. Expand unchanged fragments from that retained page. After context loss omit known_view/known, or use refresh:true for state, to receive full content. The reference decoder remains `restoreDelta`; no custom encoding or decoder was introduced.

Pilot comparison of wider standard TOON eligibility produced no improvement on33 responses:4113 ->4113 local proxy tokens. That formatter change was rejected. A separate pilot of the existing reuse recipe counted56 synthetic calls, their requests/replies and one catalog with proposed descriptions:19374 ->14392 tokens. This was exploratory, not the final performance claim. One runner assertion initially merged the transport-only unchanged marker into a full fragment; the failure and cause-specific evaluator repair are preserved.

Before confirmation, eight independent six-snapshot fixtures, the existing v19 baseline, exact candidate descriptions, o200k_base proxy metric, one catalog per variant, all calls/pages, and a10% minimum aggregate useful effect were frozen. The confirmation used two real stdio MCP servers against authenticated synthetic backends. Both variants read the same relevant current detail refs and every required text page. Complete reconstructed states and fragments, false/null/missing fields, incomplete flags, current refs, potion slots, Hermit card/roulette changes, sockets/origin/Dead On, summons/Stasis/panels and power states matched. Snapshots and frozen modules remained unchanged; no native game commands were sent. One runner syntax error prevented measurement before the corrected run and is retained separately.

Confirmation result:48 state calls plus78 context reads,126 calls per variant. Every request/reply and one real tools/list catalog are counted. Primary local text-plus-requests-plus-catalog cost31375 ->24878 (20.7% lower); secondary serialized-results-plus-requests-plus-catalog34089 ->27260 (20.0% lower). Catalog alone grows1177 ->1298; its overhead is included. Aggregate effect exceeds the frozen10% threshold with exact reconstruction.

| Frozen scenario | Baseline tokens | Reuse tokens |
| --- | ---: | ---: |
| unchanged_minimal | 269 | 632 |
| small_hp_changes | 2304 | 1135 |
| potion_consumed_or_retained | 4194 | 2764 |
| hermit_replacement_and_preview | 4922 | 3935 |
| player_socket_origin_dead_on | 4541 | 4926 |
| summons_stasis_and_panels | 4392 | 2943 |
| power_zero_negative_and_incomplete | 3792 | 2809 |
| full_context_pages_and_changes | 5784 | 4436 |

These per-scenario totals exclude the one shared catalog, which is included in the aggregate above. No case was excluded. Reuse does not save on every call: unchanged default polling is already cheaper, and frequent whole-hand replacements can make explicit reuse cost more. Prefer existing default unchanged checks for repeated identical state. Short contexts can remain ordinary reads. Lost/missing/expired baselines and unsuitable deltas still recover full information; the client's additional retained-baseline identifiers remain overhead in that case.

Conclusion: meaningful improvement for this complete local retained-baseline recipe under the frozen synthetic conditions; no meaningful improvement from wider TOON eligibility. The source change improves discoverability of an existing opt-in facility. It does not force Gemini to adopt it or establish billed-token, strategy, latency or live-game benefits. Local o200k_base is a proxy, not Gemini's tokenizer or invoices. Production release/install evidence is recorded after the required gates in the task evidence; installation is distinct from model adoption.

Reproducible evidence and canonical task contract are in C:/Users/meleb/OneDrive/문서/ChatGPT/stsmod: TOKEN-ECONOMY-TASKS.md, token-economy-pilot.json, token-economy-reuse-pilot.json, token-economy-confirm.mjs, token-economy-confirm.json, token-economy-confirm.log and token-economy-targeted-red.log / -green.log. The confirmation source/error records, exact final source hashes and release/installed checks remain available there. Baseline is the immediate v19 entrypoint, not an older full-dump or JSON-only counterfactual.
