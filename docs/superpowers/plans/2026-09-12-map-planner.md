# Map Ink and Semantic Route Planner Implementation Plan

> **For agentic workers:** Use executing-plans in this checkout, sequentially; preserve the existing uncommitted map compatibility work. No worker fan-out.

**Goal:** Let the human draw on the map and let both human and MCP plan a node-to-node route without moving the character.

**Architecture:** A bounded JDK-only annotation model owns ink, an ordered semantic route, undo and revision validation. A game-thread UI adapter renders the overlay and consumes only editor input. The existing v2 action protocol edits routes; focused MCP observations expose a compact route/current-position summary, never ink points.

**Tech Stack:** Java 8, existing LibGDX SpriteBatch, ModTheSpire patches and copied-runtime Javassist hooks, TypeScript MCP view.

## Approved scope and defaults

- Human: feather pen toggle, freehand drawing, node route tool, stroke eraser, undo and clear. Mouse wheel remains available; map navigation is withheld while editing.
- Agent: one offered `run.map.plan` action atomically replaces an ordered node-ID list (empty clears the route). Revision and map identity are mandatory. This is annotation only, never a movement command.
- Distinguish `route`, actual `current_node`, `next_planned_node`, and off-route state. Do not infer route intent from pixels. Connected public map edges are validated; no future movement/resource guarantee.
- Ink is stored in map coordinates, independent of scroll and display scale. Route segments attach to native node hitboxes. Reverse maps use their existing graph and rendered positions, not reversed coordinate guesses.
- First version is session-only: closing/reopening the map retains ink; changing map/act/run clears it. No save-file writes or tunnel changes. The existing local plugin server bundle must be refreshed to expose the new query schema.
- Bound strokes, samples, route length and undo history. Raw ink is never in game observations. Compact summaries include a revision and stroke count; full route details are on demand.

## Task 1: Pure annotation model

Files: `src/main/java/communicationmod/map/MapAnnotations.java`, `devtools/MapAnnotationsTest.java`, `devtools/verify-map-planner.ps1`.

- [x] Add a reflection-based failing test asserting `MapAnnotations` exists, then compile/run with the bundled Java 8.
- [x] Implement point transforms, bounded strokes, segment-distance erasing, undo, atomic route validation, stale revision rejection and map reset.
- [x] Test reverse graph fixtures, invalid/disconnected/duplicate routes, unchanged revision for no-ops, scroll/scale round trips, undo and ink bounds.

Contract examples:
```java
MapAnnotations model = new MapAnnotations();
model.reset("act-map-1");
model.setRoute(Arrays.asList("1,14", "2,13"), "mcp", model.revision(), knownNode, connected);
// setRoute validates the whole candidate before changing route/revision.
// commitStroke accepts map-space points, not raw screen pixels.
```

## Task 2: UI input/render and integration

Files: `src/main/java/communicationmod/map/MapDrawing.java`, `src/main/java/communicationmod/patches/MapDrawingPatch.java`, `devtools/BuildLocalObserver.java`, `devtools/MapPlannerBindingTest.java`, `devtools/LocalObserverBuildTest.java`.

- [x] Add failing binding checks for update/render hooks and map planning action.
- [x] Render toolbar/feather and ink with existing white texture/font; restore SpriteBatch color. Hide overlay behind popups and outside MAP.
- [x] Update editor before native map input; suppress click/drag only while editing/toolbar gesture, preserve wheel scrolling and normal controls when off. Refuse edit entry during pending travel; disable controller selection while editor owns the map.
- [x] Add guarded native node/boss hitbox handling so drawing cannot enter a room. Add equivalent copied-runtime hooks without repatching all of Downfall.
- [x] Add v2 route action and compact game-state observation. Existing session/state checks stay in force; edits reject changed annotation revision, changed map and human gesture ownership.

## Task 3: Low-cost MCP readout

Files: `mcp-server/src/view.ts`, `mcp-server/src/index.ts`, `mcp-server/test/view.test.mjs`.

- [x] Add failing focused-view test: compact plan/current/next/author/revision survives; raw points and long route do not leak into default view.
- [x] Expose `map_plan` context and compact top-level summary, plus `sts_get_state(view="map_plan")` for bounded map-only polling. Add stable node IDs to map context.
- [x] Run `npm test` in `mcp-server` and document an exact `sts_act` argument example.

## Task 4: Verification and handoff

- [x] Run `devtools/verify-map-planner.ps1` after a fresh Maven build.
- [x] Run `devtools/prepare-local-test.ps1` sequentially with bounded heaps; retain original installs, prior test runtimes and saves.
- [x] Review input ownership, atomic updates, bounded memory and observation cost directly (no subagent); input guards do not mutate global input flags.
- [x] Record tested boundaries and instructions in `docs/MAP-PLANNER.md`; do not claim live visual acceptance from headless tests. No automatic game launch, save migration, commit or push in this feature task.
