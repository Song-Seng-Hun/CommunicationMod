export type Obj = Record<string, unknown>;
export const obj = (x: unknown): Obj => x !== null && typeof x === 'object' && !Array.isArray(x) ? x as Obj : {};
export const list = (x: unknown): unknown[] => Array.isArray(x) ? x : [];
const pick = (o: Obj, keys: string[]): Obj => Object.fromEntries(keys.filter(k => k in o).map(k => [k,o[k]]));
const planKeys=['map_id','revision','route_author','route_count','planned_start','current_node','previous_node','next_planned_node','status','stroke_count','editing','scope','edge_policy'];
const controlKeys=['tutorial','selection_controls','reward_controls','card_reward_header','potion_controls','rest_controls','shop_controls','reward_navigation','card_selection_controls'];
function mapNode(value:unknown):Obj {
 const n=obj(value),out={...n};
 if(Number.isInteger(n.x)&&Number.isInteger(n.y))out.node_id=`${n.x},${n.y}`;
 if(Array.isArray(n.children))out.children=n.children.map(mapNode);
 return out;
}
export function mapSummary(state:Obj):Obj {
 const observation=obj(state.observation),game=obj(observation.game_state),plan=obj(game.map_plan);
 return {...pick(state,['session_id','state_id','ready','connection']),screen:game.screen_type ?? obj(observation.menu).screen,
  map_plan:Object.keys(plan).length?pick(plan,planKeys):{status:'unavailable'}};
}
// Remove repeated provenance, never card text, keyword text, numeric values or incompleteness flags.
function compact(x: unknown): unknown {
 if(Array.isArray(x))return x.map(compact);
 if(x && typeof x==='object')return Object.fromEntries(Object.entries(obj(x)).filter(([k])=>!['description_source','description_rendering','tooltips_source','tooltips_rendering','render_frame'].includes(k)).map(([k,v])=>[k,compact(v)]));
 return x;
}
function mechanicSummary(value:unknown):Obj {
 const out={...obj(value)};
 for(const key of ['collection','combat_collection'])if(out[key]){
  const pile={...obj(out[key])};delete pile.cards;pile.cards_context=key;out[key]=pile;
 }
 return out;
}
export function focus(state: Obj): Obj {
 const observation=obj(state.observation), game=obj(observation.game_state), combat=obj(game.combat_state);
 const out:Obj={...pick(state,['session_id','state_id','ready','support','runtime','connection']),
  screen:game.screen_type ?? obj(observation.menu).screen,
  player:pick(game,['class','act','floor','ascension_level','current_hp','max_hp','gold','keys','relics','potions']),
  actions:state.ready===true?list(state.actions):[],
  decision:observation.combat_decision, status:observation.menu,
  available_sections:['deck','map','map_plan','piles','mechanics','collection','combat_collection','history','screen','full']};
 if(game.mechanics)obj(out.player).mechanics=mechanicSummary(game.mechanics);
 if(Object.keys(obj(game.map_plan)).length)out.map_plan=pick(obj(game.map_plan),planKeys);
 out.deck_count=list(game.deck).length;
 const knownGame=new Set(['class','act','floor','ascension_level','current_hp','max_hp','gold','keys','relics','potions','screen_type','screen_state','deck','map','map_plan','combat_state','mechanics','narrative','seed','choice_list']);
 out.extensions=Object.fromEntries(Object.entries(game).filter(([k])=>!knownGame.has(k)));
 const knownObservation=new Set(['in_game','game_state','combat_decision','menu',...controlKeys]);
 out.observation_extensions=Object.fromEntries(Object.entries(observation).filter(([k])=>!knownObservation.has(k)));
 if(Object.keys(combat).length){
  const c={...combat};
  if(obj(combat.player).mechanics)c.player={...obj(combat.player),mechanics:mechanicSummary(obj(combat.player).mechanics)};
  for(const pile of ['draw_pile','discard_pile','exhaust_pile']){c[pile+'_count']=list(c[pile]).length;delete c[pile];}
  if(combat.hand_complete===false)c.hand=[];
  out.combat=c;
 }
 const screen={...obj(game.screen_state)};
 if(screen.event_reading){
  const reading={...obj(screen.event_reading)};
  const previews=list(screen.options).flatMap((value,option_index)=>{
   const option=obj(value);
   return option.card_preview?[{option_index,...pick(option,['choice_index','disabled','card_preview'])}]:[];
  });
  if(previews.length)screen.option_card_previews=previews;
  delete reading.last_discussion;delete screen.body_text;delete screen.options;screen.event_reading=reading;
 }
 if(Object.keys(screen).length)out.screen_state=screen;
 for(const k of controlKeys)if(k in observation)out[k]=observation[k];
 const narrative=obj(game.narrative);
 if(Object.keys(narrative).length){
  const entries=list(narrative.entries), current=entries.filter(e=>obj(e).currently_displayed!==false);
  const body=obj(screen.event_reading).body_text;
  out.narrative={...Object.fromEntries(Object.entries(narrative).filter(([k])=>!['entries','history','render_frame'].includes(k))),
   history_count:entries.length-current.length,
   entries:current.map(e=>{const entry={...obj(e)};
    if(typeof body==='string' && entry.text===body){delete entry.text;delete entry.visible_text;entry.event_reading_ref=true;}
    else if(entry.text===entry.visible_text)delete entry.visible_text;
    return entry;
   })};
 }
 return obj(compact(out));
}
export function section(state:Obj,name:string,offset:number,limit:number):Obj {
 const g=obj(obj(state.observation).game_state), c=obj(g.combat_state);
 const mechanics=obj(c.player).mechanics ?? g.mechanics;
 const envelope={session_id:state.session_id,state_id:state.state_id,section:name};
 if(name==='collection' || name==='combat_collection') {
  const value=obj(mechanics)[name];
  if(value==null)return {...envelope,data:null};
  const page=collectionPage(value,offset,limit),{cards,...metadata}=page;
  return {...envelope,...metadata,items:compact(cards)};
 }
 if(name==='mechanics' && mechanics!=null) {
  const data={...obj(mechanics)};
  for(const key of ['collection','combat_collection'])if(data[key]!=null)data[key]=collectionPage(data[key],offset,limit);
  return {...envelope,data:compact(data)};
 }
 const sources:Obj={deck:g.deck,map:Array.isArray(g.map)?g.map.map(mapNode):g.map,map_plan:pick(obj(g.map_plan),[...planKeys,'route']),piles:pick(c,['draw_pile','discard_pile','exhaust_pile','draw_pile_order_visible','draw_pile_order']),
  mechanics,history:g.narrative ?? obj(g.screen_state).event_reading,screen:g.screen_state,full:state.observation};
 const data=sources[name];
 if(Array.isArray(data))return {session_id:state.session_id,state_id:state.state_id,section:name,total:data.length,offset,items:compact(data.slice(offset,offset+limit)),next_offset:offset+limit<data.length?offset+limit:null};
 return {session_id:state.session_id,state_id:state.state_id,section:name,data:compact(data ?? null)};
}
function collectionPage(value:unknown,offset:number,limit:number):Obj {
 const pile=obj(value),cards=list(pile.cards),end=offset+limit;
 return {...pile,cards:cards.slice(offset,end),total:cards.length,offset,
  next_offset:end<cards.length?end:null,
  // This means the page contains the entire captured pile, not that nested card data is complete.
  page_complete:offset===0 && end>=cards.length && Array.isArray(pile.cards)};
}
