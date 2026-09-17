// Explicit launch verification, not a strategy client. Never resumes or makes a game choice.
import assert from 'node:assert/strict';import path from 'node:path';
import {performance} from 'node:perf_hooks';import {setTimeout as delay} from 'node:timers/promises';
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {execFile} from 'node:child_process';import {promisify} from 'node:util';
if(!process.argv[2])throw new Error('Pass the exact installed plugin cache directory.');
const client=new Client({name:'downfall-plugin-launch-verifier',version:'1'});
await client.connect(new StdioClientTransport({command:process.execPath,args:[path.resolve(process.argv[2],'scripts','start.mjs')],stderr:'inherit'}));
async function call(name,args={}){const result=await client.callTool({name,arguments:args},undefined,{timeout:90000});if(result.isError)throw new Error(JSON.stringify(result.content));return result.structuredContent.data;}
try {
 const tools=(await client.listTools()).tools.map(t=>t.name);assert.equal(tools.length,6);
 const before=await call('sts_game_status');console.log(JSON.stringify({step:'before',...before}));
 const startedAt=performance.now();const start=await call('sts_start_game',{wait_ms:15000});
 assert.ok(['connected','starting'].includes(start.phase));console.log(JSON.stringify({step:'launched',phase:start.phase,pid:start.pid,elapsed_ms:performance.now()-startedAt}));
 let state=start.state;const deadline=Date.now()+120000;
 while((!state || state.ready!==true) && Date.now()<deadline){await delay(1000);try{state=await call('sts_get_state',{wait_ms:1000});}catch{/* only startup observation discovery is retried, never game actions */}}
 assert.equal(state?.ready,true,'Game did not provide a ready state');
 assert.equal(state.runtime.test_submissions,'disabled_in_test_copy');
 const again=await call('sts_start_game',{wait_ms:1000});assert.equal(again.pid,start.pid,'Duplicate launch');assert.equal(again.phase,'connected');
 const status=await call('sts_game_status');assert.equal(status.pid,start.pid);assert.equal(status.phase,'running');
 // A ready MCP menu alone does not prove that the interactive game is visible.
 assert.ok(Number.isSafeInteger(start.pid)&&start.pid>0);
 const {stdout}=await promisify(execFile)('pwsh.exe',['-NoProfile','-NonInteractive','-Command',
  `$gameProcess=Get-Process -Id ${start.pid} -ErrorAction Stop\n@{handle=$gameProcess.MainWindowHandle.ToInt64();title=$gameProcess.MainWindowTitle}|ConvertTo-Json -Compress`],{windowsHide:true,timeout:10000,encoding:'utf8'});
 const window=JSON.parse(stdout.trim().replace(/^\uFEFF/,''));
 assert.ok(window.handle>0,'Test game has no visible main window');assert.match(window.title,/Slay the Spire/,'Main window is not the game');
 console.log(JSON.stringify({step:'PASS',pid:start.pid,window,session_id:state.session_id,state_id:state.state_id,screen:state.screen,language:state.runtime.language,build:state.runtime.build,test_submissions:state.runtime.test_submissions,tools,reused_same_pid:true,gameplay_actions:0,elapsed_ms:performance.now()-startedAt}));
} finally {await client.close();}
