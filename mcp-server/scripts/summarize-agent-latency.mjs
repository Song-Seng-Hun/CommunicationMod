// Read-only audit of raw evaluation evidence; writes a separate generated summary,
// never edits the original campaign ledger or results.
import {readFile,writeFile} from 'node:fs/promises';import path from 'node:path';
const root=process.argv[2];if(!root||!path.isAbsolute(root))throw new Error('Absolute campaign path required');
const lines=async file=>{try{return (await readFile(file,'utf8')).trim().split('\n').filter(Boolean).map(JSON.parse);}catch(e){if(e.code==='ENOENT')return [];throw e;}};
const starts=await lines(path.join(root,'starts.jsonl')),rows=[];
for(const start of starts){
 const dir=path.join(root,`${start.scenario}-${start.repetition}-${start.variant}`);
 const host=(await lines(path.join(dir,'host.jsonl'))).map(e=>JSON.parse(e.line));
 const proxy=await lines(path.join(dir,'mcp.jsonl'));
 let result;try{result=JSON.parse(await readFile(path.join(dir,'result.json'),'utf8'));}catch(e){if(e.code!=='ENOENT')throw e;}
 const usage=host.findLast(e=>e.type==='turn.completed')?.usage;
 rows.push({...start,recorded_result:!!result,status:result?.status??'interrupted_no_result',correct:result?.correct??false,
  host_calls:host.filter(e=>e.type==='item.started'&&e.item?.type==='mcp_tool_call').length,
  proxy_calls:proxy.filter(e=>e.kind==='request').length,backend_actions:proxy.filter(e=>e.kind==='backend_act').length,
  approval_denials:host.filter(e=>e.item?.type==='mcp_tool_call'&&e.item.error?.message?.includes('requires approval')).length,
  known_tokens:usage?usage.input_tokens+usage.output_tokens:null,elapsed_ms:result?.elapsed_ms??null});
}
const summary={attempted_starts:starts.length,completed_result_records:rows.filter(r=>r.recorded_result).length,
 qualified_runs:rows.filter(r=>r.correct).length,backend_actions:rows.reduce((n,r)=>n+r.backend_actions,0),
 known_tokens_lower_bound:rows.reduce((n,r)=>n+(r.known_tokens??0),0),usage_incomplete:rows.some(r=>r.known_tokens===null),
 adoption:false,reason:'Incomplete/incorrect runs; host approval blocks synthetic sts_act. No speed claim. No installation.',rows};
await writeFile(path.join(root,'audit-summary.json'),JSON.stringify(summary,null,2));console.log(JSON.stringify(summary,null,2));
