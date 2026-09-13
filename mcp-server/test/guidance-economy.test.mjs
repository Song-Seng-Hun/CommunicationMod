import test from 'node:test';import assert from 'node:assert/strict';
test('guidance cost accounting includes skill, catalog, requests, responses and extra reads',async()=>{
 const mod=await import('../scripts/measure-guidance.mjs').catch(()=>null);assert.ok(mod,'guidance workflow cost report required');
 const steps=[{request:{name:'sts_get_state',arguments:{}},response:{state_id:1}}];
 const a=mod.cost(steps,'skill','catalog');
 assert.ok(a>mod.cost(steps,'',''));assert.ok(mod.cost([...steps,...steps],'skill','catalog')>a);
});
