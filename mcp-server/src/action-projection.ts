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
