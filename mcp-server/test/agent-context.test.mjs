import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {decision,conditionalDecision,readContext} from '../dist/context.js';
import * as baseline from '../evaluation/baseline/context-v3.mjs';

const state=()=>({session_id:'agent-summary',state_id:42,ready:true,
 connection:{status:'connected',pending_request_id:null},
 actions:[{id:'run.play.0',label:'카드 사용',parameters:{}}],
 observation:{game_state:{screen_type:'NONE',deck:[],combat_state:{hand_complete:true,hand:[]}}}});
const selectedRefs=['hand/0','deck/0','screen/cards/0','collection/cards/0','combat_collection/cards/0','card_in_play'];
function withCard(card){
 const s=state(),g=s.observation.game_state;
 g.deck=[card];g.screen_state={cards:[card]};
 g.combat_state={hand_complete:true,hand:[card],card_in_play:card,
  player:{mechanics:{collection:{cards:[card]},combat_collection:{cards:[card]}}}};
 return s;
}
const atomic=(ref,data)=>({ref,format:'card',data,page_complete:true,next_offset:null});
function sizedCard(ref,length){
 const card={id:'dynamic-🔥',name:'변화하는 카드',description:'피해 12. 최대 HP 3 감소.\n"취소 불가"\u0000',
  displayed_cost_text:'X',displayed_cost_complete:false,cost_components_complete:false,
  cost_components:{energy:0,reserves:2,pyre:1},target_playability:[{target_index:0,playable:false,reason:'대상 면역 🔥'}],
  unplayable_reason:'관측 불완전: 사용 불가',tooltips:[{title:'소멸',description:'전투 중 재사용 불가'}],
  upgrade_preview:{status:'unavailable',reason:'not rendered',selected_after:{description:'방어도 9',safety:{complete:false,unknown:['동적 비용']}}},padding:''};
 const remaining=length-JSON.stringify(atomic(ref,card)).length;
 assert.ok(remaining>=0);card.padding='가'.repeat(remaining);
 assert.equal(JSON.stringify(atomic(ref,card)).length,length);
 return card;
}

test('exact empty parameters are explicit without implying omitted action details',()=>{
 const s=state();
 assert.deepEqual(decision(s).actions,[{ref:'actions/0',id:'run.play.0',label:'카드 사용',parameters:{}}]);
 assert.deepEqual(Object.keys(readContext(s,['actions/0/parameters']).fragments[0].data),[]);
 assert.deepEqual(s.actions[0].parameters,{});
});

test('empty parameters retain details_required for every other omission',()=>{
 for(const extra of [{label:'긴'.repeat(241)},{id:'x'.repeat(241)},{safety:{allowed:false}},
  {extra:0},{extra:null},{extra:''},{extra:[]},{extra:{}},{label:{text:'native'}}]){
  const s=state();Object.assign(s.actions[0],extra);
  const summary=decision(s).actions[0];
  assert.deepEqual(summary.parameters,{});assert.equal(summary.details_required,true,JSON.stringify(extra));
 }
 const s=state();Object.assign(s.actions[0],{description_source:'native',render_frame:7});
 assert.deepEqual(decision(s).actions[0],{ref:'actions/0',id:'run.play.0',label:'카드 사용',parameters:{}});
});

test('nonempty object parameters have an exact reachable reference and retain omission markers',()=>{
 const s=state();s.actions=Array.from({length:13},(_,i)=>({id:`action-${i}`,label:'선택',parameters:{target_index:i,confirmation:{required:true}}}));
 const summary=decision(s);assert.equal(summary.actions.length,12);assert.equal(summary.actions_more,'actions');
 for(const [i,action] of summary.actions.entries()){
  assert.equal(action.parameters_ref,`actions/${i}/parameters`);assert.equal(action.details_required,true);
  assert.equal(Object.hasOwn(action,'parameters'),false);
  assert.equal(readContext(s,[action.parameters_ref]).fragments[0].data.target_index,i);
  assert.equal(readContext(s,[`${action.parameters_ref}/confirmation`]).fragments[0].data.required,true);
 }
});

test('malformed, null, array and missing parameters gain neither arguments nor invented refs',()=>{
 for(const parameters of [undefined,null,[],[{}],false,0,'','malformed']){
  const s=state();if(parameters===undefined)delete s.actions[0].parameters;else s.actions[0].parameters=parameters;
  const summary=decision(s).actions[0];
  assert.equal(Object.hasOwn(summary,'parameters'),false);assert.equal(Object.hasOwn(summary,'parameters_ref'),false);
  assert.deepEqual(summary,baseline.decision(s).actions[0]);
 }
});

