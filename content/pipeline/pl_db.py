"""데이터셋 모으기(sample·release), 한자 파생(4.3), Room 스키마 JSON 기반 DB 쓰기(4.2)."""
import json
import os
import re
import sqlite3
from pathlib import Path

import levels as lv
import pl_authored as au
import pl_grammar as gm
import pl_listening as ls
import pl_question as qs
import pl_quizgen as qg
from pl_common import CJK, Fail, jdump, latest_schema, load_manifest, read_jsonl, sha256_bytes, sha256_file, write_text
from pl_snapshot import read_snapshot


class Dataset:
    def __init__(self, kind):
        self.kind = kind
        self.vocab, self.meanings = [], []
        self.kanji_info, self.kanji_source, self.link_source = {}, "authored", "authored"
        self.strokes = {}  # literal → 획 경로 목록(S-03). sample은 비어 있다
        self.level_source = "authored"
        self.sources = {}  # content_meta.sources
        self.inputs = []  # (라벨, 바이트) — content_version 해시 입력
        self.spans, self.no_gloss, self.stale = [], [], []
        self.grammar, self.grammar_examples = [], []
        self.questions, self.exams, self.listening = [], [], []
        self.jm_form, self.vocab_conflict, self.quizgen_why = [], [], {}  # quizgen.md 3.3. sample 빌드는 0행


def add_input(ds, paths, p):
    ds.inputs.append((str(Path(p).relative_to(paths.root)), Path(p).read_bytes()))


def number(items):
    """[(vid, level, row_no)] → {vid: 급수 안 sort_key}. 급수 목록 순서, 동률은 id."""
    out, by = {}, {}
    for vid, level, row_no in items:
        by.setdefault(level, []).append((row_no, vid))
    for lst in by.values():
        for n, (_, vid) in enumerate(sorted(lst)):
            out[vid] = n
    return out


def meaning_rows(vid, rec, source_id):
    return [{"vocab_id": vid, "ord": i, "text_ko": t, "source_id": source_id, "ko_source": rec["koSource"],
             "ko_method": rec["method"], "ko_review_status": rec["reviewStatus"],
             "ko_reviewed_at": rec.get("reviewedAt"), "ko_reviewer_kind": rec.get("reviewerKind")}
            for i, t in enumerate(rec["meanings"])]


def add_grammar(ds, paths, config):
    """문법 작성 파일 → ds.grammar·grammar_example (9.1). 어휘를 다 모은 뒤 부른다(sort_key가 급수별 어휘 수 V를 쓴다)."""
    recs, files = gm.load_grammar(paths, ds.kind, load_manifest(paths), config["levels"])
    for f in files:
        add_input(ds, paths, f)
    v = {}
    for r in ds.vocab:
        v[r["level"]] = v.get(r["level"], 0) + 1
    ds.grammar, ds.grammar_examples, ko = gm.grammar_rows(recs, v)
    for rid, texts in ko:
        ds.spans += au.jp_span_rows("authored", rid, texts)


def add_questions(ds, paths, config, check_targets=True):
    """문항·시험 템플릿 → ds.questions·ds.exams (9.2). 어휘·문법을 모은 뒤 부른다(targets가 이 빌드의 ID여야 한다)."""
    known = None
    if check_targets:
        known = {v["id"] for v in ds.vocab} | {g["id"] for g in ds.grammar} | {k["literal"] for k in derive_kanji(ds)[0]}
    recs, exams, files = qs.load_questions(paths, ds.kind, load_manifest(paths), config["levels"], known)
    for f in files:
        add_input(ds, paths, f)
    ds.questions, ds.exams = qs.question_rows(recs), qs.exam_rows(exams)
    for e in exams:
        if not qs.complete_exam(e):
            print(f"exam: {e['level']} template not built (parts[].count missing, architecture 9.2.1 uncertain)")
    for rid, texts in qs.ko_texts(recs):
        ds.spans += au.jp_span_rows("authored", rid, texts)


def add_listening(ds, paths, config, check_targets=True):
    """청해 대본 → ds.listening (9.3.1). 어휘·문법을 모은 뒤 부른다(targets가 이 빌드의 ID여야 한다)."""
    known = None
    if check_targets:
        known = {v["id"] for v in ds.vocab} | {g["id"] for g in ds.grammar} | {k["literal"] for k in derive_kanji(ds)[0]}
    recs, files = ls.load_listening(paths, ds.kind, load_manifest(paths), config["levels"], known)
    for f in files:
        add_input(ds, paths, f)
    ds.listening = ls.listening_rows(recs)
    for rid, texts in ls.ko_texts(recs):
        ds.spans += au.jp_span_rows("authored", rid, texts)


