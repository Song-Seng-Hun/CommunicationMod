# Replacement-session recovery repair

User authorized fixing the P2 recovery defect reported by the October 11 re-audit. If an action was uncertain when the game disconnected, reconnecting the same MCP server to a new native session previously left the old pending lock in place. Every new action was rejected Busy, including after refreshing the displayed state.

The existing GameSession receive path now retires only an obsolete local lock when an authenticated replacement state provides a nonempty different session ID and a valid state ID. Old request IDs remain consumed, old unknown outcomes remain unknown, and existing receipts are preserved. A receipt alone does not become proof of the old action's completion. Reconnecting to the same session, malformed identity, and a pending action reported by the new bridge do not bypass single-flight protection. No automatic replay or native dispatch change is introduced.

Verification completed:

- Regression first: focused existing test files produced 7 passes and 4 failures against the old code; after repair, 11/11 passed. Four session cases protect distinct lock/history boundaries and one real SDK stdio case verifies Connection/GameSession/tool registration together. No assertions were weakened or skipped.
- Independent exact-diff review approved the recovery behavior, unknown/applied_waiting outcomes, retained receipts/request IDs, remote pending and stale-pin boundaries.
- Required package workflow passed 219/219 MCP tests, launcher contracts and isolated installer scenarios. Log: `target/reconnect-package.log`.
- Installed version `0.1.0+codex.20261011121924.917f4b16`, reviewed contract v15. All 3,686 installed files match the package, all module digests verify, and all six MCP tools register.
- Installed `scripts/start.mjs` was tested through actual MCP stdio against a synthetic local backend. An old unknown command remained unknown, reconnecting to a new session allowed exactly one new selected command, and the old command was never replayed. This is installed-entrypoint verification, not live gameplay.
- Fresh guards/backups preserved 559 baseline source files except five intended existing-file changes, plus this new report. No missing or unexpected source changes. No duplicate legacy execution path was added.

Task and evidence: `C:/Users/meleb/OneDrive/문서/ChatGPT/stsmod/RECONNECT-TASKS.md`, `reconnect-installed-verification.json`, `reconnect-source-preservation.json`. The previous failing reproduction remains unchanged in `reaudit-reconnect-stdio.mjs/.json`; repair evidence uses new filenames.

The prepared game was stopped before installation; no game was launched or controlled. The gameplay process, saves, Steam install and Gemini settings were not modified. Gemini's existing MCP server must reconnect once to load the updated on-disk code; no existing process was forcibly restarted. The lower-priority start-response consistency finding was not changed.