for(const ref of selectedRefs)for(const length of [2399,2400,2401]){
 test(`selected ${ref}: entire serialized fragment boundary ${length}`,()=>{
  const card=sizedCard(ref,length),s=withCard(card),before=JSON.stringify(s);
  const result=readContext(s,[ref]).fragments[0];
  if(length<=2400){assert.deepEqual(result,atomic(ref,card));assert.equal(JSON.stringify(result).length,length);}
  else {assert.notEqual(result.format,'card');assert.deepEqual(result,baseline.readContext(s,[ref]).fragments[0]);}
  assert.equal(JSON.stringify(s),before,'Native raw state must remain unchanged');
 });
}

test('recursive provenance removal precedes the full fragment budget check',()=>{
 const card=sizedCard('deck/0',2400),raw=structuredClone(card);
 raw.description_source='native'.repeat(1000);raw.description_rendering={text:'x'.repeat(2000)};
 raw.tooltips[0].tooltips_source='native';raw.tooltips[0].tooltips_rendering='rendered';
 raw.upgrade_preview.selected_after.safety.render_frame=99;
 const s=withCard(raw);s.original_raw_state=structuredClone(s.observation);const before=structuredClone(s);
 assert.deepEqual(readContext(s,['deck/0']).fragments[0],atomic('deck/0',card));
 assert.deepEqual(s,before,'Do not clean observation or original_raw_state in place');
});

test('oversize nested records bound traversal and array allocations with limit one',()=>{
 const records=Array.from({length:100000},(_,id)=>({id,safety:{playable:false,reason:'관측 불완전'}}));
 const card={id:'oversize',upgrade_preview:{records}},s=withCard(card);
 const expected=baseline.readContext(s,['deck/0'],0,1);
 let reads=0;const allocations=[];
 const constructor={[Symbol.species]:class extends Array {
  constructor(length){super(length);allocations.push(length);}
 }};
 card.upgrade_preview.records=new Proxy(records,{get(target,key,receiver){
  if(typeof key==='string' && /^(0|[1-9]\d*)$/.test(key))reads++;
  if(key==='constructor')return constructor;
  return Reflect.get(target,key,receiver);
 }});
 const result=readContext(s,['deck/0'],0,1);
 assert.deepEqual(result,expected,'Oversize fallback must retain the exact old first page');
 assert.equal(reads,0,'The array length alone rules out 100000 records before any record read');
 assert.ok(allocations.every(length=>length<=1200),`Unbounded array-copy allocations: ${allocations}`);
 assert.equal(card.upgrade_preview.records.length,100000);
});

test('budget exhaustion stops later nested field reads and skips provenance values',()=>{
 let tailReads=0,provenanceReads=0;
 const oversized={description:'🔥'.repeat(2400),get tail(){tailReads++;return {safety:false};}};
 const s=withCard({id:'oversize',upgrade_preview:oversized});
 const expected=baseline.readContext(s,['deck/0'],0,1);tailReads=0;
 assert.deepEqual(readContext(s,['deck/0'],0,1),expected);
 assert.equal(tailReads,0,'Do not eagerly materialize entries after the budget is exhausted');
 const card=sizedCard('deck/0',2400);
 Object.defineProperty(card.upgrade_preview,'description_source',{enumerable:true,get(){provenanceReads++;return 'ignored';}});
 const clean=sizedCard('deck/0',2400);
 assert.deepEqual(readContext(withCard(card),['deck/0']).fragments[0],atomic('deck/0',clean));
 assert.equal(provenanceReads,0,'Removed provenance contributes neither traversal nor budget');
});

test('budget accounting preserves JSON escaping, punctuation and deep facts at exact boundaries',()=>{
 for(const length of [2399,2400,2401]){
  const card={id:'accounting',facts:[null,true,false,0,-1,1.25,1e30,'\u0000\t\n"\\🔥\ud800',{},[]],
   nested:JSON.parse('{"__proto__":{"safe":false},"constructor":"native","9":9,"2":2}'),
   ['escaped\n"\\key']:{safety:{reason:'알 수 없음',description_source:'removed'}},padding:''};
  const clean=structuredClone(card);delete clean['escaped\n"\\key'].safety.description_source;
  clean.padding='x'.repeat(length-JSON.stringify(atomic('deck/0',clean)).length);card.padding=clean.padding;
  const s=withCard(card),result=readContext(s,['deck/0'],0,1);
  if(length<=2400){
   assert.deepEqual(result.fragments[0],atomic('deck/0',clean));
   assert.equal(JSON.stringify(result.fragments[0]).length,length);
  }else assert.deepEqual(result,baseline.readContext(s,['deck/0'],0,1));
 }
});

