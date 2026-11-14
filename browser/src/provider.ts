import { decodeArchive } from './engine.js';
import { firstCharacter, LruCache, resourceKey } from './cache.js';
import type { BinaryProviderOptions, EngineArchive, StrokeOrder, StrokeOrderProvider } from './types.js';

const abortError = () => new DOMException('Load cancelled', 'AbortError');
/** Fetch raw .hzb URLs; HTTP Content-Encoding is decoded by the browser before arrayBuffer(). */
export function createBinaryStrokeOrderProvider(options: BinaryProviderOptions = {}): StrokeOrderProvider {
  const blocks = new LruCache<string, EngineArchive>(options.cacheBytes ?? 1024 * 1024);
  const orders = new LruCache<string, StrokeOrder>(options.cacheCharacters ?? 64);
  const shardCount = options.shardCount ?? 1024;
  const baseUrl = options.baseUrl ?? './strokes/';
  const loader = options.loadBytes ?? (async (key: string, signal: AbortSignal) => {
    const response = await fetch(new URL(key, new URL(baseUrl, document.baseURI)), { signal });
    if (!response.ok) throw new Error(`Stroke data request failed (${response.status})`);
    return new Uint8Array(await response.arrayBuffer());
  });
  const pending = new Map<string, {promise: Promise<EngineArchive>; controller: AbortController; users: number}>();
  return { async load(text, signal) {
    if (signal.aborted) throw abortError();
    const character = firstCharacter(text); if (!character) return null;
    const hit = orders.get(character); if (hit) return hit;
    const key = resourceKey(character, shardCount);
    let archive = blocks.get(key);
    if (!archive) {
      let request = pending.get(key);
      if (!request) {
        const controller = new AbortController();
        const promise = loader(key, controller.signal).then(bytes => {
          if (controller.signal.aborted) throw abortError();
          const decoded = decodeArchive(new Int8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength));
          blocks.put(key, decoded, bytes.byteLength); return decoded;
        });
        request = {promise, controller, users: 0}; pending.set(key, request);
        const current = request;
        void promise.finally(() => { if (pending.get(key) === current) pending.delete(key); }).catch(() => {});
      }
      request.users++;
      const current = request;
      archive = await new Promise<EngineArchive>((resolve, reject) => {
        let settled = false;
        const finish = (result?: EngineArchive, error?: unknown) => {
          if (settled) return; settled = true; signal.removeEventListener('abort', aborted);
          current.users--; if (current.users === 0) { if (pending.get(key) === current) pending.delete(key); current.controller.abort(); }
          if (error !== undefined) reject(error); else resolve(result!);
        };
        const aborted = () => finish(undefined, abortError());
        signal.addEventListener('abort', aborted, {once: true});
        current.promise.then(value => finish(value), error => finish(undefined, error));
        if (signal.aborted) aborted();
      });
    }
    if (signal.aborted) throw abortError();
    const order = archive.strokeOrder(character); if (order) orders.put(character, order);
    return order;
  }};
}
