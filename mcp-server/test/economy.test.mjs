import test from 'node:test';
import assert from 'node:assert/strict';
import {workflows} from '../evaluation/economy-workflows.mjs';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
test('economy workflows retain required combat/event/map/shop/collection facts',()=>{
 const cases=workflows();assert.equal(cases.length,5);
 for(const trace of cases){assert.ok(trace.before.length>=3);assert.ok(trace.after.length>=3);assert.equal(trace.after.at(-1).response.unchanged,true);}
});
test('tokenizer regression includes catalog, extra reads and no-poll totals',async()=>{
 await promisify(execFile)(process.execPath,['scripts/measure-context-economy.mjs'],{windowsHide:true,timeout:30000});
});
