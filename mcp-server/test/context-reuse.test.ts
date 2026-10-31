import test from 'node:test';
import assert from 'node:assert/strict';
import {ContextReuse,restoreDelta} from '../dist/context-reuse.js';
import {toolSchemas} from '../dist/tool-schemas.js';
import type {Wire} from '../evaluation/wire.ts';

const state=(id=1):Wire=>({session_id:'s',state_id:id,view_id:`v${id}`,ready:true,connection:{connected:true,pending:null},screen:'COMBAT',player:{hp:50},hand:Array.from({length:20},(_,i)=>({id:`card${i}`,cost:i%3,description:`Deal ${i} damage. Never discard. `.repeat(8)})),mechanics:{active:true}});
test('delta restores all JSON types, removals and whole arrays; headers stay fresh',async()=>{
 const cache=new ContextReuse(),base=state(); assert.deepEqual(await cache.state(base),base);
 const next:Wire=state(2);next.player={hp:0,unknown:null,flag:false,'a/b~c':{incomplete:true}};delete next.mechanics;next.ready=false;next.connection.pending={request_id:'r',outcome:'unknown'};next.extra=[false,null,0];
 const delta:Wire=await cache.state(next,'decision','v1');assert.equal(delta.delta.base_view,'v1');assert.deepEqual(restoreDelta(base,delta),next);
 assert.equal(delta.ready,false);assert.deepEqual(delta.connection,next.connection);assert.ok(delta.delta.set.some(([p]:Wire)=>p==='/extra'));assert.ok(!delta.delta.set.some(([p]:Wire)=>p.startsWith('/extra/')));
 assert.throws(()=>restoreDelta(state(3),delta),/base/);
});
test('only full emissions are bases; expiry, screen/session changes and eviction recover full',async()=>{
 let now=0;const cache=new ContextReuse({now:()=>now,ttlMs:10,maxEntries:2});const a=state();await cache.state(a);
 const b=state(2);b.player.hp--;assert.ok((await cache.state(b,'decision','v1')).delta);
 assert.deepEqual(await cache.state(state(3),'decision','v2'),state(3));
 now=11;assert.deepEqual(await cache.state(b,'decision','v1'),b);
 assert.deepEqual(await cache.state({...b,screen:'MAP'},'decision','v2'),{...b,screen:'MAP'});
 assert.deepEqual(await cache.state({...b,session_id:'new'},'decision','v2'),{...b,session_id:'new'});
 assert.ok(cache.stats.entries<=2);
});
test('fragment reuse needs explicit retained full ID; page/session/content changes never hide facts',async()=>{
 const cache=new ContextReuse();const data={session_id:'s',state_id:1,fragments:[{ref:'event/body',text:'Never choose without permission. '.repeat(40),format:'text'}]};
 const first:Wire=await cache.context(data);const id=first.fragments[0].content_id;assert.ok(id);
 assert.equal((await cache.context(data) as Wire).fragments[0].text,data.fragments[0].text);
 const known={'event/body':id};const repeat:Wire=await cache.context({...data,state_id:2},known);assert.equal(repeat.fragments[0].unchanged,true);assert.equal(repeat.state_id,2);
 assert.ok(!(await cache.context(data,known,1) as Wire).fragments[0].unchanged);
 const changed:Wire=structuredClone(data);changed.fragments[0].incomplete=true;assert.ok(!(await cache.context(changed,known) as Wire).fragments[0].unchanged);
 assert.ok(!(await cache.context({...data,session_id:'other'},known) as Wire).fragments[0].unchanged);
});
test('small, oversize, hostile paths, compact and absent bases safely fall back',async()=>{
 const cache=new ContextReuse({maxBytes:20000});const base=state();await cache.state(base);
 const hostile=JSON.parse(JSON.stringify(state(2)));hostile.player=JSON.parse('{"__proto__":{"polluted":true}}');assert.deepEqual(await cache.state(hostile,'decision','v1'),hostile);assert.equal(({} as Wire).polluted,undefined);
 const tiny={session_id:'s',state_id:1,fragments:[{ref:'x',value:false}]};assert.deepEqual(await cache.context(tiny),tiny);
 const over={...tiny,fragments:[{ref:'x',text:'x'.repeat(2380)}]};assert.deepEqual(await cache.context(over),over);
 assert.deepEqual(await cache.state(state(9),'map_plan','v1'),state(9));
 assert.ok(cache.stats.bytes<=20000);
});
test('optional reuse schemas remain strict and bounded',()=>{
 assert.equal(toolSchemas.sts_get_state.parse({reuse:true}).reuse,true);
 assert.equal(toolSchemas.sts_act.parse({action_id:'end',reuse:true,known_view:'v1'}).known_view,'v1');
 assert.throws(()=>toolSchemas.sts_get_context.parse({refs:['x'],known:Object.fromEntries(Array.from({length:9},(_,i)=>[String(i),'id']))}));
 assert.throws(()=>toolSchemas.sts_get_state.parse({reuse:'yes'}));
});