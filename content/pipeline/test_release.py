import json
import re
import shutil
import sqlite3
import unittest

import build
import check
from pl_common import jdump, read_json, sha256_file
from support import TempRoot, make_opener, synthetic_sources


class ReleaseFlowTest(TempRoot):
    def setUp(self):
        self.man, self.blobs = synthetic_sources()
        self.p = self.make_root(self.man, {"kind": "release", "levelSource": "S-06", "levels": [5, 4], "milestone": "T"})
        self.log = []

    def refresh(self):
        return build.main(["refresh-snapshot"], paths=self.p, out=self.log.append, opener=make_opener(self.blobs))

    def snap_hashes(self):
        return {f.name: sha256_file(f) for f in sorted(self.p.snapshot.iterdir())}

    def write_gloss(self, models=("model-b", "model-a"), drop=0, stale=False, n4_status="sampled"):
        self.assertEqual(build.main(["export-gloss-input"], paths=self.p, out=self.log.append), 0)
        self.p.kogloss.mkdir(parents=True, exist_ok=True)
        n = 0
        for level in (5, 4):
            out = []
            for line in (self.p.work / "en-gloss" / f"N{level}.jsonl").read_text(encoding="utf-8").splitlines():
                n += 1
                if n <= drop:
                    continue
                e = json.loads(line)
                st = ("unreviewed", None, None) if level == 5 or n4_status == "unreviewed" else (n4_status, "2026-11-10", "agent")
                out.append(jdump({"vocabId": e["vocabId"], "meanings": [f"뜻 {e['reading']}"], "koSource": "jmdict-translated",
                                  "method": f"llm:{models[n % len(models)]}@2026-11-10", "enHash": "stale" if stale else e["enHash"],
                                  "reviewStatus": st[0], "reviewedAt": st[1], "reviewerKind": st[2]}) + "\n")
            (self.p.kogloss / f"N{level}.jsonl").write_text("".join(out), encoding="utf-8")

    def full_build(self, **kw):
        self.assertEqual(self.refresh(), 0, self.log)
        self.write_gloss(**kw)
        shutil.rmtree(self.p.raw)  # 기본 빌드는 content/raw 없이 끝까지 돈다
        return build.main([], paths=self.p, out=self.log.append)

    def test_refresh_twice_same_hashes_then_build_without_raw(self):
        self.assertEqual(self.refresh(), 0, self.log)
        first = self.snap_hashes()
        self.assertEqual(set(first), {"S-01-2026-11-01.jsonl.gz", "S-02-2026-305_2026-11-01.jsonl.gz", "S-03-r20260714.jsonl.gz", "levels-S-06-b062d4e_2025-08-26.tsv", "S-01-2026-11-01-related.jsonl.gz", "sources.json", "LICENSE.md"})
        shutil.rmtree(self.p.raw)
        self.assertEqual(self.refresh(), 0, self.log)  # raw를 지우면 다시 받는다
        self.assertEqual(self.snap_hashes(), first)
        self.assertEqual(self.full_build(), 0, self.log)
        self.assertFalse(self.p.raw.exists())
        self.assertEqual(check.run_checks(self.p), [])

    def test_refresh_requires_level_source(self):
        self.p.config.write_text(json.dumps({"kind": "release", "levelSource": None, "levels": [5]}))
        self.assertEqual(self.refresh(), 1)

    def test_snapshot_content_is_english_only_minimal(self):
        self.refresh()
        import gzip
        text = gzip.open(self.p.snapshot / "S-01-2026-11-01.jsonl.gz", "rt", encoding="utf-8").read()
        for s in ("Wasser", "eau", "vloeistof", "無関係"):
            self.assertNotIn(s, text)  # 급수 목록에 없는 항목은 스냅샷에 없다
        k = gzip.open(self.p.snapshot / "S-02-2026-305_2026-11-01.jsonl.gz", "rt", encoding="utf-8").read()
        for s in ("SKIPCODE", "ENGMEANING", "NANORI"):
            self.assertNotIn(s, k)

    def test_export_reports_kanji_missing_without_raw(self):
        self.assertEqual(self.refresh(), 0, self.log)
        shutil.rmtree(self.p.raw)
        self.assertEqual(build.main(["export-gloss-input"], paths=self.p, out=self.log.append), 0, self.log)
        rep = self.p.report("kanji_missing.tsv", "T").read_text(encoding="utf-8").splitlines()
        self.assertEqual(rep[0], "kanji\tvocab_id")
        self.assertTrue(any("kanji derived" in l for l in self.log))

    def test_build_db_rows_and_kanji(self):
        self.assertEqual(self.full_build(), 0, self.log)
        con = sqlite3.connect(self.p.assets / "content.db")
        try:
            self.assertEqual(con.execute("SELECT id, written, reading, level, sort_key, pos, source_id, level_source_id FROM vocab WHERE id='jm:1000002'").fetchall(),
                             [("jm:1000002", "時々", "ときどき", 5, 2, "adv,n", "S-01", "S-06")])
            self.assertEqual([r[0] for r in con.execute("SELECT literal FROM kanji ORDER BY level DESC, sort_key, literal")],
                             ["水", "犬", "時", "猫", "人", "日", "本"])
            # 각 한자 카드는 그 한자를 쓰는 첫 단어와 같은 번호 체계: kanji.sort_key == MIN(vocab.sort_key)
            self.assertEqual(con.execute("SELECT COUNT(*) FROM kanji k WHERE k.sort_key <> (SELECT MIN(v.sort_key) FROM vocab_kanji vk JOIN vocab v ON v.id=vk.vocab_id WHERE vk.kanji=k.literal AND v.level=k.level)").fetchone(), (0,))
            lit = [r[0] for r in con.execute("SELECT literal FROM kanji")]
            self.assertNotIn("々", lit)
            self.assertEqual(con.execute("SELECT value FROM content_meta WHERE key='levels'").fetchone(), ("5,4",))
            meta = json.loads(con.execute("SELECT value FROM content_meta WHERE key='sources'").fetchone()[0])
            self.assertEqual(sorted(meta), ["S-01", "S-02", "S-03", "S-06"])
            self.assertEqual(meta["S-01"]["files"], {"JMdict_e": self.man[0]["sha256"]})
        finally:
            con.close()

    def test_attribution_matches_manifest_and_notices(self):
        self.assertEqual(self.full_build(models=("model-b", "model-a")), 0, self.log)
        attr = read_json(self.p.assets / "attribution.json")
        by = {s["id"]: s for s in attr["sources"]}
        self.assertEqual(set(by), {"S-01", "S-02", "S-03", "S-06"})
        for m in self.man:
            self.assertEqual(by[m["id"]]["links"], m["links"])
            self.assertEqual(by[m["id"]]["extraCredits"], m["extraCredits"])
        from pl_attr import license_md
        lic = license_md(attr)
        for m in self.man:
            for c in m["extraCredits"]:
                self.assertIn(c["text"], lic)
        self.assertEqual(attr["modelNames"], ["model-a", "model-b"])
        n10 = next(n for n in attr["notices"] if n["id"] == "N-10")
        self.assertIn("JMdict(EDRDG)의 영어 뜻을 바탕으로 한국어로 번역·편집했습니다.", n10["body"])
        self.assertIn("번역에는 AI(model-a, model-b)를 썼습니다.", n10["body"])
        self.assertNotIn("직접 작성", n10["body"])
        self.assertEqual([n["id"] for n in attr["notices"]], ["N-09", "N-10"])
        self.assertTrue(all(set(n) == {"id", "title", "body"} for n in attr["notices"]))
        self.assertIn("「level list」", attr["notices"][0]["body"])
        self.assertIn("model-a", attr["firstNotice"]["body"])
        self.assertEqual(attr["dbLicense"]["name"], "CC BY-SA 4.0")
        for text in [n["body"] for n in attr["notices"]] + [attr["firstNotice"]["body"]]:
            self.assertNotRegex(text, r"\{[^{}\s]*\}")
        self.assertEqual(list(attr["meaningSourceTexts"]), ["translated-llm", "translated-llm-nomodel", "translated-human", "authored-llm", "authored-llm-nomodel", "authored-human", "unknown"])

    def version(self):
        con = sqlite3.connect(self.p.assets / "content.db")
        try:
            return con.execute("SELECT value FROM content_meta WHERE key='content_version'").fetchone()[0]
        finally:
            con.close()

    def test_authored_override_files_change_content_version(self):
        self.assertEqual(self.full_build(), 0, self.log)
        seen = {self.version()}
        for name, val in (("reading-overrides.tsv", "ときどき"), ("written-overrides.tsv", "時々")):
            f = self.p.authored / name
            f.parent.mkdir(parents=True, exist_ok=True)
            # 기본값과 같은 값, 한 줄 추가
            f.write_text((f.read_text(encoding="utf-8") if f.exists() else "") + "jm:1000002\t" + val + "\t테스트 " + name + "\n", encoding="utf-8")
            self.assertEqual(build.main([], paths=self.p, out=self.log.append), 0, self.log)
            self.assertNotIn(self.version(), seen, name)
            seen.add(self.version())

    def test_missing_gloss_is_reported_and_count_check_fails(self):
        self.assertEqual(self.full_build(drop=2), 1)
        self.assertTrue(self.p.report("no_gloss.tsv", "T").exists())
        self.assertTrue(any(f.startswith("FAIL: count N5") for f in check.run_checks(self.p)))

    def test_stale_gloss_warns_but_builds(self):
        self.assertEqual(self.full_build(stale=True), 0, self.log)
        self.assertEqual(len(self.p.report("stale_gloss.tsv", "T").read_text().splitlines()), 1 + 6)

    def test_span_reports_include_ko_gloss_and_release_authored(self):
        self.refresh()
        self.write_gloss()
        av = self.p.authored / "vocab"
        av.mkdir(parents=True)
        (av / "n5.jsonl").write_text(jdump({"id": "av-n5-0001", "written": None, "reading": "ゐ", "level": 5, "pos": "n", "meanings": ["「ゐ」옛 글자"],
                                            "koSource": "authored", "method": "human", "reviewStatus": "unreviewed", "reviewedAt": None, "reviewerKind": None}) + "\n", encoding="utf-8")
        build.main([], paths=self.p, out=self.log.append)
        a = self.p.report("ko_spans_authored.tsv", "T").read_text(encoding="utf-8")
        self.assertIn("authored\tav-n5-0001\t「ゐ」", a)
        self.assertTrue(self.p.report("ko_spans_ko-gloss.tsv", "T").exists())

    def test_gloss_ruby_in_ko_field_fails_build(self):
        self.refresh()
        self.write_gloss()
        f = self.p.kogloss / "N5.jsonl"
        f.write_text(f.read_text(encoding="utf-8").replace("뜻 みず", "{水|みず}"), encoding="utf-8")
        self.assertEqual(build.main([], paths=self.p, out=self.log.append), 1)

    def test_ko_gloss_check(self):
        check_ = lambda: build.main(["ko-gloss", "--check"], paths=self.p, out=self.log.append)
        self.refresh()
        self.assertEqual(check_(), 1)  # ko-gloss 파일 없음
        self.write_gloss(drop=1)
        f = self.p.kogloss / "N5.jsonl"
        good = re.sub(r'"enHash":"[0-9a-f]{64}"', '"enHash":"' + "0" * 64 + '"', f.read_text(encoding="utf-8"), count=1)
        f.write_text(good, encoding="utf-8")
        self.assertEqual(check_(), 0, self.log)
        self.assertEqual(sum("no Korean meaning" in l for l in self.log), 1)
        self.assertIn("ko-gloss --check: OK (5 rows, 1 missing, 1 stale)", self.log)
        for bad in (good.replace('"jmdict-translated"', '"jmdict"', 1),  # 허용 값 밖
                    good.replace('"enHash":"' + "0" * 64 + '"', '"enHash":null', 1),
                    good + good.splitlines()[0].replace('"vocabId":"jm:', '"vocabId":"jm:9') + "\n"):  # export에 없는 ID
            f.write_text(bad, encoding="utf-8")
            self.assertEqual(check_(), 1, bad)

    def test_committed_ko_gloss_format(self):
        from pl_common import Paths
        import pl_db
        recs = pl_db.load_ko_gloss(Paths(), {"levels": [5]})
        self.assertEqual(len(recs), 667)
        # M11(D-03 잠정): N5는 에이전트 전수 검수. 사람 검수는 아직 없으므로 human이 있으면 안 된다.
        bad = [v for v, r in recs.items() if (r["reviewStatus"], r["reviewerKind"]) != ("reviewed", "agent")
               or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(r["reviewedAt"]))]
        self.assertEqual(bad, [])

    def test_committed_ko_gloss_n4(self):
        # M26b(D-03 잠정, U-4 A): N4 뜻은 10% 표본을 에이전트가 검수(reviewed), 나머지는 sampled. 사람 표시 행은 없다.
        from pl_common import Paths
        import pl_db
        n4 = pl_db.load_ko_gloss(Paths(), {"levels": [4]})
        self.assertEqual(len(n4), 630)
        self.assertFalse(set(n4) & set(pl_db.load_ko_gloss(Paths(), {"levels": [5]})))
        bad = [v for v, r in n4.items() if (r["koSource"], r["reviewerKind"]) != ("jmdict-translated", "agent")
               or r["reviewStatus"] not in ("reviewed", "sampled") or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(r["reviewedAt"]))
               or not re.fullmatch(r"llm:[\w.-]+@\d{4}-\d{2}-\d{2}", r["method"]) or not 1 <= len(r["meanings"]) <= 7]
        self.assertEqual(bad, [])
        reviewed = {v for v, r in n4.items() if r["reviewStatus"] == "reviewed"}
        self.assertGreaterEqual(len(reviewed), -(-len(n4) // 10))  # ceil(행 수 × 0.1)
        ids = (Paths().root / "_workspace/review/ko-gloss_N4-20261107.ids").read_text(encoding="utf-8").split()
        self.assertEqual(set(ids), reviewed)

    def test_review_gate_blocks_unreviewed_n4_in_release(self):
        self.assertEqual(self.full_build(n4_status="unreviewed"), 1, self.log)  # build가 끝에 check를 돌려 실패로 끝낸다
        gate = [f for f in check.run_checks(self.p) if f.startswith("FAIL: review-gate")]
        con = sqlite3.connect(self.p.assets / "content.db")
        n4 = con.execute("SELECT count(*) FROM vocab_meaning m JOIN vocab v ON v.id = m.vocab_id WHERE v.level = 4").fetchone()[0]
        n5_unrev = con.execute("SELECT count(*) FROM vocab_meaning m JOIN vocab v ON v.id = m.vocab_id WHERE v.level = 5 AND m.ko_review_status = 'unreviewed'").fetchone()[0]
        con.close()
        self.assertTrue(n4 > 0 and n5_unrev > 0)  # N5는 unreviewed여도 대상 아님
        self.assertEqual(len(gate), n4)
        self.assertTrue(all("level 4 is unreviewed" in f for f in gate), gate)
        con = sqlite3.connect(self.p.assets / "content.db")
        con.execute("UPDATE vocab_meaning SET ko_review_status = 'sampled', ko_reviewed_at = '2026-11-10', ko_reviewer_kind = 'agent' WHERE vocab_id IN (SELECT id FROM vocab WHERE level = 4)")
        con.commit()
        con.close()
        self.assertFalse([f for f in check.run_checks(self.p) if f.startswith("FAIL: review-gate")])
        con = sqlite3.connect(self.p.assets / "content.db")
        con.execute("UPDATE vocab_meaning SET ko_review_status = 'reviewed' WHERE vocab_id IN (SELECT id FROM vocab WHERE level = 4)")
        con.commit()
        con.close()
        self.assertFalse([f for f in check.run_checks(self.p) if f.startswith("FAIL: review-gate")])

    def test_release_checks_catch_empty_fields_and_app_assets(self):
        self.assertEqual(self.full_build(), 0, self.log)
        ap = self.p.assets / "attribution.json"
        good = ap.read_text(encoding="utf-8")
        for field in ("attribution", "licenseUrl", "modifications"):
            for bad in (None, "  "):
                a = json.loads(good)
                a["sources"][0][field] = bad
                ap.write_text(json.dumps(a, ensure_ascii=False), encoding="utf-8")
                self.assertIn(f"FAIL: attribution S-01 {field} is null or empty (release)", check.run_checks(self.p))
        ap.write_text(good, encoding="utf-8")
        man = json.loads(self.p.manifest.read_text())
        man.append({"id": "S-11", "name": "icons", "status": "unconfirmed", "scope": "app-asset(콘텐츠 DB 아님)"})
        self.p.manifest.write_text(json.dumps(man))
        self.assertTrue(any(f.startswith("FAIL: app-asset S-11") for f in check.run_checks(self.p)))
        man[-1]["status"] = "confirmed"
        next(m for m in man if m["id"] == "S-06")["status"] = "unconfirmed"
        self.p.manifest.write_text(json.dumps(man))
        out = check.run_checks(self.p)
        self.assertIn("FAIL: kind levelSource S-06 is not confirmed", out)
        self.assertIn("FAIL: source S-06 not confirmed", out)

    def test_snapshot_hash_check(self):
        self.assertEqual(self.full_build(), 0, self.log)
        f = self.p.snapshot / "levels-S-06-b062d4e_2025-08-26.tsv"
        f.write_text(f.read_text() + "5\t99\t\t\tx\n")
        self.assertIn("FAIL: snapshot S-06 hash differs from content/snapshot/levels-S-06-b062d4e_2025-08-26.tsv", check.run_checks(self.p))
