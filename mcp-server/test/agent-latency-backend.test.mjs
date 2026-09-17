import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile} from 'node:fs/promises';
import os from 'node:os';import path from 'node:path';import {fileURLToPath} from 'node:url';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';import {decode} from '@toon-format/toon';
import {scenario,judge} from '../evaluation/agent-latency/scenarios.mjs';
const server=fileURLToPath(new URL('../evaluation/agent-latency/server.mjs',import.meta.url)),dist=fileURLToPath(new URL('../dist',import.meta.url));
const restore=text=>text.startsWith('TOON:')?decode(text.slice(text.indexOf('\n')+1),{strict:true}):JSON.parse(text);
async function connect(id){const root=await mkdtemp(path.join(os.tmpdir(),'latency-test-')),log=path.join(root,'proxy.jsonl'),client=new Client({name:'fixture-test',version:'1'});await client.connect(new StdioClientTransport({command:process.execPath,args:[server,String(id),dist,log],stderr:'pipe'}));const call=async(name,args={})=>{const r=await client.callTool({name,arguments:args});return restore(r.content[0].text);};return {client,call,log};}

test('isolated evaluation runs pinned action/state guards, never game lifecycle',async()=>{
 const {client,call,log}=await connect(1);try{assert.equal((await client.listTools()).tools.length,6);await call('sts_get_state');const result=await call('sts_act',{action_id:'run.proceed'});assert.equal(result.outcome,undefined);assert.equal(result.state.screen,'VICTORY');const duplicate=await client.callTool({name:'sts_act',arguments:{action_id:'run.proceed'}});assert.equal(duplicate.isError,true);}finally{await client.close();}
 const rows=(await readFile(log,'utf8')).trim().split('\n').map(JSON.parse);assert.equal(rows.filter(e=>e.kind==='backend_act').length,1);assert.ok(rows.some(e=>e.kind==='response'&&e.name==='sts_act'));
});
test('forbidden lifecycle calls terminate evaluation before forwarding',async()=>{const {client,log}=await connect(1);try{await assert.rejects(()=>client.callTool({name:'sts_start_game',arguments:{}}));}finally{await client.close();}const rows=(await readFile(log,'utf8')).trim().split('\n').map(JSON.parse);assert.ok(rows.some(e=>e.kind==='violation'));assert.equal(rows.filter(e=>e.kind==='backend_act').length,0);});
for(let id=2;id<=6;id++)test(`scenario ${id}: required facts and safety reachable through actual MCP`,async()=>{
 const {client,call,log}=await connect(id),spec=scenario(id);try{
  if(id===6)assert.equal((await client.callTool({name:'sts_get_context',arguments:{refs:['actions']}})).isError,true,'context requires a presented state');
  await call('sts_get_state');const context=(refs,offset=0)=>call('sts_get_context',{refs,offset});
  if(id===2)await context(['hand/0','actions/0/parameters']);if(id===3)await context(['screen/cards/0','reward_navigation','potion_controls']);if(id===4){let offset=0;do{const r=await context(['screen/event_reading/body_text'],offset);offset=r.fragments[0].next_offset??null;}while(offset!==null);}if(id===5){await context(['collection/cards'],20);await context(['collection/cards/32']);}
  if(spec.act){const args=id===2?{target_index:0}:id===4?{reading_id:'reading-tower-10',commentary:'낡은 탑 입구 17. 붉은 문 체력 9 감소. 푸른 문 보상 없이 떠남.'}:{};const result=await call('sts_act',{action_id:spec.act,arguments:args});assert.equal(result.outcome,id===6?'unknown':undefined);if(id===6){assert.ok(result.request_id);const receipt=await call('sts_get_request',{request_id:result.request_id});assert.equal(receipt.pending,true);await call('sts_get_state');}}
 }finally{await client.close();}
 const rows=(await readFile(log,'utf8')).trim().split('\n').map(JSON.parse);assert.deepEqual(judge(id,spec.expected,rows),{correct:true,safe:true,failures:[]});
});
