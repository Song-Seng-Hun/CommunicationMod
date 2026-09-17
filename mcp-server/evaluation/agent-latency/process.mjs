import {spawn} from 'node:child_process';
export function remainingTimeout(deadline,now){
 if(!Number.isFinite(deadline)||!Number.isFinite(now)||deadline<=now)throw new Error('Evaluation deadline exhausted before spawn');
 return Math.min(90000,deadline-now);
}
// Only the process tree created for this evaluation. Never search for game processes.
export async function stopTree(child,{spawnKiller=()=>spawn('taskkill.exe',['/PID',String(child.pid),'/T','/F'],{stdio:'ignore',windowsHide:true}),cleanupMs=5000}={}){
 const detach=()=>{child.stdin?.destroy();child.stdout?.destroy();child.stderr?.destroy();child.unref();};
 // Parent exit is not proof that descendants exited; pipes may still be held.
 if(child.exitCode!==null||child.signalCode!==null){detach();return false;}
 if(!child.pid)return false;
 const closed=new Promise(resolve=>child.once('close',()=>resolve(true)));
 let killed=Promise.resolve(true),killer;
 if(process.platform==='win32'){
  killer=spawnKiller();
  killed=new Promise(resolve=>{
   killer.on('error',()=>{child.kill();resolve(false);});
   killer.on('exit',code=>{if(code!==0)child.kill();resolve(code===0);});
  });
 }else child.kill('SIGKILL');
 let timer;const result=await Promise.race([Promise.all([closed,killed]).then(xs=>xs.every(Boolean)),new Promise(resolve=>{timer=setTimeout(()=>resolve(false),Math.min(5000,cleanupMs));})]);clearTimeout(timer);
 if(!result){child.kill();detach();if(killer){killer.kill();killer.stdin?.destroy();killer.stdout?.destroy();killer.stderr?.destroy();killer.unref();}}
 return result;
}
