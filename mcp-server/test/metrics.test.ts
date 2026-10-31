import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, readdir, lstat, writeFile, symlink, link, rmdir } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { performance } from 'node:perf_hooks';

const tools = ['sts_game_status', 'sts_start_game', 'sts_get_state', 'sts_act', 'sts_get_context', 'sts_get_request'];
const MiB = 1024 * 1024;
const values = { connect_ms: 1.25, wait_ms: 2.5, response_bytes: Buffer.byteLength('한글🙂', 'utf8') };
const moduleUrl = process.env.COMMUNICATIONMOD_METRICS_TEST_MODULE
  ? pathToFileURL(process.env.COMMUNICATIONMOD_METRICS_TEST_MODULE).href
  : new URL('../dist/metrics.js', import.meta.url).href;

async function factory() {
  const module = await import(moduleUrl).catch(error => {
    if (error.code === 'ERR_MODULE_NOT_FOUND') assert.fail('Missing optional metrics module: expected RED before implementation');
    throw error;
  });
  assert.equal(typeof module.createMetrics, 'function');
  return module.createMetrics;
}

// Keep only generated temp fixtures; never clean recursively across an untrusted symlink.
async function fixture() {
  const workspace = await mkdtemp(join(tmpdir(), 'communicationmod-metrics-'));
  return { workspace, directory: join(workspace, 'target', 'agent-efficiency', 'metrics') };
}

async function rows(directory) {
  const names = await readdir(directory);
  const contents = await Promise.all(names.map(name => readFile(join(directory, name), 'utf8')));
  return contents.flatMap(text => text.trim() ? text.trim().split('\n').map(line => JSON.parse(line)) : []);
}

function emit(metrics, tool = 'sts_get_state', result = values) {
  metrics.finish(metrics.start(tool), result);
}

test('disabled unless the environment value is exactly 1: no filesystem writes or result inspection', async () => {
  const createMetrics = await factory();
  const { workspace } = await fixture();
  const poisoned = new Proxy({}, { get() { throw new Error('must not read disabled data'); } });
  for (const value of [undefined, '', '0', 'true', '01', ' 1', 1]) {
    const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: value });
    for (let index = 0; index < 10000; index++) {
      assert.equal(metrics.start('sts_act'), undefined);
      metrics.finish(undefined, poisoned);
    }
    await metrics.flush();
  }
  assert.deepEqual(await readdir(workspace), []);
});

test('enabled writes all six tools, unique server IDs, monotonic timing and UTF8 byte counts locally', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  const before = performance.now();
  const spans = tools.map(tool => metrics.start(tool));
  for (const span of spans) {
    assert.ok(span.handler_start_ms >= before);
    assert.match(span.internal_id, /^[0-9a-f-]{36}$/);
    metrics.finish(span, values);
  }
  const after = performance.now();
  await metrics.flush();
  const result = await rows(directory);
  assert.deepEqual(result.map(row => row.tool), tools);
  assert.equal(new Set(result.map(row => row.internal_id)).size, 6);
  for (const [index, row] of result.entries()) {
    assert.equal(row.internal_id, spans[index].internal_id);
    assert.equal(row.handler_start_ms, spans[index].handler_start_ms);
    assert.ok(row.handler_end_ms >= row.handler_start_ms && row.handler_end_ms <= after);
    assert.equal(row.connect_ms, values.connect_ms);
    assert.equal(row.wait_ms, values.wait_ms);
    assert.equal(row.response_bytes, 10);
    assert.equal(row.error_class, 'none');
  }
});

test('own allowlist discards unknown tools and projects metadata without touching private properties', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  const secret = 'PRIVATE-args-native-request-auth-transcript';
  for (const tool of [secret, '__proto__', 'constructor', 'STS_ACT', {}, null]) assert.equal(metrics.start(tool), undefined);
  const result = { ...values, error_class: secret, internal_id: secret, handler_start_ms: secret };
  for (const key of ['args', 'native_text', 'request_id', 'auth', 'transcript']) {
    Object.defineProperty(result, key, { enumerable: true, get() { throw new Error(secret); } });
  }
  emit(metrics, 'sts_act', result);
  await metrics.flush();
  const records = await rows(directory);
  assert.equal(records.length, 1);
  assert.deepEqual(Object.keys(records[0]).sort(), ['tool', 'internal_id', 'process_run_id', 'handler_start_ms', 'handler_end_ms', 'connect_ms', 'wait_ms', 'response_bytes', 'error_class'].sort());
  assert.equal(records[0].error_class, 'operation');
  assert.ok(!JSON.stringify(records).includes(secret));
});

