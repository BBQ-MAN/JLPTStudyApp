"""attribution.json·attribution_fallback.xml·dist 묶음 만들기(6.6, 4.6). 문구는 content/notices.ko.json에서만 온다."""
import json
import re
import sqlite3
import zipfile

from pl_common import Fail, read_json, write_text

KEYS7 = ["translated-llm", "translated-llm-nomodel", "translated-human", "authored-llm", "authored-llm-nomodel", "authored-human", "unknown"]
UNFILLED = re.compile(r"\{[^{}\s]*\}")


def fill(text, mapping):
    for k, v in mapping.items():
        text = text.replace("{" + k + "}", v)
    return text


def model_of(method):
    """ko_method 'llm:<모델>@<날짜>' → 모델(없으면 None). llm이 아니면 None."""
    if not isinstance(method, str) or not method.startswith("llm:"):
        return None
    rest = method[4:]
    return (rest.rsplit("@", 1)[0] if "@" in rest else rest).strip()


def db_facts(db_path):
    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        meta = dict(con.execute("SELECT key, value FROM content_meta"))
        tables = [r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        used, pairs, counts = set(), [], {}
        for t in tables:
            cols = [c[1] for c in con.execute(f"PRAGMA table_info(`{t}`)")]
            for col in ("source_id", "level_source_id"):
                if col in cols:
                    used |= {r[0] for r in con.execute(f"SELECT DISTINCT `{col}` FROM `{t}`") if r[0]}
            if "ko_source" in cols and "ko_method" in cols:
                pairs += con.execute(f"SELECT DISTINCT ko_source, ko_method FROM `{t}`").fetchall()
            counts[t] = con.execute(f"SELECT COUNT(*) FROM `{t}`").fetchone()[0]
        return {"meta": meta, "used": used, "pairs": pairs, "counts": counts}
    finally:
        con.close()


def meaning_sentences(facts, tpl, model_names):
    n10 = tpl["notices"]["N-10"]
    srcs = {s for s, _ in facts["pairs"]}
    translated = "jmdict-translated" in srcs
    authored = bool(srcs - {"jmdict-translated"})
    parts = []
    if translated:
        parts.append(n10["translated"])
        trans_models = sorted({m for s, meth in facts["pairs"] if s == "jmdict-translated" for m in [model_of(meth)] if m})
        if trans_models:
            parts.append(fill(n10["translatedModels"], {"modelNames": ", ".join(trans_models)}))
    if authored:
        parts.append(n10["authoredWithTranslated" if translated else "authoredOnly"])
        am = sorted({m for s, meth in facts["pairs"] if s != "jmdict-translated" for m in [model_of(meth)] if m})
        if am:
            parts.append(fill(n10["authoredModels"], {"modelNames": ", ".join(am)}))
    return " ".join(parts)


def build_attribution(manifest, facts, tpl):
    meta = facts["meta"]
    kind = meta["kind"]
    ls = meta.get("level_source", "authored")
    models = sorted({m for _, meth in facts["pairs"] for m in [model_of(meth)] if m})
    db_license = tpl["dbLicense"]
    sources = []
    for sid in sorted(i for i in facts["used"] if i != "authored"):
        if sid not in manifest:
            raise Fail(f"FAIL: attribution {sid} not in manifest")
        s = manifest[sid]
        sources.append({"id": sid, "name": s["name"], "version": s.get("version"), "attribution": s.get("attribution"),
                        "license": s.get("license"), "licenseUrl": s.get("licenseUrl"), "homepage": s.get("homepage"),
                        "modifications": s.get("modifications"), "links": s.get("links", []), "extraCredits": s.get("extraCredits", [])})
    if "authored" in facts["used"]:
        a = tpl["authored"]
        sources.append({"id": "authored", "name": a["name"], "version": meta["content_version"], "attribution": a["attribution"],
                        "license": db_license["name"], "licenseUrl": db_license["url"], "homepage": None,
                        "modifications": a["modifications"], "links": [], "extraCredits": []})
    list_name = manifest[ls]["name"] if ls in manifest else (tpl["sampleLevelListName"] if ls == "authored" else ls)
    review = tpl["reviewPolicy"]
    sentences = meaning_sentences(facts, tpl, models)
    n = tpl["notices"]
    n09 = fill(n["N-09"]["body"], {"levelListName": list_name})
    if facts["counts"].get("grammar"):  # 6.6: 문법 급수 문장은 grammar 행이 있을 때만 본문 앞에
        if not n["N-09"].get("grammarPrefix"):
            raise Fail("FAIL: attribution notices.N-09.grammarPrefix missing while grammar rows exist")
        n09 = n["N-09"]["grammarPrefix"] + " " + n09
    notices = [
        {"id": "N-09", "title": n["N-09"]["title"], "body": n09},
        {"id": "N-10", "title": n["N-10"]["title"], "body": sentences + " " + fill(n["N-10"]["tail"], {"reviewPolicy": review})},
    ]
    if facts["counts"].get("grammar") or facts["counts"].get("question"):
        notices.append({"id": "N-11", **n["N-11"]})
    if facts["counts"].get("listening"):
        notices.append({"id": "N-12", **n["N-12"]})
    fn = tpl["firstNotice"][kind]
    return {
        "contentVersion": meta["content_version"], "kind": kind, "dbLicense": db_license, "levelListName": list_name,
        "modelNames": models, "reviewPolicy": review, "sources": sources, "notices": notices,
        "firstNotice": {"title": fn["title"], "body": fill(fn["body"], {"meaningSentences": sentences})},
        "meaningSourceTexts": {k: tpl["meaningSourceTexts"][k] for k in KEYS7},
    }


def fallback_text(attr):
    seen, lines = set(), []
    for s in attr["sources"]:
        line = f"{s['attribution']} {s['licenseUrl']}"
        if line not in seen:
            seen.add(line)
            lines.append(line)
    lines.append(f"콘텐츠 DB: {attr['dbLicense']['name']} {attr['dbLicense']['url']}")
    return "\n".join(lines)


def fallback_xml(attr):
    t = fallback_text(attr).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    t = t.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if t[:1] in "@?":
        t = "\\" + t
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- 생성 파일: content/pipeline/build.py 가 attribution.json과 같은 입력으로 만든다. 손으로 고치지 않는다. -->\n"
            f'<resources>\n    <string name="attribution_fallback" formatted="false">{t}</string>\n</resources>\n')


