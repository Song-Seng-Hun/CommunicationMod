// Secure MCP Tunnel's stdio target. Loading tools never starts or resumes a game.
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
process.env.COMMUNICATIONMOD_WORKSPACE = workspace;
process.env.COMMUNICATIONMOD_LIFECYCLE_SCRIPT = path.join(workspace, 'devtools/manage-plugin-game.ps1');
// The game and its observation child do not need the tunnel control-plane credentials.
for (const name of ['CONTROL_PLANE_API_KEY', 'OPENAI_API_KEY', 'OPENAI_ADMIN_KEY', 'DOWNFALL_TUNNEL_API_KEY']) {
  delete process.env[name];
}
await import('../dist/index.js');
