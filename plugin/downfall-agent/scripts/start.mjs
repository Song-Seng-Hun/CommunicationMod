import {readFile,realpath} from 'node:fs/promises';
import {homedir} from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// No build, game launch or network request occurs just by loading this plugin.
if(Number(process.versions.node.split('.')[0])<22)throw new Error('Downfall Agent requires Node.js 22+.');
const configPath=process.env.COMMUNICATIONMOD_CONFIG ?? path.join(homedir(),'.communicationmod','plugin.json');
let config;
try{config=JSON.parse((await readFile(configPath,'utf8')).replace(/^\uFEFF/,''));}
catch{throw new Error('Downfall Agent setup missing. Run devtools/install-codex-plugin.ps1 from the CommunicationMod checkout.');}
if(typeof config.workspace!=='string'||!path.isAbsolute(config.workspace))throw new Error('Invalid Downfall Agent workspace configuration.');
process.env.COMMUNICATIONMOD_WORKSPACE=await realpath(config.workspace);
if(typeof config.powershell==='string')process.env.COMMUNICATIONMOD_POWERSHELL=config.powershell;
process.env.COMMUNICATIONMOD_LIFECYCLE_SCRIPT=fileURLToPath(new URL('./manage-plugin-game.ps1',import.meta.url));
await import('../server/index.js');
