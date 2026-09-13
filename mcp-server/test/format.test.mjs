import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {decode} from '@toon-format/toon';
import {Tiktoken} from 'js-tiktoken/lite';
import ranks from 'js-tiktoken/ranks/o200k_base';
import {formatContext} from '../dist/format.js';
import {readContext} from '../dist/context.js';
import {performanceState} from '../evaluation/performance-fixtures.mjs';
import {workflows} from '../evaluation/economy-workflows.mjs';
import {data as readingData,expected} from '../evaluation/format-reading.mjs';

const tokenizer=new Tiktoken(ranks),tokens=text=>tokenizer.encode(text,[],[]).length;
const restore=text=>text.startsWith('TOON:')?decode(text.slice(text.indexOf('\n')+1),{strict:true}):JSON.parse(text);
const directory=()=>readContext(performanceState(40),['collection/cards'],0,20);

test('default and explicit json preserve the legacy dual representation',async()=>{
 const data=directory(),expected={content:[{type:'text',text:JSON.stringify(data)}],structuredContent:{data}};
 assert.deepEqual(await formatContext(data),expected);
 assert.deepEqual(await formatContext(data,'json'),expected);
});

test('uniform context directory uses smaller lossless labeled TOON without duplication',async()=>{
 const data=directory(),original=JSON.stringify(data),result=await formatContext(data,'compact');
 assert.deepEqual(Object.keys(result),['content']);assert.equal(result.content.length,1);
 const text=result.content[0].text;
 assert.match(text,/^TOON:.*\[N\].*fields.*rows/);
 assert.match(text,/toc\[20\]\{ref,title,count\}:/);
 assert.equal(JSON.stringify(restore(text)),original);
 assert.equal(JSON.stringify(data),original,'Input must not be mutated');
 assert.ok(tokens(text)<=tokens(original)*0.9);
 assert.ok(tokens(original)-tokens(text)>=16);
 assert.ok(tokens(JSON.stringify(result))<tokens(JSON.stringify({content:[{type:'text',text:original}]})));
});

test('small, non-tabular and oversized responses retain exact JSON text',async()=>{
 const cases=[{state_id:42,unchanged:true},{text:'사용 불가.\nDo not replay unknown outcomes.'.repeat(60)},
  {rows:[{id:'a'},{id:'b',complete:false},{id:'c'}]},
  {...directory(),text:'한'.repeat(70000)}];
 for(const data of cases){const result=await formatContext(data,'compact');assert.equal(result.structuredContent,undefined);assert.equal(result.content[0].text,JSON.stringify(data));}
});

test('types, strings, control characters, unusual keys and missing fields roundtrip',async()=>{
 const values=[null,'',false,true,0,-0,1e-7,1e21,Number.MAX_SAFE_INTEGER,'001','1e3','true','null','false',
  'a,b','a\tb','a\nb','a\r\nb','"quoted"','\\path',' padded ','한글😀','[DONE]','# comment','- item',
  '\u0000\u0001\u0008\u000b\u000c\u001f','<|endoftext|>','a: b','{id}', '```toon', '\ud800','\udfff'];
 for(const value of values){
  const data=directory();data.fragments[0].data=JSON.parse('{"__proto__":{"safe":true},"constructor":"constructor","a.b":"literal key","":"empty key"}');
  data.fragments[0].data.value=value;data.fragments[0].data.missing=undefined;
  const original=JSON.stringify(data),result=await formatContext(data,'compact');
  assert.equal(JSON.stringify(restore(result.content[0].text)),original,`Value ${JSON.stringify(value)}`);
  assert.equal(JSON.stringify(data),original);
 }
 assert.equal({}.safe,undefined);
});

test('generated row data preserve every primitive and never grow compact text',async()=>{
 let seed=74321;const next=()=>((seed=(Math.imul(seed,1664525)+1013904223)>>>0)/4294967296);
 const atoms=[null,true,false,0,8,-5,0.25,'','001','unknown','사용 불가','a,b','a\tb','a\nb','"x"','한글😀'];
 for(let i=0;i<100;i++){
  const rows=Array.from({length:3+Math.floor(next()*28)},(_,j)=>({ref:`hand/${j}`,label:atoms[Math.floor(next()*atoms.length)],complete:atoms[Math.floor(next()*atoms.length)]}));
  const data={session_id:'s-001',state_id:i,toc:rows,next_offset:i%2?null:30},json=JSON.stringify(data);
  const result=await formatContext(data,'compact');
  assert.equal(JSON.stringify(restore(result.content[0].text)),json);
  assert.ok(tokens(result.content[0].text)<=tokens(json));
 }
});

test('all existing required-fact workflows roundtrip with unchanged IDs, refs and pagination',async()=>{
 for(const workflow of workflows())for(const step of workflow.after){
  const result=await formatContext(step.response,'compact');
  assert.equal(JSON.stringify(restore(result.content[0].text)),JSON.stringify(step.response),workflow.name);
 }
});

test('reading-evaluation facts have independently checked types and safety answers',async()=>{
 const result=await formatContext(readingData,'compact');assert.match(result.content[0].text,/^TOON:/);
 const data=restore(result.content[0].text),rows=data.fragment.toc,card=n=>rows.find(row=>row.ref===`collection/cards/${n}`);
 const actual={q1:data.receipt.retry_allowed,q2:data.fragment.next_offset,q3:rows.at(-1).ref,
  q4:card(22).displayed_cost_text,q5:card(24).displayed_cost_complete&&card(24).displayed_cost_text==='0',
  q6:card(26).reason,q7:card(25).note,q8:card(27).note,q9:data.fragment.total-data.fragment.offset-rows.length,q10:data.session_id};
 assert.deepEqual(actual,expected);
});

test('unsupported UTF-16 retains exact JSON instead of accepting a damaged codec result',async()=>{
 for(const text of ['\ud800','\udfff','prefix\ud800tail']){
  const data=directory();data.fragments[0].text=text;
  assert.equal((await formatContext(data,'compact')).content[0].text,JSON.stringify(data));
 }
});

test('archived isolated JSON and TOON reading answers match the frozen truth',async()=>{
 const evidence=JSON.parse(await readFile(new URL('../evaluation/format-reading-results.json',import.meta.url),'utf8'));
 assert.deepEqual(evidence.results.map(row=>row.format),['json','compact']);
 for(const row of evidence.results)assert.deepEqual(row.answers,expected,row.format);
});

test('full selective workflow saves tokens including discovery, recovery detail, requests and catalog',async()=>{
 await promisify(execFile)(process.execPath,['scripts/measure-context-format.mjs'],{windowsHide:true,timeout:30000});
});
