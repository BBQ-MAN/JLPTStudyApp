import tempfile
import unittest
from pathlib import Path

import levels as lv
from levels import LevelRow as R
from pl_parse import parse_jmdict
from support import JMDICT


def index():
    p = Path(tempfile.mkdtemp()) / "j.xml"
    p.write_text(JMDICT, encoding="utf-8")
    return lv.Index(list(parse_jmdict(p)))


class LevelsTest(unittest.TestCase):
    def setUp(self):
        self.idx = index()

    def run_map(self, rows, adapter="seq", ov=None):
        return lv.map_levels(rows, self.idx, adapter, ov or {})

    def test_seq_direct_and_reb_mismatch_and_empty_seq(self):
        rows = [R(5, 1, 1000001, "水", "みず"),          # 직결
                R(5, 2, 1000003, "猫", "ねこ"),          # reb 불일치 → unmatched, 후보는 그 seq
                R(5, 3, None, "犬", "いぬ"),             # 빈 seq → form
                R(5, 4, 4242424, None, "なし")]          # 사전에 없는 seq
        vocab, unmatched, keep = self.run_map(rows)
        self.assertEqual(set(vocab), {"jm:1000001", "jm:1000003"})
        self.assertEqual([(u[0], u[4]) for u in unmatched], [(2, "1000003"), (4, "")])
        self.assertEqual(vocab["jm:1000003"], (5, 3))

    def test_form_exact_pair_only_one_adopted(self):
        rows = [R(5, 1, None, "水", "みず"), R(5, 2, None, "水", "みづ")]
        vocab, unmatched, _ = self.run_map(rows, "form")
        self.assertEqual(list(vocab), ["jm:1000001"])
        self.assertEqual(unmatched[0][:4], (2, 5, "水", "みづ"))

    def test_zero_or_many_candidates_are_unmatched_and_not_guessed(self):
        rows = [R(5, 1, None, "端", "はし"),      # 2개
                R(5, 2, None, None, "ありがとう"),  # 가나만: keb 없는 항목 + uk 항목 = 2개
                R(5, 3, None, None, "あめ"),       # keb 있고 uk 아님 → 0개
                R(5, 4, None, None, "ねえ")]       # 가나 전용 1개 → 채택
        vocab, unmatched, keep = self.run_map(rows, "form")
        self.assertEqual(list(vocab), ["jm:1000006"])
        by = {u[0]: u for u in unmatched}
        self.assertEqual(by[1][4], "1000004,1000005")
        self.assertEqual(by[2][4], "1000011,1000012")
        self.assertEqual(by[3][4], "")
        self.assertTrue({1000004, 1000005} <= keep)  # 후보 항목도 스냅샷에 남는다

    def test_overrides_applied(self):
        d = Path(tempfile.mkdtemp())
        f = d / "level-overrides.tsv"
        f.write_text("levelSource\tlevel\trow_no\tjmdict_seq\treason\n# c\nS-06\t5\t1\t1000004\t사람이 정함\nS-06\t5\t2\tav-n5-0001\t사전에 없음\nS-99\t5\t3\t1\tx\n", encoding="utf-8")
        ov = lv.read_overrides(f, "S-06")
        self.assertEqual(ov, {(5, 1): "1000004", (5, 2): "av-n5-0001"})
        vocab, unmatched, _ = self.run_map([R(5, 1, None, "端", "はし"), R(5, 2, None, None, "ゐ"), R(5, 3, None, None, "あめ")], "form", ov)
        self.assertEqual(set(vocab), {"jm:1000004", "av-n5-0001"})
        self.assertEqual([u[0] for u in unmatched], [3])
        self.assertEqual(lv.read_overrides(d / "none.tsv", "S-06"), {})

    def test_easiest_level_wins(self):
        rows = [R(3, 7, 1000001, "水", "みず"), R(5, 9, 1000001, "水", "みず"), R(4, 1, 1000001, "水", "みず")]
        vocab, _, _ = self.run_map(rows)
        self.assertEqual(vocab, {"jm:1000001": (5, 9)})

    def test_tsv_roundtrip(self):
        rows = [R(4, 2, None, None, "あ"), R(5, 1, 7, "水", "みず")]
        p = Path(tempfile.mkdtemp()) / "l.tsv"
        p.write_text(lv.to_tsv(rows), encoding="utf-8")
        self.assertEqual(lv.read_tsv(p), sorted(rows, key=lambda r: (r.level, r.row_no)))

    def test_s06_reader(self):
        d = Path(tempfile.mkdtemp())
        (d / "S-06/original_data").mkdir(parents=True)
        (d / "S-06/original_data/n5.csv").write_text("jmdict_seq,kana,kanji,waller_definition\n1000001,みず,水,w\n,いぬ,,x\n", encoding="utf-8")
        rows = lv.read_s06(d, {"id": "S-06", "files": [{"path": "original_data/n5.csv"}]})
        self.assertEqual(rows, [R(5, 1, 1000001, "水", "みず"), R(5, 2, None, None, "いぬ")])


class RepresentativeTest(unittest.TestCase):
    def test_uk_katakana_stag_and_reading_override(self):
        from pl_db import representative, senses_for
        from pl_snapshot import snapshot_license
        e = lambda keb, reb, senses: {"seq": 1, "keb": keb, "reb": reb, "senses": senses}
        s = lambda uk=False, **kw: dict(uk=uk, gloss=["g"], **kw)
        self.assertEqual(representative(e(["為る"], ["する"], [s(True)]), R(5, 1, 1, None, "する")), (None, "する"))
        self.assertEqual(representative(e(["咖哩"], ["カレー"], [s()]), R(5, 1, 1, None, "カレー")), (None, "カレー"))
        self.assertEqual(representative(e(["椅子"], ["いす"], [s()]), R(5, 1, 1, "椅子", "いす")), ("椅子", "いす"))
        self.assertEqual(representative(e(["椅子"], ["いす"], [s()]), R(5, 1, 1, None, "いす")), (None, "いす"))
        self.assertEqual(representative(e(["明るい", "明い"], ["あかるい"], [s()]), R(5, 1, 1, "明い", "あかるい"), None, "明るい"), ("明るい", "あかるい"))
        self.assertEqual(representative(e(["四"], ["し", "よん"], [s()]), R(5, 1, 1, "四", "し"), "よん"), ("四", "よん"))
        ent = e(["今日"], ["きょう", "こんにち"], [s(), s(stagr=["こんにち"])])
        self.assertEqual(len(senses_for(ent, "今日", "きょう")), 1)
        m = {"S-06": {"id": "S-06", "name": "n", "extraCredits": [{"text": "© Jonathan Waller", "url": "http://www.tanos.co.uk/jlpt/"}]}}
        self.assertIn("© Jonathan Waller <http://www.tanos.co.uk/jlpt/>", snapshot_license(m, ["S-06"]))
