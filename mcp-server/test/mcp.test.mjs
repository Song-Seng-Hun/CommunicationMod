import test from 'node:test';import assert from 'node:assert/strict';import net from 'node:net';import {mkdtemp,mkdir,writeFile,readFile,readdir} from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import {randomBytes} from 'node:crypto';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {decode} from '@toon-format/toon';
test('real MCP stdio tool discovery, focused state, action+next state and context pagination',async()=>{
 const root=await mkdtemp(path.join(os.tmpdir(),'comm-mcp-sdk-'));const runtime=path.join(root,'target','test');const records=path.join(runtime,'recordings','r');await mkdir(records,{recursive:true});
 const token=randomBytes(32).toString('base64url');let dispatches=0;const peers=new Set();
 const state=id=>({type:'state',session_id:'s',state_id:id,ready:true,runtime:{language:'KOR'},actions:[{id:'run.event.0',label:'떠난다',parameters:{}}],observation:{game_state:{screen_type:'EVENT',current_hp:63,deck:[{name:'타격'},{name:'방어'}],mechanics:{collection:{count:135,cards_complete:true,order_visible:false,cards:Array.from({length:135},(_,i)=>({id:`card-${i}`}))}},screen_state:{body_text:'이벤트 본문 전체',options:[{text:'떠난다'}]}}}});
 const backend=net.createServer(socket=>{peers.add(socket);socket.on('close',()=>peers.delete(socket));socket.setEncoding('utf8');let buffer='',auth=false;socket.on('data',data=>{buffer+=data;for(let i;(i=buffer.indexOf('\n'))>=0;){const m=JSON.parse(buffer.slice(0,i));buffer=buffer.slice(i+1);if(!auth){assert.equal(m.token,token);auth=true;socket.write(JSON.stringify({type:'bridge_ready'})+'\n');setTimeout(()=>socket.write(JSON.stringify({type:'result',request_id:'previous',status:'applied'})+'\n'),20);setTimeout(()=>socket.write(JSON.stringify(state(1))+'\n'),60);}else{dispatches++;socket.write(JSON.stringify({type:'result',request_id:m.request_id,status:'applied'})+'\n');socket.write(JSON.stringify({...state(2),ready:false,actions:[]})+'\n');setTimeout(()=>socket.write(JSON.stringify(state(3))+'\n'),15);}}});});
 await new Promise(r=>backend.listen(0,'127.0.0.1',r));await writeFile(path.join(root,'target','local-test-ready.json'),JSON.stringify({runtime}));await writeFile(path.join(records,'mcp-bridge.json'),JSON.stringify({protocol:1,port:backend.address().port,token}));
 const client=new Client({name:'test-client',version:'1'});const transport=new StdioClientTransport({command:process.execPath,args:[path.resolve('dist/index.js')],env:{...process.env,COMMUNICATIONMOD_WORKSPACE:root,COMMUNICATIONMOD_METRICS:'1'},stderr:'pipe'});
 try{await client.connect(transport);const tools=(await client.listTools()).tools;assert.equal(tools.length,6);assert.ok(tools.some(t=>t.name==='sts_start_game'));assert.ok(tools.some(t=>t.name==='sts_game_status'));
  assert.ok(tools.every(t=>t.outputSchema===undefined),'Do not repeat permissive record-of-unknown output schemas in every tool');
  const get=await client.callTool({name:'sts_get_state',arguments:{wait_ms:1000}});assert.equal(get.structuredContent.data.screen_state.body_text,'이벤트 본문 전체');assert.equal(get.structuredContent.data.deck,undefined);
  assert.ok(get.structuredContent.data.toc.some(x=>x.ref==='screen'));assert.ok(!get.structuredContent.data.toc.some(x=>x.ref==='map'));
  const same=await client.callTool({name:'sts_get_state',arguments:{known_view:get.structuredContent.data.view_id}});assert.equal(same.structuredContent.data.unchanged,true);assert.equal(same.structuredContent.data.toc,undefined);
  const waitStarted=performance.now();
  const waited=await client.callTool({name:'sts_get_state',arguments:{known_view:get.structuredContent.data.view_id,wait_ms:80}});
  assert.ok(performance.now()-waitStarted>=65,'known_view must wait, not busy-poll');assert.equal(waited.structuredContent.data.unchanged,true);
  const changedState=state(1);changedState.observation.game_state.current_hp=64;
  const publish=message=>{for(const p of peers)p.write(JSON.stringify(message)+'\n');};
  const change=setTimeout(()=>publish(changedState),30);
  try{const next=await client.callTool({name:'sts_get_state',arguments:{known_view:get.structuredContent.data.view_id,wait_ms:1000}});assert.equal(next.structuredContent.data.player.current_hp,64);}finally{clearTimeout(change);}
  publish(state(1));
  const cancelState=await client.callTool({name:'sts_get_state',arguments:{wait_ms:0}});
  const controller=new AbortController();
  const cancelled=client.callTool({name:'sts_get_state',arguments:{known_view:cancelState.structuredContent.data.view_id,wait_ms:15000}},undefined,{signal:controller.signal});
  const rejected=assert.rejects(cancelled);const cancelTimer=setTimeout(()=>controller.abort('test cancellation'),80);
  try{await rejected;}finally{clearTimeout(cancelTimer);}
  const exampleRef=get.structuredContent.data.guidance.toc[0].ref;
  const example=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:[exampleRef]}});
  assert.equal(example.structuredContent.data.fragments[0].format,'example');assert.equal(dispatches,0);
  const partialExample=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:[exampleRef+'/calls']}});assert.equal(partialExample.isError,true);
  const wrongSessionExample=await client.callTool({name:'sts_get_context',arguments:{session_id:'other-session',state_id:1,refs:[exampleRef]}});assert.equal(wrongSessionExample.isError,true);
  const small=await client.callTool({name:'sts_get_state',arguments:{view:'map_plan'}});assert.equal(small.structuredContent.data.map_plan.status,'unavailable');assert.equal(small.structuredContent.data.screen_state,undefined);assert.equal(small.structuredContent.data.state_id,1);
  const plan=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['map_plan']}});assert.equal(plan.isError,true);
  const full=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['full']}});assert.equal(full.isError,true);
  const mechanics=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['mechanics']}});assert.equal(mechanics.isError,undefined);assert.ok(mechanics.structuredContent.data.fragments[0].toc.some(x=>x.ref==='mechanics/collection'));
  const tail=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards'],offset:130,limit:30}});
  assert.equal(tail.isError,undefined);assert.equal(tail.structuredContent.data.fragments[0].toc[4].title,'card-134');assert.equal(tail.structuredContent.data.fragments[0].next_offset,null);assert.equal(dispatches,0);
  const batch=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards/134','deck/0']}});assert.equal(batch.structuredContent.data.fragments[0].data.id,'card-134');assert.equal(batch.structuredContent.data.fragments[1].data.name,'타격');
  const compact=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards'],limit:20,response_format:'compact'}});
  assert.equal(compact.isError,undefined,'Explicit compact format must be accepted');
  assert.equal(compact.structuredContent,undefined,'Compact output must not duplicate its payload');
  assert.match(compact.content[0].text,/TOON/);
  const restore=result=>{const text=result.content[0].text;return text.startsWith('TOON:')?decode(text.slice(text.indexOf('\n')+1)):JSON.parse(text);};
  const defaultPage=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards'],limit:20}});
  assert.deepEqual(restore(compact),defaultPage.structuredContent.data);
  const recovered=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards'],limit:20,response_format:'json'}});
  assert.deepEqual(recovered,defaultPage,'JSON recovery must need no reconnect');
  const compactBatch=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards/134','deck/0'],response_format:'compact'}});
  assert.deepEqual(restore(compactBatch),batch.structuredContent.data);assert.equal(compactBatch.structuredContent,undefined);
  const compactTail=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards'],offset:130,limit:30,response_format:'compact'}});
  assert.deepEqual(restore(compactTail),tail.structuredContent.data);
  const invalid=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards'],response_format:'tsv'}});assert.equal(invalid.isError,true);
  const formatSchema=tools.find(t=>t.name==='sts_get_context').inputSchema;
  assert.deepEqual(formatSchema.properties.response_format.enum,['json','compact']);assert.equal(formatSchema.properties.response_format.default,'json');
  assert.ok(!formatSchema.required.includes('response_format'));assert.equal(dispatches,0);
  const result=await client.callTool({name:'sts_act',arguments:{session_id:'s',state_id:1,action_id:'run.event.0',request_id:'one'}});assert.equal(result.structuredContent.data.state.state_id,3);assert.equal(result.structuredContent.data.outcome,'applied');
  const stale=await client.callTool({name:'sts_act',arguments:{session_id:'s',state_id:1,action_id:'run.event.0'}});assert.equal(stale.isError,true);assert.equal(dispatches,1);
  const stalePage=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards'],offset:130}});assert.equal(stalePage.isError,true);assert.equal(dispatches,1);
  const staleExample=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:[exampleRef]}});assert.equal(staleExample.isError,true);assert.equal(dispatches,1);
  const compactStale=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:1,refs:['collection/cards'],response_format:'compact'}});
  assert.deepEqual(compactStale,stalePage,'Format must not bypass stale-state checks or rewrite errors');assert.equal(dispatches,1);
  const deck=await client.callTool({name:'sts_get_context',arguments:{session_id:'s',state_id:3,refs:['deck'],limit:1}});assert.equal(deck.structuredContent.data.fragments[0].total,2);assert.equal(deck.structuredContent.data.fragments[0].next_offset,1);
  const metricsDir=path.join(root,'target','agent-efficiency','metrics');let rows=[];
  for(let i=0;i<40;i++){
   try{const files=(await readdir(metricsDir)).filter(x=>x.endsWith('.jsonl'));rows=(await Promise.all(files.map(f=>readFile(path.join(metricsDir,f),'utf8')))).flatMap(text=>text.trim().split('\n').filter(Boolean).map(JSON.parse));}catch{}
   if(rows.some(r=>r.error_class==='cancelled'))break;await new Promise(r=>setTimeout(r,10));
  }
  assert.ok(rows.some(r=>r.tool==='sts_get_state'&&r.wait_ms>=65),'MCP wait must be metered');
  assert.ok(rows.some(r=>r.tool==='sts_get_state'&&r.error_class==='cancelled'),'SDK cancellation reason must be classified without retaining raw text');
  assert.ok(rows.every(r=>r.response_bytes>0));assert.ok(!JSON.stringify(rows).includes('이벤트 본문'));assert.ok(!JSON.stringify(rows).includes(token));
 }finally{await client.close();for(const socket of peers)socket.destroy();await new Promise(r=>backend.close(r));}
});
