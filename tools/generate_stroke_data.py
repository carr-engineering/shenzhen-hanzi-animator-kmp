#!/usr/bin/env python3
"""Reproducibly encode the pinned, lossless Hanzi corpus into HZB1 data packs.

The default source is the archived *current* corpus, including occurrence-based
component metadata. No downloads occur during generation. A custom --source
must be accompanied by --source-sha256. All profiles are validated and staged
before replacing their output directories. Requires Node's built-in Brotli.
"""
from __future__ import annotations

import argparse
import ctypes
import gzip
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools/fixtures/source-data.json.gz"
SOURCE_MANIFEST = ROOT / "tools/fixtures/source-manifest.json"
OUTPUT = ROOT / "bundled-data/src"
PINNED_SHA = "68d10a4b21150cae5e1ebbd223eed289cf32d90c"
DICTIONARY_SHA = "618dbab8a8ddefb958763c8b4afbaa741a4460de"
EXPECTED_CHAR_COUNT = 9574
CODES = {"M": 0, "L": 1, "Q": 2, "C": 3, "Z": 4}
ARITY = {"M": 2, "L": 2, "Q": 4, "C": 6, "Z": 0}
TOKEN = re.compile(r"[MLQCZ]|[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?")
BROTLI = r"""
const fs=require('fs'),z=require('zlib'),path=require('path');
for(const f of process.argv.slice(1)){
 const b=fs.readFileSync(f);const c=z.brotliCompressSync(b,{params:{[z.constants.BROTLI_PARAM_QUALITY]:11}});
 if(!z.brotliDecompressSync(c).equals(b)) throw Error('Brotli roundtrip failed');
 fs.writeFileSync(f+'.br',c);
}
"""


def uv(n: int) -> bytes:
    if not 0 <= n <= 0xffffffff:
        raise ValueError(f"unsigned integer out of range: {n}")
    out = bytearray()
    while n >= 128:
        out.append((n & 127) | 128)
        n >>= 7
    out.append(n)
    return bytes(out)


def sv(n: int) -> bytes:
    return uv((n << 1) ^ (n >> 63))


def text(s: str) -> bytes:
    raw = s.encode("utf-8")
    return uv(len(raw)) + raw



def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON object key: {key}")
        result[key] = value
    return result

