import json
import re
import sqlite3
import subprocess
import sys
import unittest
from pathlib import Path

import build
import check
from pl_common import REPO, Paths, load_config, sha256_file
from support import TempRoot


def run(p, argv=None):
    lines = []
    rc = build.main(argv or [], paths=p, out=lines.append)
    return rc, lines


def rows(p, sql):
    con = sqlite3.connect(p.assets / "content.db")
    try:
        return con.execute(sql).fetchall()
    finally:
        con.close()


class SampleBuildTest(TempRoot):
    def test_sample_builds_and_passes_check(self):
        p = self.sample_root()
        rc, lines = run(p)
        self.assertEqual(rc, 0, lines)
        self.assertEqual(rows(p, "SELECT value FROM content_meta WHERE key='kind'"), [("sample",)])
        self.assertEqual(rows(p, "SELECT COUNT(*) FROM vocab"), [(20,)])
        self.assertEqual(rows(p, "SELECT COUNT(*) FROM kanji"), [(8,)])
        self.assertEqual(rows(p, "SELECT DISTINCT source_id FROM vocab"), [("authored",)])
        self.assertEqual(check.run_checks(p), [])
        self.assertTrue(any("Japanese spans" in l for l in lines))
        self.assertTrue(p.report("ko_spans_authored.tsv", "T").exists())

    def test_two_builds_same_bytes_even_in_other_dir(self):
        a, b = self.sample_root(), self.sample_root()
        run(a)
        h1 = sha256_file(a.assets / "content.db")
        run(a)
        run(b)
        self.assertEqual(sha256_file(a.assets / "content.db"), h1)
        self.assertEqual(sha256_file(b.assets / "content.db"), h1)
        self.assertEqual((a.assets / "content.db.sha256").read_text().strip(), h1)
        self.assertEqual((a.assets / "attribution.json").read_bytes(), (b.assets / "attribution.json").read_bytes())
        self.assertEqual((a.fallback).read_bytes(), (b.fallback).read_bytes())
        z = lambda p: next(p.dist.glob("*.zip")).read_bytes()
        self.assertEqual(z(a), z(b))

    def test_release_without_level_source_stops_sample_runs(self):
        p = self.make_root(json.loads((REPO / "docs/content/manifest.json").read_text()), {"kind": "release", "levelSource": None, "levels": [5], "milestone": "T"}, True)
        rc, _ = run(p, ["build"])
        self.assertEqual(rc, 1)
        self.assertFalse((p.assets / "content.db").exists())
        p.config.write_text(json.dumps({"kind": "sample", "levelSource": None, "levels": [5], "milestone": "T"}))
        self.assertEqual(run(p, ["build"])[0], 0)

    def test_unconfirmed_level_source_stops_release(self):
        p = self.make_root(json.loads((REPO / "docs/content/manifest.json").read_text()), {"kind": "release", "levelSource": "S-05", "levels": [5], "milestone": "T"}, True)
        self.assertEqual(run(p, ["build"])[0], 1)

    def test_sample_fallback_when_s01_unconfirmed(self):
        man = json.loads((REPO / "docs/content/manifest.json").read_text())
        self.assertEqual(next(s for s in man if s["id"] == "S-01")["status"], "confirmed")
        for s in man:
            s["status"] = "unconfirmed"
        p = self.make_root(man, {"kind": "sample", "levelSource": None, "levels": [5], "milestone": "T"}, True)
        self.assertEqual(run(p, ["build"])[0], 0)
        xml = p.fallback.read_text(encoding="utf-8")
        self.assertIn("이 앱이 직접 작성한 콘텐츠 https://creativecommons.org/licenses/by-sa/4.0/", xml)
        self.assertIn("콘텐츠 DB: CC BY-SA 4.0 https://creativecommons.org/licenses/by-sa/4.0/", xml)

    def test_ids_file_and_retired(self):
        p = self.sample_root()
        run(p)
        ids = (p.ids / "sample/vocab.txt").read_text().split()
        self.assertEqual(len(ids), 20)
        voc = p.authored / "sample/vocab.jsonl"
        voc.write_text("".join(voc.read_text().splitlines(True)[1:]))  # 첫 줄 삭제
        self.assertEqual(run(p)[0], 1)  # 사람이 retired.tsv에 올리기 전에는 멈춘다
        (p.ids / "sample/retired.tsv").write_text("av-sample-0001\tv1\n水\tv1\n")
        self.assertEqual(run(p)[0], 0)
        self.assertIn(("av-sample-0001",), rows(p, "SELECT id FROM retired"))
        self.assertEqual(check.run_checks(p), [])


class SchemaTest(TempRoot):
    def test_picks_largest_schema_json(self):
        p = self.sample_root()
        s = json.loads((p.schemas / "1.json").read_text())
        s["database"]["version"] = 2
        s["database"]["identityHash"] = "abc123"
        s["database"]["entities"].append({"tableName": "extra", "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))",
                                          "fields": [], "primaryKey": {"autoGenerate": False, "columnNames": ["id"]}})
        s["database"]["setupQueries"][1] = "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'abc123')"
        (p.schemas / "2.json").write_text(json.dumps(s))
        self.assertEqual(run(p)[0], 0)
        self.assertEqual(rows(p, "PRAGMA user_version"), [(2,)])
        self.assertEqual(rows(p, "SELECT identity_hash FROM room_master_table"), [("abc123",)])
        self.assertEqual(rows(p, "SELECT COUNT(*) FROM extra"), [(0,)])
        self.assertEqual(check.run_checks(p), [])

    def test_real_schema_matches_tables(self):
        p = self.sample_root()
        run(p)
        names = {r[0] for r in rows(p, "SELECT name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'")}
        self.assertEqual(names, {"content_meta", "vocab", "vocab_meaning", "kanji", "vocab_kanji", "retired", "room_master_table",
                                 "index_vocab_level_sort_key", "index_kanji_level_sort_key", "index_vocab_kanji_kanji"})


class RepoAssetTest(unittest.TestCase):
    def test_committed_assets_pass_check(self):
        self.assertEqual(check.run_checks(Paths()), [])

    def test_check_script_exit_codes(self):
        r = subprocess.run([sys.executable, "-I", str(REPO / "content/pipeline/check.py")], capture_output=True, text=True)
        self.assertEqual((r.returncode, r.stdout.strip()), (0, "OK: content checks passed"))
        r = subprocess.run([sys.executable, "-I", str(REPO / "content/pipeline/check.py"), "--root", "/nonexistent"], capture_output=True, text=True)
        self.assertEqual(r.returncode, 1)
        self.assertRegex(r.stdout, r"^FAIL: asset ")

    def test_gitignore(self):
        lines = [l.strip() for l in (REPO / ".gitignore").read_text().splitlines() if l.strip() and not l.startswith("#")]
        for want in ("/content/raw/", "/content/work/", "/content/dist/"):
            self.assertIn(want, lines)
        self.assertFalse(any("content/snapshot" in l for l in lines))

    def test_config_is_json_with_kind(self):
        self.assertIn(load_config(Paths())["kind"], ("sample", "release"))
