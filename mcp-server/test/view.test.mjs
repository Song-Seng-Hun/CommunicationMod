import test from 'node:test';
import assert from 'node:assert/strict';
test('mechanics collection pages reach the tail and preserve independent pile metadata',async()=>{
 const {focus,section}=await import('../dist/view.js');
 const cards=Array.from({length:135},(_,i)=>({id:`card-${i}`,displayed_cost_complete:i!==134}));
 const mechanics={panel_bindings_complete:true,information_complete:false,character_specific_complete:false,
  collection:{count:136,cards_complete:false,order_visible:false,cards},combat_collection:{count:1,cards_complete:true,order_visible:false,cards:[{id:'combat'}]}};
 for(const combat of [false,true]){
  const state={session_id:'s',state_id:17,observation:{game_state:combat?{combat_state:{player:{mechanics}}}:{mechanics}}};
  const page=section(state,'mechanics',100,30);
  assert.equal(page.data.collection.cards.length,30);assert.equal(page.data.collection.cards[0].id,'card-100');
  assert.equal(page.data.collection.next_offset,130);assert.equal(page.data.collection.total,135);
  assert.equal(page.data.collection.count,136);assert.equal(page.data.collection.cards_complete,false);
  assert.equal(page.data.collection.page_complete,false);assert.equal(page.data.information_complete,false);
  assert.deepEqual(page.data.combat_collection.cards,[]);assert.equal(page.data.combat_collection.next_offset,null);
  const tail=section(state,'collection',130,30);
  assert.equal(tail.items.length,5);assert.equal(tail.items[4].displayed_cost_complete,false);assert.equal(tail.next_offset,null);
  assert.equal(tail.page_complete,false);assert.equal(tail.session_id,'s');assert.equal(tail.state_id,17);
  const single=section(state,'combat_collection',0,1);assert.equal(single.items[0].id,'combat');assert.equal(single.page_complete,true);
  const all=section(state,'collection',0,100);assert.equal(all.items.length,100);assert.equal(all.next_offset,100);
  const beyond=section(state,'collection',999,10);assert.deepEqual(beyond.items,[]);assert.equal(beyond.next_offset,null);
  const v=focus(state),summary=combat?v.combat.player.mechanics:v.player.mechanics;
  assert.equal(summary.collection.cards_context,'collection');assert.equal(summary.collection.cards,undefined);
  assert.ok(v.available_sections.includes('collection'));assert.equal(mechanics.collection.cards.length,135);
 }
 assert.equal(section({observation:{}},'collection',0,30).data,null);
 const empty=section({observation:{game_state:{mechanics:{collection:{count:0,cards_complete:true,cards:[]}}}}},'collection',0,30);
 assert.equal(empty.total,0);assert.equal(empty.next_offset,null);assert.equal(empty.page_complete,true);
});
test('native mechanics are first class outside combat and collections stay on demand',async()=>{
 const {focus,section}=await import('../dist/view.js');
 const mechanics={reserves:4,essence:12,collection:{count:2,order_visible:false,cards_complete:true,cards:[{id:'a'},{id:'b'}]},spells:[{count:2,up_next:true,card:{description:'공개 주문'}}]};
 for(const combat of [false,true]){
  const state={observation:{game_state:combat?{combat_state:{player:{mechanics}}}:{mechanics}}};
  const v=focus(state),resources=combat?v.combat.player.mechanics:v.player.mechanics;
  assert.equal(resources.reserves,4);assert.equal(resources.collection.count,2);assert.equal(resources.collection.cards,undefined);
  assert.equal(resources.spells[0].card.description,'공개 주문');assert.ok(v.available_sections.includes('mechanics'));
  assert.deepEqual(section(state,'mechanics',0,30).data.collection.cards,mechanics.collection.cards);
 }
});
test('run controls are first-class decision fields and retain warnings',async()=>{
 const {focus}=await import('../dist/view.js');
 const controls={potion_controls:[{slot:1,requires_target:true,can_use:true}],rest_controls:[{id:'run.rest.0',description:'회복',available:false}],shop_controls:[{id:'run.shop.purge',price:75,available:false,unavailable_reason:'insufficient_gold'}],reward_navigation:{unclaimed_rewards:2,may_leave_unclaimed_rewards:true},card_selection_controls:{kind:'discovery',candidate_count:4,acquires_into_deck:false}};
 const state={ready:true,observation:{...controls,game_state:{combat_state:{hand:[{is_playable:false,target_playability:[{monster_index:0,available:false,reason:'조건 불충족'}],displayed_cost_complete:true,displayed_cost_text:'X',cost_components_complete:false}],player:{mechanics:{character_specific_complete:false,encode_sequence:[{description:'인코딩 카드'}]},orbs:[{description:'구체 효과',stasis_card:{description:'정지 카드'}}]}}}}};
 const view=focus(state);for(const [key,value] of Object.entries(controls))assert.deepEqual(view[key],value,key);
 assert.equal(view.combat.hand[0].displayed_cost_text,'X');assert.equal(view.combat.hand[0].cost_components_complete,false);
 assert.equal(view.combat.hand[0].target_playability[0].reason,'조건 불충족');assert.equal(view.combat.player.mechanics.character_specific_complete,false);
 assert.equal(view.combat.player.orbs[0].stasis_card.description,'정지 카드');
});
test('upgrade comparisons survive reward/shop/grid decisions and detail views',async()=>{
 const {focus,section}=await import('../dist/view.js');
 const card={id:'Searing Blow',uuid:'owned',upgrades:3,description:'피해 27.',upgrade_preview:{status:'available',scope:'next_standard_upgrade',steps:1,from_upgrades:3,can_upgrade_after:true,after:{upgrades:4,base_damage:34,description:'피해 34.',description_complete:true,displayed_cost_complete:false},numeric_changes:{base_damage:{from:27,to:34,delta:7}}}};
 for(const screen_type of ['CARD_REWARD','SHOP_SCREEN','GRID']) {
  const state={observation:{game_state:{screen_type,screen_state:{cards:[card],for_upgrade:screen_type==='GRID',upgrade_selection_preview:{status:'displayed',after:card.upgrade_preview.after}},deck:[card]}}};
  assert.deepEqual(focus(state).screen_state.cards[0].upgrade_preview,card.upgrade_preview);
  assert.deepEqual(section(state,'screen',0,30).data.cards[0].upgrade_preview,card.upgrade_preview);
  assert.equal(section(state,'deck',0,30).items[0].upgrade_preview.after.upgrades,4);
  assert.equal(focus(state).screen_state.upgrade_selection_preview.after.displayed_cost_complete,false);
 }
});
test('event card previews survive prose deduplication without inventing event outcomes',async()=>{
 const {focus}=await import('../dist/view.js');
 const card={id:'event-card',description:'카드 내용',upgrade_preview:{status:'available',scope:'next_standard_upgrade',event_effects_predicted:false,after:{description:'강화 카드'}}};
 const state={observation:{game_state:{screen_type:'EVENT',screen_state:{options:[{text:'카드 받기',disabled:false,choice_index:0,card_preview:card},{text:'다른 선택',disabled:false,choice_index:1}],event_reading:{body_text:'긴 본문',options:[{text:'카드 받기'},{text:'다른 선택'}]}}}}};
 const v=focus(state);assert.equal(v.screen_state.options,undefined);
 assert.deepEqual(v.screen_state.option_card_previews,[{option_index:0,choice_index:0,disabled:false,card_preview:card}]);
 assert.equal(v.screen_state.option_card_previews[0].card_preview.upgrade_preview.event_effects_predicted,false);
});
test('map-only polling stays bounded even when the game observation is large',async()=>{
 const {mapSummary}=await import('../dist/view.js');
 const state={session_id:'s',state_id:44,ready:false,observation:{game_state:{screen_type:'MAP',deck:Array(100).fill({description:'text'.repeat(1000)}),map_plan:{map_id:'map-1',revision:8,route_author:'human',route:Array(64).fill('1,2'),route_count:64,current_node:'1,2',next_planned_node:'2,3',stroke_count:100,editing:true,status:'on_route',ink_points:Array(8192).fill([1,2])}}}};
 const small=mapSummary(state);assert.equal(small.state_id,44);assert.equal(small.map_plan.next_planned_node,'2,3');
 assert.equal(small.actions,undefined);assert.equal(small.deck,undefined);assert.equal(small.map_plan.route,undefined);
 assert.ok(Buffer.byteLength(JSON.stringify(small))<900);
 assert.equal(mapSummary({observation:{}}).map_plan.status,'unavailable');
});
test('map plans are cheap by default and full semantic routes are on demand',async()=>{
 const {focus,section}=await import('../dist/view.js');
 const plan={map_id:'map-2',revision:7,route_author:'human',route:['1,14','2,13','2,12'],current_node:'2,13',next_planned_node:'2,12',status:'on_route',stroke_count:5,editing:false,ink_points:[[1,2]],points:[1,2]};
 const state={observation:{game_state:{map_plan:plan,map:[{x:1,y:14,children:[{x:2,y:13}]}]}}};
 const v=focus(state);assert.equal(v.map_plan.revision,7);assert.equal(v.map_plan.current_node,'2,13');assert.equal(v.map_plan.next_planned_node,'2,12');
 assert.equal(v.map_plan.route,undefined);assert.equal(v.map_plan.ink_points,undefined);assert.equal(v.extensions.map_plan,undefined);
 const full=section(state,'map_plan',0,30);assert.deepEqual(full.data.route,plan.route);assert.equal(full.data.points,undefined);assert.equal(full.data.ink_points,undefined);
 assert.equal(section(state,'map',0,30).items[0].node_id,'1,14');
});
test('focused view preserves decisions and text, omits deck/map from combat',async()=>{
 const {focus}=await import('../dist/view.js').catch(()=>{throw new Error('Missing screen-focused MCP observation')});
 const s={session_id:'s',state_id:1,ready:true,actions:[{id:'run.play.a.0'}],runtime:{language:'KOR'},observation:{game_state:{screen_type:'NONE',deck:[{name:'private-to-default'}],map:[{x:1}],current_hp:20,combat_state:{hand:[{uuid:'a',name:'타격',description:'피해를 6 줍니다.',tooltips:[{title:'취약',description:'추가 피해'}]}],monsters:[{name:'적'}]}}}};
 const v=focus(s);assert.equal(v.combat.hand[0].description,'피해를 6 줍니다.');assert.deepEqual(v.actions,s.actions);assert.equal(v.deck,undefined);assert.equal(v.map,undefined);assert.equal(v.combat.hand[0].tooltips[0].title,'취약');
 const event=focus({...s,observation:{game_state:{screen_type:'EVENT',screen_state:{event_reading:{body_text:'본문 전체',reading_id:'r',options:[{text:'선택'}]}}}}});assert.equal(event.screen_state.event_reading.body_text,'본문 전체');
});
test('character resources and extension observations are not silently lost',async()=>{
 const {focus}=await import('../dist/view.js');const v=focus({ready:true,observation:{character_resources:{dead_on:true},game_state:{class:'HERMIT',custom_state:{resource:3}}}});assert.deepEqual(v.extensions.custom_state,{resource:3});assert.deepEqual(v.observation_extensions.character_resources,{dead_on:true});
});
test('unready state never returns usable actions or incomplete combat hand',async()=>{
 const {focus}=await import('../dist/view.js');const v=focus({ready:false,actions:[{id:'bad'}],observation:{game_state:{combat_state:{hand_complete:false,hand:[{name:'partial'}]}}}});assert.deepEqual(v.actions,[]);assert.deepEqual(v.combat.hand,[]);
});
test('current dialogue remains, duplicate event prose and old dialogue are on demand',async()=>{
 const {focus,section}=await import('../dist/view.js');const state={observation:{game_state:{screen_state:{event_reading:{body_text:'현재 본문'}},narrative:{entries:[{id:1,text:'지난 대사',currently_displayed:false},{id:2,text:'현재 본문',visible_text:'현재 본문',currently_displayed:true},{id:3,text:'상인 인사',visible_text:'상인 인사',currently_displayed:true}]}}}};
 const v=focus(state);assert.equal(v.narrative.history_count,1);assert.equal(v.narrative.entries.length,2);assert.equal(v.narrative.entries[0].event_reading_ref,true);assert.equal(v.narrative.entries[0].text,undefined);assert.equal(v.narrative.entries[1].text,'상인 인사');assert.equal(v.narrative.entries[1].visible_text,undefined);
 assert.equal(section(state,'history',0,10).data.entries[0].text,'지난 대사');
});
