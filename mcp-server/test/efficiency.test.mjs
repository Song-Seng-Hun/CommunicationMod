import test from 'node:test';import assert from 'node:assert/strict';
const load=async()=>{const m=await import('../scripts/measure-agent-efficiency.mjs').catch(()=>null);assert.ok(m,'efficiency report implementation required');return m;};
test('synthetic MCP wait comparison accounts for roundtrips and optional metrics',async()=>{
 const {probeWait}=await load();
 const off=await probeWait({durationMs:180,pollMs:30,mode:'event',metrics:false});
 const on=await probeWait({durationMs:180,pollMs:30,mode:'event',metrics:true});
 assert.equal(off.wait_calls,1);assert.equal(on.wait_calls,1);assert.equal(off.final_state,2);assert.equal(on.final_state,2);
 assert.equal(off.rows.length,0);assert.equal(off.metrics_directory_exists,false);
 assert.ok(on.rows.some(r=>r.wait_ms>=100));assert.equal(off.dispatches,0);assert.equal(on.dispatches,0);
 assert.ok(off.catalog.length===6&&on.catalog.length===6);
});
test('processing microbenchmark separates optional logger work from flush and network waiting',async()=>{
 const {processingCost}=await load();assert.equal(typeof processingCost,'function');
 const rows=await processingCost();assert.deepEqual(rows.map(r=>r.metrics),[false,true]);
 for(const row of rows){assert.equal(row.samples_ms.length,5);assert.ok(row.median_per_call_ms>=0);assert.ok(row.flush_ms>=0);}
});
test('report fingerprints include the generator, runtime, skill and reviewed contract',async()=>{
 const {artifactHashes}=await load();assert.equal(typeof artifactHashes,'function');
 const hashes=await artifactHashes();
 for(const file of ['mcp-server/src/session.ts','mcp-server/dist/index.js','mcp-server/scripts/measure-agent-efficiency.mjs','mcp-server/hooks/session-start.mjs','mcp-server/evaluation/guidance-contract.json','plugin/downfall-agent/skills/downfall-play/SKILL.md'])assert.match(hashes[file],/^[a-f0-9]{64}$/);
});
