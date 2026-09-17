import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {performance} from 'node:perf_hooks';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {Tiktoken} from 'js-tiktoken/lite';
import ranks from 'js-tiktoken/ranks/o200k_base';
import {decode} from '@toon-format/toon';
import {formatContext} from '../dist/format.js';
import {readContext,conditionalDecision} from '../dist/context.js';
import {performanceState} from '../evaluation/performance-fixtures.mjs';
import {workflows} from '../evaluation/economy-workflows.mjs';

const directory=readContext(performanceState(40),['collection/cards'],0,20);
let start=performance.now();const first=await formatContext(directory,'compact');const coldMs=performance.now()-start;
const tokenizer=new Tiktoken(ranks),textTokens=text=>tokenizer.encode(text,[],[]).length,jsonTokens=data=>textTokens(JSON.stringify(data));
const restore=text=>text.startsWith('TOON:')?decode(text.slice(text.indexOf('\n')+1),{strict:true}):JSON.parse(text);
const client=new Client({name:'context-format-audit',version:'1'});let catalog;
try{await client.connect(new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL('../dist/index.js',import.meta.url))],stderr:'pipe'}));catalog=(await client.listTools()).tools;}finally{await client.close();}
// Only catalog change in this feature is this optional field; prose stays unchanged.
const prior=structuredClone(catalog);delete prior.find(t=>t.name==='sts_get_context').inputSchema.properties.response_format;
const definitions={before:jsonTokens(prior),after:jsonTokens(catalog)};
const rows=[];
async function measure(workflow,useCompact=step=>step.request.name==='sts_get_context'){
 let rawBefore=0,rawAfter=0,wireBefore=0,wireAfter=0,converted=0,reads=0;
 for(const step of workflow.after){
  const isRead=step.request.name==='sts_get_context',compact=isRead&&useCompact(step);if(isRead)reads++;
  const before=await formatContext(step.response),after=compact?await formatContext(step.response,'compact'):before;
  const nextRequest=compact?{...step.request,arguments:{...step.request.arguments,response_format:'compact'}}:step.request;
  assert.equal(JSON.stringify(restore(after.content[0].text)),JSON.stringify(step.response));
  if(after.content[0].text.startsWith('TOON:'))converted++;
  rawBefore+=jsonTokens(step.request)+textTokens(before.content[0].text);
  rawAfter+=jsonTokens(nextRequest)+textTokens(after.content[0].text);
  wireBefore+=jsonTokens(step.request)+jsonTokens(before);
  wireAfter+=jsonTokens(nextRequest)+jsonTokens(after);
 }
 return {scenario:workflow.name,context_reads:reads,toon_replies:converted,text_plus_requests:{before:rawBefore,after:rawAfter},serialized_results_plus_requests:{before:wireBefore,after:wireAfter}};
}
for(const workflow of workflows())rows.push(await measure(workflow));
// Complete selective workflow: discovery -> directory pages -> selected card.
const browsing=performanceState(60),ids={session_id:browsing.session_id,state_id:browsing.state_id};
const browsingSteps=[{request:{name:'sts_get_state',arguments:{}},response:conditionalDecision(browsing)}];
const appendRead=(refs,extra={})=>{
 const args={...ids,refs,...extra},response=readContext(browsing,refs,extra.offset??0,extra.limit??20);
 browsingSteps.push({request:{name:'sts_get_context',arguments:args},response});return response;
};
assert.ok(browsingSteps[0].response.toc.some(row=>row.ref==='collection'));
assert.ok(appendRead(['collection']).fragments[0].toc.some(row=>row.ref==='collection/cards'));
let offset=0,lastRef;do{const page=appendRead(['collection/cards'],{offset,limit:20}).fragments[0];lastRef=page.toc.at(-1).ref;offset=page.next_offset;}while(offset!==null);
assert.equal(appendRead([lastRef]).fragments[0].data.id,'card-59');
const selective=await measure({name:'browse_60_cards_selectively',after:browsingSteps},step=>step.request.arguments.refs?.[0]==='collection/cards');
for(const key of ['text_plus_requests','serialized_results_plus_requests']){
 selective[key].before+=definitions.before;selective[key].after+=definitions.after;
 assert.ok(selective[key].after<selective[key].before,`Selective full workflow must save: ${key}`);
}
const sum=key=>({before:definitions.before+rows.reduce((n,r)=>n+r[key].before,0),after:definitions.after+rows.reduce((n,r)=>n+r[key].after,0)});
const plain=await formatContext(directory),textOnlyJson={content:plain.content};
const samples=[];for(let i=0;i<40;i++){start=performance.now();await formatContext(directory,'compact');samples.push(performance.now()-start);}samples.sort((a,b)=>a-b);
const report={measurement:'Local o200k_base; deterministic synthetic data; NOT billed tokens or actual host injection',
 definitions,rows,totals:{text_plus_requests_and_catalog:sum('text_plus_requests'),serialized_results_plus_requests_and_catalog:sum('serialized_results_plus_requests')},
 selective_browse_including_catalog:selective,
 directory_20_rows:{json_text:textTokens(plain.content[0].text),compact_text:textTokens(first.content[0].text),
  legacy_dual_result:jsonTokens(plain),json_text_only_result:jsonTokens(textOnlyJson),compact_result:jsonTokens(first),
  text_reduction_percent:Number(((1-textTokens(first.content[0].text)/textTokens(plain.content[0].text))*100).toFixed(1))},
 latency_ms:{first_candidate_in_process:Number(coldMs.toFixed(2)),warm_median:Number(samples[20].toFixed(2)),warm_p95:Number(samples[38].toFixed(2))},
 limits:'Catalog counted once. Compact request overhead counted every read. JSON fallback can make short text-only workflows cost more; structured duplication savings depend on host. No model reasoning, context replay, billing or real gameplay measured.',
 required_fact_roundtrips:'passed',gameplay_actions:0};
assert.ok(report.directory_20_rows.compact_text<report.directory_20_rows.json_text);
assert.ok(report.totals.serialized_results_plus_requests_and_catalog.after<report.totals.serialized_results_plus_requests_and_catalog.before);
await mkdir(new URL('../../target/',import.meta.url),{recursive:true});
await writeFile(new URL('../../target/context-format-report.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
