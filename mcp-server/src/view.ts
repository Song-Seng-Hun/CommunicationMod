export type Obj = Record<string, unknown>;
export const obj = (x: unknown): Obj => x !== null && typeof x === 'object' && !Array.isArray(x) ? x as Obj : {};
export const list = (x: unknown): unknown[] => Array.isArray(x) ? x : [];
const pick = (o: Obj, keys: string[]): Obj => Object.fromEntries(keys.filter(k => k in o).map(k => [k,o[k]]));
const summaryKeys=['name','id','label','type','rarity','upgrades','price','cost','misc','counter','disabled','can_use','can_discard','requires_target','reward_type','gold'];
const screenCollections=['cards','hand','relics','potions','rewards','choices','selected_cards','selected'];
// Remove repeated provenance, never card text, keyword text, numeric values or incompleteness flags.
function compact(x: unknown): unknown {
 if(Array.isArray(x))return x.map(compact);
 if(x && typeof x==='object')return Object.fromEntries(Object.entries(obj(x)).filter(([k])=>!['description_source','description_rendering','tooltips_source','tooltips_rendering','render_frame'].includes(k)).map(([k,v])=>[k,compact(v)]));
 return x;
}
function summary(x:unknown):unknown {
 if(!x || typeof x!=='object' || Array.isArray(x))return x;
 const source=obj(x),out=pick(source,summaryKeys);
 // Preserve an upstream choice index when one exists, but never invent one: actions are authoritative.
 if('choice_index' in source)out.choice_index=source.choice_index;
 return compact(out);
}
function compactPlayer(game:Obj):Obj {
 const player=pick(game,['class','act','floor','ascension_level','current_hp','max_hp','gold','keys','relics','potions']);
 if(Array.isArray(player.relics))player.relics=list(player.relics).map(summary);
 if(Array.isArray(player.potions))player.potions=list(player.potions).map(summary);
 return player;
}
function focusedScreen(raw:Obj):Obj {
 const screen={...raw};let detailCount=0;
 for(const key of screenCollections){
  if(!Array.isArray(screen[key]))continue;
  const values=list(screen[key]);detailCount+=values.length;screen[key]=values.map(summary);
 }
 if(detailCount>0)screen.details_on_demand={section:'screen',items:detailCount};
 return obj(compact(screen));
}
function screenPage(state:Obj,offset:number,limit:number):Obj {
 const g=obj(obj(state.observation).game_state),raw=obj(g.screen_state),items:unknown[]=[];
 const metadata={...raw};
 for(const key of screenCollections){
  if(!Array.isArray(raw[key]))continue;
  const values=list(raw[key]);delete metadata[key];
  for(let i=0;i<values.length;i++)items.push({collection:key,index:i,value:compact(values[i])});
 }
 if(items.length===0)return {session_id:state.session_id,state_id:state.state_id,section:'screen',data:compact(raw)};
 // Detailed card/relic text can be large. Keep each context call safely inline even when callers request 100.
 const pageSize=Math.min(limit,6),slice=items.slice(offset,offset+pageSize);
 return {session_id:state.session_id,state_id:state.state_id,section:'screen',total:items.length,offset,items:slice,next_offset:offset+pageSize<items.length?offset+pageSize:null,metadata:compact(metadata)};
}
export function focus(state: Obj): Obj {
 const observation=obj(state.observation), game=obj(observation.game_state), combat=obj(game.combat_state);
 const out:Obj={...pick(state,['session_id','state_id','ready','support','runtime','connection']),
  screen:game.screen_type ?? obj(observation.menu).screen,
  player:compactPlayer(game),
  actions:state.ready===true?list(state.actions):[],
  decision:observation.combat_decision, status:observation.menu,
  available_sections:['deck','map','piles','history','screen','full']};
 out.deck_count=list(game.deck).length;
 const knownGame=new Set(['class','act','floor','ascension_level','current_hp','max_hp','gold','keys','relics','potions','screen_type','screen_state','deck','map','combat_state','narrative','seed','choice_list']);
 out.extensions=Object.fromEntries(Object.entries(game).filter(([k])=>!knownGame.has(k)));
 const knownObservation=new Set(['in_game','game_state','combat_decision','menu','tutorial','selection_controls','reward_controls','card_reward_header']);
 out.observation_extensions=Object.fromEntries(Object.entries(observation).filter(([k])=>!knownObservation.has(k)));
 if(Object.keys(combat).length){
  const c={...combat};
  for(const pile of ['draw_pile','discard_pile','exhaust_pile']){c[pile+'_count']=list(c[pile]).length;delete c[pile];}
  if(combat.hand_complete===false)c.hand=[];
  out.combat=c;
 }
 const rawScreen={...obj(game.screen_state)},screen=focusedScreen(rawScreen);
 if(rawScreen.event_reading){const reading={...obj(rawScreen.event_reading)};delete reading.last_discussion;delete screen.body_text;delete screen.options;screen.event_reading=reading;}
 if(Object.keys(screen).length)out.screen_state=screen;
 for(const k of ['tutorial','selection_controls','reward_controls','card_reward_header'])if(k in observation)out[k]=observation[k];
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
 if(name==='screen')return screenPage(state,offset,limit);
 const sources:Obj={deck:g.deck,map:g.map,piles:pick(c,['draw_pile','discard_pile','exhaust_pile','draw_pile_order_visible','draw_pile_order']),
  history:g.narrative ?? obj(g.screen_state).event_reading,full:state.observation};
 const data=sources[name];
 if(Array.isArray(data)){
  const safeLimit=name==='deck'?Math.min(limit,12):limit;
  return {session_id:state.session_id,state_id:state.state_id,section:name,total:data.length,offset,items:compact(data.slice(offset,offset+safeLimit)),next_offset:offset+safeLimit<data.length?offset+safeLimit:null};
 }
 return {session_id:state.session_id,state_id:state.state_id,section:name,data:compact(data ?? null)};
}
