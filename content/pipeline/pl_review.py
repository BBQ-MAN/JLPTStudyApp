"""검수 도구(architecture 6.8, T07): review-sample · review-sheet · review-apply, authored 유사·중복 검사."""
import csv
import difflib
import html
import json
import math
import random
import re
from itertools import combinations
from pathlib import Path

from pl_common import Fail, read_jsonl, write_text

COLS = ["id", "ja", "ko", "source", "reviewStatus", "verdict", "note", "reviewer"]
KINDS = ("grammar", "questions", "listening")
RUBY = re.compile(r"\{([^|{}]*)\|[^{}]*\}")


def review_dir(p):
    return p.reports / "review"


def _split(target):
    kind, _, lvl = target.partition("/")
    if kind not in ("ko-gloss",) + KINDS or not re.fullmatch(r"N[1-5]", lvl):
        raise Fail(f"FAIL: review usage bad --target {target}")
    return kind, lvl


def _load_json(f):
    try:
        r = json.loads(f.read_text(encoding="utf-8"))
        r["id"]
    except (ValueError, KeyError, TypeError):
        raise Fail(f"FAIL: review {f.name} invalid json or missing id")
    return r


def load_rows(p, target):
    """→ [(id, 행 dict, 파일)] ID 순. ko-gloss는 jsonl 한 줄, 그 밖은 json 파일 하나가 행."""
    kind, lvl = _split(target)
    if kind == "ko-gloss":
        f = p.kogloss / f"{lvl}.jsonl"
        if not f.exists():
            raise Fail(f"FAIL: review {f.name} missing")
        out = [(r["vocabId"], r, f) for r in read_jsonl(f)]
    else:
        out, seen = [], set()
        for f in sorted((p.authored / kind / lvl).glob("*.json")):
            r = _load_json(f)
            if r["id"] in seen:
                raise Fail(f"FAIL: review duplicate id {r['id']}")
            seen.add(r["id"])
            out.append((r["id"], r, f))
    if not out:
        raise Fail(f"FAIL: review {target} has no rows")
    return sorted(out, key=lambda t: t[0])


def sample(p, target, ratio, seed):
    if not 0 < ratio <= 1:
        raise Fail("FAIL: review usage --ratio must be 0 < R <= 1")
    ids = [i for i, _, _ in load_rows(p, target)]
    k = math.ceil(round(len(ids) * ratio, 9))
    f = review_dir(p) / f"{target.replace('/', '_')}-{seed}.ids"
    write_text(f, "".join(i + "\n" for i in random.Random(seed).sample(ids, k)))
    return f


def ja_map(p, lvl):
    """ko-gloss ja 열용 {vocabId: 'written reading'}. export-gloss-input과 같은 대표 표기·읽기 해석.
    shortcut: levelSource나 스냅샷이 없으면(합성 픽스처) 빈 맵 → vocabId로 대체, 실제 산출물에서는 항상 있다."""
    import pl_db
    from pl_common import load_config, load_manifest
    try:
        c, m = load_config(p), load_manifest(p)
        snap, idx, vocab, _, _ = pl_db.mapped(p, c, m)
    except (Fail, KeyError, TypeError):
        return {}
    rows = {(r.level, r.row_no): r for r in snap["rows"]}
    out = {}
    for vid, (lv, no) in vocab.items():
        if lv == int(lvl[1]) and vid.startswith("jm:"):
            w, r = pl_db.representative(idx.by_seq[int(vid[3:])], rows[(lv, no)], snap["reading_ov"].get(vid), snap["written_ov"].get(vid))
            out[vid] = " ".join(filter(None, [w, r]))
    return out


def _view(kind, r, ja=None):
    """→ (ja, ko 조각들, source)"""
    if kind == "ko-gloss":
        return ((ja or {}).get(r["vocabId"]) or r["vocabId"], r["meanings"],
                f"{r.get('koSource', '')} {r.get('method', '')}")
    src = f"authored {r.get('method', '')} {' '.join(map(str, r.get('sources') or []))}"
    ko = [r.get("explanationKo", "")]
    if kind == "grammar":
        # 검수 표시(P)는 행의 한국어 필드 전부에 걸리므로 시트에도 전부 보인다(architecture 6.8)
        ko = ([r.get("meaningKo", ""), r.get("explanationKo", "")]
              + [c.get("posKo", "") for c in r.get("connections") or []]
              + [e.get("ko", "") for e in r.get("examples") or []]
              + [x.get("ko", "") for c in r.get("contrasts") or [] for x in c.get("pair") or []])
        return r.get("title", ""), ko, src
    ko += [w for w in r.get("whyWrongKo", []) if w]
    if kind == "questions":
        return r.get("stem", ""), ko, src
    return " ".join(l["text"] for l in r.get("lines", [])), ko, src


def _cell(s):
    return re.sub(r"\s+", " ", str(s)).strip()


