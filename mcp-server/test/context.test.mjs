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
 assert.equal(v.player.current_hp,20);assert.equal(v.actions[0].id,'a0');assert.ok(!JSON.stringify(v).includes('run.event.0'));
 assert.equal(v.session_id,undefined);assert.equal(v.state_id,undefined);assert.equal(v.connection,undefined);
});
test('fragments are shallow, batchable, bounded and exact long-text pages are recoverable',async()=>{
 const {readContext}=await api(),s=state();const long='긴 설명 🔥 '.repeat(2000);s.observation.game_state.screen_state.body_text=long;
 const root=readContext(s,['screen','deck']);assert.equal(root.fragments.length,2);assert.equal(root.session_id,undefined);assert.equal(root.state_id,undefined);
 assert.ok(root.fragments[0].toc.some(x=>x.ref==='screen/body_text'));assert.equal(root.fragments[0].data,undefined,'An all-directory page omits the empty data wrapper');
 const card=readContext(s,['deck/0']).fragments[0];assert.equal(card.data.displayed_cost_complete,false);assert.equal(card.data.description,'피해를 6 줍니다.');
 let recovered='',offset=0;do{const chunk=readContext(s,['screen/body_text'],offset).fragments[0];recovered+=chunk.text;offset=chunk.next_offset??null;assert.ok(JSON.stringify(chunk).length<2600);}while(offset!==null);
 assert.equal(recovered,long);assert.ok(JSON.stringify(readContext(s,Array(8).fill('screen/body_text'))).length<20000);
 for(const r of ['screen/__proto__','screen/constructor','deck/-1','deck/999','screen/body_text/length'])assert.throws(()=>readContext(s,[r]),/not available/);
 assert.throws(()=>readContext(s,['deck'],-1),/Invalid/);assert.throws(()=>readContext(s,Array(9).fill('deck')),/Invalid/);
});
test('public root transforms cannot manufacture absent current-state roots',async()=>{
 const {readContext}=await api(),s=state();delete s.observation.game_state.screen_state;s.ready=false;
 for(const root of ['actions','screen','map_plan','history','dialogue'])for(const ref of [root,root+'/0'])assert.throws(()=>readContext(s,[ref]),/not available/,ref);
});
test('encoded action paths cannot bypass aliases or server-pinned parameter hiding',async()=>{
 const {readContext}=await api(),s=state();s.actions[0].parameters={type:'object',properties:{reading_id:{const:'hidden-reading'},commentary:{type:'string'}},required:['reading_id','commentary']};
 for(const ref of ['actions/0/id','actions/%30/id','%61ctions/0/id','actions/%30/parameters/properties/reading_id','actions/0/%70arameters/properties/reading_id','actions/0/parameters/properties/%72eading_id'])assert.throws(()=>readContext(s,[ref]),/not available/,ref);
 assert.equal(readContext(s,['actions/0']).fragments[0].data.id,'a0');assert.equal(readContext(s,['actions/0/parameters/properties/commentary']).fragments[0].data.type,'string');
});
for(const kind of ['text','scalar array'])test(`${kind} batch pages include continuation metadata within their byte budget`,async()=>{
 const {readContext}=await api(),s=state(),refs=Array.from({length:8},(_,i)=>'screen/field'+i),budget=450,bytes=v=>Buffer.byteLength(JSON.stringify(v),'utf8');
 const emptyArrayPage={ref:refs[0],total:3,items:['','']},each=Math.floor((budget-bytes(emptyArrayPage))/2);
 const value=kind==='text'?'x'.repeat(500):['x'.repeat(each),'x'.repeat(each),'tail'];
 s.observation.game_state.screen_state=Object.fromEntries(refs.map(ref=>[ref.split('/')[1],value]));
 let offset=0;const recovered=refs.map(()=>kind==='text'?'':[]);
 do{
  const result=readContext(s,refs,offset,2);assert.ok(bytes(result)<=3700);
  for(const [i,page] of result.fragments.entries()){
   assert.ok(bytes(page)<=budget,`${kind}: ${bytes(page)} bytes including next_offset`);
   if(kind==='text')recovered[i]+=page.text;else recovered[i].push(...page.items);
   assert.ok(page.next_offset===undefined||page.next_offset>offset,'Continuation must advance');
  }
  offset=result.fragments[0].next_offset;
 }while(offset!==undefined);
 for(const actual of recovered)assert.deepEqual(actual,value);
});
test('a scalar array row too large for its batch budget is discoverable instead of stalling',async()=>{
 const {readContext}=await api(),s=state(),refs=Array.from({length:8},(_,i)=>'screen/field'+i),value='🔥'.repeat(120);
 s.observation.game_state.screen_state=Object.fromEntries(refs.map(ref=>[ref.split('/')[1],[value]]));
 const pages=readContext(s,refs).fragments;
 for(const [i,page] of pages.entries()){
  assert.ok(Buffer.byteLength(JSON.stringify(page),'utf8')<=450);assert.equal(page.next_offset,undefined);
  assert.equal(page.toc?.[0].ref,refs[i]+'/0');assert.equal(readContext(s,[page.toc[0].ref]).fragments[0].text,value);
 }
});
test('long advertised text refs never return a stalled surrogate page; single-ref recovery is exact',async()=>{
 const {readContext}=await api(),s=state(),refs=Array.from({length:8},(_,i)=>'screen/'+'x'.repeat(404)+i),value='🔥'.repeat(200);
 s.observation.game_state.screen_state=Object.fromEntries(refs.map(ref=>[ref.slice(7),value]));
 const advertised=[];let offset=0;
 do{const page=readContext(s,['screen'],offset).fragments[0];advertised.push(...page.toc.map(x=>x.ref));offset=page.next_offset;}while(offset!==undefined);
 assert.deepEqual(advertised,refs);
 assert.throws(()=>readContext(s,refs),/Read this ref alone/i);
 for(const ref of refs){
  let text='',cursor=0;
  do{const page=readContext(s,[ref],cursor).fragments[0];assert.ok(Buffer.byteLength(JSON.stringify(page),'utf8')<=2400);text+=page.text;assert.ok(page.next_offset===undefined||page.next_offset>cursor);cursor=page.next_offset;}while(cursor!==undefined);
  assert.equal(text,value);
 }
});
test('long refs cannot exceed a batch allocation through scalar or directory wrappers',async()=>{
 const {readContext}=await api(),s=state(),refs=Array.from({length:8},(_,i)=>'screen/'+'x'.repeat(480)+i);
 for(const value of [null,false,0,[],{flag:false}]){
  s.observation.game_state.screen_state=Object.fromEntries(refs.map(ref=>[ref.slice(7),value]));
  assert.throws(()=>readContext(s,refs),/Read this ref alone/i);
  for(const ref of refs){const page=readContext(s,[ref]).fragments[0];assert.ok(Buffer.byteLength(JSON.stringify(page),'utf8')<=2400);assert.deepEqual(Array.isArray(value)?page.items:value&&typeof value==='object'?{...page.data}:page.value,value);}
 }
});
test('conditional reads keep hidden identity in the hash without exposing it',async()=>{
 const {conditionalDecision}=await api(),s=state(),first=conditionalDecision(s);
 assert.equal(first.unchanged,undefined);assert.ok(first.view_id);assert.equal(first.session_id,undefined);assert.equal(first.state_id,undefined);
 s.connection.age_ms=100;assert.equal(conditionalDecision(s,first.view_id).unchanged,true);
 s.ready=false;const unready=conditionalDecision(s,first.view_id);assert.equal(unready.unchanged,undefined);assert.deepEqual(unready.actions,[]);
 s.ready=true;s.connection.pending_request_id='pending';assert.equal(conditionalDecision(s,first.view_id).unchanged,undefined);
 s.connection.pending_request_id=null;s.connection.status='disconnected_or_stale';assert.equal(conditionalDecision(s,first.view_id).unchanged,undefined);
 s.connection.status='connected';s.state_id++;assert.equal(conditionalDecision(s,first.view_id).unchanged,undefined);
});
test('combat facts remain accessible while upgrade detail stays off default view',async()=>{
 const {decision,readContext}=await api(),s=state('NONE');
 s.actions=[{id:'run.play.a.0',label:'타격',parameters:{}}];
 s.observation.game_state.combat_state={hand_complete:true,hand:[{id:'a',name:'타격',description:'피해 6.',cost:0,displayed_cost_complete:false,upgrade_preview:{after:{description:'피해 9.'}}}],monsters:[{name:'적',current_hp:12,intent:'ATTACK',move_adjusted_damage:5}],player:{energy:3,mechanics:{information_complete:false,collection:{count:1,cards_complete:true,order_visible:false,cards:[{id:'col'}]}}},draw_pile:[],draw_pile_order_visible:false};
 const v=decision(s);assert.equal(v.combat.hand[0].displayed_cost_complete,false);assert.equal(v.combat.hand[0].cost,0);assert.equal(v.combat.monsters[0].move_adjusted_damage,5);
 assert.equal(v.combat.hand_complete,undefined);assert.ok(!JSON.stringify(v).includes('피해 9.'));assert.ok(v.toc.some(x=>x.ref==='collection'));
 assert.equal(v.player.mechanics_incomplete,true);assert.ok(v.toc.some(x=>x.ref==='mechanics'));
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
test('hand TOC exposes only useful comparison metadata',async()=>{
 const {readContext}=await api(),s=state('NONE');
 s.actions=[{id:'run.play.a.0',label:'타격',parameters:{}}];
 s.observation.game_state.combat_state={hand_complete:true,hand:[{id:'a',name:'타격',cost:0,upgrades:2},{id:'b',name:'수비',cost:3}],monsters:[],player:{energy:3},draw_pile:[]};
 const page=readContext(s,['hand']).fragments[0];
 assert.deepEqual(page.toc[0],{ref:'hand/0',title:'타격',cost:0});assert.deepEqual(page.toc[1],{ref:'hand/1',title:'수비',cost:3});
 assert.ok(JSON.stringify(page).length<2400);
});
test('multi-card detail reads share one inline budget and omit routine card metadata',async()=>{
 const {readContext}=await api(),s=state('NONE');s.actions=[{id:'run.play.a.0',label:'카드',parameters:{}}];
 const heavy=(i,cost)=>({id:'c'+i,uuid:'u'+i,name:'긴 카드 '+i,type:'SKILL',upgrades:i%2,cost,is_playable:true,displayed_cost_text:String(cost),displayed_cost_complete:true,cost_components_complete:true,exhausts:false,ethereal:false,
  description:'다음 턴까지 피해량을 감소시키고 여러 추가 효과를 적용합니다. '.repeat(25),cost_components:[{resource:'energy',displayed_amount:cost,kind:'fixed'}],tooltips:Array.from({length:5},(_,n)=>({title:'키워드'+n,description:'아주 긴 툴팁 설명 '.repeat(20)}))});
 s.observation.game_state.combat_state={hand_complete:true,hand:[heavy(0,0),heavy(1,3),heavy(2,1),heavy(3,2)],monsters:[],player:{energy:3},draw_pile:[]};
 const result=readContext(s,['hand/0','hand/1','hand/2','hand/3']);assert.ok(Buffer.byteLength(JSON.stringify(result),'utf8')<4300);assert.equal(result.fragments.length,4);
 for(let i=0;i<4;i++){
  const f=result.fragments[i];assert.equal(f.data.cost,[0,3,1,2][i]);assert.equal(f.details_required,true);assert.equal(f.data.name,'긴 카드 '+i);
  for(const key of ['id','uuid','upgrades','displayed_cost_complete','cost_components_complete','exhausts','ethereal','page_complete','format'])assert.equal(f[key]??f.data[key],undefined,key);
 }
});
test('fully inlined controls skip redundant TOC while nested controls keep one detail ref',async()=>{
 const {decision,readContext}=await api(),s=state('NONE');s.ready=false;s.observation.menu={screen:'REST',game_screen:'NONE'};s.observation.rest_controls=[{description:'회복',available:false}];
 const rest=decision(s);assert.equal(rest.screen,'REST');assert.deepEqual(rest.rest_controls,[{description:'회복',available:false}]);assert.ok(!rest.toc.some(x=>x.ref==='rest_controls'));
 s.observation.menu={screen:'CARD_REWARD'};s.observation.combat_decision={mode:'selection',hand_complete:false};
 s.observation.game_state.combat_state={hand_complete:false,hand:[{id:'stale-card'}],monsters:[{name:'enemy'}],draw_pile:[]};s.observation.selection_controls={kind:'discovery'};
 const simple=decision(s);assert.ok(simple.toc.some(x=>x.ref==='monsters'));assert.equal(simple.selection_controls.kind,'discovery');assert.ok(!simple.toc.some(x=>x.ref==='selection_controls'));assert.ok(!simple.toc.some(x=>x.ref==='hand'));assert.throws(()=>readContext(s,['hand']),/not available/);
 s.observation.selection_controls={kind:'grid',upgrade_choice:{choice_required:true,options:[{index:0,label:'A'}]}};const nested=decision(s);assert.ok(nested.toc.some(x=>x.ref==='selection_controls'));assert.equal(nested.selection_controls.kind,'grid');
});
test('escaped text and huge fields cannot exceed budgets or stall continuations',async()=>{
 const {readContext}=await api(),s=state();s.observation.game_state.screen_state.body_text='\u0000'.repeat(3000);
 const text=readContext(s,['screen/body_text']).fragments[0];assert.ok(JSON.stringify(text).length<2500);assert.ok(text.next_offset>0);
 s.observation.game_state.screen_state={['x'.repeat(3000)]:{description:'large key'},normal:'still reachable'};
 const page=readContext(s,['screen']).fragments[0];assert.ok(page.next_offset===undefined || page.next_offset>0);assert.equal(page.information_complete,false);
 assert.equal(page.data.normal,'still reachable');assert.ok((page.toc??[]).every(x=>x.ref.length<=512));
});
test('combat metadata is inline without a redundant TOC and current card has a reachable ref',async()=>{
 const {decision,readContext}=await api(),s=state('NONE');s.observation.game_state.combat_state={turn:4,times_damaged:2,cards_discarded_this_turn:1,card_in_play:{id:'a',description:'현재 카드'}};
 const v=decision(s);for(const [key,value] of Object.entries({turn:4,times_damaged:2,cards_discarded_this_turn:1}))assert.equal(v.combat[key],value);
 assert.ok(!v.toc.some(x=>x.ref==='combat'));assert.equal(readContext(s,['combat']).fragments[0].data.turn,4);
 const cardRef=v.toc.find(x=>x.ref==='card_in_play').ref;assert.equal(readContext(s,[cardRef]).fragments[0].data.id,'a');
});
test('current shop prices and card refs avoid traversing an entire screen',async()=>{
 const {decision}=await api(),s=state('SHOP_SCREEN');s.actions=[{id:'run.shop.card.u',label:'카드',parameters:{}}];s.observation.shop_controls=[{id:'buy',price:75,available:true}];s.observation.game_state.screen_state={cards:[{name:'카드',price:75,description:'카드 효과',upgrade_preview:{after:{description:'강화'}}}]};
 const v=decision(s);assert.equal(v.shop_controls,undefined);assert.ok(v.toc.some(x=>x.ref==='shop_controls'));assert.equal(v.offers[0].ref,'screen/cards/0');assert.equal(v.offers[0].name,'카드');assert.equal(v.offers[0].price,75);assert.equal(v.offers[0].upgrades,undefined);
});
test('a selected small card is atomic without success boilerplate',async()=>{
 const {readContext}=await api(),s=state();s.observation.game_state.deck[0].tooltips=[{title:'취약',description:'추가 피해'}];
 const card=readContext(s,['deck/0']).fragments[0];assert.equal(card.data.tooltips[0].description,'추가 피해');assert.equal(card.page_complete,undefined);assert.equal(card.next_offset,undefined);assert.equal(card.format,undefined);
});
test('current event body has a direct fragment reference while reading identity stays hidden',async()=>{
 const {decision,readContext}=await api(),s=state();s.observation.game_state.screen_state={event_name:'탑',body_text:'중복 본문',event_reading:{body_text:'이야기'.repeat(1000),reading_id:'opaque',phase:'discussion_required',page_role:'before_choice',text_complete:true,options:[{text:'떠난다',disabled:false}]}};
 const v=decision(s);assert.equal(v.event_reading.body_ref,'screen/event_reading/body_text');assert.equal(v.event_reading.options[0].text,'떠난다');assert.equal(v.event_reading.reading_id,undefined);assert.equal(v.event_reading.phase,'discussion_required');assert.equal(v.screen_state.body_text,undefined);
 assert.throws(()=>readContext(s,['screen/event_reading/reading_id']),/not available/);
});
test('missing or conflicting completeness never exposes an ordinary hand',async()=>{
 const {decision,readContext}=await api(),s=state('NONE');
 assert.deepEqual(decision(s).combat.hand,[]);assert.equal(decision(s).combat.hand_complete,false);assert.throws(()=>readContext(s,['hand']),/not available/);
 s.observation.game_state.combat_state.hand_complete=true;s.observation.combat_decision={mode:'selection',hand_complete:false};
 assert.deepEqual(decision(s).combat.hand,[]);assert.equal(decision(s).combat.hand_complete,false);
});

test('character card batch fallback preserves current Dead On and incomplete facts within allocations',async()=>{
 const {decision,readContext}=await api(),s=state('NONE'),c=s.observation.game_state.combat_state;c.hand_complete=true;c.hand=Array.from({length:8},(_,i)=>({name:'Card '+i,cost:1,dead_on_position_active:i%2===0,description:'긴 현재 설명 '.repeat(300),description_complete:false,sockets:Array(80).fill('RED'),sockets_unavailable_reason:'partial_socket_capture',unknown_origin:null}));
 const before=JSON.stringify(s),refs=c.hand.map((_,i)=>'hand/'+i),batch=readContext(s,refs);assert.ok(Buffer.byteLength(JSON.stringify(batch))<=3700);
 for(const [i,page] of batch.fragments.entries()){assert.ok(Buffer.byteLength(JSON.stringify(page))<=450);assert.equal(page.data.dead_on_position_active,i%2===0);assert.equal(page.data.description_complete,false);assert.equal(page.details_required,true);assert.equal(page.data.unknown_origin,null);assert.equal(page.data.sockets_unavailable_reason,'partial_socket_capture');}
 const v=decision(s);assert.equal(v.combat.hand.length,8);assert.ok(v.combat.hand.every(x=>x.details_required===true));assert.equal(JSON.stringify(s),before);
});

