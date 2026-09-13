# MCP context economy

This contract supersedes the older `section` examples in historical milestone documents. It changes the MCP presentation/retrieval layer only, not native observations, game actions, saves or whole-state transport limits.

## Read the table of contents, then one useful piece

`sts_get_state` returns essential current-state facts and `toc` entries with a reference, short title and count. No empty fixed section catalog or unrelated screen dump. Deck/persistent resources are run context; map is scoped to map decisions; ordinary hands are inaccessible during incomplete/selection states. Combat metadata and current-card details have their own references. Unclassified extension bodies are not automatically published, and `unsupported_information` explicitly signals that gap.

| Need | Read |
| --- | --- |
| Current card or offer | Its observed `ref`; a small selected card is one atomic fragment including short keyword/upgrade details |
| Large card / deeper detail | Its child `toc`, such as a description, tooltip or upgrade comparison |
| Event body | Default `event_reading.body_ref`; follow all text chunks before acknowledgement |
| Collection tail | `collection` → `cards` directory with offset → selected card ref |
| Screen-specific rules | Current `rules` fragment only; not a universal long guide |

Example (IDs and refs must come from the current state):

```json
{"session_id":"observed-session","state_id":123,"refs":["hand/2","rules"]}
```

`sts_get_context` accepts 1–8 refs and returns `fragments`. Objects return short scalar fields plus a child toc; arrays return paged directories; text returns exact resumable chunks. Small explicitly selected cards can return a bounded nested card atom to avoid unnecessary round trips. A batch shares the same offset/limit; use separate calls when requested cursors differ. Ref paths are owned public fields, not arbitrary reflection or save paths. Prototype keys and non-current roots are rejected. There is no exposed `full` bypass.

`limit` is 1–30 fields/rows (default 20), not tokens. Text chunks are up to 1,600 UTF-16 units, with a smaller escaped-JSON budget and surrogate-safe boundaries. Follow `next_offset` unchanged; it is a field/row index for directories or a UTF-16 offset for text. Each fragment is roughly bounded to 2.4k JSON characters; native missing-data flags remain unchanged. Oversized/unaddressable fields produce explicit incompleteness and advancing cursors, never silent loss or an infinite page loop. These are wire bounds, not a promise of a fixed token budget.

## Conditional polling and safety

Use the `view_id` returned by `sts_get_state` as `known_view` on a deliberate repeat query. If identity, visible state, readiness, connection and pending-action semantics are unchanged, the reply is a small `unchanged:true` acknowledgement. Age alone does not trigger a full repeat. Omit `known_view` whenever the previous context is unavailable. This is opt-in recovery-safe polling, not an implicit server assumption that the model remembers old text.

Always keep matching session/state IDs for fragment reads. A stale-state rejection requires a new summary and fresh references. `sts_act` still returns its receipt plus the next decision, so do not insert unnecessary polling after every action. Missing descriptions/costs/constraints are unknown: read details before choosing. No observer or helper takes gameplay actions.

## Measurement

Run `npm test` in `mcp-server`; the suite includes the tokenizer regression and the ten read-only answer checks through actual MCP SDK tools. `node scripts/measure-context-economy.mjs` also writes `target/context-economy-report.json`.

The comparison freezes the previous `view.ts` output and six-tool catalog in `mcp-server/evaluation/baseline`. It uses five synthetic public scenarios: current combat card, full event story, map route, shop price/upgrade, and collection tail. Required facts are asserted equal. Both traces include the same two deliberate repeat polls. All new discovery/detail request payloads and replies count, not just the smaller default response. Tool definitions count once across the five-scenario trace.

The local tokenizer is `o200k_base` via `js-tiktoken`; these token reports make no provider/model API call. The report separately shows the same workflows without polls and the larger wire representation containing both text and structured content. Default JSON mode sends both for client compatibility; it is unknown which representation the host injects or caches. The compact-format pass below makes the tokenizer a pinned, lazily loaded runtime dependency. Model reasoning, context replay, billing and autonomous task success are NOT measured by these reports.

