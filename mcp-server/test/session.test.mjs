import test from 'node:test';import assert from 'node:assert/strict';
const state=(id,ready=true)=>({type:'state',session_id:'s',state_id:id,ready,actions:ready?[{id:'run.x',parameters:{}}]:[],observation:{game_state:{screen_type:'MAP'}}});
test('act returns receipt AND later stable state; stale/parallel/duplicates do not dispatch',async()=>{
 const {GameSession}=await import('../dist/session.js').catch(()=>{throw new Error('Missing persistent action session')});
 const sent=[];const g=new GameSession(x=>sent.push(x));g.receive(state(1));
 assert.throws(()=>g.act({session_id:'s',state_id:0,action_id:'run.x',request_id:'old',arguments:{}},100),/stale/i);
 const req={session_id:'s',state_id:1,action_id:'run.x',request_id:'r',arguments:{}};
 const promise=g.act(req,1000);assert.equal(sent.length,1);
 assert.throws(()=>g.act({...req,request_id:'other'},100),/busy/i);
 g.receive({type:'result',request_id:'r',status:'applied'});g.receive(state(2,false));
 let done=false;promise.then(()=>done=true);await new Promise(r=>setTimeout(r,5));assert.equal(done,false);
 g.receive(state(3));const answer=await promise;assert.equal(answer.state.state_id,3);assert.equal(answer.outcome,'applied');
 assert.throws(()=>g.act(req,100),/duplicate/i);assert.equal(sent.length,1);g.close();
});
test('timeout is unknown, no replay; disconnect strips ready actions',async()=>{
 const {GameSession}=await import('../dist/session.js');let count=0;const g=new GameSession(()=>count++);g.receive(state(1));
 const answer=await g.act({session_id:'s',state_id:1,action_id:'run.x',request_id:'r',arguments:{}},10);
 assert.equal(answer.outcome,'unknown');assert.equal(count,1);assert.throws(()=>g.act({session_id:'s',state_id:1,action_id:'run.x',request_id:'r2',arguments:{}},10),/busy/i);
 g.close();assert.equal(g.current().ready,false);assert.deepEqual(g.current().actions,[]);
});
