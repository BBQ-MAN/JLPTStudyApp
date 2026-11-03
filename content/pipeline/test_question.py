import copy
import json
import shutil
import sqlite3
import unittest

import build
import check
import pl_question as qs
from pl_common import REPO, Paths, jdump, read_json
from support import TempRoot

MAN = {"S-01": {"status": "confirmed"}}
KNOWN = {"av-sample-0004", "gr-n5-0001"}


def good(kind="kanji-reading"):
    base = {"id": "q-n5-vocab-0001", "level": "N5", "section": "vocab", "status": "draft", "needsReview": False, "sources": [],
            "method": "llm:m@2026-11-03", "reviewStatus": "unreviewed", "reviewedAt": None, "reviewerKind": None,
            "type": kind, "choices": ["あ", "い", "う", "え"], "answerIndex": 1, "explanationKo": "해설",
            "whyWrongKo": ["가", None, "다", "라"], "targets": []}
    return {**base, **{
        "kanji-reading": dict(stem="[[山]]へ行く。", target="山"),
        "context": dict(stem="＿＿を読む。"),
        "usage": dict(stem="x", target="山"),
        "sentence-order": dict(id="q-n5-grammar-0001", section="grammar", stem="これは ＿＿ ＿＿ ＿★＿ ＿＿ です。"),
        "paraphrase": dict(stem="[[{山|やま}]]へ行く。", target="山"),
    }[kind]}


def problems(r, **kw):
    return qs.question_problems([r], MAN, **kw)


