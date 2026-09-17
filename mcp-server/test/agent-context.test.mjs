import test from 'node:test';
import assert from 'node:assert/strict';
import {decision,conditionalDecision,readContext} from '../dist/context.js';

const state=()=>({session_id:'agent-summary',state_id:42,ready:true,connection:{status:'connected',pending_request_id:null},
 actions:[{id:'run.play.0',label:'카드 사용',parameters:{}}],
 observation:{game_state:{screen_type:'NONE',current_hp:20,max_hp:30,gold:75,deck:[],combat_state:{hand_complete:true,hand:[],monsters:[],player:{energy:3}}}}});
const withCard=card=>{const s=state();s.observation.game_state.deck=[card];s.observation.game_state.combat_state.hand=[card];return s;};

test('no-argument actions expose only executable identity and label',()=>{
 const s=state(),a=decision(s).actions[0];assert.deepEqual(a,{id:'run.play.0',label:'카드 사용'});
 assert.equal(JSON.stringify(a).includes('actions/0'),false);assert.equal(a.parameters,undefined);assert.equal(a.ref,undefined);
});

test('nonempty parameters retain one exact reachable reference',()=>{
 const s=state();s.actions=[{id:'run.play.target',label:'사용',parameters:{target_index:2,confirmation:{required:true}}}];
 const a=decision(s).actions[0];assert.equal(a.parameters_ref,'actions/0/parameters');assert.equal(a.parameters,undefined);
 assert.equal(readContext(s,[a.parameters_ref]).fragments[0].data.target_index,2);
 assert.equal(readContext(s,[a.parameters_ref+'/confirmation']).fragments[0].data.required,true);
});

test('single selected card keeps full facts without success boilerplate',()=>{
 const card={id:'a',uuid:'u',name:'카드+',cost:0,type:'SKILL',description:'방어도를 9 얻습니다.',tooltips:[{title:'방어도',description:'피해를 막습니다.'}],upgrades:1};
 const f=readContext(withCard(card),['hand/0']).fragments[0];assert.equal(f.ref,'hand/0');assert.deepEqual(f.data,card);
 for(const key of ['format','page_complete','next_offset','details_required'])assert.equal(f[key],undefined,key);
});

test('multi-card reads summarize comparison facts and stay inline',()=>{
 const s=state();s.observation.game_state.combat_state.hand=Array.from({length:4},(_,i)=>({id:'c'+i,uuid:'u'+i,name:'카드 '+i,cost:i,type:'SKILL',upgrades:2,is_playable:true,
  displayed_cost_complete:true,cost_components_complete:true,exhausts:false,ethereal:false,description:'긴 설명 '.repeat(300),tooltips:[{title:'키워드',description:'설명 '.repeat(300)}]}));
 const result=readContext(s,['hand/0','hand/1','hand/2','hand/3']);assert.ok(Buffer.byteLength(JSON.stringify(result),'utf8')<4300);
 for(const [i,f] of result.fragments.entries()){
  assert.equal(f.data.name,'카드 '+i);assert.equal(f.data.cost,i);assert.equal(f.details_required,true);
  for(const key of ['id','uuid','upgrades','displayed_cost_complete','cost_components_complete','exhausts','ethereal'])assert.equal(f.data[key],undefined,key);
 }
});

test('default combat summary is flat and decision-critical',()=>{
 const s=withCard({id:'a',uuid:'u',name:'스네코 카드',cost:0,type:'ATTACK',is_playable:true,description:'피해를 줍니다.',upgrades:3});
 const c=decision(s).combat.hand[0];assert.equal(c.ref,'hand/0');assert.equal(c.name,'스네코 카드');assert.equal(c.cost,0);assert.equal(c.type,'ATTACK');
 assert.equal(c.data,undefined);assert.equal(c.id,undefined);assert.equal(c.uuid,undefined);assert.equal(c.upgrades,undefined);
});

test('healthy transport metadata stays internal while hidden identity still invalidates view hash',()=>{
 const s=state(),v=conditionalDecision(s);for(const key of ['session_id','state_id','connection','ready'])assert.equal(v[key],undefined,key);
 assert.ok(v.view_id);assert.deepEqual(conditionalDecision(s,v.view_id),{view_id:v.view_id,unchanged:true});
 const next=structuredClone(s);next.state_id++;assert.notEqual(conditionalDecision(next).view_id,v.view_id);
 const restarted=structuredClone(s);restarted.session_id='new';assert.notEqual(conditionalDecision(restarted).view_id,v.view_id);
});

test('abnormal transport state is visible only as actionable exception metadata',()=>{
 const s=state();s.ready=false;s.connection.status='disconnected_or_stale';s.connection.pending_request_id='opaque-internal-id';
 const v=decision(s);assert.equal(v.ready,false);assert.deepEqual(v.connection,{status:'disconnected_or_stale',pending:true});assert.ok(!JSON.stringify(v).includes('opaque-internal-id'));
});