def sheet(p, target, ids_file):
    kind, lvl = _split(target)
    names = ja_map(p, lvl) if kind == "ko-gloss" else None
    want = [l for l in Path(ids_file).read_text(encoding="utf-8").splitlines() if l]
    by = {i: r for i, r, _ in load_rows(p, target)}
    missing = [i for i in want if i not in by]
    if missing:
        raise Fail(f"FAIL: review ids not in {target}: {missing[0]}")
    table = []
    for i in want:
        ja, ko, src = _view(kind, by[i], names)
        table.append([i, ja, " / ".join(k for k in ko if k), src, by[i].get("reviewStatus", ""), "", "", ""])
    table = [[_cell(c) for c in row] for row in table]
    base = Path(ids_file).with_suffix("")
    write_text(base.with_suffix(".tsv"), "".join("\t".join(r) + "\n" for r in [COLS] + table))
    lang = {"ja": "ja", "ko": "ko"}
    cells = lambda r: "".join(f'<td lang="{lang[c]}">{html.escape(v)}</td>' if c in lang else f"<td>{html.escape(v)}</td>"
                              for c, v in zip(COLS, r))
    write_text(base.with_suffix(".html"),
               '<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>검수 시트</title>'
               "<style>body{font:15px sans-serif}table{border-collapse:collapse}td,th{border:1px solid #888;padding:4px 8px}"
               "td[lang=ja]{font-family:'Hiragino Sans','Noto Sans CJK JP',sans-serif}</style></head><body>"
               f"<table><thead><tr>{''.join(f'<th>{c}</th>' for c in COLS)}</tr></thead><tbody>"
               + "".join(f"<tr>{cells(r)}</tr>" for r in table) + "</tbody></table></body></html>\n")
    return base.with_suffix(".tsv"), base.with_suffix(".html")


def _dump(r, indented):
    if indented:
        return json.dumps(r, ensure_ascii=False, indent=2)
    return json.dumps(r, ensure_ascii=False, separators=(",", ":"))


def apply(p, target, sheet_file, date, batch_sampled=False):
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date or ""):
        raise Fail("FAIL: review usage --date YYYY-MM-DD")
    sheet_file = Path(sheet_file)
    with open(sheet_file, encoding="utf-8", newline="") as fh:
        recs = list(csv.DictReader(fh, delimiter="\t", quoting=csv.QUOTE_NONE))
    if not recs or list(recs[0].keys()) != COLS:
        raise Fail("FAIL: review sheet header must be " + ",".join(COLS))
    rows = {i: (r, f) for i, r, f in load_rows(p, target)}
    todo, fixes = {}, []
    for rec in recs:
        if rec["id"] not in rows:
            raise Fail(f"FAIL: review sheet id {rec['id']} not in {target}")
        if None in rec or None in rec.values():
            raise Fail(f"FAIL: review sheet {rec['id']} bad column count")
        v, who = rec["verdict"].strip(), rec["reviewer"].strip()
        if not v or not who:  # reviewer가 비면 human을 추정하지 않고 둔다
            continue
        if v not in ("ok", "fix") or who not in ("human", "agent"):
            raise Fail(f"FAIL: review sheet {rec['id']} bad verdict/reviewer")
        todo[rec["id"]] = who
        if v == "fix":
            fixes.append(rec)
    if batch_sampled:
        kinds = set(todo.values())
        if len(kinds) != 1:
            raise Fail("FAIL: review --batch-sampled needs exactly one reviewer kind in the sheet")
        sampled_kind, sheet_ids = kinds.pop(), {rec["id"] for rec in recs}
        for i, (r, _) in rows.items():
            if i not in sheet_ids and r.get("reviewStatus") == "unreviewed":
                todo[i] = ("sampled", sampled_kind)
    for i, who in todo.items():
        status, kind = who if isinstance(who, tuple) else ("reviewed", who)
        r = rows[i][0]
        r["reviewStatus"], r["reviewedAt"], r["reviewerKind"] = status, date, kind
    files = {f for i in todo for f in [rows[i][1]]}
    for f in files:
        if f.suffix == ".jsonl":
            # 원래 행 순서(ID 정렬이 아님)를 지키려고 원본 줄 순서로 다시 쓴다
            order = [x["vocabId"] for x in read_jsonl(f)]
            text = "".join(_dump(rows[i][0], False) + "\n" for i in order)
        else:
            old = f.read_text(encoding="utf-8")
            r = next(r for r, ff in rows.values() if ff == f)
            text = _dump(r, "\n " in old) + ("\n" if old.endswith("\n") else "")
        write_text(f, text)
    if fixes:
        write_text(sheet_file.with_name(sheet_file.stem + "-fix.tsv"),
                   "".join("\t".join(r[c] for c in COLS) + "\n" for r in [dict(zip(COLS, COLS))] + fixes))
    return len(todo), len(fixes)


def strip_ja(s):
    return re.sub(r"\s+", "", RUBY.sub(r"\1", s).replace("[[", "").replace("]]", ""))


def _body(kind, r):
    if kind == "grammar":
        return "".join(e.get("ja") or "" for e in r.get("examples") or [])
    if kind == "questions":
        return (r.get("stem") or "") + (r.get("passage") or "")
    return "".join(l.get("text") or "" for l in r.get("lines") or [])


def similarity_lines(p):
    """authored 유사·중복 검사 → 'WARN: similar …' / 'FAIL: authored duplicate …' 줄."""
    out = []
    for kind in KINDS:
        items = []
        for f in sorted((p.authored / kind).glob("N[1-5]/*.json")):
            r = _load_json(f)
            t = strip_ja(_body(kind, r))
            if t:
                items.append((r["id"], t))
        for (a, ta), (b, tb) in combinations(sorted(items), 2):
            m = difflib.SequenceMatcher(None, ta, tb, autojunk=False)
            if m.real_quick_ratio() < 0.9 or m.quick_ratio() < 0.9:
                continue
            ratio = m.ratio()
            if ratio == 1.0:
                out.append(f"FAIL: authored duplicate {a} {b}")
            elif ratio >= 0.9:
                out.append(f"WARN: similar {a} {b} {ratio:.3f}")
    return out
