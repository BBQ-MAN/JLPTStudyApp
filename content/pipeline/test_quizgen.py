"""QG1(quizgen.md 3절·8.2): xref·related 스냅샷·hira·jm_form·vocab_conflict·check_quizgen·refresh-related."""
import gzip
import json
import shutil
import sqlite3
import tempfile
import unittest
from pathlib import Path

import build
import check
import pl_quizgen as qg
from pl_common import REPO, Paths, hira, jdump, read_json, sha256_bytes, sha256_file
from pl_parse import parse_jmdict
from pl_snapshot import _gz, related_file, write_related
from support import TempRoot, make_opener, synthetic_sources

XML = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE JMdict [
<!ENTITY n "noun (common) (futsuumeishi)">
]>
<JMdict>
<entry><ent_seq>10</ent_seq><k_ele><keb>橋</keb></k_ele><r_ele><reb>はし</reb></r_ele><sense><pos>&n;</pos><gloss>bridge</gloss></sense></entry>
<entry><ent_seq>11</ent_seq><k_ele><keb>箸</keb></k_ele><r_ele><reb>はし</reb></r_ele><sense><pos>&n;</pos><gloss>chopsticks</gloss></sense></entry>
<entry><ent_seq>12</ent_seq><k_ele><keb>端</keb></k_ele><r_ele><reb>ハシ</reb></r_ele><sense><pos>&n;</pos><gloss>edge</gloss></sense></entry>
<entry><ent_seq>20</ent_seq><k_ele><keb>上手</keb></k_ele><r_ele><reb>じょうず</reb></r_ele><sense><pos>&n;</pos><gloss>skillful</gloss></sense></entry>
<entry><ent_seq>21</ent_seq><k_ele><keb>上手</keb></k_ele><r_ele><reb>うわて</reb></r_ele><sense><pos>&n;</pos><gloss>upper hand</gloss>
<xref>母・はは・1</xref><xref>母・はは・1</xref><xref>父</xref></sense><sense><gloss>other</gloss><xref>パパ・ぱぱ</xref></sense></entry>
<entry><ent_seq>99</ent_seq><k_ele><keb>無関係</keb></k_ele><r_ele><reb>むかんけい</reb></r_ele><sense><pos>&n;</pos><gloss>unrelated</gloss></sense></entry>
</JMdict>
"""


def entry(seq, keb, reb, gloss=(), xref=()):
    return {"seq": seq, "keb": list(keb), "reb": list(reb), "pos": ["n"], "senses": [{"uk": False, "gloss": list(gloss)}], "xref": list(xref)}


def vrow(vid, level, written, reading):
    return {"id": vid, "level": level, "written": written, "reading": reading}


class ParseXrefTest(unittest.TestCase):
    def test_xref_opt_in_and_dedup(self):
        p = Path(tempfile.mkdtemp()) / "j.xml"
        p.write_text(XML, encoding="utf-8")
        with_x = {e["seq"]: e for e in parse_jmdict(p, xref=True)}
        self.assertEqual(with_x[21]["xref"], ["母・はは・1", "父", "パパ・ぱぱ"])  # 나온 순서, 중복 제거, 모든 sense
        self.assertEqual(with_x[10]["xref"], [])
        without = {e["seq"]: e for e in parse_jmdict(p)}
        self.assertNotIn("xref", without[21])
        self.assertEqual(sorted(without[21]), sorted(e for e in with_x[21] if e != "xref"))


class HiraTest(unittest.TestCase):
    def test_hira(self):
        self.assertEqual(hira("カン"), "かん")
        self.assertEqual(hira("コーヒー"), "こーひー")
        self.assertEqual(hira("ヴ"), "ゔ")
        self.assertEqual(hira("ひらがな漢字"), "ひらがな漢字")


class RelatedTest(TempRoot):
    def setUp(self):
        man = [dict(synthetic_sources()[0][0], downloadUrl="https://example.test/JMdict_e.gz")]
        self.p = self.make_root(man, {"kind": "release", "levelSource": "S-06", "levels": [5], "milestone": "T"})
        self.raw = self.p.raw / "S-01" / "JMdict_e.gz"
        self.raw.parent.mkdir(parents=True)
        self.raw.write_bytes(gzip.compress(XML.encode()))
        self.man = {"S-01": dict(man[0], sha256=sha256_file(self.raw))}
        _gz(self.p.snapshot / "S-01-2026-11-01.jsonl.gz", "".join(jdump(e) + "\n" for e in (entry(10, ["橋"], ["はし"]), entry(20, ["上手"], ["じょうず"]))))

    def rows(self):
        return [json.loads(l) for l in gzip.open(related_file(self.p, self.man), "rt", encoding="utf-8")]

    def test_selects_same_form_or_reading_and_excludes_unrelated(self):
        write_related(self.p, self.man, out=lambda *_: None)
        got = self.rows()
        self.assertEqual([r["seq"] for r in got], [10, 11, 12, 20, 21])  # 箸·端(ハシ)·上手(うわて)는 들어가고 무관 항목은 빠진다
        self.assertEqual(got[4], {"keb": ["上手"], "reb": ["うわて"], "seq": 21, "xref": ["母・はは・1", "父", "パパ・ぱぱ"]})
        self.assertEqual(sorted(got[0]), ["keb", "reb", "seq", "xref"])
        self.assertTrue(related_file(self.p, self.man).name == "S-01-2026-11-01-related.jsonl.gz")

    def test_twice_same_bytes_and_existing_snapshot_untouched(self):
        snap = sha256_file(self.p.snapshot / "S-01-2026-11-01.jsonl.gz")
        write_related(self.p, self.man, out=lambda *_: None)
        a = sha256_file(related_file(self.p, self.man))
        write_related(self.p, self.man, out=lambda *_: None)
        self.assertEqual(sha256_file(related_file(self.p, self.man)), a)
        self.assertEqual(sha256_file(self.p.snapshot / "S-01-2026-11-01.jsonl.gz"), snap)

    def test_refresh_related_command_fails_on_raw_hash_mismatch(self):
        self.p.manifest.write_text(json.dumps(list(self.man.values())))
        log = []
        self.assertEqual(build.main(["refresh-related"], paths=self.p, out=log.append), 0)
        self.assertTrue(related_file(self.p, self.man).exists())
        self.raw.write_bytes(gzip.compress(XML.replace("bridge", "span").encode()))
        related_file(self.p, self.man).unlink()
        self.assertEqual(build.main(["refresh-related"], paths=self.p, out=log.append), 1)
        self.assertFalse(related_file(self.p, self.man).exists())
        with self.assertRaisesRegex(Exception, "^FAIL: refresh-related S-01 raw sha256 differs from manifest"):
            write_related(self.p, self.man, out=lambda *_: None)


class FormRowsTest(unittest.TestCase):
    related = [entry(10, ["橋"], ["はし"]), entry(11, ["箸"], ["はし"]), entry(12, ["端"], ["ハシ"]), entry(20, ["上手"], ["じょうず"]),
               entry(21, ["上手"], ["うわて"]), entry(30, ["無関係", "別"], ["むかんけい"]), entry(31, [], ["はし"])]
    vocab = [vrow("jm:10", 5, "橋", "はし"), vrow("jm:20", 5, "上手", "じょうず"), vrow("jm:40", 5, None, "ねえ")]

    def test_rows(self):
        rows = qg.jm_form_rows(self.related, self.vocab)
        self.assertEqual(rows, [{"written": "上手", "reading": "うわて"}, {"written": "上手", "reading": "じょうず"}, {"written": "橋", "reading": "はし"},
                                {"written": "端", "reading": "はし"}, {"written": "箸", "reading": "はし"}])  # 정렬 (written, reading)
        self.assertNotIn({"written": "無関係", "reading": "むかんけい"}, rows)  # Wdb·Rdb 밖 쌍


class ConflictTest(unittest.TestCase):
    def conflicts(self, vocab, ko, entries):
        by = {e["seq"]: e for e in entries}
        return qg.vocab_conflicts(vocab, ko, by, {s: e["xref"] for s, e in by.items()})[0]

    def test_en_tokens(self):
        e = entry(1, [], [], ["to put on (from shoulders down)", "eyes", "the", "to be"])
        self.assertEqual(qg.en_tokens(e), {"put", "eye"})
        self.assertEqual(qg.en_tokens(entry(2, [], [], ["stories", "glass", "bus"])), {"story", "glass", "bus"})

    def test_ko_phrases(self):
        self.assertEqual(qg.ko_phrases(["(사고·일 등을) 당하다, 겪다"]), {"당하다", "겪다"})
        self.assertEqual(qg.ko_phrases(["~ 하는 것; 물 / 불"]), {"하는것", "물", "불"})

    def test_en_ko_conflicts_and_nonconflict(self):
        vocab = [vrow("jm:1", 5, "父", "ちち"), vrow("jm:2", 5, None, "パパ"), vrow("jm:3", 5, "本", "ほん"), vrow("jm:4", 5, "本棚", "ほんだな"),
                 vrow("jm:5", 5, "暑い", "あつい"), vrow("jm:6", 5, "熱い", "あつい"), vrow("jm:7", 5, "水", "みず"), vrow("jm:8", 5, "犬", "いぬ")]
        ents = [entry(1, ["父"], ["ちち"], ["father"]), entry(2, [], ["パパ"], ["papa", "father"]), entry(3, ["本"], ["ほん"], ["book"]),
                entry(4, ["本棚"], ["ほんだな"], ["bookshelf"]), entry(5, ["暑い"], ["あつい"], ["hot"]), entry(6, ["熱い"], ["あつい"], ["heated"]),
                entry(7, ["水"], ["みず"], ["water"]), entry(8, ["犬"], ["いぬ"], ["dog"])]
        ko = {"jm:1": ["아버지"], "jm:2": ["아빠"], "jm:3": ["책"], "jm:4": ["책장"], "jm:5": ["덥다", "(마음이) 뜨겁다, 열렬하다"],
              "jm:6": ["뜨겁다"], "jm:7": ["물"], "jm:8": ["개"]}
        c = self.conflicts(vocab, ko, ents)
        self.assertEqual(c["jm:1"], ["jm:2"])  # 한국어는 달라도 영어(father)가 같다
        self.assertEqual(c["jm:3"], ["jm:4"])  # 포함 관계 책 ⊂ 책장
        self.assertEqual(c["jm:5"], ["jm:6"])  # 둘째 뜻의 구절이 같다
        self.assertEqual((c["jm:7"], c["jm:8"]), ([], []))

    def test_xref_same_level_only_and_symmetric(self):
        vocab = [vrow("jm:1", 5, "母", "はは"), vrow("jm:2", 5, "お母さん", "おかあさん"), vrow("jm:3", 4, "ママ", "まま"), vrow("jm:4", 5, "牛", "うし")]
        ents = [entry(1, ["母"], ["はは"], ["mother"]), entry(2, ["お母さん"], ["おかあさん"], ["parent"], ["母・はは・1"]),
                entry(3, ["ママ"], ["まま"], ["mama"], ["母・はは"]), entry(4, ["牛"], ["うし"], ["cow"], ["お母さん"])]
        ko = {"jm:1": ["어머니"], "jm:2": ["엄마님"], "jm:3": ["마마"], "jm:4": ["소"]}
        c, why = qg.vocab_conflicts(vocab, ko, {e["seq"]: e for e in ents}, {e["seq"]: e["xref"] for e in ents})
        self.assertEqual(c["jm:1"], ["jm:2"])  # A의 xref가 B를 가리키면 양쪽 목록
        self.assertEqual(c["jm:2"], ["jm:1", "jm:4"])
        self.assertEqual(c["jm:3"], [])  # 다른 급수를 가리키는 xref는 무시
        self.assertEqual(c["jm:4"], ["jm:2"])
        self.assertIn("xref", why[("jm:1", "jm:2")][0])

    def test_symmetric_sorted_no_self_one_row_per_jm_vocab(self):
        vocab = [vrow("jm:9", 5, "a", "a"), vrow("jm:10", 5, "b", "b"), vrow("jm:2", 5, "c", "c"), vrow("av-x", 5, "d", "d")]
        ents = [entry(9, [], [], ["tree"]), entry(10, [], [], ["tree"]), entry(2, [], [], ["tree"])]
        ko = {"jm:9": ["가"], "jm:10": ["나"], "jm:2": ["다"]}
        by = {e["seq"]: e for e in ents}
        c = qg.vocab_conflicts(vocab, ko, by, {})[0]
        self.assertEqual(c, {"jm:10": ["jm:2", "jm:9"], "jm:2": ["jm:10", "jm:9"], "jm:9": ["jm:10", "jm:2"]})  # 문자열 정렬, av- 제외
        self.assertTrue(all(k not in v for k, v in c.items()))
        for a, bs in c.items():
            for b in bs:
                self.assertIn(a, c[b])


class CheckQuizgenTest(unittest.TestCase):
    def con(self, forms, conf, vocab=(("jm:1", "橋", "はし", 5), ("jm:2", "箸", "はし", 5))):
        c = sqlite3.connect(":memory:")
        c.execute("CREATE TABLE vocab (id TEXT, written TEXT, reading TEXT, level INTEGER)")
        c.execute("CREATE TABLE jm_form (written TEXT NOT NULL, reading TEXT NOT NULL)")
        c.execute("CREATE TABLE vocab_conflict (vocab_id TEXT NOT NULL, conflict_ids TEXT NOT NULL)")
        c.executemany("INSERT INTO vocab VALUES (?,?,?,?)", vocab)
        c.executemany("INSERT INTO jm_form VALUES (?,?)", forms)
        c.executemany("INSERT INTO vocab_conflict VALUES (?,?)", conf)
        return c

    GOOD_FORMS = [("橋", "はし"), ("箸", "はし")]

    def test_pass(self):
        self.assertEqual(check.check_quizgen(self.con(self.GOOD_FORMS, [("jm:1", '["jm:2"]'), ("jm:2", '["jm:1"]')])), [])

    def test_empty_tables_pass_without_jm_vocab(self):
        self.assertEqual(check.check_quizgen(self.con([], [], vocab=[("av-sample-1", "水", "みず", 5)])), [])

    def test_asymmetric_fails(self):
        out = check.check_quizgen(self.con(self.GOOD_FORMS, [("jm:1", '["jm:2"]'), ("jm:2", "[]")]))
        self.assertTrue(out and all(o.startswith("FAIL: quizgen vocab_conflict ") for o in out), out)

    def test_other_vocab_conflict_failures(self):
        ok = self.GOOD_FORMS
        for bad in ([("jm:1", '["jm:1"]'), ("jm:2", "[]")],  # 자기 자신
                    [("jm:1", "[]")],  # 한 행 빠짐
                    [("jm:1", "[]"), ("jm:2", "[]"), ("jm:9", "[]")],  # vocab에 없는 행
                    [("jm:1", "[]"), ("jm:2", "not json")],
                    [("jm:1", '["jm:2","jm:2"]'), ("jm:2", '["jm:1"]')]):  # 중복
            out = check.check_quizgen(self.con(ok, bad))
            self.assertTrue(out and all(o.startswith("FAIL: quizgen vocab_conflict ") for o in out), (bad, out))
        out = check.check_quizgen(self.con(ok, [("jm:1", '["jm:3"]'), ("jm:2", "[]")], vocab=(("jm:1", "橋", "はし", 5), ("jm:2", "箸", "はし", 5), ("jm:3", "x", "x", 4))))
        self.assertTrue(any(o.startswith("FAIL: quizgen vocab_conflict ") for o in out))  # 다른 급수

    def test_katakana_and_missing_form_fail(self):
        conf = [("jm:1", "[]"), ("jm:2", "[]")]
        out = check.check_quizgen(self.con([("橋", "ハシ"), ("箸", "はし")], conf))
        self.assertTrue(any(o.startswith("FAIL: quizgen jm_form ") for o in out), out)
        out = check.check_quizgen(self.con([("橋", "はし")], conf))  # (箸, はし) 빠짐
        self.assertTrue(any(o.startswith("FAIL: quizgen jm_form ") for o in out), out)


class ReleaseQuizgenTest(TempRoot):
    """합성 원본 release 흐름: related 파일이 만들어지고 두 표가 채워지며 check가 related 해시를 본다."""

    def setUp(self):
        self.man, self.blobs = synthetic_sources()
        self.p = self.make_root(self.man, {"kind": "release", "levelSource": "S-06", "levels": [5, 4], "milestone": "T"})
        self.log = []

    def build_all(self):
        self.assertEqual(build.main(["refresh-snapshot"], paths=self.p, out=self.log.append, opener=make_opener(self.blobs)), 0, self.log)
        self.assertEqual(build.main(["export-gloss-input"], paths=self.p, out=self.log.append), 0)
        self.p.kogloss.mkdir(parents=True, exist_ok=True)
        for level in (5, 4):
            lines = (self.p.work / "en-gloss" / f"N{level}.jsonl").read_text(encoding="utf-8").splitlines()
            (self.p.kogloss / f"N{level}.jsonl").write_text("".join(jdump({
                "vocabId": json.loads(l)["vocabId"], "meanings": [f"뜻 {json.loads(l)['reading']}"], "koSource": "jmdict-translated", "method": "llm:m@2026-11-10",
                "enHash": json.loads(l)["enHash"], "reviewStatus": "sampled", "reviewedAt": "2026-11-10", "reviewerKind": "agent"}) + "\n" for l in lines), encoding="utf-8")
        shutil.rmtree(self.p.raw)
        return build.main([], paths=self.p, out=self.log.append)

    def q(self, sql):
        con = sqlite3.connect(self.p.assets / "content.db")
        try:
            return con.execute(sql).fetchall()
        finally:
            con.close()

    def test_tables_meta_and_checks(self):
        self.assertEqual(self.build_all(), 0, self.log)
        self.assertEqual(self.q("PRAGMA user_version"), [(3,)])
        n_jm = self.q("SELECT COUNT(*) FROM vocab WHERE id LIKE 'jm:%'")[0][0]
        self.assertEqual(self.q("SELECT COUNT(*) FROM vocab_conflict"), [(n_jm,)])
        self.assertGreater(self.q("SELECT COUNT(*) FROM jm_form")[0][0], 0)
        self.assertEqual(self.q("SELECT COUNT(*) FROM vocab v WHERE written IS NOT NULL AND id LIKE 'jm:%' AND NOT EXISTS "
                                "(SELECT 1 FROM jm_form f WHERE f.written = v.written AND f.reading = v.reading)"), [(0,)])
        h = json.loads(self.q("SELECT value FROM content_meta WHERE key='sources'")[0][0])["S-01"]["related"]
        self.assertEqual(h, sha256_file(related_file(self.p, {"S-01": self.man[0]})))
        self.assertEqual(check.run_checks(self.p), [])

    def test_check_snapshot_related(self):
        self.assertEqual(self.build_all(), 0, self.log)
        f = related_file(self.p, {"S-01": self.man[0]})
        good = f.read_bytes()
        f.write_bytes(gzip.compress(b'{"keb":[],"reb":[],"seq":1,"xref":[]}\n'))
        self.assertIn("FAIL: snapshot S-01 related hash differs from content/snapshot/S-01-2026-11-01-related.jsonl.gz", check.run_checks(self.p))
        f.unlink()
        self.assertIn("FAIL: snapshot S-01-2026-11-01-related.jsonl.gz missing", check.run_checks(self.p))
        f.write_bytes(good)
        self.assertEqual(check.run_checks(self.p), [])

    def test_build_fails_without_related_file(self):
        self.assertEqual(self.build_all(), 0, self.log)
        related_file(self.p, {"S-01": self.man[0]}).unlink()
        self.assertEqual(build.main(["build"], paths=self.p, out=self.log.append), 1)


class RepoSchemaTest(unittest.TestCase):
    def test_schema_files_and_latest_version(self):
        s = Paths().schemas
        self.assertEqual(sorted(p.name for p in s.glob("*.json")), ["1.json", "2.json", "3.json"])
        d = read_json(s / "3.json")["database"]
        self.assertEqual(d["version"], 3)
        self.assertEqual(len(d["entities"]), 14)


if __name__ == "__main__":
    unittest.main()
