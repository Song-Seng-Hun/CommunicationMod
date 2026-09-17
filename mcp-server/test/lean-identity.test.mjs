import test from 'node:test';
import assert from 'node:assert/strict';
import {decision,readContext} from '../dist/context.js';

const base=(screen,actions=[])=>({session_id:'s',state_id:1,ready:true,actions,observation:{game_state:{screen_type:screen,current_hp:40,max_hp:60,gold:99,screen_state:{}}}});

test('map identity and revision stay hidden while route nodes remain writable',()=>{
 const parameters={type:'object',additionalProperties:false,properties:{map_id:{type:'string',const:'map-secret'},revision:{type:'integer',const:17},nodes:{type:'array',items:{type:'string'}}},required:['map_id','revision','nodes']};
 const s=base('MAP',[{id:'run.map.plan',label:'경로 계획',parameters}]);
 s.observation.game_state.map_plan={map_id:'map-secret',revision:17,route_author:'human',route:['1,2','2,3'],route_count:2,planned_start:'1,2',current_node:'1,2',next_planned_node:'2,3',status:'on_route',stroke_count:1,editing:false,scope:'annotation_only_session',edge_policy:'drawn_edges_only'};
 s.observation.game_state.map=[{x:1,y:2,symbol:'M',children:[{x:2,y:3}]}];
 const v=decision(s);assert.equal(v.actions[0].id,'a0');assert.equal(v.map_plan.map_id,undefined);assert.equal(v.map_plan.revision,undefined);assert.equal(v.map_plan.next_planned_node,'2,3');
 const schemaRef=v.actions[0].parameters_ref,schema=readContext(s,[schemaRef]).fragments[0];assert.ok(!JSON.stringify(schema).includes('map-secret'));assert.ok(!JSON.stringify(schema).includes('revision'));
 const properties=readContext(s,[schemaRef+'/properties']).fragments[0];assert.deepEqual(properties.toc.map(x=>x.ref),[schemaRef+'/properties/nodes']);
 const required=readContext(s,[schemaRef+'/required']).fragments[0];assert.deepEqual(required.items,['nodes']);
 const plan=readContext(s,['map_plan']).fragments[0];assert.equal(plan.data.map_id,undefined);assert.equal(plan.data.revision,undefined);assert.ok(plan.toc.some(x=>x.ref==='map_plan/route'));
 const route=readContext(s,['map_plan/route']).fragments[0];assert.deepEqual(route.items,['1,2','2,3']);assert.equal(route.toc,undefined);
 assert.throws(()=>readContext(s,['map_plan/map_id']),/not available/);assert.throws(()=>readContext(s,['map_plan/revision']),/not available/);
});

test('event reading identity stays server-side while current page facts remain visible',()=>{
 const parameters={type:'object',additionalProperties:false,properties:{reading_id:{type:'string',enum:['opaque-reading-id']},commentary:{type:'string'}},required:['reading_id','commentary']};
 const s=base('EVENT',[{id:'acknowledge_event_reading',label:'본문 확인',parameters}]);
 s.observation.game_state.screen_state={event_id:'InternalEventId',event_name:'낡은 탑',body_text:'중복 본문',body_text_source:'rendered',event_reading:{reading_id:'opaque-reading-id',phase:'discussion_required',unavailable_reason:null,page_role:'before_choice',body_text:'실제 본문',text_language:'KOR',text_complete:true,can_choose:false,options:[{text:'붉은 문',disabled:false,choice_index:0},{text:'잠긴 문',disabled:true}],commentary:null,last_discussion:null,support_status:'partial',completion_basis:'rendered'}};
 const v=decision(s);assert.equal(v.actions[0].id,'a0');assert.equal(v.event_reading.reading_id,undefined);assert.equal(v.event_reading.phase,'discussion_required');assert.equal(v.event_reading.body_ref,'screen/event_reading/body_text');assert.equal(v.screen_state.body_text,undefined);assert.equal(v.screen_state.event_id,undefined);assert.equal(v.screen_state.event_name,'낡은 탑');
 const schemaRef=v.actions[0].parameters_ref,schema=readContext(s,[schemaRef]).fragments[0];assert.ok(!JSON.stringify(schema).includes('opaque-reading-id'));
 const properties=readContext(s,[schemaRef+'/properties']).fragments[0];assert.deepEqual(properties.toc.map(x=>x.ref),[schemaRef+'/properties/commentary']);
 const required=readContext(s,[schemaRef+'/required']).fragments[0];assert.deepEqual(required.items,['commentary']);
 const reading=readContext(s,['screen/event_reading']).fragments[0];assert.equal(reading.data.reading_id,undefined);assert.equal(reading.data.phase,'discussion_required');assert.ok(reading.toc.some(x=>x.ref==='screen/event_reading/options'));
 assert.throws(()=>readContext(s,['screen/event_reading/reading_id']),/not available/);
});

