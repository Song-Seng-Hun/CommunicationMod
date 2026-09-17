import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const load=async()=>{const value=await import('../scripts/build-guidance.mjs').catch(()=>null);assert.ok(value,'offline guidance validator required');return value;};

test('offline validator strips transport bindings and still validates live argument types',async()=>{
 const {bindCall}=await load();
 const c={tool:'sts_get_context',arguments:{session_id:{$bind:'s'},state_id:{$bind:'n'},refs:{$bind:'refs'}}};
 const bindings={s:{type:'string',source:'current.session_id'},n:{type:'number',source:'current.state_id'},refs:{type:'array',source:'current.observed_refs'}};
 assert.deepEqual(bindCall(c,bindings,{refs:['deck/0']}),{refs:['deck/0']});
 assert.throws(()=>bindCall(c,bindings,{refs:'deck/0'}),/type/);assert.throws(()=>bindCall(c,bindings,{}),/binding/);
 const bogus={tool:'sts_act',arguments:{session_id:{$bind:'s'},state_id:{$bind:'n'},action_id:{$bind:'a'},arguments:{map_id:{$bind:'map'}}}};
 assert.throws(()=>bindCall(bogus,{...bindings,a:{type:'string',source:'current.offered_action.id'},map:{type:'string',source:'current.NONEXISTENT.map_id'}},{a:'run.map.plan',map:'m'}),/source/);
});

test('every built example has a bounded public wrapper and validates against real tool schemas',async()=>{
 const {validateCatalog}=await load(),{capabilities}=await import('../dist/guidance-catalog.js');const report=validateCatalog(capabilities);
 assert.equal(report.capabilities.length,capabilities.length);
 const bundle=JSON.parse(await readFile(new URL('../dist/guidance-bundle.json',import.meta.url),'utf8'));assert.equal(bundle.version,2);
 for(const cap of capabilities){const stamp=bundle.capabilities[cap.id];assert.ok(stamp);assert.ok(Number.isInteger(stamp.public_tokens)&&stamp.public_tokens>0);}
 const broken=structuredClone(capabilities);broken[0].cases[0].stop='x'.repeat(3000);assert.throws(()=>validateCatalog(broken),/2400|budget/);
});

test('source contract requires explicit reviewed revision and stable source file set',async()=>{
 const mod=await load();assert.equal(typeof mod.validateSourceContract,'function');
 assert.doesNotThrow(()=>mod.validateSourceContract({revision:'r',files:5},{revision:'r',files:5,fingerprint:'diagnostic'}));
 assert.throws(()=>mod.validateSourceContract({revision:'r',files:5},{revision:'changed',files:5}),/revision/i);
 assert.throws(()=>mod.validateSourceContract({revision:'r',files:5},{revision:'r',files:6}),/file set/i);
});
