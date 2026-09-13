import { randomUUID } from 'node:crypto';
import { constants } from 'node:fs';
import { lstat, mkdir, open, realpath, rmdir } from 'node:fs/promises';
import { dirname, join, parse, resolve } from 'node:path';
import { performance } from 'node:perf_hooks';

export type MetricsErrorClass = 'none' | 'connection' | 'cancelled' | 'operation';
export interface MetricsSpan {
  readonly internal_id: string;
  readonly handler_start_ms: number;
}
export interface MetricsResult {
  connect_ms: number;
  wait_ms: number;
  /** UTF-8 byte count computed by the caller; never pass response text here. */
  response_bytes: number;
  error_class?: MetricsErrorClass;
}
export interface Metrics {
  start(tool: string): MetricsSpan | undefined;
  finish(span: MetricsSpan | undefined, result: MetricsResult): void;
  flush(): Promise<void>;
}

const TOOLS = new Set(['sts_game_status', 'sts_start_game', 'sts_get_state', 'sts_act', 'sts_get_context', 'sts_get_request']);
const FILE_BYTES = 1024 * 1024;
const FILE_COUNT = 3;
const MAX_PENDING = 256;
const MAX_ROW_BYTES = 1024;
// Generated lazily, shared by logger instances in this server process. Not a PID.
let processRunId: string | undefined;
const RESOLVED = Promise.resolve();
const DISABLED: Metrics = Object.freeze({
  start: () => undefined,
  finish: () => {},
  flush: () => RESOLVED,
});

const numeric = (value: unknown): number =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= Number.MAX_SAFE_INTEGER ? value : 0;

function errorClass(value: unknown): MetricsErrorClass {
  if (value === undefined || value === 'none') return 'none';
  return value === 'connection' || value === 'cancelled' ? value : 'operation';
}

function samePath(left: string, right: string): boolean {
  return process.platform === 'win32' ? left.toLowerCase() === right.toLowerCase() : left === right;
}

/** Recheck every component, including workspace ancestors; do not follow links. */
async function checkDirectories(path: string): Promise<void> {
  const ancestors = [path];
  for (let parent = dirname(path); parent !== ancestors[ancestors.length - 1]; parent = dirname(parent)) {
    ancestors.push(parent);
  }
  for (const ancestor of ancestors.reverse()) {
    const info = await lstat(ancestor);
    if (info.isSymbolicLink() || !info.isDirectory()) throw new Error('Unsafe metrics directory');
  }
  // Resolve the complete accessible path; an ancestor may deny directory listing
  // while still granting traversal to the user's workspace or temp directory.
  if (!samePath(await realpath(path), path)) {
    throw new Error('Unsafe metrics directory');
  }
}

async function metricsDirectory(workspace: string): Promise<string> {
  await checkDirectories(workspace);
  let directory = workspace;
  // Fixed components only: neither tool names nor caller data can become paths.
  for (const component of ['target', 'agent-efficiency', 'metrics']) {
    directory = join(directory, component);
    try {
      await mkdir(directory, { mode: 0o700 });
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error;
    }
    await checkDirectories(directory);
  }
  return directory;
}

async function openLog(directory: string, slot: number) {
  await checkDirectories(directory);
  const path = join(directory, `metrics.${slot}.jsonl`);
  let before;
  try {
    before = await lstat(path);
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
  }
  if (before && (!before.isFile() || before.isSymbolicLink() || before.nlink !== 1)) {
    throw new Error('Unsafe metrics file');
  }
  // Never O_TRUNC: validate the actual handle before any destructive rotation.
  // O_EXCL prevents following a newly introduced link when creating a file.
  const flags = constants.O_RDWR | (constants.O_NOFOLLOW || 0)
    | (before ? 0 : constants.O_CREAT | constants.O_EXCL);
  const handle = await open(path, flags, 0o600);
  try {
    const actual = await handle.stat();
    const current = await lstat(path);
    await checkDirectories(directory);
    if (!actual.isFile() || actual.nlink !== 1 || current.isSymbolicLink()
      || actual.dev !== current.dev || actual.ino !== current.ino
      || (before && (actual.dev !== before.dev || actual.ino !== before.ino))) {
      throw new Error('Metrics file changed');
    }
    return { handle, size: actual.size };
  } catch (error) {
    await handle.close();
    throw error;
  }
}

/**
 * Optional, best-effort local metadata. Concurrent instances use one exclusive
 * metrics.writer-lock directory beside metrics; contention drops the batch.
 * The lock is released after each batch. A process crash can leave a stale lock:
 * fail closed until the user removes that empty directory with writers stopped.
 * Files metrics.0.jsonl through metrics.2.jsonl form a ring (not date ordering).
 * At most 256 rows, each <= 1 KiB, are retained across queue + in-flight batch.
 * No raw caller object is retained; unfinished spans have only weak ownership.
 * Node's portable filesystem APIs cannot atomically protect parent directories
 * against a hostile concurrent rename; the workspace must remain user-owned.
 */