def sample_dataset(paths, config):
    ds = Dataset("sample")
    d = paths.authored / "sample"
    vrecs, vfiles = au.load_authored(d, "vocab.jsonl", au.vocab_problems)
    krecs, kfiles = au.load_authored(d, "kanji.jsonl", au.kanji_problems)
    for f in vfiles + kfiles:
        add_input(ds, paths, f)
    keep = [(n, r) for n, r in enumerate(vrecs, 1) if r["level"] in config["levels"]]
    keys = number([(r["id"], r["level"], n) for n, r in keep])
    for n, r in keep:
        ds.vocab.append({"id": r["id"], "written": r.get("written"), "reading": r["reading"], "level": r["level"],
                         "sort_key": keys[r["id"]], "pos": r["pos"], "source_id": "authored", "level_source_id": "authored"})
        ds.meanings += meaning_rows(r["id"], r, "authored")
        ds.spans += au.jp_span_rows("authored", r["id"], r["meanings"])
    ds.kanji_info = {k["literal"]: {"stroke_count": k["strokeCount"], "freq": k.get("freq"),
                                    "on": k["on"], "kun": k["kun"], "ko": k["ko"]} for k in krecs}
    add_grammar(ds, paths, config)
    add_questions(ds, paths, config)
    add_listening(ds, paths, config)
    return ds


KATAKANA = re.compile(r"^[ァ-ヺー]+$")


def senses_for(entry, written, reading):
    """고른 표기·읽기에 적용되는 sense의 gloss(stagk/stagr 제한을 거른다)."""
    return [s["gloss"] for s in entry["senses"]
            if (not s.get("stagk") or written in s["stagk"]) and (not s.get("stagr") or reading in s["stagr"])]


def en_hash(entry, written, reading):
    return sha256_bytes(jdump({"reading": entry["reb"], "written": entry["keb"], "senses": senses_for(entry, written, reading)}).encode())


def representative(entry, row, reading_ov=None, written_ov=None):
    """대표 표기: 첫 sense가 uk이거나 읽기가 가타카나뿐이거나 목록 kanji가 비었으면(목록이 가나로 씀) None,
    아니면 written-overrides.tsv > 목록 kanji가 keb에 있을 때 그것 > 첫 keb.
    대표 읽기: reading-overrides.tsv > 목록 kana(reb에 있을 때) > 첫 reb."""
    reading = row.kana if row.kana in entry["reb"] else entry["reb"][0]
    if reading_ov:
        if reading_ov not in entry["reb"]:
            raise Fail(f"FAIL: reading-overrides {reading_ov} not a reading of jm:{entry['seq']}")
        reading = reading_ov
    written = None
    kana_only = (entry["senses"] and entry["senses"][0]["uk"]) or KATAKANA.match(reading)
    if entry["keb"] and not kana_only and row.kanji:
        written = row.kanji if row.kanji in entry["keb"] else entry["keb"][0]
        if written_ov:
            if written_ov not in entry["keb"]:
                raise Fail(f"FAIL: written-overrides {written_ov} not a written form of jm:{entry['seq']}")
            written = written_ov
    return written, reading


def read_reading_overrides(path):
    """reading-overrides.tsv: vocabId, reading, 사유 → {vocabId: reading}"""
    out = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            c = line.split("\t")
            if line.strip() and not line.startswith("#"):
                if len(c) < 3 or not c[2].strip():
                    raise Fail(f"FAIL: reading-overrides bad line: {line}")
                out[c[0]] = c[1]
    return out


def mapped(paths, config, manifest):
    """스냅샷 + 급수 매핑 → (snap, idx, {vid: (급수, row_no)}, unmatched, overrides)"""
    ls = config["levelSource"]
    snap = read_snapshot(paths, manifest, ls)
    ov = lv.read_overrides(paths.authored / "level-overrides.tsv", ls)
    snap["reading_ov"] = read_reading_overrides(paths.authored / "reading-overrides.tsv")
    snap["written_ov"] = read_reading_overrides(paths.authored / "written-overrides.tsv")
    idx = lv.Index(snap["entries"])
    vocab, unmatched, _ = lv.map_levels(snap["rows"], idx, lv.LEVEL_SOURCES[ls], ov)
    return snap, idx, vocab, unmatched, ov


