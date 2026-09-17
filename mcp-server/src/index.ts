import {McpServer} from '@modelcontextprotocol/sdk/server/mcp.js';
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio.js';
import {randomUUID} from 'node:crypto';import {fileURLToPath} from 'node:url';import path from 'node:path';
import {toolSchemas} from './tool-schemas.js';
import {Connection} from './connection.js';import {obj,type Obj} from './view.js';
import {conditionalDecision,readContext} from './context.js';
import {formatContext,type ContextFormat} from './format.js';
import {Lifecycle} from './lifecycle.js';
import {createMetrics,type MetricsErrorClass} from './metrics.js';
const root=process.env.COMMUNICATIONMOD_WORKSPACE ?? path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const link=new Connection(root),server=new McpServer({name:'communicationmod-mcp-server',version:'0.1.0'});
const lifecycle=new Lifecycle(root,link);
const metrics=createMetrics(root);
const read={readOnlyHint:true,destructiveHint:false,idempotentHint:true,openWorldHint:false};
function textPreview(data:Obj):string {
 const raw=JSON.stringify(data);if(raw.length<=1800)return raw;
 const keys=['session_id','state_id','ready','screen','status','outcome','request_id','view_id','unchanged'];
 const preview:Obj=Object.fromEntries(keys.filter(k=>k in data).map(k=>[k,data[k]]));
 if(Array.isArray(data.actions))preview.action_count=data.actions.length;
 if(Array.isArray(data.toc))preview.toc_count=data.toc.length;
 preview.structured_result=true;preview.note='Full result is in structuredContent.data.';
 return JSON.stringify(preview);
}
function response(data:Obj){return {content:[{type:'text' as const,text:textPreview(data)}],structuredContent:{data}};}
type TimedWait=(run:()=>Promise<Obj>)=>Promise<Obj>;
async function guard(tool:string,run:(wait:TimedWait)=>Promise<Obj>,format:ContextFormat='json',lifecycleOnly=false,signal?:AbortSignal){
 const span=metrics.start(tool);let connect_ms=0,wait_ms=0,error_class:MetricsErrorClass='none',connecting=false;
 let result;
 const wait:TimedWait=async work=>{if(!span)return work();const start=performance.now();try{return await work();}finally{wait_ms+=performance.now()-start;}};
 try{
  if(!lifecycleOnly){connecting=true;const start=span?performance.now():0;try{await link.ensure();}finally{if(span)connect_ms=performance.now()-start;}connecting=false;}
  const data=await run(wait);result=format==='compact'?await formatContext(data,format):response(data);
 }catch(e){
  error_class=signal?.aborted || e instanceof Error&&e.name==='AbortError'?'cancelled':connecting?'connection':'operation';
  result={isError:true,content:[{type:'text' as const,text:e instanceof Error?e.message:lifecycleOnly?'Game startup failed; inspect sts_game_status before retrying.':'MCP operation failed; inspect current state before acting.'}]};
 }finally{
  if(span)try{metrics.finish(span,{connect_ms,wait_ms,error_class,response_bytes:Buffer.byteLength(JSON.stringify(result),'utf8')});}catch{ /* Metrics never changes a tool result. */ }
 }
 return result!;
}
server.registerTool('sts_game_status',{title:'Downfall status',description:"Test-game process/connection. No launch. No play.",inputSchema:toolSchemas.sts_game_status,annotations:read},
 ()=>guard('sts_game_status',()=>lifecycle.status(),'json',true));
server.registerTool('sts_start_game',{title:'Start Downfall',description:"Start/reuse verified test game only on user launch/play request. Never resumes runs; never chooses actions. Starting: inspect status; no relaunch.",inputSchema:toolSchemas.sts_start_game,annotations:{...read,readOnlyHint:false}},
 ({wait_ms})=>guard('sts_start_game',()=>lifecycle.start(wait_ms),'json',true));
server.registerTool('sts_get_state',{title:'Current decision',description:"Current decision + state-scoped toc. Read needed refs via sts_get_context before acting. details_required or incomplete: never guess. known_view=previous view_id: short unchanged reply. Omit to recover summary.",inputSchema:toolSchemas.sts_get_state,annotations:read},
 ({wait_ms,view,known_view},extra)=>guard('sts_get_state',async wait=>conditionalDecision(await wait(()=>link.game.wait(wait_ms,{signal:extra.signal,
  ...(known_view!==undefined?{changed:(state:Obj)=>conditionalDecision(state,undefined,view).view_id!==known_view}:{})})),known_view,view),'json',false,extra.signal));
server.registerTool('sts_act',{title:'Act + next decision',description:"One offered action; current session/state IDs. Read needed details first; never auto-discard. Unknown/applied_waiting: inspect state/request, never replay. Receipt + next decision.",
 inputSchema:toolSchemas.sts_act,annotations:{...read,readOnlyHint:false,destructiveHint:true,idempotentHint:false}},
 args=>guard('sts_act',async wait=>{if(JSON.stringify(args.arguments).length>8000)throw new Error('Arguments too large.');const result=await wait(()=>link.game.act({...args,request_id:args.request_id ?? randomUUID()},args.wait_ms));return {...result,state:conditionalDecision(obj(result.state))};}));
server.registerTool('sts_get_context',{title:'Public context fragments',description:"Read 1-8 toc refs; same session/state IDs. Short fields, child toc, exact text chunks. Follow next_offset: row/field index; text: UTF-16 offset. No full dump. Stale: refresh state, rediscover refs. Read relevant rules.",
 inputSchema:toolSchemas.sts_get_context,annotations:read},
 args=>guard('sts_get_context',async()=>readContext(link.game.assertState(args.session_id,args.state_id),args.refs,args.offset,args.limit),args.response_format));
server.registerTool('sts_get_request',{title:'Action receipt',description:"Inspect recent receipt by request_id without replay. Missing: unknown, not failure; never resend.",inputSchema:toolSchemas.sts_get_request,annotations:read},
 ({request_id})=>guard('sts_get_request',async()=>link.game.request(request_id)));
process.on('SIGTERM',()=>{link.close();process.exit(0);});process.on('SIGINT',()=>{link.close();process.exit(0);});process.stdin.on('end',()=>link.close());
await server.connect(new StdioServerTransport());
