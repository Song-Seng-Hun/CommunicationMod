import test from 'node:test';import assert from 'node:assert/strict';
import {codexArgs,taskSettings} from '../evaluation/agent-latency/runner.mjs';
import {scenario,judge} from '../evaluation/agent-latency/scenarios.mjs';
import {remainingTimeout,stopTree} from '../evaluation/agent-latency/process.mjs';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {hostBlocker} from '../evaluation/agent-latency/events.mjs';
test('startup warnings are not tool violations; approval denials stop without changing policy',()=>{
 assert.equal(hostBlocker({type:'item.completed',item:{type:'error',message:'Under-development features enabled'}}),null);
 assert.equal(hostBlocker({type:'item.completed',item:{type:'mcp_tool_call',error:{message:'MCP tool call requires approval, but approval policy is never'}}}),'approval_required');
 assert.equal(hostBlocker({type:'turn.failed'}),'host_turn_failed');
});
test('absolute deadline includes preparation and evaluation child cleanup is bounded',async()=>{
 assert.equal(remainingTimeout(100000,99900),100);assert.equal(remainingTimeout(1000000,0),90000);
 assert.throws(()=>remainingTimeout(100,100),/deadline/);
 const child=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'pipe',windowsHide:true});
 assert.equal(await stopTree(child),true);
});
test('parent exit while descendant holds pipes fails closed without hanging',async()=>{
 const code="const c=require('child_process').spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:['ignore',1,2],windowsHide:true});console.log(c.pid);c.unref();";
 const parent=spawn(process.execPath,['-e',code],{stdio:'pipe',windowsHide:true});
 const [data]=await once(parent.stdout,'data');const pid=Number(data.toString().trim());assert.ok(Number.isInteger(pid));
 await once(parent,'exit');
 try{assert.equal(await stopTree(parent),false);}finally{
  const killer=spawn('taskkill.exe',['/PID',String(pid),'/T','/F'],{stdio:'ignore',windowsHide:true});const [code]=await once(killer,'exit');
  if(code!==0)assert.throws(()=>process.kill(pid,0),e=>e.code==='ESRCH','Some Windows hosts already reap the descendant with its parent');
 }
});
test('hanging cleanup helper is killed and detached within the bounded wait',async()=>{
 if(process.platform!=='win32')return;
 const child=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'pipe',windowsHide:true});let killer;
 const ok=await stopTree(child,{cleanupMs:50,spawnKiller:()=>{killer=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'pipe',windowsHide:true});return killer;}});
 assert.equal(ok,false);assert.equal(child.killed,true);assert.equal(killer.killed,true);
});
test('runner pins verified model/effort, only synthetic MCP, preserves auth and disables other access',()=>{
 const args=codexArgs({cwd:'C:/fake',dist:'C:/fake/dist',log:'C:/fake/log',settings:{model:'gpt-6-astra',effort:'high',scenario:1}});
 assert.equal(args[args.indexOf('--model')+1],'gpt-6-astra');
 assert.ok(args.includes('model_reasoning_effort="high"'));assert.ok(args.includes('--ignore-user-config'));assert.ok(args.includes('--ephemeral'));
 assert.ok(args.includes('read-only'));assert.ok(args.includes('approval_policy="never"'));assert.ok(args.includes('forced_login_method="chatgpt"'));
 for(const feature of ['shell_tool','apps','plugins','hooks','browser_use','computer_use','multi_agent'])assert.ok(args.some((x,i)=>x==='--disable'&&args[i+1]===feature));
 assert.ok(!args.some(x=>x.includes('dangerously')||x==='--ignore-rules'));
 assert.match(args.find(x=>x.startsWith('mcp_servers.eval=')),/enabled_tools=\["sts_get_state","sts_get_context","sts_act","sts_get_request"\]/);
});
test('no fallback to global CLI model and no guessed correct actions',async()=>{
 await assert.rejects(()=>taskSettings('unrelated.jsonl','actual-task'),/Actual task rollout/);
 for(let id=1;id<=6;id++)assert.equal(judge(id,scenario(id).expected,[]).correct,false,'Required evidence, not an answer-only benchmark');
});
test('judge rejects blocked replay and requires successful ordered uncertainty recovery',()=>{
 const expected=scenario(6).expected;
 const events=[{kind:'request',id:1,name:'sts_get_context',at:1},{kind:'response',id:1,name:'sts_get_context',error:true,data:'Stale state',at:2},
 {kind:'request',id:2,name:'sts_get_state',at:3},{kind:'response',id:2,name:'sts_get_state',data:{session_id:'latency-synthetic-v1',state_id:10,ready:true},at:4},
 {kind:'request',id:3,name:'sts_act',args:{request_id:'r',action_id:'run.rest.safe'},at:5},{kind:'backend_act',command:{request_id:'r',action_id:'run.rest.safe'},at:6},
 {kind:'response',id:3,name:'sts_act',data:{request_id:'r',outcome:'unknown',retry_allowed:false},at:7},
 {kind:'request',id:4,name:'sts_get_request',args:{request_id:'r'},at:8},{kind:'response',id:4,name:'sts_get_request',data:{request_id:'r',receipt:null,pending:true,retry_allowed:false},at:9},
 {kind:'request',id:5,name:'sts_get_state',at:10},{kind:'response',id:5,name:'sts_get_state',data:{session_id:'latency-synthetic-v1',state_id:10,connection:{pending_request_id:'r'}},at:11}];
 assert.equal(judge(6,expected,events).correct,true);
 assert.equal(judge(6,expected,[...events,{kind:'request',name:'sts_act',args:{request_id:'r'},at:12}]).safe,false);
 assert.equal(judge(6,expected,[...events,{kind:'host_action_attempt_count',count:2}]).safe,false,'Host-denied retries may never reach the MCP proxy');
 assert.equal(judge(6,expected,events.filter(e=>e.id!==5)).correct,false);
 const failed=structuredClone(events);failed.find(e=>e.kind==='response'&&e.id===4).error=true;
 assert.equal(judge(6,expected,failed).correct,false);
});
test('simple action requires returned next-state evidence; keyword-only acknowledgement is insufficient',()=>{
 const e=[{kind:'request',id:1,name:'sts_get_state',at:1},{kind:'request',id:2,name:'sts_act',at:2},{kind:'backend_act',command:{action_id:'run.proceed'},at:3}];
 assert.equal(judge(1,scenario(1).expected,e).correct,false);
 const body=scenario(4).state.observation.game_state.screen_state.event_reading.body_text;
 const events=[{kind:'request',name:'sts_get_state',at:0},{kind:'request',name:'sts_act',at:2},{kind:'response',data:{fragments:[{ref:'screen/event_reading/body_text',format:'text',offset:0,text:body}]},at:1},
 {kind:'backend_act',command:{action_id:scenario(4).act,arguments:{reading_id:'reading-tower-10',commentary:'17 9 푸른'}},at:3}];
 assert.equal(judge(4,scenario(4).expected,events).correct,false);
});