export function createMetrics(workspace: string, env: NodeJS.ProcessEnv = process.env): Metrics {
  if (env.COMMUNICATIONMOD_METRICS !== '1') return DISABLED;
  let root: string;
  try {
    root = resolve(workspace);
    // UNC/device paths are not a local workspace target.
    if (parse(root).root.startsWith('\\\\')) return DISABLED;
  } catch {
    return DISABLED;
  }
  const spans = new WeakMap<MetricsSpan, { tool: string; span: MetricsSpan }>();
  const queue: { line: string; bytes: number }[] = [];
  let pending = 0;
  let failed = false;
  let slot = 0;
  let truncate = false;
  let draining: Promise<void> | undefined;

  async function append(batch: typeof queue): Promise<void> {
    const directory = await metricsDirectory(root);
    const lock = join(dirname(directory), 'metrics.writer-lock');
    try {
      await mkdir(lock, { mode: 0o700 });
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === 'EEXIST') return;
      throw error;
    }
    try {
      await checkDirectories(lock);
      await appendLocked(directory, batch);
    } finally {
      // Remove only the empty directory this batch acquired, never recursively.
      await rmdir(lock);
    }
  }

  async function appendLocked(directory: string, batch: typeof queue): Promise<void> {
    let offset = 0;
    while (offset < batch.length) {
      const log = await openLog(directory, slot);
      try {
        if (truncate) {
          await log.handle.truncate(0);
          log.size = 0;
          truncate = false;
        }
        let end = offset;
        let bytes = 0;
        while (end < batch.length && log.size + bytes + batch[end].bytes <= FILE_BYTES) {
          bytes += batch[end++].bytes;
        }
        if (end > offset) {
          // Bounded by MAX_PENDING * MAX_ROW_BYTES, never includes raw payloads.
          const buffer = Buffer.from(batch.slice(offset, end).map(row => row.line).join(''), 'utf8');
          let written = 0;
          while (written < buffer.length) {
            const result = await log.handle.write(buffer, written, buffer.length - written, log.size + written);
            if (result.bytesWritten === 0) throw new Error('Incomplete metrics write');
            written += result.bytesWritten;
          }
          offset = end;
        }
        if (offset < batch.length) {
          slot = (slot + 1) % FILE_COUNT;
          truncate = true;
        }
      } finally {
        await log.handle.close();
      }
    }
  }

  function schedule(): void {
    if (draining) return;
    draining = new Promise<void>(done => setImmediate(done)).then(async () => {
      try {
        while (queue.length) {
          const batch = queue.splice(0);
          await append(batch);
          pending -= batch.length;
        }
      } catch {
        // Fail closed for this instance: no retries, console output or tool errors.
        failed = true;
        queue.length = 0;
        pending = 0;
      } finally {
        draining = undefined;
      }
    });
  }

  return {
    start(tool) {
      if (failed || pending >= MAX_PENDING || !TOOLS.has(tool)) return undefined;
      try {
        processRunId ??= randomUUID();
        const span = Object.freeze({ internal_id: randomUUID(), handler_start_ms: performance.now() });
        spans.set(span, { tool, span });
        return span;
      } catch {
        return undefined;
      }
    },
    finish(span, result) {
      try {
        if (!span) return;
        const owned = spans.get(span);
        if (!owned) return;
        spans.delete(span);
        if (failed || pending >= MAX_PENDING) return;
        // Explicit scalar projection: never spread, stringify or inspect caller data.
        const responseBytes = numeric(result.response_bytes);
        const row = {
          tool: owned.tool,
          internal_id: owned.span.internal_id,
          process_run_id: processRunId,
          handler_start_ms: owned.span.handler_start_ms,
          handler_end_ms: performance.now(),
          connect_ms: numeric(result.connect_ms),
          wait_ms: numeric(result.wait_ms),
          response_bytes: Number.isSafeInteger(responseBytes) ? responseBytes : 0,
          error_class: errorClass(result.error_class),
        };
        const line = JSON.stringify(row) + '\n';
        const bytes = Buffer.byteLength(line, 'utf8');
        if (bytes > MAX_ROW_BYTES) return;
        queue.push({ line, bytes });
        pending++;
        schedule();
      } catch {
        // Even a malformed runtime caller must not affect the tool's result.
      }
    },
    async flush() {
      while (draining) await draining;
    },
  };
}
