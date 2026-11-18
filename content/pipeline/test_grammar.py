import copy
import json
import shutil
import sqlite3
import subprocess
import sys
import unittest

import build
import check
import pl_grammar as gm
from pl_common import REPO, Paths, read_json
from support import TempRoot

MAN = {"S-04": {"status": "unconfirmed"}, "S-01": {"status": "confirmed"}}


def good(i=1):
    return {"id": f"gr-n5-{i:04d}", "level": "N5", "status": "draft", "needsReview": False, "sources": [],
            "method": "llm:m@2026-11-03", "reviewStatus": "unreviewed", "reviewedAt": None, "reviewerKind": None,
            "title": "〜ます", "meaningKo": "뜻", "explanationKo": "설명",
            "connections": [{"posKo": "동사", "form": "ます形", "example": "{行|い}きます"}],
            "examples": [{"ja": f"[[{{行|い}}きます]]{k}", "ko": "번역"} for k in range(3)]}


def problems(rec, man=MAN, others=()):
    return gm.grammar_problems([rec, *others], man)


class FormatTest(unittest.TestCase):
    def fails(self, mut, text, **kw):
        r = copy.deepcopy(good())
        mut(r)
        out = problems(r, **kw)
        self.assertTrue(any(text in o and o.startswith("FAIL: authored gr-n5-0001") for o in out), out)

    def test_good(self):
        self.assertEqual(problems(good()), [])

    def test_missing_field(self):
        for k in ("title", "meaningKo", "explanationKo", "connections", "examples", "method", "reviewStatus", "needsReview", "sources", "level", "status"):
            r = good()
            del r[k]
            self.assertTrue(problems(r), k)

    def test_duplicate_id(self):
        self.assertTrue(any("duplicate id" in o for o in problems(good(), others=[good()])))

    def test_two_examples(self):
        self.fails(lambda r: r["examples"].pop(), "examples need at least 3")

    def test_ruby_brackets(self):
        self.fails(lambda r: r["examples"][0].update(ja="[[{行|いきます]]"), "ruby bracket mismatch")
        self.fails(lambda r: r["examples"][0].update(ja="[[行|い}きます]]"), "ruby bracket mismatch")
        self.fails(lambda r: r["connections"][0].update(example="{行|い}き}ます"), "ruby bracket mismatch")

    def test_emphasis(self):
        self.fails(lambda r: r["examples"][0].update(ja="{行|い}きます"), "[[ ]] missing")
        self.fails(lambda r: r["examples"][0].update(ja="[[行きます"), "[[ ]] mismatch")
        self.fails(lambda r: r["examples"][0].update(ja="行きます]]"), "[[ ]] mismatch")
        self.fails(lambda r: r["examples"][0].update(ja="[[行[[きます]]]]"), "[[ ]] mismatch")

    def test_unconfirmed_sources(self):
        self.fails(lambda r: r.update(sources=[{"source": "S-04", "ref": "1"}]), "S-04 not confirmed")
        self.fails(lambda r: r.update(sources=[{"source": "S-99", "ref": "1"}]), "not in manifest")

    def test_enums(self):
        self.fails(lambda r: r.update(method="gpt"), "method invalid")
        self.fails(lambda r: r.update(reviewStatus="done"), "reviewStatus invalid")
        self.fails(lambda r: r.update(reviewStatus="reviewed"), "reviewedAt missing")
        self.fails(lambda r: r.update(status="published"), "status invalid")

    def test_korean_fields_reject_markup(self):
        paths = [("meaningKo",), ("explanationKo",), ("examples", 0, "ko"), ("connections", 0, "posKo")]
        for path in paths:
            for bad, text in (("{", "ruby in ko"), ("|", "ruby in ko"), ("}", "ruby in ko"), ("[[", "emphasis in ko"), ("]]", "emphasis in ko")):
                r = good()
                o = r
                for k in path[:-1]:
                    o = o[k]
                o[path[-1]] = "가" + bad + "나"
                self.assertTrue(any(text in x for x in problems(r)), (path, bad))
        r = good()
        r["contrasts"] = [{"grammarId": "gr-n5-0002", "pair": [{"ja": "あ", "ko": "가[[나"}, {"ja": "い", "ko": "다"}]}]
        self.assertTrue(any("emphasis in ko" in x for x in problems(r, others=[good(2)])))

    def test_connections_items(self):
        for k in ("posKo", "form", "example"):
            r = good()
            del r["connections"][0][k]
            self.assertTrue(any("connections[0]" in o for o in problems(r)), k)

    def test_contrasts(self):
        def c(gid, n=2):
            return [{"grammarId": gid, "pair": [{"ja": "あ", "ko": "가"}] * n}]
        r = good()
        r["contrasts"] = c("gr-n5-0002")
        self.assertEqual(problems(r, others=[good(2)]), [])
        self.assertTrue(any("not in build" in o for o in problems(r)))
        r["contrasts"] = c("gr-n5-0001")
        self.assertTrue(any("is itself" in o for o in problems(r)))
        for n in (1, 3):
            r["contrasts"] = c("gr-n5-0002", n)
            self.assertTrue(any("exactly 2" in o for o in problems(r, others=[good(2)])), n)

    def test_example_source_columns(self):
        r = good()
        r["examples"][0].update(ref="1")
        self.assertTrue(any("authored example has ref" in o for o in problems(r)))
        r = good()
        r["examples"][0].update(source="S-04", ref="1", author="a", license="CC0")
        self.assertTrue(any("S-04 not confirmed" in o for o in problems(r)))
        self.assertEqual(problems(r, man={"S-04": {"status": "confirmed"}}), [])
        del r["examples"][0]["author"]
        self.assertTrue(any("needs ref, author, license" in o for o in problems(r, man={"S-04": {"status": "confirmed"}})))