def license_md(attr):
    lines = ["# 학습 콘텐츠 DB 라이선스", "", f"콘텐츠 DB: {attr['dbLicense']['name']} <{attr['dbLicense']['url']}>", "",
             "## 원 자료", ""]
    for s in attr["sources"]:
        lines += [f"### {s['id']} {s['name']} ({s.get('version')})", "", f"- 저작자 표시: {s['attribution']}",
                  f"- 라이선스: {s['license']} <{s['licenseUrl']}>", f"- 변경 내용: {s['modifications']}"]
        lines += [f"- 추가 크레딧: {c['text']}" + (f" <{c['url']}>" if c.get("url") else "") for c in s.get("extraCredits") or []]
        lines.append("")
    return "\n".join(lines)


def write_attribution(paths, manifest, db_path, out=print):
    attr = build_attribution(manifest, db_facts(db_path), read_json(paths.notices))
    write_text(paths.assets / "attribution.json", json.dumps(attr, ensure_ascii=False, indent=2) + "\n")
    write_text(paths.fallback, fallback_xml(attr))
    return attr


def write_dist(paths, attr, db_path):
    """content/dist/jlpt-content-<version>.zip — 항목 순서·시각 고정."""
    paths.dist.mkdir(parents=True, exist_ok=True)
    z = paths.dist / f"jlpt-content-{attr['contentVersion']}.zip"
    items = [("LICENSE.md", license_md(attr).encode("utf-8")), ("attribution.json", (paths.assets / "attribution.json").read_bytes()),
             ("content.db", db_path.read_bytes())]
    with zipfile.ZipFile(z, "w", zipfile.ZIP_DEFLATED) as f:
        for name, data in sorted(items):
            zi = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            f.writestr(zi, data)
    return z
