import json
import shutil
import tempfile
import unittest
from unittest import mock
from pathlib import Path

import build
import pl_review as rv
from pl_common import Paths, sha256_file


def run(p, *argv):
    lines = []
    rc = build.main(list(argv), paths=p, out=lines.append)
    return rc, lines


class ReviewTest(unittest.TestCase):
    def setUp(self):
        d = Path(tempfile.mkdtemp(prefix="jlptrv-"))
        self.addCleanup(shutil.rmtree, d, True)
        self.p = Paths(d)
        (d / "docs/content").mkdir(parents=True)
        (d / "docs/content/manifest.json").write_text("[]")
        (d / "content/pipeline").mkdir(parents=True)
        (d / "content/pipeline/config.json").write_text('{"kind":"sample","levelSource":null,"levels":[5]}')
        (d / "content/ko-gloss").mkdir(parents=True)
        self.rows = [dict(enHash="h", koSource="jmdict-translated", meanings=[f"뜻{i}"], method="llm:m@2026-11-02",
                          reviewStatus="unreviewed", reviewedAt=None, reviewerKind=None, vocabId=f"jm:{1000 + i}")
                     for i in range(100)]
        self.gloss = d / "content/ko-gloss/N5.jsonl"
        self.gloss.write_text("".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) + "\n" for r in self.rows), encoding="utf-8")

    def sample(self, seed=20261103):
        rc, _ = run(self.p, "review-sample", "--target", "ko-gloss/N5", "--ratio", "0.1", "--seed", str(seed))
        self.assertEqual(rc, 0)
        return self.p.reports / f"review/ko-gloss_N5-{seed}.ids"

    def test_sample_deterministic(self):
        f = self.sample()
        h = sha256_file(f)
        self.assertEqual(len(f.read_text().splitlines()), 10)
        self.assertEqual(self.sample() and sha256_file(f), h)
        self.assertNotEqual(self.sample(7).read_text(), f.read_text())

    def make_sheet(self):
        ids = self.sample()
        rc, _ = run(self.p, "review-sheet", "--target", "ko-gloss/N5", "--ids", str(ids))
        self.assertEqual(rc, 0)
        return ids.with_suffix(".tsv"), ids.with_suffix(".html"), ids.read_text().splitlines()

    def test_grammar_view_has_all_ko_fields(self):
        r = {"title": "〜てください", "meaningKo": "뜻", "explanationKo": "설명",
             "connections": [{"posKo": "동사", "form": "て形", "example": "{食|た}べて"}],
             "examples": [{"ja": "…", "ko": "예문"}],
             "contrasts": [{"grammarId": "g2", "pair": [{"ja": "a", "ko": "비교1"}, {"ja": "b", "ko": "비교2"}]}]}
        _, ko, _ = rv._view("grammar", r)
        self.assertEqual(ko, ["뜻", "설명", "동사", "예문", "비교1", "비교2"])

    def test_sheet(self):
        tsv, html, ids = self.make_sheet()
        lines = tsv.read_text().splitlines()
        self.assertEqual(lines[0].split("\t"), ["id", "ja", "ko", "source", "reviewStatus", "verdict", "note", "reviewer"])
        self.assertEqual(len(lines), 11)
        self.assertEqual(lines[1].split("\t")[1], lines[1].split("\t")[0])  # 스냅샷 없는 픽스처는 vocabId로 대체
        with mock.patch.object(rv, "ja_map", lambda p, l: {i: "水 みず" for i in ids}):
            tsv2, _ = rv.sheet(self.p, "ko-gloss/N5", tsv.with_suffix(".ids"))
        self.assertEqual(tsv2.read_text().splitlines()[1].split("\t")[1], "水 みず")
        h = html.read_text()
        self.assertEqual(h.count("<tr>"), 11)
        self.assertIn('lang="ja"', h)
        self.assertIn('lang="ko"', h)

    def fill(self, tsv, values):
        lines = tsv.read_text().splitlines()
        out = [lines[0]]
        for n, l in enumerate(lines[1:]):
            c = l.split("\t")
            if n in values:
                c[5], c[7] = values[n]
            out.append("\t".join(c))
        tsv.write_text("\n".join(out) + "\n")

    def test_apply(self):
        tsv, _, ids = self.make_sheet()
        self.fill(tsv, {0: ("ok", "human"), 1: ("fix", "agent"), 2: ("ok", ""), 3: ("", "human")})
        before = self.gloss.read_text().splitlines()
        rc, _ = run(self.p, "review-apply", "--target", "ko-gloss/N5", "--sheet", str(tsv), "--date", "2026-11-04")
        self.assertEqual(rc, 0)
        after = self.gloss.read_text().splitlines()
        got = {json.loads(l)["vocabId"]: json.loads(l) for l in after}
        self.assertEqual([json.loads(l)["vocabId"] for l in after], [r["vocabId"] for r in self.rows])
        self.assertEqual((got[ids[0]]["reviewStatus"], got[ids[0]]["reviewedAt"], got[ids[0]]["reviewerKind"]), ("reviewed", "2026-11-04", "human"))
        self.assertEqual(got[ids[1]]["reviewerKind"], "agent")
        self.assertEqual(got[ids[2]]["reviewStatus"], "unreviewed")
        self.assertEqual(got[ids[3]]["reviewStatus"], "unreviewed")
        self.assertEqual(got[ids[1]]["meanings"], ["뜻" + str(int(ids[1][3:]) - 1000)])
        changed = [i for i, (a, b) in enumerate(zip(before, after)) if a != b]
        self.assertEqual(len(changed), 2)
        self.assertEqual(list(json.loads(after[changed[0]]).keys()), list(json.loads(before[changed[0]]).keys()))
        fix = tsv.with_name(tsv.stem + "-fix.tsv").read_text().splitlines()
        self.assertEqual(len(fix), 2)
        self.assertTrue(fix[1].startswith(ids[1]))

    def test_batch_sampled(self):
        tsv, _, ids = self.make_sheet()
        self.fill(tsv, {0: ("ok", "human")})
        run(self.p, "review-apply", "--target", "ko-gloss/N5", "--sheet", str(tsv), "--date", "2026-11-04", "--batch-sampled")
        got = [json.loads(l) for l in self.gloss.read_text().splitlines()]
        sampled = [r for r in got if r["reviewStatus"] == "sampled"]
        self.assertEqual(len(sampled), 90)
        self.assertTrue(all(r["reviewerKind"] == "human" and r["reviewedAt"] == "2026-11-04" and r["vocabId"] not in ids for r in sampled))
        self.assertEqual(sum(r["reviewStatus"] == "unreviewed" for r in got), 9)

    def test_apply_authored_json_keeps_format(self):
        d = self.p.authored / "questions/N5"
        d.mkdir(parents=True)
        q = {"id": "q-n5-vocab-0001", "stem": "x", "reviewStatus": "unreviewed", "method": "human", "reviewedAt": None, "reviewerKind": None}
        (d / "q.json").write_text(json.dumps(q, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        ids = self.p.reports / "review/ids.ids"
        ids.parent.mkdir(parents=True)
        ids.write_text("q-n5-vocab-0001\n")
        tsv, _ = rv.sheet(self.p, "questions/N5", ids)
        self.fill(tsv, {0: ("ok", "human")})
        rv.apply(self.p, "questions/N5", tsv, "2026-11-04")
        new = json.loads((d / "q.json").read_text(encoding="utf-8"))
        self.assertEqual(list(new), list(q))
        self.assertEqual(new["reviewStatus"], "reviewed")
        self.assertTrue((d / "q.json").read_text().endswith("}\n"))

    def test_apply_short_row_and_bad_json(self):
        tsv, _, _ = self.make_sheet()
        lines = tsv.read_text().splitlines()
        lines[1] = lines[1].rstrip("\t")
        tsv.write_text("\n".join(lines) + "\n")
        rc, out = run(self.p, "review-apply", "--target", "ko-gloss/N5", "--sheet", str(tsv), "--date", "2026-11-04")
        self.assertEqual(rc, 1)
        d = self.p.authored / "questions/N5"
        d.mkdir(parents=True)
        (d / "a.json").write_text('{"id":"a",')
        with self.assertRaises(rv.Fail):
            rv.similarity_lines(self.p)

    def test_similarity_null_fields(self):
        f = self.p.authored / "questions/N5/q.json"
        f.parent.mkdir(parents=True)
        f.write_text(json.dumps({"id": "q", "stem": "あいう", "passage": None}))
        self.assertEqual(rv.similarity_lines(self.p), [])

    def test_similarity(self):
        def put(kind, name, body):
            f = self.p.authored / kind / "N5" / f"{name}.json"
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(json.dumps(dict(id=name, **body), ensure_ascii=False))
        ja = "{私|わたし}は[[毎日]]学校へ行きます。朝ごはんを食べてから出かけます"
        put("grammar", "g1", {"examples": [{"ja": ja}]})
        put("grammar", "g2", {"examples": [{"ja": "私は毎日学校へ行きます。朝ごはんを食べてから出かけます"}]})
        put("grammar", "g3", {"examples": [{"ja": ja[:-1] + "ね"}]})
        put("grammar", "g4", {"examples": [{"ja": "全然違う文章がここにあります。猫が好きです"}]})
        put("questions", "q1", {"stem": ja, "passage": ""})  # 다른 종류와는 비교하지 않는다
        out = rv.similarity_lines(self.p)
        self.assertEqual(out[0], "FAIL: authored duplicate g1 g2")
        self.assertTrue(out[1].startswith("WARN: similar g1 g3 0.9"))
        self.assertTrue(out[2].startswith("WARN: similar g2 g3 "))
        self.assertEqual(len(out), 3)
        rc, _ = run(self.p, "authored")
        self.assertEqual(rc, 1)


if __name__ == "__main__":
    unittest.main()
