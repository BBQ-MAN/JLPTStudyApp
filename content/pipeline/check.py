"""check 단계(6.5): 커밋된 파일만으로, 네트워크·content/raw 없이 번들 에셋을 검사한다.
실패마다 'FAIL: <검사> <이유>' 한 줄, 하나라도 있으면 종료 코드 1."""
import json
import re
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))  # python3 -I 는 스크립트 폴더를 경로에 넣지 않는다

import levels as lv
import pl_attr as at
import pl_authored as au
from pl_common import Fail, Paths, latest_schema, load_config, load_manifest, read_json, read_jsonl, safe_version, sha256_file

AUDIO = {".mp3", ".ogg", ".wav", ".m4a", ".aac", ".flac", ".opus", ".wma", ".mid"}
ALLOWED_ASSETS = {"content.db", "content.db.sha256", "attribution.json"}
P_TABLES = {"grammar": "id", "question": "id", "listening": "id"}
MiB = 1 << 20


def norm(sql):
    return re.sub(r"\s+", " ", sql.replace(" IF NOT EXISTS", "")).strip()


def tables_with(con, col):
    out = []
    for (t,) in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall():
        if col in [c[1] for c in con.execute(f"PRAGMA table_info(`{t}`)")]:
            out.append(t)
    return out


def check_header(db):
    with open(db, "rb") as f:
        return [] if f.read(16) == b"SQLite format 3\x00" else ["FAIL: protection content.db does not start with the SQLite header (L-01.nodrm)"]


def check_hash(paths, db):
    f = paths.assets / "content.db.sha256"
    if not f.exists():
        return ["FAIL: hash content.db.sha256 missing"]
    want, got = f.read_text().strip(), sha256_file(db)
    return [] if want == got else [f"FAIL: hash content.db.sha256 {want[:12]} != actual {got[:12]}"]


