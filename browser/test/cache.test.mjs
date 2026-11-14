import test from 'node:test';
import assert from 'node:assert/strict';
import {LruCache, firstCharacter, resourceKey} from '../dist/cache.js';
test('byte-budget LRU keeps recently read blocks and declines oversized blocks', () => {
  const cache = new LruCache(10); cache.put('a', 1, 4); cache.put('b', 2, 4);
  assert.equal(cache.get('a'), 1); cache.put('c', 3, 4);
  assert.equal(cache.get('b'), undefined); assert.equal(cache.get('a'), 1);
  cache.put('huge', 4, 11); assert.equal(cache.get('huge'), undefined); assert.equal(cache.get('c'), 3);
  cache.put('a', 5, 9); assert.equal(cache.get('c'), undefined); assert.equal(cache.get('a'), 5);
});
test('Unicode codepoint sharding handles supplementary Hanzi and whitespace', () => {
  assert.equal(firstCharacter(' 𠀀森 '), '𠀀'); assert.equal(resourceKey('𠀀', 1024), '0.hzb');
  assert.equal(resourceKey('永', 1024), `${'永'.codePointAt(0) % 1024}.hzb`);
  assert.throws(() => resourceKey('', 1024), RangeError); assert.throws(() => resourceKey('永', 0), RangeError);
});
