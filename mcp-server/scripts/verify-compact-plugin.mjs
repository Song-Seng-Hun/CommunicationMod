// Installed-bundle verification using a synthetic loopback backend. No real game calls.
import assert from 'node:assert/strict';
import {readFile,readdir,mkdtemp,mkdir,writeFile} from 'node:fs/promises';
import {randomBytes} from 'node:crypto';
import {tmpdir} from 'node:os';
import path from 'node:path';
import net from 'node:net';
import {pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {performanceState} from '../evaluation/performance-fixtures.mjs';

assert.ok(process.argv[2],'Pass the exact installed cache directory.');
const cache=path.resolve(process.argv[2]);
const files=(await readdir(new URL('../dist/',import.meta.url))).filter(file=>file.endsWith('.js'));
for(const file of [...files,'package.json','package-lock.json']){
 const original=new URL(file.endsWith('.js')?`../dist/${file}`:`../${file}`,import.meta.url);
 assert.deepEqual(await readFile(path.join(cache,'server',file)),await readFile(original),`Stale installed ${file}`);
}
const requireInstalled=createRequire(pathToFileURL(path.join(cache,'server/package.json')));
const {decode}=await import(pathToFileURL(requireInstalled.resolve('@toon-format/toon')).href);
const root=await mkdtemp(path.join(tmpdir(),'comm-installed-compact-'));
const runtime=path.join(root,'target','synthetic'),records=path.join(runtime,'recordings','one');
await mkdir(records,{recursive:true});
const token=randomBytes(32).toString('base64url'),peers=new Set();let commands=0;
const backend=net.createServer(socket=>{
 peers.add(socket);socket.on('close',()=>peers.delete(socket));socket.setEncoding('utf8');
 let buffer='',authenticated=false;
 socket.on('data',chunk=>{
  buffer+=chunk;for(let end;(end=buffer.indexOf('\n'))>=0;){
   const message=JSON.parse(buffer.slice(0,end));buffer=buffer.slice(end+1);
   if(!authenticated){
    assert.equal(message.token,token);authenticated=true;
    socket.write(JSON.stringify({type:'bridge_ready'})+'\n');
    socket.write(JSON.stringify({type:'state',...performanceState(40)})+'\n');
   }else{commands++;socket.destroy();}
  }
 });
});
await new Promise(resolve=>backend.listen(0,'127.0.0.1',resolve));
const client=new Client({name:'installed-compact-verifier',version:'1'});
try{
 await writeFile(path.join(root,'target','local-test-ready.json'),JSON.stringify({runtime}));
 await writeFile(path.join(records,'mcp-bridge.json'),JSON.stringify({protocol:1,port:backend.address().port,token}));
 const config=path.join(root,'plugin.json');await writeFile(config,JSON.stringify({workspace:root}));
 await client.connect(new StdioClientTransport({command:process.execPath,args:[path.join(cache,'scripts','start.mjs')],env:{...process.env,COMMUNICATIONMOD_CONFIG:config},stderr:'pipe'}));
 const catalog=(await client.listTools()).tools;assert.equal(catalog.length,6);
 const schema=catalog.find(tool=>tool.name==='sts_get_context').inputSchema;
 assert.deepEqual(schema.properties.response_format.enum,['json','compact']);assert.equal(schema.properties.response_format.default,'json');
 const state=await client.callTool({name:'sts_get_state',arguments:{wait_ms:1000}});assert.ok(!state.isError);
 const args={session_id:state.structuredContent.data.session_id,state_id:state.structuredContent.data.state_id,refs:['collection/cards'],limit:20};
 const plain=await client.callTool({name:'sts_get_context',arguments:args});assert.ok(!plain.isError);
 const compact=await client.callTool({name:'sts_get_context',arguments:{...args,response_format:'compact'}});assert.ok(!compact.isError);
 assert.equal(compact.structuredContent,undefined);const text=compact.content[0].text;assert.match(text,/^TOON:/);
 assert.deepEqual(decode(text.slice(text.indexOf('\n')+1),{strict:true}),plain.structuredContent.data);
 const recovered=await client.callTool({name:'sts_get_context',arguments:{...args,response_format:'json'}});assert.deepEqual(recovered,plain);
 const stale=await client.callTool({name:'sts_get_context',arguments:{...args,state_id:41,response_format:'compact'}});assert.equal(stale.isError,true);
 assert.equal(commands,0);
 console.log(JSON.stringify({phase:'installed_compact_verified',server_files_equal:files.length,tools:catalog.length,compact_toon_roundtrip:true,json_recovery:true,stale_rejected:true,real_game_attached:false,gameplay_actions:0}));
}finally{await client.close();for(const socket of peers)socket.destroy();await new Promise(resolve=>backend.close(resolve));}
