"""문법 작성 파일(content/authored/grammar/N<급>/*.json, 9.1) 형식 검사와 DB 행 만들기."""
import json
import re

import pl_authored as au
from pl_common import Fail, jdump

ID = re.compile(r"^gr-n([1-5])-\d{4}$")
STATUS = {"draft", "reviewed", "retired"}
RUBY = re.compile(r"\{([^{}|]*)\|([^{}|]*)\}")
EMPH = re.compile(r"\[\[|\]\]")


def ja_problems(rid, label, text, need_emph=False, ruby_ok=True):
    """일본어 필드: 루비 {기본|읽기} 괄호 짝, [[…]] 짝·중첩 없음."""
    bad = lambda why: f"FAIL: authored {rid} {label} {why}"
    if not isinstance(text, str) or not text.strip():
        return [bad("missing")]
    out = []
    rest = text
    for m in RUBY.finditer(text):
        if not m.group(1) or not m.group(2):
            out.append(bad("ruby empty part"))
        if EMPH.search(m.group(0)):
            out.append(bad("[[ ]] inside ruby"))
    rest = RUBY.sub("", text)
    if not ruby_ok and rest != text:
        out.append(bad("ruby not allowed"))
    if any(c in rest for c in "{|}"):
        out.append(bad("ruby bracket mismatch"))
    depth, n = 0, 0
    for m in EMPH.finditer(text):
        depth += 1 if m.group() == "[[" else -1
        n += m.group() == "[["
        if depth not in (0, 1):
            out.append(bad("[[ ]] mismatch"))
            break
    else:
        if depth != 0:
            out.append(bad("[[ ]] mismatch"))
        elif need_emph and n < 1:
            out.append(bad("[[ ]] missing"))
    return out


def ko_problems(rid, label, text):
    if not isinstance(text, str) or not text.strip():
        return [f"FAIL: authored {rid} {label} missing"]
    return au.ko_text_problems(rid, text)


def source_problems(rid, label, sid, manifest):
    if sid == "authored":
        return []
    if sid not in manifest:
        return [f"FAIL: authored {rid} {label} {sid} not in manifest"]
    if manifest[sid].get("status") != "confirmed":
        return [f"FAIL: authored {rid} {label} {sid} not confirmed"]
    return []


def example_problems(rid, i, ex, manifest):
    out = []
    if not isinstance(ex, dict):
        return [f"FAIL: authored {rid} examples[{i}] not an object"]
    out += ja_problems(rid, f"examples[{i}].ja", ex.get("ja"), need_emph=True)
    out += ko_problems(rid, f"examples[{i}].ko", ex.get("ko"))
    sid = ex.get("source", "authored")
    out += source_problems(rid, f"examples[{i}].source", sid, manifest)
    ext = [ex.get(k) for k in ("ref", "author", "license")]
    if sid == "authored" and any(v is not None for v in ext):
        out.append(f"FAIL: authored {rid} examples[{i}] authored example has ref/author/license")
    elif sid != "authored" and not all(isinstance(v, str) and v.strip() for v in ext):
        out.append(f"FAIL: authored {rid} examples[{i}] {sid} example needs ref, author, license")
    return out


def common_problems(rid, r, manifest):
    """needsReview·sources·출처 필드 검사. 문법·문항 공용."""
    out = []
    bad = lambda why: f"FAIL: authored {rid} {why}"
    if not isinstance(r.get("needsReview"), bool):
        out.append(bad("needsReview must be boolean"))
    srcs = r.get("sources")
    if not isinstance(srcs, list):
        out.append(bad("sources must be a list"))
    else:
        for s in srcs:
            out += source_problems(rid, "sources", s.get("source") if isinstance(s, dict) else None, manifest)
    st = r.get("reviewStatus")
    if not isinstance(r.get("method"), str) or not au.METHOD.match(r["method"]):
        out.append(bad(f"method invalid ({r.get('method')!r})"))
    if not isinstance(st, str) or st not in au.REVIEW:
        out.append(bad(f"reviewStatus invalid ({st!r})"))
    elif st != "unreviewed" and not (isinstance(r.get("reviewedAt"), str) and au.DATE.match(r["reviewedAt"])):
        out.append(bad("reviewedAt missing"))
    if r.get("reviewerKind") not in (None, "agent", "human"):
        out.append(bad("reviewerKind invalid"))
    elif st in ("sampled", "reviewed") and r.get("reviewerKind") is None:
        out.append(bad("reviewerKind missing"))
    return out


