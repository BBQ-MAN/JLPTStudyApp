import unittest

import pl_authored as au
import pl_db as db


def ds_with(vocab, info_chars):
    ds = db.Dataset("release")
    ds.kanji_source, ds.link_source = "S-02", "S-01"
    ds.vocab = [{"id": i, "written": w, "level": lv, "sort_key": sk} for i, w, lv, sk in vocab]
    ds.kanji_info = {c: {"stroke_count": 1, "freq": None, "on": [], "kun": [], "ko": []} for c in info_chars}
    return ds


class KanjiDeriveTest(unittest.TestCase):
    def test_kanjidic_fields_carried_to_row(self):
        ds = ds_with([("jm:1", "水", 5, 0)], "水")
        ds.kanji_info["水"] = {"stroke_count": 4, "freq": 228, "on": ["スイ"], "kun": ["みず"], "ko": ["수"]}
        k = db.derive_kanji(ds)[0][0]
        self.assertEqual((k["stroke_count"], k["freq"], k["on_readings"], k["kun_readings"], k["korean_readings"]),
                         (4, 228, '["スイ"]', '["みず"]', '["수"]'))

    def test_repeat_mark_not_kanji_and_missing_listed(self):
        ds = ds_with([("jm:2", "時々", 5, 0), ("jm:9", "鬱", 5, 1)], "時々")
        kanji, vk, missing = db.derive_kanji(ds)
        self.assertEqual([k["literal"] for k in kanji], ["時"])  # 々는 KANJIDIC2에 있어도 넣지 않는다
        self.assertEqual(vk, [{"kanji": "時", "vocab_id": "jm:2", "source_id": "S-01"}])
        self.assertEqual(missing, [("鬱", "jm:9")])  # KANJIDIC2에 없는 CJK 문자

    def test_sort_key_is_first_word_sort_key(self):
        ds = ds_with([("jm:1", "日本人", 5, 0), ("jm:2", "人本", 5, 1)], "日本人")
        kanji = db.derive_kanji(ds)[0]
        self.assertEqual([(k["literal"], k["sort_key"]) for k in kanji], [("日", 0), ("本", 0), ("人", 0)])  # 첫 단어의 sort_key 그대로, 동률은 앱이 처리

    def test_first_word_wins_and_level_is_easiest(self):
        ds = ds_with([("jm:1", "水", 4, 0), ("jm:2", "犬水", 5, 3)], "水犬")
        kanji = {k["literal"]: k for k in db.derive_kanji(ds)[0]}
        self.assertEqual((kanji["水"]["level"], kanji["水"]["sort_key"]), (5, 3))  # N5 단어 jm:2의 sort_key
        self.assertEqual((kanji["犬"]["level"], kanji["犬"]["sort_key"]), (5, 3))
        self.assertEqual(kanji["犬"]["source_id"], "S-02")

    def test_sort_key_not_renumbered_per_level(self):
        ds = ds_with([("jm:1", "水", 4, 0), ("jm:2", "犬", 5, 0)], "水犬")
        self.assertEqual([(k["literal"], k["level"], k["sort_key"]) for k in db.derive_kanji(ds)[0]], [("犬", 5, 0), ("水", 4, 0)])


GOOD = {"id": "av-sample-0001", "written": None, "reading": "あ", "level": 5, "pos": "n", "meanings": ["가"], "koSource": "authored",
        "method": "llm:m@2026-11-02", "reviewStatus": "unreviewed", "reviewedAt": None, "reviewerKind": None}


class AuthoredTest(unittest.TestCase):
    def test_good_record_passes(self):
        self.assertEqual(au.vocab_problems([GOOD]), [])

    def test_ruby_and_emphasis_in_ko_field(self):
        for text, want in (("{水|みず}", "ruby"), ("a|b", "ruby"), ("x}", "ruby"), ("[[a]]", "emphasis"), ("a]]", "emphasis")):
            r = dict(GOOD, meanings=[text])
            self.assertIn(f"FAIL: authored av-sample-0001 {want} in ko field", au.vocab_problems([r]))
        # ko-gloss 줄도 같은 메시지('authored')
        self.assertIn("FAIL: authored jm:1 ruby in ko field", au.meaning_problems({"vocabId": "jm:1", "meanings": ["{a|b}"], "koSource": "authored",
                      "method": "human", "reviewStatus": "unreviewed"}, "ko-gloss"))

    def test_format_errors(self):
        bad = [dict(GOOD, id="x"), dict(GOOD), dict(GOOD, level=9), dict(GOOD, reviewStatus="reviewed"), dict(GOOD, method="human?"), dict(GOOD, meanings=[]),
               dict(GOOD, id="av-sample-0002", reviewStatus="sampled", reviewedAt="2026-11-02")]
        out = "\n".join(au.vocab_problems(bad))
        for s in ("id format", "duplicate id", "level invalid", "reviewedAt missing", "method invalid", "meanings empty",
                  "av-sample-0002 reviewerKind missing"):
            self.assertIn(s, out)
        self.assertIn("duplicate kanji", "\n".join(au.kanji_problems([{"literal": "水", "strokeCount": 4, "on": [], "kun": [], "ko": []}] * 2)))

    def test_japanese_spans_listed_not_failed(self):
        rows = au.jp_span_rows("authored", "id1", ["「ありがとうございます」의 줄임말 and 水 와 〜さん"])
        self.assertEqual([r[2] for r in rows], ["「ありがとうございます」", "水", "〜さん"])
