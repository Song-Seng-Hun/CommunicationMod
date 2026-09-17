import {encode,decode} from '@toon-format/toon';
import type {Tiktoken} from 'js-tiktoken/lite';
import type {CallToolResult} from '@modelcontextprotocol/sdk/types.js';
import type {Obj} from './view.js';

export type ContextFormat='json'|'compact';
const guide='TOON: 2-space indent; [N]{fields}: rows.\n';
const textResult=(text:string):CallToolResult=>({content:[{type:'text',text}]});
let tokenizer:Promise<Tiktoken>|undefined;

// No tokenizer initialization on legacy, small or non-tabular reads.
function getTokenizer(){
 return tokenizer??=Promise.all([import('js-tiktoken/lite'),import('js-tiktoken/ranks/o200k_base')])
  .then(([{Tiktoken},ranks])=>new Tiktoken(ranks.default));
}
function record(value:unknown):value is Record<string,unknown>{return value!==null&&typeof value==='object'&&!Array.isArray(value);}
function hasTable(value:unknown,depth=0):boolean{
 if(depth>32||value===null||typeof value!=='object')return false;
 if(Array.isArray(value)&&value.length>=3&&record(value[0])){
  const keys=Object.keys(value[0]);
  if(keys.length>0&&value.every(row=>{
   if(!record(row))return false;
   const rowKeys=Object.keys(row);
   return rowKeys.length===keys.length&&keys.every((key,i)=>rowKeys[i]===key&&(row[key]===null||typeof row[key]!=='object'));
  }))return true;
 }
 return Object.values(value).some(child=>hasTable(child,depth+1));
}

/** Presentation only. Emit one model-visible payload; never duplicate JSON in structuredContent. */
export async function formatContext(data:Obj,format:ContextFormat='json'):Promise<CallToolResult>{
 const json=JSON.stringify(data);
 if(format==='json')return textResult(json);
 const fallback=textResult(json);
 // Conversion is bounded; nothing is truncated when the bound is exceeded.
 if(json.length<512||json.length>32768)return fallback;
 try{
  const normalized:unknown=JSON.parse(json);
  if(!hasTable(normalized))return fallback;
  const toon=encode(normalized,{indentSize:2,delimiter:','});
  // Includes property order, omitted values, exact strings and all JSON types.
  if(JSON.stringify(decode(toon,{strict:true,indentSize:2}))!==json)return fallback;
  const candidate=textResult(guide+toon),encoder=await getTokenizer();
  const count=(text:string)=>encoder.encode(text,[],[]).length;
  const before=count(json),after=count(guide+toon);
  if(before-after<16||after>before*0.9)return fallback;
  // Protect hosts that serialize the entire text block.
  if(count(JSON.stringify(candidate))>=count(JSON.stringify(fallback)))return fallback;
  return candidate;
 }catch{
  // Codec/tokenizer limitations must never hide context or turn a read into an error.
  return fallback;
 }
}
