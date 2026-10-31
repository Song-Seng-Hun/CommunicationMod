// Read-only verification of the installed plugin; never launches or acts in the game.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';

if(!process.argv[2])throw new Error('Pass the exact installed plugin cache directory.');
const cache=path.resolve(process.argv[2]);
for(const file of ['index.js','view.js','context.js','lifecycle.js'])assert.deepEqual(
 await readFile(path.join(cache,'server',file)),await readFile(new URL(`../dist/${file}`,import.meta.url)),`Stale installed ${file}`);
const {focus,section}=await import(pathToFileURL(path.join(cache,'server','view.js')).href);
const {conditionalDecision,readContext}=await import(pathToFileURL(path.join(cache,'server','context.js')).href);
const comparison={status:'available',scope:'next_standard_upgrade',after:{upgrades:4,base_damage:34},can_upgrade_after:true};
const output=focus({observation:{game_state:{screen_type:'EVENT',screen_state:{event_reading:{body_text:'fixture'},options:[{choice_index:0,disabled:false,card_preview:{upgrade_preview:comparison}}]}}}});
assert.deepEqual(output.screen_state.option_card_previews[0].card_preview.upgrade_preview,comparison);
const controls={potion_controls:[{slot:0,can_use:true}],shop_controls:[{id:'run.shop.purge',price:75}],rest_controls:[{available:false}],selection_controls:{upgrade_choice:{selected_after:{description:'선택한 강화'}}},reward_navigation:{unclaimed_rewards:2,may_leave_unclaimed_rewards:true}};
const decision=focus({ready:true,observation:{...controls,game_state:{}}});
for(const [key,value] of Object.entries(controls))assert.deepEqual(decision[key],value,`Missing installed control: ${key}`);
const mechanics={reserves:4,essence:9,collection:{count:1,order_visible:false,cards:[{id:'public-collection-card'}]}};
const resourceState={observation:{game_state:{mechanics}}};
assert.equal(focus(resourceState).player.mechanics.reserves,4);
assert.equal(focus(resourceState).player.mechanics.collection.cards,undefined);
assert.deepEqual(section(resourceState,'mechanics',0,30).data.collection.cards,mechanics.collection.cards);
const large={session_id:'verify',state_id:12,observation:{game_state:{mechanics:{panel_bindings_complete:true,information_complete:false,character_specific_complete:false,collection:{count:135,cards_complete:true,order_visible:false,cards:Array.from({length:135},(_,i)=>({id:`card-${i}`,displayed_cost_complete:false}))}}}}};
assert.equal(section(large,'mechanics',100,30).data.collection.next_offset,130);
const tail=section(large,'collection',130,30);assert.equal(tail.items.length,5);assert.equal(tail.items[4].id,'card-134');assert.equal(tail.next_offset,null);
assert.equal(focus(large).player.mechanics.information_complete,false);
const small=conditionalDecision(large);assert.ok(small.toc.some(x=>x.ref==='collection'));
assert.equal(conditionalDecision(large,small.view_id).unchanged,true);
assert.equal(readContext(large,['collection/cards/134']).fragments[0].data.id,'card-134');
assert.throws(()=>readContext(large,['full']),/not available/);
const client=new Client({name:'card-upgrade-install-verifier',version:'1'});
try {
 await client.connect(new StdioClientTransport({command:process.execPath,args:[path.join(cache,'scripts','start.mjs')],stderr:'inherit'}));
 const {tools}=await client.listTools();assert.equal(tools.length,6);
 assert.match(tools.find(t=>t.name==='sts_get_state').description,/known_view/);
 assert.ok(tools.find(t=>t.name==='sts_get_context').inputSchema.properties.refs);
 assert.equal(tools.find(t=>t.name==='sts_get_context').inputSchema.properties.section,undefined);
 assert.match(tools.find(t=>t.name==='sts_act').description,/never auto-discard/);
 assert.match(tools.find(t=>t.name==='sts_get_context').description,/No full dump/);
 const status=await client.callTool({name:'sts_game_status',arguments:{}},undefined,{timeout:20000});
 assert.ok(!status.isError,JSON.stringify(status.content));
 console.log(JSON.stringify({phase:'installed_context_economy_verified',matching_server_bundle:true,scoped_fragments:true,conditional_reads:true,legacy_projection_regressions:true,collection_pagination:true,completeness_flags_preserved:true,game_phase:status.structuredContent.data.phase,gameplay_actions:0}));
} finally {await client.close();}