test('control trees expose short action aliases instead of native action IDs',()=>{
 const s=base('COMBAT_REWARD',[{id:'run.reward.0',label:'골드',parameters:{}},{id:'run.reward.1',label:'유물',parameters:{}}]);
 s.observation.reward_controls=[{id:'run.reward.0',label:'골드',supported:true,pending:false,ignored:false,claimable:true,mutually_exclusive_with:'run.reward.1'},{id:'internal-no-action',label:'대기 중',supported:false,pending:true,ignored:false,claimable:false}];
 const page=readContext(s,['reward_controls']).fragments[0],text=JSON.stringify(page);assert.ok(text.includes('a0'));assert.ok(text.includes('a1'));assert.ok(!text.includes('run.reward.'));assert.ok(!text.includes('internal-no-action'));
});

test('normal converter metadata does not masquerade as unsupported information',()=>{
 const s=base('NONE');Object.assign(s.observation.game_state,{screen_name:'NONE',is_screen_up:false,room_phase:'COMBAT',action_phase:'WAITING_ON_USER',room_type:'MonsterRoom',act_boss:'boss',seed:7,ascension_level:0,relics:[],potions:[],keys:{ruby:false,emerald:false,sapphire:false},narrative:{entries:[]}});
 assert.equal(decision(s).unsupported_information,undefined);
 s.observation.game_state.truly_unknown={x:1};assert.equal(decision(s).unsupported_information,true);
});

test('dialogue history omits observer bookkeeping but keeps semantic prose and context',()=>{
 const s=base('EVENT');s.observation.game_state.narrative={session_id:'history-secret',revision:9,render_frame:77,dropped_entries:0,situation:{room_visit:'4'},support_status:'partial',visibility_basis:'rendered',unavailable_reasons:['custom_renderers_not_verified','pixel_occlusion_not_verified'],entries:[{id:41,text:'상인 인사',speaker_type:'npc',speaker_name:'상인',text_language:'KOR',context:{floor:'8',act:'1',room_visit:'4',channel:'speech'},currently_displayed:true,visible_text:'상인 인사',text_truncated:false}]};
 const history=readContext(s,['history']).fragments[0],raw=JSON.stringify(history);assert.ok(!raw.includes('history-secret'));assert.ok(!raw.includes('render_frame'));assert.ok(!raw.includes('room_visit'));assert.ok(!raw.includes('"id":41'));assert.ok(history.toc.some(x=>x.ref==='history/entries'));
 const entry=readContext(s,['history/entries/0']).fragments[0].data;assert.equal(entry.text,'상인 인사');assert.equal(entry.speaker_name,'상인');assert.equal(entry.context,undefined);
 const context=readContext(s,['history/entries/0/context']).fragments[0].data;assert.equal(context.floor,'8');assert.equal(context.act,'1');assert.equal(context.room_visit,undefined);assert.equal(context.channel,undefined);
});
