import {createHash} from 'node:crypto';
import {obj,list,type Obj} from './view.js';
import {selectGuidance,guidanceFragment} from './guidance.js';
import {groupEquivalentCards} from './hand-dedupe.js';
import {actionAlias,publicParameterSchema} from './action-projection.js';

const pick=(o:Obj,keys:string[]):Obj=>Object.fromEntries(keys.filter(k=>k in o).map(k=>[k,o[k]]));
const present=(v:unknown)=>v!=null && (Array.isArray(v)?v.length>0:typeof v==='object'?Object.keys(obj(v)).length>0:true);
const child=(ref:string,key:string)=>ref+'/'+encodeURIComponent(key);
const size=(v:unknown)=>Array.isArray(v)||typeof v==='string'?v.length:Object.keys(obj(v)).length;
const bytes=(v:unknown)=>Buffer.byteLength(JSON.stringify(v),'utf8');
const provenance=new Set(['description_source','description_rendering','tooltips_source','tooltips_rendering','render_frame']);
const nativeKeys=new Set(['screen_name','is_screen_up','screen_type','room_phase','action_phase','current_action','room_type','current_hp','max_hp','floor','act','act_boss','gold','seed','class','ascension_level','relics','potions','keys','deck','map','map_plan','combat_state','mechanics','narrative','screen_state','choice_list','compact_state','context_truncated']);
const controlKeys=['tutorial','selection_controls','reward_controls','card_reward_header','potion_controls','rest_controls','shop_controls','reward_navigation','card_selection_controls','wheel_controls','match_controls'];
const knownObservationKeys=new Set(['in_game','game_state','combat_decision','menu',...controlKeys]);
const mapPlanKeys=['route_author','route_count','current_node','next_planned_node','status','editing'];
const mapViewKeys=['ready','screen','connection'];
const mechanicsZeroNoise=new Set(['temporary_hp','max_orb_slots']);
const rules:Record<string,string>={
 act:'Use only offered actions from the current state. Read needed referenced details. One action per call; never replay unknown/applied_waiting. Refresh state after stale rejection.',
 cost:'The scalar cost is the current card.costForTurn energy cost and must be considered for ordinary cards, including Snecko-randomized costs. Use displayed_cost_text and cost_components to qualify X/alternate-resource/special costs. Read target_playability and unplayable_reason before playing.',
 upgrade:'Before acquisition or upgrade, read offered card upgrade_preview and relevant keyword details. Only next standard upgrade, not random/event/relic outcomes. Read selected_after before separate branch/tree confirmation.',
 event:'Read and present current event body, situation and choices before acknowledgement. Send meaningful commentary; the server pins the current reading identity. Discuss result pages too. Do not silently acknowledge incomplete text.',
 map:'Map planning draws route, never moves character. Send only observed node IDs; the server pins map identity and revision. Native edges only, not predicted travel powers.',
 reward:'Read reward_navigation before leaving: unclaimed rewards may be abandoned. Never auto-discard potion for space; discarding: separate destructive choice.',
 potion:'Potion inventory is a current snapshot. Read the chosen potion details_required ref and tooltips_more for additional rules before using it. can_use=false is not usage history. Input acceptance alone does not prove consumption: check fresh slot IDs. full describes space only, not reward eligibility (e.g. Sozu).'
};
interface Scope {screen:string;combat:boolean;roots:Obj;unsupported:boolean;}
const handVisible=(c:Obj,o:Obj)=> (c.hand_complete===true || obj(o.combat_decision).hand_complete===true)
 && c.hand_complete!==false && obj(o.combat_decision).hand_complete!==false && obj(o.combat_decision).mode!=='selection';
