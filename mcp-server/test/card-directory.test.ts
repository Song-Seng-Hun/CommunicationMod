import test from 'node:test';
import assert from 'node:assert/strict';
import {decision,readContext} from '../dist/context.js';
import type {Wire} from '../evaluation/wire.ts';

const bytes=(value:Wire)=>Buffer.byteLength(JSON.stringify(value),'utf8');
const card=(i=0):Wire=>({
 id:'guardian-'+i,uuid:'owned-'+i,name:'Socket card '+i,cost:i,type:'SKILL',rarity:'COMMON',
 upgrades:0,has_target:false,is_playable:true,exhausts:false,ethereal:false,
 displayed_cost_text:String(i),displayed_cost_complete:false,cost_components_complete:false,
 description:'Native text 🔥 '.repeat(240),description_complete:true,
 tooltips:[{title:'Socket',description:'Keep every keyword. '.repeat(160)}],
 base_damage:0,damage:0,base_block:5,block:5,base_magic_number:2,magic_number:2,
 socket_capacity:2,sockets:['RED','BLUE'],unknown_origin:{id:'Unknown',name:'Observed origin'},
 cost_components:[{resource:'energy_or_reserves',displayed_amount:i,kind:'fixed'}],
 cost_components_unavailable_reason:'unrendered_preview',
 upgrade_preview:{status:'available',after:{description:'Upgraded text 🔥 '.repeat(220),socket_capacity:3}}
});
const state=(cards:Wire):Wire=>({session_id:'card-directory',state_id:1,ready:true,actions:[],
 observation:{game_state:{screen_type:'NONE',deck:cards,combat_state:{hand_complete:true,hand:cards}}}});

// Traverse only advertised child refs and continuation offsets, never guessed fields.
function recover(s:Wire,ref:Wire,first:Wire,seen=new Set()):Wire {
 let data:Wire={},items=[],text='',kind='object',offset=0,pages=0;
 for(;;){
  assert.ok(++pages<100,'pagination must terminate');
  const page=first??(readContext(s,[ref],offset,5) as Wire).fragments[0];first=undefined;
  assert.ok(bytes(page)<=2400,'single fragment byte budget');
  if(Object.hasOwn(page,'value'))return page.value;
  if(Object.hasOwn(page,'text')){kind='text';text+=page.text;}
  else if(Object.hasOwn(page,'items')){kind='array';items.push(...page.items);}
  else{
   Object.assign(data,page.data??{});if(page.total!==undefined)kind='array';
   for(const entry of page.toc??[]){
    seen.add(entry.ref);const value=recover(s,entry.ref,undefined,seen);
    if(kind==='array')items.push(value);else data[decodeURIComponent(entry.ref.split('/').at(-1))]=value;
   }
  }
  if(page.next_offset===undefined)break;
  assert.ok(page.next_offset>offset,'continuation must advance');offset=page.next_offset;
 }
 return kind==='text'?text:kind==='array'?items:data;
}

for(const count of [1,4,8])test(`${count} oversized card(s) retain bounded directories and exact discoverable details`,()=>{
 const cards=Array.from({length:count},(_,i)=>card(i)),s=state(cards),before=JSON.stringify(s);
 const refs=(decision(s) as Wire).combat.hand.map((row:Wire)=>row.ref),result:Wire=readContext(s,refs,0,20);
 assert.equal(refs.length,count);assert.ok(bytes(result)<=3700,'shared response budget');
 for(const [i,page] of result.fragments.entries()){
  assert.ok(bytes(page)<=Math.min(2400,Math.floor(3600/count)),'whole fragment budget including cursor');
  assert.ok(page.toc?.length||page.next_offset>0,'oversized card must advertise children or a continuation');
  assert.ok(page.next_offset>0,'remaining card fields must have a continuation');
  const seen=new Set();assert.deepEqual(recover(s,page.ref,page,seen),cards[i]);
  for(const field of ['description','tooltips','sockets','unknown_origin','cost_components','upgrade_preview'])
   assert.ok(seen.has(page.ref+'/'+field),field+' must be discovered');
  assert.ok(seen.has(page.ref+'/upgrade_preview/after/description'),'nested upgrade text must be discovered');
 }
 assert.equal(JSON.stringify(s),before,'projection must not mutate the snapshot');
});

test('directory pages accept the exact byte boundary and defer a one-byte overflow',()=>{
 const budget=450,ref='hand/0',data={name:'Boundary',first:'x'.repeat(120),second:''};
 const expected={ref,details_required:true,next_offset:5,data,toc:[{ref:ref+'/description',count:2500},{ref:ref+'/sockets',count:1}]};
 data.second='y'.repeat(budget-bytes(expected));
 assert.ok(data.second.length<=240);assert.equal(bytes(expected),budget);
 for(const extra of [0,1]){
  const c={...data,second:data.second+'y'.repeat(extra),description:'d'.repeat(2500),sockets:['RED'],unknown_origin:{id:'Unknown'}};
  const s=state(Array.from({length:8},()=>structuredClone(c)));
  const refs=(readContext(s,['hand']) as Wire).fragments[0].toc.map((row:Wire)=>row.ref);
  const result:Wire=readContext(s,refs,0,5),page=result.fragments[0];
  for(const fragment of result.fragments)assert.ok(bytes(fragment)<=budget,'cursor must fit inside the budget');
  if(extra===0)assert.deepEqual(JSON.parse(JSON.stringify(page)),expected);
  else assert.equal(page.next_offset,4,'the overflowing sockets entry belongs on the next page');
  const seen=new Set();assert.deepEqual(recover(s,ref,page,seen),c);
  for(const key of ['description','sockets','unknown_origin'])assert.ok(seen.has(ref+'/'+key));
 }
});

test('every atomic card location falls back to a discoverable directory',()=>{
 const c=card(),s=state([c]),g:Wire=s.observation.game_state;
 g.screen_state={cards:[c]};g.combat_state.card_in_play=c;
 g.combat_state.player={mechanics:{collection:{cards:[c]},combat_collection:{cards:[c]}}};
 for(const ref of ['hand/0','deck/0','screen/cards/0','collection/cards/0','combat_collection/cards/0','card_in_play']){
  const page=(readContext(s,[ref]) as Wire).fragments[0];assert.ok(page.toc?.length,ref);
  assert.deepEqual(recover(s,ref,page),c,ref);
 }
});

test('small cards remain exact atomic fragments alone and in a batch',()=>{
 const cards=[{name:'Small',cost:0,description:'Block 5.',sockets:['RED']},
  {name:'Other',cost:1,upgrade_preview:{after:{description:'Block 8.'}}}],s=state(cards);
 for(const refs of [['hand/0'],['hand/0','hand/1']]){
  const result=readContext(s,refs);
  assert.deepEqual(result,{fragments:refs.map((ref,i)=>({ref,data:cards[i]}))});
 }
});

test('directory fallback does not expose hidden hands or noncurrent roots',()=>{
 const s:Wire=state([card()]);s.observation.game_state.combat_state.hand_complete=false;
 s.observation.combat_decision={mode:'selection',hand_complete:false};
 assert.deepEqual((decision(s) as Wire).combat.hand,[]);
 for(const ref of ['hand','hand/0','hand/0/sockets','full','mechanics/ghostflames','deck/0/__proto__'])
  assert.throws(()=>readContext(s,[ref]),/Reference not available/);
 s.observation.game_state.screen_type='EVENT';
 s.observation.game_state.combat_state.player={mechanics:{combat_collection:{cards:[card()]}}};
 assert.throws(()=>readContext(s,['combat_collection/cards/0']),/Reference not available/);
});
