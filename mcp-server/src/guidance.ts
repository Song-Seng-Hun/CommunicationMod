/** Packaged, non-executable examples. No live action binding or automatic calls. */
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {capabilities,type Capability,type Capsule} from './guidance-catalog.js';
import {obj,list,type Obj} from './view.js';

const hash=(v:string)=>createHash('sha256').update(v).digest('hex');
const unavailable=()=>{throw new Error('Reference not available in current state.');};
const cleanProse=(text:string)=>text
 .replace(/same current session\/state;?\s*/gi,'')
 .replace(/current session\/state and\s*/gi,'')
 .replace(/Stale required IDs\/refs/gi,'Stale refs')
 .replace(/No current IDs\/refs for context/gi,'No current refs for context')
 .replace(/current IDs\/refs/gi,'current refs');
function publicCapsule(source:Capsule):Capsule {
 const capsule=structuredClone(source) as Capsule & {revision?:string};
 delete capsule.revision;
 for(const [key,binding] of Object.entries(capsule.bindings))if(['current.session_id','current.state_id'].includes(binding.source))delete capsule.bindings[key];
 for(const call of capsule.calls){delete call.arguments.session_id;delete call.arguments.state_id;}
 for(const key of ['when','not_when','requires','expect','stop'] as const)capsule[key]=cleanProse(capsule[key]);
 return capsule;
}
export function exampleFragment(capsule:Capsule):Obj {
 return {ref:'guidance/'+capsule.id,data:publicCapsule(capsule)};
}
// Validate the build artifact once. Missing/stale packaging disables guidance only.
const active=new Map<string,{capability:Capability;revision:string;inline?:Capsule}>();
try {
 const bundle=JSON.parse(readFileSync(new URL('./guidance-bundle.json',import.meta.url),'utf8'));
 const required=['context.js','view.js','session.js','guidance.js','guidance-catalog.js','tool-schemas.js'];
 if(required.some(name=>bundle.modules?.[name]!==hash(readFileSync(new URL(name,import.meta.url),'utf8'))))throw new Error('Guidance contract mismatch');
 for(const capability of capabilities){
  const stamp=bundle.capabilities?.[capability.id];
  if(!stamp || stamp.digest!==hash(JSON.stringify(capability)))continue;
  active.set(capability.id,{capability,revision:stamp.digest.slice(0,16)});
 }
}catch{ /* Existing state/rules remain available even without example artifacts. */ }

// Index once, not a catalog scan/tokenizer pass per state. Source data stays immutable.
const screens=new Map<string,Set<string>>(),rootsIndex=new Map<string,Set<string>>(),actionsIndex=new Map<string,Set<string>>();
const specials=new Map<string,Set<string>>();
const index=(map:Map<string,Set<string>>,key:string,id:string)=>{if(!map.has(key))map.set(key,new Set());map.get(key)!.add(id);};
for(const [id,{capability:c}] of active){
 for(const s of c.gate.screens??[])index(screens,s,id);
 for(const r of c.gate.roots??[])index(rootsIndex,r,id);
 for(const a of c.gate.actions??[])index(actionsIndex,a,id);
 if(c.gate.special)index(specials,c.gate.special,id);
}
export interface GuidanceSelection {summary:Obj;directory:Obj;capsules:Map<string,Capsule>;}
export function selectGuidance(state:Obj,scope:{screen:string;roots:Obj;unsupported:boolean}):GuidanceSelection|undefined {
 // Raw identity gates validity internally; it is never emitted in guidance.
 if(typeof state.session_id!=='string'||!state.session_id||!Number.isSafeInteger(state.state_id)||Number(state.state_id)<0)return;
 const ids=new Set<string>(),add=(values?:Set<string>)=>{for(const value of values??[])ids.add(value);};
 const connection=obj(state.connection),pending=!!connection.pending_request_id;
 const blocked=state.ready!==true || pending || (typeof connection.status==='string' && connection.status!=='connected') || scope.unsupported;
 if(blocked){
  add(specials.get(pending?'pending':'unready'));
 }else{
  add(specials.get('ready'));add(screens.get(scope.screen));
  for(const root of Object.keys(scope.roots))add(rootsIndex.get(root));
  let empty=false,parameters=false;
  for(const value of list(scope.roots.actions)){
   const a=obj(value);if(typeof a.id!=='string')continue;
   let matched=actionsIndex.get(a.id);
   if(!matched)for(let i=a.id.lastIndexOf('.');i>=0;i=a.id.lastIndexOf('.',i-1)){
    matched=actionsIndex.get(a.id.slice(0,i+1));if(matched || i===0)break;
   }
   add(matched);
   if(a.parameters!==null && typeof a.parameters==='object' && !Array.isArray(a.parameters)){
    if(Object.keys(a.parameters).length)parameters=true;else empty=true;
   }
  }
  if(empty)add(specials.get('empty_parameters'));if(parameters)add(specials.get('parameters'));
 }
 const selected=[...ids].map(id=>active.get(id)!).filter(x=>x && x.capability.gate.special!=='bootstrap'
  && (!blocked || x.capability.id.startsWith('recovery.')))
  .sort((a,b)=>a.capability.priority-b.capability.priority || (a.capability.id<b.capability.id?-1:1));
 if(!selected.length)return;
 const directory:Obj=Object.create(null),capsules=new Map<string,Capsule>();
 for(const {capability:c} of selected){
  const cases:Obj=Object.create(null);
  for(const capsule of c.cases){cases[capsule.case]={title:capsule.case};capsules.set('guidance/'+capsule.id,capsule);}
  directory[c.id]=cases;
 }
 const first=selected[0].capability;
 const summary:Obj={ref:'guidance/'+first.id+'/normal'};
 return {summary,directory,capsules};
}

/** Canonical paths only. Do not expose capsule descendants without safety context. */
export function guidanceFragment(selection:GuidanceSelection|undefined,ref:string,offset:number):Obj|undefined {
 if(!selection)unavailable();
 if(ref==='guidance')return;
 if(!/^guidance\/[a-z][a-z0-9.-]*(?:\/(?:normal|incomplete|exception))?$/.test(ref))return unavailable();
 const parts=ref.split('/');if(!Object.hasOwn(selection!.directory,parts[1]))return unavailable();
 if(parts.length===2)return;
 const capsule=selection!.capsules.get(ref);if(!capsule)return unavailable();
 if(offset!==0)throw new Error('Example offset must be 0; capsule is atomic.');
 return exampleFragment(capsule);
}
