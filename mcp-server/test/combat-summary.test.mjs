import test from 'node:test';
import assert from 'node:assert/strict';
import {decision,readContext,conditionalDecision} from '../dist/context.js';

const card=()=>({id:'strike',name:'Strike',description:'Deal 6 damage.',is_playable:true,
 displayed_cost_text:'1',displayed_cost_complete:true,cost_components_complete:true,
 cost_components:[{resource:'energy',displayed_amount:1,kind:'fixed',free_to_play_once:false}],
 cost_components_scope:'pinned_native_payment_components_not_card_effects',
 target_playability:[{available:true,monster_index:0,name:'Slime'}]});
const state=(cards=[card()])=>({session_id:'fixture',state_id:1,ready:true,actions:[],
 observation:{game_state:{screen_type:'NONE',combat_state:{hand_complete:true,hand:cards}}}});

test('combat summary carries small exact payment and target facts without a detail read',()=>{
 const s=state(),before=JSON.stringify(s),c=s.observation.game_state.combat_state.hand[0],view=decision(s).combat.hand[0];
 for(const key of ['cost_components','cost_components_scope','target_playability'])assert.deepEqual(view[key],c[key]);
 assert.equal(view.details_required,true,'Other omitted facts still require inspection');
 assert.equal(JSON.stringify(s),before);
});
test('compact combat evidence preserves unknown, false, zero and unavailable reasons',()=>{
 const c=card();c.cost_components_complete=false;c.cost_components[0].displayed_amount=null;
 c.target_playability=[{available:false,monster_index:0,name:'Slime',reason:'immune'}];
 c.cost_components_unavailable_reason='unknown_provider';
 const s=state([c]),v=decision(s).combat.hand[0];
 assert.equal(v.cost_components_complete,false);assert.deepEqual(v.target_playability,c.target_playability);
 assert.equal(v.cost_components[0].displayed_amount,null);assert.equal(v.cost_components_unavailable_reason,'unknown_provider');
});
test('oversized or nested evidence stays fully reachable and is never partially inlined',()=>{
 const c=card();c.target_playability[0].reason='No action. '.repeat(90);
 const s=state([c]),v=decision(s).combat.hand[0];assert.equal(v.target_playability,undefined);assert.equal(v.cost_components,undefined);
 assert.deepEqual(readContext(s,[v.ref]).fragments[0].data.target_playability,c.target_playability);
 c.target_playability=[{available:false,detail:{information_complete:false}}];
 assert.equal(decision(s).combat.hand[0].target_playability,undefined);
});
test('evidence respects hand-wide budget and projection visibility',()=>{
 const s=state(Array.from({length:2},card)),v=decision(s);
 const extra=v.combat.hand.reduce((n,c)=>n+(c.cost_components?JSON.stringify({cost_components:c.cost_components,cost_components_scope:c.cost_components_scope,target_playability:c.target_playability}).length:0),0);
 assert.ok(extra>0&&extra<=768);
 assert.ok(decision(state(Array.from({length:10},card))).combat.hand.every(c=>c.cost_components===undefined),'Do not pay for early-card evidence when the complete hand does not fit');
 s.observation.game_state.combat_state.hand_complete=false;assert.deepEqual(decision(s).combat.hand,[]);
 Object.defineProperty(s.observation.game_state,'combat_state',{get(){throw Error('unrelated');}});
 assert.doesNotThrow(()=>conditionalDecision(s,undefined,'map_plan'));
});
test('huge target lists do not traverse rows and changed native evidence invalidates the view',()=>{
 const c=card(),s=state([c]),before=conditionalDecision(s).view_id;
 c.target_playability[0].available=false;assert.notEqual(conditionalDecision(s).view_id,before);
 const targets=new Array(1000000);Object.defineProperty(targets,0,{get(){throw Error('unbounded target read');}});c.target_playability=targets;
 assert.doesNotThrow(()=>decision(s));assert.equal(decision(s).combat.hand[0].target_playability,undefined);
});
