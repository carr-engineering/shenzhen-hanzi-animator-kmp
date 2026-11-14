import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {gunzipSync} from 'node:zlib';
import {decodeArchive} from '../dist/engine.js';
const commandCodes = {M:0, L:1, Q:2, C:3, Z:4}, arity = {M:2, L:2, Q:4, C:6, Z:0};
function outline(svg) {
  const tokens = svg.match(/[MLQCZ]|[-+]?(?:\d*\.?\d+)(?:[eE][-+]?\d+)?/g);
  const commands = [], coordinates = []; let cursor = 0;
  while (cursor < tokens.length) {
    const command = tokens[cursor++]; commands.push(commandCodes[command]);
    for (let i = 0; i < arity[command]; i++) { const value = Number(tokens[cursor++]); coordinates.push(i % 2 ? 900 - value : value); }
  }
  return {commands, coordinates};
}
test('JS codec preserves the complete 9,574-character corpus and all component occurrences', async () => {
  const source = JSON.parse(gunzipSync(await readFile(new URL('../../tools/fixtures/source-data.json.gz', import.meta.url))));
  const archives = new Map(); let count = 0;
  for (const [character, expected] of Object.entries(source)) {
    const shard = character.codePointAt(0) % 1024;
    if (!archives.has(shard)) { const bytes = await readFile(new URL(`../../bundled-data/src/webMain/resources/strokes/${shard}.hzb`, import.meta.url)); archives.set(shard, decodeArchive(new Int8Array(bytes))); }
    const actual = archives.get(shard).strokeOrder(character);
    assert.equal(actual.character, character); assert.equal(actual.strokes.length, expected.strokes.length, character);
    assert.deepEqual(actual.components, expected.components ?? null, character);
    actual.strokes.forEach((stroke, index) => {
      const normalized = outline(expected.strokes[index]);
      assert.deepEqual(stroke.commands, normalized.commands, `${character} commands ${index}`);
      assert.deepEqual(stroke.coordinates, normalized.coordinates, `${character} outline ${index}`);
      assert.equal(stroke.isRadical, expected.radStrokes?.includes(index) ?? false, `${character} radical ${index}`);
      assert.equal(stroke.componentIndex, expected.strokeComponents?.[index] ?? null, `${character} membership ${index}`);
      assert.equal(stroke.median.length, expected.medians[index].length, `${character} median count ${index}`);
      stroke.median.forEach((point, i) => { const pair = expected.medians[index][i]; assert.ok(Math.abs(point[0] - pair[0]) < 0.001 && Math.abs(point[1] - (900 - pair[1])) < 0.001, `${character} median ${index}/${i}`); });
    }); count++;
  }
  assert.equal(count, 9574);
});
