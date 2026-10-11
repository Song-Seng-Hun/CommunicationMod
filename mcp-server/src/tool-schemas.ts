// Shared with offline example validation. Public inputs contain only agent decisions, not transport identities.
import {z} from 'zod';
const reuse=z.boolean().optional().describe('Opt-in lossless reuse; retain complete FULL responses.'),known_view=z.string().min(1).max(64).optional().describe('With reuse:true, retained FULL view_id. Apply delta to that full response, never another delta. Omit after context loss.');
const known=z.record(z.string(),z.string()).refine(x=>Object.keys(x).length<=8&&Object.entries(x).every(([k,v])=>k.length>0&&k.length<=512&&v.length>0&&v.length<=64),'Up to 8 bounded ref/content ID pairs').optional().describe('With reuse:true, retained full fragment ref/content_id pairs at the same offset/limit. Omit after context loss.');
export const toolSchemas={
 sts_game_status:z.object({}).strict(),
 sts_start_game:z.object({wait_ms:z.number().int().min(0).max(15000).default(15000)}).strict(),
 sts_get_state:z.object({wait_ms:z.number().int().min(0).max(15000).default(0),view:z.enum(['decision','map_plan']).default('decision'),refresh:z.boolean().default(false).describe('true only when a full summary is needed again even if unchanged'),reuse,known_view}).strict(),
 sts_act:z.object({action_id:z.string().min(1).max(512),arguments:z.record(z.string(),z.unknown()).default({}),reuse,known_view}).strict(),
 sts_get_context:z.object({refs:z.array(z.string().min(1).max(512)).min(1).max(8),offset:z.number().int().min(0).max(Number.MAX_SAFE_INTEGER).default(0),limit:z.number().int().min(1).max(30).default(20),response_format:z.enum(['json','compact']).default('compact').describe('compact: lossless TOON when materially smaller, otherwise exact JSON text; json: force JSON text.'),reuse,known}).strict(),
 sts_get_request:z.object({request_id:z.string().min(1).max(128)}).strict()
};
