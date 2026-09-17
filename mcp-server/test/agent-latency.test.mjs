import test from 'node:test';
import assert from 'node:assert/strict';
import {schedule,metrics,assess,Budget} from '../evaluation/agent-latency/metrics.mjs';

test('evaluation budget is 24 starts, 20 minutes, 90 seconds per run; missing model fails closed',()=>{
 assert.equal(schedule().length,24);
 assert.deepEqual(schedule().slice(0,4).map(x=>x.variant),['baseline','candidate','candidate','baseline']);
 assert.throws(()=>new Budget({},0),/model/);
 const b=new Budget({model:'actual-model',effort:'high'},0);
 for(let i=0;i<24;i++)assert.equal(b.start(i),90000);
 assert.throws(()=>b.start(24),/budget/);
 assert.throws(()=>new Budget({model:'m',effort:'high'},0).start(1200000),/budget/);
 assert.equal(new Budget({model:'m',effort:'high'},0).start(1199990),10);
});

test('proxy metrics exclude overlap, startup, processing; no pure-inference claim',()=>{
 const events=[{kind:'request',id:1,name:'sts_get_state',at:10},{kind:'request',id:2,name:'sts_get_context',at:11},{kind:'response',id:1,at:20},{kind:'response',id:2,at:30},{kind:'request',id:3,name:'sts_get_context',at:55},{kind:'response',id:3,at:60}];
 const m=metrics(events);
 assert.equal(m.source,'mcp_proxy');assert.deepEqual(m.gaps_ms,[25]);
 assert.deepEqual(m.server_ms,[10,19,5]);assert.equal(m.calls,3);
});
test('metrics distinguish invalid arguments and replay attempts from deliberate stale recovery',()=>{
 const rows=[{kind:'request',id:1,name:'sts_act',args:{request_id:'one'},at:0},{kind:'response',id:1,error:true,data:'Invalid arguments',at:1},
 {kind:'request',id:2,name:'sts_act',args:{request_id:'one'},at:2},{kind:'response',id:2,error:true,data:'Duplicate request ID',at:3},
 {kind:'request',id:3,name:'sts_get_context',args:{},at:4},{kind:'response',id:3,error:true,data:'Stale state',at:5}];
 const m=metrics(rows);assert.equal(m.invalid_arguments,1);assert.equal(m.retries,1);assert.equal(m.stale_rejections,1);
});

test('acceptance fails closed for missing samples, failed correctness, missing usage and per-scenario regression',()=>{
 const rows=schedule().map(x=>({...x,source:'mcp_proxy',gaps_ms:[x.variant==='baseline'?100:70],total_ms:1000,tokens:100,correct:true,safe:true,status:'completed',cleanup_verified:true}));
 assert.equal(assess(rows).accepted,true);
 assert.equal(assess(rows.slice(1)).accepted,false);
 for(const patch of [{cleanup_verified:false},{cleanup_verified:undefined},{tokens:null},{correct:false},{safe:false},{status:'timeout'},{source:'host_event'},{total_ms:1300},{tokens:140}]){
  const copy=structuredClone(rows);Object.assign(copy[1],patch);assert.equal(assess(copy).accepted,false,JSON.stringify(patch));
 }
 const copy=structuredClone(rows);copy.filter(x=>x.variant==='candidate').forEach(x=>x.gaps_ms=[90]);assert.equal(assess(copy).accepted,false);
});
