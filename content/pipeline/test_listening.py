import copy
import json
import sqlite3
import unittest

import build
import check
import pl_listening as ls
from pl_common import REPO, Paths, jdump, read_json
from support import TempRoot

MAN = {"S-01": {"status": "confirmed"}}


def good():
    return {"id": "ls-n5-0001", "level": "N5", "status": "draft", "needsReview": False, "sources": [],
            "method": "llm:m@2026-11-03", "reviewStatus": "unreviewed", "reviewedAt": None, "reviewerKind": None,
            "type": "point", "lines": [{"speaker": "F", "text": "{駅|えき}へ行く。"}, {"speaker": "N", "text": "どこへ行きますか。", "ttsText": "どこへいきますか。"}],
            "question": "{駅|えき}ですか。", "choices": ["あ", "い", "う", "え"], "answerIndex": 1, "explanationKo": "해설",
            "whyWrongKo": ["가", None, "다", "라"], "targets": []}


def problems(r, **kw):
    return ls.listening_problems([r], MAN, **kw)


class FormatTest(unittest.TestCase):
    def fails(self, mut, text):
        r = copy.deepcopy(good())
        mut(r)
        out = problems(r)
        self.assertTrue(any(text in o and o.startswith("FAIL: authored ls-n5-0001 ") for o in out), out)

    def test_good(self):
        self.assertEqual(problems(good(), known=set()), [])
        r = good()
        del r["question"]
        self.assertEqual(problems(r), [])

    def test_lines(self):
        self.fails(lambda r: r.update(lines=[]), "lines empty")
        self.fails(lambda r: r.pop("lines"), "lines empty")
        for v in ("X", "m", None, 1):
            self.fails(lambda r: r["lines"][0].update(speaker=v), "speaker invalid")
        for bad in ("{", "|", "}", "[[", "]]"):
            self.fails(lambda r: r["lines"][1].update(ttsText="あ" + bad), "ttsText must be plain")
        self.fails(lambda r: r["lines"][1].update(ttsText=""), "ttsText must be a non-empty")
        self.fails(lambda r: r["lines"][0].update(text="[[駅]]へ"), "[[ ]]")
        self.fails(lambda r: r["lines"][0].update(text="{駅|えき へ"), "ruby bracket mismatch")
        self.fails(lambda r: r["lines"][0].update(text="駅(えき)}"), "ruby bracket mismatch")

    def test_question(self):
        self.fails(lambda r: r.update(question="[[駅]]"), "question has [[ ]]")
        self.fails(lambda r: r.update(question=""), "question empty")
        self.fails(lambda r: r.update(question=None), "question empty")

    def test_type_choices_answer(self):
        self.fails(lambda r: r.update(type="vocab"), "is not a listening code")
        self.fails(lambda r: r.update(type="short"), "is not a listening code")
        self.fails(lambda r: r["choices"].pop(), "choices need exactly 4")
        for v in (4, -1, "1", None, True):
            self.fails(lambda r: r.update(answerIndex=v), "answerIndex out of range")
        self.fails(lambda r: r.update(whyWrongKo=["가", "x", "다", "라"]), "must be null")
        out = problems(dict(good(), targets=["zz"]), known={"a"})
        self.assertTrue(any("targets zz not found" in o for o in out), out)

    def test_source_fields(self):
        self.fails(lambda r: r.update(method="nope"), "method invalid")
        self.fails(lambda r: r.update(reviewStatus="done"), "reviewStatus invalid")
        self.fails(lambda r: r.update(reviewStatus="reviewed", reviewedAt=None), "reviewedAt missing")

    def test_ko_field_markup(self):
        for bad in ("{", "|", "}", "[[", "]]"):
            self.fails(lambda r: r.update(explanationKo="해설" + bad), "in ko field")
            self.fails(lambda r: r["whyWrongKo"].__setitem__(0, "x" + bad), "in ko field")

    def test_id_level(self):
        self.assertIn("FAIL: authored ls-n5-1 id format", problems(dict(good(), id="ls-n5-1")))
        self.fails(lambda r: r.update(level="N4"), "level differs")


