import net from 'node:net';import path from 'node:path';import {readFile,readdir,stat,realpath} from 'node:fs/promises';
import {GameSession} from './session.js';import {obj,type Obj} from './view.js';
export class Connection {
 private socket?:net.Socket;private connecting?:Promise<void>;
 readonly game=new GameSession(command=>{if(!this.socket || this.socket.destroyed)throw new Error('Disconnected');this.socket.write(JSON.stringify(command)+'\n');});
 constructor(private workspace:string){}
 async ensure():Promise<void>{
  if(this.socket && !this.socket.destroyed)return;
  if(!this.connecting)this.connecting=this.connect().finally(()=>{this.connecting=undefined;});return this.connecting;
 }
 private async connect():Promise<void>{
  const root=await realpath(path.join(this.workspace,'target'));
  const ready=obj(JSON.parse(await readFile(path.join(root,'local-test-ready.json'),'utf8')));
  const runtime=await realpath(String(ready.runtime));const relative=path.relative(root,runtime);
  if(relative.startsWith('..')||path.isAbsolute(relative)||!relative)throw new Error('Runtime outside project target.');
  const recordings=path.join(runtime,'recordings');
  const dirs=await Promise.all((await readdir(recordings,{withFileTypes:true})).filter(d=>d.isDirectory()).map(async d=>({path:path.join(recordings,d.name),time:(await stat(path.join(recordings,d.name))).mtimeMs})));
  dirs.sort((a,b)=>b.time-a.time);if(!dirs[0])throw new Error('Start the test game with -McpControl first.');
  const endpoint=obj(JSON.parse(await readFile(path.join(dirs[0].path,'mcp-bridge.json'),'utf8')));
  if(endpoint.protocol!==1 || !Number.isInteger(endpoint.port) || Number(endpoint.port)<1 || Number(endpoint.port)>65535 || typeof endpoint.token!=='string' || !/^[A-Za-z0-9_-]{43}$/.test(endpoint.token))throw new Error('Invalid MCP bridge descriptor.');
  await new Promise<void>((resolve,reject)=>{
   const socket=net.createConnection({host:'127.0.0.1',port:Number(endpoint.port)});this.socket=socket;socket.setNoDelay(true);socket.setEncoding('utf8');let buffer='',authenticated=false;
   const timer=setTimeout(()=>{socket.destroy();reject(new Error('Bridge connection timed out. Start game with -McpControl.'));},3000);
   socket.on('connect',()=>socket.write(JSON.stringify({token:endpoint.token})+'\n'));
   socket.on('data',(chunk:string)=>{
    buffer+=chunk;if(buffer.length>2*1024*1024){socket.destroy();return;}
    for(let i;(i=buffer.indexOf('\n'))>=0;){const line=buffer.slice(0,i);buffer=buffer.slice(i+1);try{
     const message=obj(JSON.parse(line));if(message.type==='bridge_ready')authenticated=true;
     if(authenticated){this.game.receive(message);if(message.type==='state'){clearTimeout(timer);resolve();}}
    }catch{socket.destroy();}}
   });
   socket.on('error',()=>{clearTimeout(timer);reject(new Error('Bridge unavailable. Start the verified test game with -McpControl.'));});
   socket.on('close',()=>{clearTimeout(timer);if(this.socket===socket)this.socket=undefined;this.game.close();reject(new Error('Bridge authentication/connection closed.'));});
  });
 }
 close():void{this.socket?.destroy();this.game.close();}
}
