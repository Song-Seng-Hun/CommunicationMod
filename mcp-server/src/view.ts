export type Obj = Record<string, unknown>;
export const obj = (x: unknown): Obj => x !== null && typeof x === 'object' && !Array.isArray(x) ? x as Obj : {};
export const list = (x: unknown): unknown[] => Array.isArray(x) ? x : [];
const pick = (o: Obj, keys: string[]): Obj => Object.fromEntries(keys.filter(k => k in o).map(k => [k,o[k]]));
// Remove repeated provenance, never card text, keyword text, numeric values or incompleteness flags.
function compact(x: unknown): unknown {
 if(Array.isArray(x))return x.map(compact);
 if(x && typeof x==='object')return Object.fromEntries(Object.entries(obj(x)).filter(([k])=>!['description_source','description_rendering','tooltips_source','tooltips_rendering','render_frame'].includes(k)).map(([k,v])=>[k,compact(v)]));
 return x;
}
export function focus(state: Obj): Obj {
 const observation=obj(state.observation), game=obj(observation.game_state), combat=obj(game.combat_state);
 const out:Obj={...pick(state,['session_id','state_id','ready','support','runtime','connection']),
  screen:game.screen_type ?? obj(observation.menu).screen,
  player:pick(game,['class','act','floor','ascension_level','current_hp','max_hp','gold','keys','relics','potions']),
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
 const screen={...obj(game.screen_state)};
 if(screen.event_reading){const reading={...obj(screen.event_reading)};delete reading.last_discussion;delete screen.body_text;delete screen.options;screen.event_reading=reading;}
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
 const sources:Obj={deck:g.deck,map:g.map,piles:pick(c,['draw_pile','discard_pile','exhaust_pile','draw_pile_order_visible','draw_pile_order']),
  history:g.narrative ?? obj(g.screen_state).event_reading,screen:g.screen_state,full:state.observation};
 const data=sources[name];
 if(Array.isArray(data))return {session_id:state.session_id,state_id:state.state_id,section:name,total:data.length,offset,items:compact(data.slice(offset,offset+limit)),next_offset:offset+limit<data.length?offset+limit:null};
 return {session_id:state.session_id,state_id:state.state_id,section:name,data:compact(data ?? null)};
}