class NonHashableTest(unittest.TestCase):
    def test_grammar_id_list_is_fail_not_typeerror(self):
        r = good()
        r["contrasts"] = [{"grammarId": ["x"], "pair": []}]
        self.assertTrue(any("grammarId" in x for x in problems(r)))


class LoadTest(TempRoot):
    def build(self, p, recs=None):
        if recs is not None:
            d = p.authored / "sample/grammar"
            for f in d.glob("*.json"):
                f.unlink()
            shutil.rmtree(p.authored / "sample/questions", True)  # 샘플 문항의 targets가 바뀐 어휘·문법을 가리킨다
            shutil.rmtree(p.authored / "sample/exams", True)
            for r in recs:
                (d / f"{r['id']}.json").write_text(json.dumps(r, ensure_ascii=False), encoding="utf-8")
        lines = []
        return build.main([], paths=p, out=lines.append), lines

    def rows(self, p, sql):
        con = sqlite3.connect(p.assets / "content.db")
        try:
            return con.execute(sql).fetchall()
        finally:
            con.close()

    def test_sample_loads_and_checks(self):
        p = self.sample_root()
        rc, _ = self.build(p)
        self.assertEqual(rc, 0)
        self.assertEqual(self.rows(p, "SELECT id, sort_key FROM grammar ORDER BY id"), [("gr-n5-0001", 0), ("gr-n5-0002", 10)])
        self.assertEqual(self.rows(p, "PRAGMA user_version"), [(3,)])
        self.assertEqual(self.rows(p, "SELECT ord, source_id, source_ref, author, license FROM grammar_example WHERE grammar_id='gr-n5-0001' ORDER BY ord"),
                         [(0, "authored", None, None, None), (1, "authored", None, None, None), (2, "authored", None, None, None)])
        self.assertEqual(check.run_checks(p), [])
        self.assertEqual((p.ids / "sample/grammar.txt").read_text().split(), ["gr-n5-0001", "gr-n5-0002"])

    def test_columns_and_json(self):
        p = self.sample_root()
        a, b = good(1), good(2)
        a.update(method="human", reviewStatus="reviewed", reviewedAt="2026-11-03", reviewerKind="human", needsReview=True)
        a["contrasts"] = [{"grammarId": "gr-n5-0002", "pair": [{"ja": "[[あ]]", "ko": "가"}, {"ja": "い", "ko": "나"}]}]
        self.assertEqual(self.build(p, [a, b])[0], 0)
        r = self.rows(p, "SELECT ko_source, ko_method, ko_review_status, ko_reviewed_at, ko_reviewer_kind, needs_review, source_id FROM grammar WHERE id='gr-n5-0001'")
        self.assertEqual(r, [("authored", "human", "reviewed", "2026-11-03", "human", 1, "authored")])
        r = self.rows(p, "SELECT ko_method, ko_review_status, ko_reviewed_at, ko_reviewer_kind, needs_review, connections_json, contrasts_json FROM grammar WHERE id='gr-n5-0002'")
        self.assertEqual(r, [("llm:m@2026-11-03", "unreviewed", None, None, 0, json.dumps(b["connections"], ensure_ascii=False, separators=(",", ":"), sort_keys=True), "[]")])
        cj = self.rows(p, "SELECT contrasts_json FROM grammar WHERE id='gr-n5-0001'")[0][0]
        self.assertEqual(json.loads(cj), a["contrasts"])

    def test_sort_key(self):
        for v, want in ((100, [0, 25, 50, 75]), (0, [0, 1, 2, 3])):
            p = self.sample_root()
            voc = p.authored / "sample/vocab.jsonl"
            lines = voc.read_text(encoding="utf-8").splitlines()
            tpl = json.loads(lines[0])
            recs = [dict(tpl, id=f"av-sample-{i + 1:04d}", reading="あ%d" % i) for i in range(v)]
            voc.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in recs), encoding="utf-8")
            (p.authored / "sample/kanji.jsonl").write_text("", encoding="utf-8")
            self.assertEqual(self.build(p, [good(i) for i in (1, 2, 3, 4)])[0], 0, v)
            self.assertEqual([r[0] for r in self.rows(p, "SELECT sort_key FROM grammar ORDER BY id")], want)
            self.assertEqual(self.rows(p, "SELECT COUNT(*) FROM grammar_example"), [(12,)])

    def test_level_outside_config_dropped(self):
        p = self.sample_root()
        n4 = good(1)
        n4.update(id="gr-n4-0001", level="N4")
        self.assertEqual(self.build(p, [good(1), n4])[0], 0)
        self.assertEqual(self.rows(p, "SELECT id FROM grammar"), [("gr-n5-0001",)])
        self.assertEqual(self.rows(p, "SELECT value FROM content_meta WHERE key='levels'"), [("5",)])

    def test_s04_unconfirmed_stops_confirmed_passes(self):
        r = good()
        r["examples"][0].update(source="S-04", ref="9", author="u", license="CC0 1.0")
        p = self.sample_root()
        self.assertEqual(self.build(p, [r])[0], 1)
        self.assertFalse((p.assets / "content.db").exists())
        man = read_json(p.manifest)
        next(s for s in man if s["id"] == "S-04")["status"] = "confirmed"
        p.manifest.write_text(json.dumps(man, ensure_ascii=False), encoding="utf-8")
        self.assertEqual(self.build(p, [r])[0], 0)
        self.assertEqual(self.rows(p, "SELECT source_id, source_ref, author, license FROM grammar_example WHERE ord=0"), [("S-04", "9", "u", "CC0 1.0")])

    def test_check_blocks_bad_example_source_columns(self):
        p = self.sample_root()
        self.build(p)
        con = sqlite3.connect(p.assets / "content.db")
        con.execute("UPDATE grammar_example SET author='x' WHERE grammar_id='gr-n5-0001' AND ord=0")
        con.commit()
        con.close()
        self.assertTrue(any(o.startswith("FAIL: refs grammar_example gr-n5-0001#0") for o in check.run_checks(p)))

    def test_retired_not_in_db(self):
        p = self.sample_root()
        a = good(1)
        a["status"] = "retired"
        (p.ids / "sample").mkdir(parents=True, exist_ok=True)
        self.assertEqual(self.build(p, [a, good(2)])[0], 0)
        self.assertEqual(self.rows(p, "SELECT id FROM grammar"), [("gr-n5-0002",)])

    def test_format_error_stops_build(self):
        p = self.sample_root()
        r = good()
        r["examples"].pop()
        rc, _ = self.build(p, [r])
        self.assertEqual(rc, 1)


