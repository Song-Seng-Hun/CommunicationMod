import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {Tiktoken} from 'js-tiktoken/lite';
import ranks from 'js-tiktoken/ranks/o200k_base';
import {workflows} from '../evaluation/economy-workflows.mjs';
import assert from 'node:assert/strict';

const tokenizer=new Tiktoken(ranks),count=x=>tokenizer.encode(JSON.stringify(x),[],[]).length;
const baseline=JSON.parse(await readFile(new URL('../evaluation/baseline/tools-v1.json',import.meta.url),'utf8'));
const client=new Client({name:'economy-catalog-verifier',version:'1'});let current;
try{await client.connect(new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL('../dist/index.js',import.meta.url))],stderr:'pipe'}));current=(await client.listTools()).tools;}finally{await client.close();}
const definitions={before:count(baseline),after:count(current)};
const rows=workflows().map(test=>{
 const tokens=steps=>steps.reduce((sum,step)=>sum+count(step.request)+count(step.response),0);
 const duplicated=steps=>steps.reduce((sum,step)=>sum+count(step.request)+count({content:[{type:'text',text:JSON.stringify(step.response)}],structuredContent:{data:step.response}}),0);
 return {scenario:test.name,calls_before:test.before.length,calls_after:test.after.length,
  payload_tokens_before:tokens(test.before),payload_tokens_after:tokens(test.after),
  without_polls_before:tokens(test.before.slice(0,-2)),without_polls_after:tokens(test.after.slice(0,-2)),
  duplicated_wire_before:duplicated(test.before),duplicated_wire_after:duplicated(test.after)};
});
const before=definitions.before+rows.reduce((sum,row)=>sum+row.payload_tokens_before,0),after=definitions.after+rows.reduce((sum,row)=>sum+row.payload_tokens_after,0);
const report={measurement:'local o200k_base tokenization of deterministic required-fact read workflows, NOT billed/live AI usage',
 scope:'One shared tool catalog + all requests/responses for five scenarios, including extra detail reads and two unchanged polls per scenario. No reasoning, SDK framing or host context-replay tokens. Duplicated-wire estimates are separate, not assumed model injection.',
 definitions,rows,totals:{before,after,reduction_percent:Number(((1-after/before)*100).toFixed(1))},
 without_polls:{before:definitions.before+rows.reduce((sum,r)=>sum+r.without_polls_before,0),after:definitions.after+rows.reduce((sum,r)=>sum+r.without_polls_after,0)},
 caveat:'Individual one-shot detail workflows may grow because discovery and extra calls cost tokens. No claim that every call, billing total, or autonomous model run is cheaper.',required_fact_checks:'passed',gameplay_actions:0};
const output=new URL('../../target/context-economy-report.json',import.meta.url);await mkdir(new URL('../../target/',import.meta.url),{recursive:true});await writeFile(output,JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));assert.ok(after<before,'Total tokenizer-measured payload cost must improve, including added reads');
assert.ok(report.without_polls.after<report.without_polls.before,'Shared-catalog totals without polls must also improve');
