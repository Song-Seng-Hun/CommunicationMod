import test from 'node:test';
import assert from 'node:assert/strict';
import {decision,readContext,conditionalDecision} from '../dist/context.js';

const potion=(slot,id,name=id)=>({slot,id,name,can_use:false,can_discard:true,requires_target:false});
const empty=slot=>({...potion(slot,'Potion Slot','포션 슬롯'),can_discard:false});
const state=potions=>({session_id:'potions',state_id:1,ready:true,actions:[],observation:{game_state:{screen_type:'NONE',potions}}});
const counts=v=>Object.fromEntries(['capacity','held_count','empty_slots','full'].map(k=>[k,v[k]]));

test('two held potions and one native empty slot stay distinct in the decision and paged context',()=>{
 const s=state([potion(0,'Ancient Potion','고대 포션'),empty(1),potion(2,'SpeedPotion','속도 포션')]),before=JSON.stringify(s);
 const v=decision(s).player.potion_inventory,expected={capacity:3,held_count:2,empty_slots:1,full:false};
 assert.deepEqual(counts(v),expected);assert.equal(v.ref,'player/potions');
 assert.deepEqual(v.slots.map(p=>[p.slot,p.empty,p.ref]),[[0,false,'player/potions/0'],[1,true,'player/potions/1'],[2,false,'player/potions/2']]);
 assert.equal(v.slots[2].name,'속도 포션');assert.equal(v.slots[2].can_use,false);
 const directory=readContext(s,['player']).fragments[0].toc.find(v=>v.ref==='player/potions');
 assert.equal(directory.count,3);assert.equal(directory.count_kind,'slots');assert.deepEqual(counts(directory),expected);
 const page=readContext(s,['player/potions'],1,1).fragments[0];
 assert.equal(page.total,3);assert.equal(page.total_kind,'slots');assert.deepEqual(counts(page),expected);assert.equal(page.next_offset,2);
 assert.equal(page.toc[0].ref,'player/potions/1');assert.equal(page.toc[0].empty,true);
 const leaf=readContext(s,['player/potions/2']).fragments[0].data;assert.equal(leaf.id,'SpeedPotion');assert.equal(leaf.slot,2);assert.equal(leaf.empty,false);
 assert.equal(JSON.stringify(s),before,'projection must not change native observations');
});

test('actual capacity, not a fixed three or usability/reward eligibility, determines fullness',()=>{
 const s=state([potion(0,'Ancient Potion'),potion(1,'SpeedPotion')]);
 assert.deepEqual(counts(decision(s).player.potion_inventory),{capacity:2,held_count:2,empty_slots:0,full:true});
 s.observation.game_state.potions.push(empty(2));s.observation.game_state.relics=[{id:'Sozu',name:'Sozu'}];
 s.observation.reward_controls=[{claimable:false,reason:'potion_slots_full_or_sozu'}];
 assert.deepEqual(counts(decision(s).player.potion_inventory),{capacity:3,held_count:2,empty_slots:1,full:false});
 assert.deepEqual(counts(decision(state([empty(0),empty(1),empty(2)])).player.potion_inventory),{capacity:3,held_count:0,empty_slots:3,full:false});
 const zero={capacity:0,held_count:0,empty_slots:0,full:true};assert.deepEqual(counts(decision(state([])).player.potion_inventory),zero);
 assert.deepEqual(counts(readContext(state([]),['player/potions']).fragments[0]),zero);
 assert.deepEqual(counts(readContext(s,['player/potions'],3).fragments[0]),{capacity:3,held_count:2,empty_slots:1,full:false});
});

test('unavailable and unidentified inventory never becomes an invented zero or occupied slot',()=>{
 for(const value of [undefined,null,{}])assert.equal(decision(state(value)).player?.potion_inventory,undefined);
 const v=decision(state([empty(0),{slot:1,name:'unknown',can_use:false}])).player.potion_inventory;
 assert.deepEqual(counts(v),{capacity:2,held_count:null,empty_slots:null,full:null});assert.equal(v.information_complete,false);
 assert.equal(v.slots[1].empty,undefined);
});

test('fresh snapshots retain unusable potions and invalidate reuse when an occupied slot becomes empty',()=>{
 const s=state([potion(0,'Ancient Potion'),empty(1)]),first=conditionalDecision(s);
 s.state_id++;const stillHeld=conditionalDecision(s,first.view_id);
 assert.equal(stillHeld.player.potion_inventory.held_count,1);assert.equal(stillHeld.player.potion_inventory.slots[0].id,'Ancient Potion');
 s.observation.game_state.potions[0]=empty(0);const consumed=conditionalDecision(s,stillHeld.view_id);
 assert.equal(consumed.unchanged,undefined);assert.equal(consumed.player.potion_inventory.held_count,0);assert.equal(consumed.player.potion_inventory.slots[0].empty,true);
 assert.equal(conditionalDecision(s,consumed.view_id).unchanged,true);
});

