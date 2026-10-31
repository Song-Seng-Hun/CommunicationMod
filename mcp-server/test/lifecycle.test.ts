import test from 'node:test';import assert from 'node:assert/strict';
test('Windows native executables are awaited under the filtered MCP environment',async()=>{
 const {windowsEnvironment}=await import('../dist/lifecycle.js');assert.equal(typeof windowsEnvironment,'function','Missing PATHEXT restoration');
 const env=windowsEnvironment({SYSTEMROOT:'C:\\Windows'});assert.equal(env.PATHEXT,'.COM;.EXE;.BAT;.CMD');assert.equal(env.SYSTEMROOT,'C:\\Windows');assert.equal(windowsEnvironment({PATHEXT:'.EXE'}).PATHEXT,'.EXE');
});
test('status is read-only; start returns connected state after one scoped launch',async()=>{
 const {Lifecycle}=await import('../dist/lifecycle.js').catch(()=>{throw new Error('Missing plugin lifecycle controller')});
 const calls=[];let connected=false;
 const link={ensure:async()=>{if(!connected)throw new Error('not ready');},game:{current:()=>({ready:connected,state_id:7,session_id:'test',connection:{status:connected?'connected':'disconnected_or_stale'}})}};
 const lifecycle=new Lifecycle('C:/fixture',link,async operation=>{calls.push(operation);if(operation==='Launch')connected=true;return {phase:connected?'running':'stopped',pid:connected?123:null};});
 assert.equal((await lifecycle.status()).phase,'stopped');assert.deepEqual(calls,['Status']);
 const result=await lifecycle.start(100);assert.equal(result.phase,'connected');assert.equal(result.state.state_id,7);assert.deepEqual(calls,['Status','Launch']);
});
test('startup errors do not relaunch or act; loading reports explicit next step',async()=>{
 const {Lifecycle}=await import('../dist/lifecycle.js');let launches=0;
 const link={ensure:async()=>{throw new Error('not ready');},game:{current:()=>({ready:false,actions:[],connection:{status:'disconnected_or_stale'}})}};
 const lifecycle=new Lifecycle('C:/fixture',link,async()=>{launches++;return {phase:'running',pid:123};});
 const result=await lifecycle.start(0);assert.equal(result.phase,'starting');assert.equal(launches,1);assert.equal(result.retry_launch,false);
 const broken=new Lifecycle('C:/fixture',link,async()=>{throw new Error('hash mismatch');});await assert.rejects(broken.start(0),/hash mismatch/);
});
