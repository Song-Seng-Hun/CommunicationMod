import assert from 'node:assert/strict';
import {fixture} from './fixture.mjs';
import {focus,section,mapSummary} from './baseline/view-v1.mjs';
import {conditionalDecision,readContext} from '../dist/context.js';

// Deterministic public fixtures, not actual game sessions or autonomous model decisions.
// Both variants must recover the SAME task facts; count every extra request and reply.
export function workflows(){
 const cases=[];
 function trace(name,state,check){
  const old=[],next=[];let view;
  const ids={session_id:state.session_id,state_id:state.state_id};
  const oldCall=(tool,args={})=>{const data=tool==='sts_get_state'?(args.view==='map_plan'?mapSummary(state):focus(state)):section(state,args.section,args.offset??0,args.limit??30);old.push({request:{name:tool,arguments:args},response:data});return data;};
  const newCall=(tool,args={})=>{const data=tool==='sts_get_state'?conditionalDecision(state,args.known_view,args.view):readContext(state,args.refs,args.offset??0,args.limit??20);next.push({request:{name:tool,arguments:args},response:data});if(tool==='sts_get_state'&&!data.unchanged)view=data;return data;};
  const oldState=oldCall('sts_get_state'),newState=newCall('sts_get_state');
  const read=(refs,extra={})=>newCall('sts_get_context',{...ids,refs,...extra}).fragments;
  const legacy=(sectionName,extra={})=>oldCall('sts_get_context',{...ids,section:sectionName,...extra});
  check({oldState,newState,read,legacy,oldCall,newCall,ids});
  // Same two deliberate read-only polls in each trace; conditional polling is explicit.
  for(let i=0;i<2;i++){oldCall('sts_get_state');newCall('sts_get_state',{known_view:view.view_id});}
  cases.push({name,state:structuredClone(state),before:old,after:next});
 }
 const combat=structuredClone(fixture);
 trace('combat_current_card',combat,({oldState,newState,read})=>{
  assert.equal(newState.combat.monsters[0].current_hp,oldState.combat.monsters[0].current_hp);
  assert.equal(newState.combat.hand[2].description,oldState.combat.hand[2].description);
  const card=read([newState.combat.hand[2].ref])[0].data;
  assert.equal(card.tooltips[0].description,oldState.combat.hand[2].tooltips[0].description);
 });
 const event=structuredClone(fixture);event.state_id++;event.observation.game_state.screen_type='EVENT';delete event.observation.game_state.combat_state;
 const body='당신은 갈림길 앞에 섰다. 바람이 오래된 문을 흔든다. '.repeat(24);
 event.observation.game_state.screen_state={event_reading:{body_text:body,reading_id:'reading-1',options:[{text:'문을 연다'},{text:'떠난다'}]}};
 event.actions=[{id:'event.acknowledge',label:'본문 읽음 확인',parameters:{reading_id:{type:'string'},commentary:{type:'string'}}}];
 trace('event_read_exact_story',event,({oldState,newState,read})=>{
  assert.equal(newState.event_reading.reading_id,oldState.screen_state.event_reading.reading_id);
  let text='',offset=0;do{const part=read([newState.event_reading.body_ref],{offset})[0];text+=part.text;offset=part.next_offset;}while(offset!==null);
  assert.equal(text,oldState.screen_state.event_reading.body_text);
  assert.deepEqual(newState.event_reading.options.map(x=>x.text),oldState.screen_state.event_reading.options.map(x=>x.text));
 });
 const map=structuredClone(fixture);map.state_id+=2;map.observation.game_state.screen_type='MAP';delete map.observation.game_state.combat_state;
 map.observation.game_state.map_plan={map_id:'map-1',revision:2,current_node:'0,0',next_planned_node:'0,1',route:['0,0','0,1'],status:'on_route',route_author:'human'};
 trace('map_current_route',map,({oldState,newState,read,legacy})=>{
  assert.equal(newState.map_plan.next_planned_node,oldState.map_plan.next_planned_node);
  const directory=read(['map_plan/route'])[0];const rows=read(directory.toc.map(x=>x.ref));
  assert.deepEqual(rows.map(x=>x.text),legacy('map_plan').data.route);
 });
 const shop=structuredClone(fixture);shop.state_id+=3;shop.observation.game_state.screen_type='SHOP_SCREEN';delete shop.observation.game_state.combat_state;
 shop.observation.game_state.screen_state={cards:[{...shop.observation.game_state.deck[0],upgrade_preview:{status:'available',after:{name:'일격+',description:'피해 9.',base_damage:9},can_upgrade_after:false}}]};
 shop.observation.shop_controls=[{id:'run.shop.card.a',price:75,available:true}];shop.actions=[{id:'run.shop.card.a',label:'일격',parameters:{}}];
 trace('shop_price_upgrade_comparison',shop,({oldState,newState,read})=>{
  assert.equal(newState.shop_controls[0].price,oldState.shop_controls[0].price);
  const card=read([newState.offers[0].ref])[0];assert.equal(card.data.name,oldState.screen_state.cards[0].name);
  assert.equal(card.data.upgrade_preview.after.description,oldState.screen_state.cards[0].upgrade_preview.after.description);
 });
 const collection=structuredClone(fixture);collection.state_id+=4;
 collection.observation.game_state.combat_state.player={mechanics:{reserves:4,information_complete:false,collection:{count:135,cards_complete:true,order_visible:false,cards:Array.from({length:135},(_,i)=>({id:`card-${i}`,name:`수집 카드 ${i}`,description:`저장한 공개 효과 ${i}.`,displayed_cost_complete:false}))}}};
 trace('collection_last_card',collection,({newState,read,legacy})=>{
  assert.ok(newState.toc.some(x=>x.ref==='collection'));
  const collectionInfo=read(['collection'])[0];assert.equal(collectionInfo.data.count,135);
  const dir=read(['collection/cards'],{offset:130,limit:30})[0];assert.equal(dir.next_offset,null);
  const last=read([dir.toc.at(-1).ref])[0].data;
  assert.equal(last.description,legacy('collection',{offset:130,limit:30}).items.at(-1).description);
  assert.equal(last.displayed_cost_complete,false);
 });
 return cases;
}
