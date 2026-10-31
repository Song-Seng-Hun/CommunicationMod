import test from 'node:test';
import assert from 'node:assert/strict';
import {GameSession} from '../dist/session.js';
import {conditionalDecision} from '../dist/context.js';

const state=(extra={})=>({type:'state',session_id:'s',state_id:1,ready:true,actions:[{id:'run.room.proceed',parameters:{}}],observation:{game_state:{screen_type:'NONE',current_hp:20}},...extra});
const tick=async(t,ms)=>{t.mock.timers.tick(ms);await Promise.resolve();await Promise.resolve();};
const setup=t=>{t.mock.timers.enable({apis:['Date','setTimeout'],now:10000});const g=new GameSession(()=>{});g.receive(state());t.after(()=>g.close());return g;};
const options=(g,mode='decision',signal)=>{const known=conditionalDecision(g.current(),undefined,mode).view_id;return {signal,changed:s=>conditionalDecision(s,undefined,mode).view_id!==known};};

test('matching view waits across identical heartbeats and returns on same-ID content change',async t=>{
 const g=setup(t),opt=options(g);let done=false;
 const p=g.wait(15000,opt).then(s=>{done=true;return s;});
 await tick(t,1000);g.receive(state());await tick(t,1000);assert.equal(done,false);
 g.receive(state({observation:{game_state:{screen_type:'NONE',current_hp:19}}}));
 assert.equal((await p).observation.game_state.current_hp,19);assert.equal(g.waiters.size,0);
});
test('unchanged deadline is absolute despite heartbeat freshness refresh',async t=>{
 const g=setup(t),opt=options(g);let done=false;
 const p=g.wait(3000,opt).then(s=>{done=true;return s;});
 await tick(t,1000);g.receive(state());await tick(t,1000);g.receive(state());assert.equal(done,false);
 await tick(t,1000);await p;assert.equal(done,true);assert.equal(g.waiters.size,0);
});
test('freshness expires at five seconds without polling and heartbeat rearms only freshness',async t=>{
 const g=setup(t);let done=false;
 const p=g.wait(15000,options(g)).then(s=>{done=true;return s;});
 await tick(t,4000);g.receive(state());await tick(t,4999);assert.equal(done,false);
 await tick(t,1);assert.equal((await p).connection.status,'disconnected_or_stale');assert.equal(g.waiters.size,0);
});
test('legacy unready wait terminates on freshness expiry and removes abort listeners',async t=>{
 const g=setup(t),a=new AbortController();g.receive(state({ready:false}));
 let done=false;const p=g.wait(15000,{signal:a.signal}).then(s=>{done=true;return s;});
 await tick(t,4999);assert.equal(done,false);await tick(t,1);
 assert.equal(done,true,'freshness expiry must terminate even without a known view');
 assert.equal((await p).connection.status,'disconnected_or_stale');assert.equal(g.waiters.size,0);
 const {getEventListeners}=await import('node:events');assert.equal(getEventListeners(a.signal,'abort').length,0);
});
test('known-view wait observes ready and remote pending changes without dispatching',async t=>{
 const g=setup(t);g.receive(state({ready:false}));const ready=g.wait(15000,options(g));
 g.receive(state());assert.equal((await ready).ready,true);
 const pending=g.wait(15000,options(g));g.receive({type:'bridge_ready',pending_request_id:'remote'});
 assert.equal((await pending).connection.pending_request_id,'remote');
 const receipt=g.wait(15000,options(g));g.receive({type:'result',request_id:'remote',status:'applied'});
 assert.equal((await receipt).connection.pending_request_id,null);assert.equal(g.waiters.size,0);
});
test('freshness clock boundary cannot leave a waiter without its expiry timer',async t=>{
 const g=setup(t);let reads=0,done=false;
 t.mock.method(Date,'now',()=>++reads<=5?14999:15000);
 const p=g.wait(15000,{changed:()=>false}).then(s=>{done=true;return s;});
 await tick(t,1);assert.equal(done,true,'clock crossing must resolve or retain a one-ms freshness timer');
 assert.equal((await p).connection.status,'disconnected_or_stale');assert.equal(g.waiters.size,0);
});
test('changed, zero-wait and legacy ready calls remain immediate',async t=>{
 const g=setup(t);assert.equal((await g.wait(1000,{changed:()=>true})).state_id,1);
 assert.equal((await g.wait(0,options(g))).state_id,1);assert.equal((await g.wait(1000)).ready,true);
 g.receive(state({ready:false}));let done=false;const p=g.wait(1000).then(s=>{done=true;return s;});
 await tick(t,1);assert.equal(done,false);g.receive(state());assert.equal((await p).ready,true);
});
test('concurrent waiters cancel independently; close and pending changes wake without replay',async t=>{
 const g=setup(t),a=new AbortController();const opt=options(g);
 const p=g.wait(15000,{...opt,signal:a.signal});const q=g.wait(15000,opt);
 const rejected=assert.rejects(p,/abort/i);a.abort();await rejected;assert.equal(g.waiters.size,1);
 const act=g.act({session_id:'s',state_id:1,action_id:'run.room.proceed',request_id:'r',arguments:{}},1000);
 assert.equal((await q).connection.pending_request_id,'r');assert.equal(g.waiters.size,0);
 const end=g.wait(15000,options(g));g.close();assert.equal((await end).ready,false);await act;
 assert.equal(g.waiters.size,0);assert.equal(g.request('r').retry_allowed,false);
});
test('registration rechecks state, handles early abort and cleans throwing predicates',async t=>{
 const g=setup(t),a=new AbortController();a.abort();await assert.rejects(g.wait(10,{...options(g),signal:a.signal}),/abort/i);
 let calls=0;const p=g.wait(1000,{changed:()=>++calls>=2});assert.equal((await p).ready,true);
 await assert.rejects(g.wait(1000,{changed:()=>{throw Error('predicate');}}),/predicate/);assert.equal(g.waiters.size,0);
});
test('map wait never traverses unrelated cards or guidance and wakes on new session',async t=>{
 const g=setup(t),s=state({observation:{game_state:{screen_type:'MAP',map_plan:{revision:1}}}});
 Object.defineProperty(s.observation.game_state,'deck',{enumerable:true,get(){throw Error('unrelated deck');}});g.receive(s);
 const p=g.wait(15000,options(g,'map_plan'));await tick(t,1);g.receive({...s,session_id:'new'});
 assert.equal((await p).session_id,'new');assert.equal(g.waiters.size,0);
});
