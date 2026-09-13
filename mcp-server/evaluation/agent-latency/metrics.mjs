export function schedule(){
 const rows=[];
 for(let scenario=1;scenario<=6;scenario++)for(let repetition=1;repetition<=2;repetition++){
  const variants=(scenario+repetition)%2===0?['baseline','candidate']:['candidate','baseline'];
  for(const variant of variants)rows.push({scenario,repetition,variant});
 }
 return rows;
}
export class Budget {
 constructor(settings,start){
  if(!settings.model||!settings.effort)throw new Error('Verified task model and effort required.');
  this.deadline=start+1200000;this.starts=0;
 }
 start(now){
  if(this.starts>=24||now>=this.deadline)throw new Error('Evaluation budget exhausted.');
  this.starts++;return Math.min(90000,this.deadline-now);
 }
}
export const median=xs=>{
 if(!xs.length)return null;
 const a=[...xs].sort((a,b)=>a-b),i=Math.floor(a.length/2);
 return a.length%2?a[i]:(a[i-1]+a[i])/2;
};
const p90=xs=>xs.length?[...xs].sort((a,b)=>a-b)[Math.max(0,Math.ceil(xs.length*0.9)-1)]:null;
export function metrics(events,source='mcp_proxy'){
 const pending=new Map(),gaps_ms=[],server_ms=[],seen=new Set(),actionIds=new Set();let last=null,calls=0,duplicates=0,errors=0,invalid_arguments=0,retries=0,stale_rejections=0;
 for(const e of events){
  if(e.kind==='request'){
   if(pending.size===0&&last!==null&&e.at>=last)gaps_ms.push(e.at-last);
   last=null;pending.set(e.id,e);calls++;
   if(e.name==='sts_get_context'){
    const key=JSON.stringify(e.args);if(seen.has(key))duplicates++;seen.add(key);
   }
   if(e.name==='sts_act'&&e.args?.request_id){if(actionIds.has(e.args.request_id))retries++;actionIds.add(e.args.request_id);}
  }else if(e.kind==='response'&&pending.has(e.id)){
   server_ms.push(e.at-pending.get(e.id).at);pending.delete(e.id);
   if(e.error){errors++;const text=JSON.stringify(e.data??'');if(/Invalid arguments|invalid_type|invalid_value|Arguments too large/.test(text))invalid_arguments++;if(/Stale state/.test(text))stale_rejections++;}
   if(pending.size===0)last=e.at;
  }
 }
 return {source,gaps_ms,server_ms,calls,duplicates,errors,invalid_arguments,retries,stale_rejections};
}
const valid=n=>typeof n==='number'&&Number.isFinite(n)&&n>=0;
export function assess(rows){
 const reasons=[],expected=schedule(),key=x=>`${x.scenario}/${x.repetition}/${x.variant}`;
 if(rows.length!==24||new Set(rows.map(key)).size!==24||expected.some(x=>!rows.some(r=>key(x)===key(r))))reasons.push('incomplete_samples');
 if(rows.some(r=>r.correct!==true||r.safe!==true||r.status!=='completed'))reasons.push('correctness_or_safety');
 if(rows.some(r=>r.cleanup_verified!==true))reasons.push('cleanup_unverified');
 if(rows.some(r=>!valid(r.tokens)||!valid(r.total_ms)||!r.gaps_ms?.length||r.gaps_ms.some(n=>!valid(n))))reasons.push('missing_metrics');
 if(new Set(rows.map(r=>r.source)).size!==1||rows.some(r=>!['host_event','mcp_proxy'].includes(r.source)))reasons.push('mixed_or_unknown_clock');
 const groups={};
 for(const variant of ['baseline','candidate']){
  const rs=rows.filter(r=>r.variant===variant);
  const gaps=rs.flatMap(r=>r.gaps_ms??[]);
  // Each matched task has equal weight even when optimization removes extra calls.
  groups[variant]={gap_median_ms:median(rs.map(r=>median(r.gaps_ms??[])).filter(valid)),pooled_gap_p90_ms:p90(gaps),gap_samples:gaps.length,total_median_ms:median(rs.map(r=>r.total_ms).filter(valid))};
 }
 const {baseline:b,candidate:c}=groups;
 if(!(b.gap_median_ms>0&&c.gap_median_ms!==null&&c.gap_median_ms<=b.gap_median_ms*0.8))reasons.push('gap_reduction_below_20_percent');
 if(c.total_median_ms===null||b.total_median_ms===null||c.total_median_ms>b.total_median_ms)reasons.push('overall_time_regression');
 const scenarios=[];
 for(let scenario=1;scenario<=6;scenario++){
  const base=rows.filter(r=>r.scenario===scenario&&r.variant==='baseline'),next=rows.filter(r=>r.scenario===scenario&&r.variant==='candidate');
  const a=median(base.map(r=>r.total_ms).filter(valid)),z=median(next.map(r=>r.total_ms).filter(valid));
  const tokenBase=median(base.map(r=>r.tokens).filter(valid)),tokenNext=median(next.map(r=>r.tokens).filter(valid));
  if(a===null||z===null||z>a*1.1)reasons.push(`scenario_${scenario}_time`);
  // Also protect every matched repetition from a token regression masked by a median.
  if(base.some(r=>{const n=next.find(n=>n.repetition===r.repetition);return !n||!valid(n.tokens)||!valid(r.tokens)||n.tokens>r.tokens*1.15;}))reasons.push(`scenario_${scenario}_tokens`);
  scenarios.push({scenario,baseline_ms:a,candidate_ms:z,baseline_tokens:tokenBase,candidate_tokens:tokenNext});
 }
 return {accepted:reasons.length===0,reasons,groups,scenarios,source:rows[0]?.source??null,measurement_note:'Host event delivery or MCP round-trip proxy; neither is pure model inference time. p90 is descriptive only.'};
}