One-shot details can cost more than a pre-expanded tiny snapshot. Do not quote the repeat-poll saving as a universal per-call or billed-token saving. The test gates both the shared-catalog repeated workflow and its no-poll total, while preserving the individual rows so regressions cannot be hidden in an average. This is not live gameplay acceptance or a proof that every possible agent strategy uses fewer tokens.

### Verified local comparison (2026-09-12)

| Counted scope | Previous tokens | Current tokens | Reduction |
| --- | ---: | ---: | ---: |
| Tool definitions | 1,902 | 1,048 | 44.9% |
| Five workflows + definitions once + two repeated polls per workflow | 10,079 | 5,305 | 47.4% |
| Same workflows + definitions once, without repeated polls | 4,859 | 4,609 | 5.1% |

All five required-fact checks and all 31 tests passed. Each individual no-poll detail workflow grew in payload-only tokens; the no-poll combined saving comes from the smaller shared tool catalog. These figures are the local scripted tokenizer result, not measured host billing or autonomous gameplay. See the generated `target/context-economy-report.json` for every row and call count.

### Meaning-preserving prose pass

The later Caveman-style pass edits only six tool titles/descriptions, six state-specific rules, and one startup waiting hint. It does not transform arbitrary response strings. Game text, exact errors, receipts, numbers, units, JSON fields and identifiers remain unchanged. Negation, permission, uncertainty and ordering remain explicit. No Caveman package, hook, external summarizer or global MCP proxy is installed.

`test/prose.test.mjs` compares the real SDK catalog against `evaluation/baseline/prose-v2.json`, checks unchanged schemas/annotations, asserts safety clauses and exact native evidence, and measures each edited unit with `o200k_base`. Every unit must be shorter. These checks complement semantic review; they do not prove equivalence for every model interpretation.

Measured prose units, counted once each including title/description wrappers: 523 to 473 tokens (9.6% lower). Full catalog: 1,048 to 1,020 tokens (2.7% lower). Updated five-workflow trace with repeated polls: 5,305 to 5,277 tokens; without polls: 4,609 to 4,581. These incremental savings are separate from the earlier structural optimization. The report is `target/prose-compression-report.json`; neither report measures actual model billing. Full suite: 33 tests passed.

### Projection performance pass

Array directories now build pairs for at most the requested page, rather than the entire array. Total counts, absolute references and cursor/budget behavior remain unchanged. Map-only polling builds only the identity/connection/plan view; it does not traverse narrative, combat or control details. Fixed observation-key membership is reused. No state cache is introduced.

`test/performance.test.mjs` checks exact serialized responses, hashes, errors and boundaries against frozen `evaluation/baseline/context-v3.mjs`. It also checks deterministic work counts: three requested rows from 10,000 cards require 3 instead of 10,000 element reads; map-only polling reads 0 instead of 10,000 unrelated narrative entries. Malformed non-string ready-state action IDs use the original projection to preserve conversion errors and prefix short-circuits. Full suite: 38 tests passed.

Run `node scripts/measure-context-performance.mjs` from `mcp-server` for the synthetic CPU benchmark. The recorded large-page and map-only cases improved approximately 17x and 7x. Ordinary decisions were essentially unchanged or slightly slower in the same sample; every row is retained in `target/context-performance-report.json`. Timing uses 2,000 warmup calls per implementation and alternating 100-call batches; it reports median/p95 batch means, not individual-call tail latency. These are not game FPS, end-to-end MCP latency or token-savings claims.

### Lossless selective format pass

`sts_get_context` alone adds optional `response_format: json | compact`, default `json`. The JSON default is unchanged. Compact requests return one text block (no duplicate `structuredContent`): labeled TOON when profitable, otherwise exact JSON text. The label explains two-space indentation and row/column headers. Other tools, annotations, argument semantics, native strings, receipts, errors, IDs, view hashes and pagination stay unchanged. No third-party MCP or global configuration is modified.