test('only original owned spans can finish, once; foreign or forged IDs are never logged', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  const other = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  const span = metrics.start('sts_act');
  assert.ok(Object.isFrozen(span));
  metrics.finish({ ...span, internal_id: 'request-secret' }, values);
  metrics.finish(other.start('sts_act'), values);
  metrics.finish(undefined, values);
  metrics.finish(span, values);
  metrics.finish(span, values);
  await metrics.flush();
  assert.equal((await rows(directory)).length, 1);
});

test('error classes are bounded and numeric fields cannot carry text, NaN or unsafe counts', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  for (const error_class of ['none', 'connection', 'cancelled', 'operation']) emit(metrics, 'sts_act', { ...values, error_class });
  emit(metrics, 'sts_act', { connect_ms: 'secret', wait_ms: Infinity, response_bytes: -10, error_class: new Error('secret') });
  emit(metrics, 'sts_act', { connect_ms: NaN, wait_ms: -1, response_bytes: Number.MAX_VALUE });
  await metrics.flush();
  const result = await rows(directory);
  assert.deepEqual(result.slice(0, 4).map(row => row.error_class), ['none', 'connection', 'cancelled', 'operation']);
  for (const row of result.slice(4)) {
    assert.equal(row.connect_ms, 0);
    assert.equal(row.wait_ms, 0);
    assert.equal(row.response_bytes, 0);
  }
  assert.ok(!JSON.stringify(result).includes('secret'));
});

test('enabled remains lazy until completion; flush drains without needing another tool call', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  const span = metrics.start('sts_get_state');
  await metrics.flush();
  assert.deepEqual(await readdir(workspace), []);
  metrics.finish(span, values);
  await Promise.all([metrics.flush(), metrics.flush()]);
  assert.equal((await rows(directory)).length, 1);
  emit(metrics);
  await metrics.flush();
  assert.equal((await rows(directory)).length, 2);
});

test('filesystem failures and throwing metadata never escape finish or flush', async () => {
  const createMetrics = await factory();
  const { workspace } = await fixture();
  await writeFile(join(workspace, 'target'), 'unrelated-file');
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  assert.doesNotThrow(() => emit(metrics, 'sts_act', { get connect_ms() { throw new Error('private'); } }));
  assert.doesNotThrow(() => emit(metrics));
  await assert.doesNotReject(metrics.flush());
  emit(metrics);
  await assert.doesNotReject(metrics.flush());
  assert.equal(await readFile(join(workspace, 'target'), 'utf8'), 'unrelated-file');
});

test('overload drops rows before reading metadata and bounds queued bytes', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  let inspected = 0;
  const result = { ...values, get connect_ms() { inspected++; return 1; }, transcript: 'secret'.repeat(MiB) };
  for (let index = 0; index < 20000; index++) emit(metrics, 'sts_get_state', result);
  assert.ok(inspected > 0 && inspected <= 256, `projected ${inspected} rows in a synchronous burst`);
  await metrics.flush();
  const records = await rows(directory);
  assert.equal(records.length, inspected);
  assert.ok(Buffer.byteLength(JSON.stringify(records)) <= 256 * 1024);
  emit(metrics);
  await metrics.flush();
  assert.equal((await rows(directory)).length, inspected + 1);
});

test('rotation keeps at most three regular files of at most 1 MiB with complete JSON rows', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  let lastId;
  for (let batch = 0; batch < 88; batch++) {
    for (let index = 0; index < 256; index++) {
      const span = metrics.start('sts_get_context');
      lastId = span.internal_id;
      metrics.finish(span, values);
    }
    await metrics.flush();
  }
  const names = await readdir(directory);
  assert.equal(names.length, 3);
  for (const name of names) {
    const info = await lstat(join(directory, name));
    assert.ok(info.isFile() && info.size <= MiB && info.size > 0);
  }
  const result = await rows(directory);
  assert.ok(result.length > 0 && result.length < 88 * 256);
  assert.ok(result.some(row => row.internal_id === lastId));
});

test('existing full active file rotates before append without exceeding the cap', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  await mkdir(directory, { recursive: true });
  const active = join(directory, 'metrics.0.jsonl');
  await writeFile(active, 'x'.repeat(MiB));
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  emit(metrics);
  await metrics.flush();
  assert.equal((await lstat(active)).size, MiB);
  const next = JSON.parse((await readFile(join(directory, 'metrics.1.jsonl'), 'utf8')).trim());
  assert.equal(next.tool, 'sts_get_state');
});

