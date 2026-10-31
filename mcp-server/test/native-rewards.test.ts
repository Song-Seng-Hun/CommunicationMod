import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {capabilities} from '../dist/guidance-catalog.js';
import {coverageFixtures} from '../evaluation/guidance-fixtures.ts';
import {fixtureScenario,assertActionEvidence} from '../evaluation/guidance-scenarios.ts';
import {readContext} from '../dist/context.js';

const inventory=JSON.parse(await readFile(new URL('../../devtools/native-rewards.json',import.meta.url),'utf8'));
test('every pinned native reward is joined to semantic guidance and a correctly typed fixture',()=>{
 const cap=capabilities.find(c=>c.id==='reward.take');
 assert.equal(new Set(inventory.rewards.map(r=>r.class)).size,inventory.rewards.length);
 for(const row of inventory.rewards){
  assert.ok(cap.covers.includes(row.cover),`missing native reward coverage: ${row.class}`);
  const fixture=coverageFixtures.find(f=>f.capability==='reward.take'&&f.cover===row.cover);assert.ok(fixture,row.class);
  const f=fixtureScenario(fixture),control=f.state.observation.reward_controls[0];
  assert.equal(f.state.observation.game_state.screen_type,'COMBAT_REWARD',row.class);
  assert.equal(control.kind,row.type.toLowerCase(),row.class);assert.equal(control.claimable,true);
  assert.doesNotThrow(()=>assertActionEvidence(f));
  if(row.kind==='card'){
   assert.deepEqual(control.native_reward,{choice_details_available_after_claim:true});
   assert.throws(()=>readContext(f.state,['screen/cards']),/not available/);
  }else if(['remove','transform','upgrade','boss_relic'].includes(row.kind)){
   assert.equal(control.native_reward.selection_operation,row.kind);assert.equal(control.native_reward.selection_count,1);
  }else if(row.kind==='gold')assert.equal(control.native_reward.amount,7);
  control.claimable=false;assert.throws(()=>assertActionEvidence(f),/reward/);
 }
});
test('reward examples separate opening native selection from executing a deck change',()=>{
 const normal=capabilities.find(c=>c.id==='reward.take').cases.find(c=>c.case==='normal');
 assert.equal(normal.calls.filter(c=>c.tool==='sts_act').length,1);
 for(const term of [/remove/i,/transform/i,/upgrade/i,/separate/i,/hidden/i,/gold/i])assert.match(normal.requires+' '+normal.expect,term);
 assert.match(normal.stop,/never retransmit/i);
});
