import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {Tiktoken} from 'js-tiktoken/lite';
import ranks from 'js-tiktoken/ranks/o200k_base';
import {readContext} from '../dist/context.js';
import {Lifecycle} from '../dist/lifecycle.js';
const baseline=JSON.parse(await readFile(new URL('../evaluation/baseline/prose-v2.json',import.meta.url),'utf8'));
const tokenizer=new Tiktoken(ranks),count=value=>tokenizer.encode(JSON.stringify(value),[],[]).length;
const state=screen_type=>({ready:true,actions:[],observation:{game_state:{screen_type,current_hp:20,combat_state:{turn:1}},reward_navigation:{unclaimed_rewards:2}}});

test('six tool descriptions and current rules shrink without changing schemas or safety clauses',async()=>{
 const client=new Client({name:'prose-audit',version:'1'});let tools;
 try{await client.connect(new StdioClientTransport({command:process.execPath,args:[fileURLToPath(new URL('../dist/index.js',import.meta.url))],stderr:'pipe'}));tools=(await client.listTools()).tools;}finally{await client.close();}
 const structural=items=>items.map(({title,description,...rest})=>{
  const copy=structuredClone(rest);
  // The separate compact-format feature adds one optional field, not prose compression.
  if(copy.name==='sts_get_context')delete copy.inputSchema.properties.response_format;
  return copy;
 });
 assert.deepEqual(structural(tools),structural(baseline.tools),'Only prose may change; schemas and safety annotations must not');
 const descriptions=Object.fromEntries(tools.map(t=>[t.name,t.description]));
 const required={
  sts_game_status:[/process/i,/connection/i,/no launch|without launching/i,/no play|without.*playing/i],
  sts_start_game:[/verified test game/i,/only.*user.*launch\/play request/i,/never.*resum/i,/never.*choos|no.*action/i,/starting.*status/i,/no relaunch|do not relaunch/i],
  sts_get_state:[/current.*decision/i,/toc/i,/state.scoped/i,/sts_get_context.*before act/i,/details_required.*incomplete.*(do not|never) guess/i,/known_view.*previous/i,/omit.*recover/i],
  sts_act:[/one offered action/i,/current session\/state IDs/i,/details first/i,/never auto-discard/i,/unknown\/applied_waiting.*inspect.*never replay/i,/receipt.*next decision/i],
  sts_get_context:[/1-8/i,/same session\/state IDs/i,/fields/i,/child toc/i,/exact text/i,/next_offset/i,/row\/field/i,/UTF-16/i,/no full dump/i,/stale.*refresh.*rediscover/i,/relevant rules/i],
  sts_get_request:[/receipt/i,/request_id/i,/without replay/i,/missing.*unknown.*not failure/i,/never resend/i]
 };
 for(const [name,patterns] of Object.entries(required))for(const pattern of patterns)assert.match(descriptions[name],pattern,`${name}: ${pattern}`);
 const rules={};
 for(const screen of ['EVENT','MAP','COMBAT_REWARD','NONE']){
  const s=state(screen),fragment=readContext(s,['rules']).fragments[0];Object.assign(rules,fragment.data);
  for(const row of fragment.toc??[])rules[row.ref.split('/').at(-1)]=readContext(s,[row.ref]).fragments[0].text;
 }
 assert.deepEqual(Object.keys(rules).sort(),Object.keys(baseline.rules).sort());
 const ruleClauses={
  act:[/only offered actions/i,/ready state/i,/read.*details/i,/one action per call/i,/never replay unknown\/applied_waiting/i,/refresh.*stale rejection/i],
  cost:[/native displayed_cost_text and cost_components/i,/energy\/reserves\/X\/Pyre/,/incomplete or unrendered.*unknown/i,/target_playability and unplayable_reason before playing/i],
  upgrade:[/before acquisition or upgrade/i,/offered card.*upgrade_preview/i,/keyword/i,/only.*next standard upgrade/i,/not random\/event\/relic outcomes/i,/selected_after before separate branch\/tree confirmation/i],
  event:[/read and present (the )?current event body, situation.*choices before acknowledgement/i,/observed reading_id/i,/meaningful commentary/i,/discuss result pages/i,/never acknowledge incomplete text|do not.*acknowledge incomplete text/i],
  map:[/draw.*route/i,/never moves.*character/i,/offered map_id\/revision and node IDs/i,/stale revisions.*reject/i,/native edges only/i,/not predicted travel powers/i],
  reward:[/read reward_navigation before leaving/i,/unclaimed rewards may be abandoned/i,/never auto-discard.*potion.*space/i,/discard.*separate destructive choice/i]
 };
 for(const [name,patterns] of Object.entries(ruleClauses))for(const pattern of patterns)assert.match(rules[name],pattern,`${name}: ${pattern}`);
 let launches=0;
 const lifecycle=new Lifecycle('fixture',{ensure:async()=>{},game:{current:()=>({connection:{status:'disconnected_or_stale'}})}},async()=>{launches++;return {phase:'running'};});
 const loading=await lifecycle.start(0);assert.equal(launches,1);assert.equal(loading.retry_launch,false);
 assert.match(loading.next_step,/sts_get_state or sts_game_status/);assert.match(loading.next_step,/no second game|do not start a second game/i);
 const rows=[...tools.map((tool,i)=>({name:tool.name,before:count({title:baseline.tools[i].title,description:baseline.tools[i].description}),after:count({title:tool.title,description:tool.description})})),
  ...Object.keys(rules).map(name=>({name:`rules/${name}`,before:count(baseline.rules[name]),after:count(rules[name])})),
  {name:'startup_wait',before:count(baseline.next_step),after:count(loading.next_step)}];
 const total={before:rows.reduce((n,r)=>n+r.before,0),after:rows.reduce((n,r)=>n+r.after,0)};
 const report={measurement:'Local o200k_base; prose once each, NOT billed tokens or task savings',rows,total,catalog:{before:count(baseline.tools),after:count(tools)},gameplay_actions:0};
 await mkdir(new URL('../../target/',import.meta.url),{recursive:true});
 await writeFile(new URL('../../target/prose-compression-report.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
 assert.deepEqual(rows.filter(r=>r.after>=r.before),[],'Each edited prose unit must use fewer tokens');
});

test('native text, numeric facts, identifiers, uncertainty and exact errors are not rewritten',async()=>{
 const card={id:'never/replay-X',name:'화염',description:'피해 12. 방어도 없으면 사용 불가. 최대 HP 3 감소.',displayed_cost_text:'X',cost_components:{energy:0,reserves:2},displayed_cost_complete:false,unplayable_reason:'Do not replay unknown outcomes.',upgrade_preview:{status:'unavailable',reason:'not rendered'}};
 const s={session_id:'s-01',state_id:42,...state('CARD_REWARD')};s.observation.game_state.screen_state={cards:[card],event_reading:{body_text:'선택 전: 골드 75 소모. 취소 불가.\n동의하지 않으면 떠난다.'}};
 assert.deepEqual(readContext(s,['screen/cards/0']).fragments[0].data,card);
 assert.equal(readContext(s,['screen/event_reading/body_text']).fragments[0].text,s.observation.game_state.screen_state.event_reading.body_text);
 assert.throws(()=>readContext(s,['full']),{message:'Reference not available in current state.'});
 const exact='Native error: never resend; request_id=abc; code=409';
 const lifecycle=new Lifecycle('fixture',{ensure:async()=>{},game:{current:()=>({})}},async()=>{throw new Error(exact);});
 await assert.rejects(lifecycle.start(0),{message:exact});
});