test('directory symlinks/junctions, including the workspace itself, cannot redirect writes', async () => {
  const createMetrics = await factory();
  for (const component of ['workspace', 'target', 'agent-efficiency', 'metrics']) {
    const { workspace } = await fixture();
    const outside = await mkdtemp(join(tmpdir(), 'communicationmod-metrics-outside-'));
    const parts = ['target', 'agent-efficiency', 'metrics'];
    const index = parts.indexOf(component);
    const redirected = component === 'workspace' ? join(workspace, 'alias') : join(workspace, ...parts.slice(0, index + 1));
    await mkdir(join(redirected, '..'), { recursive: true });
    await symlink(outside, redirected, process.platform === 'win32' ? 'junction' : 'dir');
    const metrics = createMetrics(component === 'workspace' ? redirected : workspace, { COMMUNICATIONMOD_METRICS: '1' });
    emit(metrics);
    await assert.doesNotReject(metrics.flush());
    assert.deepEqual(await readdir(outside), []);
  }
});

test('hard-linked log files are refused, including a file selected for rotation', async () => {
  const createMetrics = await factory();
  for (const slot of [0, 1]) {
    const { workspace, directory } = await fixture();
    await mkdir(directory, { recursive: true });
    const outside = join(workspace, 'private.txt');
    await writeFile(outside, 'private-content');
    if (slot === 1) await writeFile(join(directory, 'metrics.0.jsonl'), 'x'.repeat(MiB));
    await link(outside, join(directory, `metrics.${slot}.jsonl`));
    const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
    emit(metrics);
    await assert.doesNotReject(metrics.flush());
    assert.equal(await readFile(outside, 'utf8'), 'private-content');
  }
});

test('a log path that is a directory/junction is refused without touching its target', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const outside = await mkdtemp(join(tmpdir(), 'communicationmod-metrics-outside-'));
  await mkdir(directory, { recursive: true });
  await symlink(outside, join(directory, 'metrics.0.jsonl'), process.platform === 'win32' ? 'junction' : 'dir');
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  emit(metrics);
  await assert.doesNotReject(metrics.flush());
  assert.deepEqual(await readdir(outside), []);
});

test('exclusive workspace writer lock drops a contended batch and recovers after release', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const lock = join(workspace, 'target', 'agent-efficiency', 'metrics.writer-lock');
  await mkdir(lock, { recursive: true });
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  emit(metrics);
  await metrics.flush();
  assert.deepEqual(await readdir(directory), []);
  assert.ok((await lstat(lock)).isDirectory());
  await rmdir(lock);
  emit(metrics);
  await metrics.flush();
  assert.equal((await rows(directory)).length, 1);
  await assert.rejects(lstat(lock), { code: 'ENOENT' });
});

test('concurrent instances share one clock identity and recover from lock contention', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const first = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  const second = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  emit(first);
  emit(second);
  await Promise.all([first.flush(), second.flush()]);
  const before = (await rows(directory)).length;
  emit(first);
  await first.flush();
  emit(second);
  await second.flush();
  const result = await rows(directory);
  assert.equal(result.length, before + 2);
  assert.match(result[0].process_run_id, /^[0-9a-f-]{36}$/);
  assert.equal(new Set(result.map(row => row.process_run_id)).size, 1);
});

function childWriter(workspace) {
  const source = `
    import { createMetrics } from ${JSON.stringify(moduleUrl)};
    const metrics = createMetrics(${JSON.stringify(workspace)}, { COMMUNICATIONMOD_METRICS: '1' });
    for (let batch = 0; batch < 12; batch++) {
      for (let index = 0; index < 256; index++) metrics.finish(metrics.start('sts_act'), { connect_ms: 0, wait_ms: 0, response_bytes: 10 });
      await metrics.flush();
    }
  `;
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, ['--experimental-default-type=module', '--input-type=module', '-e', source], { stdio: ['ignore', 'ignore', 'pipe'] });
    let stderr = '';
    child.stderr.on('data', chunk => { stderr = (stderr + chunk.toString()).slice(-4096); });
    child.once('error', reject);
    child.once('exit', code => code === 0 ? resolve() : reject(new Error(`Writer exited ${code}: ${stderr}`)));
  });
}

test('independent processes have different random clock identities and concurrent writers respect file caps', async () => {
  const createMetrics = await factory();
  const { workspace, directory } = await fixture();
  const metrics = createMetrics(workspace, { COMMUNICATIONMOD_METRICS: '1' });
  emit(metrics);
  await metrics.flush();
  const parentId = (await rows(directory))[0].process_run_id;
  assert.match(parentId, /^[0-9a-f-]{36}$/);
  await childWriter(workspace);
  let result = await rows(directory);
  assert.equal(new Set(result.map(row => row.process_run_id)).size, 2);
  await Promise.all([childWriter(workspace), childWriter(workspace)]);
  result = await rows(directory);
  assert.ok(result.some(row => row.process_run_id !== parentId));
  const names = await readdir(directory);
  assert.ok(names.length <= 3);
  for (const name of names) assert.ok((await lstat(join(directory, name))).size <= MiB);
  await assert.rejects(lstat(join(directory, '..', 'metrics.writer-lock')), { code: 'ENOENT' });
});
