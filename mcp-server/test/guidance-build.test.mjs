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
 const bogus={tool:'sts_act',arguments:{session_id:{$bind:'s'},state_id:{$bind:'n'},action_id:{$bind:'a'},arguments:{unknown_dynamic:{$bind:'bad'}}}};
 assert.throws(()=>bindCall(bogus,{...bindings,a:{type:'string',source:'current.offered_action.id'},bad:{type:'string',source:'current.NONEXISTENT.value'}},{a:'run.x',bad:'m'}),/source/);
});

test('public guidance calls omit pinned event/map constants and dead bindings',async()=>{
 const {bindCall}=await load(),action={type:'string',source:'current.offered_action.id'};
 const map={tool:'sts_act',arguments:{session_id:{$bind:'s'},state_id:{$bind:'n'},action_id:{$bind:'a'},arguments:{map_id:{$bind:'map'},revision:{$bind:'rev'},nodes:{$bind:'nodes'}}}};
 const mapBindings={s:{type:'string',source:'current.session_id'},n:{type:'number',source:'current.state_id'},a:action,map:{type:'string',source:'current.offered_action.parameters.properties.map_id.const'},rev:{type:'number',source:'current.offered_action.parameters.properties.revision.const'},nodes:{type:'array',source:'agent.selected_node_ids_from_current.observed_map_validated_against_drawn_edges_and_offered_route_schema'}};
 assert.deepEqual(bindCall(map,mapBindings,{a:'run.map.plan',nodes:['1,2','2,3']}),{action_id:'run.map.plan',arguments:{nodes:['1,2','2,3']}});
 const event={tool:'sts_act',arguments:{action_id:{$bind:'a'},arguments:{reading_id:{$bind:'reading'},commentary:{$bind:'commentary'}}}};
 const eventBindings={a:action,reading:{type:'string',source:'current.event_reading.reading_id'},commentary:{type:'string',source:'agent.commentary_already_presented_to_user_for_current.event_reading.reading_id'}};
 assert.deepEqual(bindCall(event,eventBindings,{a:'acknowledge_event_reading',commentary:'현재 이벤트를 설명함'}),{action_id:'acknowledge_event_reading',arguments:{commentary:'현재 이벤트를 설명함'}});
 const {capabilities}=await import('../dist/guidance-catalog.js'),{exampleFragment}=await import('../dist/guidance.js');
 for(const id of ['event.ack','map.plan']){
  const cap=capabilities.find(x=>x.id===id),raw=cap.cases.find(x=>x.case==='normal'),shown=exampleFragment(raw),text=JSON.stringify(shown);
  assert.ok(!text.includes('reading_id'),id);assert.ok(!text.includes('map_id'),id);assert.ok(!text.includes('current.offered_action.parameters.properties.revision.const'),id);
 }
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
