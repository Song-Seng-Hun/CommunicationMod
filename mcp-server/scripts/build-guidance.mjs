// Offline only: validates declarative examples, never invokes their tools.
import assert from 'node:assert/strict';
import {readFile,writeFile,readdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {Tiktoken} from 'js-tiktoken/lite';
import ranks from 'js-tiktoken/ranks/o200k_base';
import {toolSchemas} from '../dist/tool-schemas.js';
import {contextText} from '../hooks/session-start.mjs';

const digest=v=>createHash('sha256').update(v).digest('hex');
const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const sources=new Map(Object.entries({
 'previous.view_id':'string','current.session_id':'string','current.state_id':'number',
 'current.observed_refs':'array','last.fragment.next_offset':'number',
 'current.offered_action.id':'string','last.request_id':'string',
 'agent.arguments_validated_against_current.offered_action.parameters_and_complete_relevant_evidence':'object',
 'current.event_reading.reading_id':'string',
 'agent.commentary_already_presented_to_user_for_current.event_reading.reading_id':'string',
 'current.offered_action.parameters.properties.map_id.const':'string',
 'current.offered_action.parameters.properties.revision.const':'number',
 'agent.selected_node_ids_from_current.observed_map_validated_against_drawn_edges_and_offered_route_schema':'array'
}));
export async function sourceContract(){
 const root=new URL('../../',import.meta.url);
 const files=['mcp-server/src/tool-schemas.ts','mcp-server/src/context.ts','mcp-server/src/view.ts','mcp-server/src/session.ts'];
 const walk=async relative=>{
  for(const entry of await readdir(new URL(relative+'/',root),{withFileTypes:true})){
   const name=relative+'/'+entry.name;
   if(entry.isDirectory())await walk(name);else if(name.endsWith('.java'))files.push(name);
  }
 };
 await walk('src/main/java/communicationmod');files.sort();
 const parts=[];
 for(const file of files)parts.push([file,digest((await readFile(new URL(file,root),'utf8')).replace(/\r\n/g,'\n'))]);
 return {fingerprint:digest(JSON.stringify(parts)),files:files.length};
}
export function validateSourceContract(expected,actual){
 assert.equal(actual.fingerprint,expected.fingerprint,'Guidance source contract changed: revalidate examples/tests and explicitly update the reviewed contract record.');
}
export function bindCall(call,bindings,values){
 for(const binding of Object.values(bindings))assert.equal(sources.get(binding.source),binding.type,'Unknown or mistyped binding source: '+binding.source);
 const bind=v=>{
  if(object(v) && Object.hasOwn(v,'$bind')){
   assert.deepEqual(Object.keys(v),['$bind'],'binding must be a single typed reference');
   const key=v.$bind,b=bindings[key];assert.ok(b && Object.hasOwn(values,key),'missing binding: '+key);
   const value=values[key],type=Array.isArray(value)?'array':value===null?'null':typeof value;
   assert.equal(type,b.type,'binding type: '+key);
   return structuredClone(value);
  }
  if(Array.isArray(v))return v.map(bind);
  if(object(v))return Object.fromEntries(Object.entries(v).map(([k,x])=>[k,bind(x)]));
  return v;
 };
 for(const [key,source] of [['session_id','current.session_id'],['state_id','current.state_id']]){
  if(Object.hasOwn(call.arguments,key))assert.equal(bindings[call.arguments[key]?.$bind]?.source,source,key+' binding source');
 }
 const args=bind(call.arguments);assert.ok(Object.hasOwn(toolSchemas,call.tool),'unknown tool: '+call.tool);
 toolSchemas[call.tool].parse(args);
 return args;
}
function samples(bindings){
 return Object.fromEntries(Object.entries(bindings).map(([key,b])=>[key,
  b.type==='number'?17:b.type==='object'?{}:b.type==='array'?['deck/0']:'synthetic-observed']));
}
export function validateCatalog(capabilities){
 const names=new Set(),ids=new Set(),report=[];
 for(const cap of capabilities){
  assert.match(cap.id,/^[a-z][a-z0-9.-]*$/);assert.ok(!names.has(cap.id),'duplicate capability');names.add(cap.id);
  assert.deepEqual(cap.cases.map(c=>c.case).sort(),['exception','incomplete','normal']);
  assert.ok(cap.covers.length>0,cap.id+' coverage');
  for(const c of cap.cases){
   assert.equal(c.id,cap.id+'/'+c.case);assert.equal(c.capability,cap.id);assert.ok(c.revision);
   assert.ok(!ids.has(c.id),'duplicate example');ids.add(c.id);
   for(const key of ['when','not_when','requires','expect','stop'])assert.ok(typeof c[key]==='string' && c[key].trim(),c.id+': '+key);
   assert.ok(object(c.bindings));assert.ok(Array.isArray(c.calls));
   const fragment={ref:'guidance/'+c.id,format:'example',data:c,page_complete:true,next_offset:null};
   assert.ok(JSON.stringify(fragment).length<=2400,c.id+': exceeds 2400 character budget ('+JSON.stringify(fragment).length+')');
   const values=samples(c.bindings);
   for(const call of c.calls){
    for(const key of ['action_id','request_id','refs'])if(Object.hasOwn(call.arguments,key)){
     const v=call.arguments[key];assert.ok(object(v)&&Object.hasOwn(v,'$bind') || Array.isArray(v)&&v.every(x=>object(x)&&Object.hasOwn(x,'$bind')),c.id+': literal dynamic '+key);
    }
    bindCall(call,c.bindings,values);
   }
  }
  report.push({id:cap.id,examples:cap.cases.length,covers:cap.covers});
 }
 return {capabilities:report,examples:ids.size};
}
export async function buildGuidance(){
 const contract=await sourceContract();
 validateSourceContract(JSON.parse(await readFile(new URL('../evaluation/guidance-contract.json',import.meta.url),'utf8')),contract);
 const {capabilities}=await import('../dist/guidance-catalog.js');
 const report=validateCatalog(capabilities),encoder=new Tiktoken(ranks);
 const modules={};
 for(const name of ['context.js','view.js','session.js','guidance.js','guidance-catalog.js','tool-schemas.js'])modules[name]=digest(await readFile(new URL('../dist/'+name,import.meta.url),'utf8'));
 const stamps={};
 for(const c of capabilities){
  const normal=c.cases.find(x=>x.case==='normal'),tokens=encoder.encode(JSON.stringify(normal),[],[]).length;
  stamps[c.id]={digest:digest(JSON.stringify(c)),...(tokens<=160?{inline:normal.id,inline_tokens:tokens}:{})};
 }
 const revision=digest(JSON.stringify(stamps)).slice(0,16),hookTokens=encoder.encode(contextText(revision),[],[]).length;
 assert.ok(hookTokens<=128,'SessionStart context exceeds 128 token budget');
 const hook={revision,tokens:hookTokens,digest:digest(await readFile(new URL('../hooks/session-start.mjs',import.meta.url)))};
 const bundle={version:1,contract,modules,capabilities:stamps,hook};
 await writeFile(new URL('../dist/guidance-bundle.json',import.meta.url),JSON.stringify(bundle)+'\n');
 console.log(JSON.stringify({guidance:report.examples,capabilities:report.capabilities.length,inline:Object.values(stamps).filter(x=>x.inline).length}));
 return bundle;
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href)await buildGuidance();
