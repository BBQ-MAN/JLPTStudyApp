"""quizgen 배제 데이터(docs/spec/quizgen.md 3.3·3.4): jm_form(표기·읽기 쌍)·vocab_conflict(같은 급수 뜻 충돌 목록)."""
import re

from pl_common import hira, jdump

EN_STOP = {"a", "an", "the", "to", "of", "be", "is", "are", "am", "was", "were", "been", "being", "one", "ones", "someone", "something",
           "somebody", "somewhere", "oneself", "etc", "eg", "ie", "in", "on", "at", "by", "for", "with", "from", "into", "onto", "as", "or",
           "and", "not", "it", "its", "that", "this", "than", "up", "out", "off", "over", "so", "such", "very", "esp", "usu", "oft"}


def en_tokens(entry):
    """항목 하나의 모든 sense·모든 gloss의 영어 토큰(괄호 설명 제외, 복수형 단순화, 불용어 제외). stagk·stagr 제한은 무시한다."""
    out = set()
    for s in entry["senses"]:
        for g in s["gloss"]:
            g = re.sub(r"\([^)]*\)", " ", g.lower())
            for t in re.findall(r"[a-z]+", g):
                if t.endswith("ies") and len(t) > 4:
                    t = t[:-3] + "y"
                elif t.endswith("s") and not t.endswith("ss") and len(t) > 3:
                    t = t[:-1]
                if len(t) > 1 and t not in EN_STOP:
                    out.add(t)
    return out


def ko_phrases(texts):
    """어휘 하나의 모든 vocab_meaning.text_ko → 구절 집합(괄호 설명 제외, , ; / 로 나눔, 공백·~〜…· 제거)."""
    out = set()
    for text in texts:
        for p in re.split(r"[,;/]", re.sub(r"\([^)]*\)", "", text)):
            p = re.sub(r"[\s~〜…·]", "", p)
            if p:
                out.add(p)
    return out


def ko_overlap(A, B):
    """같거나 한쪽이 다른 쪽에 포함되는 첫 구절 쌍(없으면 None; A·B는 구절 목록). 포함 관계는 과배제(안전 쪽)."""
    for a in A:
        for b in B:
            if a == b or a in b or b in a:
                return a, b
    return None


def jm_form_rows(related, vocab):
    """related 항목의 (keb, hira(reb)) 조합 중 keb ∈ Wdb 또는 hira(reb) ∈ Rdb. re_restr는 읽지 않는다(과배제 = 안전). 정렬 (written, reading)."""
    jm = [v for v in vocab if v["id"].startswith("jm:") and v["written"] is not None]
    wdb, rdb = {v["written"] for v in jm}, {hira(v["reading"]) for v in jm}
    pairs = {(k, hira(r)) for e in related for k in e["keb"] for r in e["reb"] if k in wdb or hira(r) in rdb}
    return [{"written": w, "reading": r} for w, r in sorted(pairs)]


def vocab_conflicts(vocab, ko, entry_of, xref_of):
    """→ ({vocab_id: 정렬된 충돌 ID 목록}, {(a, b): [(이유, 증거)]}). 모든 `jm:` 어휘가 키를 가진다. 같은 급수·a≠b만, 대칭.
    vocab: 행(id, level, written, reading), ko: {id: 모든 뜻 text_ko}, entry_of: {seq: S-01 스냅샷 항목}, xref_of: {seq: xref 목록}."""
    jm = sorted((v for v in vocab if v["id"].startswith("jm:")), key=lambda v: v["id"])
    why = {}

    def link(a, b, reason, evidence):
        why.setdefault(tuple(sorted((a["id"], b["id"]))), []).append((reason, evidence))

    by_level = {}
    for v in jm:
        by_level.setdefault(v["level"], []).append(v)
    phr = {v["id"]: sorted(ko_phrases(ko.get(v["id"], []))) for v in jm}  # 정렬: 증거 구절이 입력 순서에 따라 바뀌지 않게
    tok = {v["id"]: en_tokens(entry_of[int(v["id"][3:])]) if int(v["id"][3:]) in entry_of else set() for v in jm}
    for group in by_level.values():
        for i, a in enumerate(group):
            for b in group[i + 1:]:
                hit = ko_overlap(phr[a["id"]], phr[b["id"]])
                if hit:
                    link(a, b, "ko", "/".join(hit))
                shared = tok[a["id"]] & tok[b["id"]]
                if shared:
                    link(a, b, "en", sorted(shared)[0])
        for a in group:  # xref: A의 xref가 같은 급수 B를 가리키면 양방향(1단계)
            for x in xref_of.get(int(a["id"][3:]), []):
                t = [s for s in x.split("・") if not s.isdigit()]
                if not t:
                    continue
                for b in group:
                    e = entry_of.get(int(b["id"][3:]))
                    if b is not a and e and t[0] in e["keb"] + e["reb"] and (len(t) < 2 or t[1] in e["reb"]):
                        link(a, b, "xref", x)
    conf = {v["id"]: [] for v in jm}
    for a, b in why:
        conf[a].append(b)
        conf[b].append(a)
    return {k: sorted(v) for k, v in conf.items()}, why


def derive(ds, entries, related):
    """ds.vocab·ds.meanings와 S-01 스냅샷 항목(entries)·related 항목으로 ds.jm_form·ds.vocab_conflict·ds.quizgen_why를 채운다."""
    ko = {}
    for m in ds.meanings:
        ko.setdefault(m["vocab_id"], []).append(m["text_ko"])
    entry_of = {e["seq"]: e for e in entries}
    conf, ds.quizgen_why = vocab_conflicts(ds.vocab, ko, entry_of, {e["seq"]: e["xref"] for e in related})
    ds.jm_form = jm_form_rows(related, ds.vocab)
    ds.vocab_conflict = [{"vocab_id": v, "conflict_ids": jdump(ids)} for v, ids in sorted(conf.items())]


def conflicts_tsv(why, level_of):
    """보고서(커밋 안 함): a\\tb\\tlevel\\treasons\\tevidence — reasons는 en·ko·xref 쉼표, evidence는 이유마다 첫 증거 하나."""
    lines = ["a\tb\tlevel\treasons\tevidence\n"]
    for (a, b), items in sorted(why.items()):
        first = {}
        for reason, ev in items:
            first.setdefault(reason, ev)
        reasons = [r for r in ("en", "ko", "xref") if r in first]
        lines.append(f"{a}\t{b}\t{level_of[a]}\t{','.join(reasons)}\t{first[reasons[0]]}\n")
    return "".join(lines)
