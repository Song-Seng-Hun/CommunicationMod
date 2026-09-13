# Lossless compact MCP context

User approved selective TOON optimization after the feasibility comparison. Scope: this workspace's `sts_get_context`, not third-party MCPs, game logic, saves, global configuration, or deployment.

## Contract

- Keep existing JSON text plus `structuredContent.data` by default. Add only optional `response_format: json | compact` on context reads. Existing callers, IDs, refs, pagination, limits, errors and guards remain compatible.
- `compact` returns one text block, with no duplicate structured payload. Prefer JSON for small or non-tabular fragments. For repeated uniform primitive rows, use the pinned official TOON codec with ordinary comma-delimited headers and two-space indentation. No invented TSV type dialect, renaming, summarization or abbreviation of game text.
- Serialize through the existing JSON representation first. Decode every candidate and require exact JSON serialization equality before returning it. Unsupported strings, problematic keys, codec failures, and unprofitable conversions fall back to exact JSON text.
- Include a short TOON format label/reading guide in each converted response. Count this overhead. Select TOON only when local `o200k_base` text tokens save at least 16 tokens and 10%; require serialized text-block tokens not to grow. This is a tokenizer-specific bound, not a billing claim.
- Limit conversion work to bounded context-size inputs; load tokenizer only for candidates. Do not cache game responses or alter conditional view hashes.

## Validation

1. Red/green tests: old default shape; explicit JSON recovery; compact text-only; uniform rows compress; tiny/irregular inputs remain JSON.
2. Exact equality over workflows and generated edge cases: null/empty/missing, number-like strings, Boolean-like strings, quotes, delimiters, control characters, Korean, emoji, UTF-16, keys, IDs and incomplete flags.
3. Real SDK stdio integration with synthetic backend: compact reads, multiple refs/pages, invalid/stale requests, unchanged action dispatch semantics.
4. Report current-catalog overhead, extra request fields, raw text and serialized result totals separately. Measure cold/warm latency. Existing full regression suite must pass.
5. Independent review and bounded AI interpretation checks supplement deterministic roundtrips; neither proves universal model comprehension. Keep compact opt-in and JSON recovery available.

## Alternatives considered

Full TSV replacement needs extra type/escaping conventions and breaks structured consumers. Default text-only replacement breaks current clients. Keeping TOON text plus duplicate structured JSON reduces less and conflicts with the compatibility JSON-text recommendation. Selective explicit compact mode isolates these costs and risks.

No commits, pushes, game launch or installed-plugin replacement in this change. Deployment remains a separate verified step.
