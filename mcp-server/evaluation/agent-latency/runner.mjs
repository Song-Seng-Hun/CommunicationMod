// Explicitly invoked bounded Codex evaluation. npm test never starts a model.
import {spawn} from 'node:child_process';import {createReadStream,appendFileSync} from 'node:fs';
import {readFile,writeFile,mkdtemp,mkdir,symlink,access} from 'node:fs/promises';
import path from 'node:path';import os from 'node:os';import readline from 'node:readline';
import {fileURLToPath} from 'node:url';import {createHash} from 'node:crypto';
import {Budget,schedule,metrics,assess} from './metrics.mjs';import {scenario,judge} from './scenarios.mjs';
import {remainingTimeout,stopTree} from './process.mjs';
import {hostBlocker} from './events.mjs';
const here=path.dirname(fileURLToPath(import.meta.url)),repo=path.resolve(here,'../../..');
const now=()=>Number(process.hrtime.bigint())/1e6;
const allowed=['sts_get_state','sts_get_context','sts_act','sts_get_request'];
export async function taskSettings(rollout,thread){
 if(!thread||!path.basename(rollout).includes(thread))throw new Error('Actual task rollout required');
 let result;
 for await(const line of readline.createInterface({input:createReadStream(rollout),crlfDelay:Infinity})){
  if(!line.includes('turn_context'))continue;
  let j;try{j=JSON.parse(line);}catch{continue;}
  if(j.type==='turn_context')result={model:j.payload.model,effort:j.payload.effort??j.payload.collaboration_mode?.settings?.reasoning_effort,timestamp:j.timestamp,thread};
 }
 if(!result?.model||!result?.effort)throw new Error('Actual task model/effort unavailable; evaluation deferred');
 return result;
}
export async function verifySnapshot(root){
 const manifest=JSON.parse(await readFile(path.join(root,'manifest.json'),'utf8'));
 for(const [name,hash] of Object.entries(manifest.hashes)){
  if(path.isAbsolute(name)||name.split(/[\\/]/).includes('..'))throw new Error('Invalid snapshot path');
  if(createHash('sha256').update(await readFile(path.join(root,name))).digest('hex')!==hash)throw new Error('Snapshot changed: '+name);
 }
 await writeFile(path.join(root,'package.json'),JSON.stringify({type:'module'}));
 try{await access(path.join(root,'node_modules'));}catch{await symlink(path.join(repo,'mcp-server/node_modules'),path.join(root,'node_modules'),'junction');}
 return manifest;
}
export function codexArgs({cwd,dist,log,settings}){
 const args=['exec','--strict-config','--ignore-user-config','--ephemeral','--skip-git-repo-check','--sandbox','read-only','--cd',cwd,'--json','--color','never','--model',settings.model,
  '-c',`model_reasoning_effort=${JSON.stringify(settings.effort)}`,'-c','approval_policy="never"','-c','web_search="disabled"','-c','forced_login_method="chatgpt"'];
 for(const feature of ['shell_tool','shell_snapshot','unified_exec','apps','plugins','hooks','browser_use','browser_use_external','computer_use','in_app_browser','image_generation','multi_agent','multi_agent_v2','memories','skill_search','skill_mcp_dependency_install','workspace_dependencies','view_image','sleep_tool','code_mode','goals'])args.push('--disable',feature);
 // The host runtime is required by this task's model even when no Code Mode override is requested.
 args.push('--enable','code_mode_host','--enable','skip_host_skill_discovery','-c','suppress_unstable_features_warning=true');
 args.push('-c',`mcp_servers.eval={command=${JSON.stringify(process.execPath)},args=${JSON.stringify([path.join(here,'server.mjs'),String(settings.scenario),dist,log,String(settings.deadline??0)])},enabled_tools=${JSON.stringify(allowed)},startup_timeout_sec=15,tool_timeout_sec=20}`);
 args.push('-');return args;
}
async function runOne(codex,root,row,snap,settings,deadline){
 const run=path.join(root,`${row.scenario}-${row.repetition}-${row.variant}`);await mkdir(run);
 const cwd=await mkdtemp(path.join(os.tmpdir(),'downfall-agent-eval-')),log=path.join(run,'mcp.jsonl'),hostLog=path.join(run,'host.jsonl');
 const skill=await readFile(path.join(snap,'plugin/downfall-agent/skills/downfall-play/SKILL.md'),'utf8');
 const prompt=`가상 Downfall 평가 과제. 실제 게임이나 외부 자료 사용 금지. eval MCP의 sts_get_state, sts_get_context, sts_act, sts_get_request만 사용. 도구 실행은 모두 가상 백엔드에 한정됨. 시작/상태 프로세스/셸/브라우저/파일/다른 MCP 도구 사용 금지. 필요한 사실은 도구로 확인하고 추측하지 말라.\n\n플러그인 지침:\n${skill}\n\n과제:\n${scenario(row.scenario).prompt}\n최종 응답은 JSON 객체 하나: {"decision":"proceeded|played|acquired|acknowledged|inspected|uncertain_stop 중 해당 하나","facts":{과제에서 요구한 키:관측한 값}}. 이벤트 설명 등 필요한 중간 설명은 유지.`;
 await writeFile(path.join(run,'prompt.txt'),prompt);
 const env={...process.env};for(const key of Object.keys(env))if(key.startsWith('CODEX_')&&key!=='CODEX_HOME')delete env[key];
 delete env.COMMUNICATIONMOD_WORKSPACE;
 const runDeadline=Math.min(deadline,now()+90000);
 const args=codexArgs({cwd,dist:path.join(snap,'mcp-server/dist'),log,settings:{...settings,scenario:row.scenario,deadline:runDeadline}});
 await writeFile(path.join(run,'invocation.json'),JSON.stringify({args,settings,cwd},null,2));
 const timeout=remainingTimeout(runDeadline,now());
 const started=now(),child=spawn(codex,args,{cwd,env,stdio:['pipe','pipe','pipe'],windowsHide:true});
 let status='running',usage=null,answer=null,finalAt=null,stderr='',violation=null,blocker=null,cleanupVerified=true,resolveExit,cleanupWork=Promise.resolve(true);const host=[];
 const exited=new Promise(resolve=>{resolveExit=resolve;child.on('error',e=>{stderr+=e.message;resolve(-1);});child.on('close',resolve);});
 const kill=why=>{if(status==='running'){
  status=why;cleanupWork=stopTree(child);cleanupWork.then(ok=>{if(!ok)resolveExit(-2);});
 }};
 const timer=setTimeout(()=>kill('timeout'),timeout);
 let inspecting=false;
 const monitor=setInterval(async()=>{
  if(inspecting||status!=='running')return;inspecting=true;
  try{const text=await readFile(log,'utf8');const e=text.split('\n').filter(Boolean).map(x=>{try{return JSON.parse(x);}catch{return {};}}).find(e=>e.kind==='violation');
   if(e){violation='proxy:'+e.reason;kill('violation');}
  }catch{}finally{inspecting=false;}
 },50);
 const lines=readline.createInterface({input:child.stdout,crlfDelay:Infinity});
 lines.on('line',line=>{
  const at=now();appendFileSync(hostLog,JSON.stringify({at,line})+'\n');
  let e;try{e=JSON.parse(line);}catch{return;}
  const item=e.item;
  const blocked=hostBlocker(e);if(blocked){blocker=blocked;kill(blocked==='approval_required'?'approval_blocked':'failed');}
  if(e.type==='turn.completed')usage=e.usage??null;
  if(e.type==='turn.failed')kill('failed');
  if(item?.type==='mcp_tool_call'){
   if(item.server!=='eval'||!allowed.includes(item.tool)){violation='unexpected_tool';kill('violation');return;}
   if(e.type==='item.started')host.push({kind:'request',id:item.id,name:item.tool,args:item.arguments,at});
   if(e.type==='item.completed')host.push({kind:'response',id:item.id,at,error:!!item.error});
  }else if(item&&!['reasoning','agent_message','error'].includes(item.type)){
   violation='unexpected_host_item:'+item.type;kill('violation');
  }
  if(e.type==='item.completed'&&item?.type==='agent_message'){
   try{answer=JSON.parse(item.text.replace(/^```(?:json)?\s*|\s*```$/g,''));finalAt=at;}catch{}
  }
 });
 child.stderr.on('data',x=>{stderr+=x.toString();if(stderr.length>1000000)kill('stderr_budget');});
 child.stdin.on('error',()=>kill('failed'));child.stdin.end(prompt);
 const code=await exited;clearTimeout(timer);clearInterval(monitor);cleanupVerified=await cleanupWork;
 if(status==='running')status=code===0?'completed':'failed';
 await writeFile(path.join(run,'stderr.txt'),stderr);
 let proxy=[];try{proxy=(await readFile(log,'utf8')).trim().split('\n').filter(Boolean).map(JSON.parse);}catch{}
 const proxyViolation=proxy.find(e=>e.kind==='violation');if(proxyViolation){violation='proxy:'+proxyViolation.reason;status='violation';}
 // Prefer host lifecycle events only when complete and one-to-one with actual proxy calls.
 const hostRequests=host.filter(e=>e.kind==='request').length,proxyCalls=proxy.filter(e=>e.kind==='request').length;
 const clock=hostRequests>0&&hostRequests===proxyCalls&&host.filter(e=>e.kind==='response').length===hostRequests?'host_event':'mcp_proxy';
 const m=metrics(clock==='host_event'?host:proxy,clock),p=metrics(proxy);
 const verdict=judge(row.scenario,answer,[...proxy,{kind:'host_action_attempt_count',count:host.filter(e=>e.kind==='request'&&e.name==='sts_act').length}]);
 const tokenValue=usage&&Number.isFinite(usage.input_tokens)&&Number.isFinite(usage.output_tokens)?usage.input_tokens+usage.output_tokens:null;
 const result={...row,...m,server_ms:p.server_ms,duplicates:p.duplicates,errors:p.errors,invalid_arguments:p.invalid_arguments,retries:p.retries,stale_rejections:p.stale_rejections,...verdict,safe:verdict.safe&&!violation,status,answer,usage,tokens:tokenValue,total_ms:finalAt?finalAt-started:null,elapsed_ms:now()-started,
  calls:Math.max(hostRequests,proxyCalls),host_calls:hostRequests,proxy_calls:proxyCalls,initial_connection_ms:proxy.find(e=>e.kind==='initialize_response')?.at-started,proxy_ready_ms:proxy.find(e=>e.kind==='ready')?.at-started,violation,blocker,cleanup_verified:cleanupVerified,run_dir:run};
 await writeFile(path.join(run,'result.json'),JSON.stringify(result,null,2));return result;
}
export async function campaign({codex,baseline,candidate,rollout,thread,output,resume=false}){
 const settings=await taskSettings(rollout,thread);
 await verifySnapshot(baseline);await verifySnapshot(candidate);
 // Exclusive creation prevents accidental resume/rerun of a spent campaign.
 let rows=[],started=now(),starts=0;
 if(resume){
  const previous=JSON.parse(await readFile(path.join(output,'report.json'),'utf8'));
  if(previous.settings.model!==settings.model||previous.settings.effort!==settings.effort||previous.settings.thread!==thread)throw new Error('Resume settings mismatch');
  const ledger=(await readFile(path.join(output,'starts.jsonl'),'utf8')).trim().split('\n').map(JSON.parse);
  if(ledger.length!==previous.rows.length||ledger.length!==previous.starts)throw new Error('Ambiguous pending evaluation; never replay');
  if(previous.rows.some(r=>r.cleanup_verified!==true))throw new Error('Previous evaluation cleanup unverified; cannot resume');
  if(previous.rows.some(r=>r.violation&&!(r.violation==='unexpected_host_item:error'&&r.calls===0&&r.cleanup_verified===true)))throw new Error('Actual isolation violation: campaign cannot resume');
  rows=previous.rows;starts=ledger.length;
  // Legacy first-run ledger lacks a deadline file; subtract one second conservatively.
  try{started=JSON.parse(await readFile(path.join(output,'budget.json'),'utf8')).started;}catch{started=ledger[0].at-1000;}
 }else{
  await mkdir(output);await writeFile(path.join(output,'settings.json'),JSON.stringify(settings,null,2),{flag:'wx'});
  await writeFile(path.join(output,'budget.json'),JSON.stringify({started,max_starts:24,duration_ms:1200000,per_run_ms:90000}),{flag:'wx'});
 }
 const budget=new Budget(settings,started);budget.starts=starts;
 for(const row of schedule().slice(starts)){
  let timeout;try{timeout=budget.start(now());}catch{break;}
  appendFileSync(path.join(output,'starts.jsonl'),JSON.stringify({at:now(),...row})+'\n');
  const result=await runOne(codex,output,row,row.variant==='baseline'?baseline:candidate,settings,budget.deadline);rows.push(result);
  console.log(JSON.stringify({run:rows.length,...row,status:result.status,correct:result.correct,tokens:result.tokens,total_ms:result.total_ms}));
  await writeFile(path.join(output,'report.json'),JSON.stringify({settings,starts:budget.starts,rows,assessment:assess(rows)},null,2));
  // Infrastructure/tool isolation failure is not a reason to spend more model calls.
  if(result.blocker||result.status==='violation'||result.violation||!result.cleanup_verified||result.calls===0||result.status==='failed')break;
 }
 return {settings,starts:budget.starts,rows,assessment:assess(rows)};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const [codex,baseline,candidate,rollout,thread,output,resumeFlag]=process.argv.slice(2);
 if(![codex,baseline,candidate,rollout,thread,output].every(Boolean))throw new Error('Required: codex baseline candidate actual-rollout thread output-new-dir');
 console.log(JSON.stringify(await campaign({codex,baseline,candidate,rollout,thread,output,resume:resumeFlag==='--resume'}),null,2));
}
