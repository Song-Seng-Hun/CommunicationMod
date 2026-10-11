import test from 'node:test';
import assert from 'node:assert/strict';
import {decision,readContext,conditionalDecision} from '../dist/context.js';

test('crowded combat never silently loses power descriptions and preserves exact native refs',()=>{
 const s=state(Array.from({length:10},(_,i)=>({...card(),name:'Card '+i,description:'Card explanation. '.repeat(20)})));
 const powers=Array.from({length:5},(_,i)=>({name:'Power '+i,type:i%2?'DEBUFF':'BUFF',amount:i-2,description:'공격에 반응하는 공개 효과입니다. '.repeat(12),description_complete:true}));
 s.observation.game_state.combat_state.monsters=Array.from({length:3},(_,i)=>({name:'Monster '+i,current_hp:100,intent:'ATTACK',powers}));
 s.observation.game_state.combat_state.player={powers};const original=JSON.stringify(s),v=decision(s);
 let omitted=0;for(const owner of [v.player,...v.combat.monsters])for(const p of owner.powers){
  assert.match(p.ref,/^(player|monsters\/\d+)\/powers\/\d+$/);
  if(p.description===undefined||p.description_truncated){assert.equal(p.details_required,true);omitted++;}
  const detail=readContext(s,[p.ref]).fragments[0];assert.equal(detail.data.name,p.name);
  const exact=readContext(s,[p.ref+'/description']).fragments[0].text;assert.equal(exact,powers[Number(p.ref.split('/').at(-1))].description);
 }
 assert.ok(omitted>0,'Fixture must exercise description omission');assert.equal(JSON.stringify(s),original);
 assert.ok(Buffer.byteLength(JSON.stringify(v))<5000,'Keep the compact decision bounded');
});

test('power directories expose effects with advancing budgeted pages and explicit incomplete text',()=>{
 const s=state([]),powers=[{name:'가시',type:'BUFF',amount:3,description:'공격받으면 피해를 3 줍니다.',description_complete:true},
  {name:'힘',type:'BUFF',amount:-1,description:'공격 피해가 1 감소합니다.',description_complete:true},
  {name:'긴 특성',amount:0,description:'효과 설명 🔥 '.repeat(60),description_complete:false,description_truncated:true},
  {name:'미확인 효과',amount:0},...Array.from({length:3},(_,i)=>({name:'Trait '+i,amount:1,description:'A current public trait.'})),
  {name:'긴'.repeat(81),type:'BUFF',amount:-1,damage:2,just_applied:true,description:'설명'.repeat(200),description_complete:false,description_truncated:true}];
 s.observation.game_state.combat_state.monsters=Array.from({length:8},(_,i)=>({name:'Enemy '+i,powers}));
 assert.equal(decision(s).combat.monsters[0].powers_more,'monsters/0/powers');
 const refs=Array.from({length:8},(_,i)=>'monsters/'+i+'/powers'),original=JSON.stringify(s);
 for(const batch of [[refs[0]],refs]){
  let offset=0;const collected=[];
  do{const result=readContext(s,batch,offset,3);const page=result.fragments[0];
   for(const p of result.fragments)assert.ok(Buffer.byteLength(JSON.stringify(p))<=Math.min(2400,Math.floor(3600/batch.length)));
   assert.equal(page.total,powers.length);assert.ok(page.toc?.length);collected.push(...page.toc);
   assert.ok(page.next_offset===undefined||page.next_offset>offset);offset=page.next_offset;
  }while(offset!==undefined);
  assert.deepEqual(collected.map(p=>p.ref),powers.map((_,i)=>refs[0]+'/'+i));
  assert.equal(collected[0].description,powers[0].description);assert.equal(collected[0].type,'BUFF');assert.equal(collected[1].amount,-1);
  for(const p of [collected[2],collected[3]])assert.equal(p.details_required,true);
  assert.equal(collected[2].description_complete,false);assert.equal(collected[2].description_truncated,true);
  assert.equal(collected[7].details_required,true);assert.equal(collected[7].description_complete,false);
 }
 assert.equal(JSON.stringify(s),original);

});

const card=()=>({id:'strike',name:'Strike',description:'Deal 6 damage.',cost:1,is_playable:true,
 displayed_cost_text:'1',displayed_cost_complete:true,cost_components_complete:true,
 cost_components:[{resource:'energy',displayed_amount:1,kind:'fixed',free_to_play_once:false}],
 cost_components_scope:'pinned_native_payment_components_not_card_effects',
 target_playability:[{available:true,monster_index:0,name:'Slime'}]});
