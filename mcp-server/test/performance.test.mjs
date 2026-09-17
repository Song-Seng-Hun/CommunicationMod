import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import * as current from '../dist/context.js';
import * as baseline from '../evaluation/baseline/context-v3.mjs';
import {performanceState,countedArray} from '../evaluation/performance-fixtures.mjs';
import {fixture} from '../evaluation/fixture.mjs';

const wire=run=>{try{return JSON.stringify(run());}catch(error){return JSON.stringify({error:error.name,message:error.message});}};
const provenance=new Set(['description_source','description_rendering','tooltips_source','tooltips_rendering','render_frame']);
// Derive only approved action/guidance deltas from the immutable old projection.
// Retain every other field (and its order), including unrelated omission markers.
function intendedDecision(state){
 const view=baseline.decision(state);
 view.actions=view.actions.map((old,i)=>{
  const action=state.actions[i],parameters=action?.parameters,summary={...old};
  if(parameters===null || typeof parameters!=='object' || Array.isArray(parameters))return summary;
  if(Object.keys(parameters).length){summary.parameters_ref=`actions/${i}/parameters`;return summary;}
  summary.parameters={};
  const otherOmission=Object.keys(action).some(key=>!provenance.has(key) && key!=='parameters'
   && (!['id','label'].includes(key) || !Object.hasOwn(old,key)));
  if(!otherOmission)delete summary.details_required;
  return summary;
 });
 // Guidance has separate state-filter/content/budget assertions in guidance tests.
 // Copy only the explicitly approved addition; every old field/order/error stays exact.
 const addition=current.decision(state);
 if(Object.hasOwn(addition,'guidance')){
  view.guidance=addition.guidance;
  view.toc.push({ref:'guidance',title:'Examples'});
 }
 return view;
}
const hash=(mode,view)=>createHash('sha256').update(mode+JSON.stringify(view)).digest('hex').slice(0,24);
function intendedConditional(state,knownView){
 const view=intendedDecision(state),view_id=hash('decision',view);
 if(knownView===view_id){
  const header=Object.fromEntries(['session_id','state_id','ready','connection'].filter(k=>k in view).map(k=>[k,view[k]]));
  return {...header,view_id,unchanged:true};
 }
 return {...view,view_id};
}
function same(name,...args){
 const expected=name==='decision'?()=>intendedDecision(...args)
  :name==='conditionalDecision' && (args[2]??'decision')==='decision'?()=>intendedConditional(...args)
  :()=>baseline[name](...args);
 // Do not catch assertions in wire(): matching thrown assertions are not parity.
 assert.equal(wire(()=>current[name](...args)),wire(expected),`${name}: ${JSON.stringify(args.slice(1))}`);
 if(name==='conditionalDecision'){
  const [state,,mode='decision']=args,full=current.conditionalDecision(state,undefined,mode);
  const {view_id,...view}=full;
  assert.equal(view_id,hash(mode,view),'Independently recompute the actual new view hash');
 }
}

test('three-row array page reads only three entries, retaining absolute cursors',()=>{
 const s=performanceState(),cards=countedArray(s.observation.game_state.mechanics.collection.cards);
 s.observation.game_state.mechanics.collection.cards=cards.array;
 const page=current.readContext(s,['collection/cards'],9990,3).fragments[0];
 assert.equal(cards.reads,3);assert.equal(page.total,10000);assert.equal(page.next_offset,9993);
 assert.deepEqual(page.toc.map(row=>row.ref),['collection/cards/9990','collection/cards/9991','collection/cards/9992']);
 current.readContext(s,['collection/cards'],10000,3);assert.equal(cards.reads,3,'An exhausted page reads no entries');
});

test('map-only polling does not inspect unrelated narrative, combat or controls',()=>{
 for(const screen of ['MAP','EVENT','NONE']){
  const s=performanceState(10000,screen),g=s.observation.game_state;
  const entries=countedArray(g.narrative.entries);g.narrative.entries=entries.array;
  let unrelated=0;
  for(const [owner,key] of [[g,'combat_state'],[g,'screen_state'],[s.observation,'tutorial']]){
   const value=owner[key];Object.defineProperty(owner,key,{enumerable:true,get(){unrelated++;return value;}});
  }
  const result=current.conditionalDecision(s,undefined,'map_plan');
  assert.equal(entries.reads,0);assert.equal(unrelated,0);
  assert.equal(result.screen,screen);assert.equal(result.map_plan.status,screen==='MAP'?'ready':'unavailable');
 }
});

