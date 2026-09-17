import test from 'node:test';
import assert from 'node:assert/strict';
import {decision,readContext} from '../dist/context.js';

const card=(uuid,cost=1,extra={})=>({
 id:'Defend_R',uuid,name:'수비',type:'SKILL',rarity:'BASIC',upgrades:0,cost,
 description:'방어도를 5 얻습니다.',is_playable:true,has_target:false,exhausts:false,ethereal:false,
 displayed_cost_text:String(cost),displayed_cost_complete:true,cost_components_complete:true,
 hand_index:0,...extra
});
const state=cards=>({
 session_id:'dedupe',state_id:1,ready:true,actions:cards.map((c,i)=>({id:`run.play.${c.uuid}.-1`,label:c.name,parameters:{}})),
 observation:{game_state:{screen_type:'NONE',combat_state:{hand_complete:true,hand:cards,player:{energy:3},monsters:[]}}}
});

test('default hand collapses decision-equivalent copies but keeps one usable ref',()=>{
 const a=card('a'),b={...card('b'),hand_index:1},c={...card('c'),hand_index:2};
 const view=decision(state([a,b,c]));
 assert.equal(view.combat.hand.length,1);
 assert.equal(view.combat.hand[0].ref,'hand/0');
 assert.equal(view.combat.hand[0].copies,3);
 assert.equal(view.combat.hand[0].cost,1);
});

test('Snecko/current-cost and positional decision differences prevent collapsing',()=>{
 const a=card('a',0),b={...card('b',3),hand_index:1};
 const c={...card('c',0),hand_index:2,dead_on_position_active:true,dead_on_source:'hermit_isDeadOnPos_ui_predicate'};
 const view=decision(state([a,b,c]));
 assert.equal(view.combat.hand.length,3);
 assert.deepEqual(view.combat.hand.map(x=>x.cost),[0,3,0]);
 assert.ok(view.combat.hand.every(x=>x.copies===undefined));
});

test('dynamic observed card facts prevent accidental collapsing',()=>{
 const a=card('a',1,{displayed_values:{B:5}}),b={...card('b',1,{displayed_values:{B:8}}),hand_index:1};
 assert.equal(decision(state([a,b])).combat.hand.length,2);
});

test('context hand directory stays lossless and exposes every physical card ref',()=>{
 const cards=[card('a'),{...card('b'),hand_index:1},{...card('c'),hand_index:2}];
 const s=state(cards),page=readContext(s,['hand']).fragments[0];
 assert.equal(page.toc.length,3);
 assert.deepEqual(page.toc.map(x=>x.ref),['hand/0','hand/1','hand/2']);
 assert.deepEqual(page.toc.map(x=>x.cost),[1,1,1]);
});
