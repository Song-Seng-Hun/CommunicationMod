import assert from 'node:assert/strict';
import {performance} from 'node:perf_hooks';
import {mkdir,writeFile} from 'node:fs/promises';
import * as before from '../evaluation/baseline/context-v3.mjs';
import * as after from '../dist/context.js';
import {performanceState,countedArray} from '../evaluation/performance-fixtures.mjs';

const cases=[];
for(const size of [30,10000]){
 const page=performanceState(size);page.observation.game_state.narrative.entries=[];
 cases.push({name:`array_page_${size}`,run:api=>api.readContext(page,['collection/cards'],size-3,3)});
 const map=performanceState(size),known=before.conditionalDecision(map,undefined,'map_plan').view_id;
 cases.push({name:`map_${size}`,run:api=>api.conditionalDecision(map,undefined,'map_plan')});
 cases.push({name:`map_unchanged_${size}`,run:api=>api.conditionalDecision(map,known,'map_plan')});
 cases.push({name:`decision_${size}`,run:api=>api.conditionalDecision(map)});
}
const batch=100,samples=11,warmup=2000;let sink=0;
const measure=(scenario,api)=>{const start=performance.now();for(let i=0;i<batch;i++){
 const result=scenario.run(api);sink+=result.state_id??0;
}return (performance.now()-start)*1000/batch;};
const stats=values=>{const sorted=[...values].sort((a,b)=>a-b);return {median_batch_mean_us:+sorted[Math.floor(sorted.length/2)].toFixed(2),p95_batch_mean_us:+sorted[Math.ceil(sorted.length*0.95)-1].toFixed(2)};};
const rows=cases.map(scenario=>{
 assert.equal(JSON.stringify(scenario.run(after)),JSON.stringify(scenario.run(before)),scenario.name);
 for(let i=0;i<warmup;i++){scenario.run(before);scenario.run(after);}
 const baseline=[],current=[];
 for(let i=0;i<samples;i++)if(i%2){current.push(measure(scenario,after));baseline.push(measure(scenario,before));}
 else{baseline.push(measure(scenario,before));current.push(measure(scenario,after));}
 const old=stats(baseline),next=stats(current);
 return {scenario:scenario.name,before:old,after:next,median_speedup:+(old.median_batch_mean_us/next.median_batch_mean_us).toFixed(2)};
});
const counts=api=>{
 const s=performanceState(),cards=countedArray(s.observation.game_state.mechanics.collection.cards),entries=countedArray(s.observation.game_state.narrative.entries);
 s.observation.game_state.mechanics.collection.cards=cards.array;s.observation.game_state.narrative.entries=entries.array;
 api.readContext(s,['collection/cards'],9990,3);const page_reads=cards.reads,priorNarrativeReads=entries.reads;
 api.conditionalDecision(s,undefined,'map_plan');return {array_page_element_reads:page_reads,map_narrative_element_reads:entries.reads-priorNarrativeReads};
};
const report={measurement:'Local synthetic in-process microbenchmark; NOT game FPS, MCP transport latency or billed-token savings',node:process.version,
 method:{warmup_per_implementation:warmup,batch_size:batch,alternating_samples:samples,statistic:'median/p95 of batch mean microseconds per call; no timing test threshold'},
 rows,deterministic_reads:{before:counts(before),after:counts(after)},exact_output_checks:'passed',gameplay_actions:0};
assert.equal(report.deterministic_reads.after.array_page_element_reads,3);
assert.equal(report.deterministic_reads.after.map_narrative_element_reads,0);
await mkdir(new URL('../../target/',import.meta.url),{recursive:true});
await writeFile(new URL('../../target/context-performance-report.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));assert.ok(sink>0);
