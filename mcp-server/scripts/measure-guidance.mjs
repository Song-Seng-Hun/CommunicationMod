// Scripted read workflows only. Never starts a game or a model.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';
import {Tiktoken} from 'js-tiktoken/lite';import ranks from 'js-tiktoken/ranks/o200k_base';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {workflows} from '../evaluation/economy-workflows.mjs';
import * as current from '../dist/context.js';
const enc=new Tiktoken(ranks),count=x=>enc.encode(typeof x==='string'?x:JSON.stringify(x),[],[]).length;
export const cost=(steps,skill,catalog)=>count(skill)+count(catalog)+steps.reduce((n,s)=>n+count(s.request)+count(s.response),0);
const duplicated=(steps,skill,catalog)=>cost(steps.map(s=>({...s,response:{content:[{type:'text',text:JSON.stringify(s.response)}],structuredContent:{data:s.response}}})),skill,catalog);
export async function measure(baselinePath){
 const baseline=path.resolve(baselinePath),repo=fileURLToPath(new URL('../../',import.meta.url));
 const manifest=JSON.parse(await readFile(path.join(baseline,'manifest.json'),'utf8'));
 if(!manifest.hashes?.['mcp-server/src/context.ts'])throw new Error('Frozen baseline manifest required');
 const old=await import(pathToFileURL(path.join(baseline,'mcp-server/dist/context.js')).href);
 const skillPath='plugin/downfall-agent/skills/downfall-play/SKILL.md';
 const beforeSkill=await readFile(path.join(baseline,skillPath),'utf8'),afterSkill=await readFile(path.join(repo,skillPath),'utf8');
 const client=new Client({name:'guidance-catalog-cost',version:'1'});let catalog;
 try{await client.connect(new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL('../dist/index.js',import.meta.url))],stderr:'pipe'}));catalog=(await client.listTools()).tools;}finally{await client.close();}
 // workflows already checks required native facts. Replay its exact reads with each projection.
 const rows=workflows();
 const results=[];
 for(const trace of rows){
  const s=trace.state;if(!s)throw new Error('Workflow must expose its synthetic state');
  const replay=(api,examples)=>{
   const steps=[];let last;
   const call=(name,args)=>{
    const response=name==='sts_get_state'?api.conditionalDecision(s,args.known_view,args.view):api.readContext(s,args.refs,args.offset??0,args.limit??20);
    steps.push({request:{name,arguments:args},response});if(name==='sts_get_state'&&!response.unchanged)last=response;return response;
   };
   for(const [i,step] of trace.after.entries()){
    const args=structuredClone(step.request.arguments);if(args.known_view)args.known_view=last.view_id;
    call(step.request.name,args);
    if(i===0 && examples && last.guidance?.toc.length){
     const normalRef=last.guidance.toc[0].ref;
     const seen=last.guidance.example?'guidance/'+last.guidance.example.id:undefined;
     if(examples==='oneshot'){
      if(seen!==normalRef)call('sts_get_context',{session_id:s.session_id,state_id:s.state_id,refs:[normalRef]});
      continue;
     }
     const ids={session_id:s.session_id,state_id:s.state_id};
     const root=call('sts_get_context',{...ids,refs:[last.guidance.more]});
     const cap=root.fragments[0].toc.find(x=>normalRef.startsWith(x.ref+'/'));
     if(!cap)throw new Error('Representative not discoverable in current directory');
     const dir=call('sts_get_context',{...ids,refs:[cap.ref]});
     const refs=dir.fragments[0].toc.filter(x=>x.ref!==seen).map(x=>x.ref);
     if(refs.length)call('sts_get_context',{session_id:s.session_id,state_id:s.state_id,refs});
    }
   }
   return steps;
  };
  const before=replay(old,false);
  for(const mode of ['inline','oneshot','fewshot']){
   const after=replay(current,mode==='inline'?false:mode);
   const b=cost(before,beforeSkill,catalog),a=cost(after,afterSkill,catalog);
   results.push({scenario:trace.name,mode,calls_before:before.length,calls_after:after.length,before:b,after:a,increase_percent:+((a/b-1)*100).toFixed(2),duplicated_before:duplicated(before,beforeSkill,catalog),duplicated_after:duplicated(after,afterSkill,catalog)});
  }
 }
 const report={measurement:'Local o200k scripted workflows, NOT actual agent usage/speed or billing',baseline,scope:'Each task includes skill and catalog once, all request/reply payloads and example lookup calls. Host replay/reasoning unknown; duplicated response bounds separate.',required_facts:'workflows assertions passed',real_game_actions:0,model_runs:0,rows:results};
 await mkdir(new URL('../../target',import.meta.url),{recursive:true});
 await writeFile(new URL('../../target/guidance-token-report.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify(report,null,2));return report;
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href){if(!process.argv[2])throw new Error('Usage: measure-guidance.mjs <frozen-baseline>');await measure(process.argv[2]);}
