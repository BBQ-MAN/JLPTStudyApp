import json
import sqlite3
import unittest

import build
import check
import pl_attr as at
from pl_common import read_json, sha256_file
from support import TempRoot


class CheckFailuresTest(TempRoot):
    """실패를 일부러 만든 픽스처로 검사마다 'FAIL: <검사> <이유>'를 확인한다."""

    def setUp(self):
        self.p = self.sample_root()
        self.assertEqual(build.main([], paths=self.p, out=lambda *_: None), 0)
        self.assertEqual(check.run_checks(self.p), [])

    def sql(self, *stmts):
        con = sqlite3.connect(self.p.assets / "content.db")
        for s in stmts:
            con.execute(s)
        con.commit()
        con.close()

    def attr(self, fn):
        f = self.p.assets / "attribution.json"
        a = read_json(f)
        fn(a)
        f.write_text(json.dumps(a, ensure_ascii=False), encoding="utf-8")

    def has(self, line):
        out = check.run_checks(self.p)
        self.assertIn(line, out)
        self.assertTrue(all(o.startswith("FAIL: ") for o in out), out)

    def test_source_id(self):
        self.sql("UPDATE vocab SET source_id = 'S-99' WHERE id = 'av-sample-0001'")
        self.has("FAIL: source S-99 not in manifest")
        self.sql("UPDATE vocab SET source_id = '' WHERE id = 'av-sample-0001'")
        self.has("FAIL: source vocab.source_id empty in 1 rows")

    def test_not_confirmed(self):
        # S-08은 excluded로 고정된 행이라 다른 출처가 confirmed로 바뀌어도 이 검사가 유지된다(S-03은 T03에서 confirmed).
        self.sql("UPDATE kanji SET source_id = 'S-08'")
        self.has("FAIL: source S-08 not confirmed")

    def test_audio_file(self):
        (self.p.assets / "clip.mp3").write_bytes(b"x")
        (self.p.root / "app/src/main/assets/licenses").mkdir(parents=True)
        (self.p.root / "app/src/main/assets/licenses/a.wav").write_bytes(b"x")
        out = check.run_checks(self.p)
        self.assertIn("FAIL: forbidden-file app/src/main/assets/content/clip.mp3 is an audio file", out)
        self.assertIn("FAIL: forbidden-file app/src/main/assets/licenses/a.wav is an audio file", out)
        (self.p.assets / "other.bin").write_bytes(b"x")
        self.assertIn("FAIL: forbidden-file app/src/main/assets/content/other.bin is not an allowed content asset", check.run_checks(self.p))

    def test_id_lost(self):
        self.sql("DELETE FROM vocab_meaning WHERE vocab_id = 'av-sample-0003'", "DELETE FROM vocab WHERE id = 'av-sample-0003'")
        self.has("FAIL: ids av-sample-0003 was shipped before but is missing (not in retired)")
        self.sql("INSERT INTO retired VALUES ('av-sample-0003', 'x')")
        self.assertNotIn("FAIL: ids av-sample-0003 was shipped before but is missing (not in retired)", check.run_checks(self.p))

    def test_meaning_column_missing(self):
        self.sql("UPDATE vocab_meaning SET ko_method = '' WHERE vocab_id = 'av-sample-0001'")
        self.has("FAIL: meanings av-sample-0001#0 ko_method empty")
        self.sql("UPDATE vocab_meaning SET ko_method = 'llm:m@2026-11-02', ko_review_status = 'reviewed', ko_reviewed_at = NULL WHERE vocab_id = 'av-sample-0001'")
        self.has("FAIL: meanings av-sample-0001#0 ko_reviewed_at empty while reviewed")
        self.sql("UPDATE vocab_meaning SET ko_review_status = 'bogus' WHERE vocab_id = 'av-sample-0001'")
        self.has("FAIL: meanings av-sample-0001#0 ko_review_status invalid (bogus)")

    def test_vocab_without_meaning(self):
        self.sql("DELETE FROM vocab_meaning WHERE vocab_id = 'av-sample-0002'")
        self.has("FAIL: meanings av-sample-0002 vocab has no meaning row")

    def test_attribution_missing_source(self):
        self.attr(lambda a: a.update(sources=[]))
        self.has("FAIL: attribution authored used in DB but missing from attribution.json")

    def test_s01_links_empty(self):
        self.sql("UPDATE vocab SET source_id = 'S-01'")
        self.attr(lambda a: a["sources"].append({"id": "S-01", "links": []}))
        self.has("FAIL: attribution S-01 links empty")

    def test_fallback_differs(self):
        self.p.fallback.write_text(self.p.fallback.read_text(encoding="utf-8").replace("이 앱", "그 앱"), encoding="utf-8")
        self.has("FAIL: attribution attribution_fallback.xml differs from the one made from attribution.json")
        self.p.fallback.unlink()
        self.has("FAIL: attribution attribution_fallback.xml differs from the one made from attribution.json")

    def test_notice_placeholder_left(self):
        self.attr(lambda a: a["notices"][0].update(body="급수 「{levelListName}」"))
        self.has("FAIL: attribution N-09 unfilled placeholder {levelListName}")
        self.attr(lambda a: a["firstNotice"].update(body="{meaningSentences}"))
        self.has("FAIL: attribution firstNotice unfilled placeholder {meaningSentences}")

    def test_notice_shape(self):
        self.attr(lambda a: a["notices"].append({"id": "N-08", "title": "t", "body": "b", "showWhen": "always"}))
        out = check.run_checks(self.p)
        self.assertIn("FAIL: attribution notice N-08 must have exactly id, title, body", out)
        self.assertIn("FAIL: attribution N-08 must be in dbLicense only, not notices", out)

    def test_meaning_source_texts_key_missing(self):
        self.attr(lambda a: a["meaningSourceTexts"].pop("authored-llm-nomodel"))
        self.has("FAIL: attribution meaningSourceTexts missing key authored-llm-nomodel")

    def test_n11_n12_required_when_rows_exist(self):
        self.attr(lambda a: a.update(notices=[n for n in a["notices"] if n["id"] not in ("N-11", "N-12")]))
        self.sql("DROP TABLE grammar", "CREATE TABLE grammar (id TEXT)", "INSERT INTO grammar VALUES ('gr-n5-0001')", "DROP TABLE listening", "CREATE TABLE listening (id TEXT, targets_json TEXT, answer_index INT)", "INSERT INTO listening VALUES ('ls-n5-0001', '[]', 0)")
        out = check.run_checks(self.p)
        self.assertIn("FAIL: attribution N-11 missing while grammar or question rows exist", out)
        self.assertIn("FAIL: attribution N-12 missing while listening rows exist", out)

    def test_schema_version_and_hash(self):
        self.sql("PRAGMA user_version = 1")
        self.has("FAIL: schema user_version db=1 json=2")
        self.sql("UPDATE room_master_table SET identity_hash = 'zzz'")
        self.assertTrue(any(l.startswith("FAIL: schema room_master_table identity hash zzz") for l in check.run_checks(self.p)))
        self.sql("ALTER TABLE vocab ADD COLUMN extra TEXT")
        self.assertIn("FAIL: schema vocab differs from schema json", check.run_checks(self.p))
        self.sql("DROP INDEX index_vocab_level_sort_key")
        self.assertIn("FAIL: schema index_vocab_level_sort_key missing in content.db", check.run_checks(self.p))

    def test_asset_v1_schema(self):
        self.sql("PRAGMA user_version = 1")
        self.assertTrue(any(l.startswith("FAIL: schema ") for l in check.run_checks(self.p)))

    def test_p_columns_when_rows_exist(self):
        self.sql("DELETE FROM grammar_example", "DELETE FROM grammar")  # 샘플 문법을 비운다
        self.assertEqual([l for l in check.run_checks(self.p) if "ko-columns" in l], [])
        P = "'authored','authored','human','unreviewed',NULL,NULL,0"
        self.sql(f"INSERT INTO grammar VALUES ('gr-n5-0001',5,1,'t','m','e','[]','[]',{P})")
        self.assertFalse([l for l in check.run_checks(self.p) if "ko-columns" in l])
        self.sql("UPDATE grammar SET ko_method = ''")
        self.assertIn("FAIL: ko-columns grammar gr-n5-0001 ko_method empty", check.run_checks(self.p))

    def test_ref_checks(self):
        self.sql("INSERT INTO kanji_stroke VALUES ('龍','[]','S-03')")
        self.assertIn("FAIL: refs kanji_stroke.literal not in kanji: 龍", check.run_checks(self.p))
        self.sql("INSERT INTO grammar_example VALUES ('gr-x',0,'ja','ko','authored',NULL,NULL,NULL)")
        self.assertIn("FAIL: refs grammar_example.grammar_id not in grammar: gr-x", check.run_checks(self.p))

    def test_sqlite_header(self):
        db = self.p.assets / "content.db"
        db.write_bytes(b"ENCRYPTED" + db.read_bytes()[9:])
        out = check.run_checks(self.p)
        self.assertIn("FAIL: protection content.db does not start with the SQLite header (L-01.nodrm)", out)

    def test_hash(self):
        (self.p.assets / "content.db.sha256").write_text("0" * 64 + "\n")
        self.assertTrue(any(l.startswith("FAIL: hash content.db.sha256 000000000000") for l in check.run_checks(self.p)))

    def test_kind_mismatch_and_release_without_level_source(self):
        cfg = json.loads(self.p.config.read_text())
        cfg["kind"] = "release"
        self.p.config.write_text(json.dumps(cfg))
        out = check.run_checks(self.p)
        self.assertIn("FAIL: kind config is release but content_meta.kind=sample", out)
        self.assertIn("FAIL: kind levelSource None is not confirmed", out)

    def test_authored_format_in_check(self):
        f = self.p.authored / "sample/vocab.jsonl"
        f.write_text(f.read_text(encoding="utf-8").replace("물", "{물|mul}", 1), encoding="utf-8")
        self.has("FAIL: authored av-sample-0001 ruby in ko field")


