"""청해 대본(content/authored/listening/N<급>/*.json) 형식 검사와 DB 행 만들기(architecture 9.3.1)."""
import re

import pl_authored as au
import pl_grammar as gm
import pl_question as qs
from pl_common import Fail, jdump

ID = re.compile(r"^ls-n([1-5])-(\d{4})$")
SPEAKERS = {"M", "F", "N"}
EMPH = ("[[", "]]")


def line_problems(rid, i, ln):
    bad = lambda why: f"FAIL: authored {rid} lines[{i}] {why}"
    if not isinstance(ln, dict):
        return [bad("not an object")]
    out = []
    if not isinstance(ln.get("speaker"), str) or ln["speaker"] not in SPEAKERS:
        out.append(bad(f"speaker invalid ({ln.get('speaker')!r})"))
    t = ln.get("text")
    out += gm.ja_problems(rid, f"lines[{i}].text", t)
    if isinstance(t, str) and any(e in t for e in EMPH):
        out.append(bad("text has [[ ]]"))
    if "ttsText" in ln:
        tt = ln["ttsText"]
        if not isinstance(tt, str) or not tt.strip():
            out.append(bad("ttsText must be a non-empty string"))
        elif any(c in tt for c in "{|}") or any(e in tt for e in EMPH):
            out.append(bad("ttsText must be plain text (no { | } [[ ]])"))
    return out


def listening_problems(recs, manifest, known=None):
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
        if not isinstance(r.get("status"), str) or r["status"] not in gm.STATUS:
            out.append(bad(f"status invalid ({r.get('status')!r})"))
        out += gm.common_problems(rid, r, manifest)
        if not isinstance(r.get("type"), str) or r["type"] not in qs.TYPES["listening"]:
            out.append(bad(f"type {r.get('type')!r} is not a listening code"))
        lines = r.get("lines")
        if not isinstance(lines, list) or not lines:
            out.append(bad("lines empty"))
        else:
            for i, ln in enumerate(lines):
                out += line_problems(rid, i, ln)
        if "question" in r:
            q = r["question"]
            if not isinstance(q, str) or not q.strip():
                out.append(bad("question empty (omit the key instead)"))
            else:
                out += gm.ja_problems(rid, "question", q)
                if any(e in q for e in EMPH):
                    out.append(bad("question has [[ ]]"))
        ch = r.get("choices")
        if not isinstance(ch, list) or len(ch) != 4:
            out.append(bad("choices need exactly 4"))
            ch = []
        for i, c in enumerate(ch):
            out += gm.ja_problems(rid, f"choices[{i}]", c)
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
    return out


def files_for(paths, kind):
    if kind == "sample":
        return sorted((paths.authored / "sample" / "listening").glob("*.json"))
    return sorted((paths.authored / "listening").glob("N[1-5]/*.json"))


def load_listening(paths, kind, manifest, levels=None, known=None):
    """→ (대본 레코드들, 읽은 파일들). 형식 문제가 있으면 Fail. levels 밖 급수는 뺀다."""
    files = files_for(paths, kind)
    recs = gm.read_all(files)
    if levels is not None:
        lv = lambda s: isinstance(s, str) and s[1:].isdigit() and int(s[1:]) in levels
        keep = [(f, r) for f, r in zip(files, recs) if lv(r.get("level")) or not isinstance(r.get("level"), str)]
        files, recs = [f for f, _ in keep], [r for _, r in keep]
    problems = listening_problems(recs, manifest, known)
    if problems:
        raise Fail("\n".join(problems))
    return recs, files


def listening_rows(recs):
    """retired 제외 → listening 행(ID 순). question 없으면 NULL, ttsText 없으면 키 생략."""
    return [{"id": r["id"], "level": int(r["level"][1]), "sort_key": int(r["id"][-4:]), "type": r["type"],
             "lines_json": jdump([{k: ln[k] for k in ("speaker", "text", "ttsText") if k in ln} for ln in r["lines"]]),
             "question": r.get("question"), "choices_json": jdump(r["choices"]), "answer_index": r["answerIndex"],
             "explanation_ko": r["explanationKo"], "why_wrong_ko_json": jdump(r["whyWrongKo"]), "targets_json": jdump(r["targets"]),
             "source_id": "authored", "ko_source": "authored", "ko_method": r["method"], "ko_review_status": r["reviewStatus"],
             "ko_reviewed_at": r.get("reviewedAt"), "ko_reviewer_kind": r.get("reviewerKind"), "needs_review": int(r["needsReview"])}
            for r in sorted((r for r in recs if r["status"] != "retired"), key=lambda r: r["id"])]


def ko_texts(recs):
    return qs.ko_texts(recs)
