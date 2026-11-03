"""문항(content/authored/questions/N<급>/*.json)·모의고사 템플릿(exams/N<급>.json) 형식 검사와 DB 행 만들기(architecture 9.2.2~9.2.3)."""
import re
from collections import Counter

import pl_authored as au
import pl_grammar as gm
from pl_common import Fail, jdump, read_json

ID = re.compile(r"^q-n([1-5])-(vocab|grammar|reading)-(\d{4})$")
TYPES = {
    "vocab": {"kanji-reading", "orthography", "word-formation", "context", "paraphrase", "usage"},
    "grammar": {"grammar-form", "sentence-order", "text-grammar"},
    "reading": {"short", "mid", "long", "integrated", "thematic", "info-search"},
    "listening": {"task", "point", "summary", "utterance", "quick-response", "integrated"},
}
HIGHLIGHT = {"kanji-reading", "orthography", "paraphrase"}  # [[…]] 정확히 1개 + target
NEEDS_TARGET = HIGHLIGHT | {"usage"}
BLANKED = {"context", "grammar-form", "word-formation", "text-grammar"}  # ＿＿ 1개 이상
BLANK, STAR = "＿＿", "＿★＿"
# 선택지 4개 중 "모두 맞음/정답 없음"류(실제 시험에 없는 형식)
NONE_OF = re.compile(r"(すべて|全て|全部|どれ|いずれ)(も|が)?(正しい|正しくない|あっている|合っている|間違い|まちがい|違う|ちがう|同じ|当てはまる)"
                     r"|正解(は)?(ありません|なし|無し)|答え(は)?(ありません|なし)|該当(なし|しない)|どれでもない|上記(の)?(すべて|全て)"
                     r"|모두 (맞|옳|정답)|정답 (없|이 없)")
SPAN = re.compile(r"\[\[(.*?)\]\]")
RUBY_BASE = re.compile(r"\{([^{}|]*)\|[^{}|]*\}")
RUBY = re.compile(r"\{[^{}|]*\|[^{}|]*\}")


def blank_problems(rid, qtype, stem, passage, choices):
    bad = lambda why: f"FAIL: authored {rid} blank {why}"
    out, b, s, sb, ss = [], 0, 0, 0, 0
    for name, f in (("stem", stem), ("passage", passage)):
        if not isinstance(f, str):
            continue
        for m in RUBY.finditer(f):  # 루비 읽기 안의 토큰
            if "＿" in m.group(0) or "★" in m.group(0):
                out.append(bad("token inside ruby"))
        for m in SPAN.finditer(f):
            if "＿" in m.group(1) or "★" in m.group(1):
                out.append(bad("token inside [[ ]]"))
        f = RUBY_BASE.sub(r"\1", f)  # base 안 토큰은 아래 stray로도 잡힌다
        n = f.count(STAR)
        rest = f.replace(STAR, "")
        k = rest.count(BLANK)
        if "＿" in rest.replace(BLANK, "") or "★" in rest:
            out.append(bad(f"stray ＿ or ★ in {name}"))
        b, s = b + k, s + n
        if name == "stem":
            sb, ss = k, n
    if RUBY_BASE.search(f"{stem}{passage}") and any(
            "＿" in m.group(1) or "★" in m.group(1) for m in RUBY_BASE.finditer(f"{stem}{passage}")):
        out.append(bad("token inside ruby base"))
    if any(isinstance(c, str) and ("＿" in c or "★" in c) for c in choices):
        out.append(bad("token in choices"))
    if qtype == "sentence-order":
        if (sb, ss) != (3, 1) or (b, s) != (3, 1):
            out.append(bad(f"sentence-order needs 3 ＿＿ and 1 ＿★＿ in stem only (has {sb}, {ss}; total {b}, {s})"))
    elif qtype in BLANKED:
        if b < 1 or s:
            out.append(bad(f"{qtype} needs 1+ ＿＿ and no ＿★＿ (has {b}, {s})"))
    elif b or s:
        out.append(bad(f"{qtype} must have no blank (has {b}, {s})"))
    return out


def target_problems(rid, r, qtype):
    bad = lambda why: f"FAIL: authored {rid} target {why}"
    tg = r.get("target")
    if qtype not in NEEDS_TARGET:
        return [bad("not allowed for this type")] if tg is not None else []
    if not isinstance(tg, str) or not tg.strip():
        return [bad("missing")]
    if RUBY.search(tg) or any(c in tg for c in "{|}") or "[[" in tg:
        return [bad("must be plain text")]
    if qtype not in HIGHLIGHT:
        return []
    stem, passage = r.get("stem"), r.get("passage")
    text = stem if isinstance(stem, str) and stem.strip() else passage
    spans = SPAN.findall(text) if isinstance(text, str) else []
    if len(spans) != 1:
        return [bad(f"needs exactly one [[ ]] in stem (has {len(spans)})")]
    if qtype == "kanji-reading" and RUBY.search(spans[0]):
        return [bad("ruby inside [[ ]] of kanji-reading")]
    if RUBY_BASE.sub(r"\1", spans[0]) != tg:
        return [bad("differs from the [[ ]] base text")]
    return []


