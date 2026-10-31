// Read-only installed-bundle/schema check. Never launches or plays the game.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';

if(!process.argv[2])throw new Error('Pass the exact installed plugin cache directory.');
const cache=path.resolve(process.argv[2]);
for(const file of ['index.js','view.js','context.js'])assert.deepEqual(
 await readFile(path.join(cache,'server',file)),await readFile(new URL(`../dist/${file}`,import.meta.url)),`Stale installed ${file}`);
const client=new Client({name:'map-planner-install-verifier',version:'1'});
try {
 await client.connect(new StdioClientTransport({command:process.execPath,args:[path.join(cache,'scripts','start.mjs')],stderr:'inherit'}));
 const {tools}=await client.listTools();assert.equal(tools.length,6);
 const state=tools.find(t=>t.name==='sts_get_state'),context=tools.find(t=>t.name==='sts_get_context');
 assert.ok(state.inputSchema.properties.view.enum.includes('map_plan'));
 assert.ok(context.inputSchema.properties.refs);
 const status=await client.callTool({name:'sts_game_status',arguments:{}},undefined,{timeout:20000});
 assert.ok(!status.isError,JSON.stringify(status.content));
 console.log(JSON.stringify({phase:'installed_map_planner_verified',matching_server_bundle:true,cheap_map_view:true,map_plan_context:true,game_phase:status.structuredContent.data.phase,gameplay_actions:0}));
} finally {await client.close();}
