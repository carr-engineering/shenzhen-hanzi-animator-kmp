import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createBinaryStrokeOrderProvider} from '../dist/provider.js';
import {decodeArchive, createStrokeSession} from '../dist/engine.js';
const cp = '永'.codePointAt(0) % 1024;
const bytes = new Uint8Array(await readFile(new URL(`../../bundled-data/src/webMain/resources/strokes/${cp}.hzb`, import.meta.url)));

test('Kotlin codec and session decode data and recognize its normalized medians', () => {
  const archive = decodeArchive(new Int8Array(bytes)); const order = archive.strokeOrder('永');
  assert.equal(order.character, '永'); assert.equal(order.strokes.length, 5); assert.equal(order.supportsPractice, true);
  const session = createStrokeSession(order, {}); assert.equal(session.startQuiz(), true);
  for (const stroke of order.strokes) assert.equal(session.submitStroke(stroke.median.flat()).accepted, true);
  const state = session.snapshot(); assert.equal(state.complete, true); assert.equal(state.totalMistakes, 0); assert.deepEqual(state.mistakesPerStroke, [0, 0, 0, 0, 0]);
  const plain = {...order, strokes: order.strokes.map(stroke => ({...stroke, commands: [...stroke.commands], coordinates: [...stroke.coordinates], median: stroke.median.map(point => [...point])}))};
  delete plain.__order; const fromPlain = createStrokeSession(plain, {}); fromPlain.startQuiz(); assert.equal(fromPlain.submitStroke(plain.strokes[0].median.flat()).accepted, true);
  assert.throws(() => createStrokeSession({...plain, strokes: [{...plain.strokes[0], coordinates: [NaN]}]}, {}));
  session.reset(); session.play(); session.advance(1e6); assert.deepEqual(session.snapshot().progress, [1, 1, 1, 1, 1]);
  const corrupt = new Int8Array(bytes); corrupt[corrupt.length - 1] ^= 1; assert.throws(() => decodeArchive(corrupt));
});
test('provider deduplicates a block while cancellation remains per consumer', async () => {
  let calls = 0, release, sourceSignal;
  const provider = createBinaryStrokeOrderProvider({loadBytes: async (key, signal) => { calls++; sourceSignal = signal; assert.equal(key, `${cp}.hzb`); await new Promise(r => release = r); return bytes; }});
  const a = new AbortController(), b = new AbortController();
  const first = provider.load('永', a.signal); const rejected = assert.rejects(first, error => error.name === 'AbortError');
  const second = provider.load('永', b.signal); a.abort(); assert.equal(sourceSignal.aborted, false); release();
  await rejected; assert.equal((await second).character, '永'); assert.equal(calls, 1);
  assert.equal((await provider.load('永', new AbortController().signal)).character, '永'); assert.equal(calls, 1);
});
test('a fully cancelled request is replaced rather than reused', async () => {
  let calls = 0; const releases = [];
  const provider = createBinaryStrokeOrderProvider({loadBytes: async (key, signal) => { calls++; await new Promise(resolve => releases.push(resolve)); return bytes; }});
  const old = new AbortController(); const first = provider.load('永', old.signal); const rejected = assert.rejects(first, error => error.name === 'AbortError'); old.abort(); await rejected;
  const second = provider.load('永', new AbortController().signal); assert.equal(calls, 2); releases[0](); releases[1](); assert.equal((await second).character, '永');
});

test('public orders can be serialized and supplied back without private engine state', () => {
  const order = decodeArchive(new Int8Array(bytes)).strokeOrder('永');
  const encoded = JSON.stringify(order); const restored = JSON.parse(encoded);
  assert.equal(Object.keys(order).includes('__order'), false);
  assert.equal('__order' in restored, false);
  assert.deepEqual(Object.keys(restored).sort(), ['character', 'components', 'strokes', 'supportsPractice']);
  const session = createStrokeSession(restored, {}); session.startQuiz();
  for (const stroke of restored.strokes) assert.equal(session.submitStroke(stroke.median.flat()).accepted, true);
  assert.equal(session.snapshot().complete, true);
});

test('custom providers reject invalid component indexes and fractional command codes', () => {
  const original = JSON.parse(JSON.stringify(decodeArchive(new Int8Array(bytes)).strokeOrder('永')));
  for (const invalid of ['0', 0.5, Infinity, -1, 2]) {
    const order = structuredClone(original); order.components = ['first', 'second']; order.strokes[0].componentIndex = invalid;
    assert.throws(() => createStrokeSession(order, {}), `component index ${invalid}`);
  }
  const order = structuredClone(original); order.strokes[0].commands[0] = 0.5;
  assert.throws(() => createStrokeSession(order, {}), 'fractional command must not be truncated to MOVE');
});
