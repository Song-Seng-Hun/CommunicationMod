# Compact Context Implementation Plan

> Execution: inline in the existing user-owned dirty feature checkout; independent read-only review before completion. Preserve unrelated changes. The user approved the preceding selective-format design and implementation.

**Goal:** Reduce context-read tokens without losing facts or breaking existing callers.

**Architecture:** Optional compact response at the MCP boundary; unchanged context projection and action pipeline. Official TOON codec, exact roundtrip gate, lazy tokenizer and JSON fallback.

**Tech Stack:** Existing TypeScript/MCP SDK, @toon-format/toon 4.1.1, js-tiktoken 1.0.21.

## Tasks

- [x] Add `test/format.test.mjs`: legacy shape, compact rows, fallback, roundtrip edge cases and workflow invariants. Add real SDK compact assertions to `test/mcp.test.mjs`; run before implementation to observe rejection of the new field.
- [x] Add `src/format.ts` with `formatContext(data, format = 'json')`; return legacy dual response by default. For compact, return JSON or labeled TOON in text only. Compare decoded JSON with original JSON and count complete candidate text plus text-block representation before selection.
- [x] Integrate only `sts_get_context` via optional `response_format` enum. All other tool schemas and guards unchanged. Update prose regression to explicitly allow this one new field and separately account for format guidance.
- [x] Add `scripts/measure-context-format.mjs`: prior catalog reconstructed by removing only the new optional field, actual new catalog, workflow calls, a 20-row directory case, request overhead, text/wire totals, conversion timing and roundtrip assertions. Generate ignored report in `target`.
- [x] Run `npm test`, `node scripts/measure-context-format.mjs`, independent read-only review and interpretation smoke check; resolve findings with failing tests before fixes.
- [x] Update README and context economy documentation with usage, compatibility, actual measurements and limitations. Do not deploy, launch game, commit or push.

Commands run from `mcp-server`. Example opt-in: `{"session_id":"<observed>","state_id":42,"refs":["collection/cards"],"response_format":"compact"}`. Omit response_format or use `json` for exact legacy structured results.

## Execution evidence

- Before implementation, real SDK compact read failed at `Explicit compact format must be accepted` because the old strict schema rejected it.
- Final `npm test`: 48 passed, 0 failed. Build passed. Scoped `git diff --check` passed; existing Windows permission/line-ending warnings are unrelated to this change.
- 20-row body: 407 -> 337 tokens. Complete selective 60-card browse, including all requests and catalog once: 2,836 -> 2,685. Indiscriminate compact on the older five small-read workflows grows text cost (5,277 -> 5,362); default remains JSON.
- Independent reviewer Peirce found no actionable issue. Blind readers Franklin (JSON) and Feynman (TOON) each answered 10/10; raw answers archived in `evaluation/format-reading-results.json`. This is bounded smoke evidence, not universal comprehension.
- New packages pinned; install scripts disabled. Installed plugin, original installs, saves and copied runtime untouched. No real gameplay, commit or push.