test('oversize cards retain exact old pages, child refs and unicode recovery',()=>{
 const card=sizedCard('deck/0',2401);card.description='\u0000한글🔥\\"\n'.repeat(500);
 card.description_source='native';card.upgrade_preview.description_source='native';
 const s=withCard(card);let offset=0;
 do{
  const result=readContext(s,['deck/0'],offset,3);
  assert.deepEqual(result,baseline.readContext(s,['deck/0'],offset,3));
  offset=result.fragments[0].next_offset;
 }while(offset!==null);
 let text='';offset=0;
 do{
  const result=readContext(s,['deck/0/description'],offset).fragments[0];
  assert.deepEqual(result,baseline.readContext(s,['deck/0/description'],offset).fragments[0]);
  text+=result.text;offset=result.next_offset;
 }while(offset!==null);
 assert.equal(text,card.description);
 assert.equal(readContext(s,['deck/0/upgrade_preview/selected_after/safety']).fragments[0].data.complete,false);
});

test('atomic selection preserves the existing ref regex and offset-zero scope',()=>{
 const s=withCard(sizedCard('deck/0',2400));
 for(const ref of selectedRefs)for(const offset of [1,3,100]){
  assert.deepEqual(readContext(s,[ref],offset,3),baseline.readContext(s,[ref],offset,3));
 }
 for(const ref of ['hand','deck','screen/cards','collection/cards','combat_collection/cards','deck/0/upgrade_preview',
  'deck/00','deck/%30','screen/cards/%30','deck/-1','deck/0/constructor','deck/0/__proto__']){
  const wire=api=>{try{return JSON.stringify(api.readContext(s,[ref]));}catch(e){return `${e.name}: ${e.message}`;}};
  assert.equal(wire({readContext}),wire(baseline),ref);
 }
 // The pre-existing shortcut accepts arrays as object values; do not widen or narrow it.
 for(const card of [null,false,7,'🔥',[{text:'native'}]]){
  const s=withCard(card);assert.deepEqual(readContext(s,['deck/0']),baseline.readContext(s,['deck/0']));
 }
});

test('no-argument actions do not replace dynamic card facts or relevant gameplay rules',()=>{
 const card=sizedCard('hand/0',2400),s=withCard(card),summary=decision(s);
 assert.deepEqual(summary.actions[0].parameters,{});assert.equal(summary.actions[0].details_required,undefined);
 assert.equal(summary.combat.hand[0].details_required,true);
 assert.equal(summary.combat.hand[0].displayed_cost_complete,false);
 assert.deepEqual(readContext(s,['hand/0']).fragments[0].data,card);
 assert.deepEqual(readContext(s,['rules']),baseline.readContext(s,['rules']));
 card.target_playability[0].reason='상태 변경: 더 이상 대상 지정 불가';
 card.cost_components.reserves=4;
 // Changes remain available even when the card now falls back to shallow pages.
 assert.equal(readContext(s,['hand/0/target_playability/0']).fragments[0].data.reason,card.target_playability[0].reason);
 assert.equal(readContext(s,['hand/0/cost_components']).fragments[0].data.reserves,4);
});

test('view IDs hash the new summary and retain ready/session/state/connection invalidation',()=>{
 const s=state(),before=structuredClone(s),view=conditionalDecision(s);
 const expected=createHash('sha256').update('decision'+JSON.stringify(decision(s))).digest('hex').slice(0,24);
 assert.equal(view.view_id,expected);assert.notEqual(view.view_id,baseline.conditionalDecision(s).view_id);
 assert.equal(conditionalDecision(s,view.view_id).unchanged,true);
 assert.equal(conditionalDecision(s,baseline.conditionalDecision(s).view_id).unchanged,undefined);
 assert.deepEqual(s,before);
 for(const change of [s=>{s.ready=false;},s=>{s.session_id='new';},s=>{s.state_id++;},
  s=>{s.connection.pending_request_id='pending';},s=>{s.connection.status='disconnected_or_stale';},
  s=>{s.actions[0].parameters={target_index:1};}]){
  const next=structuredClone(s);change(next);assert.equal(conditionalDecision(next,view.view_id).unchanged,undefined);
 }
 s.ready=false;assert.deepEqual(decision(s).actions,[]);assert.throws(()=>readContext(s,['actions/0/parameters']),/not available/);
});
