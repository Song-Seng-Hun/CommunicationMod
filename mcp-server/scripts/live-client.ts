// Manual verification client: one JSON line = one MCP call. No gameplay decisions/retries.
import {Client} from '@modelcontextprotocol/sdk/client/index.js';
import {StdioClientTransport} from '@modelcontextprotocol/sdk/client/stdio.js';
import {createInterface} from 'node:readline';
import {fileURLToPath} from 'node:url';
import {performance} from 'node:perf_hooks';
import path from 'node:path';
const client=new Client({name:'communicationmod-manual-verifier',version:'1.0.0'});
const entry=process.argv[2]?path.resolve(process.argv[2],'scripts','start.mjs'):fileURLToPath(new URL('../dist/index.js',import.meta.url));
await client.connect(new StdioClientTransport({command:process.execPath,args:[entry],stderr:'inherit'}));
console.log(JSON.stringify({tools:(await client.listTools()).tools.map(t=>t.name)}));
try {
 for await(const line of createInterface({input:process.stdin,crlfDelay:Infinity})) {
  if(!line.trim())continue;
  try {
   const request=JSON.parse(line);
   if(request.benchmark===true) {
    const times=[];let result;
    for(let i=0;i<50;i++){const start=performance.now();result=await client.callTool({name:'sts_get_state',arguments:{}});if(result.isError)throw new Error(JSON.stringify(result));times.push(performance.now()-start);}
    times.sort((a,b)=>a-b);const focused=result.structuredContent.data;
    const full=await client.callTool({name:'sts_get_context',arguments:{session_id:focused.session_id,state_id:focused.state_id,section:'full'}});
    console.log(JSON.stringify({benchmark:{samples:50,median_ms:times[25],p95_ms:times[47],focused_bytes:Buffer.byteLength(JSON.stringify(focused)),full_observation_bytes:Buffer.byteLength(JSON.stringify(full.structuredContent?.data.data)),state_id:focused.state_id}}));
   } else {
    const start=performance.now();const result=await client.callTool(request);
    console.log(JSON.stringify({elapsed_ms:performance.now()-start,result:result.structuredContent?.data ?? result}));
   }
  }catch(e){console.log(JSON.stringify({error:String(e)}));}
 }
}finally{await client.close();}
