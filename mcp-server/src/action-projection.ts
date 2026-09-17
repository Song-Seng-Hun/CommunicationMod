import {obj,list,type Obj} from './view.js';

export const actionAlias=(index:number)=>'a'+index;

const clone=<T>(value:T):T=>structuredClone(value);
const same=(a:unknown,b:unknown)=>JSON.stringify(a)===JSON.stringify(b);

function fixed(schema:unknown):{fixed:boolean;value?:unknown}{
 const s=obj(schema);
 if(Object.hasOwn(s,'const'))return {fixed:true,value:s.const};
 const values=list(s.enum);
 if(values.length===1)return {fixed:true,value:values[0]};
 return {fixed:false};
}

/** Hide parameters fully determined by the pinned native action. */
export function publicParameterSchema(value:unknown):Obj {
 const source=obj(value);if(!Object.keys(source).length)return {};
 const schema=clone(source),properties=obj(schema.properties);
 if(!Object.keys(properties).length)return schema;
 const visible:Obj={},hidden=new Set<string>();
 for(const [key,property] of Object.entries(properties)){
  if(fixed(property).fixed)hidden.add(key);else visible[key]=property;
 }
 schema.properties=visible;
 if(Array.isArray(schema.required)){
  const required=schema.required.filter(key=>typeof key==='string'&&!hidden.has(key));
  if(required.length)schema.required=required;else delete schema.required;
 }
 const structural=new Set(['type','additionalProperties','properties','required']);
 if(!Object.keys(visible).length&&Object.keys(schema).every(key=>structural.has(key)))return {};
 return schema;
}

/** Reinsert fixed parameters from the exact action schema selected in the pinned state. */
export function injectFixedArguments(schemaValue:unknown,argumentsValue:unknown):Obj {
 const out=clone(obj(argumentsValue)),properties=obj(obj(schemaValue).properties);
 for(const [key,property] of Object.entries(properties)){
  const item=fixed(property);if(!item.fixed)continue;
  if(Object.hasOwn(out,key)&&!same(out[key],item.value))throw new Error('Fixed action parameter does not match the pinned state.');
  out[key]=clone(item.value);
 }
 return out;
}