def gloss_input_dataset(snap, idx, vocab, rows, config):
    """뜻풀이 없이 어휘·한자 파생만 확인하는 Dataset(릴리스 DB 아님)."""
    ds = Dataset("release")
    ds.kanji_source, ds.link_source = "S-02", "S-01"
    keys = number([(vid, lvl, no) for vid, (lvl, no) in vocab.items() if lvl in config["levels"]])
    for vid, (lvl, no) in sorted(vocab.items()):
        if lvl in config["levels"] and vid.startswith("jm:"):
            w, _ = representative(idx.by_seq[int(vid[3:])], rows[(lvl, no)], snap["reading_ov"].get(vid), snap["written_ov"].get(vid))
            ds.vocab.append({"id": vid, "written": w, "level": lvl, "sort_key": keys[vid]})
    ds.kanji_info = {c: {"stroke_count": k["stroke_count"], "freq": k["freq"], "on": k["ja_on"], "kun": k["ja_kun"], "ko": k["korean_h"]}
                     for c, k in snap["kanji"].items() if k["stroke_count"] is not None}
    return ds


def export_gloss_input(paths, config, manifest, out=print):
    """스냅샷에서 급수 매핑된 어휘의 영어 gloss를 번역 작성자 입력으로 낸다(content/work/en-gloss)."""
    snap, idx, vocab, _, _ = mapped(paths, config, manifest)
    rows = {(r.level, r.row_no): r for r in snap["rows"]}
    for level in config["levels"]:
        lines = []
        for vid, (lvl, no) in sorted(vocab.items(), key=lambda kv: (kv[1][1], kv[0])):
            if lvl != level or not vid.startswith("jm:"):
                continue
            e = idx.by_seq[int(vid[3:])]
            w, r = representative(e, rows[(lvl, no)], snap["reading_ov"].get(vid), snap["written_ov"].get(vid))
            lines.append(jdump({"vocabId": vid, "written": w, "reading": r, "pos": ",".join(e["pos"]),
                                "senses": senses_for(e, w, r), "enHash": en_hash(e, w, r)}) + "\n")
        write_text(paths.work / "en-gloss" / f"N{level}.jsonl", "".join(lines))
    kanji, _, missing = derive_kanji(gloss_input_dataset(snap, idx, vocab, rows, config))
    write_text(paths.report("kanji_missing.tsv", config["milestone"]), "kanji\tvocab_id\n" + "".join(f"{c}\t{v}\n" for c, v in missing))
    out(f"export-gloss-input: {len(kanji)} kanji derived, {len(missing)} kanji missing from KANJIDIC2")
    out(f"export-gloss-input: content/work/en-gloss/N*.jsonl for levels {config['levels']}")


def load_ko_gloss(paths, config, ds=None):
    recs, problems = {}, []
    for level in config["levels"]:
        p = paths.kogloss / f"N{level}.jsonl"
        if not p.exists():
            continue
        if ds is not None:
            add_input(ds, paths, p)
        for r in read_jsonl(p):
            problems += au.meaning_problems(r, "ko-gloss")
            if r.get("vocabId") in recs:
                problems.append(f"FAIL: ko-gloss {r.get('vocabId')} duplicate vocabId")
            recs[r.get("vocabId")] = r
    if problems:
        raise Fail("\n".join(problems))
    return recs


