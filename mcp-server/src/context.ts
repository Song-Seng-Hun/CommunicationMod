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
const nativeKeys=new Set(['class','act','floor','ascension_level','current_hp','max_hp','gold','keys','relics','potions','screen_type','screen_state','deck','map','map_plan','combat_state','mechanics','narrative','seed','choice_list']);
const controlKeys=['tutorial','selection_controls','reward_controls','card_reward_header','potion_controls','rest_controls','shop_controls','reward_navigation','card_selection_controls'];
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
 reward:'Read reward_navigation before leaving: unclaimed rewards may be abandoned. Never auto-discard potion for space; discarding: separate destructive choice.'
};
interface Scope {screen:string;combat:boolean;roots:Obj;unsupported:boolean;}
const handVisible=(c:Obj,o:Obj)=> (c.hand_complete===true || obj(o.combat_decision).hand_complete===true)
 && c.hand_complete!==false && obj(o.combat_decision).hand_complete!==false && obj(o.combat_decision).mode!=='selection';
function mechanicsMeaningful(value:unknown):boolean {
 const v=obj(value);
 for(const [key,item] of Object.entries(v)){
  if(['scope','collection','combat_collection'].includes(key))continue;
  if(key==='character_supported'){if(item===false)return true;continue;}
  if(key==='information_issues'){if(Array.isArray(item)&&item.length)return true;continue;}
  if(key==='information_issue_count'){if(typeof item==='number'&&item>0)return true;continue;}
  if(key.endsWith('_complete')){if(item===false)return true;continue;}
  if(key.endsWith('_unavailable_reason')){if(item!=null)return true;continue;}
  if(mechanicsZeroNoise.has(key)&&item===0)continue;
  if(present(item))return true;
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
  add('player',{...pick(g,['class','act','floor','ascension_level','current_hp','max_hp','gold','keys','relics','potions']),...(combat?obj(c.player):{})});
  add('deck',g.deck);add('screen',g.screen_state);
  const mechanics=combat?obj(c.player).mechanics ?? g.mechanics:g.mechanics;
  if(mechanicsMeaningful(mechanics))add('mechanics',mechanics);
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
 const gated:Record<string,boolean>={tutorial:true,potion_controls:Object.keys(g).length>0,rest_controls:screen==='REST'||hasAction('run.rest.'),shop_controls:screen==='SHOP_SCREEN'||hasAction('run.shop.'),
  reward_controls:['COMBAT_REWARD','CARD_REWARD','BOSS_REWARD'].includes(screen),reward_navigation:['COMBAT_REWARD','CARD_REWARD','BOSS_REWARD'].includes(screen),card_reward_header:screen==='CARD_REWARD',selection_controls:['GRID','HAND_SELECT','CARD_REWARD'].includes(screen),card_selection_controls:['GRID','HAND_SELECT','CARD_REWARD'].includes(screen)};
 for(const key of controlKeys)if(gated[key])add(key,o[key]);
 const relevantRules:Obj={act:rules.act};if(combat)relevantRules.cost=rules.cost;if(['GRID','CARD_REWARD','SHOP_SCREEN','REST','EVENT'].includes(screen))relevantRules.upgrade=rules.upgrade;if(screen==='EVENT')relevantRules.event=rules.event;if(screen==='MAP')relevantRules.map=rules.map;if(roots.reward_navigation||roots.potion_controls)relevantRules.reward=rules.reward;if(Object.keys(roots).length)add('rules',relevantRules);
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
function monsterSummary(value:unknown,index:number):Obj {
 const v=obj(value),out:Obj={ref:'monsters/'+index};
 for(const key of ['name','current_hp','max_hp','intent','move_adjusted_damage'])if(Object.hasOwn(v,key)&&scalar(v[key],120))out[key]=v[key];
 if(typeof v.block==='number'&&v.block>0)out.block=v.block;
 if(typeof v.move_hits==='number'&&v.move_hits>1)out.move_hits=v.move_hits;
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
function sanitizeControlTree(value:unknown,actionAliases:Map<string,string>,depth=0):unknown {
 if(depth>24)return value;if(Array.isArray(value))return value.map(item=>sanitizeControlTree(item,actionAliases,depth+1));
 if(value&&typeof value==='object'){
  const out:Obj={};for(const [key,item] of Object.entries(obj(value))){
   if((key==='id'||key==='action_id')&&typeof item==='string'){const alias=actionAliases.get(item);if(alias)out.action=alias;continue;}
   if(key==='mutually_exclusive_with'&&typeof item==='string'){const alias=actionAliases.get(item);if(alias)out[key]=alias;continue;}
   out[key]=sanitizeControlTree(item,actionAliases,depth+1);
  }return out;
 }
 return value;
}
function entry(ref:string,value:unknown,title?:string):Obj {
 const v=obj(value),out:Obj={ref},leaf=ref.split('/').at(-1)??ref,label=String(title??v.name??v.label??v.title??v.id??leaf).slice(0,64);
 if(label!==ref&&label!==leaf)out.title=label;if(Array.isArray(value)||typeof value==='string')out.count=size(value);
 if(ref.startsWith('hand/')){if(typeof v.name==='string')out.title=v.name.slice(0,64);if(typeof v.cost==='number'||typeof v.cost==='string')out.cost=v.cost;}return out;
}
function resolve(roots:Obj,ref:string):unknown {
 let value:unknown=roots;for(const encoded of ref.split('/')){let key:string;try{key=decodeURIComponent(encoded);}catch{throw new Error('Reference not available in current state.');}
  if(!key||['__proto__','constructor','prototype'].includes(key)||value===null||typeof value!=='object'||!Object.hasOwn(value,key)||(Array.isArray(value)&&!/^(0|[1-9]\d*)$/.test(key)))throw new Error('Reference not available in current state.');value=(value as Obj)[key];}return value;
}
function publicResolve(roots:Obj,ref:string):unknown {
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
 if(ref==='screen'||ref.startsWith('screen/'))return resolve({screen:publicScreen(roots.screen)},ref);
 if(ref==='map_plan'||ref.startsWith('map_plan/'))return resolve({map_plan:publicMapPlan(roots.map_plan)},ref);
 const root=ref.split('/')[0];if(controlKeys.includes(root)&&Object.hasOwn(roots,root))return resolve({[root]:sanitizeControlTree(roots[root],actionMap)},ref);
 return resolve(roots,ref);
}
const cardRef=(ref:string)=>/^(?:(?:hand|deck|screen\/cards|collection\/cards|combat_collection\/cards)\/\d+|card_in_play)$/.test(ref);
function cardSummary(ref:string,value:unknown,budget:number,forceDetails=true):Obj {
 const source=obj(value),data:Obj={},out:Obj={ref,data};let needs=forceDetails;
 for(const key of ['name','cost','type','is_playable'])if(Object.hasOwn(source,key)&&scalar(source[key])){data[key]=source[key];if(bytes(out)>budget&&key!=='cost'&&key!=='name')delete data[key];}
 if(source.is_playable===false){if(scalar(source.unplayable_reason))data.unplayable_reason=source.unplayable_reason;else needs=true;}
 const rendered=source.displayed_cost_text;if(typeof rendered==='string'&&rendered.length<=32&&(typeof source.cost!=='number'||rendered!==String(source.cost)))data.displayed_cost_text=rendered;
 if(source.displayed_cost_complete===false){data.displayed_cost_complete=false;needs=true;}if(source.cost_components_complete===false){data.cost_components_complete=false;needs=true;}if(source.description_complete===false){data.description_complete=false;needs=true;}
 for(const key of ['has_target','exhausts','ethereal'])if(source[key]===true)data[key]=true;
 if(typeof source.description==='string'&&bytes(out)<budget-60){let text=source.description.slice(0,240);data.description=text;if(text.length<source.description.length){data.description_truncated=true;needs=true;}while(bytes(out)>budget&&text.length>24){text=text.slice(0,Math.floor(text.length*.7));data.description=text;data.description_truncated=true;needs=true;}if(bytes(out)>budget){delete data.description;delete data.description_truncated;needs=true;}}
 if(needs)out.details_required=true;
 if(bytes(out)>budget&&data.description!==undefined){delete data.description;delete data.description_truncated;out.details_required=true;}
 return out;
}
function flatCardSummary(ref:string,value:unknown,budget:number,forceDetails=false):Obj {const compact=cardSummary(ref,value,budget,forceDetails),out:Obj={ref,...obj(compact.data)};if(compact.details_required===true)out.details_required=true;return out;}
/** One shallow fragment; offset is a field/row index, or a UTF-16 text offset. */
function fragment(ref:string,value:unknown,offset:number,limit:number,budget=2400):Obj {
 const pageBudget=Math.max(360,Math.min(2400,budget));
 if(offset===0&&cardRef(ref)&&value!==null&&typeof value==='object'){
  const card:Obj={ref,data:null};let remaining=pageBudget-bytes(card)+4;const exceeded=Symbol('card budget exceeded'),spend=(chars:number)=>(remaining-=chars)>=0;
  const stringFits=(text:string)=>{const encoded=Buffer.byteLength(JSON.stringify(text),'utf8');return encoded<=remaining&&spend(encoded);};const omitted=(v:unknown)=>v===undefined||typeof v==='function'||typeof v==='symbol';
  const clean=(v:unknown,arrayItem=false):unknown=>{if(typeof v==='string')return stringFits(v)?v:exceeded;if(v===null||typeof v!=='object')return spend(omitted(v)?(arrayItem?4:0):Buffer.byteLength(JSON.stringify(v),'utf8'))?v:exceeded;if(!spend(2))return exceeded;
   if(Array.isArray(v)){if(Math.max(0,2*v.length-1)>remaining)return exceeded;const out:unknown[]=[];out.length=v.length;for(let i=0;i<v.length;i++){if(i>0&&!spend(1))return exceeded;const item=clean(v[i],true);if(item===exceeded)return exceeded;if(i in v)out[i]=item;}return out;}
   const source=obj(v),out:Obj={};let fields=0;for(const key in source){if(!Object.hasOwn(source,key)||provenance.has(key))continue;const item=source[key];if(!omitted(item)&&((fields++>0&&!spend(1))||!stringFits(key)||!spend(1)))return exceeded;const cleaned=clean(item);if(cleaned===exceeded)return exceeded;Object.defineProperty(out,key,{value:cleaned,enumerable:true,writable:true,configurable:true});}return out;};
  const data=clean(value);if(data!==exceeded){card.data=data;if(bytes(card)<=pageBudget)return card;}return cardSummary(ref,value,pageBudget,true);
 }
 if(typeof value==='string'){let end=Math.min(value.length,offset+1600);while(bytes({ref,text:value.slice(offset,end)})>pageBudget&&end>offset+1)end=offset+Math.floor((end-offset)*.8);if(end<value.length&&/[\uD800-\uDBFF]/.test(value[end-1]))end--;const out:Obj={ref,text:value.slice(offset,end)};if(end<value.length)out.next_offset=end;return out;}
 if(value===null||typeof value!=='object')return {ref,value};
 const array=Array.isArray(value),fields=array?value.slice(offset,offset+limit).map((v,i)=>[String(offset+i),v] as const):Object.entries(obj(value)).filter(([key])=>!provenance.has(key)),total=array?value.length:fields.length,out:Obj={ref};if(array)out.total=total;
 const data:Obj=Object.create(null),toc:Obj[]=[];let i=offset;for(;i<Math.min(total,offset+limit);i++){const [key,item]=fields[array?i-offset:i],path=child(ref,key);if(path.length>512||['__proto__','constructor','prototype'].includes(key)){out.information_complete=false;out.unavailable_reason='field_reference_budget_or_unsupported_key';continue;}
  if(!array&&(item===null||['number','boolean'].includes(typeof item)||typeof item==='string'&&item.length<=240))data[key]=item;else toc.push(entry(path,item,array?undefined:key));if(bytes({...out,data,toc})>pageBudget){delete data[key];if(toc.at(-1)?.ref===path)toc.pop();if(i===offset){out.information_complete=false;out.unavailable_reason='field_budget_exceeded';i++;}break;}}
 if(i<total)out.next_offset=i;if(!array&&Object.keys(data).length)out.data=data;if(toc.length)out.toc=toc;return out;
}
export function readContext(state:Obj,refs:string[],offset=0,limit=20):Obj {
 if(!Array.isArray(refs)||refs.length<1||refs.length>8||refs.some(r=>typeof r!=='string'||r.length<1||r.length>512)||!Number.isSafeInteger(offset)||offset<0||!Number.isInteger(limit)||limit<1||limit>30)throw new Error('Invalid fragment request.');
 const current=scope(state),{roots}=current,unique=[...new Set(refs)],perFragment=Math.max(360,Math.floor(3600/unique.length)),needed=unique.some(ref=>ref==='guidance'||ref.startsWith('guidance/')),guidance=needed?selectGuidance(state,current):undefined;if(guidance)roots.guidance=guidance.directory;
 return {fragments:unique.map(ref=>{if(ref==='guidance'||ref.startsWith('guidance/')){const capsule=guidanceFragment(guidance,ref,offset);if(capsule)return capsule;}return fragment(ref,publicResolve(roots,ref),offset,limit,perFragment);})};
}
function combatEvidence(card:Obj,budget:number):Obj|undefined {
 if(budget<=2)return;const fields=['cost_components','cost_components_scope','cost_components_unavailable_reason','target_playability','available_energy','available_reserves','displayed_cost_kind','x_resource_budget','x_resource_budget_complete','x_resource_budget_scope'],out:Obj={};let found=false;
 for(const key of fields){if(!Object.hasOwn(card,key))continue;const value=card[key];if(key==='cost_components'||key==='target_playability'){if(!Array.isArray(value)||value.length>3)return;const rows:Obj[]=[];for(const item of value){if(item===null||typeof item!=='object'||Array.isArray(item))return;const row:Obj={};let count=0;for(const k in item){if(!Object.hasOwn(item,k))continue;if(++count>8||k.length>64)return;const v=(item as Obj)[k];if(!scalar(v,120))return;Object.defineProperty(row,k,{value:v,enumerable:true});}rows.push(row);}out[key]=rows;found=true;}else{if(!scalar(value,120))return;out[key]=value;}if(bytes(out)>budget)return;}return found?out:undefined;
}
function decisionHeader(state:Obj,screen:string):Obj {const out:Obj={screen};if(state.ready!==true)out.ready=false;const connection=connectionSummary(state);if(connection)out.connection=connection;return out;}
function ensureToc(out:Obj,ref:string,value:unknown){const toc=list(out.toc) as Obj[];if(!toc.some(x=>obj(x).ref===ref))toc.push(entry(ref,value,ref));}
function trimDecision(out:Obj,roots:Obj,actions:unknown[]):Obj {
 const combat=obj(out.combat),hand=list(combat.hand) as Obj[];
 if(bytes(out)>4400)for(const card of hand)if(card.description!==undefined){delete card.description;delete card.description_truncated;card.details_required=true;}
 if(bytes(out)>4400&&list(combat.monsters).length>4){combat.monsters=list(combat.monsters).slice(0,4);combat.monsters_more='monsters';}
 if(bytes(out)>4400&&Array.isArray(out.offers)&&out.offers.length>6){out.offers=out.offers.slice(0,6);out.offers_more='screen/cards';}
 const reading=obj(out.event_reading);if(bytes(out)>4400&&Array.isArray(reading.options)&&reading.options.length>6){reading.options=reading.options.slice(0,6);reading.options_more='screen/event_reading/options';}
 if(bytes(out)>4400&&Array.isArray(out.actions)&&out.actions.length>8){out.actions=out.actions.slice(0,8);out.actions_more='actions';ensureToc(out,'actions',actions);}
 if(bytes(out)>4400)for(const card of hand){for(const key of ['type','is_playable','has_target','exhausts','ethereal'])delete card[key];card.details_required=true;}
 return out;
}
/** Compact default decision; detail roots remain available through toc/context. */
export function decision(state:Obj):Obj {
 const current=scope(state),{screen,combat,roots,unsupported}=current,o=obj(state.observation),c=obj(obj(o.game_state).combat_state),out:Obj=decisionHeader(state,screen);if(unsupported)out.unsupported_information=true;
 if(roots.player){
  const keys=combat?['current_hp','max_hp','gold','energy','block']:['class','act','floor','current_hp','max_hp','gold'],player=inlineSummary(roots.player,keys),stance=obj(obj(roots.player).stance),stanceName=stance.name??stance.id;
  if(typeof stanceName==='string'&&stanceName)player.stance=stanceName;
  Object.assign(player,mechanicsDecisionSummary(roots.mechanics));if(Object.keys(player).length)out.player=player;
 }
 const actions=list(roots.actions),actionMap=aliases(actions);out.actions=actions.slice(0,12).map(publicAction);if(actions.length>12)out.actions_more='actions';
 if(combat){
  const complete=handVisible(c,o),hand=complete?list(roots.hand):[],monsters=list(roots.monsters),shownHand=hand.slice(0,10);
  const groupedHand=hand.length<=10?groupEquivalentCards(shownHand):shownHand.map((value,index)=>({value,index,copies:1}));
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
 const skip=new Set(['combat','menu']);if(actions.length<=12)skip.add('actions');out.toc=Object.entries(roots).filter(([ref])=>!skip.has(ref)).map(([ref,value])=>entry(ref,value,ref));
 const guidance=selectGuidance(state,current);if(guidance)list(out.toc).push({ref:'guidance'});return trimDecision(out,roots,actions);
}
function mapDecision(state:Obj):Obj {
 const o=obj(state.observation),g=obj(o.game_state),screen=String(obj(o.menu).screen??g.screen_type??'unavailable');if(state.ready===true&&list(state.actions).some(a=>typeof obj(a).id!=='string')){const full=decision(state);return {...pick(full,mapViewKeys),map_plan:full.map_plan??{status:'unavailable'}};}
 const plan=screen==='MAP'&&Object.keys(g).length&&present(g.map_plan)&&g.map_plan;return {...decisionHeader(state,screen),map_plan:plan?inlineSummary(publicMapPlan(plan),mapPlanKeys):{status:'unavailable'}};
}
export function conditionalDecision(state:Obj,knownView?:string,mode:'decision'|'map_plan'='decision'):Obj {
 const view=mode==='map_plan'?mapDecision(state):decision(state),viewId=createHash('sha256').update(mode+'|'+String(state.session_id)+'|'+String(state.state_id)+'|'+JSON.stringify(view)).digest('hex').slice(0,24);if(knownView===viewId)return {view_id:viewId,unchanged:true};return {...view,view_id:viewId};
}
