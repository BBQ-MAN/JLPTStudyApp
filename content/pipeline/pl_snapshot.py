"""refresh-snapshot(fetch → parse → levels → snapshot)와 스냅샷 읽기(6.7)."""
import gzip
import io
import json
import posixpath

import levels as lv
from pl_common import CJK, Fail, jdump, read_json, safe_version, sha256_file, snapshot_name, write_text
from pl_fetch import fetch
from pl_parse import parse_jmdict, parse_kanjidic2
from pl_strokes import read_kanjivg


def _gz(path, text):
    buf = io.BytesIO()
    with gzip.GzipFile(filename="", mode="wb", fileobj=buf, compresslevel=9, mtime=0) as g:  # mtime=0: 결정적
        g.write(text.encode("utf-8"))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(buf.getvalue())


def _gz_lines(path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def _single(raw, src):
    files = src.get("files") or [{"path": posixpath.basename(src["downloadUrl"])}]
    return raw / src["id"] / files[0]["path"]


def snapshot_files(paths, manifest, level_source):
    return {
        "S-01": paths.snapshot / snapshot_name(manifest["S-01"], "jsonl.gz"),
        "S-02": paths.snapshot / snapshot_name(manifest["S-02"], "jsonl.gz"),
        "S-03": paths.snapshot / snapshot_name(manifest["S-03"], "jsonl.gz"),
        level_source: paths.snapshot / f"levels-{level_source}-{safe_version(manifest[level_source].get('version'))}.tsv",
    }


def refresh_snapshot(paths, config, manifest, refresh=(), opener=None, out=print):
    ls = config.get("levelSource")
    if not ls:
        raise Fail("FAIL: refresh-snapshot levelSource is null")
    if ls not in lv.READERS:
        raise Fail(f"FAIL: refresh-snapshot no level reader for {ls}")
    kw = {"opener": opener} if opener else {}
    used = {}
    for sid in ("S-01", "S-02", "S-03", ls):
        if sid not in manifest:
            raise Fail(f"FAIL: fetch {sid} not in manifest")
        used[sid] = fetch(manifest[sid], paths.raw, refresh=(sid in refresh), out=out, **kw)

    rows = lv.READERS[ls](paths.raw, manifest[ls])
    overrides = lv.read_overrides(paths.authored / "level-overrides.tsv", ls)
    seqs, kanas = lv.wanted(rows, overrides)
    kept = [e for e in parse_jmdict(_single(paths.raw, manifest["S-01"])) if e["seq"] in seqs or kanas.intersection(e["reb"])]
    vocab, unmatched, keep = lv.map_levels(rows, lv.Index(kept), lv.LEVEL_SOURCES[ls], overrides)
    entries = sorted((e for e in kept if e["seq"] in keep), key=lambda e: e["seq"])
    chars = {c for e in entries for k in e["keb"] for c in k if CJK.match(c)}
    kanji = sorted((k for k in parse_kanjidic2(_single(paths.raw, manifest["S-02"])) if k["literal"] in chars), key=lambda k: k["literal"])

    # S-03 스냅샷: S-02 스냅샷의 한자 중 KanjiVG에 있는 것(DB 한자는 그 부분집합). 없는 한자는 build가 strokes_missing.tsv에 남긴다.
    strokes = read_kanjivg(_single(paths.raw, manifest["S-03"]), [k["literal"] for k in kanji])
    files = snapshot_files(paths, manifest, ls)
    _gz(files["S-03"], "".join(jdump(r) + "\n" for r in strokes))
    _gz(files["S-01"], "".join(jdump(e) + "\n" for e in entries))
    _gz(files["S-02"], "".join(jdump(k) + "\n" for k in kanji))
    write_text(files[ls], lv.to_tsv(rows))
    write_text(paths.snapshot / "sources.json", jdump({s: {"version": manifest[s].get("version"), "files": used[s]} for s in sorted(used)}) + "\n")
    write_text(paths.snapshot / "LICENSE.md", snapshot_license(manifest, sorted(used)))
    report_unmatched(paths, config, unmatched)
    out(f"snapshot: {len(entries)} entries, {len(kanji)} kanji, {len(strokes)} stroke sets, {len(rows)} level rows, {len(unmatched)} unmatched")
    return files


def report_unmatched(paths, config, unmatched):
    p = paths.report("unmatched.tsv", config["milestone"])
    write_text(p, "row_no\tlevel\tkanji\tkana\tcandidates\n" + "".join("\t".join(map(str, u)) + "\n" for u in unmatched))


def snapshot_license(manifest, ids):
    lines = ["# content/snapshot 라이선스", "",
             "이 폴더의 파일은 아래 자료에서 일부 필드만 뽑은 것이다(원 자료에서 일부 필드만 뽑음). 각 자료의 조건을 따른다.", ""]
    for i in ids:
        s = manifest[i]
        lines += [f"## {s['id']} {s['name']} ({s.get('version')})", "",
                  f"- 저작자 표시: {s.get('attribution')}", f"- 라이선스: {s.get('license')} <{s.get('licenseUrl')}>",
                  f"- 변경 내용: {s.get('modifications')}"]
        lines += [f"- 추가 크레딧: {c['text']}" + (f" <{c['url']}>" if c.get("url") else "") for c in s.get("extraCredits") or []]
        lines.append("")
    return "\n".join(lines)


def read_snapshot(paths, manifest, level_source):
    files = snapshot_files(paths, manifest, level_source)
    for p in files.values():
        if not p.exists():
            raise Fail(f"FAIL: snapshot {p.name} missing (run build.py refresh-snapshot)")
    return {
        "entries": _gz_lines(files["S-01"]),
        "kanji": {k["literal"]: k for k in _gz_lines(files["S-02"])},
        "strokes": {r["literal"]: r["paths"] for r in _gz_lines(files["S-03"])},
        "rows": lv.read_tsv(files[level_source]),
        "sources": read_json(paths.snapshot / "sources.json"),
        "hashes": {s: sha256_file(p) for s, p in files.items()},
        "files": files,
    }
