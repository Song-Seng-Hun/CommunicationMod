// Read-only registration check, independent of whether the game is running.
import assert from 'node:assert/strict';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';

const client = new Client({name:'downfall-chat-entry-verifier', version:'1'});
const entry = fileURLToPath(new URL('./chat-entry.mjs', import.meta.url));
try {
  // Emulate a tunnel daemon whose cwd is unrelated to the checkout.
  await client.connect(new StdioClientTransport({command:process.execPath, args:[entry], cwd:path.parse(entry).root, stderr:'inherit'}));
  const {tools} = await client.listTools();
  assert.deepEqual(tools.map(t => t.name).sort(), ['sts_game_status','sts_start_game','sts_get_state','sts_act','sts_get_context','sts_get_request'].sort());
  for (const tool of tools) {
    assert.ok(tool.outputSchema, `Missing output schema: ${tool.name}`);
    for (const hint of ['readOnlyHint','destructiveHint','openWorldHint']) assert.equal(typeof tool.annotations[hint], 'boolean');
  }
  const result = await client.callTool({name:'sts_game_status', arguments:{}}, undefined, {timeout:15000});
  assert.ok(!result.isError, JSON.stringify(result.content));
  console.log(JSON.stringify({phase:'local_mcp_verified', tools:tools.map(t=>t.name), game_phase:result.structuredContent.data.phase, gameplay_actions:0, chat_registration_verified:false}));
} finally {await client.close();}
