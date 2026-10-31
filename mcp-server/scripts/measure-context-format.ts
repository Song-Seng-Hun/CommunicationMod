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
import {performanceState} from '../evaluation/performance-fixtures.ts';
import {workflows,type Step} from '../evaluation/economy-workflows.ts';
import type {Wire} from '../evaluation/wire.ts';
type Totals='text_plus_requests'|'serialized_results_plus_requests';

const directory=readContext(performanceState(40),['collection/cards'],0,20);
let start=performance.now();const first:Wire=await formatContext(directory,'compact');const coldMs=performance.now()-start;
const tokenizer=new Tiktoken(ranks),textTokens=(text:string)=>tokenizer.encode(text,[],[]).length,jsonTokens=(data:unknown)=>textTokens(JSON.stringify(data));
const restore=(text:string)=>text.startsWith('TOON:')?decode(text.slice(text.indexOf('\n')+1),{strict:true}):JSON.parse(text);
const client=new Client({name:'context-format-audit',version:'1'});let catalog:Wire[];
try{await client.connect(new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL('../dist/index.js',import.meta.url))],stderr:'pipe'}));catalog=(await client.listTools()).tools;}finally{await client.close();}
// Isolate formatting on the current public contract, not a removed opt-in API.
assert.equal(catalog.find(t=>t.name==='sts_get_context').inputSchema.properties.response_format.default,'compact');
const definitions={before:jsonTokens(catalog),after:jsonTokens(catalog)};
const rows:Awaited<ReturnType<typeof measure>>[]=[];
async function measure(workflow:{name:string,after:Step[]}){
 let rawBefore=0,rawAfter=0,wireBefore=0,wireAfter=0,converted=0,reads=0,requestTokens=0;
 for(const step of workflow.after){
  const isRead=step.request.name==='sts_get_context',compact=['sts_get_state','sts_act','sts_get_context'].includes(step.request.name);if(isRead)reads++;
  const before:Wire=await formatContext(step.response),after:Wire=compact?await formatContext(step.response,'compact'):before;
  const requestCost=jsonTokens(step.request);requestTokens+=requestCost;
  assert.equal(JSON.stringify(restore(after.content[0].text)),JSON.stringify(step.response));
  if(after.content[0].text.startsWith('TOON:'))converted++;
  rawBefore+=requestCost+textTokens(before.content[0].text);
  rawAfter+=requestCost+textTokens(after.content[0].text);
  wireBefore+=requestCost+jsonTokens(before);
  wireAfter+=requestCost+jsonTokens(after);
 }
 return {scenario:workflow.name,requests:workflow.after.length,request_tokens:requestTokens,context_reads:reads,toon_replies:converted,text_plus_requests:{before:rawBefore,after:rawAfter},serialized_results_plus_requests:{before:wireBefore,after:wireAfter}};
}
for(const workflow of workflows())rows.push(await measure(workflow));
// Complete selective workflow: discovery -> directory pages -> selected card.
const browsing=performanceState(60),{view_id,...shown}=conditionalDecision(browsing);
const browsingSteps:Step[]=[{request:{name:'sts_get_state',arguments:{}},response:shown}];
const appendRead=(refs:string[],extra:{offset?:number,limit?:number}={})=>{
 const args={refs,...extra},response:Wire=readContext(browsing,refs,extra.offset??0,extra.limit??20);
 browsingSteps.push({request:{name:'sts_get_context',arguments:args},response});return response;
};
assert.ok(browsingSteps[0].response.toc.some((row:Wire)=>row.ref==='collection'));
assert.ok(appendRead(['collection']).fragments[0].toc.some((row:Wire)=>row.ref==='collection/cards'));
let offset=0,lastRef;do{const page=appendRead(['collection/cards'],{offset,limit:20}).fragments[0];lastRef=page.toc.at(-1).ref;const next=page.next_offset??null;if(next!==null)assert.ok(Number.isInteger(next)&&next>offset,'directory cursor must advance');offset=next;}while(offset!==null);
assert.equal(lastRef,'collection/cards/59');
const selected=appendRead([lastRef]).fragments[0].data;
assert.equal(selected.name,browsing.observation.game_state.mechanics.collection.cards[59].name);
assert.equal(selected.description,browsing.observation.game_state.mechanics.collection.cards[59].description);
assert.equal(selected.displayed_cost_complete,false);assert.equal(selected.id,'card-59');
// Include this already-measured workflow in the aggregate, without counting its catalog twice.
const browse=await measure({name:'browse_60_cards_selectively',after:browsingSteps});rows.push(browse);
const selective=structuredClone(browse);
for(const row of rows)for(const key of ['text_plus_requests','serialized_results_plus_requests'] as const)
 assert.ok(row[key].after<=row[key].before,`Workflow must not grow: ${row.scenario} ${key}`);
for(const key of ['text_plus_requests','serialized_results_plus_requests'] as const){
 selective[key].before+=definitions.before;selective[key].after+=definitions.after;
 assert.ok(selective[key].after<selective[key].before,`Selective full workflow must save: ${key}`);
}
const sum=(key:Totals)=>({before:definitions.before+rows.reduce((n,r)=>n+r[key].before,0),after:definitions.after+rows.reduce((n,r)=>n+r[key].after,0)});
const plain:Wire=await formatContext(directory),textOnlyJson={content:plain.content};
const samples=[];for(let i=0;i<40;i++){start=performance.now();await formatContext(directory,'compact');samples.push(performance.now()-start);}samples.sort((a,b)=>a-b);
const report={measurement:'Local o200k_base; deterministic synthetic data; NOT billed tokens or actual host injection',
 comparison:'JSON-only formatting vs current automatic compact; identical public requests and facts',
 definitions,rows,totals:{text_plus_requests_and_catalog:sum('text_plus_requests'),serialized_results_plus_requests_and_catalog:sum('serialized_results_plus_requests')},
 selective_browse_including_catalog:selective,
 directory_20_rows:{json_text:textTokens(plain.content[0].text),compact_text:textTokens(first.content[0].text),
  json_text_only_result:jsonTokens(textOnlyJson),compact_result:jsonTokens(first),
  text_reduction_percent:Number(((1-textTokens(first.content[0].text)/textTokens(plain.content[0].text))*100).toFixed(1))},
 latency_ms:{first_candidate_in_process:Number(coldMs.toFixed(2)),warm_median:Number(samples[20].toFixed(2)),warm_p95:Number(samples[38].toFixed(2))},
 limits:'Current catalog counted once per comparison. Every request and reply counted, including polls, discovery, pages and selected detail. JSON-only is a formatting counterfactual, not an alternate public state API. No duplicated structuredContent or opt-in compact argument. No model reasoning, context replay, billing or real gameplay measured.',
 required_fact_roundtrips:'passed',gameplay_actions:0};
assert.ok(report.directory_20_rows.compact_text<report.directory_20_rows.json_text);
assert.ok(report.totals.serialized_results_plus_requests_and_catalog.after<report.totals.serialized_results_plus_requests_and_catalog.before);
await mkdir(new URL('../../target/',import.meta.url),{recursive:true});
await writeFile(new URL('../../target/context-format-report.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