def question_problems(recs, manifest, known=None):
    """known: 있으면 targets가 이 ID 집합 안에 있어야 한다."""
    out, seen = [], set()
    for r in recs:
        rid = r.get("id", "?")
        bad = lambda why: f"FAIL: authored {rid} {why}"
        m = ID.match(rid) if isinstance(rid, str) else None
        if not m:
            out.append(bad("id format"))
        if isinstance(rid, str):
            if rid in seen:
                out.append(bad("duplicate id"))
            seen.add(rid)
        if r.get("level") not in ("N1", "N2", "N3", "N4", "N5"):
            out.append(bad("level invalid"))
        elif m and r["level"] != "N" + m.group(1):
            out.append(bad("level differs from id"))
        if r.get("section") not in ("vocab", "grammar", "reading"):
            out.append(bad(f"section invalid ({r.get('section')!r})"))
        elif m and r["section"] != m.group(2):
            out.append(bad("section differs from id"))
        if not isinstance(r.get("status"), str) or r["status"] not in gm.STATUS:
            out.append(bad(f"status invalid ({r.get('status')!r})"))
        out += gm.common_problems(rid, r, manifest)
        qtype = r.get("type")
        sec = r.get("section")
        if isinstance(sec, str) and sec in TYPES and not (isinstance(qtype, str) and qtype in TYPES[sec]):
            out.append(bad(f"type {qtype!r} is not a {r['section']} code"))
        stem, passage = r.get("stem"), r.get("passage")
        if isinstance(stem, str) and not stem.strip() and isinstance(passage, str) and passage.strip():
            pass  # stem이 비고 passage가 있는 문항(대상 구간은 passage에서 찾는다)
        else:
            out += gm.ja_problems(rid, "stem", stem)
        if passage is not None:
            out += gm.ja_problems(rid, "passage", passage)
        ch = r.get("choices")
        if not isinstance(ch, list) or len(ch) != 4:
            out.append(bad("choices need exactly 4"))
            ch = []
        for i, c in enumerate(ch):
            out += gm.ja_problems(rid, f"choices[{i}]", c)
            if isinstance(c, str) and NONE_OF.search(c):
                out.append(bad(f"choices[{i}] is an all/none-of-the-above choice"))
        ai = r.get("answerIndex")
        ai_ok = isinstance(ai, int) and not isinstance(ai, bool) and 0 <= ai <= 3
        if not ai_ok:
            out.append(bad("answerIndex out of range 0..3"))
        out += gm.ko_problems(rid, "explanationKo", r.get("explanationKo"))
        ww = r.get("whyWrongKo")
        if not isinstance(ww, list) or len(ww) != 4:
            out.append(bad("whyWrongKo needs exactly 4 items"))
        elif ai_ok:
            for i, w in enumerate(ww):
                if i == ai:
                    if w is not None:
                        out.append(bad(f"whyWrongKo[{i}] must be null (correct answer)"))
                elif not isinstance(w, str) or not w.strip():
                    out.append(bad(f"whyWrongKo[{i}] must be a non-empty string"))
                else:
                    out += gm.ko_problems(rid, f"whyWrongKo[{i}]", w)
        tg = r.get("targets")
        if not isinstance(tg, list) or not all(isinstance(t, str) for t in tg):
            out.append(bad("targets must be a list of ids"))
        elif known is not None:
            out += [bad(f"targets {t} not found in build") for t in tg if t not in known]
        if isinstance(qtype, str):
            out += target_problems(rid, r, qtype)
            out += blank_problems(rid, qtype, stem, passage, ch)
    return out + distribution_problems(recs)


def distribution_problems(recs):
    """급수·영역별 정답 위치: 문항 10개 이상이면 한 위치가 40%를 넘지 않아야 한다."""
    groups = {}
    for r in recs:
        if r.get("status") != "retired" and isinstance(r.get("level"), str) and isinstance(r.get("section"), str) \
                and isinstance(r.get("answerIndex"), int) and 0 <= r["answerIndex"] <= 3:
            groups.setdefault((r.get("level"), r.get("section")), []).append(r["answerIndex"])
    out = []
    for (lvl, sec), xs in sorted(groups.items(), key=str):
        if len(xs) >= 10:
            pos, n = Counter(xs).most_common(1)[0]
            if n * 10 > len(xs) * 4:
                out.append(f"FAIL: authored {lvl} {sec} answer position {pos} is {n}/{len(xs)} (over 40%)")
    return out