class NoticesGrammarTest(TempRoot):
    def run_build(self, p):
        return build.main([], paths=p, out=lambda *_: None)

    def notices(self, p):
        return {n["id"]: n for n in read_json(p.assets / "attribution.json")["notices"]}

    def test_with_grammar(self):
        p = self.sample_root()
        self.assertEqual(self.run_build(p), 0)
        tpl = read_json(p.notices)["notices"]
        n = self.notices(p)
        self.assertEqual(n["N-11"], {"id": "N-11", **tpl["N-11"]})
        self.assertTrue(n["N-09"]["body"].startswith(tpl["N-09"]["grammarPrefix"]))
        for x in n.values():
            self.assertNotRegex(x["body"], r"\{[^{}\s]*\}")
        self.assertEqual(check.run_checks(p), [])

    def test_without_grammar_matches_template(self):
        p = self.sample_root()
        for f in (p.authored / "sample/grammar").glob("*.json"):
            f.unlink()
        shutil.rmtree(p.authored / "sample/questions")
        self.assertEqual(self.run_build(p), 0)
        n = self.notices(p)
        self.assertNotIn("N-11", n)
        tpl = read_json(p.notices)["notices"]["N-09"]
        self.assertNotIn(tpl["grammarPrefix"], n["N-09"]["body"])
        self.assertEqual(n["N-09"]["body"], tpl["body"].replace("{levelListName}", read_json(p.assets / "attribution.json")["levelListName"]))
        self.assertNotRegex(n["N-09"]["body"], r"\{[^{}\s]*\}")

    def test_question_only_build_has_no_prefix(self):
        import pl_attr as at
        tpl = read_json(self.sample_root().notices)
        facts = {"meta": {"kind": "sample", "content_version": "v1"}, "used": {"authored"}, "pairs": [("authored", "human")], "counts": {"question": 3}}
        a = at.build_attribution({}, facts, tpl)
        ids = [n["id"] for n in a["notices"]]
        self.assertIn("N-11", ids)
        self.assertNotIn(tpl["notices"]["N-09"]["grammarPrefix"], a["notices"][0]["body"])
        facts["counts"] = {"grammar": 1}
        tpl["notices"]["N-09"].pop("grammarPrefix")
        with self.assertRaises(Exception) as cm:
            at.build_attribution({}, facts, tpl)
        self.assertIn("grammarPrefix", str(cm.exception))

    def test_missing_prefix_key_fails_build(self):
        p = self.sample_root()
        t = read_json(p.notices)
        del t["notices"]["N-09"]["grammarPrefix"]
        p.notices.write_text(json.dumps(t, ensure_ascii=False), encoding="utf-8")
        self.assertEqual(self.run_build(p), 1)

    def test_check_blocks_missing_n11_and_prefix(self):
        p = self.sample_root()
        self.run_build(p)
        f = p.assets / "attribution.json"
        a = read_json(f)
        a["notices"] = [n for n in a["notices"] if n["id"] != "N-11"]
        f.write_text(json.dumps(a, ensure_ascii=False), encoding="utf-8")
        out = check.run_checks(p)
        self.assertIn("FAIL: attribution N-11 missing while grammar or question rows exist", out)
        a["notices"][0]["body"] = "x"
        f.write_text(json.dumps(a, ensure_ascii=False), encoding="utf-8")
        self.assertTrue(any("grammarPrefix" in o for o in check.run_checks(p)))


