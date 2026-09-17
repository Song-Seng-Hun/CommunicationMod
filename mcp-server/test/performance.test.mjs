import test from 'node:test';
import assert from 'node:assert/strict';
import * as current from '../dist/context.js';
import {performanceState,countedArray} from '../evaluation/performance-fixtures.mjs';

const bytes=value=>Buffer.byteLength(JSON.stringify(value),'utf8');

test('three-row array page reads only three entries and retains absolute cursor',()=>{
 const s=performanceState(),cards=countedArray(s.observation.game_state.mechanics.collection.cards);
 s.observation.game_state.mechanics.collection.cards=cards.array;
 const page=current.readContext(s,['collection/cards'],9990,3).fragments[0];
 assert.equal(cards.reads,3);assert.equal(page.total,10000);assert.equal(page.next_offset,9993);
 assert.deepEqual(page.toc.map(row=>row.ref),['collection/cards/9990','collection/cards/9991','collection/cards/9992']);
 current.readContext(s,['collection/cards'],10000,3);assert.equal(cards.reads,3,'Exhausted page reads no entries');
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
  assert.equal(entries.reads,0);assert.equal(unrelated,0);assert.equal(result.screen,screen);
  assert.equal(result.map_plan.status,screen==='MAP'?'ready':'unavailable');
  assert.equal(result.session_id,undefined);assert.equal(result.state_id,undefined);
 }
});

test('fragment traversal is bounded, paged and rejects unsafe paths without mutation',()=>{
 const s=performanceState(135,'EVENT'),g=s.observation.game_state;
 g.screen_state={body_text:'\u0000한글🔥'.repeat(1000),normal:7,nested:{'a/b%':{message:'그대로'}}};
 const before=JSON.stringify(s);
 const page=current.readContext(s,['collection/cards'],130,30).fragments[0];assert.equal(page.toc.length,5);assert.equal(page.next_offset,undefined);
 let recovered='',offset=0;do{const p=current.readContext(s,['screen/body_text'],offset).fragments[0];recovered+=p.text;offset=p.next_offset??null;assert.ok(bytes(p)<=2400);}while(offset!==null);
 assert.equal(recovered,g.screen_state.body_text);
 for(const ref of ['full','screen/__proto__','screen/constructor','screen/%ZZ','screen/body_text/length','collection/cards/00','collection/cards/-1','collection/cards/99999'])assert.throws(()=>current.readContext(s,[ref]),/Reference not available/);
 assert.equal(JSON.stringify(s),before,'Projection must not mutate input state');
});

test('large default decisions remain comfortably below Antigravity inline limit',()=>{
 const s=performanceState(10000,'NONE');
 s.actions=Array.from({length:12},(_,i)=>({id:`run.play.${i}`,label:`카드 ${i}`,parameters:{}}));
 s.observation.game_state.combat_state={hand_complete:true,player:{energy:3,block:7},hand:Array.from({length:10},(_,i)=>({name:`카드 ${i}`,cost:i%4,type:i%2?'SKILL':'ATTACK',is_playable:true,description:'피해 6.'})),monsters:Array.from({length:5},(_,i)=>({name:`적 ${i}`,current_hp:20,intent:'ATTACK',move_adjusted_damage:6}))};
 const view=current.decision(s);assert.ok(bytes(view)<5000,`decision ${bytes(view)} bytes`);
 assert.equal(view.session_id,undefined);assert.equal(view.state_id,undefined);assert.equal(view.connection,undefined);assert.equal(view.ready,undefined);
});

test('hidden state identity invalidates view hash but is never model-visible',()=>{
 const s=performanceState(3,'NONE'),first=current.conditionalDecision(s);assert.ok(first.view_id);
 assert.equal(first.session_id,undefined);assert.equal(first.state_id,undefined);assert.deepEqual(current.conditionalDecision(s,first.view_id),{view_id:first.view_id,unchanged:true});
 const changed=structuredClone(s);changed.state_id++;assert.notEqual(current.conditionalDecision(changed).view_id,first.view_id);
 const restarted=structuredClone(s);restarted.session_id='new';assert.notEqual(current.conditionalDecision(restarted).view_id,first.view_id);
});

test('action summaries omit empty parameter/ref boilerplate but preserve real argument refs',()=>{
 const s=performanceState(1,'NONE');s.actions=[{id:'run.x',label:'선택',parameters:{}}];assert.deepEqual(current.decision(s).actions[0],{id:'run.x',label:'선택'});
 s.actions=[{id:'run.y',label:'대상',parameters:{target_index:1}}];const a=current.decision(s).actions[0];assert.equal(a.parameters_ref,'actions/0/parameters');assert.equal(current.readContext(s,[a.parameters_ref]).fragments[0].data.target_index,1);
});

test('multi-card reads share one bounded response budget',()=>{
 const s=performanceState(4,'NONE');s.observation.game_state.combat_state={hand_complete:true,hand:Array.from({length:4},(_,i)=>({id:`c${i}`,uuid:`u${i}`,name:`카드 ${i}`,cost:i,type:'SKILL',upgrades:2,is_playable:true,description:'긴 설명 '.repeat(500),tooltips:[{title:'키워드',description:'긴 툴팁 '.repeat(500)}]})),monsters:[],player:{energy:3}};
 const result=current.readContext(s,['hand/0','hand/1','hand/2','hand/3']);assert.ok(bytes(result)<4300,`context ${bytes(result)} bytes`);
 for(const [i,f] of result.fragments.entries()){assert.equal(f.data.cost,i);assert.equal(f.data.id,undefined);assert.equal(f.data.uuid,undefined);assert.equal(f.data.upgrades,undefined);}
});
