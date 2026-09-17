// Curated synthetic observations, not a game simulator or an agent policy.
import assert from 'node:assert/strict';
import {conditionalDecision,readContext} from '../dist/context.js';
export function fixtureScenario(fixture){
 const id=fixture.capability,action=fixture.action??(id==='action.parameters'?'run.map.plan':'run.room.proceed');
 let screen=id.startsWith('shop.')?'SHOP_SCREEN':id.startsWith('rest.')?'REST':
  id.startsWith('selection.grid')||id==='selection.upgrade'?'GRID':id.startsWith('selection.hand')?'HAND_SELECT':
  id.startsWith('reward.')?'CARD_REWARD':id.startsWith('boss.')?'BOSS_REWARD':id==='chest.open'?'CHEST':
  id.startsWith('map.')||id==='action.parameters'?'MAP':id.startsWith('event.')||id==='narrative.history'?'EVENT':
  id.startsWith('menu.')?'MAIN_MENU':'NONE';
 const card={id:'observed-card',uuid:'observed-uuid',description:'피해 12. HP 3 감소.',description_complete:true,
  displayed_cost_text:fixture.cover==='resource.X'?'X':'1',displayed_cost_complete:true,cost_components_complete:true,
  cost_components:{energy:1,reserves:0,pyre:0},has_target:!action.endsWith('.-1'),is_playable:true,
  ...(!action.endsWith('.-1')?{target_playability:[{monster_index:0,available:true,name:'Visible foe'}]}:{}),
  tooltips:[{title:'소멸',description:'재사용 불가'}],upgrade_preview:{status:'available',after:{description:'피해 15. HP 3 감소.'}},
  upgrade_selection_preview:{after:{description:'피해 15. HP 3 감소.'}}};
 const resource=fixture.cover.startsWith('resource.')?fixture.cover.slice(9):'energy';
 const g={screen_type:screen,current_hp:20,max_hp:50,gold:75,deck:[card,{...card,id:'second-card'}],
  mechanics:{information_complete:true,[resource]:{value:resource==='X'?'X':2,complete:true},collection:{count:1,cards_complete:true,order_visible:false,cards:[card]}},
  screen_state:{cards:[card]}};
 const o={game_state:g},parameters={};
 const state={type:'state',session_id:'fixture-session',state_id:17,ready:true,actions:[{id:action,parameters}],observation:o};
 let refs=['deck/0'];
 if(screen==='NONE'||screen==='HAND_SELECT'){
  g.combat_state={hand_complete:screen!=='HAND_SELECT',hand:[card],player:{energy:3,mechanics:g.mechanics},monsters:[{name:'Visible foe',current_hp:12}],draw_pile:[card],draw_pile_order_visible:false};
  if(screen==='NONE')refs=id==='resources.public'?['mechanics']:id==='cards.collections'?['piles']:['hand/0','monsters/0'];
  else{o.combat_decision={mode:'selection',hand_complete:false};o.selection_controls={selected_count:0,required_count:1};refs=['selection_controls','screen/cards/0'];}
 }
 if(screen==='SHOP_SCREEN'){o.shop_controls=[{id:action,price:75,available:true}];refs=['shop_controls/0','screen/cards/0'];}
 if(screen==='REST'){o.rest_controls=[{id:action,available:true,description:'Heal 15 HP'}];refs=['rest_controls/0','deck/0'];}
 if(screen==='CARD_REWARD'||screen==='BOSS_REWARD'){
  o.reward_controls=[{id:action,type:fixture.cover.slice(7),available:true,mutually_exclusive_with:'run.reward.1'}];
  o.reward_navigation={unclaimed_rewards:1,proceed_abandons_rewards:true};
  o.card_reward_header={mode:fixture.cover.startsWith('card_mode.')?fixture.cover.slice(10):'reward'};
  refs=['reward_controls/0','reward_navigation','screen/cards/0'];
 }
 if(id.startsWith('potion.')){o.potion_controls=[{id:action,slot:0,target_required:fixture.cover==='potion.targeted',available:true,description:'Heal 10 HP'}];refs=['potion_controls/0'];}
 if(screen==='GRID'){
  o.selection_controls={selected_count:1,required_count:1};
  if(id==='selection.grid.confirm'){
   g.screen_state.for_upgrade=true;
   g.screen_state.upgrade_selection_preview={after:{description:'피해 15. HP 3 감소.'}};
  }
  if(id==='selection.upgrade')o.selection_controls.upgrade_choice={kind:fixture.cover.slice(8),choice_required:true,candidates:[{action_id:action,after:{description:'피해 15.'}}],selected_after:{description:'피해 15.'}};
  else if(fixture.cover==='grid.confirm.branch')o.selection_controls.upgrade_choice={kind:'branch',choice_required:false,selected_after:{description:'피해 16.'}};
  refs=['selection_controls','screen/cards/0'];
 }
 if(screen==='MAP'){
  g.map=[{x:0,y:0,symbol:'M',parents:[],children:[{x:0,y:1}]},{x:0,y:1,symbol:'?',parents:[{x:0,y:0}],children:[{x:1,y:2}]},{x:1,y:2,symbol:'R',parents:[{x:0,y:1}],children:[]}];
  g.map_plan={map_id:'observed-map',revision:4,current_node:'0,0'};
  refs=['map','map_plan'];
  if(action==='run.map.plan')Object.assign(parameters,{type:'object',required:['map_id','revision','nodes'],additionalProperties:false,properties:{map_id:{type:'string',const:'observed-map'},revision:{type:'integer',const:4},nodes:{type:'array',maxItems:64,items:{type:'string'}}}});
 }
 if(screen==='EVENT'){
  g.screen_state={event_reading:{reading_id:'observed-reading',body_text:'본문: HP 3 소모. 취소 불가.',body_complete:true,options_complete:true,options:[{text:'떠난다'}]}};
  g.narrative={entries:[{text:'현재 대화',currently_displayed:true}]};
  refs=id==='narrative.history'?['history']:['screen/event_reading/body_text','screen/event_reading/options'];
  if(id==='event.ack')Object.assign(parameters,{type:'object',required:['reading_id','commentary'],additionalProperties:false,properties:{reading_id:{type:'string',const:'observed-reading'},commentary:{type:'string',minLength:1,maxLength:4096}}});
 }
 if(screen==='MAIN_MENU'){o.menu={screen,choices:[{id:action,available:true}]};refs=['menu'];}
 if(id==='tutorial.confirm'){o.tutorial={text:'확인 전 읽기',available:true};refs=['tutorial'];}
 return {state,refs,permission:true,discardPermission:id==='potion.discard',evidenceComplete:true,
  commentary:'본문: HP 3 소모. 취소 불가. 떠난다.',nodes:['0,1','1,2'],request_id:'observed-request'};
}
export function validateArguments(schema,args){
 if(!Object.keys(schema).length){assert.deepEqual(args,{});return;}
 for(const required of schema.required??[])assert.ok(Object.hasOwn(args,required),'missing offered argument '+required);
 if(schema.additionalProperties===false)assert.ok(Object.keys(args).every(k=>Object.hasOwn(schema.properties,k)),'unknown offered argument');
 for(const [key,spec] of Object.entries(schema.properties??{})){
  const v=args[key];if(v===undefined)continue;
  if(Object.hasOwn(spec,'const'))assert.deepEqual(v,spec.const,'stale argument '+key);
  if(spec.type==='integer')assert.ok(Number.isInteger(v));else assert.equal(Array.isArray(v)?'array':typeof v,spec.type);
  if(typeof v==='string'){if(spec.minLength)assert.ok(v.length>=spec.minLength);if(spec.maxLength)assert.ok(v.length<=spec.maxLength);}
  if(spec.items?.enum)for(const item of v)assert.ok(spec.items.enum.includes(item),'unoffered node');
  if(spec.maxItems!==undefined)assert.ok(v.length<=spec.maxItems,'maxItems exceeded');
  if(spec.items?.type)for(const item of v)assert.equal(typeof item,spec.items.type,'item type');
 }
}
export function assertActionEvidence(f){
 assert.ok(f.permission,'permission missing');assert.equal(f.state.ready,true,'unready');
 assert.ok(!f.pending,'pending outcome');assert.ok(f.evidenceComplete,'incomplete evidence');
 const a=f.state.actions[0];assert.ok(a,'unoffered action');
 if(a.id.startsWith('run.potion.discard.'))assert.ok(f.discardPermission,'discard permission missing');
 if(a.id.startsWith('run.play.')){
  const combat=f.state.observation.game_state.combat_state,card=combat.hand[0];
  assert.equal(combat.hand_complete,true,'incomplete hand');assert.equal(card.displayed_cost_complete,true,'unknown cost');
  assert.equal(card.cost_components_complete,true,'incomplete cost components');
  assert.equal(card.is_playable,true,'unplayable card');
  const target=Number(a.id.slice(a.id.lastIndexOf('.')+1));
  if(card.has_target===true)assert.ok(card.target_playability?.some(t=>t.monster_index===target&&t.available===true),'unplayable target');
  else {assert.equal(card.has_target,false,'unknown target requirement');assert.equal(target,-1,'invalid untargeted index');}
 }
 if(a.id==='acknowledge_event_reading'){
  const e=f.state.observation.game_state.screen_state.event_reading;
  assert.ok(e.body_complete&&e.options_complete,'incomplete event');assert.ok(f.commentary,'event not presented');
 }
 if(a.id==='run.grid.confirm' && f.state.observation.game_state.screen_state.for_upgrade){
  const choice=f.state.observation.selection_controls.upgrade_choice;
  if(choice){assert.equal(choice.choice_required,false,'branch choice unfinished');assert.ok(choice.selected_after,'branch choice preview missing');}
  else assert.ok(f.state.observation.game_state.screen_state.upgrade_selection_preview?.after,'ordinary preview missing');
 }
}
export function resolveBindings(capsule,f){
 const view=conditionalDecision(f.state),action=f.state.actions[0];
 const paged=Object.values(capsule.bindings).some(b=>b.source==='last.fragment.next_offset');
 const previousFragment=paged?readContext(f.state,['deck'],0,1).fragments[0]:undefined;
 // Agent-derived inputs are explicit selections, validated against observed contracts.
 const args=action.id==='run.map.plan'?{map_id:action.parameters.properties.map_id.const,revision:action.parameters.properties.revision.const,nodes:f.nodes}:
  action.id==='acknowledge_event_reading'?{reading_id:view.event_reading.reading_id,commentary:f.commentary}:{};
 const inputs={
  'previous.view_id':view.view_id,'current.session_id':view.session_id,'current.state_id':view.state_id,
  'current.observed_refs':paged?[previousFragment.ref]:f.refs,'last.fragment.next_offset':previousFragment?.next_offset,'current.offered_action.id':action.id,'last.request_id':f.request_id,
  'agent.arguments_validated_against_current.offered_action.parameters_and_complete_relevant_evidence':()=>{assertActionEvidence(f);validateArguments(action.parameters,args);return args;},
  'current.event_reading.reading_id':()=>view.event_reading.reading_id,
  'agent.commentary_already_presented_to_user_for_current.event_reading.reading_id':()=>{assertActionEvidence(f);return f.commentary;},
  'current.offered_action.parameters.properties.map_id.const':()=>action.parameters.properties.map_id.const,
  'current.offered_action.parameters.properties.revision.const':()=>action.parameters.properties.revision.const,
  'agent.selected_node_ids_from_current.observed_map_validated_against_drawn_edges_and_offered_route_schema':()=>{
   const map=readContext(f.state,['map']).fragments[0];assert.ok(map.toc);
   const g=f.state.observation.game_state;
   validateArguments(action.parameters,args);
   const nodes=new Map(g.map.map(n=>[`${n.x},${n.y}`,n]));
   assert.equal(new Set(f.nodes).size,f.nodes.length,'duplicate node');
   for(let i=0;i<f.nodes.length;i++){
    const id=f.nodes[i];assert.ok(typeof id==='string'&&id.length<=32&&nodes.has(id),'unknown node');
    if(i>0)assert.ok(nodes.get(f.nodes[i-1]).children.some(n=>`${n.x},${n.y}`===id),'not a native edge');
   }
   return f.nodes;
  }
 };
 return Object.fromEntries(Object.entries(capsule.bindings).map(([key,b])=>{
  assert.ok(Object.hasOwn(inputs,b.source),'unknown binding source '+b.source);
  const input=inputs[b.source],value=typeof input==='function'?input():input;assert.notEqual(value,undefined,'unobserved binding '+b.source);
  return [key,value];
 }));
}
