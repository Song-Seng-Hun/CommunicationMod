import test from 'node:test';import assert from 'node:assert/strict';import path from 'node:path';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
test('ten frozen read-only evaluation answers are reachable through actual MCP tools',async()=>{
 const client=new Client({name:'frozen-verifier',version:'1'});await client.connect(new StdioClientTransport({command:process.execPath,args:[path.resolve('evaluation/server.mjs')],stderr:'pipe'}));
 try{
  const call=async(name,args={})=>{const r=await client.callTool({name,arguments:args});assert.notEqual(r.isError,true);return r.structuredContent.data;};
  const s=await call('sts_get_state',{wait_ms:1000});assert.equal(s.session_id,'frozen-evaluation-v1');
  const context=(ref,extra={})=>call('sts_get_context',{session_id:s.session_id,state_id:s.state_id,refs:[ref],...extra});
  async function read(ref){let data={},rows=[],text='',format;for(let offset=0;offset!==null;){const page=(await context(ref,{offset})).fragments[0];format=page.format;
   if(format==='value')return page.value;if(format==='text')text+=page.text;else Object.assign(data,page.data);
   for(const item of page.toc??[]){const value=await read(item.ref);if(format==='index')rows.push(value);else data[decodeURIComponent(item.ref.split('/').at(-1))]=value;}
   offset=page.next_offset??null;}return format==='text'?text:format==='index'?rows:data;}
  const deck=await read('deck'),piles=await read('piles'),history=await read('history'),request=await call('sts_get_request',{request_id:'fixture-choice'});
  const mapClient=new Client({name:'map-verifier',version:'1'});let map=[];
  try{await mapClient.connect(new StdioClientTransport({command:process.execPath,args:[path.resolve('evaluation/server.mjs')],env:{...process.env,COMMUNICATIONMOD_EVAL_SCREEN:'MAP'},stderr:'pipe'}));
   const m=(await mapClient.callTool({name:'sts_get_state',arguments:{}})).structuredContent.data;
   const directory=(await mapClient.callTool({name:'sts_get_context',arguments:{session_id:m.session_id,state_id:m.state_id,refs:['map']}})).structuredContent.data.fragments[0];
   for(const entry of directory.toc){map.push((await mapClient.callTool({name:'sts_get_context',arguments:{session_id:m.session_id,state_id:m.state_id,refs:[entry.ref]}})).structuredContent.data.fragments[0].data);}
  }finally{await mapClient.close();}
  const first=s.combat.hand[0],center=deck.find(c=>c.uuid===s.combat.hand[2].uuid),offhand=new Set([...piles.draw_pile,...piles.discard_pile,...piles.exhaust_pile].map(c=>c.uuid));
  const answers=[deck.filter(c=>c.id===first.id).length,center.name,Math.max(...deck.filter(c=>c.id===first.id).map(c=>c.displayed_values.D)),piles.draw_pile_order_visible?'True':'False',deck.filter(c=>c.type==='SKILL'&&piles.discard_pile.some(d=>d.uuid===c.uuid)).length,map.filter(n=>n.symbol==='R'&&n.y>Math.min(...map.map(m=>m.y))).length,history.entries.find(e=>e.currently_displayed===false).text,center.tooltips[0].title,request.receipt.status==='applied'&&request.receipt.state_id<s.state_id?'True':'False',deck.filter(c=>!offhand.has(c.uuid)).length].map(String);
  const {readFile}=await import('node:fs/promises');const xml=await readFile('evaluation/evaluation.xml','utf8');const expected=[...xml.matchAll(/<answer>(.*?)<\/answer>/g)].map(m=>m[1]);assert.equal(expected.length,10);assert.deepEqual(answers,expected);
 }finally{await client.close();}
});
