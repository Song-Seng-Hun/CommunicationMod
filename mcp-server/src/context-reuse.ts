import {createHash} from 'node:crypto';
import {formatContext,tokenCount,type ContextFormat} from './format.js';
import {obj,type Obj} from './view.js';

type Patch={base_view:string,set:[string,unknown][],remove:string[]};
type Entry={value:Obj,expires:number,bytes:number};
const record=(x:unknown):x is Obj=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const clone=(x:Obj):Obj=>JSON.parse(JSON.stringify(x));
const forbidden=new Set(['__proto__','prototype','constructor']);
const pointer=(key:string)=>key.replace(/~/g,'~0').replace(/\//g,'~1');
function diff(before:unknown,after:unknown,patch:Patch,path='',depth=0){
 if(JSON.stringify(before)===JSON.stringify(after))return;
 if(depth>32||patch.set.length+patch.remove.length>=128)throw new Error('Delta budget');
 if(record(before)&&record(after)){
  for(const key of new Set([...Object.keys(before),...Object.keys(after)])){
   if(forbidden.has(key))throw new Error('Unsafe delta path');
   const p=path+'/'+pointer(key);
   if(!Object.hasOwn(after,key))patch.remove.push(p);
   else if(!Object.hasOwn(before,key))patch.set.push([p,after[key]]);
   else diff(before[key],after[key],patch,p,depth+1);
  }
 }else patch.set.push([path,after]); // Arrays and scalar values are atomic.
}

/** Reference decoder. Only reconstructs JSON; never dispatches game actions. */
export function restoreDelta(base:Obj,response:Obj):Obj{
 const patch=response.delta as Patch;
 if(!patch||base.view_id!==patch.base_view||base.session_id!==response.session_id)throw new Error('Wrong delta base');
 const result=clone(base);
 const parent=(path:string):[Obj,string]=>{
  if(!path.startsWith('/'))throw new Error('Invalid delta path');
  const keys=path.slice(1).split('/').map(k=>k.replace(/~1/g,'/').replace(/~0/g,'~'));
  if(keys.some(k=>forbidden.has(k)))throw new Error('Unsafe delta path');
  let node=result;
  for(const k of keys.slice(0,-1)){
   if(!Object.hasOwn(node,k)||!record(node[k]))throw new Error('Invalid delta parent');
   node=node[k] as Obj;
  }
  return [node,keys.at(-1)!];
 };
 for(const path of patch.remove){const [node,key]=parent(path);delete node[key];}
 for(const [path,value] of patch.set){const [node,key]=parent(path);node[key]=JSON.parse(JSON.stringify(value));}
 return result;
}

/** One stdio client; bounded, ephemeral FULL emissions only. No timers or disk state. */
export class ContextReuse{
 private entries=new Map<string,Entry>();private bytes=0;
 private now:()=>number;private ttl:number;private maxEntries:number;private maxBytes:number;
 constructor(options:{now?:()=>number,ttlMs?:number,maxEntries?:number,maxBytes?:number}={}){
  this.now=options.now??Date.now;this.ttl=options.ttlMs??300000;
  this.maxEntries=options.maxEntries??128;this.maxBytes=options.maxBytes??524288;
 }
 get stats(){return {entries:this.entries.size,bytes:this.bytes};}
 clear(){this.entries.clear();this.bytes=0;}
 private drop(key:string){const old=this.entries.get(key);if(old){this.bytes-=old.bytes;this.entries.delete(key);}}
 private get(key:string){const entry=this.entries.get(key);if(entry&&entry.expires>this.now())return entry.value;this.drop(key);}
 private put(key:string,value:Obj){
  const json=JSON.stringify(value),bytes=Buffer.byteLength(json);
  if(json.length>32768||bytes>this.maxBytes)return;
  for(const [k,v] of this.entries)if(v.expires<=this.now())this.drop(k);
  this.drop(key);
  while(this.entries.size>=this.maxEntries||this.bytes+bytes>this.maxBytes){const oldest=this.entries.keys().next().value;if(oldest===undefined)return;this.drop(oldest);}
  this.entries.set(key,{value:JSON.parse(json),bytes,expires:this.now()+this.ttl});this.bytes+=bytes;
 }
 private key(...parts:unknown[]){return JSON.stringify(parts);}
 private async saves(full:Obj,reduced:Obj,input:Obj,format:ContextFormat='json'){
  if(JSON.stringify(full).length>32768)return false;
  const [a,b]=await Promise.all([formatContext(full,format),formatContext(reduced,format)]);
  const text=(r:typeof a)=>r.content.map(c=>c.type==='text'?c.text:'').join('');
  const overhead=await tokenCount(JSON.stringify(input));
  const before=await tokenCount(text(a)),after=await tokenCount(text(b))+overhead;
  return before-after>=32&&after<=before*0.9&&await tokenCount(JSON.stringify(b))+overhead<await tokenCount(JSON.stringify(a));
 }
 async state(full:Obj,mode='decision',knownView?:string):Promise<Obj>{
  try{
   const key=this.key('state',full.session_id,mode,full.view_id);
   const base=knownView===undefined?undefined:this.get(this.key('state',full.session_id,mode,knownView));
   if(base&&JSON.stringify(base.screen)===JSON.stringify(full.screen)){
    const header:Obj={};for(const k of ['session_id','state_id','ready','connection','view_id'])if(Object.hasOwn(full,k))header[k]=full[k];
    let reduced:Obj;
    if(base.view_id===full.view_id)reduced={...header,unchanged:true};
    else{
     const patch:Patch={base_view:knownView!,set:[],remove:[]};diff(base,full,patch);
     reduced={...header,delta:patch};
     // Exact typed reconstruction, including removals. Do not trust a partial diff.
     if(JSON.stringify(restoreDelta(base,reduced))!==JSON.stringify(full)){
      // Property insertion order can differ without a semantic change; fall back conservatively.
      this.put(key,full);return full;
     }
    }
    if(await this.saves(full,reduced,{reuse:true,known_view:knownView}))return reduced;
   }
   this.put(key,full);
  }catch{ /* Presentation failure must not hide an applied action/receipt. */ }
  return full;
 }
 async context(full:Obj,known:Record<string,string>={},offset=0,limit=20,format:ContextFormat='json'):Promise<Obj>{
  try{
   const fragments=Array.isArray(full.fragments)?full.fragments:[];
   const candidate:unknown[]=[],complete:unknown[]=[];
   for(const item of fragments){
    const f=obj(item),json=JSON.stringify(f);
    const id=createHash('sha256').update(this.key(full.session_id,offset,limit,f)).digest('hex').slice(0,24);
    const tagged={...f,content_id:id};
    if(json.length<768||JSON.stringify(tagged).length>2400){candidate.push(f);complete.push(f);continue;}
    const key=this.key('fragment',full.session_id,f.ref,offset,limit,id);
    const retained=known[String(f.ref)]===id&&this.get(key);
    complete.push(tagged);
    candidate.push(retained?{ref:f.ref,content_id:id,unchanged:true}:tagged);
   }
   const all={...full,fragments:complete},short={...full,fragments:candidate};
   const result=JSON.stringify(candidate)!==JSON.stringify(complete)&&await this.saves(all,short,{reuse:true,known},format)?short:all;
   // Only full fragments actually emitted may become future baselines.
   for(const item of result.fragments){const f=obj(item);if(f.content_id&&!f.unchanged)this.put(this.key('fragment',full.session_id,f.ref,offset,limit,f.content_id),f);}
   return result;
  }catch{return full;}
 }
}