class RepoTest(unittest.TestCase):
    def test_sample_ids_listed_and_schema_untouched(self):
        ids = set((REPO / "content/ids/sample/grammar.txt").read_text().split())
        have = {f.stem for f in (REPO / "content/authored/sample/grammar").glob("*.json")}
        self.assertEqual(len(have), 2)
        self.assertEqual(ids, have)
        self.assertEqual(sorted(p.name for p in Paths().schemas.glob("*.json")), ["1.json", "2.json", "3.json"])
        self.assertEqual(read_json(Paths().schemas / "2.json")["database"]["version"], 2)

    def test_committed_n5_grammar(self):
        # M16: N5 문법 20개 이상, 직접 작성(sources·예문 출처 없음), LLM 작성(검수 상태는 review-apply로 바뀔 수 있음), 유사 검사 0건.
        import pl_review as rv
        recs, _ = gm.load_grammar(Paths(), "release", read_json(Paths().manifest), [5])
        self.assertGreaterEqual(len(recs), 20)
        bad = [r["id"] for r in recs if r["sources"] != [] or r["method"] == "human" or r["reviewStatus"] not in ("unreviewed", "sampled", "reviewed")
               or any(k in e for e in r["examples"] for k in ("source", "ref", "author", "license"))]
        self.assertEqual(bad, [])
        self.assertEqual([l for l in rv.similarity_lines(Paths()) if " gr-" in l], [])


if __name__ == "__main__":
    unittest.main()