def check_schema(paths, con):
    _, schema = latest_schema(paths)
    d = schema["database"]
    out = []
    uv = con.execute("PRAGMA user_version").fetchone()[0]
    if uv != d["version"]:
        out.append(f"FAIL: schema user_version db={uv} json={d['version']}")
    try:
        h = con.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()
    except sqlite3.Error:
        h = None
    if not h or h[0] != d["identityHash"]:
        out.append(f"FAIL: schema room_master_table identity hash {h[0] if h else None} != json {d['identityHash']}")
    want = {}
    for e in d["entities"]:
        want[e["tableName"]] = norm(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for ix in e.get("indices", []):
            want[ix["name"]] = norm(ix["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    have = {n: norm(s) for n, s in con.execute(
        "SELECT name, sql FROM sqlite_master WHERE type IN ('table','index') AND sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'")}
    for n in sorted(want.keys() - have.keys()):
        out.append(f"FAIL: schema {n} missing in content.db")
    for n in sorted(have.keys() - want.keys()):
        out.append(f"FAIL: schema {n} not in schema json")
    for n in sorted(want.keys() & have.keys()):
        if want[n] != have[n]:
            out.append(f"FAIL: schema {n} differs from schema json")
    return out


def check_kind(cfg, manifest, meta):
    out = []
    if cfg["kind"] == "release":
        if meta.get("kind") != "release":
            out.append(f"FAIL: kind config is release but content_meta.kind={meta.get('kind')}")
        ls = cfg.get("levelSource")
        if not ls or manifest.get(ls, {}).get("status") != "confirmed":
            out.append(f"FAIL: kind levelSource {ls} is not confirmed")
    elif meta.get("kind") != cfg["kind"]:
        out.append(f"FAIL: kind config={cfg['kind']} content_meta.kind={meta.get('kind')}")
    return out


def check_sources(con, manifest):
    out, seen = [], set()
    for col in ("source_id", "level_source_id"):
        for t in tables_with(con, col):
            n = con.execute(f"SELECT COUNT(*) FROM `{t}` WHERE `{col}` IS NULL OR `{col}` = ''").fetchone()[0]
            if n:
                out.append(f"FAIL: source {t}.{col} empty in {n} rows")
            for (sid,) in con.execute(f"SELECT DISTINCT `{col}` FROM `{t}` WHERE `{col}` != ''"):
                if sid in seen or sid == "authored":
                    continue
                seen.add(sid)
                if sid not in manifest:
                    out.append(f"FAIL: source {sid} not in manifest")
                elif manifest[sid].get("status") != "confirmed":
                    out.append(f"FAIL: source {sid} not confirmed")
    return out


def check_forbidden(paths):
    out = []
    base = paths.root / "app/src/main"
    for root in (base / "assets", base / "res/raw"):
        if root.exists():
            for p in sorted(root.rglob("*")):
                if p.is_file() and p.suffix.lower() in AUDIO:
                    out.append(f"FAIL: forbidden-file {p.relative_to(paths.root)} is an audio file")
    if paths.assets.exists():
        for p in sorted(paths.assets.iterdir()):
            if p.name not in ALLOWED_ASSETS and p.suffix.lower() not in AUDIO:
                out.append(f"FAIL: forbidden-file {p.relative_to(paths.root)} is not an allowed content asset")
    return out


def check_ids(paths, con, kind):
    out = []
    d = paths.ids / kind
    retired = {r[0] for r in con.execute("SELECT id FROM retired")}
    for name, sql in (("vocab", "SELECT id FROM vocab"), ("kanji", "SELECT literal FROM kanji")):
        f = d / f"{name}.txt"
        if not f.exists():
            continue
        now = {r[0] for r in con.execute(sql)}
        for i in sorted(set(f.read_text(encoding="utf-8").split()) - now - retired):
            out.append(f"FAIL: ids {i} was shipped before but is missing (not in retired)")
    return out


def ko_problems(con, table, idcol, manifest):
    out = []
    cols = [c[1] for c in con.execute(f"PRAGMA table_info(`{table}`)")]
    need = [c for c in ("source_id", "ko_source", "ko_method", "ko_review_status") if c in cols]
    if table == "vocab_meaning":
        need.insert(0, "text_ko")
    for r in con.execute(f"SELECT * FROM `{table}`"):
        row = dict(zip(cols, r))
        rid = f"{row[idcol]}" + (f"#{row['ord']}" if "ord" in row else "")
        tag = f"meanings {rid}" if table == "vocab_meaning" else f"ko-columns {table} {rid}"
        for c in need:
            if row[c] is None or str(row[c]).strip() == "":
                out.append(f"FAIL: {tag} {c} empty")
        ks = row.get("ko_source")
        if ks and not (ks in ("authored", "jmdict-translated") or ks in manifest):
            out.append(f"FAIL: {tag} ko_source invalid ({ks})")
        if row.get("ko_method") and not au.METHOD.match(row["ko_method"]):
            out.append(f"FAIL: {tag} ko_method invalid ({row['ko_method']})")
        st = row.get("ko_review_status")
        if st and st not in au.REVIEW:
            out.append(f"FAIL: {tag} ko_review_status invalid ({st})")
        if st in ("sampled", "reviewed") and not row.get("ko_reviewed_at"):
            out.append(f"FAIL: {tag} ko_reviewed_at empty while {st}")
        if row.get("ko_reviewer_kind") not in (None, "agent", "human"):
            out.append(f"FAIL: {tag} ko_reviewer_kind invalid")
    return out


def check_refs(con):
    """9.0: 빈 테이블이면 통과. 행이 있으면 kanji_stroke.literal ⊂ kanji.literal, grammar_example.grammar_id ⊂ grammar.id."""
    out = []
    for sql, tag in (("SELECT literal FROM kanji_stroke WHERE literal NOT IN (SELECT literal FROM kanji) ORDER BY literal", "refs kanji_stroke.literal not in kanji"),
                     ("SELECT DISTINCT grammar_id FROM grammar_example WHERE grammar_id NOT IN (SELECT id FROM grammar) ORDER BY grammar_id", "refs grammar_example.grammar_id not in grammar")):
        out += [f"FAIL: {tag}: {r[0]}" for r in con.execute(sql)]
    return out


def check_meanings(con, manifest):
    out = ko_problems(con, "vocab_meaning", "vocab_id", manifest)
    for (vid,) in con.execute("SELECT id FROM vocab WHERE id NOT IN (SELECT vocab_id FROM vocab_meaning) ORDER BY id"):
        out.append(f"FAIL: meanings {vid} vocab has no meaning row")
    names = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    for t, idc in P_TABLES.items():
        if t in names:
            out += ko_problems(con, t, idc, manifest)
    return out


def check_attribution(paths, manifest, db, cfg, release):
    p = paths.assets / "attribution.json"
    if not p.exists():
        return ["FAIL: attribution attribution.json missing"]
    try:
        attr = read_json(p)
    except ValueError as e:
        return [f"FAIL: attribution attribution.json invalid json ({e})"]
    out = []
    facts = at.db_facts(db)
    have = {s["id"]: s for s in attr.get("sources", [])}
    for sid in sorted(facts["used"]):
        if sid not in have:
            out.append(f"FAIL: attribution {sid} used in DB but missing from attribution.json")
    for sid in ("S-01", "S-02"):
        if sid in facts["used"] and not have.get(sid, {}).get("links"):
            out.append(f"FAIL: attribution {sid} links empty")
    if release:
        for sid, s in have.items():
            if sid == "authored":
                continue
            for f in ("attribution", "licenseUrl", "modifications"):
                if s.get(f) is None or str(s.get(f)).strip() == "":
                    out.append(f"FAIL: attribution {sid} {f} is null or empty (release)")
    try:
        want = at.fallback_xml(attr)
        got = paths.fallback.read_text(encoding="utf-8") if paths.fallback.exists() else None
        if got != want:
            out.append("FAIL: attribution attribution_fallback.xml differs from the one made from attribution.json")
    except (KeyError, TypeError) as e:
        out.append(f"FAIL: attribution attribution.json malformed ({e!r})")
    bodies = [(n.get("id"), n.get("body", "")) for n in attr.get("notices", [])] + [("firstNotice", attr.get("firstNotice", {}).get("body", ""))]
    for nid, body in bodies:
        for m in at.UNFILLED.findall(body):
            out.append(f"FAIL: attribution {nid} unfilled placeholder {m}")
    for n in attr.get("notices", []):
        if set(n) != {"id", "title", "body"}:
            out.append(f"FAIL: attribution notice {n.get('id')} must have exactly id, title, body")
        if n.get("id") == "N-08":
            out.append("FAIL: attribution N-08 must be in dbLicense only, not notices")
    for k in at.KEYS7:
        if not (attr.get("meaningSourceTexts") or {}).get(k):
            out.append(f"FAIL: attribution meaningSourceTexts missing key {k}")
    ids = {n.get("id") for n in attr.get("notices", [])}
    c = facts["counts"]
    if (c.get("grammar") or c.get("question")) and "N-11" not in ids:
        out.append("FAIL: attribution N-11 missing while grammar or question rows exist")
    if c.get("listening") and "N-12" not in ids:
        out.append("FAIL: attribution N-12 missing while listening rows exist")
    return out


def check_app_assets(manifest):
    return [f"FAIL: app-asset {s['id']} ({s.get('name')}) is {s.get('status')}, not confirmed (release)"
            for s in manifest.values() if str(s.get("scope", "")).startswith("app-asset") and s.get("status") != "confirmed"]


def snap_file(paths, sid, info, manifest):
    v = safe_version(info.get("version"))
    cands = [paths.snapshot / f"{sid}-{v}.jsonl.gz", paths.snapshot / f"levels-{sid}-{v}.tsv"]
    return next((c for c in cands if c.exists()), cands[0])


def check_snapshot(paths, meta, manifest):
    out = []
    try:
        sources = json.loads(meta.get("sources", "{}"))
    except ValueError:
        return ["FAIL: snapshot content_meta.sources is not json"]
    for sid, info in sorted(sources.items()):
        f = snap_file(paths, sid, info, manifest)
        if not f.exists():
            out.append(f"FAIL: snapshot {f.name} missing")
        elif sha256_file(f) != info.get("snapshot"):
            out.append(f"FAIL: snapshot {sid} hash differs from content/snapshot/{f.name}")
    return out


def check_size(db, meta):
    lv_ = {x for x in meta.get("levels", "").split(",") if x}
    n = db.stat().st_size
    if n > 12 * MiB:
        return [f"FAIL: size content.db {n} bytes > 12 MiB"]
    if lv_ == {"5"} and n > 2 * MiB:
        return [f"FAIL: size content.db {n} bytes > 2 MiB (N5 only)"]
    return []


def check_count(paths, con, meta, manifest):
    out = []
    ls = meta.get("level_source")
    try:
        info = json.loads(meta["sources"])[ls]
        rows = lv.read_tsv(snap_file(paths, ls, info, manifest))
    except (KeyError, ValueError, OSError):
        return [f"FAIL: count cannot read the level list of {ls}"]
    for level in sorted({int(x) for x in meta.get("levels", "").split(",") if x}, reverse=True):
        total = sum(1 for r in rows if r.level == level)
        n = con.execute("SELECT COUNT(*) FROM vocab WHERE level = ?", (level,)).fetchone()[0]
        if total == 0 or n < 0.9 * total or n > total:
            out.append(f"FAIL: count N{level} vocab={n} list rows={total} (must be 90%..100%)")
    return out


def check_authored(paths, kind):
    out = []
    if kind == "sample":
        d = paths.authored / "sample"
        for fn, v in (("vocab.jsonl", au.vocab_problems), ("kanji.jsonl", au.kanji_problems)):
            if (d / fn).exists():
                out += v(read_jsonl(d / fn))
    else:
        for p in sorted((paths.authored / "vocab").glob("*.jsonl")):
            out += au.vocab_problems(read_jsonl(p))
        for p in sorted(paths.kogloss.glob("N*.jsonl")):
            for r in read_jsonl(p):
                out += au.meaning_problems(r, "ko-gloss")
    return out


def run_checks(paths):
    out = []
    db = paths.assets / "content.db"
    if not db.exists():
        return ["FAIL: asset app/src/main/assets/content/content.db missing"]
    cfg, manifest = load_config(paths), load_manifest(paths)
    out += check_header(db) + check_hash(paths, db) + check_forbidden(paths)
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    try:
        try:
            meta = dict(con.execute("SELECT key, value FROM content_meta"))
        except sqlite3.Error as e:
            return out + [f"FAIL: schema content.db cannot be read ({e})"]
        kind = meta.get("kind", cfg["kind"])
        release = cfg["kind"] == "release" or kind == "release"
        out += check_kind(cfg, manifest, meta) + check_schema(paths, con) + check_sources(con, manifest)
        out += check_ids(paths, con, kind) + check_refs(con) + check_meanings(con, manifest)
        out += check_attribution(paths, manifest, db, cfg, release) + check_size(db, meta) + check_authored(paths, kind)
        if release:
            out += check_app_assets(manifest) + check_count(paths, con, meta, manifest)
        if meta.get("sources", "{}") != "{}":
            out += check_snapshot(paths, meta, manifest)
    except sqlite3.Error as e:
        out.append(f"FAIL: schema content.db query failed ({e})")
    finally:
        con.close()
    return out


def main(argv):
    if argv[-1:] == ["--root"]:
        print("FAIL: usage --root needs a path")
        return 1
    root = Path(argv[argv.index("--root") + 1]) if "--root" in argv else None
    paths = Paths(root) if root else Paths()
    try:
        fails = run_checks(paths)
    except Fail as e:
        fails = str(e).splitlines()
    for line in fails:
        print(line)
    if fails:
        return 1
    print("OK: content checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