def check_ko_gloss(paths, config, out=print):
    """ko-gloss --check(M9): 형식·허용 값 + export(content/work/en-gloss)와 vocabId·enHash 대조. kind와 무관하게 돈다."""
    problems, export = [], {}
    for level in config["levels"]:
        for f in (paths.kogloss / f"N{level}.jsonl", paths.work / "en-gloss" / f"N{level}.jsonl"):
            if not f.exists():
                problems.append(f"FAIL: ko-gloss {f.relative_to(paths.root)} missing (en-gloss: run export-gloss-input)")
        if problems:
            continue
        export.update({r["vocabId"]: r["enHash"] for r in read_jsonl(paths.work / "en-gloss" / f"N{level}.jsonl")})
    if problems:
        raise Fail("\n".join(problems))
    recs = load_ko_gloss(paths, config)
    problems += [f"FAIL: ko-gloss {v} enHash missing" for v, r in recs.items() if not re.fullmatch(r"[0-9a-f]{64}", str(r.get("enHash")))]
    problems += [f"FAIL: ko-gloss {v} not in en-gloss export" for v in sorted(set(recs) - set(export))]
    if problems:
        raise Fail("\n".join(problems))
    missing = sorted(set(export) - set(recs))
    stale = sorted(v for v in recs if recs[v]["enHash"] != export[v])
    for v in missing:
        out(f"WARN: ko-gloss {v} no Korean meaning")
    for v in stale:
        out(f"WARN: ko-gloss {v} enHash differs from export (stale)")
    out(f"ko-gloss --check: OK ({len(recs)} rows, {len(missing)} missing, {len(stale)} stale)")


def release_dataset(paths, config, manifest):
    ls = config["levelSource"]
    ds = Dataset("release")
    ds.level_source, ds.kanji_source, ds.link_source = ls, "S-02", "S-01"
    snap, idx, vocab, unmatched, ov = mapped(paths, config, manifest)
    from pl_snapshot import report_unmatched
    report_unmatched(paths, config, unmatched)
    for f in list(snap["files"].values()) + [snap["related_file"], paths.snapshot / "sources.json"]:
        add_input(ds, paths, f)
    for name in ("level-overrides.tsv", "reading-overrides.tsv", "written-overrides.tsv"):  # build 입력인 authored 덮어쓰기 전부
        if (paths.authored / name).exists():
            add_input(ds, paths, paths.authored / name)
    gloss = load_ko_gloss(paths, config, ds)
    avs, avfiles = au.load_authored(paths.authored / "vocab", "*.jsonl", au.vocab_problems)
    for f in avfiles:
        add_input(ds, paths, f)
    authored = {r["id"]: r for r in avs}
    rows = {(r.level, r.row_no): r for r in snap["rows"]}
    keys = number([(vid, lvl, no) for vid, (lvl, no) in vocab.items() if lvl in config["levels"]])
    for vid, (lvl, no) in sorted(vocab.items()):
        if lvl not in config["levels"]:
            continue
        if vid.startswith("av-"):
            r = authored.get(vid)
            if r is None:
                raise Fail(f"FAIL: build level-override {vid} not in content/authored/vocab")
            written, reading, pos, rec, src, lsrc = r.get("written"), r["reading"], r["pos"], r, "authored", "authored"
        else:
            e = idx.by_seq.get(int(vid[3:]))
            if e is None:
                raise Fail(f"FAIL: build {vid} not in snapshot (level-overrides seq missing)")
            written, reading = representative(e, rows[(lvl, no)], snap["reading_ov"].get(vid), snap["written_ov"].get(vid))
            pos, rec, src, lsrc = ",".join(e["pos"]), gloss.get(vid), "S-01", ls
            if rec is None:
                ds.no_gloss.append(vid)
                continue
            eh = en_hash(e, written, reading)
            if rec.get("enHash") != eh:
                ds.stale.append((vid, str(rec.get("enHash")), eh))
        ds.vocab.append({"id": vid, "written": written, "reading": reading, "level": lvl, "sort_key": keys[vid],
                         "pos": pos, "source_id": src, "level_source_id": lsrc})
        ds.meanings += meaning_rows(vid, rec, "S-01" if rec["koSource"] == "jmdict-translated" else "authored")
        ds.spans += au.jp_span_rows("ko-gloss" if src == "S-01" else "authored", vid, rec["meanings"])
    ds.kanji_info = {c: {"stroke_count": k["stroke_count"], "freq": k["freq"], "on": k["ja_on"], "kun": k["ja_kun"], "ko": k["korean_h"]}
                     for c, k in snap["kanji"].items() if k["stroke_count"] is not None}
    ds.strokes = snap["strokes"]
    ds.sources = {s: {"version": snap["sources"][s]["version"], "files": snap["sources"][s]["files"], "snapshot": snap["hashes"][s]}
                  for s in sorted(snap["hashes"])}
    ds.sources["S-01"]["related"] = snap["related_hash"]
    qg.derive(ds, snap["entries"], snap["related"])
    add_grammar(ds, paths, config)
    add_questions(ds, paths, config)
    add_listening(ds, paths, config)
    return ds


