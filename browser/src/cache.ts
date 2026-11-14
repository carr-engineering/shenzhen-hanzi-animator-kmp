/** Insertion ordered byte-budget LRU. Oversized entries are usable but never retained. */
export class LruCache<K, V> {
  private entries = new Map<K, {value: V; size: number}>(); private size = 0;
  constructor(private budget: number) { if (!Number.isSafeInteger(budget) || budget < 0) throw new RangeError('Cache budget must be a non-negative integer'); }
  get(key: K): V | undefined { const item = this.entries.get(key); if (!item) return; this.entries.delete(key); this.entries.set(key, item); return item.value; }
  put(key: K, value: V, size = 1): void {
    const old = this.entries.get(key); if (old) { this.size -= old.size; this.entries.delete(key); }
    if (size > this.budget) return;
    this.entries.set(key, {value, size}); this.size += size;
    while (this.size > this.budget) { const key = this.entries.keys().next().value!; this.size -= this.entries.get(key)!.size; this.entries.delete(key); }
  }
}
export function firstCharacter(text: string): string { return Array.from(text.trim())[0] ?? ''; }
export function resourceKey(character: string, shardCount: number): string {
  if (!Number.isInteger(shardCount) || shardCount < 1 || shardCount > 65536) throw new RangeError('Invalid shard count');
  const codepoint = firstCharacter(character).codePointAt(0); if (codepoint === undefined) throw new RangeError('Character is required');
  return `${codepoint % shardCount}.hzb`;
}