const state=(cards=[card()])=>({session_id:'fixture',state_id:1,ready:true,actions:[],observation:{game_state:{screen_type:'NONE',combat_state:{hand_complete:true,hand:cards}}}});
const compactCard=()=>({...card(),cost_components:[{displayed_amount:1,free_to_play_once:false}],target_playability:[{available:true,monster_index:0}]});
const evidenceKeys=['cost_components','cost_components_scope','cost_components_unavailable_reason','target_playability'];
const evidenceBytes=c=>Buffer.byteLength(JSON.stringify(Object.fromEntries(evidenceKeys.filter(k=>Object.hasOwn(c,k)).map(k=>[k,c[k]]))),'utf8');

test('complete combat summary carries small exact payment and target facts without forcing a detail read',()=>{
 const s=state([compactCard()]),before=JSON.stringify(s),c=s.observation.game_state.combat_state.hand[0],view=decision(s).combat.hand[0];assert.ok(evidenceBytes(c)<=220);
 for(const key of ['cost_components','cost_components_scope','target_playability'])assert.deepEqual(view[key],c[key]);
 assert.equal(view.cost,1);assert.equal(view.details_required,undefined);assert.equal(JSON.stringify(s),before);
});
test('compact combat evidence preserves unknown, false, zero and unavailable reasons',()=>{
 for(const amount of [null,0]){
  const c=compactCard();delete c.cost_components_scope;c.cost_components_complete=false;c.cost_components[0].displayed_amount=amount;c.target_playability=[{available:false,monster_index:0,reason:'immune'}];c.cost_components_unavailable_reason='unknown_provider';assert.ok(evidenceBytes(c)<=220);
  const v=decision(state([c])).combat.hand[0];assert.equal(v.details_required,true);assert.equal(v.cost_components_complete,false);assert.deepEqual(v.target_playability,c.target_playability);assert.deepEqual(v.cost_components,c.cost_components);assert.equal(v.cost_components_unavailable_reason,'unknown_provider');
 }
});
test('over-budget native evidence stays exact on demand and state changes invalidate its view',()=>{
 const c=card(),s=state([c]),view=conditionalDecision(s);assert.ok(evidenceBytes(c)>220);
 for(const key of evidenceKeys){assert.equal(view.combat.hand[0][key],undefined);assert.deepEqual(readContext(s,[view.combat.hand[0].ref]).fragments[0].data[key],c[key]);}
 c.target_playability[0].available=false;s.state_id++;
 assert.equal(conditionalDecision(s,view.view_id).unchanged,undefined);assert.equal(readContext(s,['hand/0']).fragments[0].data.target_playability[0].available,false);
});
test('oversized or nested evidence stays fully reachable and is never partially inlined',()=>{
 const c=card();c.target_playability[0].reason='No action. '.repeat(90);const s=state([c]),v=decision(s).combat.hand[0];assert.equal(v.target_playability,undefined);assert.equal(v.cost_components,undefined);assert.deepEqual(readContext(s,[v.ref]).fragments[0].data.target_playability,c.target_playability);
 c.target_playability=[{available:false,detail:{information_complete:false}}];assert.equal(decision(s).combat.hand[0].target_playability,undefined);
});
test('evidence respects hand-wide budget and projection visibility',()=>{
 const distinctCards=n=>Array.from({length:n},(_,i)=>({...compactCard(),cost:i}));
 const s=state(distinctCards(2)),v=decision(s),extra=v.combat.hand.reduce((n,c)=>n+(c.cost_components?evidenceBytes(c):0),0);
 assert.equal(v.combat.hand.length,2);assert.ok(extra>0&&extra<=480);
 const crowded=state(distinctCards(10));assert.ok(decision(crowded).combat.hand.every(c=>c.cost_components===undefined),'Do not pay for early-card evidence when the complete hand does not fit');assert.deepEqual(readContext(crowded,['hand/9']).fragments[0].data.target_playability,crowded.observation.game_state.combat_state.hand[9].target_playability);
 const copies=decision(state(Array.from({length:10},compactCard))).combat.hand;assert.equal(copies.length,1);assert.equal(copies[0].copies,10);assert.deepEqual(copies[0].cost_components,compactCard().cost_components);
 s.observation.game_state.combat_state.hand_complete=false;assert.deepEqual(decision(s).combat.hand,[]);Object.defineProperty(s.observation.game_state,'combat_state',{get(){throw Error('unrelated');}});assert.doesNotThrow(()=>conditionalDecision(s,undefined,'map_plan'));
});
test('changed inlined native evidence invalidates the view without a state identity change',()=>{
 const c=compactCard(),s=state([c]),before=conditionalDecision(s).view_id;c.target_playability[0].available=false;assert.notEqual(conditionalDecision(s).view_id,before);
});

