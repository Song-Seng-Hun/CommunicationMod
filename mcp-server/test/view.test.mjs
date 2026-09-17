import test from 'node:test';
import assert from 'node:assert/strict';
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
test('large card choice screens stay compact and expose paginated full details',async()=>{
 const {focus,section}=await import('../dist/view.js');
 const cards=Array.from({length:20},(_,i)=>({uuid:`uuid-${i}`,id:`Card${i}`,name:`카드 ${i}`,upgrades:i%2,rarity:'COMMON',type:'SKILL',cost:1,description:`카드 ${i}의 상세 설명 `.repeat(20),tooltips:[{title:'키워드',description:'상세 툴팁 '.repeat(10)}]}));
 const state={session_id:'s',state_id:7,ready:true,actions:cards.map((_,i)=>({id:`choose.${i}`,label:`카드 ${i}`,parameters:{index:i}})),observation:{game_state:{screen_type:'GRID',current_hp:50,max_hp:70,gold:120,deck:cards,screen_state:{cards,selected_cards:[],num_cards:1,for_purge:true,for_upgrade:false,for_transform:false,confirm_up:false}}}};
 const view=focus(state);assert.equal(view.deck,undefined);assert.equal(view.deck_count,20);assert.equal(view.screen_state.cards.length,20);assert.equal(view.screen_state.cards[0].description,undefined);assert.equal(view.screen_state.cards[0].uuid,undefined);assert.equal(view.screen_state.details_on_demand.section,'screen');assert.ok(JSON.stringify(view).length<5000,JSON.stringify(view).length);
 const page=section(state,'screen',0,30);assert.equal(page.total,20);assert.equal(page.items.length,6);assert.equal(page.next_offset,6);assert.match(page.items[0].value.description,/상세 설명/);assert.equal(page.items[0].collection,'cards');
 const deck=section(state,'deck',0,100);assert.equal(deck.items.length,12);assert.equal(deck.next_offset,12);
});