class LoadTest(TempRoot):
    def build(self, p):
        return build.main([], paths=p, out=lambda *_: None)

    def rows(self, p, sql):
        con = sqlite3.connect(p.assets / "content.db")
        try:
            return con.execute(sql).fetchall()
        finally:
            con.close()

    def write(self, p, r):
        (p.authored / "sample/listening" / f"{r['id']}.json").write_text(json.dumps(r, ensure_ascii=False), encoding="utf-8")

    def test_sample_loads_and_checks(self):
        p = self.sample_root()
        self.assertEqual(self.build(p), 0)
        self.assertEqual(self.rows(p, "SELECT id, level, sort_key, type, source_id, ko_source FROM listening ORDER BY id"),
                         [("ls-n5-0001", 5, 1, "point", "authored", "authored"), ("ls-n5-0002", 5, 2, "quick-response", "authored", "authored")])
        self.assertEqual(self.rows(p, "PRAGMA user_version"), [(3,)])
        self.assertEqual(check.run_checks(p), [])
        self.assertEqual((p.ids / "sample/listening.txt").read_text().split(), sorted(f.stem for f in (p.authored / "sample/listening").glob("*.json")))

    def test_question_null_and_tts_key(self):
        p = self.sample_root()
        self.assertEqual(self.build(p), 0)
        self.assertEqual(self.rows(p, "SELECT id, question IS NULL, typeof(question) FROM listening ORDER BY id"),
                         [("ls-n5-0001", 0, "text"), ("ls-n5-0002", 1, "null")])
        lines = json.loads(self.rows(p, "SELECT lines_json FROM listening WHERE id='ls-n5-0001'")[0][0])
        self.assertEqual([("ttsText" in ln) for ln in lines], [False, True, False])
        self.assertEqual(self.rows(p, "SELECT lines_json FROM listening WHERE id='ls-n5-0002'")[0][0], '[{"speaker":"M","text":"ありがとうございました。"}]')

    def test_columns(self):
        p = self.sample_root()
        r = read_json(p.authored / "sample/listening/ls-n5-0001.json")
        r.update(method="human", reviewStatus="reviewed", reviewedAt="2026-11-03", reviewerKind="human", needsReview=True)
        self.write(p, r)
        self.assertEqual(self.build(p), 0)
        got = self.rows(p, "SELECT ko_source, ko_method, ko_review_status, ko_reviewed_at, ko_reviewer_kind, needs_review, choices_json, why_wrong_ko_json, targets_json, answer_index FROM listening WHERE id='ls-n5-0001'")
        self.assertEqual(got, [("authored", "human", "reviewed", "2026-11-03", "human", 1, jdump(r["choices"]), jdump(r["whyWrongKo"]), "[]", 1)])
        got = self.rows(p, "SELECT ko_method, ko_review_status, ko_reviewed_at, ko_reviewer_kind, needs_review FROM listening WHERE id='ls-n5-0002'")
        self.assertEqual(got, [("llm:claude-sonnet-5-5@2026-11-03", "unreviewed", None, None, 1)])

    def test_bad_file_stops_build(self):
        p = self.sample_root()
        r = read_json(p.authored / "sample/listening/ls-n5-0001.json")
        r["lines"][0]["speaker"] = "X"
        self.write(p, r)
        self.assertEqual(self.build(p), 1)
        self.assertFalse((p.assets / "content.db").exists())

    def test_check_blocks_missing_n12(self):
        p = self.sample_root()
        self.build(p)
        a = read_json(p.assets / "attribution.json")
        n12 = next(n for n in a["notices"] if n["id"] == "N-12")
        self.assertTrue(n12["title"] and n12["body"])
        a["notices"] = [n for n in a["notices"] if n["id"] != "N-12"]
        (p.assets / "attribution.json").write_text(json.dumps(a, ensure_ascii=False), encoding="utf-8")
        self.assertIn("FAIL: attribution N-12 missing while listening rows exist", check.run_checks(p))

    def test_check_blocks_audio_file(self):
        p = self.sample_root()
        self.build(p)
        (p.assets / "ls-n5-0001.mp3").write_bytes(b"x")
        self.assertTrue(any(o.startswith("FAIL: forbidden-file") and "ls-n5-0001.mp3" in o for o in check.run_checks(p)))


class RepoTest(unittest.TestCase):
    def test_sample_ids_and_n12_template_and_schema(self):
        have = {f.stem for f in (REPO / "content/authored/sample/listening").glob("*.json")}
        self.assertEqual(len(have), 2)
        self.assertEqual(set((REPO / "content/ids/sample/listening.txt").read_text().split()), have)
        n12 = read_json(REPO / "content/notices.ko.json")["notices"]["N-12"]  # D-04 A 문구(sources.md N-12.listening-audio)
        self.assertEqual(n12["title"], "청해 음성")
        self.assertTrue(n12["body"].startswith("청해 음성은 이 기기의 음성 합성(TTS) 엔진이"))
        self.assertEqual(sorted(p.name for p in Paths().schemas.glob("*.json")), ["1.json", "2.json", "3.json"])
        self.assertEqual(read_json(Paths().schemas / "2.json")["database"]["version"], 2)

    def test_committed_n5_listening(self):
        # M25: N5 대본 15개 이상, 직접 작성, 유사 검사 0건, 숫자·조수사·동형이음어가 있는 줄은 ttsText가 있고 ttsText는 그것을 가나로 풀었다.
        import re
        import pl_grammar as gm
        import pl_review as rv
        recs, _ = ls.load_listening(Paths(), "release", read_json(Paths().manifest), [5])
        self.assertGreaterEqual(len(recs), 15)
        bad = [r["id"] for r in recs if r["sources"] != [] or r["method"] == "human" or not r["targets"]
               or r["reviewStatus"] not in ("unreviewed", "sampled", "reviewed")]
        self.assertEqual(bad, [])
        self.assertEqual([l for l in rv.similarity_lines(Paths()) if " ls-" in l], [])
        risky = re.compile(r"[0-9０-９一二三四五六七八九十百千万半何]|今日|明日|昨日|家")
        no_tts, unresolved = [], []
        for r in recs:
            for i, ln in enumerate(r["lines"]):
                if risky.search(gm.RUBY.sub(r"\1", ln["text"])) and "ttsText" not in ln:
                    no_tts.append(f"{r['id']}#{i}")
                if risky.search(ln.get("ttsText", "")):
                    unresolved.append(f"{r['id']}#{i}")
        self.assertEqual(no_tts, [])
        self.assertEqual(unresolved, [])


if __name__ == "__main__":
    unittest.main()
