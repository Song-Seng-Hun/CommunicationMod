// Evaluation-only frozen backend. Never connects to a real game; all mutations rejected.
import net from 'node:net';import {spawn} from 'node:child_process';import {mkdtemp,mkdir,writeFile} from 'node:fs/promises';import path from 'node:path';import os from 'node:os';import {randomBytes} from 'node:crypto';import {fileURLToPath} from 'node:url';
import {fixture,receipt} from './fixture.mjs';
const root=await mkdtemp(path.join(os.tmpdir(),'communicationmod-eval-'));
const runtime=path.join(root,'target','fixture'),records=path.join(runtime,'recordings','frozen');await mkdir(records,{recursive:true});
const token=randomBytes(32).toString('base64url'),peers=new Set();
const backend=net.createServer(socket=>{
 peers.add(socket);socket.setEncoding('utf8');let buffer='',auth=false;
 const send=x=>socket.write(JSON.stringify(x)+'\n');
 const heartbeat=setInterval(()=>{if(auth)send(fixture);},500);
 socket.on('close',()=>{clearInterval(heartbeat);peers.delete(socket);});socket.on('error',()=>socket.destroy());
 socket.on('data',data=>{buffer+=data;if(buffer.length>16384){socket.destroy();return;}for(let i;(i=buffer.indexOf('\n'))>=0;){
  let request;try{request=JSON.parse(buffer.slice(0,i));}catch{socket.destroy();return;}buffer=buffer.slice(i+1);
  if(!auth){if(request.token!==token){socket.destroy();return;}auth=true;send({type:'bridge_ready'});send(receipt);send(fixture);}
  else send({type:'error',request_id:request.request_id,code:'read_only_fixture'});
 }});
});
await new Promise(resolve=>backend.listen(0,'127.0.0.1',resolve));
await writeFile(path.join(root,'target','local-test-ready.json'),JSON.stringify({runtime}));
await writeFile(path.join(records,'mcp-bridge.json'),JSON.stringify({protocol:1,port:backend.address().port,token}));
const child=spawn(process.execPath,[fileURLToPath(new URL('../dist/index.js',import.meta.url))],{stdio:'inherit',env:{...process.env,COMMUNICATIONMOD_WORKSPACE:root},windowsHide:true});
function stop(){for(const peer of peers)peer.destroy();backend.close();child.kill();}
process.on('SIGINT',stop);process.on('SIGTERM',stop);child.on('exit',code=>{stop();process.exitCode=code ?? 0;});