test('every character boss shows current ordered cards and Hermit roulette replacement in the default decision',()=>{
 const s=state(),ui={visible:true,information_complete:false,information_issues:['orbs.hidden_values'],hand:[{id:'EnemyStrike',name:'Strike',cost:1,description:'Deal 6 damage.'}],energy:{current:2,capacity:3},stance:{name:'Wrath',description:'Double attack damage.'},relics:[{name:'Burning Blood',counter:0}],orbs:[{name:'Dark',values_visible:false}],mechanic_panel:{name:'Boss mechanic',description:'Public explanation.'}};
 const bossCard=(name,slot)=>({visible:true,slot,name,type:'ATTACK',description:'Deal 6 damage.',description_complete:true,displayed_cost_text:'1',displayed_cost_complete:true});
 ui.hand=['Strike','Defend','Bash'].map(bossCard);
 for(const name of ['Ironclad','Silent','Defect','Watcher','Hermit','Merchant']){
  s.observation.game_state.combat_state.monsters=[{name,current_hp:200,public_combat:ui}];
  const hand=decision(s).combat.monsters[0].public_combat.hand;assert.deepEqual(hand.map(c=>c.name),['Strike','Defend','Bash'],name);
  assert.deepEqual(hand.map(c=>c.ref),ui.hand.map((_,i)=>'monsters/0/public_combat/hand/'+i));
  assert.ok(hand.every(c=>c.description==='Deal 6 damage.'&&c.displayed_cost_text==='1'));
 }
 ui.roulette_preview={visible:true,card:bossCard('Next public roulette card',0)};
 const original=JSON.stringify(s),before=decision(s),v=before.combat.monsters[0].public_combat;assert.equal(v.ref,'monsters/0/public_combat');assert.equal(v.hand_count,3);assert.deepEqual(v.energy,ui.energy);assert.equal(v.stance,'Wrath');assert.equal(v.information_complete,false);
 assert.equal(v.roulette_preview.card.name,'Next public roulette card');assert.equal(v.roulette_preview.card.ref,v.ref+'/roulette_preview/card');
 const detail=readContext(s,[v.ref]).fragments[0];assert.ok(detail.toc.some(row=>row.ref===v.ref+'/hand'));
 assert.equal(readContext(s,[v.ref+'/hand/0']).fragments[0].data.description,'Deal 6 damage.');assert.equal(readContext(s,[v.ref+'/relics/0']).fragments[0].data.counter,0);
 assert.equal(readContext(s,[v.roulette_preview.card.ref]).fragments[0].data.name,'Next public roulette card');assert.equal(JSON.stringify(s),original);
 const oldView=conditionalDecision(s).view_id;ui.hand=[...ui.hand.slice(1),bossCard('Roulette replacement',2)];ui.roulette_preview.card=bossCard('Changed preview',0);
 const after=decision(s);assert.notDeepEqual(after,before);assert.equal(after.combat.monsters[0].public_combat.hand_count,3);assert.deepEqual(after.combat.monsters[0].public_combat.hand.map(c=>c.name),['Defend','Bash','Roulette replacement']);
 assert.notEqual(conditionalDecision(s).view_id,oldView);assert.equal(readContext(s,[v.ref+'/hand/2']).fragments[0].data.name,'Roulette replacement');assert.equal(readContext(s,[v.roulette_preview.card.ref]).fragments[0].data.name,'Changed preview');
 ui.hand[1]={visible:false,slot:1,name:'Hidden identity',description:'Hidden effect'};const faceHidden=decision(s).combat.monsters[0].public_combat.hand[1];assert.equal(faceHidden.visible,false);assert.equal(faceHidden.name,undefined);assert.equal(faceHidden.description,undefined);
 ui.hand_visible=false;ui.roulette_preview.visible=false;const concealed=decision(s).combat.monsters[0].public_combat;assert.equal(concealed.hand_visible,false);assert.equal(concealed.hand_count,undefined);assert.equal(concealed.hand,undefined);assert.equal(concealed.roulette_preview.card,undefined);
 ui.visible=false;const hidden=decision(s).combat.monsters[0].public_combat;assert.equal(hidden.visible,false);assert.equal(hidden.energy,undefined);assert.equal(hidden.stance,undefined);assert.equal(hidden.hand,undefined);assert.equal(hidden.roulette_preview,undefined);
});
test('boss card explanations remain bounded, explicit when incomplete, and reachable under crowded combat',()=>{
 const s=state(Array.from({length:10},(_,i)=>({...card(),name:'Player '+i,description:'Player card explanation. '.repeat(20)})));
 const bossCard=(i)=>({visible:true,slot:i,name:'공개 카드 '+i,displayed_cost_text:i?'X':'0',displayed_cost_complete:i!==1,description:'플레이어가 확인할 수 있는 현재 카드 효과입니다. '.repeat(30),description_complete:i!==2,description_truncated:i===2});
 s.observation.game_state.combat_state.monsters=Array.from({length:3},(_,i)=>({name:'Boss '+i,current_hp:100,public_combat:{visible:true,hand_visible:true,hand:[0,1,2,3].map(bossCard),hand_truncated:true,information_complete:false,roulette_preview:{visible:true,card:bossCard(4)}}}));
 const original=JSON.stringify(s),view=decision(s);assert.ok(Buffer.byteLength(JSON.stringify(view))<5000);
 for(const monster of view.combat.monsters){const boss=monster.public_combat;assert.equal(boss.hand.length,3);assert.equal(boss.hand_count,4);assert.equal(boss.hand_more,boss.ref+'/hand');assert.equal(boss.hand_truncated,true);
  for(const c of [...boss.hand,boss.roulette_preview.card]){assert.equal(c.details_required,true);const actual=readContext(s,[c.ref+'/description']).fragments[0].text;assert.ok(actual.startsWith(c.description??''));if(c.description!==undefined)assert.equal(c.description_truncated,true);}
  assert.equal(boss.hand[1].displayed_cost_complete,false);assert.equal(boss.hand[2].description_complete,false);
 }
 assert.equal(JSON.stringify(s),original);
 const denseCard={visible:true,name:'긴'.repeat(80),type:'ATTACK',displayed_cost_text:'1'.repeat(32),displayed_cost_complete:true,description:'Public effect.',description_complete:true,exhausts:true,ethereal:true};
 const denseState=()=>state([]);const dense=denseState();dense.observation.game_state.combat_state.monsters=[{name:'Hermit',public_combat:{visible:true,roulette_preview:{visible:true,card:denseCard}}}];
 const compact=decision(dense).combat.monsters[0].public_combat.roulette_preview.card;
 const overflows=[3,4].map(count=>{const crowded=denseState();crowded.observation.game_state.combat_state.monsters=Array.from({length:count},(_,i)=>({name:'Boss '+i,public_combat:{visible:true,hand:Array.from({length:3},()=>({...denseCard,displayed_cost_text:'X',description:'Effect. '.repeat(80)})),roulette_preview:{visible:true,card:{...denseCard,displayed_cost_text:'X',description:'Effect. '.repeat(80)}}}}));return {state:crowded,view:decision(crowded)};});
 assert.deepEqual({omitted_description_requires_details:compact.description!==undefined||compact.details_required===true,crowded_budgets:overflows.map(({view})=>Buffer.byteLength(JSON.stringify(view))<=4400)},
  {omitted_description_requires_details:true,crowded_budgets:[true,true]});
 for(const {state:crowded,view} of overflows)for(const monster of view.combat.monsters)for(const c of [...monster.public_combat.hand,monster.public_combat.roulette_preview.card]){
  assert.equal(c.details_required,true);assert.equal(readContext(crowded,[c.ref+'/name']).fragments[0].text,denseCard.name);
 }
});
test('huge payment or target tables never traverse rows during hand deduplication',()=>{
 for(const key of ['cost_components','target_playability']){
  const rows=new Array(1000000);Object.defineProperty(rows,0,{get(){throw Error('unbounded evidence read');}});rows[999999]={available:false};
  const s=state([ {...compactCard(),[key]:rows}, {...compactCard(),[key]:rows} ]);
  assert.doesNotThrow(()=>decision(s));const hand=decision(s).combat.hand;assert.equal(hand.length,2);assert.ok(hand.every(c=>c[key]===undefined&&c.copies===undefined));
  const page=readContext(s,[hand[1].ref+'/'+key],999999,1).fragments[0];assert.equal(page.total,1000000);assert.equal(page.next_offset,undefined);assert.deepEqual({...readContext(s,[page.toc[0].ref]).fragments[0].data},{available:false});
 }
});

