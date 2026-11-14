# HZB1 lossless stroke archive

All fixed-width integers are unsigned little-endian 32-bit integers. Geometry
uses source coordinates (1024-unit glyph width, y up); the runtime model applies
y = 900 − sourceY exactly once.

The 20-byte header is ASCII `HZB1`, record count, directory byte length, payload
byte length and CRC32 of the directory plus payload. The directory has exactly 12 bytes per
record: Unicode scalar, payload-relative byte offset and record byte length.
Scalars are strictly increasing; record ranges are contiguous, non-overlapping
and cover the whole payload. Header/directory sizes and all bounds are checked.

Each character record contains:

1. Unsigned varint stroke count and component count (`0` = absent, otherwise
   count + 1), followed by UTF-8 component labels prefixed by unsigned byte length.
   Duplicate labels represent distinct occurrences and are retained.
2. For each stroke, an unsigned varint flags field: bit 0 is radical, remaining
   bits encode membership (`0` = unmapped, otherwise component index + 1).
3. Unsigned varint outline header: command count shifted left one, low bit
   indicates half-unit rather than integer precision. Commands use three bits
   each, packed from least significant bit: M=0, L=1, Q=2, C=3, Z=4. Unused bits
   must be zero. Commands have 2/2/4/6/0 coordinate values respectively.
4. All X coordinates followed by all Y coordinates. Each axis begins at zero
   and uses Zigzag-encoded signed delta varints. Divide decoded values by the
   outline's precision scale (1 or 2).
5. Unsigned varint median point count, followed by interleaved X,Y deltas. Each
   axis begins at zero. Unsigned `0xffffffff` is an escape followed by an exact
   little-endian Float64. Otherwise the varint is a Zigzag integer delta.
   After either value, the previous-axis integer is the value truncated toward
   zero. This preserves fractional source medians without quantization.

The archive does not embed JSON, SVG text or compression. System asset readers
apply gzip, Apple Brotli or Apple LZMA; web responses use negotiated HTTP
compression. Unsupported versions, invalid UTF-8, illegal commands, nonfinite
geometry, invalid component indexes, bad checksums and truncated records fail
explicitly. The Kotlin codec also bounds counts and allocation sizes.
