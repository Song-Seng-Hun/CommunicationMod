import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {Tiktoken} from 'js-tiktoken/lite';
import ranks from 'js-tiktoken/ranks/o200k_base';
const load=async()=>{const value=await import('../scripts/build-guidance.mjs').catch(()=>null);assert.ok(value,'offline guidance validator required');return value;};

test('offline validator rejects undefined bindings and mismatched input types',async()=>{
 const {bindCall}=await load();
 const c={tool:'sts_get_context',arguments:{session_id:{$bind:'s'},state_id:{$bind:'n'},refs:{$bind:'refs'}}};
 const bindings={s:{type:'string',source:'current.session_id'},n:{type:'number',source:'current.state_id'},refs:{type:'array',source:'current.observed_refs'}};
 assert.deepEqual(bindCall(c,bindings,{s:'fixture',n:17,refs:['deck/0']}),{session_id:'fixture',state_id:17,refs:['deck/0']});
 assert.throws(()=>bindCall(c,bindings,{s:'fixture',n:'17',refs:['deck/0']}),/type/);
 assert.throws(()=>bindCall(c,bindings,{s:'fixture',n:17}),/binding/);
 assert.throws(()=>bindCall(c,{...bindings,n:{type:'number',source:'other.state_id'}},{s:'fixture',n:17,refs:['deck/0']}),/state|source/);
 const bogus={tool:'sts_act',arguments:{session_id:{$bind:'s'},state_id:{$bind:'n'},action_id:{$bind:'a'},arguments:{map_id:{$bind:'map'}}}};
 assert.throws(()=>bindCall(bogus,{...bindings,a:{type:'string',source:'current.offered_action.id'},map:{type:'string',source:'current.NONEXISTENT.map_id'}},{s:'s',n:1,a:'run.map.plan',map:'m'}),/source/);
});

test('every built example has bounded wrapper, a real schema, and audited inline size',async()=>{
 const {validateCatalog}=await load();
 const {capabilities}=await import('../dist/guidance-catalog.js');
 const report=validateCatalog(capabilities);
 assert.equal(report.capabilities.length,capabilities.length);
 const enc=new Tiktoken(ranks),bundle=JSON.parse(await readFile(new URL('../dist/guidance-bundle.json',import.meta.url),'utf8'));
 for(const cap of capabilities)for(const c of cap.cases){
  const wrapper={ref:'guidance/'+c.id,format:'example',data:c,page_complete:true,next_offset:null};
  assert.ok(JSON.stringify(wrapper).length<=2400,c.id);
  const stamp=bundle.capabilities[cap.id];assert.ok(stamp);
  if(stamp.inline===c.id)assert.ok(enc.encode(JSON.stringify(c),[],[]).length<=160,c.id);
 }
 const broken=structuredClone(capabilities);broken[0].cases[0].stop='x'.repeat(2400);
 assert.throws(()=>validateCatalog(broken),/2400|budget/);
});
test('source contract drift requires explicit revalidation, never automatic blessing',async()=>{
 const mod=await load();assert.equal(typeof mod.validateSourceContract,'function');
 assert.doesNotThrow(()=>mod.validateSourceContract({fingerprint:'reviewed'},{fingerprint:'reviewed'}));
 assert.throws(()=>mod.validateSourceContract({fingerprint:'reviewed'},{fingerprint:'changed'}),/revalidat/i);
});
