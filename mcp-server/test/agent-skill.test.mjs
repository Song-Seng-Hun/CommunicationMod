import test from 'node:test';import assert from 'node:assert/strict';import {readFile} from 'node:fs/promises';
const text=await readFile(new URL('../../plugin/downfall-agent/skills/downfall-play/SKILL.md',import.meta.url),'utf8');
test('agent skill references actual event field and deterministic batch/format rules',()=>{
 assert.ok(!text.includes('event_reading_ref'));assert.ok(text.includes('event_reading.body_ref'));
 for(const term of ['parameters_ref','refs','offset','compact','JSON','sts_get_request','unknown','applied_waiting'])assert.ok(text.includes(term),term);
});
test('agent skill preserves launch, hidden information, event and replay safety',()=>{
 for(const term of ['do not launch','Never play from an unready or incomplete hand','Preserve hidden draw order','Do not acknowledge silently','never replay an uncertain action','stop at first death','no save editing/retry/reroll','never restart the game'])assert.ok(text.includes(term),term);
});
test('skill routes optional examples and keeps transport identity inside the server',()=>{
 for(const term of ['guidance','bindings','sts_game_status({})','sts_start_game({wait_ms:15000})','Do not send `$bind`','pins the exact state','do not invent or carry session/state IDs'])assert.ok(text.includes(term),term);
 assert.ok(!text.includes('matching session/state IDs'));
});
test('skill uses bounded event wait, never automatic heartbeat escalation',()=>{
 for(const term of ['known_view','wait_ms:15000','two consecutive','no automatic heartbeat'])assert.ok(text.includes(term),term);
});
