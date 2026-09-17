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
const response=(data:Obj)=>({content:[{type:'text' as const,text:JSON.stringify(data)}]});
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
server.registerTool('sts_get_state',{title:'Current decision',description:"Current decision + state-scoped toc. The server pins this state for later context/action calls. Read needed refs via sts_get_context before acting. known_view=previous view_id: short unchanged reply.",inputSchema:toolSchemas.sts_get_state,annotations:read},
 ({wait_ms,view,known_view},extra)=>guard('sts_get_state',async wait=>{
  const state=await wait(()=>link.game.wait(wait_ms,{signal:extra.signal,
   ...(known_view!==undefined?{changed:(candidate:Obj)=>conditionalDecision(candidate,undefined,view).view_id!==known_view}:{})}));
  link.game.present(state);return conditionalDecision(state,known_view,view);
 },'json',false,extra.signal));
server.registerTool('sts_act',{title:'Act + next decision',description:"One offered action from the pinned state. Read needed details first; never auto-discard. Unknown/applied_waiting: inspect request; never replay. Successful calls return the next decision directly.",
 inputSchema:toolSchemas.sts_act,annotations:{...read,readOnlyHint:false,destructiveHint:true,idempotentHint:false}},
 args=>guard('sts_act',async wait=>{
  if(JSON.stringify(args.arguments).length>8000)throw new Error('Arguments too large.');
  const result=await wait(()=>link.game.actPresented({action_id:args.action_id,arguments:args.arguments,request_id:args.request_id ?? randomUUID()},args.wait_ms));
  const next=obj(result.state);link.game.present(next);
  const outcome=String(result.outcome),data:Obj={outcome,state:conditionalDecision(next)};
  if(outcome==='unknown'||outcome==='applied_waiting')data.request_id=result.request_id;
  if(outcome==='rejected'){
   const receipt=obj(result.receipt),reason=receipt.message ?? receipt.error ?? receipt.reason ?? receipt.status;
   if(reason!==undefined)data.reason=reason;
  }
  return data;
 }));
server.registerTool('sts_get_context',{title:'Public context fragments',description:"Read 1-8 refs from the pinned state. Multiple refs share an inline response budget: card refs may return card_summary; read one card ref alone for full detail. Follow next_offset when present. Stale pin: refresh state. Read relevant rules.",
 inputSchema:toolSchemas.sts_get_context,annotations:read},
 args=>guard('sts_get_context',async()=>readContext(link.game.assertPresented(),args.refs,args.offset,args.limit),args.response_format));
server.registerTool('sts_get_request',{title:'Action receipt',description:"Inspect an uncertain recent action by request_id without replay. Missing: unknown, not failure; never resend.",inputSchema:toolSchemas.sts_get_request,annotations:read},
 ({request_id})=>guard('sts_get_request',async()=>link.game.request(request_id)));
process.on('SIGTERM',()=>{link.close();process.exit(0);});process.on('SIGINT',()=>{link.close();process.exit(0);});process.stdin.on('end',()=>link.close());
await server.connect(new StdioServerTransport());