class NoticesTest(TempRoot):
    def test_sample_n10_has_authored_sentence_only(self):
        p = self.sample_root()
        build.main([], paths=p, out=lambda *_: None)
        n10 = next(n for n in read_json(p.assets / "attribution.json")["notices"] if n["id"] == "N-10")
        self.assertIn("한국어 뜻은 이 앱이 직접 작성했습니다.", n10["body"])
        self.assertNotIn("번역", n10["body"])
        self.assertNotIn("JMdict", n10["body"])

    def test_mixed_facts(self):
        tpl = read_json(self.sample_root().notices)
        facts = {"meta": {"kind": "release", "content_version": "v1", "level_source": "S-06"}, "used": {"S-01", "authored"},
                 "pairs": [("jmdict-translated", "llm:b@2026-01-01"), ("jmdict-translated", "llm:a@2026-01-02"), ("authored", "llm:c@2026-01-03"), ("authored", "human")],
                 "counts": {"grammar": 1, "listening": 1}}
        man = {"S-01": {"name": "JMdict", "links": ["l"], "extraCredits": []}, "S-06": {"name": "Level list"}}
        a = at.build_attribution(man, facts, tpl)
        self.assertEqual(a["modelNames"], ["a", "b", "c"])
        body = next(n for n in a["notices"] if n["id"] == "N-10")["body"]
        self.assertIn("번역에는 AI(a, b)를 썼습니다. 일부 뜻은 이 앱이 직접 작성했습니다.", body)
        self.assertEqual([n["id"] for n in a["notices"]], ["N-09", "N-10", "N-11", "N-12"])

    def test_model_of(self):
        self.assertEqual(at.model_of("llm:x-1@2026-01-01"), "x-1")
        self.assertEqual(at.model_of("llm:@2026-01-01"), "")
        self.assertIsNone(at.model_of("human"))

    def test_fallback_xml_escaping(self):
        a = {"sources": [{"attribution": "Group's \"licence\" & <b>", "licenseUrl": "https://x"}], "dbLicense": {"name": "N", "url": "u"}}
        x = at.fallback_xml(a)
        self.assertIn("Group\\'s \\\"licence\\\" &amp; &lt;b&gt; https://x\\n콘텐츠 DB: N u", x)