class FormatTest(unittest.TestCase):
    def fails(self, mut, text, kind="kanji-reading", **kw):
        r = copy.deepcopy(good(kind))
        mut(r)
        out = problems(r, **kw)
        self.assertTrue(any(text in o and o.startswith(f"FAIL: authored {r['id']} ") for o in out), out)

    def test_good(self):
        for k in ("kanji-reading", "context", "usage", "sentence-order", "paraphrase"):
            self.assertEqual(problems(good(k), known=KNOWN), [], k)

    def test_choices_answer_targets(self):
        self.fails(lambda r: r["choices"].pop(), "choices need exactly 4")
        self.fails(lambda r: r["choices"].append("お"), "choices need exactly 4")
        for v in (4, -1, "1", None, True):
            self.fails(lambda r: r.update(answerIndex=v), "answerIndex out of range")
        self.fails(lambda r: r.update(targets=["gr-n5-9999"]), "targets gr-n5-9999 not found", known=KNOWN)
        self.assertEqual(problems(dict(good(), targets=["gr-n5-0001"]), known=KNOWN), [])

    def test_non_string_fields_fail_not_crash(self):
        for k in ("id", "section", "type", "status", "reviewStatus", "level"):
            for v in (["a"], {"a": 1}):
                r = dict(good(), **{k: v})
                out = problems(r)
                self.assertTrue(any(o.startswith("FAIL: authored ") for o in out), (k, out))

    def test_all_none_of_above(self):
        for c in ("すべて正しい", "どれも正しくない", "正解なし", "上記のすべて", "모두 맞음", "정답 없음"):
            self.fails(lambda r: r["choices"].__setitem__(3, c), "all/none-of-the-above")

    def test_type_and_section(self):
        self.fails(lambda r: r.update(type="kanji"), "type")
        self.fails(lambda r: r.update(type="short"), "type")  # reading 코드는 vocab에 쓸 수 없다
        self.fails(lambda r: r.update(section="grammar"), "section differs from id")
        self.fails(lambda r: r.update(section="listening"), "section invalid")
        self.fails(lambda r: r.update(id="q-n4-vocab-0001"), "level differs from id")

    def test_enums_and_ko_markup(self):
        self.fails(lambda r: r.update(method="gpt"), "method invalid")
        self.fails(lambda r: r.update(reviewStatus="done"), "reviewStatus invalid")
        for bad, text in (("{", "ruby in ko"), ("|", "ruby in ko"), ("}", "ruby in ko"), ("[[", "emphasis in ko"), ("]]", "emphasis in ko")):
            self.fails(lambda r: r.update(explanationKo="가" + bad + "나"), text)
            self.fails(lambda r: r["whyWrongKo"].__setitem__(0, "가" + bad + "나"), text)

    def test_why_wrong(self):
        self.fails(lambda r: r["whyWrongKo"].pop(), "whyWrongKo needs exactly 4")
        self.fails(lambda r: r["whyWrongKo"].__setitem__(1, "정답 자리"), "whyWrongKo[1] must be null")
        self.fails(lambda r: r["whyWrongKo"].__setitem__(0, ""), "whyWrongKo[0] must be a non-empty")
        self.fails(lambda r: r["whyWrongKo"].__setitem__(0, None), "whyWrongKo[0] must be a non-empty")

    def test_target_rules(self):
        self.fails(lambda r: r.pop("target"), "target missing")
        self.fails(lambda r: r.update(target="川"), "target differs")
        self.fails(lambda r: r.update(stem="山へ行く。"), "needs exactly one [[ ]]")
        self.fails(lambda r: r.update(stem="[[山]]と[[川]]"), "needs exactly one [[ ]]")
        self.fails(lambda r: r.update(stem="[[{山|やま}]]へ行く。"), "ruby inside [[ ]]")
        self.fails(lambda r: r.pop("target"), "target missing", kind="usage")
        self.fails(lambda r: r.update(target="山"), "not allowed", kind="context")
        self.fails(lambda r: r.pop("target"), "target missing", kind="paraphrase")
        self.assertEqual(problems(dict(good("usage"), stem="[[山]]へ"), known=KNOWN), [])  # 용법은 [[ ]] 없어도, 있어도
        self.assertEqual(problems(dict(good("context"), stem="[[山]]へ＿＿")), [])
        self.assertEqual(problems(dict(good("paraphrase")), known=KNOWN), [])  # 루비는 유의 표현만 허용
        r = good()
        r.update(stem="", passage="[[山]]へ行く。")  # stem이 비면 passage에서 찾는다
        self.assertEqual(problems(r), [])

    def test_blank_tokens(self):
        so = "sentence-order"
        for stem in ("これは ＿＿ ＿＿ ＿＿ ＿＿", "これは ＿＿ ＿＿ ＿★＿ ＿★＿", "これは ＿＿ ＿＿ ＿★＿", "これは ＿＿ ＿＿ ＿＿ ＿★＿ ＿＿", "これは"):
            self.fails(lambda r: r.update(stem=stem), "blank", kind=so)
        for stem in ("ない。", "＿＿と＿★＿"):
            self.fails(lambda r: r.update(stem=stem), "blank", kind="context")
        self.fails(lambda r: r.update(stem="＿＿だ [[山]]", target="山"), "blank")  # 그 밖의 유형에 ＿＿
        self.fails(lambda r: r.update(stem="[[山]]＿★＿", target="山"), "blank")
        for stem in ("これは＿だ。", "これは★だ。", "＿＿＿だ。", "これは＿＿ ★"):
            self.fails(lambda r: r.update(stem=stem), "blank", kind="context")
        self.fails(lambda r: r.update(stem="{＿＿|あ}だ"), "blank", kind="context")
        self.fails(lambda r: r.update(stem="＿＿だ [[＿＿]]"), "blank", kind="context")
        self.fails(lambda r: r.update(passage="＿★＿"), "blank", kind="context")
        self.assertEqual(problems(dict(good("context"), stem="x", passage="＿＿")), [])
        self.fails(lambda r: r["choices"].__setitem__(0, "＿"), "blank", kind="context")
        self.fails(lambda r: r.update(stem="これは ＿＿ ＿★＿", passage="＿＿ ＿＿"), "blank", kind=so)  # stem만 센다
        self.fails(lambda r: r.update(stem="読む＿", passage="＿"), "blank", kind="context")  # 필드 경계
        self.fails(lambda r: r.update(stem="{本|＿＿}を読む。"), "blank", kind="context")  # 루비 읽기

    def test_non_string_fields(self):
        for k, v in (("id", ["x"]), ("section", ["a"]), ("type", ["a"])):
            self.assertTrue(any("FAIL" in p for p in problems(dict(good(), **{k: v}))))

    def test_distribution(self):
        recs = [dict(good("context"), id=f"q-n5-vocab-{i:04d}", answerIndex=i % 4 if i > 4 else 0, whyWrongKo=["a"] * 4)
                for i in range(1, 11)]
        for r in recs:
            r["whyWrongKo"][r["answerIndex"]] = None
        self.assertTrue(any("answer position 0 is 5/10" in o for o in qs.question_problems(recs, MAN)))
        recs[1]["answerIndex"] = 1  # 4/10 = 40% 는 통과
        recs[1]["whyWrongKo"] = ["a", None, "a", "a"]
        recs[0]["answerIndex"] = 2
        recs[0]["whyWrongKo"] = ["a", "a", None, "a"]
        self.assertEqual([o for o in qs.question_problems(recs, MAN) if "answer position" in o], [])
        self.assertEqual(qs.distribution_problems(recs[:9]), [])  # 10개 미만이면 검사 없음

    def test_duplicate(self):
        self.assertTrue(any("duplicate id" in o for o in qs.question_problems([good(), good()], MAN)))


