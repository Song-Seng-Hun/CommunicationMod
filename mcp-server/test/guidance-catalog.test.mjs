import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {pathToFileURL,fileURLToPath} from 'node:url';
import path from 'node:path';
import {getEncoding} from 'js-tiktoken';
import {providerLiterals,actionFactories,legacyActions,controlRoots,contextRoots,dynamicContracts,coverageFixtures,requiredProviderCoverage,requiredRootCoverage,transportInventoryFiles} from '../evaluation/guidance-fixtures.mjs';

const root=fileURLToPath(new URL('../../src/main/java/communicationmod/',import.meta.url));
const catalogURL=process.env.GUIDANCE_CATALOG_MODULE?pathToFileURL(path.resolve(process.env.GUIDANCE_CATALOG_MODULE)).href:new URL('../dist/guidance-catalog.js',import.meta.url).href;
const api=async()=>{
 const result=await import(catalogURL).catch(error=>({loadError:error}));
 assert.ok(Array.isArray(result.capabilities),'Missing declarative capabilities export: '+(result.loadError?.message??catalogURL));
 return result.capabilities;
};
const sorted=x=>[...new Set(x)].sort();
const literals=source=>[...source.matchAll(/"(?:\\.|[^"\\\r\n])*"|'(?:\\.|[^'\\\r\n])*'|\/\/[^\n]*|\/\*[\s\S]*?\*\//g)].filter(x=>x[0][0]==='"').map(x=>x[0].slice(1,-1));
const actionLiterals=source=>sorted(literals(source).filter(x=>/^(run\.|menu\.)/.test(x)||x==='acknowledge_event_reading'));
async function javaSources(dir=root,prefix=''){
 const out={};
 for(const e of await readdir(dir,{withFileTypes:true})){
  if(e.isDirectory())Object.assign(out,await javaSources(path.join(dir,e.name),prefix+e.name+'/'));
  else if(e.name.endsWith('.java'))out[prefix+e.name]=await readFile(path.join(dir,e.name),'utf8');
 }
 return out;
}
function rootsFrom(source){
 const keys=source.match(/\bconst controlKeys\s*=\s*\[([^\]]+)\]/)?.[1]??'';
 const controls=[...keys.matchAll(/['"]([^'"]+)['"]/g)].map(x=>x[1]);
 const roots=[...source.matchAll(/\badd\(\s*['"]([^'"]+)['"]/g)].map(x=>x[1]);
 return {controls:sorted(controls),roots:sorted([...roots,...controls].filter(x=>x!=='guidance'))};
}
const gateMatches=(gate,action)=>(gate.actions??[]).some(x=>x.endsWith('.')?action.startsWith(x):action===x);
const wrapper=c=>({ref:'guidance/'+c.id,format:'example',data:c,page_complete:true,next_offset:null});
const bind=name=>({$bind:name});
function assertCoverage(caps,fixtures){
 const byId=new Map(caps.map(x=>[x.id,x]));
 const requireOwner=(owner,covers,origin)=>{
  const cap=byId.get(owner);assert.ok(cap,origin+': missing '+owner);
  for(const cover of covers){
   assert.ok(cap.covers.includes(cover),origin+': missing '+cover+' in '+owner);
   assert.ok(fixtures.some(f=>f.capability===owner&&f.cover===cover),origin+': missing fixture '+cover);
  }
 };
 for(const [file,rows] of Object.entries(requiredProviderCoverage))
  for(const [literal,owner,covers] of rows)requireOwner(owner,covers,file+':'+literal);
 for(const [root,owners] of Object.entries(requiredRootCoverage))
  for(const [owner,covers] of owners)requireOwner(owner,covers,'root '+root);
 assert.deepEqual(sorted(caps.flatMap(x=>x.covers)),sorted(fixtures.map(x=>x.cover)));
 for(const fixture of fixtures){
  const cap=byId.get(fixture.capability);assert.ok(cap,fixture.capability);
  assert.ok(cap.covers.includes(fixture.cover),fixture.cover);
  if(fixture.action)assert.ok(gateMatches(cap.gate,fixture.action),fixture.cover+' gate');
 }
}

