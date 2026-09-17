import {obj,type Obj} from './view.js';

const ignored=new Set(['uuid','hand_index','description_source','description_rendering','tooltips_source','tooltips_rendering','render_frame']);

function canonical(value:unknown):unknown {
 if(Array.isArray(value))return value.map(canonical);
 if(value!==null&&typeof value==='object'){
  const source=obj(value),out:Obj={};
  for(const key of Object.keys(source).filter(key=>!ignored.has(key)).sort())out[key]=canonical(source[key]);
  return out;
 }
 return value;
}

export interface GroupedCard {value:unknown;index:number;copies:number;}

/** Collapse only cards whose complete observed state differs by identity/hand position metadata. */
export function groupEquivalentCards(cards:unknown[]):GroupedCard[] {
 const groups:GroupedCard[]=[],seen=new Map<string,GroupedCard>();
 for(let index=0;index<cards.length;index++){
  const value=cards[index],key=JSON.stringify(canonical(value)),existing=seen.get(key);
  if(existing){existing.copies++;continue;}
  const group={value,index,copies:1};groups.push(group);seen.set(key,group);
 }
 return groups;
}