test('current character card facts preserve false, sockets and observed origin with exact details',()=>{
 const cards=[{...card(),dead_on_position_active:false},{...card(),dead_on_position_active:true},{...card(),socket_capacity:2,sockets:['RED','BLUE'],gem_type:'RED'},{...card(),unknown_origin:{id:'Strike',name:'Strike'}}],s=state(cards),before=JSON.stringify(s),hand=decision(s).combat.hand;
 assert.equal(hand.length,4);assert.equal(hand[0].dead_on_position_active,false);assert.equal(hand[1].dead_on_position_active,true);assert.equal(hand[2].socket_capacity,2);assert.deepEqual(hand[2].sockets,['RED','BLUE']);assert.equal(hand[2].gem_type,'RED');assert.deepEqual(hand[3].unknown_origin,{id:'Strike',name:'Strike'});
 cards[2].sockets=Array(7).fill('RED');const overflow=decision(s).combat.hand[2];assert.equal(overflow.sockets,undefined);assert.equal(overflow.details_required,true);assert.deepEqual(readContext(s,[overflow.ref+'/sockets']).fragments[0].items,cards[2].sockets);
 cards[2].sockets=['RED','BLUE'];assert.equal(JSON.stringify(s),before);
});

test('current public orbs and panel children are discoverable without invented absent or hidden facts',()=>{
 const s=state([]),c=s.observation.game_state.combat_state;c.player={orbs:[{name:'Slime',passive_amount:4,evoke_amount:0,upgraded:false,displayed_debuff:0},{name:'Stasis',passive_amount:2,stasis_card:{name:'Defend',cost:1,description:'Gain 5 Block.'}},{details_complete:false,unavailable_reason:'orb_not_rendered',name:'Hidden',passive_amount:99}],mechanics:{temporary_hp:0,max_orb_slots:3,ghostflames:[{name:'Searing'}],champ_stance:{id:'Defensive'},encode_sequence:[{name:'Strike'}],function_preview:{name:'Function'},compile_panel:{description:'Current compile'},gremlins:[{name:'Mad'}],spells:[{name:'Spell'}],information_complete:false}};
 const before=JSON.stringify(s),v=decision(s),orbs=v.player.orbs;assert.deepEqual(orbs.map(x=>x.ref),[0,1,2].map(i=>'player/orbs/'+i));assert.equal(orbs[0].upgraded,false);assert.equal(orbs[0].displayed_debuff,0);assert.equal(orbs[1].stasis_card.name,'Defend');assert.equal(orbs[1].stasis_card.ref,'player/orbs/1/stasis_card');assert.equal(orbs[2].name,undefined);assert.equal(orbs[2].passive_amount,undefined);assert.equal(orbs[2].details_complete,false);
 for(const key of ['ghostflames','champ_stance','encode_sequence','function_preview','compile_panel','gremlins','spells']){assert.ok(v.player.mechanics_refs.includes('mechanics/'+key),key);assert.ok(readContext(s,['mechanics/'+key]).fragments.length);}
 assert.equal(v.player.mechanics_incomplete,true);assert.equal(JSON.stringify(s),before);c.player.orbs=[];assert.deepEqual(decision(s).player.orbs,[]);delete c.player.orbs;assert.equal(decision(s).player.orbs,undefined);delete c.player.mechanics;assert.equal(decision(s).player?.mechanics_refs,undefined);const hidden={visible:false};Object.defineProperty(hidden,'name',{get(){throw Error('hidden orb read');}});c.player.orbs=[hidden];assert.doesNotThrow(()=>decision(s));assert.equal(decision(s).player.orbs[0].name,undefined);
});

