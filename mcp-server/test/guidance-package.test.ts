import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readdir,copyFile,symlink,readFile,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';

test('stale or missing guidance artifact disables examples only; native rules still work',async()=>{
 const dist=fileURLToPath(new URL('../dist/',import.meta.url));
 for(const mode of ['missing','mismatch','one-capability','view-drift','session-drift']){
  const temp=await mkdtemp(path.join(tmpdir(),'guidance-package-'));
  for(const name of await readdir(dist))if(name.endsWith('.js'))await copyFile(path.join(dist,name),path.join(temp,name));
  await writeFile(path.join(temp,'package.json'),JSON.stringify({type:'module'}));
  await symlink(fileURLToPath(new URL('../node_modules',import.meta.url)),path.join(temp,'node_modules'),'junction');
  if(mode!=='missing'){
   const bundle=JSON.parse(await readFile(path.join(dist,'guidance-bundle.json'),'utf8'));
   if(mode==='mismatch')bundle.modules['context.js']='bad';
   else if(mode==='one-capability')for(const [id,v] of Object.entries(bundle.capabilities))if(id.startsWith('shop'))v.digest='bad';
   await writeFile(path.join(temp,'guidance-bundle.json'),JSON.stringify(bundle));
   if(mode.endsWith('-drift')){
    const file=path.join(temp,mode.slice(0,-6)+'.js');
    await writeFile(file,(await readFile(file,'utf8'))+'\n// Synthetic post-tsc drift, old bundle retained.\n');
   }
  }
  const api=await import(pathToFileURL(path.join(temp,'context.js')).href);
  const s={session_id:'s',state_id:1,ready:true,actions:[{id:'run.shop.card.a',parameters:{}}],observation:{game_state:{screen_type:'SHOP_SCREEN',current_hp:7}}};
  const view=api.decision(s);assert.equal(view.player.current_hp,7);assert.ok(api.readContext(s,['rules']).fragments.length);
  if(mode==='one-capability'){
   assert.ok(view.toc.some(x=>x.ref==='guidance'));assert.equal(view.guidance,undefined);
   const rows=api.readContext(s,['guidance']).fragments[0].toc;assert.ok(rows.every(x=>!x.ref.startsWith('guidance/shop')));
  }else{
   assert.ok(!view.toc.some(x=>x.ref==='guidance'));assert.equal(view.guidance,undefined);assert.throws(()=>api.readContext(s,['guidance']),/not available/);
  }
 }
});
