import gzip
import json
from pathlib import Path
import struct
import unittest

import generate_stroke_data as codec


class BinaryGenerationTest(unittest.TestCase):
    def fixture(self):
        return {"森": {"strokes": ["M 1 2 L 3.5 4 Q 5 6 7 8 C 9 10 11 12 13 14 Z", "M 0 0 Z"],
                      "medians": [[[1, 2], [3.25, 4], [5, 6.5]], []], "radStrokes": [0],
                      "components": ["木", "木"], "strokeComponents": [1, None]}}

    def test_every_command_half_precision_fractional_medians_and_occurrences(self):
        entries = self.fixture()
        codec.validate(entries, expected_count=1)
        raw = codec.archive(entries)
        codec.verify(raw, entries)
        self.assertEqual(raw, codec.archive(entries))

    def test_source_is_verified_and_all_9574_records_roundtrip(self):
        import hashlib
        source = gzip.decompress(codec.SOURCE.read_bytes())
        manifest = json.loads(codec.SOURCE_MANIFEST.read_text())
        self.assertEqual(manifest["sha256"], hashlib.sha256(source).hexdigest())
        data = json.loads(source)
        codec.validate(data)
        codec.verify(codec.archive(data), data)
        self.assertEqual(9574, len(data))
        self.assertEqual(["木", "木", "木"], data["森"]["components"])
        self.assertTrue(any(n is None for n in data["赢"]["strokeComponents"]))

    def test_truncated_corrupt_and_trailing_archive_data_are_rejected(self):
        raw = codec.archive(self.fixture())
        for invalid in (raw[:-1], raw + b"garbage", raw[:-1] + bytes([raw[-1] ^ 1])):
            with self.assertRaises(ValueError):
                codec.verify(invalid, self.fixture())

    def test_duplicate_json_keys_fail_instead_of_overwriting(self):
        with self.assertRaises(ValueError):
            json.loads('{"中":{},"中":{}}', object_pairs_hook=codec.strict_object)

    def test_invalid_source_is_not_silently_dropped(self):
        for field, value in (("medians", []), ("radStrokes", [99]), ("strokeComponents", [99, None]),
                             ("strokes", ["M 1 2 A 3 4", "M 0 0 Z"]), ("surprise", True)):
            data = self.fixture()
            data["森"][field] = value
            with self.assertRaises(ValueError):
                codec.validate(data, expected_count=1)


if __name__ == "__main__":
    unittest.main()
