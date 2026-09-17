import test from 'node:test';
import assert from 'node:assert/strict';
const api=async()=>import('../dist/context.js').catch(()=>{throw new Error('Missing state-scoped fragment context API');});
export const state=(screen='EVENT')=>({session_id:'s',state_id:3,ready:true,connection:{status:'connected',age_ms:0,pending_request_id:null},actions:[{id:'run.event.0',label:'떠난다',parameters:{}}],observation:{game_state:{screen_type:screen,current_hp:20,max_hp:50,gold:75,deck:[{id:'a',name:'타격',description:'피해를 6 줍니다.',displayed_cost_complete:false}],screen_state:{body_text:'현재 이야기',options:[{text:'떠난다'}]},map:[{x:1,y:2}],map_plan:{revision:3,route:['1,2']},combat_state:{hand:[{id:'stale-card'}]},unclassified:{secret:'must-not-dump'}},shop_controls:[{id:'stale-shop'}]}});
test('only current roots are discoverable or readable, no full/extension escape',async()=>{
 const {decision,readContext}=await api(),s=state(),v=decision(s),refs=v.toc.map(x=>x.ref);
 assert.ok(refs.includes('screen'));assert.ok(refs.includes('deck'));
 for(const r of ['map','map_plan','hand','shop_controls','full','extensions']){
  assert.ok(!refs.includes(r),r);assert.throws(()=>readContext(s,[r]),/not available/);
 }
 assert.ok(!JSON.stringify(v).includes('must-not-dump'));assert.equal(v.unsupported_information,true);
 assert.equal(v.player.current_hp,20);assert.equal(v.actions[0].id,'run.event.0');
 assert.equal(v.available_sections,undefined);assert.equal(v.runtime,undefined);
});
test('fragments are shallow, batchable, bounded and exact long-text pages are recoverable',async()=>{
 const {readContext}=await api(),s=state();const long='긴 설명 🔥 '.repeat(2000);s.observation.game_state.screen_state.body_text=long;
 const root=readContext(s,['screen','deck']);assert.equal(root.fragments.length,2);
 assert.ok(root.fragments[0].toc.some(x=>x.ref==='screen/body_text'));assert.equal(root.fragments[0].data.body_text,undefined);
 const card=readContext(s,['deck/0']).fragments[0];assert.equal(card.data.displayed_cost_complete,false);assert.equal(card.data.description,'피해를 6 줍니다.');
 let recovered='',offset=0;do{const chunk=readContext(s,['screen/body_text'],offset).fragments[0];recovered+=chunk.text;offset=chunk.next_offset;assert.ok(JSON.stringify(chunk).length<2600);}while(offset!==null);
 assert.equal(recovered,long);assert.ok(JSON.stringify(readContext(s,Array(8).fill('screen/body_text'))).length<20000);
 for(const r of ['screen/__proto__','screen/constructor','deck/-1','deck/999','screen/body_text/length'])assert.throws(()=>readContext(s,[r]),/not available/);
 assert.throws(()=>readContext(s,['deck'],-1),/Invalid/);assert.throws(()=>readContext(s,Array(9).fill('deck')),/Invalid/);
});
test('conditional reads require explicit known_view and invalidate on safety changes',async()=>{
 const {conditionalDecision}=await api(),s=state(),first=conditionalDecision(s);
 assert.equal(first.unchanged,undefined);assert.ok(first.view_id);
 s.connection.age_ms=100;assert.equal(conditionalDecision(s,first.view_id).unchanged,true);
 assert.ok(conditionalDecision(s).toc);s.ready=false;
 const unready=conditionalDecision(s,first.view_id);assert.equal(unready.unchanged,undefined);assert.deepEqual(unready.actions,[]);
 s.ready=true;s.connection.pending_request_id='pending';assert.equal(conditionalDecision(s,first.view_id).unchanged,undefined);
 s.connection.pending_request_id=null;s.connection.status='disconnected_or_stale';assert.equal(conditionalDecision(s,first.view_id).unchanged,undefined);
 s.connection.status='connected';s.state_id++;assert.equal(conditionalDecision(s,first.view_id).unchanged,undefined);
});
test('combat facts remain accessible while inactive UI and upgrade detail stay off default view',async()=>{
 const {decision,readContext}=await api(),s=state('NONE');
 s.actions=[{id:'run.play.a.0',label:'타격',parameters:{}}];
 s.observation.game_state.combat_state={hand_complete:true,hand:[{id:'a',name:'타격',description:'피해 6.',cost:0,displayed_cost_complete:false,upgrade_preview:{after:{description:'피해 9.'}}}],monsters:[{name:'적',current_hp:12,intent:'ATTACK',move_adjusted_damage:5}],player:{energy:3,mechanics:{information_complete:false,collection:{count:1,cards_complete:true,order_visible:false,cards:[{id:'col'}]}}},draw_pile:[],draw_pile_order_visible:false};
 const v=decision(s);assert.equal(v.combat.hand[0].displayed_cost_complete,false);assert.equal(v.combat.hand[0].cost,0);assert.equal(v.combat.monsters[0].move_adjusted_damage,5);
 assert.ok(!JSON.stringify(v).includes('피해 9.'));assert.ok(v.toc.some(x=>x.ref==='collection'));
 assert.equal(readContext(s,['hand/0/upgrade_preview/after']).fragments[0].data.description,'피해 9.');
 assert.equal(readContext(s,['collection/cards/0']).fragments[0].data.id,'col');
 assert.equal(readContext(s,['mechanics']).fragments[0].data.information_complete,false);
});
test('Snecko/current turn cost changes invalidate known_view even when other card text is unchanged',async()=>{
 const {conditionalDecision}=await api(),s=state('NONE');
 s.actions=[{id:'run.play.a.0',label:'타격',parameters:{}}];
 s.observation.game_state.combat_state={hand_complete:true,hand:[{id:'a',uuid:'u',name:'타격',cost:0,displayed_cost_complete:false}],monsters:[],player:{energy:3},draw_pile:[]};
 const first=conditionalDecision(s);assert.equal(first.combat.hand[0].cost,0);
 s.observation.game_state.combat_state.hand[0].cost=3;
 const changed=conditionalDecision(s,first.view_id);assert.equal(changed.unchanged,undefined);assert.equal(changed.combat.hand[0].cost,3);
});
test('hand TOC exposes current turn cost without opening each card',async()=>{
 const {readContext}=await api(),s=state('NONE');
 s.actions=[{id:'run.play.a.0',label:'타격',parameters:{}}];
 s.observation.game_state.combat_state={hand_complete:true,hand:[{id:'a',name:'타격',cost:0},{id:'b',name:'수비',cost:3}],monsters:[],player:{energy:3},draw_pile:[]};
 const page=readContext(s,['hand']).fragments[0];
 assert.equal(page.toc[0].ref,'hand/0');assert.equal(page.toc[0].title,'타격');assert.equal(page.toc[0].cost,0);
 assert.equal(page.toc[1].ref,'hand/1');assert.equal(page.toc[1].title,'수비');assert.equal(page.toc[1].cost,3);
 assert.ok(JSON.stringify(page).length<2400);
});
test('native choice screen and in-combat selection retain current controls',async()=>{
 const {decision,readContext}=await api(),s=state('NONE');s.ready=false;s.observation.menu={screen:'REST',game_screen:'NONE'};
 s.observation.rest_controls=[{description:'회복',available:false}];
 assert.equal(decision(s).screen,'REST');assert.ok(decision(s).toc.some(x=>x.ref==='rest_controls'));
 s.observation.menu={screen:'CARD_REWARD'};s.observation.combat_decision={mode:'selection',hand_complete:false};
 s.observation.game_state.combat_state={hand_complete:false,hand:[{id:'stale-card'}],monsters:[{name:'enemy'}],draw_pile:[]};s.observation.selection_controls={kind:'discovery'};
 assert.ok(decision(s).toc.some(x=>x.ref==='monsters'));assert.ok(decision(s).toc.some(x=>x.ref==='selection_controls'));
 assert.ok(!decision(s).toc.some(x=>x.ref==='hand'));assert.throws(()=>readContext(s,['hand']),/not available/);
});
test('escaped text and huge fields cannot exceed budgets or stall continuations',async()=>{
 const {readContext}=await api(),s=state();s.observation.game_state.screen_state.body_text='\u0000'.repeat(3000);
 const text=readContext(s,['screen/body_text']).fragments[0];assert.ok(JSON.stringify(text).length<2500);assert.ok(text.next_offset>0);
 s.observation.game_state.screen_state={['x'.repeat(3000)]:{description:'large key'},normal:'still reachable'};
 const page=readContext(s,['screen']).fragments[0];assert.ok(page.next_offset===null || page.next_offset>0);assert.equal(page.information_complete,false);
 assert.equal(page.data.normal,'still reachable');assert.ok((page.toc??[]).every(x=>x.ref.length<=512));
});
test('combat metadata and current card have reachable short references',async()=>{
 const {decision,readContext}=await api(),s=state('NONE');s.observation.game_state.combat_state={turn:4,times_damaged:2,cards_discarded_this_turn:1,card_in_play:{id:'a',description:'현재 카드'}};
 assert.ok(decision(s).toc.some(x=>x.ref==='combat'));
 assert.equal(readContext(s,['combat']).fragments[0].data.turn,4);assert.equal(readContext(s,['card_in_play']).fragments[0].data.id,'a');
});
test('current shop prices and card refs avoid traversing an entire screen',async()=>{
 const {decision}=await api(),s=state('SHOP_SCREEN');s.actions=[{id:'run.shop.card.u',label:'카드',parameters:{}}];s.observation.shop_controls=[{id:'buy',price:75,available:true}];s.observation.game_state.screen_state={cards:[{name:'카드',price:75,description:'카드 효과',upgrade_preview:{after:{description:'강화'}}}]};
 const v=decision(s);assert.equal(v.shop_controls,undefined);assert.ok(v.toc.some(x=>x.ref==='shop_controls'));assert.equal(v.offers[0].ref,'screen/cards/0');assert.equal(v.offers[0].name,'카드');assert.equal(v.offers[0].price,75);
});
test('a selected small card is one atomic fragment, not a chain of tiny reads',async()=>{
 const {readContext}=await api(),s=state();s.observation.game_state.deck[0].tooltips=[{title:'취약',description:'추가 피해'}];
 const card=readContext(s,['deck/0']).fragments[0];assert.equal(card.data.tooltips[0].description,'추가 피해');assert.equal(card.page_complete,true);
});
test('current event body has a direct fragment reference and short choices',async()=>{
 const {decision}=await api(),s=state();s.observation.game_state.screen_state={event_reading:{body_text:'이야기'.repeat(1000),reading_id:'r',options:[{text:'떠난다',disabled:false}]}};
 const v=decision(s);assert.equal(v.event_reading.body_ref,'screen/event_reading/body_text');assert.equal(v.event_reading.options[0].text,'떠난다');assert.equal(v.event_reading.reading_id,'r');
});
test('missing or conflicting completeness never exposes an ordinary hand',async()=>{
 const {decision,readContext}=await api(),s=state('NONE');
 assert.deepEqual(decision(s).combat.hand,[]);assert.throws(()=>readContext(s,['hand']),/not available/);
 s.observation.game_state.combat_state.hand_complete=true;s.observation.combat_decision={mode:'selection',hand_complete:false};
 assert.deepEqual(decision(s).combat.hand,[]);assert.equal(decision(s).combat.hand_complete,false);
});