The official `@toon-format/toon` 4.1.1 codec is pinned and has no runtime dependency tree. `js-tiktoken` 1.0.21 is promoted to a pinned runtime dependency. No install scripts are run. The formatter serializes using the original JSON contract, considers uniform primitive-row tables, and demands exact JSON equality after strict decoding. It falls back for unsupported Unicode, unsafe/non-roundtripping representations, unprofitable conversion, and codec/tokenizer errors. Nothing is truncated. Inputs over 32,768 JSON characters bypass conversion; the upstream context fragment limits are unchanged. No cache stores game payloads.

The selection gate counts the complete guide and data: at least 16 tokens and 10% fewer raw `o200k_base` tokens, plus smaller serialized text-block output. This bound does not apply to another tokenizer, whole task cost, catalog overhead or host billing. JSON/default, small and non-tabular requests do not initialize the tokenizer; first conversion in a process measured about 0.6 s, warmed 20-row conversions about 3 ms median / 4 ms p95. This is formatter-only latency, not end-to-end MCP timing.

| Measured scope | Before | After |
| --- | ---: | ---: |
| Full 20-row directory body, including metadata/TOON guide | 407 | 337 |
| Same directory serialized result (legacy duplicated JSON vs compact text only) | 853 | 353 |
| Complete selective 60-card browsing: text + all requests + catalog once | 2,836 | 2,685 |
| Same selective browsing, serialized results + requests + catalog | 4,684 | 3,243 |
| Existing five workflows, **indiscriminate** compact: text + requests + catalog | 5,277 | 5,362 |
| Same indiscriminate trace with serialized results | 9,469 | 8,199 |

The catalog grows by 45 tokens (1,020 → 1,065). All five older workflows' small context reads fall back to JSON, so extra arguments/catalog make raw text totals worse. This counterexample is kept in the report: compact should be selected for sizeable directory pages, not every read. For the three-page 60-card browse, discovery and final card detail remain JSON; only the directories use compact, producing 5.3% full text/request/catalog savings. Duplication removal is reported separately because the host may inject only one representation.

Validation: `test/format.test.mjs` checks defaults, exact roundtrips, 100 seeded row cases, primitive/Unicode/escaping boundaries, unsupported UTF-16 fallback, all prior workflow facts and frozen reading-answer semantics. `test/mcp.test.mjs` checks real SDK stdio compact pages/batches, JSON recovery without reconnect, schema defaults, invalid formats, stale errors and unchanged dispatch guards. Prior prose checks still require unchanged safety clauses; they now explicitly permit the one independently tested schema addition. Independent read-only code review found no actionable issues.

Two isolated readers received only one representation each and the same ten questions, with no expected answers or tool access. JSON: 10/10; TOON: 10/10, including replay prohibition, unknown cost, numeric-looking IDs, string `null`, exact newline/tab and continuation cursor. Fixtures and actual answers are in `mcp-server/evaluation/format-reading.mjs` and `format-reading-results.json`. This is a one-shot smoke check, not statistical equivalence, actual-host injection verification, or gameplay acceptance.

Run `node scripts/measure-context-format.mjs` after building. The generated `target/context-format-report.json` retains every row, raw and serialized counts, catalog overhead and timing. Final full suite: 48 tests passed. Source/build verified locally; the installed plugin and copied game runtime are not replaced in this pass. Existing installed tools do not gain this option until a separate plugin rebuild/install and MCP reload.

Follow-up: on the user's subsequent update request, version `0.1.0+codex.20260912053309` was installed/enabled locally. The installed bundle passed `scripts/verify-compact-plugin.mjs` through actual SDK stdio against an isolated synthetic backend: matching server files, compact roundtrip, JSON recovery and stale rejection. Game runtime/saves unchanged, real gameplay actions 0. Start a new Codex task to pick up the new tool schema; see `docs/CODEX-PLUGIN.md` for backups and installation evidence.
