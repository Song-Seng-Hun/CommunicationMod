import {encode,decode} from '@toon-format/toon';
import type {Tiktoken} from 'js-tiktoken/lite';
import type {CallToolResult} from '@modelcontextprotocol/sdk/types.js';
import type {Obj} from './view.js';

export type ContextFormat='json'|'compact';
const guide='TOON: 2-space indent; [N]{fields}: rows.\n';
const textResult=(text:string):CallToolResult=>({content:[{type:'text',text}]});
let tokenizer:Promise<Tiktoken>|undefined;

// No tokenizer initialization on small or non-tabular reads.
function getTokenizer(){
 return tokenizer??=Promise.all([import('js-tiktoken/lite'),import('js-tiktoken/ranks/o200k_base')])
  .then(([{Tiktoken},ranks])=>new Tiktoken(ranks.default));
}
function record(value:unknown):value is Record<string,unknown>{return value!==null&&typeof value==='object'&&!Array.isArray(value);}
function hasTable(value:unknown,depth=0):boolean{
 if(depth>32||value===null||typeof value!=='object')return false;
 // Two rows are enough to try: later byte/token checks reject cases where TOON is not worthwhile.
 if(Array.isArray(value)&&value.length>=2&&record(value[0])){
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
  const toon=encode(normalized,{indentSize:2,delimiter:','}),toonText=guide+toon;
  // Includes property order, omitted values, exact strings and all JSON types.
  if(JSON.stringify(decode(toon,{strict:true,indentSize:2}))!==json)return fallback;
  const jsonBytes=Buffer.byteLength(json,'utf8'),toonBytes=Buffer.byteLength(toonText,'utf8');
  if(toonBytes>=jsonBytes)return fallback;
  const candidate=textResult(toonText),encoder=await getTokenizer(),count=(text:string)=>encoder.encode(text,[],[]).length;
  const before=count(json),after=count(toonText);
  // Never trade more model tokens for fewer bytes. Adopt when either the model-token or host-byte saving is material.
  if(after>before)return fallback;
  const tokenWorth=before-after>=16&&after<=before*0.9;
  const byteWorth=jsonBytes-toonBytes>=256&&toonBytes<=jsonBytes*0.9;
  if(!tokenWorth&&!byteWorth)return fallback;
  // Protect hosts that serialize the entire MCP text block rather than only its text.
  const candidateWire=JSON.stringify(candidate),fallbackWire=JSON.stringify(fallback);
  if(count(candidateWire)>count(fallbackWire)||Buffer.byteLength(candidateWire,'utf8')>=Buffer.byteLength(fallbackWire,'utf8'))return fallback;
  return candidate;
 }catch{
  // Codec/tokenizer limitations must never hide context or turn a read into an error.
  return fallback;
 }
}
