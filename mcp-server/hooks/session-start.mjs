// Inert source template: never registered automatically. No transcript/game reads.
import {readFile,realpath} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';
import path from 'node:path';
const hash=x=>createHash('sha256').update(x).digest('hex');
const repo=fileURLToPath(new URL('../../',import.meta.url));
export const contextText=_revision=>`Downfall examples are available on demand. Read only needed current guidance refs via sts_get_context. Recheck dynamic costs, targets and offered actions after state changes. Unknown/applied_waiting: inspect original request_id; never resend. No game state or permission restored.`;
export function contextFor(input,env,revision){
 if(env.DOWNFALL_AGENT_CONTEXT!=='1'||input?.hook_event_name!=='SessionStart'||!['startup','resume','compact'].includes(input.source)||!(/^[a-f0-9]{16}$/).test(revision))return '';
 return contextText(revision);
}
export async function loadContext(input,env=process.env){
 if(!contextFor(input,env,'0'.repeat(16)))return '';
 try{
  if(typeof input.cwd!=='string'||path.relative(await realpath(repo),await realpath(input.cwd))!=='')return '';
  const bundle=JSON.parse(await readFile(new URL('../dist/guidance-bundle.json',import.meta.url),'utf8'));
  for(const name of ['context.js','action-projection.js','hand-dedupe.js','view.js','session.js','guidance.js','guidance-catalog.js','tool-schemas.js']){
   if(bundle.modules?.[name]!==hash(await readFile(new URL('../dist/'+name,import.meta.url))))return '';
  }
  if(bundle.hook?.digest!==hash(await readFile(fileURLToPath(import.meta.url))))return '';
  const revision=hash(JSON.stringify(bundle.capabilities)).slice(0,16);
  if(bundle.hook.revision!==revision || !Number.isInteger(bundle.hook.tokens) || bundle.hook.tokens>128 || bundle.hook.tokens<1)return '';
  return contextFor(input,env,revision);
 }catch{return '';}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 let input='',oversize=false;
 for await(const chunk of process.stdin){if(!oversize){input+=chunk;if(Buffer.byteLength(input)>8192){oversize=true;input='';}}}
 try{if(!oversize){const text=await loadContext(JSON.parse(input));if(text)process.stdout.write(JSON.stringify({hookSpecificOutput:{hookEventName:'SessionStart',additionalContext:text}}));}}catch{ /* Missing/invalid input is silent. */ }
}