test('dense character summaries preserve Dead On and truthful refs within unchanged budgets',()=>{
 const s=state(Array.from({length:10},(_,i)=>({...card(),name:'Card '+i,dead_on_position_active:i%2===0,socket_capacity:3,sockets:['RED','BLUE','GREEN'],description:'Effect. '.repeat(100)}))),c=s.observation.game_state.combat_state;
 const powers=Array.from({length:6},(_,i)=>({name:'Power '+i,amount:i,description:'Public effect. '.repeat(100),description_complete:false}));c.player={powers,orbs:Array.from({length:12},(_,i)=>({name:'Slime '+i,passive_amount:i,description:'Orb effect. '.repeat(80)})),mechanics:{ghostflames:[{name:'Searing'}],encode_sequence:[{name:'Encode'}]}};c.monsters=Array.from({length:4},(_,i)=>({name:'Boss '+i,powers,public_combat:{visible:true,hand:Array.from({length:3},(_,slot)=>({visible:true,slot,name:'Boss card',description:'Boss effect. '.repeat(80)}))}}));
 s.observation.game_state.potions=Array.from({length:6},(_,i)=>({id:'Potion'+i,name:'Potion '+i,description:'Potion effect. '.repeat(80)}));const before=JSON.stringify(s),v=decision(s);assert.ok(Buffer.byteLength(JSON.stringify(v))<=4400,'bytes='+Buffer.byteLength(JSON.stringify(v))+' sections='+JSON.stringify(Object.fromEntries(Object.entries(v).map(([k,x])=>[k,Buffer.byteLength(JSON.stringify(x))]))));assert.deepEqual(v.combat.hand.map(x=>x.dead_on_position_active),Array.from({length:10},(_,i)=>i%2===0));assert.equal(v.player.orbs_more,'player/orbs');assert.ok(v.player.mechanics_refs.includes('mechanics/ghostflames'));assert.ok(v.player.mechanics_refs.includes('mechanics/encode_sequence'));assert.ok(v.combat.hand.every(x=>x.cost===1));let shortened=0;for(const owner of [v.player,...v.combat.monsters])if(owner.power_count!==undefined){shortened++;assert.equal(owner.power_count,6);assert.ok(owner.powers.length<6);assert.ok(owner.powers.every((x,i)=>x.ref===owner.powers_more+'/'+i&&x.description_complete===false));assert.equal(readContext(s,[owner.powers_more+'/5']).fragments[0].data.name,'Power 5');}assert.ok(shortened>0);assert.equal(JSON.stringify(s),before);
});




