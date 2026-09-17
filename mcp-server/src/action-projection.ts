import {obj,list,type Obj} from './view.js';

export const actionAlias=(index:number)=>'a'+index;

/** Compact semantic verb for a native action ID. Keeps routing opaque while preserving what the action does. */
export function actionOperation(value:unknown):string|undefined {
 const id=typeof value==='string'?value:'';if(!id)return;
 if(id==='acknowledge_event_reading')return 'ack';
 if(id==='run.end_turn')return 'end_turn';
 if(id.startsWith('run.play.'))return 'play';
 if(id==='run.reward.proceed'||id==='run.room.proceed'||id==='run.rest.proceed')return 'proceed';
 if(id.startsWith('run.reward.'))return 'claim';
 if(id.startsWith('run.potion.use.'))return 'potion_use';
 if(id.startsWith('run.potion.discard.'))return 'potion_discard';
 if(id==='run.card_reward.skip')return 'skip';
 if(id==='run.card_reward.bowl')return 'bowl';
 if(id.startsWith('run.card_reward.'))return 'take_card';
 if(id.startsWith('run.shop.card.')||id.startsWith('run.shop.relic.')||id.startsWith('run.shop.potion.'))return 'buy';
 if(id==='run.shop.purge')return 'purge';
 if(id==='run.shop.enter')return 'enter_shop';
 if(id==='run.shop.leave')return 'leave_shop';
 if(id==='run.chest.open')return 'open';
 if(id.startsWith('run.boss_relic.'))return id.endsWith('.skip')?'skip':'take_relic';
 if(id.startsWith('run.hand.select.'))return 'select';
 if(id.startsWith('run.hand.deselect.'))return 'deselect';
 if(id==='run.hand.confirm')return 'confirm';
 if(id.startsWith('run.grid.select.'))return 'select';
 if(id.startsWith('run.grid.deselect.'))return 'deselect';
 if(id.startsWith('run.grid.upgrade.'))return 'upgrade';
 if(id==='run.grid.confirm')return 'confirm';
 if(id==='run.grid.cancel')return 'cancel';
 if(id==='run.map.plan')return 'plan';
 if(id==='run.map.return')return 'return';
 if(id.startsWith('run.map.'))return 'move';
 if(id.startsWith('run.event.'))return 'choose';
 if(id==='run.tutorial.confirm')return 'confirm';
 if(id.startsWith('run.rest.'))return 'rest';
 if(id.startsWith('menu.'))return 'menu';
 return 'act';
}

const clone=<T>(value:T):T=>structuredClone(value);
const same=(a:unknown,b:unknown)=>JSON.stringify(a)===JSON.stringify(b);

function fixed(schema:unknown):{fixed:boolean;value?:unknown}{
 const s=obj(schema);
 if(Object.hasOwn(s,'const'))return {fixed:true,value:s.const};
 const values=list(s.enum);
 if(values.length===1)return {fixed:true,value:values[0]};
 return {fixed:false};
}
const requiredKeys=(schema:Obj)=>new Set(list(schema.required).filter((key):key is string=>typeof key==='string'));

/** Hide only required parameters fully determined by the exact pinned native action. */
export function publicParameterSchema(value:unknown):Obj {
 const source=obj(value);if(!Object.keys(source).length)return {};
 const schema=clone(source),properties=obj(schema.properties);
 if(!Object.keys(properties).length)return schema;
 const required=requiredKeys(schema),visible:Obj={},hidden=new Set<string>();
 for(const [key,property] of Object.entries(properties)){
  if(required.has(key)&&fixed(property).fixed)hidden.add(key);else visible[key]=property;
 }
 schema.properties=visible;
 if(Array.isArray(schema.required)){
  const publicRequired=schema.required.filter(key=>typeof key==='string'&&!hidden.has(key));
  if(publicRequired.length)schema.required=publicRequired;else delete schema.required;
 }
 const structural=new Set(['type','additionalProperties','properties','required']);
 if(!Object.keys(visible).length&&Object.keys(schema).every(key=>structural.has(key)))return {};
 return schema;
}

/** Reinsert only required fixed parameters from the exact action schema selected in the pinned state. */
export function injectFixedArguments(schemaValue:unknown,argumentsValue:unknown):Obj {
 const schema=obj(schemaValue),out=clone(obj(argumentsValue)),properties=obj(schema.properties),required=requiredKeys(schema);
 for(const [key,property] of Object.entries(properties)){
  if(!required.has(key))continue;
  const item=fixed(property);if(!item.fixed)continue;
  if(Object.hasOwn(out,key)&&!same(out[key],item.value))throw new Error('Fixed action parameter does not match the pinned state.');
  out[key]=clone(item.value);
 }
 return out;
}
