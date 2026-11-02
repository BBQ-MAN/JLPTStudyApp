"""authored·ko-gloss 단계: 직접 작성 어휘·한자와 한국어 뜻 파일 형식 검사, 한국어 필드 안 일본어 구간 보고(4.4, 6.4)."""
import re

from pl_common import JP_SPAN, Fail, read_jsonl, write_text

METHOD = re.compile(r"^(human|llm:[^@]*@\d{4}-\d{2}-\d{2})$")
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
REVIEW = {"unreviewed", "sampled", "reviewed"}
VOCAB_ID = re.compile(r"^av-(sample|n[1-5])-\d{4}$")


# JMdict DTD 품사 엔티티 이름 중 앱이 쓰는 것(PosLabels.kt와 같은 집합 + 동사 활용형).
JMDICT_POS = {
    "n", "pn", "num", "ctr", "pref", "suf", "adj-i", "adj-ix", "adj-na", "adj-no", "adj-pre-noun", "adj-f", "adj-t", "adv", "adv-to",
    "prt", "conj", "int", "exp", "aux", "aux-v", "cop", "vs", "vs-i", "vs-s", "vk", "vt", "vi", "v1", "v1-s", "vz", "n-adv", "n-t",
    "n-suf", "n-pr", "n-pref", "unc",
} | {"v5" + x for x in ("", "u", "k", "g", "s", "t", "n", "b", "m", "r", "aru", "k-s", "r-i", "u-s")}

def ko_text_problems(rid, text):
    """한국어 필드에 루비 {…|…}·강조 [[…]] 표기가 있으면 실패(4.4)."""
    out = []
    if any(c in text for c in "{|}"):
        out.append(f"FAIL: authored {rid} ruby in ko field")
    if "[[" in text or "]]" in text:
        out.append(f"FAIL: authored {rid} emphasis in ko field")
    return out


def meaning_problems(rec, check):
    """ko-gloss 줄·authored 어휘 줄이 공유하는 한국어 뜻·출처 필드 검사."""
    rid = rec.get("vocabId") or rec.get("id") or "?"
    bad = lambda why: f"FAIL: {check} {rid} {why}"
    out = []
    ms = rec.get("meanings")
    if not isinstance(ms, list) or not ms or not all(isinstance(m, str) and m.strip() for m in ms):
        return [bad("meanings empty")]
    for m in ms:
        out += ko_text_problems(rid, m)
    ks = rec.get("koSource")
    if not (ks in ("authored", "jmdict-translated") or (isinstance(ks, str) and re.match(r"^S-\d+$", ks))):
        out.append(bad(f"koSource invalid ({ks!r})"))
    if not isinstance(rec.get("method"), str) or not METHOD.match(rec["method"]):
        out.append(bad(f"method invalid ({rec.get('method')!r})"))
    st = rec.get("reviewStatus")
    if st not in REVIEW:
        out.append(bad(f"reviewStatus invalid ({st!r})"))
    elif st != "unreviewed" and not (isinstance(rec.get("reviewedAt"), str) and DATE.match(rec["reviewedAt"])):
        out.append(bad("reviewedAt missing"))
    if rec.get("reviewerKind") not in (None, "agent", "human"):
        out.append(bad("reviewerKind invalid"))
    elif st in ("sampled", "reviewed") and rec.get("reviewerKind") is None:  # 검수자 종류 없는 검수 표시 금지(D-03)
        out.append(bad("reviewerKind missing"))
    return out


def vocab_problems(recs):
    out, seen = [], set()
    for r in recs:
        rid = r.get("id", "?")
        if not isinstance(rid, str) or not VOCAB_ID.match(rid):
            out.append(f"FAIL: authored {rid} id format")
        if rid in seen:
            out.append(f"FAIL: authored {rid} duplicate id")
        seen.add(rid)
        if not r.get("reading"):
            out.append(f"FAIL: authored {rid} reading missing")
        if r.get("written") is not None and not isinstance(r["written"], str):
            out.append(f"FAIL: authored {rid} written invalid")
        if r.get("level") not in (1, 2, 3, 4, 5):
            out.append(f"FAIL: authored {rid} level invalid")
        if not r.get("pos"):
            out.append(f"FAIL: authored {rid} pos missing")
        elif r["pos"] not in JMDICT_POS:
            out.append(f"FAIL: authored {rid} pos {r['pos']} is not a JMdict part-of-speech code")
        out += meaning_problems(r, "authored")
    return out


def kanji_problems(recs):
    out, seen = [], set()
    for r in recs:
        lit = r.get("literal", "?")
        if not isinstance(lit, str) or len(lit) != 1:
            out.append(f"FAIL: authored {lit} kanji literal must be one char")
        if lit in seen:
            out.append(f"FAIL: authored {lit} duplicate kanji")
        seen.add(lit)
        if not isinstance(r.get("strokeCount"), int) or r["strokeCount"] < 1:
            out.append(f"FAIL: authored {lit} strokeCount invalid")
        if r.get("freq") is not None and not isinstance(r["freq"], int):
            out.append(f"FAIL: authored {lit} freq invalid")
        for k in ("on", "kun", "ko"):
            if not isinstance(r.get(k), list) or not all(isinstance(x, str) for x in r[k]):
                out.append(f"FAIL: authored {lit} {k} must be a string list")
    return out


def load_authored(directory, pattern, validate):
    """→ (레코드들, 읽은 파일들). 형식 문제가 있으면 Fail."""
    files = sorted(directory.glob(pattern))
    recs = [r for p in files for r in read_jsonl(p)]
    problems = validate(recs)
    if problems:
        raise Fail("\n".join(problems))
    return recs, files


def jp_span_rows(label, rid, texts):
    """한국어 필드 안 일본어 구간 → [(파일/단계, id, 구간)]"""
    return [(label, rid, span) for t in texts for span in JP_SPAN.findall(t)]


def report_spans(paths, config, name, rows, out=print):
    write_text(paths.report(f"ko_spans_{name}.tsv", config["milestone"]),
               "source\tid\tspan\n" + "".join("\t".join(r) + "\n" for r in sorted(set(rows))))
    out(f"{name}: {len(set(rows))} Japanese spans in ko fields listed in ko_spans_{name}.tsv (review only)")
