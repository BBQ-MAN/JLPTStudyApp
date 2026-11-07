import json
import unittest

import build
import check
from pl_common import Fail
from pl_strokes import parse_strokes
from support import TempRoot, kvg_svg, make_opener, synthetic_sources
import shutil
import sqlite3


class ParseStrokesTest(unittest.TestCase):
    def test_order_by_s_number_and_nothing_else(self):
        ds = [f"M{i},0" for i in range(1, 12)]  # 11획: s10·s11 이 문자열 정렬로는 s2 앞에 온다
        got = parse_strokes(kvg_svg("水", ds), "水")
        self.assertEqual(got, ds)
        text = json.dumps(got, ensure_ascii=False)
        for s in ("ELEMENT", "RADICAL", "NUMBER", "TYPE", "水"):
            self.assertNotIn(s, text)  # kvg:element·radical·type 과 StrokeNumbers 는 결과에 없다

    def test_viewbox_must_be_109(self):
        with self.assertRaises(Fail) as c:
            parse_strokes(kvg_svg("水", ["M1,1"], vb="0 0 1024 1024"), "水")
        self.assertTrue(str(c.exception).startswith("FAIL: parse S-03 viewBox 0 0 1024 1024"))

    def test_stroke_numbers_must_be_contiguous(self):
        svg = kvg_svg("水", ["M1,1", "M2,2", "M3,3"]).replace(b"-s3", b"-s5")
        with self.assertRaises(Fail):
            parse_strokes(svg, "水")


class StrokeReleaseTest(TempRoot):
    def setUp(self):
        self.man, self.blobs = synthetic_sources()
        self.p = self.make_root(self.man, {"kind": "release", "levelSource": "S-06", "levels": [5, 4], "milestone": "T"})
        self.log = []

    def test_kanji_stroke_rows_and_missing_report(self):
        self.assertEqual(build.main(["refresh-snapshot"], paths=self.p, out=self.log.append, opener=make_opener(self.blobs)), 0, self.log)
        first = (self.p.snapshot / "S-03-r20260714.jsonl.gz").read_bytes()
        shutil.rmtree(self.p.raw)
        build.main(["refresh-snapshot"], paths=self.p, out=self.log.append, opener=make_opener(self.blobs))
        self.assertEqual((self.p.snapshot / "S-03-r20260714.jsonl.gz").read_bytes(), first)  # 결정적
        build.main(["export-gloss-input"], paths=self.p, out=self.log.append)
        self.p.kogloss.mkdir(parents=True, exist_ok=True)
        for level in (5, 4):
            rows = [json.loads(l) for l in (self.p.work / "en-gloss" / f"N{level}.jsonl").read_text(encoding="utf-8").splitlines()]
            (self.p.kogloss / f"N{level}.jsonl").write_text("".join(json.dumps(
                {"vocabId": e["vocabId"], "meanings": ["뜻"], "koSource": "jmdict-translated", "method": "llm:m@2026-11-10", "enHash": e["enHash"],
                 "reviewStatus": "unreviewed" if level == 5 else "sampled", "reviewedAt": None if level == 5 else "2026-11-10",
                 "reviewerKind": None if level == 5 else "agent"}, ensure_ascii=False, sort_keys=True) + "\n" for e in rows), encoding="utf-8")
        shutil.rmtree(self.p.raw)
        self.assertEqual(build.main([], paths=self.p, out=self.log.append), 0, self.log)
        con = sqlite3.connect(self.p.assets / "content.db")
        n_kanji = con.execute("SELECT count(*) FROM kanji").fetchone()[0]
        n_stroke = con.execute("SELECT count(*) FROM kanji_stroke").fetchone()[0]
        missing = self.p.report("strokes_missing.tsv", "T").read_text(encoding="utf-8").splitlines()
        self.assertEqual(missing, ["犬\tN5"])
        self.assertEqual(n_stroke, n_kanji - len(missing))
        self.assertTrue(self.p.report("strokes_count_mismatch.tsv", "T").exists())
        self.assertEqual(con.execute("SELECT DISTINCT source_id FROM kanji_stroke").fetchall(), [("S-03",)])
        self.assertEqual(json.loads(con.execute("SELECT paths_json FROM kanji_stroke WHERE literal='水'").fetchone()[0])[:2], ["M1,1c1,2", "M1,1c2,2"])
        self.assertEqual(con.execute("PRAGMA user_version").fetchone()[0], 2)
        self.assertIn("S-03", {s["id"] for s in json.loads((self.p.assets / "attribution.json").read_text(encoding="utf-8"))["sources"]})
        self.assertEqual(check.run_checks(self.p), [])
        con.close()


if __name__ == "__main__":
    unittest.main()