def derive_kanji(ds):
    """4.3: written 안의 KANJIDIC2 한자만. 반환 (kanji 행, vocab_kanji 행, 없는 한자 [(문자, 어휘 ID)])"""
    first = {}  # 문자 → (level, vocab sort_key, 단어 안 위치)가 가장 앞선 것
    pairs, missing = set(), set()
    for v in ds.vocab:
        for pos, c in enumerate(v["written"] or ""):
            if not CJK.match(c):  # 々·ヶ·〆·가나 제외
                continue
            if c not in ds.kanji_info:
                missing.add((c, v["id"]))
                continue
            pairs.add((c, v["id"]))
            cand = (-v["level"], v["sort_key"], pos)
            if c not in first or cand < first[c]:
                first[c] = cand
    order = sorted(first, key=lambda c: (first[c], ord(c)))
    kanji = []
    for c in order:
        level = -first[c][0]
        i = ds.kanji_info[c]
        kanji.append({"literal": c, "level": level, "stroke_count": i["stroke_count"], "freq": i["freq"],
                      "on_readings": jdump(i["on"]), "kun_readings": jdump(i["kun"]), "korean_readings": jdump(i["ko"]),
                      "sort_key": first[c][1], "source_id": ds.kanji_source})
    vk = [{"kanji": c, "vocab_id": vid, "source_id": ds.link_source} for c, vid in sorted(pairs)]
    return kanji, vk, sorted(missing)


def stroke_rows(ds, kanji):
    """9.4: DB 한자 중 스냅샷에 획 경로가 있는 것만. 경로는 jdump(공백 없는 JSON 문자열 배열)."""
    return [{"literal": k["literal"], "paths_json": jdump(ds.strokes[k["literal"]]), "source_id": "S-03"}
            for k in sorted(kanji, key=lambda r: r["literal"]) if k["literal"] in ds.strokes]


def content_version(ds, config, schema_bytes):
    h = [config["kind"], ",".join(map(str, sorted(config["levels"]))), str(config.get("levelSource") if config["kind"] != "sample" else None), sha256_bytes(schema_bytes)]
    for label, data in sorted(ds.inputs):
        h.append(f"{label}\0{sha256_bytes(data)}")
    return "v" + sha256_bytes("\n".join(h).encode())[:12]


def update_ids(paths, kind, now, version):
    """content/ids/<kind>/{vocab,kanji}.txt는 배포한 ID(합집합). retired.tsv는 사람이 고치는 입력: 사라진 ID가 거기 없으면 멈춘다."""
    d = paths.ids / kind
    rt = d / "retired.tsv"
    retired = {l.split("\t")[0]: (l.split("\t") + [version])[1] for l in rt.read_text(encoding="utf-8").splitlines() if l} if rt.exists() else {}
    for name, ids in now.items():
        f = d / f"{name}.txt"
        prev = set(f.read_text(encoding="utf-8").split()) if f.exists() else set()
        lost = sorted(prev - ids - set(retired))
        if lost:
            raise Fail(f"FAIL: ids {lost[0]} lost ({len(lost)} total; add to {rt.relative_to(paths.root)} to retire)")
    for name, ids in now.items():
        f = d / f"{name}.txt"
        prev = set(f.read_text(encoding="utf-8").split()) if f.exists() else set()
        write_text(f, "".join(i + "\n" for i in sorted(prev | ids)))
    back = set().union(*now.values())
    return sorted((i, v) for i, v in retired.items() if i not in back)


def create_schema(con, schema):
    db = schema["database"]
    for ent in db["entities"]:
        name = ent["tableName"]
        con.execute(ent["createSql"].replace("${TABLE_NAME}", name))
        for ix in ent.get("indices", []):
            con.execute(ix["createSql"].replace("${TABLE_NAME}", name))
    for q in db["setupQueries"]:
        con.execute(q)
    con.execute(f"PRAGMA user_version = {int(db['version'])}")


def insert(con, table, rows):
    if rows:
        cols = list(rows[0])
        con.executemany(f"INSERT INTO `{table}` ({', '.join(f'`{c}`' for c in cols)}) VALUES ({', '.join('?' * len(cols))})",
                        [[r[c] for c in cols] for r in rows])


