import test from 'node:test';import assert from 'node:assert/strict';import path from 'node:path';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
test('ten frozen read-only evaluation answers are reachable through actual MCP tools',async()=>{
 const client=new Client({name:'frozen-verifier',version:'1'});await client.connect(new StdioClientTransport({command:process.execPath,args:[path.resolve('evaluation/server.mjs')],stderr:'pipe'}));
 try{
  const call=async(name,args={})=>{const r=await client.callTool({name,arguments:args});assert.notEqual(r.isError,true);return r.structuredContent.data;};
  const s=await call('sts_get_state',{wait_ms:1000});assert.equal(s.session_id,'frozen-evaluation-v1');
  const context=(section,extra={})=>call('sts_get_context',{session_id:s.session_id,state_id:s.state_id,section,...extra});
  const deck=[];for(let offset=0;offset!==null;){const page=await context('deck',{offset,limit:3});deck.push(...page.items);offset=page.next_offset;}
  const piles=(await context('piles')).data,history=(await context('history')).data,request=await call('sts_get_request',{request_id:'fixture-choice'});
  const map=[];for(let offset=0;offset!==null;){const page=await context('map',{offset,limit:2});map.push(...page.items);offset=page.next_offset;}
  const first=s.combat.hand[0],center=deck.find(c=>c.uuid===s.combat.hand[2].uuid),offhand=new Set([...piles.draw_pile,...piles.discard_pile,...piles.exhaust_pile].map(c=>c.uuid));
  const answers=[deck.filter(c=>c.id===first.id).length,center.name,Math.max(...deck.filter(c=>c.id===first.id).map(c=>c.displayed_values.D)),piles.draw_pile_order_visible?'True':'False',deck.filter(c=>c.type==='SKILL'&&piles.discard_pile.some(d=>d.uuid===c.uuid)).length,map.filter(n=>n.symbol==='R'&&n.y>Math.min(...map.map(m=>m.y))).length,history.entries.find(e=>e.currently_displayed===false).text,center.tooltips[0].title,request.receipt.status==='applied'&&request.receipt.state_id<s.state_id?'True':'False',deck.filter(c=>!offhand.has(c.uuid)).length].map(String);
  const {readFile}=await import('node:fs/promises');const xml=await readFile('evaluation/evaluation.xml','utf8');const expected=[...xml.matchAll(/<answer>(.*?)<\/answer>/g)].map(m=>m[1]);assert.equal(expected.length,10);assert.deepEqual(answers,expected);
 }finally{await client.close();}
});
