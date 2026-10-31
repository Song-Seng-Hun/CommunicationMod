import test from 'node:test';
import assert from 'node:assert/strict';
import {workflows} from '../evaluation/economy-workflows.ts';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
test('economy workflows retain required combat/event/map/shop/collection facts',()=>{
 const cases=workflows();assert.equal(cases.length,5);
 for(const trace of cases){assert.ok(trace.before.length>=3);assert.ok(trace.after.length>=3);assert.equal(trace.after.at(-1).response.unchanged,true);}
 for(const trace of cases)for(const step of trace.after){
  for(const key of ['session_id','state_id','known_view'])assert.equal(Object.hasOwn(step.request.arguments,key),false,`private request identity: ${key}`);
  for(const key of ['session_id','state_id','view_id'])assert.equal(Object.hasOwn(step.response,key),false,`private response identity: ${key}`);
 }
 const event=cases.find(x=>x.name==='event_read_exact_story'),body=event.after.filter(x=>x.request.arguments.refs?.includes('screen/event_reading/body_text'));
 assert.equal(body.map(x=>x.response.fragments[0].text).join(''),event.state.observation.game_state.screen_state.event_reading.body_text);
 assert.equal(body.length,1,'fixture body fits one exact text response');assert.equal(body.at(-1).response.fragments[0].next_offset,undefined);
 const shop=cases.find(x=>x.name==='shop_price_upgrade_comparison');
 assert.ok(shop.after.some(x=>x.request.arguments.refs?.includes('shop_controls/0')),'on-demand price retrieval is counted');
});
test('tokenizer regression includes catalog, extra reads and no-poll totals',async()=>{
 await promisify(execFile)(process.execPath,['scripts/measure-context-economy.ts'],{windowsHide:true,timeout:30000});
});