def exam():
    return {"level": "N5", "sourceUrl": "https://www.jlpt.jp/x", "checkedAt": "2026-11-02",
            "sections": [{"id": "vocab", "titleKo": "어휘", "minutes": 20, "parts": [{"section": "vocab", "type": "context", "count": 3}]}]}


class ExamFormatTest(unittest.TestCase):
    def test_good(self):
        self.assertEqual(qs.exam_problems(exam(), "N5"), [])

    def fails(self, mut, text):
        e = exam()
        mut(e)
        self.assertTrue(any(text in o and o.startswith("FAIL: authored exam N5") for o in qs.exam_problems(e, "N5")), qs.exam_problems(e, "N5"))

    def test_fields(self):
        self.fails(lambda e: e.pop("sourceUrl"), "sourceUrl")
        self.fails(lambda e: e.pop("checkedAt"), "checkedAt")
        self.fails(lambda e: e["sections"][0]["parts"][0].update(type="nope"), "type")
        self.fails(lambda e: e["sections"][0]["parts"][0].update(type="short"), "type")  # section vocab에 reading 코드
        self.fails(lambda e: e["sections"][0]["parts"][0].update(count=0), "count")
        for bad in (["a"], {"a": 1}):  # 손으로 쓴 파일의 잘못된 타입은 TypeError가 아니라 FAIL 줄
            self.fails(lambda e, b=bad: e["sections"][0]["parts"][0].update(section=b), "section invalid")
            self.fails(lambda e, b=bad: e["sections"][0]["parts"][0].update(type=b), "type")
        self.fails(lambda e: e.update(sections=[]), "sections")
        self.fails(lambda e: e.update(level="N4"), "level")


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
        (p.authored / "sample/questions" / f"{r['id']}.json").write_text(json.dumps(r, ensure_ascii=False), encoding="utf-8")

    def test_sample_loads_and_checks(self):
        p = self.sample_root()
        self.assertEqual(self.build(p), 0)
        self.assertEqual(self.rows(p, "SELECT id, sort_key, target FROM question ORDER BY id"),
                         [("q-n5-grammar-0001", 1, None), ("q-n5-grammar-0002", 2, None), ("q-n5-vocab-0001", 1, "山"), ("q-n5-vocab-0002", 2, None)])
        self.assertEqual(self.rows(p, "PRAGMA user_version"), [(2,)])
        self.assertEqual(check.run_checks(p), [])
        self.assertEqual((p.ids / "sample/question.txt").read_text().split(), sorted(f.stem for f in (p.authored / "sample/questions").glob("*.json")))

    def test_columns(self):
        p = self.sample_root()
        r = read_json(p.authored / "sample/questions/q-n5-vocab-0001.json")
        r.update(method="human", reviewStatus="reviewed", reviewedAt="2026-11-03", reviewerKind="human", needsReview=True, passage="p")
        self.write(p, r)
        self.assertEqual(self.build(p), 0)
        got = self.rows(p, "SELECT ko_source, ko_method, ko_review_status, ko_reviewed_at, ko_reviewer_kind, needs_review, source_id, target, passage FROM question WHERE id='q-n5-vocab-0001'")
        self.assertEqual(got, [("authored", "human", "reviewed", "2026-11-03", "human", 1, "authored", "山", "p")])
        got = self.rows(p, "SELECT ko_method, ko_review_status, ko_reviewed_at, ko_reviewer_kind, needs_review, choices_json, why_wrong_ko_json, targets_json, answer_index FROM question WHERE id='q-n5-vocab-0002'")
        q = read_json(p.authored / "sample/questions/q-n5-vocab-0002.json")
        self.assertEqual(got, [("llm:claude-sonnet-5-5@2026-11-03", "unreviewed", None, None, 1, jdump(q["choices"]), jdump(q["whyWrongKo"]), '["av-sample-0007"]', 0)])

    def test_exam_template_row(self):
        p = self.sample_root()
        self.assertEqual(self.build(p), 0)
        src = read_json(p.authored / "sample/exams/N5.json")
        got = self.rows(p, "SELECT level, template_json, source_url, checked_at, source_id FROM exam_template")
        self.assertEqual(got, [(5, jdump({"sections": src["sections"]}), src["sourceUrl"], src["checkedAt"], "authored")])
        self.assertTrue(got[0][1].startswith('{"sections":[{"id":'))
        self.assertIn("언어지식(문자·어휘)", got[0][1])  # ensure_ascii=False

    def test_missing_target_stops_build(self):
        p = self.sample_root()
        r = read_json(p.authored / "sample/questions/q-n5-vocab-0001.json")
        r["targets"] = ["gr-n5-9999"]
        self.write(p, r)
        self.assertEqual(self.build(p), 1)
        self.assertFalse((p.assets / "content.db").exists())

    def test_n11_with_questions_only(self):
        p = self.sample_root()
        shutil.rmtree(p.authored / "sample/grammar")
        for f in (p.authored / "sample/questions").glob("*grammar*"):  # targets가 문법 ID를 가리키는 문항은 뺀다
            f.unlink()
        r = read_json(p.authored / "sample/questions/q-n5-vocab-0001.json")
        self.assertEqual(self.build(p), 0)
        self.assertEqual(self.rows(p, "SELECT COUNT(*) FROM grammar"), [(0,)])
        self.assertGreater(self.rows(p, "SELECT COUNT(*) FROM question")[0][0], 0)
        a = read_json(p.assets / "attribution.json")
        n11 = next(n for n in a["notices"] if n["id"] == "N-11")
        tpl = read_json(p.notices)["notices"]["N-11"]
        self.assertEqual((n11["title"], n11["body"]), (tpl["title"], tpl["body"]))
        self.assertNotIn(read_json(p.notices)["notices"]["N-09"]["grammarPrefix"], a["notices"][0]["body"])
        self.assertEqual(check.run_checks(p), [])
        del r

    def test_check_blocks_bad_target_and_missing_n11(self):
        p = self.sample_root()
        self.build(p)
        con = sqlite3.connect(p.assets / "content.db")
        con.execute("UPDATE question SET targets_json='[\"zz\"]' WHERE id='q-n5-vocab-0001'")
        con.commit()
        con.close()
        self.assertTrue(any(o.startswith("FAIL: refs question q-n5-vocab-0001 target zz") for o in check.run_checks(p)))


class RepoTest(unittest.TestCase):
    def test_sample_ids_listed_and_schema_untouched(self):
        ids = set((REPO / "content/ids/sample/question.txt").read_text().split())
        have = {f.stem for f in (REPO / "content/authored/sample/questions").glob("*.json")}
        self.assertEqual(len(have), 4)
        self.assertEqual(ids, have)
        self.assertEqual(len(list((REPO / "content/authored/sample/exams").glob("*.json"))), 1)
        self.assertEqual(sorted(p.name for p in Paths().schemas.glob("*.json")), ["1.json", "2.json"])
        self.assertEqual(read_json(Paths().schemas / "2.json")["database"]["version"], 2)


if __name__ == "__main__":
    unittest.main()
