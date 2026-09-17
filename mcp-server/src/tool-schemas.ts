// Shared with offline example validation. These are the existing public inputs.
import {z} from 'zod';
export const toolSchemas={
 sts_game_status:z.object({}).strict(),
 sts_start_game:z.object({wait_ms:z.number().int().min(0).max(15000).default(15000)}).strict(),
 sts_get_state:z.object({wait_ms:z.number().int().min(0).max(15000).default(0),view:z.enum(['decision','map_plan']).default('decision'),known_view:z.string().max(64).optional()}).strict(),
 sts_act:z.object({action_id:z.string().min(1).max(512),arguments:z.record(z.string(),z.unknown()).default({})}).strict(),
 sts_get_context:z.object({refs:z.array(z.string().min(1).max(512)).min(1).max(8),offset:z.number().int().min(0).max(Number.MAX_SAFE_INTEGER).default(0),limit:z.number().int().min(1).max(30).default(20),response_format:z.enum(['json','compact']).default('json').describe('compact: lossless TOON/JSON text only; json: JSON text. Omit/use json for compatibility.')}).strict(),
 sts_get_request:z.object({request_id:z.string().min(1).max(128)}).strict()
};
