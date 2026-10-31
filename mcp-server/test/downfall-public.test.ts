import test from 'node:test';
import assert from 'node:assert/strict';
import {decision,conditionalDecision,readContext} from '../dist/context.js';

const wheel=()=>({ready:true,session_id:'offline',state_id:1,actions:[{id:'run.wheel.spin',parameters:{}}],observation:{game_state:{screen_type:'EVENT'},wheel_controls:{spin_available:true}}});
test('event relic preview is discoverable without repeating its tooltip in decision and disappears with its page',()=>{
 const tip={title:'Stasis Egg',description:'Lose 3 HP. Do not discard.',description_complete:true};
 const s={...wheel(),actions:[],observation:{game_state:{screen_type:'EVENT',screen_state:{options:[{text:'Take the egg',disabled:false,relic_preview:{id:'egg',name:'Stasis Egg',tooltips:[tip],tooltips_complete:true}}]}}}};
 const row=readContext(s,['screen/options/0']).fragments[0];
 assert.ok(row.toc.some(x=>x.ref==='screen/options/0/relic_preview'));
 const detail=readContext(s,['screen/options/0/relic_preview']).fragments[0];
 assert.ok(detail.toc.some(x=>x.ref==='screen/options/0/relic_preview/tooltips'));
 assert.deepEqual({...readContext(s,['screen/options/0/relic_preview/tooltips/0']).fragments[0].data},tip);
 assert.ok(!JSON.stringify(decision(s)).includes(tip.description));
 s.observation.game_state={screen_type:'MAP'};
 assert.throws(()=>readContext(s,['screen/options/0/relic_preview']),/not available/);
});
test('random conjure snapshot retains public spells but has no implied next marker',()=>{
 const row={count:2,card:{id:'spell',name:'Spell',description:'Deal 3 damage.'}};
 const s={...wheel(),actions:[],observation:{game_state:{screen_type:'NONE',combat_state:{hand_complete:true,hand:[],player:{mechanics:{spells:[row],spells_complete:true}}}}}};
 assert.equal(readContext(s,['mechanics/spells/0']).fragments[0].data.count,2);
 assert.equal(readContext(s,['mechanics/spells/0']).fragments[0].data.up_next,undefined);
 assert.throws(()=>readContext(s,['mechanics/spells/0/up_next']),/not available/);
 const before=conditionalDecision(s);row.up_next=true;s.state_id++;
 assert.equal(readContext(s,['mechanics/spells/0']).fragments[0].data.up_next,true);
 assert.notEqual(conditionalDecision(s).view_id,before.view_id,'new public native state invalidates view without inlining spell details');
});
test('match controls expose current public positions and aliases only in the event',()=>{
 const s={...wheel(),actions:[{id:'run.match.flip.0',parameters:{}}],observation:{game_state:{screen_type:'EVENT'},match_controls:{flip_available:true,cards:[{position:0,face_up:false,available:true,action_id:'run.match.flip.0'},{position:1,face_up:true,available:false,card:{id:'visible',description:'Lose 2 HP.'}}]}}};
 assert.ok(decision(s).toc.some(x=>x.ref==='match_controls'));
 assert.equal(readContext(s,['match_controls/cards/0']).fragments[0].data.action,'a0');
 assert.equal(readContext(s,['match_controls/cards/1/card']).fragments[0].data.id,'visible');
 assert.ok(!JSON.stringify(readContext(s,['match_controls/cards/0'])).includes('run.match'));
 assert.throws(()=>readContext(s,['match_controls/cards/0/card']),/not available/);
 const example=readContext(s,['guidance/event.match/normal']).fragments[0].data;
 assert.match(example.requires,/acknowledg/i);assert.match(example.requires,/face.down/i);
 s.observation.game_state.screen_type='MAP'; // Even a lingering native action cannot reopen an EVENT-only root.
 assert.throws(()=>readContext(s,['match_controls']),/not available/);
 assert.throws(()=>readContext(s,['guidance/event.match/normal']),/not available/);
});
test('match controls hide face-down identity on every ref and keep the default decision bounded',()=>{
 const s={...wheel(),actions:[{id:'run.match.flip.0',parameters:{}}],observation:{game_state:{screen_type:'EVENT'},match_controls:{flip_available:true,attempts_remaining:4,cards:[{position:0,face_up:false,available:true,action_id:'run.match.flip.0',id:'hidden-id',name:'Hidden name',card:{id:'hidden-card',description:'Hidden effect'}}]}}};
 const root=readContext(s,['match_controls']).fragments[0];assert.equal(root.data.attempts_remaining,4);
 const row=readContext(s,['match_controls/cards/0']).fragments[0];assert.deepEqual({...row.data},{position:0,face_up:false,available:true,action:'a0'});
 for(const leaf of ['id','name','card','%63ard/id','action_id'])assert.throws(()=>readContext(s,['match_controls/cards/0/'+leaf]),/not available/);
 assert.ok(!JSON.stringify(decision(s)).includes('attempts_remaining'));
 assert.ok(!JSON.stringify(decision(s)).includes('Hidden'));
 delete s.observation.match_controls;
 assert.throws(()=>readContext(s,['guidance/event.match/normal']),/not available/);
});
test('wheel continuation exposes its control and acknowledgement-safe example only in context',()=>{
 const s=wheel();assert.ok(decision(s).toc.some(x=>x.ref==='wheel_controls'));
 assert.equal(readContext(s,['wheel_controls']).fragments[0].data.spin_available,true);
 const c=readContext(s,['guidance/event.choice/normal']).fragments[0].data;
 assert.match(c.requires,/spin_available/);assert.match(c.requires,/acknowledg/i);
 s.actions=[];s.observation.game_state.screen_type='MAP';
 assert.throws(()=>readContext(s,['wheel_controls']),/not available/);
 assert.throws(()=>readContext(s,['guidance/event.choice/normal']),/not available/);
});
test('native public preview fragments preserve costs, negation and incomplete variants',()=>{
 const preview={count:1,cards_complete:false,variant:'base_catalog',unavailable_reason:'upgraded_gallery_context_not_captured',order_visible:false,cards:[{id:'visible',description:'Lose 3 HP. Cannot retain.',cost:0,exhausts:true}]};
 const s={...wheel(),actions:[],observation:{game_state:{screen_type:'NONE',combat_state:{hand_complete:true,hand:[{id:'Unknown',previews:preview}],monsters:[{name:'Enemy',collectible_preview:{card:preview.cards[0]}}]}}}};
 const summary=readContext(s,['hand/0/previews']).fragments[0];
 assert.deepEqual({...summary.data},Object.fromEntries(Object.entries(preview).filter(([key])=>key!=='cards')));
 assert.ok(summary.toc.some(x=>x.ref==='hand/0/previews/cards'));
 assert.deepEqual({...readContext(s,['hand/0/previews/cards/0']).fragments[0].data},preview.cards[0]);
 assert.deepEqual({...readContext(s,['monsters/0/collectible_preview/card']).fragments[0].data},preview.cards[0]);
 s.observation.game_state.screen_type='MAP';assert.throws(()=>readContext(s,['monsters/0/collectible_preview']),/not available/);
});
test('upgraded public gallery retains effective values and explicit unsupported candidates',()=>{
 const cards=[{id:'Strike_R',upgrades:1,base_damage:9,base_cost:1,description:'Deal 9 damage.',description_complete:true},{id:'branch',status:'unavailable',reason:'upgrade_choice_required_use_native_preview'}];
 const previews={source:'public_unknown_gallery_catalog',count:2,variant:'upgraded',cards_complete:false,order_visible:false,cards};
 const s={...wheel(),actions:[],observation:{game_state:{screen_type:'NONE',combat_state:{hand_complete:true,hand:[{id:'Unknown',previews}]}}}};
 const directory=readContext(s,['hand/0/previews']).fragments[0];assert.equal(directory.data.variant,'upgraded');assert.equal(directory.data.cards_complete,false);
 const pages=readContext(s,['hand/0/previews/cards/0','hand/0/previews/cards/1']).fragments;
 for(let i=0;i<cards.length;i++)assert.deepEqual({...pages[i].data},cards[i]);
 assert.equal(pages[1].data.base_damage,undefined);
});
test('Collector reward payload retains native essence cost and exact card through discoverable refs',()=>{
 const upgrade={status:'available',scope:'next_standard_upgrade',steps:1,event_effects_predicted:false,on_obtain_effects_predicted:false,after:{id:'collector:VisibleCard',description:'Lose 2 HP. Exhaust.',base_cost:0,exhausts:true,displayed_cost_complete:false}};
 const s={...wheel(),actions:[{id:'run.reward.0',parameters:{}}],observation:{game_state:{screen_type:'COMBAT_REWARD'},reward_controls:[{id:'run.reward.0',kind:'collector_collectiblecardreward',claimable:true,native_reward:{currency:'essence',cost:3,essence:4,card:{id:'collector:VisibleCard',name:'Collectible',description:'Lose 3 HP. Exhaust.',exhausts:true,previews:{cards:[{id:'native-preview',name:'Visible'}]},upgrade_preview:upgrade}}}]}};
 const row=readContext(s,['reward_controls/0']).fragments[0];assert.ok(row.toc.some(x=>x.ref==='reward_controls/0/native_reward'));assert.equal(row.data.action,'a0');assert.ok(!JSON.stringify(row).includes('run.reward.'));
 const detail=readContext(s,['reward_controls/0/native_reward']).fragments[0];assert.equal(detail.data.currency,'essence');assert.equal(detail.data.cost,3);assert.equal(detail.data.essence,4);
 assert.ok(detail.toc.some(x=>x.ref==='reward_controls/0/native_reward/card'));
 assert.equal(readContext(s,['reward_controls/0/native_reward/card']).fragments[0].data.exhausts,true);
 assert.equal(readContext(s,['reward_controls/0/native_reward/card/id']).fragments[0].text,'collector:VisibleCard');
 assert.equal(readContext(s,['reward_controls/0/native_reward/card/previews/cards/0/id']).fragments[0].text,'native-preview');
 const comparison=readContext(s,['reward_controls/0/native_reward/card/upgrade_preview']).fragments[0];assert.equal(comparison.data.status,'available');assert.equal(comparison.data.on_obtain_effects_predicted,false);
 assert.deepEqual({...readContext(s,['reward_controls/0/native_reward/card/upgrade_preview/after']).fragments[0].data},upgrade.after);
 s.observation.reward_controls[0].native_reward.card.upgrade_preview={status:'unavailable',reason:'decision_context_not_captured'};
 assert.equal(readContext(s,['reward_controls/0/native_reward/card/upgrade_preview']).fragments[0].data.reason,'decision_context_not_captured');
});