test('catalog loads as declarative capabilities',async()=>{assert.ok((await api()).length>=25);});
test('every capability has exactly three independent bounded capsules with typed used bindings',async()=>{
 const caps=await api(),ids=new Set();
 for(const cap of caps){
  assert.equal(typeof cap.id,'string');assert.ok(!ids.has(cap.id),cap.id);ids.add(cap.id);
  assert.equal(typeof cap.title,'string');assert.ok(cap.title.length>0);assert.ok([0,1,2].includes(cap.priority));
  assert.ok(cap.covers.length>0);assert.deepEqual(sorted(cap.cases.map(x=>x.case)),['exception','incomplete','normal']);
  assert.ok(Object.keys(cap.gate).length>0);
  for(const [k,v] of Object.entries(cap.gate)){
   if(k==='special')assert.ok(['ready','parameters','empty_parameters','pending','unready','bootstrap'].includes(v));
   else {assert.ok(['screens','roots','actions'].includes(k));assert.ok(Array.isArray(v)&&v.length>0);assert.ok(v.every(x=>typeof x==='string'&&x.length>0));}
  }
  for(const c of cap.cases){
   assert.equal(c.id,cap.id+'/'+c.case);assert.equal(c.revision,'1');assert.equal(c.capability,cap.id);
   for(const key of ['when','not_when','requires','expect','stop'])assert.ok(typeof c[key]==='string'&&c[key].trim(),c.id+': '+key);
   assert.ok(JSON.stringify(wrapper(c)).length<=2400,c.id+' wrapper exceeds 2400');
   assert.ok(Array.isArray(c.calls));assert.ok(c.bindings&&typeof c.bindings==='object');
   const used=new Set();
   function visit(v){
    if(v===null||typeof v!=='object')return;
    if(Object.hasOwn(v,'$bind')){
     assert.deepEqual(Object.keys(v),['$bind']);assert.ok(Object.hasOwn(c.bindings,v.$bind),c.id+': unknown binding '+v.$bind);used.add(v.$bind);return;
    }
    for(const child of Object.values(v))visit(child);
   }
   visit(c.calls);assert.deepEqual(sorted(used),sorted(Object.keys(c.bindings)),c.id+': unused bindings');
   for(const binding of Object.values(c.bindings)){
    assert.ok(['string','number','object','array'].includes(binding.type));assert.ok(typeof binding.source==='string'&&binding.source.length>0);
   }
   for(const call of c.calls){
    assert.ok(['sts_game_status','sts_start_game','sts_get_state','sts_act','sts_get_context','sts_get_request'].includes(call.tool),call.tool);
    assert.ok(call.arguments&&typeof call.arguments==='object'&&!Array.isArray(call.arguments));
    for(const [key,type,source] of [['session_id','string','current.session_id'],['state_id','number','current.state_id'],['action_id','string','current.offered_action.id'],['request_id','string','last.request_id']]){
     if(Object.hasOwn(call.arguments,key)){
      const arg=call.arguments[key];assert.ok(arg&&typeof arg==='object'&&typeof arg.$bind==='string',c.id+': literal '+key);
      assert.deepEqual(c.bindings[arg.$bind],{type,source});
     }
    }
    if(call.tool==='sts_get_context'){
     const refs=call.arguments.refs;assert.ok(refs&&typeof refs==='object'&&typeof refs.$bind==='string',c.id+': literal refs');
     assert.deepEqual(c.bindings[refs.$bind],{type:'array',source:'current.observed_refs'});
    }
   }
  }
 }
});
test('capsules preserve permission, incomplete-read-only and no-retransmit boundaries',async()=>{
 for(const cap of await api())for(const c of cap.cases){
  const prose=[c.when,c.not_when,c.requires,c.expect,c.stop].join(' ');
  assert.match(prose,/unknown.*missing.*receipt.*never (retransmit|replay)|unknown\/missing receipt.*never (retransmit|replay)/i,c.id);
  if(c.case==='incomplete')assert.ok(c.calls.every(x=>!['sts_act','sts_start_game'].includes(x.tool)),c.id);
  if(c.calls.some(x=>x.tool==='sts_act')){
   assert.equal(c.case,'normal');assert.match(prose,/explicit task permission/i);assert.match(prose,/ready/i);
   assert.match(prose,/current offered action/i);assert.match(prose,/complete relevant evidence/i);
   assert.match(prose,/pending/i);
  }
  if(c.calls.some(x=>x.tool==='sts_start_game')){
   assert.equal(cap.gate.special,'bootstrap');assert.match(prose,/explicit user launch permission/i);
  }
  if(cap.id.startsWith('lifecycle.'))assert.deepEqual(cap.gate,{special:'bootstrap'});
  if(cap.id==='recovery.receipt')assert.deepEqual(cap.gate,{special:'pending'});
  if(cap.id==='recovery.unsupported')assert.deepEqual(cap.gate,{special:'unready'});
  if(cap.id==='recovery.stale')assert.deepEqual(cap.gate,{special:'bootstrap'},'No server evidence selects stale-rejection help');
  if(['state.decision','action.simple'].includes(cap.id))assert.equal(cap.priority,2);
  if(['cards.inspect','action.parameters'].includes(cap.id))assert.equal(cap.priority,1);
 }
});
test('static catalog contains no runtime dependencies or generated behavior',async()=>{
 const source=await readFile(new URL('../src/guidance-catalog.ts',import.meta.url),'utf8');
 assert.ok(!/^\s*import\b/m.test(source),'No runtime imports');
 const declaration=source.match(/export const capabilities\s*:\s*Capability\[\]\s*=\s*([\s\S]*);\s*$/);
 assert.ok(declaration,'Export the literal capabilities array');
 assert.deepEqual(JSON.parse(declaration[1]),await api(),'Only JSON data, no factories or runtime expressions');
});
test('source inventory independently rejects new Java literals and action providers',async()=>{
 const sources=await javaSources(),observed={};
 for(const [file,source] of Object.entries(sources)){const ids=actionLiterals(source);if(ids.length)observed[file]=ids;}
 assert.deepEqual(observed,Object.fromEntries(Object.entries(providerLiterals).map(([k,v])=>[k,sorted(v)])));
 assert.deepEqual(sorted(Object.entries(sources).filter(([,s])=>/new ProtocolSession\.Action\s*\(/.test(s)).map(([f])=>f)),sorted(actionFactories));
 const legacy=[...sources['protocol/CombatActions.java'].matchAll(/\baction\("([^"]+)"/g)].map(x=>x[1]);
 assert.deepEqual(sorted(legacy),sorted(legacyActions));
 // New exact subtypes fail even under an already known family; comments are not actions.
 const old=sources['observation/RoomUi.java'];
 assert.notDeepEqual(actionLiterals(old+'\n action("run.shop.NEW", x);'),sorted(providerLiterals['observation/RoomUi.java']));
 assert.deepEqual(actionLiterals(old+'\n // "run.shop.COMMENT"'),sorted(providerLiterals['observation/RoomUi.java']));
 assert.ok(/new ProtocolSession\.Action\s*\(/.test('new ProtocolSession.Action("new_family", label, schema);')); // factory path inventory catches unprefixed new providers
});
test('native context roots and controls are independently inventoried without importing context',async()=>{
 const source=await readFile(new URL('../src/context.ts',import.meta.url),'utf8'),observed=rootsFrom(source);
 assert.deepEqual(observed.controls,sorted(controlRoots));assert.deepEqual(observed.roots,sorted(contextRoots));
 assert.notDeepEqual(rootsFrom(source+"\nadd('new_control',value);").roots,sorted(contextRoots));
 assert.notDeepEqual(rootsFrom(source.replace("const controlKeys=[","const controlKeys=['new_control',")).controls,sorted(controlRoots));
});

test('required semantic ownership is joined to original Java inventory and every public root',async()=>{
 const sources=await javaSources();
 assert.deepEqual(sorted(Object.keys(requiredProviderCoverage)),sorted(Object.keys(providerLiterals).filter(f=>!transportInventoryFiles.includes(f))));
 for(const [file,rows] of Object.entries(requiredProviderCoverage)){
  assert.deepEqual(sorted(rows.map(r=>r[0])),actionLiterals(sources[file]),file+' needs explicit semantic owners for every literal');
  for(const [,owner,covers] of rows)assert.ok(owner&&covers.length>0,file+' empty semantic obligation');
 }
 const observed=rootsFrom(await readFile(new URL('../src/context.ts',import.meta.url),'utf8'));
 assert.deepEqual(sorted(Object.keys(requiredRootCoverage)),observed.roots);
 for(const control of observed.controls)assert.ok(requiredRootCoverage[control]?.length>0,control+' missing control ownership');
 assertCoverage(await api(),coverageFixtures);
});
test('dynamic providers retain every reviewed semantic discriminator',async()=>{
 for(const spec of dynamicContracts){
  const source=await readFile(path.join(root,spec.file),'utf8');
  const actual=spec.pattern?literals(source.match(new RegExp(spec.pattern,'s'))?.[1]??''):[...source.matchAll(/(?:button\.result\.name\(\)|result)\.equals\("([^"]+)"\)/g)].map(x=>x[1]);
  assert.deepEqual(sorted(actual),sorted(spec.values),spec.file);
 }
});
test('fixture coverage names each semantic subtype without catchall action/root coverage',async()=>{
 const caps=await api();assertCoverage(caps,coverageFixtures);
 for(const cap of caps){
  assert.ok(!cap.covers.some(x=>/\*|catchall|all_actions|all_roots/.test(x)));
  assert.ok(!(cap.gate.actions??[]).some(x=>['run.','menu.','run.shop.','run.grid.','run.hand.'].includes(x)),cap.id+' broad family');
  if(cap.gate.actions)assert.ok(!cap.gate.screens&&!cap.gate.roots&&!cap.gate.special,cap.id+' action-specific gates must only match offered actions');
 }
});

test('removing a capability and its fixtures cannot erase an inventoried provider obligation',async()=>{
 const caps=await api();assertCoverage(caps,coverageFixtures);
 assert.throws(()=>assertCoverage(caps.filter(c=>c.id!=='potion.use'),coverageFixtures.filter(f=>f.capability!=='potion.use')),/potion.use|potion.targeted/);
 assert.throws(()=>assertCoverage(caps.filter(c=>c.id!=='tutorial.confirm'),coverageFixtures.filter(f=>f.capability!=='tutorial.confirm')),/tutorial.confirm/);
 const changed=structuredClone(caps);changed.find(c=>c.id==='potion.use').covers=['potion.untargeted'];
 assert.throws(()=>assertCoverage(changed,coverageFixtures.filter(f=>f.cover!=='potion.targeted')),/potion.targeted/);
 // Every required subtype must survive deletion of its labels on BOTH sides.
 const requirements=new Set([
  ...Object.values(requiredProviderCoverage).flatMap(rows=>rows.flatMap(([,owner,covers])=>covers.map(c=>owner+'|'+c))),
  ...Object.values(requiredRootCoverage).flatMap(rows=>rows.flatMap(([owner,covers])=>covers.map(c=>owner+'|'+c))),
 ]);
 for(const item of requirements){
  const [owner,cover]=item.split('|'),mutant=structuredClone(caps);
  mutant.find(c=>c.id===owner).covers=mutant.find(c=>c.id===owner).covers.filter(c=>c!==cover);
  assert.throws(()=>assertCoverage(mutant,coverageFixtures.filter(f=>f.capability!==owner||f.cover!==cover)),error=>error.code==='ERR_ASSERTION'&&error.message.includes(cover),item+' removal escaped source obligations');
 }
});

test('grid confirmation distinguishes ordinary native after from branch/tree selected_after',async()=>{
 const cap=(await api()).find(c=>c.id==='selection.grid.confirm');
 for(const kind of ['normal','incomplete']){
  const c=cap.cases.find(c=>c.case===kind);
  assert.match(c.requires,/Ordinary upgrade: read (?:screen\.)?upgrade_selection_preview\.after/);
  assert.match(c.requires,/ordinary upgrade does not require (?:upgrade_choice\.)?selected_after/i);
  assert.match(c.requires,/Branch\/tree upgrade: read selection_controls\.upgrade_choice\.selected_after and require choice_required=false/);
  assert.doesNotMatch(c.requires,/if upgrading, read latest selected_after/);
 }
 const native=await readFile(path.join(root,'observation/CardUpgradeObservation.java'),'utf8');
 const extension=await readFile(path.join(root,'observation/UpgradeChoiceUi.java'),'utf8');
 assert.match(native,/result\.put\("after",snapshot\(screen\.upgradePreviewCard\)\)/);
 assert.match(extension,/choice\.add\("selected_after"/);
 assert.match(extension,/choice\.addProperty\("choice_required"/);
});
test('at least one complete state/simple capsule fits the 160 o200k inline budget',async()=>{
 const candidates=(await api()).filter(x=>['state.decision','action.simple'].includes(x.id)).flatMap(x=>x.cases),enc=getEncoding('o200k_base');
 assert.ok(candidates.some(c=>enc.encode(JSON.stringify(wrapper(c))).length<=160),'Keep a short safe capsule; other examples may be lookup-only');
});
test('special call shapes preserve actual event and map parameter contracts',async()=>{
 const caps=await api(),normal=id=>caps.find(x=>x.id===id).cases.find(x=>x.case==='normal');
 const ack=normal('event.ack'),ackArgs=ack.calls.find(x=>x.tool==='sts_act').arguments.arguments;
 assert.deepEqual(sorted(Object.keys(ackArgs)),['commentary','reading_id']);
 for(const value of Object.values(ackArgs))assert.equal(ack.bindings[value.$bind].type,'string');
 const plan=normal('map.plan'),planArgs=plan.calls.find(x=>x.tool==='sts_act').arguments.arguments;
 assert.deepEqual(sorted(Object.keys(planArgs)),['map_id','nodes','revision']);
 for(const [key,type] of [['map_id','string'],['revision','number'],['nodes','array']])assert.equal(plan.bindings[planArgs[key].$bind].type,type);
 const generic=normal('action.parameters'),args=generic.calls.find(x=>x.tool==='sts_act').arguments.arguments;
 assert.deepEqual(args,bind('parameters'));assert.equal(generic.bindings.parameters.type,'object');
});