function mechanicsNeedsRef(value:unknown):boolean {
 const v=obj(value);
 for(const [key,item] of Object.entries(v)){
  if(['scope','collection','combat_collection'].includes(key))continue;
  if(key==='character_supported'){if(item===false)return true;continue;}
  if(key==='information_issues'){if(Array.isArray(item)&&item.length)return true;continue;}
  if(key==='information_issue_count'){if(typeof item==='number'&&item>0)return true;continue;}
  if(key.endsWith('_complete')){if(item===false)return true;continue;}
  if(key.endsWith('_unavailable_reason')){if(item!=null)return true;continue;}
  if(mechanicsZeroNoise.has(key)&&item===0)continue;
  if(item!==null&&typeof item==='object'&&present(item))return true;
 }
 return false;
}
function scope(state:Obj):Scope {
 const o=obj(state.observation),g=obj(o.game_state),c=obj(g.combat_state);
 const screen=String(obj(o.menu).screen ?? g.screen_type ?? 'unavailable');
 const actions=state.ready===true?list(state.actions):[];
 const hasAction=(prefix:string)=>actions.some(a=>String(obj(a).id).startsWith(prefix));
 const combat=Object.keys(c).length>0 && !['EVENT','MAP','SHOP_SCREEN','SHOP_ROOM','REST','COMBAT_REWARD','BOSS_REWARD','CHEST','COMPLETE','GAME_OVER','VICTORY'].includes(screen)
  && (screen!=='CARD_REWARD' || obj(o.combat_decision).mode==='selection');
 const roots:Obj={};const add=(key:string,value:unknown)=>{if(present(value))roots[key]=value;};
 add('actions',actions);
 if(Object.keys(g).length){
  const player={...pick(g,['class','act','floor','ascension_level','current_hp','max_hp','gold','keys','relics','potions']),...(combat?obj(c.player):{})};
  if(Array.isArray(g.potions))player.potions=g.potions.map(value=>{const row=obj(value);return {...row,...(typeof row.id==='string'&&row.id?{empty:row.id==='Potion Slot'}:{})};});
  add('player',player);
  add('deck',g.deck);add('screen',g.screen_state);
  const mechanics=combat?obj(c.player).mechanics ?? g.mechanics:g.mechanics;
  if(mechanicsNeedsRef(mechanics))add('mechanics',mechanics);
  add('collection',obj(mechanics).collection);
  if(combat){
   const combatMeta:Obj=pick(c,['turn','cards_discarded_this_turn','times_damaged']);if(!handVisible(c,o))combatMeta.hand_complete=false;add('combat',combatMeta);
   add('card_in_play',c.card_in_play);add('combat_collection',obj(mechanics).combat_collection);if(handVisible(c,o))add('hand',c.hand);add('monsters',c.monsters);
   add('piles',pick(c,['draw_pile','discard_pile','exhaust_pile','draw_pile_order_visible','draw_pile_order']));
  }
  if(screen==='MAP'){add('map',g.map);add('map_plan',g.map_plan);}
  const narrative=obj(g.narrative),current=list(narrative.entries).filter(e=>obj(e).currently_displayed!==false);add('dialogue',current);if(current.length||screen==='EVENT')add('history',narrative);
 }
 add('menu',o.menu);
 const gated:Record<string,boolean>={tutorial:true,match_controls:screen==='EVENT',wheel_controls:screen==='EVENT'||hasAction('run.wheel.spin'),potion_controls:Object.keys(g).length>0,rest_controls:screen==='REST'||hasAction('run.rest.'),shop_controls:screen==='SHOP_SCREEN'||hasAction('run.shop.'),
  reward_controls:['COMBAT_REWARD','CARD_REWARD','BOSS_REWARD'].includes(screen),reward_navigation:['COMBAT_REWARD','CARD_REWARD','BOSS_REWARD'].includes(screen),card_reward_header:screen==='CARD_REWARD',selection_controls:['GRID','HAND_SELECT','CARD_REWARD'].includes(screen),card_selection_controls:['GRID','HAND_SELECT','CARD_REWARD'].includes(screen)};
 for(const key of controlKeys)if(gated[key])add(key,o[key]);
 if(roots.match_controls){
  const controls=obj(roots.match_controls);
  roots.match_controls={...controls,cards:list(controls.cards).map(value=>{
   const row=obj(value);return pick(row,['position','row','column','face_up','available','action_id',...(row.face_up===true?['card']:[])]);
  })};
 }
 const relevantRules:Obj={act:rules.act};if(combat)relevantRules.cost=rules.cost;if(['GRID','CARD_REWARD','SHOP_SCREEN','REST','EVENT'].includes(screen))relevantRules.upgrade=rules.upgrade;if(screen==='EVENT')relevantRules.event=rules.event;if(screen==='MAP')relevantRules.map=rules.map;if(roots.reward_navigation||roots.potion_controls)relevantRules.reward=rules.reward;if(Array.isArray(g.potions))relevantRules.potion=rules.potion;if(Object.keys(roots).length)add('rules',relevantRules);
 return {screen,combat,roots,unsupported:Object.keys(g).some(k=>!nativeKeys.has(k))||Object.keys(o).some(k=>!knownObservationKeys.has(k))};
}
function connectionSummary(state:Obj):Obj|undefined {
 const c=obj(state.connection),status=typeof c.status==='string'?c.status:undefined,pending=!!c.pending_request_id;if((status===undefined||status==='connected')&&!pending)return;
 const out:Obj={};if(status&&status!=='connected')out.status=status;if(pending)out.pending=true;return out;
}
const scalar=(v:unknown,max=160)=>v===null||typeof v==='boolean'||typeof v==='number'&&Number.isFinite(v)||typeof v==='string'&&v.length<=max;
function inlineSummary(value:unknown,keys?:string[]):Obj {
 const v=obj(value),source=keys?pick(v,keys):v,out:Obj={};for(const [key,item] of Object.entries(source))if(!provenance.has(key)&&scalar(item,200))out[key]=item;return out;
}
function mechanicsDecisionSummary(value:unknown):Obj {
 const v=obj(value),out:Obj={};let incomplete=false;
 const panels=['ghostflames','champ_stance','encode_sequence','function_preview','compile_panel','gremlins','spells'].filter(key=>present(v[key]));
 if(panels.length)out.mechanics_refs=panels.map(key=>child('mechanics',key));
 for(const [key,item] of Object.entries(v)){
  if(['scope','collection','combat_collection'].includes(key))continue;
  if(key==='character_supported'){if(item===false)incomplete=true;continue;}
  if(key==='information_issues'){if(Array.isArray(item)&&item.length)incomplete=true;continue;}
  if(key==='information_issue_count'){if(typeof item==='number'&&item>0)incomplete=true;continue;}
  if(key.endsWith('_complete')){if(item===false)incomplete=true;continue;}
  if(key.endsWith('_unavailable_reason')){if(item!=null)incomplete=true;continue;}
  if(mechanicsZeroNoise.has(key)&&item===0)continue;
  if(scalar(item,120))out[key]=item;
 }
 if(incomplete)out.mechanics_incomplete=true;
 return out;
}
function powerSummary(value:unknown,ref:string):Obj {
 const v=obj(value),out:Obj={ref};
 if(typeof v.name==='string'&&v.name){out.name=v.name.slice(0,80);if(v.name.length>80){out.name_truncated=true;out.details_required=true;}}
 if(typeof v.type==='string'&&v.type)out.type=v.type;
 if(typeof v.amount==='number'&&Number.isFinite(v.amount))out.amount=v.amount;
 if(typeof v.damage==='number'&&Number.isFinite(v.damage))out.damage=v.damage;
 if(v.just_applied===true)out.just_applied=true;
 if(typeof v.description==='string'&&v.description){
  const text=v.description.slice(0,180);out.description=text;
  if(text.length<v.description.length){out.description_truncated=true;out.details_required=true;}
 }else out.details_required=true;
 if(v.description_truncated===true){out.description_truncated=true;out.details_required=true;}
 if(v.description_complete===false){out.description_complete=false;out.details_required=true;}
 if(v.misc!==undefined||v.card!==undefined)out.details_required=true;
 return out;
}
function powersSummary(value:unknown,limit:number,ref:string):Obj[] {
 return list(value).slice(0,limit).map((power,index)=>powerSummary(power,ref+'/'+index));
}
function publicAction(value:unknown,index:number):Obj {
 const action=obj(value),out:Obj={id:actionAlias(index)};
 if(typeof action.label==='string'){out.label=action.label.slice(0,160);if(action.label.length>160)out.label_truncated=true;}
 const parameters=publicParameterSchema(action.parameters);if(Object.keys(parameters).length)out.parameters_ref=child('actions/'+index,'parameters');
 return out;
}
const aliases=(actions:unknown[])=>new Map(actions.map((value,index)=>[String(obj(value).id),actionAlias(index)]));
function refSummary(value:unknown,ref:string,keys?:string[]):Obj {return {ref,...inlineSummary(value,keys)};}
function controlSummary(value:unknown,actionAliases:Map<string,string>):Obj {
 const v=obj(value),out:Obj={};
 for(const [key,item] of Object.entries(v)){
  if(provenance.has(key)||item==null)continue;
  if((key==='id'||key==='action_id')&&typeof item==='string'){const alias=actionAliases.get(item);if(alias)out.action=alias;continue;}
  if(key==='mutually_exclusive_with'&&typeof item==='string'){const alias=actionAliases.get(item);if(alias)out[key]=alias;continue;}
  if(['available','supported','claimable','can_use','can_discard'].includes(key)&&item===true)continue;
  if(['pending','ignored','disabled','requires_target'].includes(key)&&item===false)continue;
  if(key.endsWith('_complete')&&item===true)continue;
  if(scalar(item,160))out[key]=item;
 }
 return out;
}
function bossCardSummary(ref:string,value:unknown):Obj {
 const v=obj(value),out:Obj={ref,...pick(v,['slot','visible'])};
 if(v.visible===false){if(scalar(v.visibility_reason))out.visibility_reason=v.visibility_reason;return out;}
 if(v.details_complete===false){out.details_complete=false;out.details_required=true;if(scalar(v.unavailable_reason))out.unavailable_reason=v.unavailable_reason;return out;}
 const name=typeof v.name==='string'?v.name.slice(0,80):undefined;
 Object.assign(out,flatCardSummary(ref,{...v,...(name!==undefined?{name}:{})},480,false));
 if(typeof v.name==='string'&&v.name.length>80){out.name_truncated=true;out.details_required=true;}
 if(!name||typeof out.description!=='string'||!out.description)out.details_required=true;
 if(v.description_truncated===true){out.description_truncated=true;out.details_required=true;}
 if(typeof v.displayed_cost_text!=='string'||!v.displayed_cost_text||v.displayed_cost_text.length>32)out.details_required=true;
 return out;
}
function monsterSummary(value:unknown,index:number):Obj {
 const v=obj(value),out:Obj={ref:'monsters/'+index};
 for(const key of ['name','current_hp','max_hp','intent','move_adjusted_damage'])if(Object.hasOwn(v,key)&&scalar(v[key],120))out[key]=v[key];
 if(typeof v.block==='number'&&v.block>0)out.block=v.block;
 if(typeof v.move_hits==='number'&&v.move_hits>1)out.move_hits=v.move_hits;
 const powers=list(v.powers);if(powers.length){out.powers=powersSummary(powers,5,'monsters/'+index+'/powers');if(powers.length>5)out.powers_more='monsters/'+index+'/powers';}
 const boss=obj(v.public_combat);
 if(Object.keys(boss).length){
  const ref='monsters/'+index+'/public_combat',summary:Obj={ref,...pick(boss,['visible','hand_visible','hand_truncated','hand_unavailable_reason','roulette_preview_unavailable_reason','information_complete'])};
  if(boss.visible!==false){
   if(boss.hand_visible!==false&&Array.isArray(boss.hand)){
    summary.hand_count=boss.hand.length;summary.hand=boss.hand.slice(0,3).map((value,i)=>bossCardSummary(ref+'/hand/'+i,value));
    if(boss.hand.length>3||boss.hand_truncated===true)summary.hand_more=ref+'/hand';
   }
   const preview=obj(boss.roulette_preview);
   if(Object.keys(preview).length){
    const row:Obj={ref:ref+'/roulette_preview',...pick(preview,['visible','visibility_reason','information_complete'])};
    if(preview.visible===true&&preview.card)row.card=bossCardSummary(ref+'/roulette_preview/card',preview.card);
    summary.roulette_preview=row;
   }
   const energy=pick(obj(boss.energy),['current','capacity']);if(Object.keys(energy).length)summary.energy=energy;
   const stance=obj(boss.stance).name;if(typeof stance==='string'&&stance.length<=120)summary.stance=stance;
  }
  out.public_combat=summary;
 }
 for(const key of ['is_gone','is_dead','half_dead'])if(v[key]===true)out[key]=true;
 return out;
}
function eventOptionSummary(value:unknown,index:number):Obj {
 const v=obj(value),out:Obj={ref:'screen/event_reading/options/'+index};
 if(typeof v.text==='string')out.text=v.text.slice(0,240);if(v.disabled===true)out.disabled=true;return out;
}
function offerSummary(value:unknown,index:number,screen:string):Obj {
 const v=obj(value),out:Obj={ref:'screen/cards/'+index};
 for(const key of ['name','type','price'])if(Object.hasOwn(v,key)&&scalar(v[key],160))out[key]=v[key];
 if(screen==='SHOP_SCREEN'){
  if(v.available===false)out.available=false;if(v.affordable===false)out.affordable=false;
  if(typeof v.unavailable_reason==='string'&&v.unavailable_reason)out.unavailable_reason=v.unavailable_reason;
 }else{
  if(typeof v.description==='string')out.description=v.description.slice(0,240);
  if(typeof v.displayed_cost_text==='string')out.displayed_cost_text=v.displayed_cost_text;
  if(v.displayed_cost_complete===false)out.displayed_cost_complete=false;
 }
 return out;
}
function publicEventReading(value:unknown):Obj {
 const v=obj(value),out:Obj={};
 for(const key of ['phase','page_role'])if(typeof v[key]==='string')out[key]=v[key];
 if(typeof v.body_text==='string')out.body_text=v.body_text;
 if(v.text_complete===false)out.text_complete=false;
 if(typeof v.unavailable_reason==='string'&&v.unavailable_reason)out.unavailable_reason=v.unavailable_reason;
 const options=list(v.options).map((row,i)=>eventOptionSummary(row,i));if(options.length)out.options=options;
 return out;
}
function publicScreen(value:unknown):Obj {
 const source=obj(value),out=structuredClone(source) as Obj;delete out.event_id;delete out.body_text_source;
 if(present(source.event_reading)){delete out.body_text;out.event_reading=publicEventReading(source.event_reading);}
 return out;
}
function publicMapPlan(value:unknown):Obj {
 return pick(obj(value),['route_author','route','route_count','planned_start','current_node','next_planned_node','status','stroke_count','editing']);
}
function publicDialogueEntry(value:unknown,currentOnly=false):Obj {
 const v=obj(value),out:Obj={};
 for(const key of ['text','speaker_type','speaker_name','text_language'])if(v[key]!=null)out[key]=v[key];
 const context={...obj(v.context)};delete context.room_visit;delete context.channel;if(Object.keys(context).length)out.context=context;
 if(!currentOnly&&v.currently_displayed===true)out.currently_displayed=true;
 if(!currentOnly&&v.currently_displayed===false)out.currently_displayed=false;
 if(typeof v.visible_text==='string'&&v.visible_text&&v.visible_text!==v.text)out.visible_text=v.visible_text;
 if(v.text_truncated===true)out.text_truncated=true;
 return out;
}
function publicHistory(value:unknown):Obj {
 const v=obj(value),out:Obj={},entries=list(v.entries).map(row=>publicDialogueEntry(row,false));if(entries.length)out.entries=entries;
 if(typeof v.dropped_entries==='number'&&v.dropped_entries>0)out.dropped_entries=v.dropped_entries;
 const reasons=list(v.unavailable_reasons).filter(reason=>reason!=='custom_renderers_not_verified'&&reason!=='pixel_occlusion_not_verified');if(reasons.length)out.unavailable_reasons=reasons;
 return out;
}
function sanitizeControlTree(value:unknown,actionAliases:Map<string,string>,depth=0,cardPayload=false):unknown {
 if(depth>24)return value;if(Array.isArray(value))return value.map(item=>sanitizeControlTree(item,actionAliases,depth+1,cardPayload));
 if(value&&typeof value==='object'){
  const out:Obj={};for(const [key,item] of Object.entries(obj(value))){
   if((key==='id'&&!cardPayload||key==='action_id')&&typeof item==='string'){const alias=actionAliases.get(item);if(alias)out.action=alias;continue;}
   if(key==='mutually_exclusive_with'&&typeof item==='string'){const alias=actionAliases.get(item);if(alias)out[key]=alias;continue;}
   out[key]=sanitizeControlTree(item,actionAliases,depth+1,cardPayload||['card','cards','before','after','selected_after'].includes(key));
  }return out;
 }
 return value;
}
function scalarRecord(value:unknown,max=200):boolean {
 if(value===null||typeof value!=='object'||Array.isArray(value))return false;
 return Object.entries(obj(value)).every(([key,item])=>provenance.has(key)||scalar(item,max));
}
function compactControlArray(value:unknown):boolean {
 return Array.isArray(value)&&value.length<=12&&value.every(row=>scalarRecord(row,160));
}
/** The pinned native array contains every slot, including the exact PotionSlot ID. */
function potionCounts(slots:unknown[]):Obj {
 let held=0,empty=0;for(const value of slots){const id=obj(value).id;if(typeof id!=='string'||!id)return {capacity:slots.length,held_count:null,empty_slots:null,full:null,information_complete:false};if(id==='Potion Slot')empty++;else held++;}
 return {capacity:slots.length,held_count:held,empty_slots:empty,full:empty===0};
}
function potionSlotSummary(value:unknown,ref:string):Obj {
 const row=obj(value),out:Obj={ref,...pick(row,['slot','empty','can_use','can_discard','requires_target'])};
 for(const key of ['id','name'])if(typeof row[key]==='string'){out[key]=row[key].slice(0,80);if(row[key].length>80){out[key+'_truncated']=true;out.details_required=true;}}
 if(row.id==='Potion Slot')return out;
 if(typeof row.description==='string'&&row.description){out.description=row.description.slice(0,160);if(row.description.length>160){out.description_truncated=true;out.details_required=true;}}else out.details_required=true;
 if(row.description_complete===false){out.description_complete=false;out.details_required=true;}
 if(row.description_truncated===true){out.description_truncated=true;out.details_required=true;}
 if(Array.isArray(row.tooltips)){out.tooltip_count=row.tooltips.length;if(row.tooltips.length){out.tooltips_more=ref+'/tooltips';out.details_required=true;}}
 if(row.tooltips_complete!==true){out.tooltips_complete=false;out.details_required=true;}
 if(row.tooltips_truncated===true){out.tooltips_truncated=true;out.details_required=true;}
 return out;
}
function potionSummary(slots:unknown[]):Obj {
 const ref='player/potions',out:Obj={ref,...potionCounts(slots),slots:slots.slice(0,6).map((value,index)=>potionSlotSummary(value,ref+'/'+index))};
 if(slots.length>6)out.slots_more=ref;return out;
}
function entry(ref:string,value:unknown,title?:string):Obj {
 if(/^(?:player|monsters\/\d+)\/powers\/\d+$/.test(ref)){
  const {name,...power}=powerSummary(value,ref);if(name!==undefined)power.title=name;return power;
 }
 if(/^player\/potions\/\d+$/.test(ref)){
  const {name,...potion}=potionSlotSummary(value,ref);if(name!==undefined)potion.title=name;return potion;
 }
 const v=obj(value),out:Obj={ref},leaf=ref.split('/').at(-1)??ref,label=String(title??v.name??v.label??v.title??v.id??leaf).slice(0,64);
 if(label!==ref&&label!==leaf)out.title=label;if(Array.isArray(value)||typeof value==='string')out.count=size(value);
 if(ref==='player/potions'&&Array.isArray(value))Object.assign(out,{count_kind:'slots'},potionCounts(value));
 if(ref.startsWith('hand/')){if(typeof v.name==='string')out.title=v.name.slice(0,64);if(typeof v.cost==='number'||typeof v.cost==='string')out.cost=v.cost;}return out;
}
function resolve(roots:Obj,ref:string):unknown {
 let value:unknown=roots;for(const encoded of ref.split('/')){let key:string;try{key=decodeURIComponent(encoded);}catch{throw new Error('Reference not available in current state.');}
  if(!key||['__proto__','constructor','prototype'].includes(key)||value===null||typeof value!=='object'||!Object.hasOwn(value,key)||(Array.isArray(value)&&!/^(0|[1-9]\d*)$/.test(key)))throw new Error('Reference not available in current state.');value=(value as Obj)[key];}return value;
}
function publicResolve(roots:Obj,ref:string):unknown {
 const root=ref.split('/')[0];if(!Object.hasOwn(roots,root))throw new Error('Reference not available in current state.');
 const actions=list(roots.actions),actionMap=aliases(actions);
 if(ref==='actions')return actions.map(publicAction);
 const actionMatch=/^actions\/(0|[1-9]\d*)(?:\/(.*))?$/.exec(ref);
 if(actionMatch){
  const index=Number(actionMatch[1]);if(index>=actions.length)throw new Error('Reference not available in current state.');const action=obj(actions[index]),rest=actionMatch[2];
  if(!rest)return publicAction(action,index);
  if(rest==='parameters'||rest.startsWith('parameters/')){
   const parameters=publicParameterSchema(action.parameters);if(!Object.keys(parameters).length)throw new Error('Reference not available in current state.');
   return resolve({parameters},rest);
  }
  throw new Error('Reference not available in current state.');
 }
 if(root==='actions')throw new Error('Reference not available in current state.');
 if(ref==='screen'||ref.startsWith('screen/'))return resolve({screen:publicScreen(roots.screen)},ref);
 if(ref==='map_plan'||ref.startsWith('map_plan/'))return resolve({map_plan:publicMapPlan(roots.map_plan)},ref);
 if(ref==='history'||ref.startsWith('history/'))return resolve({history:publicHistory(roots.history)},ref);
 if(ref==='dialogue'||ref.startsWith('dialogue/'))return resolve({dialogue:list(roots.dialogue).map(row=>publicDialogueEntry(row,true))},ref);
 if(controlKeys.includes(root))return resolve({[root]:sanitizeControlTree(roots[root],actionMap)},ref);
 return resolve(roots,ref);
}
const cardRef=(ref:string)=>/^(?:(?:hand|deck|screen\/cards|collection\/cards|combat_collection\/cards|monsters\/\d+\/public_combat\/hand)\/\d+|monsters\/\d+\/public_combat\/roulette_preview\/card|player\/orbs\/\d+\/stasis_card|card_in_play)$/.test(ref);
function cardSummary(ref:string,value:unknown,budget:number,forceDetails=true):Obj {
 const source=obj(value),data:Obj={},out:Obj={ref,data};let needs=forceDetails;
 if(typeof source.dead_on_position_active==='boolean')data.dead_on_position_active=source.dead_on_position_active;
 for(const key of ['socket_capacity','gem_type','sockets','unknown_origin'])if(Object.hasOwn(source,key)){
  const item=source[key];let fact:unknown;
  if(key==='sockets'&&Array.isArray(item)&&item.length<=6&&item.every(v=>typeof v==='string'&&v.length<=32))fact=[...item];
  else if(key==='unknown_origin'&&item&&typeof item==='object'&&!Array.isArray(item)&&Object.keys(item).length>0&&Object.keys(item).length<=2&&Object.entries(item).every(([k,v])=>['id','name'].includes(k)&&typeof v==='string'&&v.length<=64))fact={...obj(item)};
  else if(key==='socket_capacity'&&typeof item==='number'&&Number.isFinite(item))fact=item;
  else if(key==='gem_type'&&typeof item==='string'&&item.length<=32)fact=item;
  if(fact!==undefined){data[key]=fact;if(bytes(out)>budget-40){delete data[key];needs=true;}}else needs=true;
 }
 for(const [key,item] of Object.entries(source))if((key.endsWith('_unavailable_reason')||key.endsWith('_complete')&&item===false)&&['sockets','unknown_origin','dead_on'].some(prefix=>key.startsWith(prefix))){needs=true;if(scalar(item,80))data[key]=item;}
 for(const key of ['name','cost','type','is_playable'])if(Object.hasOwn(source,key)&&scalar(source[key])){data[key]=source[key];if(bytes(out)>budget&&key!=='cost'&&key!=='name')delete data[key];}
 if(source.is_playable===false){if(scalar(source.unplayable_reason))data.unplayable_reason=source.unplayable_reason;else needs=true;}
 const rendered=source.displayed_cost_text;if(typeof rendered==='string'&&rendered.length<=32&&(typeof source.cost!=='number'||rendered!==String(source.cost)))data.displayed_cost_text=rendered;
 if(source.displayed_cost_complete===false){data.displayed_cost_complete=false;needs=true;}if(source.cost_components_complete===false){data.cost_components_complete=false;needs=true;}if(source.description_complete===false){data.description_complete=false;needs=true;}
 for(const key of ['has_target','exhausts','ethereal'])if(source[key]===true)data[key]=true;
 if(typeof source.description==='string'&&bytes(out)<budget-60){let text=source.description.slice(0,240);data.description=text;if(text.length<source.description.length){data.description_truncated=true;needs=true;}while(bytes(out)>budget&&text.length>24){text=text.slice(0,Math.floor(text.length*.7));data.description=text;data.description_truncated=true;needs=true;}if(bytes(out)>budget){delete data.description;delete data.description_truncated;needs=true;}}
 if(typeof source.description==='string'&&data.description===undefined)needs=true;
 if(needs)out.details_required=true;
 if(bytes(out)>budget&&data.description!==undefined){delete data.description;delete data.description_truncated;out.details_required=true;}
 for(const key of ['sockets','unknown_origin','gem_type','socket_capacity'])if(bytes(out)>budget&&Object.hasOwn(data,key)){delete data[key];out.details_required=true;}
 return out;
}
function flatCardSummary(ref:string,value:unknown,budget:number,forceDetails=false):Obj {const compact=cardSummary(ref,value,budget,forceDetails),out:Obj={ref,...obj(compact.data)};if(compact.details_required===true)out.details_required=true;return out;}
function orbSummary(value:unknown,index:number):Obj {
 const v=obj(value),ref='player/orbs/'+index,out:Obj={ref};
 if(v.visible===false||v.unavailable_reason!=null){Object.assign(out,pick(v,['details_complete','unavailable_reason','visible']));out.details_required=true;return out;}
 for(const key of ['name','passive_amount','evoke_amount','current_hp','max_hp','hp','upgraded','displayed_debuff'])if(Object.hasOwn(v,key)&&scalar(v[key],80))out[key]=v[key];
 if(v.stasis_card)out.stasis_card=flatCardSummary(ref+'/stasis_card',v.stasis_card,160,true);
 for(const key of ['details_complete','description_complete','stasis_card_unavailable_reason'])if(Object.hasOwn(v,key))out[key]=v[key];
 out.details_required=true;return out;
}
/** One shallow fragment; offset is a field/row index, or a UTF-16 text offset. */
function fragment(ref:string,value:unknown,offset:number,limit:number,budget=2400):Obj {
 const pageBudget=Math.max(360,Math.min(2400,budget));
 const cardDirectory=cardRef(ref);
 if(offset===0&&cardRef(ref)&&value!==null&&typeof value==='object'){
  const card:Obj={ref,data:null};let remaining=pageBudget-bytes(card)+4;const exceeded=Symbol('card budget exceeded'),spend=(chars:number)=>(remaining-=chars)>=0;
  const stringFits=(text:string)=>{const encoded=Buffer.byteLength(JSON.stringify(text),'utf8');return encoded<=remaining&&spend(encoded);};const omitted=(v:unknown)=>v===undefined||typeof v==='function'||typeof v==='symbol';
  const clean=(v:unknown,arrayItem=false):unknown=>{if(typeof v==='string')return stringFits(v)?v:exceeded;if(v===null||typeof v!=='object')return spend(omitted(v)?(arrayItem?4:0):Buffer.byteLength(JSON.stringify(v),'utf8'))?v:exceeded;if(!spend(2))return exceeded;
   if(Array.isArray(v)){if(Math.max(0,2*v.length-1)>remaining)return exceeded;const out:unknown[]=[];out.length=v.length;for(let i=0;i<v.length;i++){if(i>0&&!spend(1))return exceeded;const item=clean(v[i],true);if(item===exceeded)return exceeded;if(i in v)out[i]=item;}return out;}
   const source=obj(v),out:Obj={};let fields=0;for(const key in source){if(!Object.hasOwn(source,key)||provenance.has(key))continue;const item=source[key];if(!omitted(item)&&((fields++>0&&!spend(1))||!stringFits(key)||!spend(1)))return exceeded;const cleaned=clean(item);if(cleaned===exceeded)return exceeded;Object.defineProperty(out,key,{value:cleaned,enumerable:true,writable:true,configurable:true});}return out;};
  const data=clean(value);if(data!==exceeded){card.data=data;if(bytes(card)<=pageBudget)return card;}
 }
 if(typeof value==='string'){
  let end=Math.min(value.length,offset+1600);const page=():Obj=>({ref,text:value.slice(offset,end),...(end<value.length?{next_offset:end}:{})});
  while(bytes(page())>pageBudget&&end>offset+1)end=offset+Math.floor((end-offset)*.8);
  if(end<value.length&&/[\uD800-\uDBFF]/.test(value[end-1]))end--;
  const out=page();
  // A long ref can consume a batch allocation before even one code point fits.
  // Fail explicitly; never publish an unchanged cursor or split a surrogate pair.
  if(end<=offset&&offset<value.length||bytes(out)>pageBudget)throw new Error('Text page exceeds batch budget. Read this ref alone: '+ref);
  return out;
 }
 if(value===null||typeof value!=='object')return {ref,value};
 const array=Array.isArray(value),slice=array?value.slice(offset,offset+limit):[];
 if(array){
  const total=value.length,end=Math.min(total,offset+limit);
  if(ref!=='player/potions'&&slice.every(item=>scalar(item,240))){const out:Obj={ref,total,items:[]},items=out.items as unknown[];let i=offset;for(;i<end;i++){items.push(slice[i-offset]);if(bytes({...out,...(i+1<total?{next_offset:i+1}:{})})>pageBudget){items.pop();break;}}if(i>offset||i>=total){if(i<total)out.next_offset=i;return out;}}
 }
 const fields=array?slice.map((v,i)=>[String(offset+i),v] as const):Object.entries(obj(value)).filter(([key])=>!provenance.has(key)),total=array?value.length:fields.length,out:Obj={ref};if(array)out.total=total;if(ref==='player/potions'&&array)Object.assign(out,{total_kind:'slots'},potionCounts(value));if(cardDirectory&&offset===0)out.details_required=true;
 const data:Obj=Object.create(null),toc:Obj[]=[];
 if(cardDirectory&&offset===0)Object.assign(data,pick(obj(cardSummary(ref,value,pageBudget,false).data),['dead_on_position_active','description_complete','displayed_cost_complete','cost_components_complete']));
 let i=offset;for(;i<Math.min(total,offset+limit);i++){const [key,item]=fields[array?i-offset:i],path=child(ref,key);if(path.length>512||['__proto__','constructor','prototype'].includes(key)){out.information_complete=false;out.unavailable_reason='field_reference_budget_or_unsupported_key';continue;}
  const routineCardField=cardDirectory&&(['id','uuid','upgrades'].includes(key)||(['displayed_cost_complete','cost_components_complete'].includes(key)&&item===true)||(['exhausts','ethereal'].includes(key)&&item===false));
  if(!array&&!routineCardField&&(item===null||['number','boolean'].includes(typeof item)||typeof item==='string'&&item.length<=240))data[key]=item;else toc.push(entry(path,item,array?undefined:key));
  const pageBytes=()=>bytes({...out,data,toc,...(i+1<total?{next_offset:i+1}:{})});
  // Keep the first power/potion ref when prose cannot fit a multi-ref page.
  const row=toc.at(-1);if(i===offset&&row?.ref===path&&/^(?:(?:player|monsters\/\d+)\/powers|player\/potions)$/.test(ref)&&pageBytes()>pageBudget){
   delete row.description;row.details_required=true;
   if(pageBytes()>pageBudget)toc[toc.length-1]={ref:path,details_required:true,...pick(row,['slot','empty','description_complete','description_truncated','tooltips_complete','tooltips_truncated'])};
  }
  if(pageBytes()>pageBudget){delete data[key];if(toc.at(-1)?.ref===path)toc.pop();if(i===offset){out.information_complete=false;out.unavailable_reason='field_budget_exceeded';i++;}break;}}
 if(i<total)out.next_offset=i;if(!array&&Object.keys(data).length)out.data=data;if(toc.length)out.toc=toc;return out;
}
export function readContext(state:Obj,refs:string[],offset=0,limit=20):Obj {
 if(!Array.isArray(refs)||refs.length<1||refs.length>8||refs.some(r=>typeof r!=='string'||r.length<1||r.length>512)||!Number.isSafeInteger(offset)||offset<0||!Number.isInteger(limit)||limit<1||limit>30)throw new Error('Invalid fragment request.');
 const current=scope(state),{roots}=current,unique=[...new Set(refs)],perFragment=Math.max(360,Math.floor(3600/unique.length)),needed=unique.some(ref=>ref==='guidance'||ref.startsWith('guidance/')),guidance=needed?selectGuidance(state,current):undefined;if(guidance)roots.guidance=guidance.directory;
 return {fragments:unique.map(ref=>{
  if(ref==='guidance'||ref.startsWith('guidance/')){const capsule=guidanceFragment(guidance,ref,offset);if(capsule)return capsule;}
  const part=fragment(ref,publicResolve(roots,ref),offset,limit,perFragment);
  if(bytes(part)>Math.min(2400,perFragment))throw new Error('Fragment exceeds batch budget. Read this ref alone: '+ref);
  return part;
 })};
}
function combatEvidence(card:Obj,budget:number):Obj|undefined {
 if(budget<=2)return;const fields=['cost_components','cost_components_scope','cost_components_unavailable_reason','target_playability','available_energy','available_reserves','displayed_cost_kind','x_resource_budget','x_resource_budget_complete','x_resource_budget_scope'],out:Obj={};let found=false;
 for(const key of fields){if(!Object.hasOwn(card,key))continue;const value=card[key];if(key==='cost_components'||key==='target_playability'){if(!Array.isArray(value)||value.length>3)return;const rows:Obj[]=[];for(const item of value){if(item===null||typeof item!=='object'||Array.isArray(item))return;const row:Obj={};let count=0;for(const k in item){if(!Object.hasOwn(item,k))continue;if(++count>8||k.length>64)return;const v=(item as Obj)[k];if(!scalar(v,120))return;Object.defineProperty(row,k,{value:v,enumerable:true});}rows.push(row);}out[key]=rows;found=true;}else{if(!scalar(value,120))return;out[key]=value;}if(bytes(out)>budget)return;}return found?out:undefined;
}
function decisionHeader(state:Obj,screen:string):Obj {const out:Obj={screen};if(state.ready!==true)out.ready=false;const connection=connectionSummary(state);if(connection)out.connection=connection;return out;}
function ensureToc(out:Obj,ref:string,value:unknown){const toc=list(out.toc) as Obj[];if(!toc.some(x=>obj(x).ref===ref))toc.push(entry(ref,value,ref));}
function trimDecision(out:Obj,roots:Obj,actions:unknown[]):Obj {
 const combat=obj(out.combat),hand=list(combat.hand) as Obj[];
 const bossCards=list(combat.monsters).flatMap(value=>{const boss=obj(obj(value).public_combat),preview=obj(boss.roulette_preview);return [...list(boss.hand),...(preview.card?[preview.card]:[])];}) as Obj[];
 const potions=obj(obj(out.player).potion_inventory);
 const player=obj(out.player);
 if(bytes(out)>4400&&Array.isArray(player.orbs)){delete player.orbs;player.orbs_more='player/orbs';}
 if(bytes(out)>4400)for(const value of [...list(potions.slots)].reverse()){if(bytes(out)<=4400)break;const potion=obj(value);if(potion.description!==undefined){delete potion.description;potion.details_required=true;}}
 if(bytes(out)>4400&&Array.isArray(potions.slots)){delete potions.slots;potions.slots_more='player/potions';}
 if(bytes(out)>4400)for(const card of [...hand,...bossCards]){if(bytes(out)<=4400)break;if(card.description!==undefined){delete card.description;card.details_required=true;}}
 if(bytes(out)>4400){
  const strip=(powers:unknown)=>{for(const value of [...list(powers)].reverse()){if(bytes(out)<=4400)break;const power=obj(value);if(power.description!==undefined){delete power.description;power.details_required=true;}}};
  strip(obj(out.player).powers);for(const monster of list(combat.monsters))strip(obj(monster).powers);
 }
 if(bytes(out)>4400&&list(combat.monsters).length>4){combat.monsters=list(combat.monsters).slice(0,4);combat.monsters_more='monsters';}
 if(bytes(out)>4400&&Array.isArray(out.offers)&&out.offers.length>6){out.offers=out.offers.slice(0,6);out.offers_more='screen/cards';}
 const reading=obj(out.event_reading);if(bytes(out)>4400&&Array.isArray(reading.options)&&reading.options.length>6){reading.options=reading.options.slice(0,6);reading.options_more='screen/event_reading/options';}
 if(bytes(out)>4400&&Array.isArray(out.actions)&&out.actions.length>8){out.actions=out.actions.slice(0,8);out.actions_more='actions';ensureToc(out,'actions',actions);}
 if(bytes(out)>4400)for(const card of hand){for(const key of ['type','is_playable','has_target','exhausts','ethereal'])delete card[key];card.details_required=true;}
 if(bytes(out)>4400)for(const card of [...hand,...bossCards]){if(bytes(out)<=4400)break;for(const key of ['socket_capacity','sockets','gem_type','unknown_origin'])delete card[key];card.details_required=true;}
 if(bytes(out)>4400)for(const card of bossCards){
  if(bytes(out)<=4400)break;for(const key of ['slot','type','exhausts','ethereal'])delete card[key];if(card.visible===true)delete card.visible;card.details_required=true;
 }
 if(bytes(out)>4400)for(const card of bossCards){
  if(bytes(out)<=4400)break;if(typeof card.name==='string'&&card.name.length>32){card.name=card.name.slice(0,32);card.name_truncated=true;card.details_required=true;}
 }
 if(bytes(out)>4400)for(const card of bossCards){
  if(bytes(out)<=4400)break;
  const minimal:Obj={ref:card.ref,details_required:true,...pick(card,['visible','description_complete','description_truncated','displayed_cost_complete','details_complete','unavailable_reason','visibility_reason'])};
  for(const key of Object.keys(card))delete card[key];Object.assign(card,minimal);
 }
 if(bytes(out)>4400){
  const owners=[{summary:player,source:obj(roots.player),ref:'player/powers'},...list(combat.monsters).map((value,index)=>({summary:obj(value),source:obj(list(roots.monsters)[index]),ref:'monsters/'+index+'/powers'}))];
  for(const {summary,source,ref} of owners.reverse()){
   const powers=list(summary.powers);if(!powers.length)continue;
   while(bytes(out)>4400&&powers.length>1){powers.pop();summary.powers_more=ref;summary.power_count=list(source.powers).length;}
  }
 }
 if(bytes(out)>4400)for(const card of hand){
  if(bytes(out)<=4400)break;
  if(typeof card.name==='string'&&card.name.length>32){let end=32;if(/[\uD800-\uDBFF]/.test(card.name[end-1]))end--;card.name=card.name.slice(0,end);card.name_truncated=true;card.details_required=true;}
 }
 return out;
}
/** Compact default decision; detail roots remain available through toc/context. */
export function decision(state:Obj):Obj {
 const current=scope(state),{screen,combat,roots,unsupported}=current,o=obj(state.observation),c=obj(obj(o.game_state).combat_state),out:Obj=decisionHeader(state,screen);if(unsupported)out.unsupported_information=true;
 if(roots.player){
  const playerSource=obj(roots.player),keys=combat?['current_hp','max_hp','gold','energy','block']:['class','act','floor','current_hp','max_hp','gold'],player=inlineSummary(playerSource,keys),stance=obj(playerSource.stance),stanceName=stance.name??stance.id;
  if(Array.isArray(playerSource.potions))player.potion_inventory=potionSummary(playerSource.potions);
  if(typeof stanceName==='string'&&stanceName)player.stance=stanceName;
  if(combat){const powers=list(playerSource.powers);if(powers.length){player.powers=powersSummary(powers,6,'player/powers');if(powers.length>6)player.powers_more='player/powers';}}
  if(combat&&Array.isArray(playerSource.orbs)){player.orbs=playerSource.orbs.slice(0,6).map(orbSummary);if(playerSource.orbs.length>6)player.orbs_more='player/orbs';}
  Object.assign(player,mechanicsDecisionSummary(combat?obj(c.player).mechanics??obj(obj(o.game_state).mechanics):obj(obj(o.game_state).mechanics)));if(Object.keys(player).length)out.player=player;
 }
 const actions=list(roots.actions),actionMap=aliases(actions);out.actions=actions.slice(0,12).map(publicAction);if(actions.length>12)out.actions_more='actions';
 if(combat){
  const complete=handVisible(c,o),hand=complete?list(roots.hand):[],monsters=list(roots.monsters),shownHand=hand.slice(0,10);
  // Equality must not traverse evidence tables rejected by the summary's row bound.
  const boundedTables=shownHand.every(value=>['cost_components','target_playability'].every(key=>!Array.isArray(obj(value)[key])||list(obj(value)[key]).length<=3));
  const groupedHand=hand.length<=10&&boundedTables?groupEquivalentCards(shownHand):shownHand.map((value,index)=>({value,index,copies:1}));
  const perCard=Math.max(145,Math.floor(1800/Math.max(1,groupedHand.length))),evidence=groupedHand.map(group=>combatEvidence(obj(group.value),220));
  const inlineEvidence=hand.length<=10&&groupedHand.every((group,i)=>evidence[i]!==undefined||!['cost_components','target_playability'].some(k=>Object.hasOwn(obj(group.value),k)))&&evidence.reduce((n,v)=>n+(v?bytes(v):0),0)<=480;
  const combatOut:Obj={hand:groupedHand.map((group,i)=>{const summary=flatCardSummary('hand/'+group.index,group.value,perCard,false);if(inlineEvidence&&evidence[i])Object.assign(summary,evidence[i]);if(group.copies>1)summary.copies=group.copies;return summary;}),monsters:monsters.slice(0,8).map(monsterSummary)};
  if(!complete)combatOut.hand_complete=false;out.combat=combatOut;Object.assign(combatOut,inlineSummary(roots.combat));if(hand.length>10)combatOut.hand_more='hand';if(monsters.length>8)combatOut.monsters_more='monsters';
 }
 if(roots.screen){
  const screenValue=obj(roots.screen),keys=screen==='EVENT'&&present(screenValue.event_reading)?['event_name','for_upgrade']:['body_text','event_name','for_upgrade'],screenState=inlineSummary(screenValue,keys);if(Object.keys(screenState).length)out.screen_state=screenState;
 }
 if(screen==='EVENT'&&present(obj(roots.screen).event_reading)){
  const reading=publicEventReading(obj(roots.screen).event_reading),summary:Obj={body_ref:'screen/event_reading/body_text'};
  for(const key of ['phase','page_role','text_complete','unavailable_reason'])if(Object.hasOwn(reading,key))summary[key]=reading[key];
  const options=list(reading.options);if(options.length)summary.options=options.slice(0,12);out.event_reading=summary;
 }
 for(const key of ['rest_controls','reward_controls'])if(roots[key])out[key]=list(roots[key]).slice(0,12).map(value=>controlSummary(value,actionMap));
 if(['SHOP_SCREEN','CARD_REWARD','GRID','BOSS_REWARD'].includes(screen)&&Array.isArray(obj(roots.screen).cards))out.offers=list(obj(roots.screen).cards).slice(0,12).map((value,i)=>offerSummary(value,i,screen));
 for(const key of ['reward_navigation','selection_controls','card_selection_controls'])if(roots[key]){const summary=inlineSummary(roots[key]);if(Object.keys(summary).length)out[key]=summary;}if(roots.map_plan){const summary=inlineSummary(publicMapPlan(roots.map_plan),mapPlanKeys);if(Object.keys(summary).length)out.map_plan=summary;}
 const menu=obj(roots.menu);if(roots.menu&&(state.ready!==true||menu.reason!==undefined)){const summary=inlineSummary(roots.menu,['reason','game_screen']);if(Object.keys(summary).length)out.menu=summary;}
 const skip=new Set(['combat','menu']);if(actions.length<=12)skip.add('actions');
 for(const key of ['reward_navigation','selection_controls','card_selection_controls'])if(roots[key]&&scalarRecord(roots[key]))skip.add(key);
 for(const key of ['rest_controls','reward_controls'])if(compactControlArray(roots[key]))skip.add(key);
 if(roots.map_plan){const plan=publicMapPlan(roots.map_plan);if(list(plan.route).length===0&&Number(plan.stroke_count??0)===0)skip.add('map_plan');}
 out.toc=Object.entries(roots).filter(([ref])=>!skip.has(ref)).map(([ref,value])=>entry(ref,value,ref));
 const guidance=selectGuidance(state,current);if(guidance)list(out.toc).push({ref:'guidance'});return trimDecision(out,roots,actions);
}
function mapDecision(state:Obj):Obj {
 const o=obj(state.observation),g=obj(o.game_state),screen=String(obj(o.menu).screen??g.screen_type??'unavailable');if(state.ready===true&&list(state.actions).some(a=>typeof obj(a).id!=='string')){const full=decision(state);return {...pick(full,mapViewKeys),map_plan:full.map_plan??{status:'unavailable'}};}
 const plan=screen==='MAP'&&Object.keys(g).length&&present(g.map_plan)&&g.map_plan;return {...decisionHeader(state,screen),map_plan:plan?inlineSummary(publicMapPlan(plan),mapPlanKeys):{status:'unavailable'}};
}
export function conditionalDecision(state:Obj,knownView?:string,mode:'decision'|'map_plan'='decision'):Obj {
 const view=mode==='map_plan'?mapDecision(state):decision(state),viewId=createHash('sha256').update(mode+'|'+String(state.session_id)+'|'+String(state.state_id)+'|'+JSON.stringify(view)).digest('hex').slice(0,24);if(knownView===viewId)return {view_id:viewId,unchanged:true};return {...view,view_id:viewId};
}
