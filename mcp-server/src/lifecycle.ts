import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import path from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';
import {focus,obj,type Obj} from './view.js';
import type {Connection} from './connection.js';
const execute=promisify(execFile);
type Operation='Status'|'Launch';
type Runner=(operation:Operation)=>Promise<Obj>;
export function windowsEnvironment(env:NodeJS.ProcessEnv):NodeJS.ProcessEnv {
 return {...env,PATHEXT:env.PATHEXT || '.COM;.EXE;.BAT;.CMD'};
}

/** Lifecycle is separate from game actions: no command dispatch or implicit resume. */
export class Lifecycle {
 private run:Runner;
 constructor(workspace:string,private link:Pick<Connection,'ensure'|'game'>,runner?:Runner){
  this.run=runner ?? (async operation=>{
   if(process.platform!=='win32')throw new Error('The local Downfall plugin launcher currently requires Windows.');
   const script=process.env.COMMUNICATIONMOD_LIFECYCLE_SCRIPT ?? path.join(workspace,'devtools','manage-plugin-game.ps1');
   const {stdout}=await execute(process.env.COMMUNICATIONMOD_POWERSHELL ?? 'pwsh.exe',
    ['-NoProfile','-NonInteractive','-File',script,'-Operation',operation,'-Workspace',workspace],
    {windowsHide:true,timeout:45000,maxBuffer:32768,encoding:'utf8',env:windowsEnvironment(process.env)});
   return obj(JSON.parse(stdout.trim().replace(/^\uFEFF/,'')));
  });
 }
 async status():Promise<Obj>{
  const processState=await this.run('Status');
  const connection=obj(this.link.game.current().connection);
  return {...processState,connection:connection.status ?? 'not_connected',next_step:processState.phase==='stopped'?'sts_start_game':'sts_get_state'};
 }
 async start(waitMs:number):Promise<Obj>{
  const processState=await this.run('Launch');
  if(!['running','reused'].includes(String(processState.phase)))return {...processState,retry_launch:false};
  const deadline=Date.now()+waitMs;
  do {
   try {
    await this.link.ensure();const state=this.link.game.current();
    if(obj(state.connection).status==='connected')return {...processState,phase:'connected',state:focus(state),retry_launch:false};
   }catch { /* startup discovery only; never retry game actions or spawn here */ }
   if(Date.now()>=deadline)break;
   await delay(Math.min(250,deadline-Date.now()));
  }while(Date.now()<deadline);
  return {...processState,phase:'starting',retry_launch:false,next_step:'sts_get_state or sts_game_status; do not start a second game'};
 }
}
