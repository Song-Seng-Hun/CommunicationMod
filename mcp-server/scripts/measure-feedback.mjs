// Offline projections only. No model, game or tool dispatch.
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';
import {Tiktoken} from 'js-tiktoken/lite';
import ranks from 'js-tiktoken/ranks/o200k_base';
import * as current from '../dist/context.js';
import {workflows} from '../evaluation/economy-workflows.mjs';
import {toolSchemas} from '../dist/tool-schemas.js';
const root=fileURLToPath(new URL('../../',import.meta.url));
const baseline=path.join(root,'target/feedback-baseline-20260913');
const old=await import(pathToFileURL(path.join(baseline,'mcp-server/dist/context.js')).href);
const encoder=new Tiktoken(ranks),count=x=>encoder.encode(typeof x==='string'?x:JSON.stringify(x),[],[]).length;
const result=data=>({content:[{type:'text',text:JSON.stringify(data)}],structuredContent:{data}});
const sha=x=>createHash('sha256').update(x).digest('hex');
const skill=await readFile(new URL('../../plugin/downfall-agent/skills/downfall-play/SKILL.md',import.meta.url),'utf8');
assert.equal(skill,await readFile(path.join(baseline,'plugin/downfall-agent/skills/downfall-play/SKILL.md'),'utf8'));
assert.equal(await readFile(new URL('../src/tool-schemas.ts',import.meta.url),'utf8'),await readFile(path.join(baseline,'mcp-server/src/tool-schemas.ts'),'utf8'));
// Registration descriptions unchanged; shared schema/skill counted once in both.
const schemas=Object.fromEntries(Object.entries(toolSchemas).map(([name,schema])=>[name,schema.toJSONSchema()]));
const fixed=count(skill)+count(schemas);
const cost=calls=>fixed+calls.reduce((n,c)=>n+count(c.request)+count(result(c.response)),0);
const simple=()=>({id:'strike',name:'Strike',description:'Deal 6 damage.',is_playable:true,
 displayed_cost_text:'1',displayed_cost_complete:true,cost_components_complete:true,
 cost_components:[{resource:'energy',displayed_amount:1,kind:'fixed',free_to_play_once:false}],
 cost_components_scope:'pinned_native_payment_components_not_card_effects',
 target_playability:[{available:true,monster_index:0,name:'Slime'}]});
const state=cards=>({session_id:'synthetic',state_id:1,ready:true,actions:[],observation:{game_state:{screen_type:'NONE',combat_state:{hand_complete:true,hand:cards}}}});
const expensive=simple();expensive.target_playability[0].reason='Do not play: condition unresolved. '.repeat(15);
const incomplete=simple();incomplete.cost_components_complete=false;incomplete.cost_components[0].displayed_amount=null;
const scenarios=[['small_payment_target',state([simple()]),0],['incomplete_payment',state([incomplete]),0],['large_reason',state([expensive]),0],['late_card_in_large_hand',state(Array.from({length:10},simple)),9]];
const keys=['cost_components','cost_components_scope','target_playability','cost_components_complete'];
const facts=c=>Object.fromEntries(keys.filter(k=>Object.hasOwn(c,k)).map(k=>[k,c[k]]));
const rows=[];
for(const [name,s,index] of scenarios){
 const replay=api=>{
  const response=api.conditionalDecision(s),calls=[{request:{name:'sts_get_state',arguments:{wait_ms:0}},response}];
  let selected=response.combat.hand[index];
  if(!keys.every(k=>Object.hasOwn(selected,k))){
   const request={name:'sts_get_context',arguments:{session_id:s.session_id,state_id:s.state_id,refs:[selected.ref]}};
   const detail=api.readContext(s,request.arguments.refs);calls.push({request,response:detail});selected=detail.fragments[0].data;
  }
  assert.deepEqual(facts(selected),facts(s.observation.game_state.combat_state.hand[index]));return calls;
 };
 const before=replay(old),after=replay(current),a=cost(before),b=cost(after);
 assert.ok(b<=a*1.05,name+': more than 5% token regression');
 rows.push({name,before_calls:before.length,after_calls:after.length,before_tokens:a,after_tokens:b,change_percent:+((b/a-1)*100).toFixed(2)});
}
const regression=[];
for(const trace of workflows()){
 const replay=api=>{let known;return trace.after.map(step=>{
  const request=structuredClone(step.request),args=request.arguments;if(args.known_view)args.known_view=known;
  const response=request.name==='sts_get_state'?api.conditionalDecision(trace.state,args.known_view,args.view):api.readContext(trace.state,args.refs,args.offset??0,args.limit??20);
  if(response.view_id)known=response.view_id;return {request,response};
 });};
 const before=replay(old),after=replay(current);assert.deepEqual(after,before,'Existing workflow drift: '+trace.name);
 regression.push({name:trace.name,before_tokens:cost(before),after_tokens:cost(after),change_percent:0});
}
const report={kind:'Scripted required-evidence acquisition; no agent latency or whole action-cycle claim',
 accounting:'o200k: unchanged skill and input schemas once; every request and full text+structured MCP payload. Host replay, reasoning and billing unknown. Descriptions are unchanged but not counted.',
 baseline,rows,regression,hashes:{baseline_context:sha(await readFile(path.join(baseline,'mcp-server/dist/context.js'))),candidate_context:sha(await readFile(new URL('../dist/context.js',import.meta.url)))}};
await mkdir(path.join(root,'target/feedback-review'),{recursive:true});
await writeFile(path.join(root,'target/feedback-review/token-report.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
