# Collection pagination and completeness implementation plan

> Execute inline with TDD; preserve existing dirty changes. No commit, push, game launch, or save mutation.

**Goal:** Make public Collector contents beyond 128 accessible and distinguish native panel bindings from delivered information completeness.

**Architecture:** Retain the existing immutable session/state snapshot transport. Remove the collection-only 128-row truncation (like the existing deck projection); sort only visible copied cards by ID/UUID, never native draw order. Paginate nested collection cards in `mechanics`, plus independent `collection` and `combat_collection` sections. This bounds MCP responses, not upstream snapshot capture: existing transport budgets still apply. Recursive completeness checks inspect the already projected public data, not game internals, with bounded issue paths.

**Tech Stack:** Java 8, Javassist fixtures/native binding tests, TypeScript, Node MCP SDK.

- [x] Add RED Java tests: 130+ cards, masked/null exclusion, source preservation, masked spell flag; pure completeness tests for nested false flags, unavailable statuses, supported/unsupported and issue-path bounds.
- [x] Add RED MCP tests: page 0/100/last, independent piles, empty/missing sections, metadata and state identity, stale context rejection and zero additional action dispatches.
- [x] Implement `CharacterResources` capture, `MechanicsCompleteness` aggregation, `PlayerMechanicsObservation` integration, `view.ts` pagination and `index.ts` schema/discovery.
- [x] GREEN: `devtools/verify-run-usability.ps1` and `npm test` in `mcp-server`.
- [x] Review scoped changes and update resource/API documentation and installed-plugin verifier.
- [x] Fresh isolated runtime build and plugin update; verify installed bundle via read-only MCP status. No gameplay; no claim of live Collector validation.

Acceptance examples: a 135-row collection page at offset 100/limit 30 returns 30 rows and next_offset 130; the final page returns 5 rows and null. `collection.cards[0].displayed_cost_complete=false` makes information/character completeness false while panel binding completeness stays true. Hidden rows are not projected or assigned public order positions.

## Execution evidence

- RED observed: missing recursive completeness; Java public collection beyond 128 retained assertion; MCP mechanics returned 135 instead of 30 rows; actual SDK rejected the new collection section before schema implementation.
- GREEN: fresh `verify-run-usability.ps1`, including nested integration, 138 visible persistent cards, independent 132-card combat collection, masks/nulls, fixed-UUID duplicate-ID ordering and source preservation. MCP suite 18/18, including real SDK pagination and stale-state rejection with no extra action dispatch.
- Read-only independent review found no blocking defects; both suggested test gaps (duplicate IDs and large combat collection) were added and passed in the final pipeline.
- Full `prepare-local-test.ps1` passed after the scoped Maven permission retry. Prepared runtime: `target/local-test-20260912-124552-af471f87`; 27 patched classes, manifest hashes and packaged `MechanicsCompleteness.class` verified. Original Steam files unchanged.
- Installed existing `downfall-agent@personal` version `0.1.0+codex.20260912034817` using the normal backup/cachebuster/reinstall workflow; existing marketplace entry unchanged.
- Installed verifier passed after a scoped Windows CIM permission retry: bundle byte match, collection pagination, preserved completeness flags, six MCP tools, `game_phase=stopped`, `gameplay_actions=0`.
- No commit/push, gameplay, save migration, or live Collector acceptance performed. Whole-state transport size and upstream capture cost remain limitations; pagination does not make upstream capture lazy. Use a new Codex task to pick up the updated plugin schema.
