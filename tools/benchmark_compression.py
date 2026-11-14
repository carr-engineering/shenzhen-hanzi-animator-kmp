#!/usr/bin/env python3
"""Compare lossless formats/codecs without changing shipped data or adding runtime dependencies.

python3 tools/benchmark_compression.py [--characters '森聽赢']
Writes build/reports/compression-benchmark.json; requires Node for Brotli.
Python 3.14's optional stdlib Zstandard is measured when available.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import lzma
import platform
import subprocess
import time
from pathlib import Path

import generate_stroke_data as generator

ROOT = Path(__file__).resolve().parent.parent


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--characters', help='Measure only these characters; default is the full corpus.')
    args = parser.parse_args()
    source = gzip.decompress((ROOT / 'tools/fixtures/source-data.json.gz').read_bytes())
    manifest = json.loads((ROOT / 'tools/fixtures/source-manifest.json').read_text())
    source_hash = hashlib.sha256(source).hexdigest()
    if source_hash != manifest['sha256']:
        raise ValueError('Pinned source checksum mismatch')
    corpus = json.loads(source, object_pairs_hook=generator.strict_object)
    if args.characters:
        corpus = {c: corpus[c] for c in sorted(set(args.characters), key=ord)}
    binary = generator.archive(corpus)
    generator.verify(binary, corpus)
    json_bytes = generator.canonical(corpus)
    # Node verifies each Brotli stream independently before returning its measured length.
    brotli_script = """
const z=require('node:zlib');const chunks=[];
process.stdin.on('data',b=>chunks.push(b));process.stdin.on('end',()=>{
const raw=Buffer.concat(chunks),start=performance.now();
const encoded=z.brotliCompressSync(raw,{params:{[z.constants.BROTLI_PARAM_QUALITY]:Number(process.argv[1])}});
const encodeMs=performance.now()-start,decodeStart=performance.now();
const decoded=z.brotliDecompressSync(encoded);
if(!decoded.equals(raw))throw new Error('Roundtrip mismatch');
process.stdout.write(JSON.stringify({encodedBytes:encoded.length,encodeMs,decodeMs:performance.now()-decodeStart}));});
"""
    rows = []
    for name, raw in [('canonical-json', json_bytes), ('indexed-binary-hzb1', binary)]:
        for codec, encode, decode in [
            ('gzip9', lambda b: gzip.compress(b, compresslevel=9, mtime=0), gzip.decompress),
            ('xz-lzma9', lambda b: lzma.compress(b, preset=9), lzma.decompress),
        ]:
            start = time.perf_counter(); encoded = encode(raw); encode_ms = (time.perf_counter()-start)*1000
            start = time.perf_counter(); decoded = decode(encoded); decode_ms = (time.perf_counter()-start)*1000
            if decoded != raw:
                raise ValueError('Codec roundtrip mismatch')
            row = dict(format=name, codec=codec, rawBytes=len(raw), encodedBytes=len(encoded), encodeMs=encode_ms, decodeMs=decode_ms)
            rows.append(row); print(json.dumps(row), flush=True)
        for quality in [9, 11]:
            stats = json.loads(subprocess.run(['node', '-e', brotli_script, str(quality)], input=raw, capture_output=True, check=True).stdout)
            row = dict(format=name, codec=f'brotli{quality}', rawBytes=len(raw), **stats)
            rows.append(row); print(json.dumps(row), flush=True)
    unavailable = []
    try:
        from compression import zstd
    except ImportError:
        unavailable.append('Zstandard requires a Python build with compression.zstd support.')
    else:
        for level in [19, 22]:
            start = time.perf_counter(); encoded = zstd.compress(binary, level=level); encode_ms = (time.perf_counter()-start)*1000
            start = time.perf_counter(); decoded = zstd.decompress(encoded); decode_ms = (time.perf_counter()-start)*1000
            if decoded != binary:
                raise ValueError('Zstandard roundtrip mismatch')
            row = dict(format='indexed-binary-hzb1', codec=f'zstd{level}', rawBytes=len(binary), encodedBytes=len(encoded), encodeMs=encode_ms, decodeMs=decode_ms)
            rows.append(row); print(json.dumps(row), flush=True)
    report = dict(sourceSha256=source_hash, characters=len(corpus), python=platform.python_version(),
                  node=subprocess.check_output(['node', '--version'], text=True).strip(), results=rows,
                  unavailable=unavailable, caveats=[
                      'Timings are host observations, not mobile/web latency.',
                      'XZ is not Apple Compression LZMA framing; use generator --ios-codec apple-lzma for that profile.',
                      'Zstandard/XZ on other platforms may require additional runtime decoders; codec bytes alone do not determine app size.',
                      'Monolithic JSON differs from the old 256-shard package; this isolates representation and delivery codec.',
                  ])
    output = ROOT / 'build/reports/compression-benchmark.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2)+'\n')


if __name__ == '__main__':
    main()