def exam_problems(rec, name):
    """name: 파일 stem(N5)."""
    bad = lambda why: f"FAIL: authored exam {name} {why}"
    if not isinstance(rec, dict):
        return [bad("not an object")]
    out = []
    if rec.get("level") != name or name not in ("N1", "N2", "N3", "N4", "N5"):
        out.append(bad("level differs from file name"))
    url = rec.get("sourceUrl")
    if not isinstance(url, str) or not re.match(r"^https?://\S+$", url):
        out.append(bad("sourceUrl missing"))
    ca = rec.get("checkedAt")
    if not isinstance(ca, str) or not au.DATE.match(ca):
        out.append(bad("checkedAt missing"))
    secs = rec.get("sections")
    if not isinstance(secs, list) or not secs:
        return out + [bad("sections empty")]
    for i, s in enumerate(secs):
        if not isinstance(s, dict) or not isinstance(s.get("id"), str) or not s["id"]:
            out.append(bad(f"sections[{i}] needs id"))
            continue
        out += [x.replace("FAIL: authored ", f"FAIL: authored exam {name} ", 1) for x in gm.ko_problems("-", f"sections[{i}].titleKo", s.get("titleKo"))]
        if not isinstance(s.get("minutes"), int) or isinstance(s.get("minutes"), bool) or s["minutes"] < 1:
            out.append(bad(f"sections[{i}] minutes invalid"))
        parts = s.get("parts")
        if not isinstance(parts, list) or not parts:
            out.append(bad(f"sections[{i}] parts empty"))
            continue
        for j, p in enumerate(parts):
            if not isinstance(p, dict) or not isinstance(p.get("section"), str) or p["section"] not in TYPES:
                out.append(bad(f"sections[{i}].parts[{j}] section invalid"))
            elif not isinstance(p.get("type"), str) or p["type"] not in TYPES[p["section"]]:
                out.append(bad(f"type sections[{i}].parts[{j}] {p.get('type')!r} is not a {p['section']} code"))
            if isinstance(p, dict) and (not isinstance(p.get("count"), int) or isinstance(p.get("count"), bool) or p["count"] < 1):
                out.append(bad(f"sections[{i}].parts[{j}] count invalid"))
    return out


def files_for(paths, kind):
    if kind == "sample":
        return sorted((paths.authored / "sample" / "questions").glob("*.json")), sorted((paths.authored / "sample" / "exams").glob("N[1-5].json"))
    return sorted((paths.authored / "questions").glob("N[1-5]/*.json")), sorted((paths.authored / "exams").glob("N[1-5].json"))


def load_questions(paths, kind, manifest, levels=None, known=None):
    """→ (문항 레코드들, 시험 템플릿 레코드들, 읽은 파일들). 형식 문제가 있으면 Fail. levels 밖 급수는 뺀다."""
    qfiles, efiles = files_for(paths, kind)
    qrecs = gm.read_all(qfiles)
    exams = []
    for f in efiles:
        try:
            exams.append(read_json(f))
        except ValueError as e:
            raise Fail(f"FAIL: authored exam {f.stem} invalid json ({e})")
    if levels is not None:
        lv = lambda s: isinstance(s, str) and s[1:].isdigit() and int(s[1:]) in levels
        keep = [(f, r) for f, r in zip(qfiles, qrecs) if lv(r.get("level")) or not isinstance(r.get("level"), str)]
        qfiles, qrecs = [f for f, _ in keep], [r for _, r in keep]
        keep = [(f, r) for f, r in zip(efiles, exams) if not isinstance(r, dict) or lv(r.get("level")) or not isinstance(r.get("level"), str)]
        efiles, exams = [f for f, _ in keep], [r for _, r in keep]
    problems = question_problems(qrecs, manifest, known)
    for f, e in zip(efiles, exams):
        problems += exam_problems(e, f.stem)
    if problems:
        raise Fail("\n".join(problems))
    return qrecs, exams, qfiles + efiles


def question_rows(recs):
    """retired 제외 → question 행(ID 순). sort_key = ID 끝 4자리 번호."""
    return [{"id": r["id"], "level": int(r["level"][1]), "section": r["section"], "type": r["type"], "sort_key": int(r["id"][-4:]),
             "stem": r["stem"], "passage": r.get("passage"), "target": r.get("target"), "choices_json": jdump(r["choices"]),
             "answer_index": r["answerIndex"], "explanation_ko": r["explanationKo"], "why_wrong_ko_json": jdump(r["whyWrongKo"]),
             "targets_json": jdump(r["targets"]), "source_id": "authored", "ko_source": "authored", "ko_method": r["method"],
             "ko_review_status": r["reviewStatus"], "ko_reviewed_at": r.get("reviewedAt"), "ko_reviewer_kind": r.get("reviewerKind"),
             "needs_review": int(r["needsReview"])}
            for r in sorted((r for r in recs if r["status"] != "retired"), key=lambda r: r["id"])]


def exam_rows(exams):
    return [{"level": int(e["level"][1]), "template_json": jdump({"sections": e["sections"]}), "source_url": e["sourceUrl"],
             "checked_at": e["checkedAt"], "source_id": "authored"} for e in sorted(exams, key=lambda e: e["level"], reverse=True)]


def ko_texts(recs):
    """한국어 필드 안 일본어 구간 보고용 [(id, [텍스트])]."""
    return [(r["id"], [r["explanationKo"]] + [w for w in r["whyWrongKo"] if w]) for r in recs if r["status"] != "retired"]
