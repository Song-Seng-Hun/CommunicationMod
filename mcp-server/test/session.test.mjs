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
 assert.throws(()=>g.actPresented({action_id:'a0',request_id:'missing',arguments:{}},100),/Missing required action arguments: nodes/);assert.equal(sent.length,0,'missing public args must fail before bridge dispatch');
 const pending=g.actPresented({action_id:'a0',request_id:'map',arguments:{nodes:['1,2','2,3']}},1000);
 assert.deepEqual(sent[0].arguments,{nodes:['1,2','2,3'],map_id:'map-9',revision:4});assert.equal(sent[0].action_id,'run.map.plan');
 g.receive({type:'result',request_id:'map',status:'applied'});g.receive(state(2));await pending;g.close();
 const bad=new GameSession(()=>{});bad.receive(state(1,true,[{id:'run.map.plan',parameters}]));bad.present(bad.current());
 assert.throws(()=>bad.actPresented({action_id:'a0',request_id:'bad',arguments:{map_id:'stale',nodes:[]}},100),/Unexpected action argument: map_id/);bad.close();
});

test('replacement session retires an unknown local lock without permitting old replay',async()=>{
 const {GameSession}=await import('../dist/session.js');const sent=[],g=new GameSession(x=>sent.push(x));g.receive(state(1));g.present(g.current());
 const old=await g.actPresented({action_id:'a0',request_id:'old-unknown',arguments:{}},10);assert.equal(old.outcome,'unknown');g.close();
 g.receive({type:'bridge_ready',pending_request_id:null});g.receive({...state(1),session_id:'replacement'});
 assert.deepEqual(g.request('old-unknown'),{request_id:'old-unknown',receipt:null,pending:false,retry_allowed:false});
 assert.throws(()=>g.assertPresented(),/presented state|stale/i);
 assert.throws(()=>g.act({session_id:'replacement',state_id:1,action_id:'run.x',request_id:'old-unknown',arguments:{}},100),/duplicate/i);
 g.present(g.current());const next=g.actPresented({action_id:'a0',request_id:'new-choice',arguments:{}},1000);
 assert.equal(sent.length,2);assert.equal(sent[1].session_id,'replacement');
 g.receive({type:'result',request_id:'new-choice',status:'applied'});g.receive({...state(2),session_id:'replacement'});assert.equal((await next).outcome,'applied');g.close();
});

test('same-session reconnect stays locked until its receipt and subsequent ready state',async()=>{
 const {GameSession}=await import('../dist/session.js');const sent=[],g=new GameSession(x=>sent.push(x));g.receive(state(1));
 const req={session_id:'s',state_id:1,action_id:'run.x',request_id:'same-old',arguments:{}};
 await g.act(req,10);g.close();g.receive({type:'bridge_ready',pending_request_id:null});g.receive(state(2));
 assert.equal(g.request(req.request_id).pending,true);assert.throws(()=>g.act({...req,state_id:2,request_id:'same-new'},100),/busy/i);
 g.receive({type:'result',request_id:req.request_id,status:'applied'});assert.equal(g.request(req.request_id).pending,true);
 g.receive(state(3));assert.equal(g.request(req.request_id).pending,false);assert.equal(sent.length,1);g.close();
});

test('invalid replacement identity cannot release an unknown action lock',async()=>{
 const {GameSession}=await import('../dist/session.js');const g=new GameSession(()=>{});g.receive(state(1));
 await g.act({session_id:'s',state_id:1,action_id:'run.x',request_id:'invalid-old',arguments:{}},10);g.close();g.receive({type:'bridge_ready',pending_request_id:null});
 for(const identity of [{session_id:undefined,state_id:1},{session_id:'',state_id:1},{session_id:7,state_id:1},{session_id:'new',state_id:undefined},{session_id:'new',state_id:-1},{session_id:'new',state_id:1.5},{session_id:'new',state_id:'1'},{session_id:'new',state_id:Number.MAX_SAFE_INTEGER+1}]){
  g.receive({...state(1),...identity});assert.equal(g.request('invalid-old').pending,true,JSON.stringify(identity));
 }
 g.receive({...state(1),session_id:'valid-new'});assert.equal(g.request('invalid-old').pending,false);g.close();
});

test('replacement recovery preserves remote pending and an applied old receipt without completing it',async()=>{
 const {GameSession}=await import('../dist/session.js');const sent=[],g=new GameSession(x=>sent.push(x));g.receive(state(1));
 const waiting=g.act({session_id:'s',state_id:1,action_id:'run.x',request_id:'old-applied',arguments:{}},1000);
 g.receive({type:'result',request_id:'old-applied',status:'applied'});
 g.receive({type:'bridge_ready',pending_request_id:'remote-new'});g.receive({...state(1),session_id:'new'});
 const answer=await waiting;assert.equal(answer.outcome,'applied_waiting','new session must not complete the old action');
 assert.equal(g.request('old-applied').pending,false);assert.equal(g.request('old-applied').receipt.status,'applied');assert.equal(g.request('old-applied').retry_allowed,false);
 assert.equal(g.current().connection.pending_request_id,'remote-new');assert.throws(()=>g.act({session_id:'new',state_id:1,action_id:'run.x',request_id:'new-choice',arguments:{}},100),/busy/i);
 assert.throws(()=>g.act({session_id:'new',state_id:1,action_id:'run.x',request_id:'old-applied',arguments:{}},100),/duplicate/i);
 g.receive({type:'result',request_id:'remote-new',status:'applied'});assert.equal(g.current().connection.pending_request_id,null);assert.equal(sent.length,1);g.close();
});
