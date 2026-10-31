// Generated evaluation snapshots only. Never writes the installed plugin or game.
import {mkdtemp, mkdir, readdir, readFile, copyFile, writeFile,symlink} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const repo=fileURLToPath(new URL('../../',import.meta.url));
await mkdir(path.join(repo,'target'),{recursive:true});
const root=await mkdtemp(path.join(repo,'target','agent-latency-baseline-'));
const hashes={};
for(const dir of ['mcp-server/src','mcp-server/dist','plugin/downfall-agent/skills/downfall-play']){
 await mkdir(path.join(root,dir),{recursive:true});
 for(const name of await readdir(path.join(repo,dir))){
  if(!/\.(ts|js|md)$/.test(name))continue;
  const rel=dir+'/'+name,bytes=await readFile(path.join(repo,rel));
  hashes[rel]=createHash('sha256').update(bytes).digest('hex');
  await copyFile(path.join(repo,rel),path.join(root,rel));
 }
}
await writeFile(path.join(root,'manifest.json'),JSON.stringify({created_at:new Date().toISOString(),hashes},null,2));
await writeFile(path.join(root,'package.json'),JSON.stringify({type:'module'}));
await symlink(path.join(repo,'mcp-server','node_modules'),path.join(root,'node_modules'),'junction');
console.log(root);
