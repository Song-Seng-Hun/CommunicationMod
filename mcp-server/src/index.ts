import {McpServer} from '@modelcontextprotocol/sdk/server/mcp.js';
import {StdioServerTransport} from '@modelcontextprotocol/sdk/server/stdio.js';
import {z} from 'zod';import {randomUUID} from 'node:crypto';import {fileURLToPath} from 'node:url';import path from 'node:path';
import {Connection} from './connection.js';import {focus,section,obj,type Obj} from './view.js';
const root=process.env.COMMUNICATIONMOD_WORKSPACE ?? path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const link=new Connection(root),server=new McpServer({name:'communicationmod-mcp-server',version:'0.1.0'});
const read={readOnlyHint:true,destructiveHint:false,idempotentHint:true,openWorldHint:false};
const ids={session_id:z.string().min(1).max(128),state_id:z.number().int().nonnegative()};
const outputSchema=z.object({data:z.record(z.string(),z.unknown())});
function response(data:Obj){return {content:[{type:'text' as const,text:JSON.stringify(data)}],structuredContent:{data}};}
async function guard(run:()=>Promise<Obj>){try{await link.ensure();return response(await run());}catch(e){return {isError:true,content:[{type:'text' as const,text:e instanceof Error?e.message:'MCP operation failed; inspect current state before acting.'}]};}}
server.registerTool('sts_get_state',{title:'Current game decision',description:'Get only current-screen decision information, full localized card/event text and offered actions. Deck/map/history are on-demand via sts_get_context. Never infer actions from an unready state.',inputSchema:z.object({wait_ms:z.number().int().min(0).max(15000).default(0)}).strict(),outputSchema,annotations:read},
 ({wait_ms})=>guard(async()=>focus(await link.game.wait(wait_ms))));
server.registerTool('sts_act',{title:'Act and await next game decision',description:'Apply ONE offered action using the observed session/state IDs, then return its receipt and next stable decision. No automatic retries or multi-action planning. For event acknowledgement provide the reading_id and meaningful commentary in arguments. Unknown/applied_waiting means inspect state and request; do not resend.',
 inputSchema:z.object({...ids,action_id:z.string().min(1).max(512),request_id:z.string().min(1).max(128).optional(),arguments:z.record(z.string(),z.unknown()).default({}),wait_ms:z.number().int().min(10).max(15000).default(8000)}).strict(),outputSchema,annotations:{...read,readOnlyHint:false,destructiveHint:true,idempotentHint:false}},
 args=>guard(async()=>{if(JSON.stringify(args.arguments).length>8000)throw new Error('Arguments too large.');const result=await link.game.act({...args,request_id:args.request_id ?? randomUUID()},args.wait_ms);return {...result,state:focus(obj(result.state))};}));
server.registerTool('sts_get_context',{title:'Additional public game context',description:'Read an on-demand public section for the same session/state: deck, full map, piles, history, screen, or full observation. Never accesses internal game objects or save files. Lists are paginated; contents retain upstream visibility rules.',
 inputSchema:z.object({...ids,section:z.enum(['deck','map','piles','history','screen','full']),offset:z.number().int().min(0).default(0),limit:z.number().int().min(1).max(100).default(30)}).strict(),outputSchema,annotations:read},
 args=>guard(async()=>section(link.game.assertState(args.session_id,args.state_id),args.section,args.offset,args.limit)));
server.registerTool('sts_get_request',{title:'Inspect action outcome without replay',description:'Read a recent action receipt by request_id after timeout or disconnect. Missing receipt is unknown, not evidence of failure. Keeps the last 128 bridge receipts; never resends an action.',inputSchema:z.object({request_id:z.string().min(1).max(128)}).strict(),outputSchema,annotations:read},
 ({request_id})=>guard(async()=>link.game.request(request_id)));
process.on('SIGTERM',()=>{link.close();process.exit(0);});process.on('SIGINT',()=>{link.close();process.exit(0);});process.stdin.on('end',()=>link.close());
await server.connect(new StdioServerTransport());
