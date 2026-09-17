# Downfall Agent Plugin Implementation Plan

**Goal:** Deliver and install a personal Codex plugin that starts the verified local test game, waits for MCP, and exposes focused gameplay tools. Continue implementation directly as explicitly requested; no further design approval gate or subagents.

**Architecture:** Package the existing TypeScript MCP server, a Windows-only lifecycle adapter, a bootstrap, and a gameplay skill. Keep machine-specific workspace configuration and all game/save data outside plugin caches. Preserve the prepared runtime and Java 8 validation. No game JAR redistribution, Steam edits, online submissions, automatic gameplay or arbitrary shell tool.

**Tasks and acceptance:**

- [x] Add failing lifecycle/SDK tests; retain existing guard tests and validate actual repeated start + cross-process mutex. Run `npm test` in `mcp-server` and observe failures before implementation. An already connected competing MCP client is not forcibly disconnected; no new game is launched to bypass it.
- [x] Add `src/lifecycle.ts`, fixed `devtools/manage-plugin-game.ps1`, and `sts_start_game` / `sts_game_status` tools. Use a workspace-specific Windows named mutex across inspect/validate/spawn, exact Java command-line ownership, and existing launcher hash checks. Status returns phase, PID and useful error; start returns starting/connected without acting in game.
- [x] Add repository-owned `plugin/downfall-agent` manifest/bootstrap/skill. Use official plugin-creator scaffold for personal marketplace; bundle only tested MCP JS/runtime dependencies and the lifecycle helper. Bootstrap reads a separate machine config, never infers workspace from cache/cwd.
- [x] Create repeatable package/install scripts that preserve original files/settings, validate manifests/skills, install through `codex plugin add`, and leave saves outside cache. Run Node tests and PowerShell guard tests, then validate installed cache via actual SDK. Reproduce/fix filtered Windows PATHEXT environment failure before claiming live success.
- [x] Start actual test game through installed plugin, verify Korean main-menu state and repeated-start same PID. Do not resume or make gameplay choices during this launch test. Check test-only online block/hash evidence. Only after success, back up/remove exact legacy communicationmod MCP registration.
- [x] Record actual measured scope, installation/reload instructions, remaining gameplay limitations in `docs/CODEX-PLUGIN.md`. Publish this verified unit only to Song-Seng-Hun/CommunicationMod; no upstream PR.