def write_db(path, schema, tables):
    tmp = Path(str(path) + ".tmp")
    tmp.unlink(missing_ok=True)
    con = sqlite3.connect(tmp)
    try:
        create_schema(con, schema)
        for name, rows in tables:
            insert(con, name, rows)
        con.commit()
        con.execute("VACUUM")
    finally:
        con.close()
    os.replace(tmp, path)


def build_db(paths, config, ds, out=print):
    """→ (content_version, db 경로)"""
    _, schema = latest_schema(paths)
    schema_file = max((p for p in paths.schemas.glob("*.json") if p.stem.isdigit()), key=lambda p: int(p.stem))
    version = content_version(ds, config, schema_file.read_bytes())
    kanji, vk, missing = derive_kanji(ds)
    retired = update_ids(paths, ds.kind, {"vocab": {v["id"] for v in ds.vocab}, "kanji": {k["literal"] for k in kanji},
                                       **({"grammar": {g["id"] for g in ds.grammar}} if ds.grammar else {}),
                                       **({"question": {q["id"] for q in ds.questions}} if ds.questions else {}),
                                       **({"listening": {q["id"] for q in ds.listening}} if ds.listening else {})}, version)
    meta = [("content_version", version), ("kind", ds.kind), ("level_source", ds.level_source),
            ("levels", ",".join(map(str, sorted(config["levels"], reverse=True)))), ("sources", jdump(ds.sources))]
    tables = [("content_meta", [{"key": k, "value": v} for k, v in sorted(meta)]),
              ("vocab", sorted(ds.vocab, key=lambda r: r["id"])),
              ("vocab_meaning", sorted(ds.meanings, key=lambda r: (r["vocab_id"], r["ord"]))),
              ("kanji", sorted(kanji, key=lambda r: r["literal"])),
              ("vocab_kanji", vk),
              ("kanji_stroke", stroke_rows(ds, kanji)),
              ("jm_form", ds.jm_form),
              ("vocab_conflict", ds.vocab_conflict),
              ("grammar", ds.grammar),
              ("grammar_example", ds.grammar_examples),
              ("question", ds.questions),
              ("exam_template", ds.exams),
              ("listening", ds.listening),
              ("retired", [{"id": i, "retired_in": v} for i, v in retired])]
    paths.assets.mkdir(parents=True, exist_ok=True)
    db = paths.assets / "content.db"
    write_db(db, schema, tables)
    write_text(paths.assets / "content.db.sha256", sha256_file(db) + "\n")
    m = config["milestone"]
    write_text(paths.report("kanji_missing.tsv", m), "kanji\tvocab_id\n" + "".join(f"{c}\t{v}\n" for c, v in missing))
    if ds.kind == "release":
        have = {k["literal"] for k in kanji} & ds.strokes.keys()
        write_text(paths.report("strokes_missing.tsv", m), "".join(f"{k['literal']}\tN{k['level']}\n" for k in kanji if k["literal"] not in have))  # 머리말 없음: 행 수 = 빠진 한자 수
        # KANJIDIC2 획 수와 KanjiVG 경로 수가 다른 한자(FAIL 아님: 그림은 KanjiVG 기준). 머리말 없음.
        write_text(paths.report("strokes_count_mismatch.tsv", m), "".join(
            f"{k['literal']}\t{k['stroke_count']}\t{len(ds.strokes[k['literal']])}\n" for k in kanji
            if k["literal"] in have and k["stroke_count"] != len(ds.strokes[k["literal"]])))
    if ds.kind == "release":
        write_text(paths.report("quizgen_conflicts.tsv", m), qg.conflicts_tsv(ds.quizgen_why, {v["id"]: v["level"] for v in ds.vocab}))
    if ds.no_gloss:
        write_text(paths.report("no_gloss.tsv", m), "vocab_id\n" + "".join(f"{v}\n" for v in ds.no_gloss))
    write_text(paths.report("stale_gloss.tsv", m), "vocab_id\tgloss_enHash\tcurrent_enHash\n" + "".join("\t".join(s) + "\n" for s in ds.stale))
    out(f"build: {len(ds.vocab)} vocab, {len(kanji)} kanji, {len(ds.grammar)} grammar, {len(ds.questions)} question, {len(ds.exams)} exam, {len(ds.listening)} listening, {len(ds.jm_form)} jm_form, {len(ds.vocab_conflict)} vocab_conflict, version {version}, {len(missing)} kanji missing, {len(ds.no_gloss)} without gloss")
    return version, db
