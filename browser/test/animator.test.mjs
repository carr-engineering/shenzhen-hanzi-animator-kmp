import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHanziAnimator} from '../dist/index.js';
import {decodeArchive} from '../dist/engine.js';
const bytes = await readFile(new URL(`../../bundled-data/src/webMain/resources/strokes/${'永'.codePointAt(0) % 1024}.hzb`, import.meta.url));
const archive = decodeArchive(new Int8Array(bytes)); const order = archive.strokeOrder('永');
class Canvas extends EventTarget {
  style = {}; attributes = {}; width = 0; height = 0; captures = new Set();
  getContext() { return new Proxy({}, {get: (_, key) => () => {}, set: () => true}); }
  getBoundingClientRect() { return {width: 320, height: 320, left: 0, top: 0}; }
  setAttribute(key, value) { this.attributes[key] = value; }
  hasPointerCapture(id) { return this.captures.has(id); }
  setPointerCapture(id) { this.captures.add(id); }
  releasePointerCapture(id) { this.captures.delete(id); }
}
globalThis.window = Object.assign(new EventTarget(), {devicePixelRatio: 2});
globalThis.ResizeObserver = class {observe() {} disconnect() {}};
globalThis.Path2D = class {moveTo() {} lineTo() {} quadraticCurveTo() {} bezierCurveTo() {} closePath() {}};
let frames = new Map(), sequence = 0;
globalThis.requestAnimationFrame = fn => { const id = ++sequence; frames.set(id, fn); return id; };
globalThis.cancelAnimationFrame = id => frames.delete(id);
function pointer(canvas, type, point, id = 1) {
  const event = new Event(type, {cancelable: true});
  Object.assign(event, {pointerId: id, isPrimary: true, button: 0, clientX: 12 + point[0] * 296 / 1024, clientY: 12 + point[1] * 296 / 1024});
  canvas.dispatchEvent(event); return event;
}
test('latest character wins, failed loads recover and callback updates are live', async () => {
  const canvas = new Canvas(), requests = [];
  let oldCallbacks = 0, newCallbacks = 0, results = 0;
  const animator = createHanziAnimator(canvas, {provider: {load(character) {return new Promise((resolve, reject) => requests.push({character, resolve, reject}));}}, onStrokeResult: () => oldCallbacks++});
  const old = animator.setCharacter('森'), current = animator.setCharacter('永');
  requests[1].resolve(order); await current; requests[0].resolve(archive.strokeOrder(archive.characters[0])); await old;
  assert.equal(animator.state().supportsPractice, true); assert.equal(canvas.width, 640);
  animator.updateOptions({onStrokeResult: result => {newCallbacks++; results += Number(result.accepted);}}); animator.startQuiz(); assert.equal(canvas.style.touchAction, 'none');
  const median = order.strokes[0].median; pointer(canvas, 'pointerdown', median[0]); for (const point of median.slice(1, -1)) pointer(canvas, 'pointermove', point); pointer(canvas, 'pointerup', median.at(-1));
  assert.equal(oldCallbacks, 0); assert.equal(newCallbacks, 1); assert.equal(results, 1); assert.equal(animator.state().currentStroke, 1);
  const states = []; animator.updateOptions({onLoadState: state => states.push(state)});
  const fail = animator.setCharacter('永'); requests[2].reject(new Error('offline')); await fail; assert.deepEqual(states, ['loading', 'error']); assert.equal(animator.state(), null);
  const retry = animator.setCharacter('永'); requests[3].resolve(order); await retry; assert.equal(states.at(-1), 'loaded');
  animator.dispose(); assert.equal(canvas.style.touchAction, 'auto');
});
test('cancelled pointers and resizing never submit traces; disposal suppresses pending loads', async () => {
  const canvas = new Canvas(); let submissions = 0, resolveLoad;
  const animator = createHanziAnimator(canvas, {provider: {load: async () => order}, onStrokeResult: () => submissions++}); await animator.setCharacter('永'); animator.startQuiz();
  const median = order.strokes[0].median; pointer(canvas, 'pointerdown', median[0]); pointer(canvas, 'pointercancel', median[1]); pointer(canvas, 'pointerup', median.at(-1)); assert.equal(submissions, 0);
  pointer(canvas, 'pointerdown', median[0]); window.dispatchEvent(new Event('resize')); pointer(canvas, 'pointerup', median.at(-1)); assert.equal(submissions, 0); assert.equal(animator.state().totalMistakes, 0);
  animator.reset(); assert.equal(canvas.style.touchAction, 'auto');
  let states = 0; animator.updateOptions({provider: {load: () => new Promise(resolve => resolveLoad = resolve)}, onLoadState: () => states++}); animator.dispose(); const before = states; resolveLoad(order); await Promise.resolve(); await Promise.resolve(); assert.equal(states, before);
});
test('callbacks may dispose or replace a request without stale submissions', async () => {
  const canvas = new Canvas(); let count = 0, completions = 0;
  const animator = createHanziAnimator(canvas, {provider: {load: async () => order}, onStrokeResult: () => {count++; if (count === order.strokes.length) animator.dispose();}, onComplete: () => completions++});
  await animator.setCharacter('永'); animator.startQuiz();
  for (const stroke of order.strokes) { pointer(canvas, 'pointerdown', stroke.median[0]); for (const point of stroke.median.slice(1, -1)) pointer(canvas, 'pointermove', point); pointer(canvas, 'pointerup', stroke.median.at(-1)); }
  assert.equal(count, 5); assert.equal(completions, 0); assert.equal(animator.state(), null);
  const calls = []; let redirected = false;
  const replacement = createHanziAnimator(new Canvas(), {provider: {load: async character => {calls.push(character); return order;}}, onLoadState(state) {if (state === 'loading' && !redirected) {redirected = true; void replacement.setCharacter('新');}}});
  await replacement.setCharacter('旧'); await Promise.resolve(); assert.deepEqual(calls, ['新']); replacement.dispose();
});
test('completion summary stays stable when a result callback resets the same session', async () => {
  const canvas = new Canvas(); let accepted = 0, summaries = [];
  const animator = createHanziAnimator(canvas, {provider: {load: async () => order}, onStrokeResult(result) {if (result.accepted && ++accepted === order.strokes.length) animator.reset();}, onComplete: summary => summaries.push(summary)});
  await animator.setCharacter('永'); animator.startQuiz();
  function draw(median) {pointer(canvas, 'pointerdown', median[0]); for (const point of median.slice(1, -1)) pointer(canvas, 'pointermove', point); pointer(canvas, 'pointerup', median.at(-1));}
  draw([...order.strokes[0].median].reverse());
  assert.equal(animator.state().totalMistakes, 1);
  for (const stroke of order.strokes) draw(stroke.median);
  assert.deepEqual(summaries, [{totalMistakes: 1, mistakesPerStroke: [1, 0, 0, 0, 0]}]);
  assert.equal(animator.state().mode, 'Idle'); assert.equal(animator.state().totalMistakes, 0);
  animator.dispose();
});
