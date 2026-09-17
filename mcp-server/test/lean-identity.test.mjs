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
 const schema=readContext(s,[v.actions[0].parameters_ref]).fragments[0].data;assert.deepEqual(Object.keys(schema.properties),['nodes']);assert.deepEqual(schema.required,['nodes']);assert.ok(!JSON.stringify(schema).includes('map-secret'));assert.ok(!JSON.stringify(schema).includes('revision'));
 const plan=readContext(s,['map_plan']).fragments[0];assert.equal(plan.data.map_id,undefined);assert.equal(plan.data.revision,undefined);assert.ok(plan.toc.some(x=>x.ref==='map_plan/route'));
 assert.throws(()=>readContext(s,['map_plan/map_id']),/not available/);assert.throws(()=>readContext(s,['map_plan/revision']),/not available/);
});

test('event reading identity stays server-side while current page facts remain visible',()=>{
 const parameters={type:'object',additionalProperties:false,properties:{reading_id:{type:'string',enum:['opaque-reading-id']},commentary:{type:'string'}},required:['reading_id','commentary']};
 const s=base('EVENT',[{id:'acknowledge_event_reading',label:'본문 확인',parameters}]);
 s.observation.game_state.screen_state={event_id:'InternalEventId',event_name:'낡은 탑',body_text:'중복 본문',body_text_source:'rendered',event_reading:{reading_id:'opaque-reading-id',phase:'discussion_required',unavailable_reason:null,page_role:'before_choice',body_text:'실제 본문',text_language:'KOR',text_complete:true,can_choose:false,options:[{text:'붉은 문',disabled:false,choice_index:0},{text:'잠긴 문',disabled:true}],commentary:null,last_discussion:null,support_status:'partial',completion_basis:'rendered'}};
 const v=decision(s);assert.equal(v.actions[0].id,'a0');assert.equal(v.event_reading.reading_id,undefined);assert.equal(v.event_reading.phase,'discussion_required');assert.equal(v.event_reading.body_ref,'screen/event_reading/body_text');assert.equal(v.screen_state.body_text,undefined);assert.equal(v.screen_state.event_id,undefined);assert.equal(v.screen_state.event_name,'낡은 탑');
 const schema=readContext(s,[v.actions[0].parameters_ref]).fragments[0].data;assert.deepEqual(Object.keys(schema.properties),['commentary']);assert.ok(!JSON.stringify(schema).includes('opaque-reading-id'));
 const reading=readContext(s,['screen/event_reading']).fragments[0];assert.equal(reading.data.reading_id,undefined);assert.equal(reading.data.phase,'discussion_required');assert.ok(reading.toc.some(x=>x.ref==='screen/event_reading/options'));
 assert.throws(()=>readContext(s,['screen/event_reading/reading_id']),/not available/);
});

test('control trees expose short action aliases instead of native action IDs',()=>{
 const s=base('COMBAT_REWARD',[{id:'run.reward.0',label:'골드',parameters:{}},{id:'run.reward.1',label:'유물',parameters:{}}]);
 s.observation.reward_controls=[{id:'run.reward.0',label:'골드',supported:true,pending:false,ignored:false,claimable:true,mutually_exclusive_with:'run.reward.1'},{id:'internal-no-action',label:'대기 중',supported:false,pending:true,ignored:false,claimable:false}];
 const page=readContext(s,['reward_controls']).fragments[0];const text=JSON.stringify(page);assert.ok(text.includes('a0'));assert.ok(text.includes('a1'));assert.ok(!text.includes('run.reward.'));assert.ok(!text.includes('internal-no-action'));
});