def canonical(data: dict) -> bytes:
    return json.dumps(data, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode()


def parse_path(path: str) -> list[tuple[str, tuple[float, ...]]]:
    if not isinstance(path, str):
        raise ValueError("outline must be SVG text")
    tokens = []
    at = 0
    for token in TOKEN.finditer(path):
        if path[at:token.start()].strip(" \t\r\n,"):
            raise ValueError(f"unsupported SVG syntax: {path[at:token.start()]!r}")
        tokens.append(token.group())
        at = token.end()
    if path[at:].strip(" \t\r\n,"):
        raise ValueError("unsupported SVG suffix")
    commands = []
    at = 0
    while at < len(tokens):
        command = tokens[at]
        at += 1
        if command not in ARITY or at + ARITY[command] > len(tokens):
            raise ValueError("invalid outline command")
        coords = tuple(float(x) for x in tokens[at:at + ARITY[command]])
        at += ARITY[command]
        if any(not math.isfinite(x) or abs(x) > 32767 or x * 2 != int(x * 2) for x in coords):
            raise ValueError("outline needs unsupported precision or exceeds bounds")
        commands.append((command, coords))
    if not commands or commands[0][0] != "M":
        raise ValueError("outline must begin with M")
    return commands


def validate(data: dict, expected_count: int | None = EXPECTED_CHAR_COUNT) -> None:
    if not isinstance(data, dict) or (expected_count is not None and len(data) != expected_count):
        raise ValueError(f"expected {expected_count} characters")
    for char, entry in data.items():
        if len(char) != 1 or 0xd800 <= ord(char) <= 0xdfff:
            raise ValueError("character keys must be Unicode scalars")
        if set(entry) - {"strokes", "medians", "radStrokes", "components", "strokeComponents"}:
            raise ValueError(f"unknown fields in {char!r}; update the format instead of dropping data")
        strokes, medians = entry.get("strokes"), entry.get("medians")
        if not isinstance(strokes, list) or not strokes or len(strokes) > 256:
            raise ValueError(f"invalid strokes for {char}")
        if not isinstance(medians, list) or len(medians) != len(strokes):
            raise ValueError(f"stroke/median count mismatch for {char}")
        for path in strokes:
            parse_path(path)
        for points in medians:
            if not isinstance(points, list):
                raise ValueError(f"invalid medians for {char}")
            for point in points:
                if (not isinstance(point, list) or len(point) != 2 or
                    any(isinstance(n, bool) or not isinstance(n, (int, float)) or not math.isfinite(n) or abs(n) > 32767 for n in point)):
                    raise ValueError(f"invalid median point for {char}")
        radicals = entry.get("radStrokes", [])
        if not isinstance(radicals, list) or len(radicals) != len(set(radicals)) or any(type(n) is not int or not 0 <= n < len(strokes) for n in radicals):
            raise ValueError(f"invalid radical indexes for {char}")
        components = entry.get("components")
        indexes = entry.get("strokeComponents")
        if components is None:
            if indexes is not None:
                raise ValueError(f"component indexes without labels for {char}")
        else:
            if not isinstance(components, list) or any(not isinstance(s, str) or not s for s in components):
                raise ValueError(f"invalid components for {char}")
            if not isinstance(indexes, list) or len(indexes) != len(strokes) or any(n is not None and (type(n) is not int or not 0 <= n < len(components)) for n in indexes):
                raise ValueError(f"invalid component membership for {char}")


def encode_record(entry: dict) -> bytes:
    out = bytearray(uv(len(entry["strokes"])))
    components = entry.get("components")
    out += uv(0 if components is None else len(components) + 1)
    for label in components or []:
        out += text(label)
    radical = set(entry.get("radStrokes", []))
    memberships = entry.get("strokeComponents", [None] * len(entry["strokes"]))
    for index, outline in enumerate(entry["strokes"]):
        membership = memberships[index]
        out += uv(((0 if membership is None else membership + 1) << 1) | (index in radical))
        commands = parse_path(outline)
        scale = 2 if any(x != int(x) for _, coords in commands for x in coords) else 1
        out += uv((len(commands) << 1) | (scale == 2))
        bits = sum(CODES[command] << (3 * i) for i, (command, _) in enumerate(commands))
        out += bits.to_bytes((len(commands) * 3 + 7) // 8, "little")
        for axis in (0, 1):
            previous = 0
            for _, coords in commands:
                for value in coords[axis::2]:
                    current = int(value * scale)
                    out += sv(current - previous)
                    previous = current
        points = entry["medians"][index]
        out += uv(len(points))
        previous = [0, 0]
        for point in points:
            for axis, value in enumerate(point):
                if value != int(value):
                    out += uv(0xffffffff) + struct.pack("<d", value)
                else:
                    out += sv(int(value) - previous[axis])
                previous[axis] = int(value)
    return bytes(out)


def archive(entries: dict) -> bytes:
    payload, directory = bytearray(), bytearray()
    for char, entry in sorted(entries.items(), key=lambda item: ord(item[0])):
        record = encode_record(entry)
        directory += struct.pack("<III", ord(char), len(payload), len(record))
        payload += record
    header = b"HZB1" + struct.pack("<IIII", len(entries), len(directory), len(payload), zlib.crc32(directory + payload))
    return header + directory + payload


class Reader:
    def __init__(self, raw: bytes):
        self.raw, self.at = raw, 0

    def take(self, count):
        if count < 0 or self.at + count > len(self.raw):
            raise ValueError("truncated record")
        out = self.raw[self.at:self.at + count]
        self.at += count
        return out

    def uv(self):
        out, shift = 0, 0
        while shift <= 28:
            value = self.take(1)[0]
            out |= (value & 127) << shift
            if value < 128:
                if out > 0xffffffff:
                    raise ValueError("oversized varint")
                return out
            shift += 7
        raise ValueError("oversized varint")

    def text(self):
        return self.take(self.uv()).decode("utf-8")


def decode_record(raw: bytes) -> dict:
    reader = Reader(raw)
    count, component_count = reader.uv(), reader.uv()
    labels = None if component_count == 0 else [reader.text() for _ in range(component_count - 1)]
    outlines, medians, radicals, memberships = [], [], [], []
    commands_by_code = list(CODES)
    for index in range(count):
        flags, outline_info = reader.uv(), reader.uv()
        memberships.append(None if flags >> 1 == 0 else (flags >> 1) - 1)
        if flags & 1:
            radicals.append(index)
        command_count, scale = outline_info >> 1, 2 if outline_info & 1 else 1
        bits = int.from_bytes(reader.take((command_count * 3 + 7) // 8), "little")
        commands = [commands_by_code[(bits >> (3 * i)) & 7] for i in range(command_count)]
        axes = []
        for _ in (0, 1):
            previous, values = 0, []
            for command in commands:
                for _ in range(ARITY[command] // 2):
                    delta = reader.uv()
                    previous += (delta >> 1) ^ -(delta & 1)
                    values.append(previous / scale)
            axes.append(iter(values))
        outlines.append([(command, tuple(next(axes[j % 2]) for j in range(ARITY[command]))) for command in commands])
        points, previous = [], [0, 0]
        for _ in range(reader.uv()):
            point = []
            for axis in (0, 1):
                value = reader.uv()
                if value == 0xffffffff:
                    current = struct.unpack("<d", reader.take(8))[0]
                else:
                    current = previous[axis] + ((value >> 1) ^ -(value & 1))
                point.append(current)
                previous[axis] = int(current)
            points.append(point)
        medians.append(points)
    if reader.at != len(raw):
        raise ValueError("unconsumed record bytes")
    return {"outlines": outlines, "medians": medians, "radStrokes": radicals, "components": labels, "strokeComponents": memberships}


def verify(raw: bytes, entries: dict) -> None:
    if raw[:4] != b"HZB1":
        raise ValueError("invalid archive")
    count, directory_size, payload_size, crc = struct.unpack_from("<IIII", raw, 4)
    if count != len(entries) or directory_size != count * 12 or len(raw) != 20 + directory_size + payload_size:
        raise ValueError("invalid archive lengths")
    payload = raw[20 + directory_size:]
    if zlib.crc32(raw[20:]) != crc:
        raise ValueError("invalid archive checksum")
    previous_end = 0
    for index, (char, entry) in enumerate(sorted(entries.items(), key=lambda item: ord(item[0]))):
        cp, offset, length = struct.unpack_from("<III", raw, 20 + index * 12)
        if cp != ord(char) or offset != previous_end:
            raise ValueError("invalid directory")
        previous_end = offset + length
        actual = decode_record(payload[offset:offset + length])
        expected = {"outlines": [parse_path(path) for path in entry["strokes"]], "medians": entry["medians"],
                    "radStrokes": entry.get("radStrokes", []), "components": entry.get("components"),
                    "strokeComponents": entry.get("strokeComponents", [None] * len(entry["strokes"]))}
        if actual != expected:
            raise ValueError(f"numeric/metadata roundtrip mismatch for {char}")
    if previous_end != payload_size:
        raise ValueError("unindexed payload")



def apple_lzma(raw: bytes) -> bytes:
    """Apple's LZMA stream format, compatible with Compression's native decoder."""
    library = ctypes.CDLL("/usr/lib/libcompression.dylib")
    encode = library.compression_encode_buffer
    decode = library.compression_decode_buffer
    signature = [ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p, ctypes.c_int]
    encode.argtypes = decode.argtypes = signature
    encode.restype = decode.restype = ctypes.c_size_t
    source = ctypes.create_string_buffer(raw)
    capacity = max(1024, len(raw) * 2)
    target = ctypes.create_string_buffer(capacity)
    length = encode(target, capacity, source, len(raw), None, 0x306)
    if not length:
        raise ValueError("Apple LZMA encoder failed")
    compressed = target.raw[:length]
    back = ctypes.create_string_buffer(len(raw))
    compressed_input = ctypes.create_string_buffer(compressed)
    decoded_length = decode(back, len(raw), compressed_input, len(compressed), None, 0x306)
    if decoded_length != len(raw) or back.raw != raw:
        raise ValueError("Apple LZMA roundtrip failed")
    return compressed

def write_profile(staged: Path, entries: dict, profile: str, shards: int, codec: str, source_hash: str) -> dict:
    directory = staged / profile / "resources/strokes"
    directory.mkdir(parents=True)
    buckets = [{} for _ in range(shards)]
    for char, entry in entries.items():
        buckets[ord(char) % shards][char] = entry
    resources = []
    for index, bucket in enumerate(buckets):
        raw = archive(bucket)
        verify(raw, bucket)
        key = "all.hzb" if shards == 1 else f"{index}.hzb"
        encoded = gzip.compress(raw, compresslevel=9, mtime=0) if codec == "gzip" else apple_lzma(raw) if codec == "apple-lzma" else raw
        (directory / key).write_bytes(encoded)
        resources.append({"key": key, "decodedBytes": len(raw), "encodedBytes": len(encoded), "sha256": hashlib.sha256(raw).hexdigest()})
    if codec in ("brotli", "http"):
        files = [str(directory / item["key"]) for item in resources]
        # Keep command lines below OS argument limits for large custom shard counts.
        for first in range(0, len(files), 128):
            subprocess.run(["node", "-e", BROTLI, *files[first:first + 128]], check=True)
        for item in resources:
            path = directory / item["key"]
            br = path.with_suffix(path.suffix + ".br")
            item["brotliBytes"] = br.stat().st_size
            if codec == "brotli":
                os.replace(br, path)
                item["encodedBytes"] = path.stat().st_size
            else:
                zipped = gzip.compress(path.read_bytes(), compresslevel=9, mtime=0)
                path.with_suffix(path.suffix + ".gz").write_bytes(zipped)
                item["gzipBytes"] = len(zipped)
    (directory / "index.txt").write_text("".join(sorted(entries, key=ord)), encoding="utf-8")
    manifest = {"format": "HZB1", "characterCount": len(entries), "sourceSha256": source_hash,
                "shardCount": shards, "codec": codec, "resources": resources,
                "generator": {"python": sys.version.split()[0], "zlib": zlib.ZLIB_VERSION,
                              "node": subprocess.check_output(["node", "--version"], text=True).strip()}}
    (directory / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    return manifest


def replace_directory(staged: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    backup = destination.with_name(destination.name + ".previous")
    if backup.exists():
        raise RuntimeError(f"recover/remove previous generation first: {backup}")
    if destination.exists():
        os.replace(destination, backup)
    try:
        os.replace(staged, destination)
    except BaseException:
        if backup.exists():
            os.replace(backup, destination)
        raise
    if backup.exists():
        shutil.rmtree(backup)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=SOURCE)
    parser.add_argument("--source-sha256")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--web-shards", type=int, default=1024)
    parser.add_argument("--ios-codec", default="brotli", choices=("brotli", "apple-lzma"), help="Apple LZMA encoding requires macOS; supply matching iOS source codec.")
    parser.add_argument("--native-shards", type=int, default=1, choices=(1, 16))
    parser.add_argument("--characters", help="Only include these Unicode characters; preserves lossless records.")
    args = parser.parse_args()
    if not 1 <= args.web_shards <= 4096:
        parser.error("--web-shards must be in 1..4096")
    if args.source != SOURCE and not args.source_sha256:
        parser.error("custom --source requires --source-sha256")
    compressed = args.source.read_bytes()
    source = gzip.decompress(compressed) if compressed[:2] == b"\x1f\x8b" else compressed
    expected_hash = args.source_sha256 or json.loads(SOURCE_MANIFEST.read_text())["sha256"]
    if hashlib.sha256(source).hexdigest() != expected_hash:
        raise ValueError("source checksum mismatch")
    data = json.loads(source, object_pairs_hook=strict_object)
    validate(data)
    if args.characters is not None:
        requested = set(args.characters)
        missing = requested - set(data)
        if missing:
            raise ValueError(f"characters unavailable: {''.join(sorted(missing))}")
        data = {char: data[char] for char in sorted(requested, key=ord)}
    with tempfile.TemporaryDirectory(prefix="hanzi-pack-", dir=ROOT / "tools") as temporary:
        staged = Path(temporary)
        manifests = {}
        for profile, shards, codec in (("jvmMain", args.native_shards, "gzip"), ("androidMain", args.native_shards, "gzip"),
                                       ("iosMain", args.native_shards, args.ios_codec), ("webMain", args.web_shards, "http")):
            manifests[profile] = write_profile(staged, data, profile, shards, codec, expected_hash)
        # No committed data is touched until all profiles and numeric roundtrips pass.
        for profile in manifests:
            replace_directory(staged / profile / "resources/strokes", args.output / profile / "resources/strokes")
        summary = {profile: {"encodedBytes": sum(item["encodedBytes"] for item in manifest["resources"]),
                             "decodedBytes": sum(item["decodedBytes"] for item in manifest["resources"]),
                             "brotliBytes": sum(item.get("brotliBytes", 0) for item in manifest["resources"]),
                             "gzipBytes": sum(item.get("gzipBytes", 0) for item in manifest["resources"])}
                   for profile, manifest in manifests.items()}
        print(json.dumps(summary, indent=2))
        report = (ROOT / "build/reports/compression-experiment/final-packs.json"
                  if args.output.resolve() == OUTPUT.resolve() else args.output / "generation-summary.json")
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(summary, indent=2) + "\n")


if __name__ == "__main__":
    main()
