import json
import tempfile
import unittest
from pathlib import Path

from pl_parse import parse_jmdict, parse_kanjidic2
from support import JMDICT, kanjidic


def write(text):
    p = Path(tempfile.mkdtemp()) / "f.xml"
    p.write_text(text, encoding="utf-8")
    return p


class ParseTest(unittest.TestCase):
    def test_jmdict_english_only(self):
        entries = list(parse_jmdict(write(JMDICT)))
        dump = json.dumps(entries, ensure_ascii=False)
        for foreign in ("Wasser", "eau", "vloeistof", "lsource"):
            self.assertNotIn(foreign, dump)
        water = entries[0]
        self.assertEqual(water["seq"], 1000001)
        self.assertEqual([s["gloss"] for s in water["senses"]], [["water"], ["fluid"]])
        self.assertEqual(water["pos"], ["n"])  # DTD 엔티티 설명 → 코드
        self.assertEqual(entries[1]["pos"], ["adv", "n"])
        kana = {e["seq"]: e for e in entries}
        self.assertTrue(kana[1000011]["senses"][0]["uk"])
        self.assertEqual(kana[1000006]["keb"], [])

    def test_jmdict_gz(self):
        import gzip
        p = Path(tempfile.mkdtemp()) / "JMdict_e.gz"
        p.write_bytes(gzip.compress(JMDICT.encode()))
        self.assertEqual(len(list(parse_jmdict(p))), 13)

    def test_kanjidic2_only_used_fields(self):
        chars = list(parse_kanjidic2(write(kanjidic())))
        dump = json.dumps(chars, ensure_ascii=False)
        for banned in ("SKIPCODE", "nelson", "99", "ENGMEANING", "FRMEANING", "pinyin1", "viet1", "kor_r", "NANORI", "jlpt", "grade"):
            self.assertNotIn(banned, dump)
        self.assertEqual(sorted(chars[0]), ["freq", "ja_kun", "ja_on", "korean_h", "literal", "stroke_count"])
        self.assertEqual((chars[0]["literal"], chars[0]["stroke_count"], chars[0]["freq"], chars[0]["korean_h"]), ("水", 4, 100, ["수"]))  # 첫 stroke_count