def grammar_problems(recs, manifest):
    out, seen = [], set()
    ids = {r.get("id") for r in recs if r.get("status") != "retired"}
    for r in recs:
        rid = r.get("id", "?")
        bad = lambda why: f"FAIL: authored {rid} {why}"
        m = ID.match(rid) if isinstance(rid, str) else None
        if not m:
            out.append(bad("id format"))
        if rid in seen:
            out.append(bad("duplicate id"))
        seen.add(rid)
        if r.get("level") not in ("N1", "N2", "N3", "N4", "N5"):
            out.append(bad("level invalid"))
        elif m and r["level"] != "N" + m.group(1):
            out.append(bad("level differs from id"))
        if not isinstance(r.get("status"), str) or r["status"] not in STATUS:
            out.append(bad(f"status invalid ({r.get('status')!r})"))
        out += common_problems(rid, r, manifest)
        out += ja_problems(rid, "title", r.get("title"), ruby_ok=False)
        out += ko_problems(rid, "meaningKo", r.get("meaningKo"))
        out += ko_problems(rid, "explanationKo", r.get("explanationKo"))
        cons = r.get("connections")
        if not isinstance(cons, list) or not cons:
            out.append(bad("connections empty"))
        else:
            for i, c in enumerate(cons):
                if not isinstance(c, dict) or not all(isinstance(c.get(k), str) and c[k].strip() for k in ("posKo", "form", "example")):
                    out.append(bad(f"connections[{i}] needs posKo, form, example"))
                    continue
                out += ko_problems(rid, f"connections[{i}].posKo", c["posKo"])
                out += ja_problems(rid, f"connections[{i}].form", c["form"]) + ja_problems(rid, f"connections[{i}].example", c["example"])
        exs = r.get("examples")
        if not isinstance(exs, list) or len(exs) < 3:
            out.append(bad("examples need at least 3"))
        for i, ex in enumerate(exs if isinstance(exs, list) else []):
            out += example_problems(rid, i, ex, manifest)
        cts = r.get("contrasts", [])
        if not isinstance(cts, list):
            out.append(bad("contrasts must be a list"))
            cts = []
        for i, c in enumerate(cts):
            gid = c.get("grammarId") if isinstance(c, dict) else None
            if gid == rid:
                out.append(bad(f"contrasts[{i}] grammarId is itself"))
            elif not isinstance(gid, str) or gid not in ids:
                out.append(bad(f"contrasts[{i}] grammarId {gid} not in build"))
            pair = c.get("pair") if isinstance(c, dict) else None
            if not isinstance(pair, list) or len(pair) != 2 or not all(isinstance(x, dict) for x in pair):
                out.append(bad(f"contrasts[{i}] pair must have exactly 2 items"))
                continue
            for j, x in enumerate(pair):
                out += ja_problems(rid, f"contrasts[{i}].pair[{j}].ja", x.get("ja")) + ko_problems(rid, f"contrasts[{i}].pair[{j}].ko", x.get("ko"))
    return out


def files_for(paths, kind):
    if kind == "sample":
        return sorted((paths.authored / "sample" / "grammar").glob("*.json"))
    return sorted((paths.authored / "grammar").glob("N[1-5]/*.json"))


def read_all(files):
    recs = []
    for f in files:
        try:
            r = json.loads(f.read_text(encoding="utf-8"))
        except ValueError as e:
            raise Fail(f"FAIL: authored {f.name} invalid json ({e})")
        if not isinstance(r, dict):
            raise Fail(f"FAIL: authored {f.name} not an object")
        if r.get("id") != f.stem:
            raise Fail(f"FAIL: authored {r.get('id')} id differs from file name {f.name}")
        recs.append(r)
    return recs


def load_grammar(paths, kind, manifest, levels=None):
    """→ (레코드들, 읽은 파일들). 형식 문제가 있으면 Fail. levels가 있으면 그 밖 급수 파일은 어휘처럼 뺀다."""
    files = files_for(paths, kind)
    recs = read_all(files)
    if levels is not None:
        keep = [(f, r) for f, r in zip(files, recs) if isinstance(r.get("level"), str) and r["level"][1:].isdigit() and int(r["level"][1:]) in levels or not isinstance(r.get("level"), str)]
        files, recs = [f for f, _ in keep], [r for _, r in keep]
    problems = grammar_problems(recs, manifest)
    if problems:
        raise Fail("\n".join(problems))
    return recs, files


def grammar_rows(recs, vocab_by_level):
    """retired 제외. → (grammar 행, grammar_example 행, 한국어 필드 텍스트 [(id, [텍스트])]). sort_key는 9.1."""
    live = sorted((r for r in recs if r["status"] != "retired"), key=lambda r: r["id"])
    by = {}
    for r in live:
        by.setdefault(int(r["level"][1]), []).append(r)
    grows, erows, ko = [], [], []
    for lvl, lst in by.items():
        v, g = vocab_by_level.get(lvl, 0), len(lst)
        for i, r in enumerate(lst):
            grows.append({"id": r["id"], "level": lvl, "sort_key": i * v // g if v else i, "title": r["title"],
                          "meaning_ko": r["meaningKo"], "explanation_ko": r["explanationKo"],
                          "connections_json": jdump(r["connections"]), "contrasts_json": jdump(r.get("contrasts", [])),
                          "source_id": "authored", "ko_source": "authored", "ko_method": r["method"],
                          "ko_review_status": r["reviewStatus"], "ko_reviewed_at": r.get("reviewedAt"),
                          "ko_reviewer_kind": r.get("reviewerKind"), "needs_review": int(r["needsReview"])})
            for o, ex in enumerate(r["examples"]):
                erows.append({"grammar_id": r["id"], "ord": o, "ja": ex["ja"], "ko": ex["ko"], "source_id": ex.get("source", "authored"),
                              "source_ref": ex.get("ref"), "author": ex.get("author"), "license": ex.get("license")})
            ko.append((r["id"], [r["meaningKo"], r["explanationKo"]] + [c["posKo"] for c in r["connections"]]
                       + [e["ko"] for e in r["examples"]] + [p["ko"] for c in r.get("contrasts", []) for p in c["pair"]]))
    return grows, erows, ko
