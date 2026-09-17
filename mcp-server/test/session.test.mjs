import test from 'node:test';import assert from 'node:assert/strict';
const state=(id,ready=true,actions=ready?[{id:'run.x',parameters:{}}]:[])=>({type:'state',session_id:'s',state_id:id,ready,actions,observation:{game_state:{screen_type:'MAP'}}});
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
test('public pinned path keeps stale protection and resolves short action aliases',async()=>{
 const {GameSession}=await import('../dist/session.js');const sent=[];const g=new GameSession(x=>sent.push(x));g.receive(state(1));
 assert.throws(()=>g.assertPresented(),/presented state/i);g.present(g.current());
 const pending=g.actPresented({action_id:'a0',request_id:'pin-1',arguments:{}},1000);assert.equal(sent[0].session_id,'s');assert.equal(sent[0].state_id,1);assert.equal(sent[0].action_id,'run.x');
 g.receive({type:'result',request_id:'pin-1',status:'applied'});g.receive(state(2));const answer=await pending;assert.equal(answer.outcome,'applied');
 assert.throws(()=>g.assertPresented(),/stale/i,'old pin must not silently follow a newer state');
 g.present(g.current());assert.equal(g.assertPresented().state_id,2);assert.throws(()=>g.actPresented({action_id:'a9',request_id:'bad-alias',arguments:{}},100),/not offered/i);g.close();
});
test('public action arguments omit pinned constants and the session restores them before dispatch',async()=>{
 const {GameSession}=await import('../dist/session.js');const sent=[],parameters={type:'object',additionalProperties:false,properties:{map_id:{type:'string',const:'map-9'},revision:{type:'integer',const:4},nodes:{type:'array'}},required:['map_id','revision','nodes']};
 const g=new GameSession(x=>sent.push(x));g.receive(state(1,true,[{id:'run.map.plan',parameters}]));g.present(g.current());
 const pending=g.actPresented({action_id:'a0',request_id:'map',arguments:{nodes:['1,2','2,3']}},1000);
 assert.deepEqual(sent[0].arguments,{nodes:['1,2','2,3'],map_id:'map-9',revision:4});assert.equal(sent[0].action_id,'run.map.plan');
 g.receive({type:'result',request_id:'map',status:'applied'});g.receive(state(2));await pending;g.close();
 const bad=new GameSession(()=>{});bad.receive(state(1,true,[{id:'run.map.plan',parameters}]));bad.present(bad.current());
 assert.throws(()=>bad.actPresented({action_id:'a0',request_id:'bad',arguments:{map_id:'stale',nodes:[]}},100),/Fixed action parameter/);bad.close();
});