test('large slot lists remain bounded and keep full counts and exact physical refs',()=>{
 const s=state(Array.from({length:40},(_,i)=>({...potion(i,'potion-'+i,'이름'.repeat(100)),description:'현재 효과 '.repeat(100),description_complete:false,tooltips:[{title:'규칙',description:'추가 규칙 '.repeat(100)}],tooltips_complete:false,tooltips_truncated:true})));
 const v=decision(s).player.potion_inventory;assert.equal(v.capacity,40);assert.equal(v.held_count,40);assert.ok(v.slots.length<=6);assert.equal(v.slots_more,'player/potions');
 const page=readContext(s,['player/potions'],39,1).fragments[0];assert.equal(page.toc[0].ref,'player/potions/39');assert.equal(page.held_count,40);
 assert.equal(page.toc[0].details_required,true);assert.equal(page.toc[0].description_complete,false);assert.equal(page.toc[0].tooltips_complete,false);assert.equal(page.toc[0].tooltips_truncated,true);
 assert.ok(Buffer.byteLength(JSON.stringify(decision(s)))<4400);
});

// Catches missing default main effects and undiscoverable cached additional rules.
test('custom potion effects and current cached public rules are visible through summary and exact refs',()=>{
 const tip=(title,description)=>({title,description,description_complete:true,description_truncated:false,source:'potion_cache'});
 const held={...potion(0,'collector:ReservePotion','비축 물약'),description:'비축을 2 얻습니다.',description_complete:true,
  tooltips:[tip('비축 물약','비축을 2 얻습니다.'),tip('비축','사용하지 않은 비축은 다음 턴으로 유지됩니다.')],tooltips_complete:true,tooltips_truncated:false};
 const s=state([held,empty(1)]),before=JSON.stringify(s),inventory=decision(s).player.potion_inventory,summary=inventory.slots[0];
 assert.equal(summary.description,held.description);assert.equal(summary.tooltip_count,2);assert.equal(summary.tooltips_more,'player/potions/0/tooltips');assert.equal(summary.details_required,true);
 assert.equal(summary.can_use,false);assert.equal(summary.can_discard,true);assert.equal(summary.requires_target,false);
 assert.equal(inventory.slots[1].details_required,undefined,'native empty slots have no missing effect');
 const row=readContext(s,['player/potions'],0,1).fragments[0].toc[0];assert.equal(row.description,held.description);assert.equal(row.tooltips_more,summary.tooltips_more);
 const leaf=readContext(s,[summary.ref]).fragments[0];assert.equal(leaf.data.description,held.description);assert.ok(leaf.toc.some(t=>t.ref===summary.tooltips_more));
 const rules=readContext(s,[summary.tooltips_more]).fragments[0];assert.equal(rules.total,2);assert.equal(rules.toc[1].ref,'player/potions/0/tooltips/1');
 const rule=readContext(s,[rules.toc[1].ref]).fragments[0];assert.equal(rule.data.description,held.tooltips[1].description);
 assert.equal(JSON.stringify(s),before,'projection must not alter cached text or the native slot');
 s.state_id++;held.description='비축을 3 얻습니다.';held.tooltips[1].description='갱신된 현재 규칙입니다.';
 assert.equal(decision(s).player.potion_inventory.slots[0].description,held.description);
 assert.equal(readContext(s,[rules.toc[1].ref]).fragments[0].data.description,held.tooltips[1].description);
});

// Catches false completeness and dropped directory refs under the added prose load.
test('missing clipped or incomplete potion evidence keeps detail signals and budgeted current refs',()=>{
 const held={...potion(0,'audit:LongPotion','긴 이름 '.repeat(60)),description:'효과 '.repeat(700),description_complete:false,description_truncated:true,
  tooltips:Array.from({length:32},(_,i)=>({title:'규칙 '+i,description:'추가 효과 '.repeat(400),description_complete:true})),tooltips_complete:false,tooltips_truncated:true};
 const s=state([held,empty(1)]),summary=decision(s).player.potion_inventory.slots[0];
 assert.equal(summary.details_required,true);assert.equal(summary.name_truncated,true);assert.equal(summary.description_complete,false);assert.equal(summary.description_truncated,true);assert.equal(summary.tooltips_complete,false);assert.equal(summary.tooltips_truncated,true);
 assert.ok(summary.description.length<held.description.length);assert.equal(summary.tooltip_count,32);
 const refs=['player/potions','player','rules','player/potions/0','player/potions/0/tooltips','player/potions/0/description','player/potions/1','player/potions/0/tooltips/0'];
 const batch=readContext(s,refs),directory=batch.fragments[0];assert.equal(directory.toc[0].ref,summary.ref);assert.equal(directory.toc[0].details_required,true);assert.equal(directory.toc[0].description_complete,false);assert.equal(directory.toc[0].tooltips_truncated,true);
 assert.ok(batch.fragments.every(f=>Buffer.byteLength(JSON.stringify(f))<=450));
 let offset=0,text='';do{const page=readContext(s,[summary.ref+'/description'],offset).fragments[0];text+=page.text;offset=page.next_offset;}while(offset!==undefined);
 assert.equal(text,held.description);
 const unknown=decision(state([potion(0,'audit:OldSnapshot')])).player.potion_inventory.slots[0];assert.equal(unknown.description,undefined);assert.equal(unknown.details_required,true);assert.notEqual(unknown.tooltips_complete,true);
});
