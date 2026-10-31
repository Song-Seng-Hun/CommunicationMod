import test from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import {mkdtemp,mkdir,writeFile} from 'node:fs/promises';
import os from 'node:os';import path from 'node:path';import {randomBytes} from 'node:crypto';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {decode} from '@toon-format/toon';
import {restoreDelta} from '../dist/context-reuse.js';
import type {Wire} from '../evaluation/wire.ts';
const read=(r:Wire)=>{assert.ok(!r.isError,JSON.stringify(r));const s=r.content[0].text;return s.startsWith('TOON:')?decode(s.slice(s.indexOf('\n')+1)):JSON.parse(s);};
const potionSlots=(consumed:Wire)=>[
 {slot:0,id:consumed?'Potion Slot':'Fire Potion',name:consumed?'Potion Slot':'Fire Potion',can_use:!consumed,can_discard:!consumed,requires_target:!consumed},
 {slot:1,id:'Block Potion',name:'Block Potion',can_use:true,can_discard:true,requires_target:false},
 {slot:2,id:'Potion Slot',name:'Potion Slot',can_use:false,can_discard:false,requires_target:false}
];
const assertPotions=(view:Wire,consumed=false)=>{
 const inventory=view.player.potion_inventory;
 assert.equal(inventory.capacity,3);assert.equal(inventory.held_count,consumed?1:2);assert.equal(inventory.empty_slots,consumed?2:1);assert.equal(inventory.full,false);
 assert.deepEqual(inventory.slots.map(({slot,id}:Wire)=>({slot,id})),potionSlots(consumed).map(({slot,id})=>({slot,id})), 'native potion identities and slot indices must survive projection');
};

// Regression: packaging current pinned APIs must retain installed full-baseline reuse,
// without sending its presentation arguments to the native bridge or reviving stale pins.
test('real stdio retains optional reuse, typed delta, context pages and native argument boundaries',async()=>{
 const root=await mkdtemp(path.join(os.tmpdir(),'comm-reuse-')),runtime=path.join(root,'target','test'),records=path.join(runtime,'recordings','r');await mkdir(records,{recursive:true});
 const token=randomBytes(32).toString('base64url'),peers=new Set(),commands:Wire[]=[];
 const state=(id:Wire,hp=63)=>({type:'state',session_id:'s',state_id:id,ready:true,actions:Array.from({length:10},(_,i)=>({id:'run.event.'+i,label:'Option '+i+': inspect the visible reward and leave the room.',parameters:{}})),observation:{game_state:{screen_type:'EVENT',current_hp:hp,potions:potionSlots(id===3),screen_state:{body_text:'The public event text is fully known. '.repeat(40),options:[{text:'Leave'}]}}}});
 const backend:Wire=net.createServer(s=>{peers.add(s);s.on('close',()=>peers.delete(s));s.setEncoding('utf8');let buf='',auth=false;s.on('data',chunk=>{buf+=chunk;for(let i;(i=buf.indexOf('\n'))>=0;){const m=JSON.parse(buf.slice(0,i));buf=buf.slice(i+1);if(!auth){assert.equal(m.token,token);auth=true;s.write(JSON.stringify({type:'bridge_ready'})+'\n'+JSON.stringify(state(1))+'\n');}else{commands.push(m);s.write(JSON.stringify({type:'result',request_id:m.request_id,status:'applied'})+'\n'+JSON.stringify(state(2,62))+'\n');}}});});
 await new Promise(r=>backend.listen(0,'127.0.0.1',r));await writeFile(path.join(root,'target','local-test-ready.json'),JSON.stringify({runtime}));await writeFile(path.join(records,'mcp-bridge.json'),JSON.stringify({protocol:1,port:backend.address().port,token}));
 const client=new Client({name:'reuse-regression',version:'1'}),transport=new StdioClientTransport({command:process.execPath,args:[path.resolve('dist/index.js')],env:{...process.env,COMMUNICATIONMOD_WORKSPACE:root},stderr:'pipe'});
 const call=async(name:Wire,args={})=>read(await client.callTool({name,arguments:args}));
 try{
  await client.connect(transport);
  const base=await call('sts_get_state',{reuse:true});assert.equal(typeof base.view_id,'string');assert.equal(base.session_id,'s');assert.equal(base.ready,true);
  assertPotions(base);
  const repeated=await call('sts_get_state',{reuse:true,known_view:base.view_id});assert.equal(repeated.unchanged,true);assert.equal(repeated.view_id,base.view_id);
  const full=await call('sts_get_state',{reuse:true});assert.equal(full.unchanged,undefined);assert.deepEqual(full,base);
  const args={refs:['screen/body_text'],reuse:true,response_format:'json'},page=await call('sts_get_context',args),f=page.fragments[0];assert.ok(f.content_id);
  const cached=await call('sts_get_context',{...args,known:{[f.ref]:f.content_id}});assert.equal(cached.fragments[0].unchanged,true);
  const act=await call('sts_act',{action_id:base.actions[0].id,reuse:true,known_view:base.view_id});assert.equal(commands.length,1);assert.equal(commands[0].reuse,undefined);assert.equal(commands[0].known_view,undefined);assert.equal(commands[0].action_id,'run.event.0');
  assert.ok(act.state.delta,'small state change should use retained full base');const rebuilt:Wire=restoreDelta(base,act.state);assert.equal(rebuilt.player.current_hp,62);assert.equal(rebuilt.state_id,2);
  assertPotions(rebuilt);assert.deepEqual(rebuilt.player.potion_inventory,base.player.potion_inventory,'backend applied receipt does not imply a potion was consumed');
  const again=await call('sts_get_state',{reuse:true,known_view:act.state.view_id});assert.equal(again.delta,undefined,'delta result is not an emitted full baseline');assert.equal(again.player.current_hp,62);
  const ordinary=await call('sts_get_state',{refresh:true});assert.equal(ordinary.session_id,undefined);assert.equal(ordinary.view_id,undefined);
  for(const s of peers)(s as Wire).write(JSON.stringify(state(3,61))+'\n');await new Promise(r=>setTimeout(r,20));
  assert.equal((await client.callTool({name:'sts_get_context',arguments:{...args,known:{[f.ref]:f.content_id}}})).isError,true,'reuse cannot bypass stale pinned state');
  const refreshed=await call('sts_get_state',{refresh:true});assertPotions(refreshed,true);
 }finally{await client.close();for(const s of peers)(s as Wire).destroy();await new Promise(r=>backend.close(r));}
});