test('array/object/text fragment JSON, errors, budgets and page boundaries match baseline',()=>{
 for(const length of [0,1,3,31,135,10000]){
  const s=performanceState(length),g=s.observation.game_state;
  // Empty arrays remain addressable below a nonempty collection root.
  g.mechanics.collection.cards=Array.from({length},(_,i)=>[null,false,17,'긴 문장 🔥'.repeat(100),{id:`c-${i}`,name:'x'.repeat(200),detail:{n:i}}][i%5]);
  for(const offset of [0,1,Math.max(0,length-1),length,length+1,Number.MAX_SAFE_INTEGER])for(const limit of [1,3,20,30])same('readContext',s,['collection/cards'],offset,limit);
 }
 const s=performanceState(32,'EVENT'),g=s.observation.game_state;
 g.screen_state={body_text:'\u0000한글🔥'.repeat(1000),description_source:'hidden',normal:7,['x'.repeat(600)]:{},nested:{'a/b%':{message:'그대로'}}};
 // JSON.parse preserves an own __proto__ field without changing the prototype.
 Object.assign(g.screen_state,JSON.parse('{"constructor":{},"prototype":{}}'));
 for(const refs of [['screen'],['screen/body_text'],['screen/nested/a%2Fb%25'],['screen','screen'],['deck'],['full'],['screen/__proto__'],['screen/constructor'],['screen/%ZZ'],['screen/body_text/length'],['collection/cards/00'],['collection/cards/-1'],['collection/cards/99999'],[]]){
  for(const offset of [0,1,20,1600,20000,-1,0.5])same('readContext',s,refs,offset,3);
 }
 for(const limit of [0,31,NaN])same('readContext',s,['screen'],0,limit);
});

test('decision parity permits only intended action deltas; map views and hashes remain exact',()=>{
 const screens=['MAP','EVENT','NONE','REST','SHOP_SCREEN','CARD_REWARD','GRID','HAND_SELECT','COMBAT_REWARD','BOSS_REWARD','VICTORY',''];
 for(const screen of screens)for(const plan of [null,{},[],false,0,'x',{revision:0,editing:false},{map_id:'m',revision:7,route:['1','2'],status:'x'.repeat(241),description_source:'skip'}]){
  const s=performanceState(3,screen);s.observation.game_state.map_plan=plan;
  for(const nativeScreen of [screen,'NONE']){
   s.observation.game_state.screen_type=nativeScreen;
   for(const mode of ['decision','map_plan']){
    const before=JSON.stringify(s);same('decision',s);same('conditionalDecision',s,undefined,mode);
    const known=baseline.conditionalDecision(s,undefined,mode).view_id;
    same('conditionalDecision',s,known,mode);same('conditionalDecision',s,'wrong',mode);
    same('conditionalDecision',s,current.conditionalDecision(s,undefined,mode).view_id,mode);
    assert.equal(JSON.stringify(s),before,'Projection must not mutate its input');
   }
  }
 }
 for(const s of [{},{ready:false},structuredClone(fixture),{observation:{menu:{screen:'MAP'}}},{observation:{game_state:{screen_type:'MAP',map_plan:[]}}}]){
  same('decision',s);for(const mode of ['decision','map_plan'])same('conditionalDecision',s,undefined,mode);
 }
 const s=performanceState(3),known=baseline.conditionalDecision(s,undefined,'map_plan').view_id;
 for(const mutate of [()=>{s.connection.age_ms++;},()=>{s.ready=false;},()=>{s.connection.pending_request_id='p';},()=>{s.connection.status='disconnected_or_stale';},()=>{s.state_id++;},()=>{s.session_id='new';},()=>{s.observation.game_state.map_plan.revision++;},()=>{s.observation.menu.screen='EVENT';},()=>{delete s.connection;}]){
  mutate();same('conditionalDecision',s,known,'map_plan');
 }
});

test('action parity normalization is limited to exact empty objects and nonempty object refs',()=>{
 for(const parameters of [undefined,null,[],[1],false,0,'',{}, {target_index:0}, {'a/b':{required:true}}]){
  for(const extra of [{},{label:'긴'.repeat(241)},{id:'x'.repeat(241)},{extra:null},{extra:{}},{description_source:'native'}, {render_frame:12}]){
   const s=performanceState(1),action={id:'run.play.0',label:'카드',...extra};
   if(parameters!==undefined)action.parameters=parameters;
   s.actions=[action];same('decision',s);same('conditionalDecision',s);same('conditionalDecision',s,undefined,'map_plan');
  }
 }
});

test('map-only polling preserves malformed action-ID errors and prefix short-circuits',()=>{
 const bad={id:{toString:null}},rest={id:'run.rest.sleep'},shop={id:'run.shop.buy'};
 for(const screen of ['MAP','REST','SHOP_SCREEN','EVENT'])for(const ready of [true,false]){
  for(const actions of [[bad],[rest,shop,bad],[rest,bad,shop],[shop,rest,bad],[shop,bad,rest]]){
   const s={ready,actions,observation:{menu:{screen}}};
   // These cases intentionally throw; retain the old exact error comparison.
   assert.equal(wire(()=>current.conditionalDecision(s,undefined,'map_plan')),wire(()=>baseline.conditionalDecision(s,undefined,'map_plan')));
  }
 }
});