test('socket facts never silently omit a currently captured card effect',()=>{
 const s=state(Array.from({length:10},(_,i)=>({name:'Card '+i,cost:1,type:'ATTACK',socket_capacity:3,sockets:['RED','BLUE','GREEN'],gem_type:'RED',description:'Deal 6 damage.',description_complete:true}))),before=JSON.stringify(s),v=decision(s);
 for(const c of v.combat.hand){assert.ok(c.description==='Deal 6 damage.'||c.details_required===true,'Omitted captured effect needs its current card detail ref: '+c.ref);assert.equal(readContext(s,[c.ref+'/description']).fragments[0].text,'Deal 6 damage.');}
 assert.ok(Buffer.byteLength(JSON.stringify(v))<=4400);assert.equal(JSON.stringify(s),before);
});

test('dense long-name hands without powers retain cost and Dead On within 4400 bytes',()=>{
 const cards=Array.from({length:10},(_,i)=>({name:'긴'.repeat(75)+i,cost:1,type:'ATTACK',dead_on_position_active:i%2===0,description:'Effect.',description_complete:true})),s=state(cards),c=s.observation.game_state.combat_state;c.player={};c.monsters=Array.from({length:4},()=>({name:'Boss',public_combat:{visible:true,hand:[0,1,2].map(i=>({name:'Boss card'+i,visible:true,description:'Boss effect.',displayed_cost_text:'1',description_complete:true,displayed_cost_complete:true}))}}));const before=JSON.stringify(s),v=decision(s);
 assert.ok(Buffer.byteLength(JSON.stringify(v))<=4400,'No-power decision bytes='+Buffer.byteLength(JSON.stringify(v)));assert.equal(v.combat.hand.length,10);
 for(const [i,row] of v.combat.hand.entries()){assert.equal(row.ref,'hand/'+i);assert.equal(row.cost,1);assert.equal(row.dead_on_position_active,i%2===0);if(row.name!==cards[i].name){assert.equal(row.name_truncated,true);assert.equal(row.details_required,true);assert.equal(readContext(s,[row.ref+'/name']).fragments[0].text,cards[i].name);}}
 assert.equal(JSON.stringify(s),before);
});
