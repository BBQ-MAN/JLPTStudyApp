"""QA 일회성: JMdict_e.gz 를 직접 파싱해 quizgen_sample.md 의 정답 둘 문항을 찾는다.
사용: python3 -I check_sample.py <JMdict_e.gz> <quizgen_sample.md>
DB 의 jm_form / vocab_conflict 는 쓰지 않는다."""
import sys, gzip, re, html, collections

def hira(s): return "".join(chr(ord(c)-0x60) if 0x30A1 <= ord(c) <= 0x30F6 else c for c in s)

def tags(block, name):
    return re.findall(r"<%s(?:\s[^>]*)?>(.*?)</%s>" % (name, name), block, re.S)

def parse(path):
    raw = gzip.open(path, "rt", encoding="utf-8").read()
    ents = {}
    for m in re.finditer(r"<entry>(.*?)</entry>", raw, re.S):
        b = m.group(1)
        seq = int(tags(b, "ent_seq")[0])
        ks = [(html.unescape(tags(k, "keb")[0]), tags(k, "ke_inf")) for k in re.findall(r"<k_ele>(.*?)</k_ele>", b, re.S)]
        rs = []
        for r in re.findall(r"<r_ele>(.*?)</r_ele>", b, re.S):
            rs.append((html.unescape(tags(r, "reb")[0]), [html.unescape(x) for x in tags(r, "re_restr")], tags(r, "re_inf"), "<re_nokanji" in r))
        ss = []
        for s in re.findall(r"<sense>(.*?)</sense>", b, re.S):
            ss.append(dict(gloss=[html.unescape(g) for g in tags(s, "gloss")], xref=[html.unescape(x) for x in tags(s, "xref")],
                           stagk=[html.unescape(x) for x in tags(s, "stagk")], stagr=[html.unescape(x) for x in tags(s, "stagr")]))
        ents[seq] = dict(keb=ks, reb=rs, senses=ss)
    return ents

STOP_SPEC = {"a","an","the","to","of","be","is","are","am","was","were","been","being","one","ones","someone","something","somebody","somewhere","oneself","etc","eg","ie","in","on","at","by","for","with","from","into","onto","as","or","and","not","it","its","that","this","than","up","out","off","over","so","such","very","esp","usu","oft"}
STOP_MIN = {"a","an","the","to","of","be","or","and","in","on","at","by","for","with","from","as","it","one"}
def toks(e, stop, strip_paren=True):
    out = set()
    for s in e["senses"]:
        for g in s["gloss"]:
            g = g.lower()
            if strip_paren: g = re.sub(r"\([^)]*\)", " ", g)
            for t in re.findall(r"[a-z]+", g):
                if t.endswith("ies") and len(t) > 4: t = t[:-3] + "y"
                elif t.endswith("s") and not t.endswith("ss") and len(t) > 3: t = t[:-1]
                if len(t) > 1 and t not in stop: out.add(t)
    return out
def phrases(e):
    return {re.sub(r"\([^)]*\)", "", g).strip().lower() for s in e["senses"] for g in s["gloss"]} - {""}

def parse_sample(path):
    qs = []; cur = None; sec = ""
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.startswith("### "): sec = line[4:]
        m = re.match(r"#### (N\d) (\S+) · (\d+)", line)
        if m: cur = dict(level=m.group(1), kind=m.group(2), n=m.group(3), choices=[], sec=sec); qs.append(cur); continue
        if cur is None: continue
        m = re.match(r"\| ID \| `(.*?)` \|", line)
        if m: cur["id"] = m.group(1)
        m = re.match(r"\| 지문 \| (.*) \|$", line)
        if m: cur["stem"] = m.group(1)
        m = re.match(r"\| 출처 단어 ID \| `jm:(\d+)` \((.*?) / (.*?)\) \|", line)
        if m: cur["seq"] = int(m.group(1)); cur["W"] = m.group(2); cur["R"] = m.group(3)
        m = re.match(r"\| (\d) \| (.*?) \| (✔ 정답)? ?\| (.*?) \| (.*?) \| (?:`jm:(\d+)`)? ?\|$", line)
        if m: cur["choices"].append(dict(i=int(m.group(1)), text=m.group(2), ans=bool(m.group(3)), kind=m.group(4), why=m.group(5), src=int(m.group(6)) if m.group(6) else None))
    return qs

def main(jm, sample):
    E = parse(jm); Q = parse_sample(sample)
    print("JMdict entries", len(E), "questions", len(Q))
    by_keb = collections.defaultdict(set); by_reading = collections.defaultdict(set)
    for seq, e in E.items():
        for k, _ in e["keb"]: by_keb[k].add(seq)
        for r in e["reb"]: by_reading[hira(r[0])].add(seq)
    cnt = collections.Counter(); hits = []; warn = []
    for q in Q:
        ch = q["choices"]; assert len(ch) == 4 and sum(c["ans"] for c in ch) == 1, (q["id"], len(ch))
        ans = [c for c in ch if c["ans"]][0]
        typ = q["id"].split(":")[1]; cnt[(q["level"], typ)] += 1
        if typ == "kanji-reading":
            W = q["W"]
            rd = collections.defaultdict(list)    # reading -> [(seq, restricted?)]
            for s in by_keb[W]:
                for reb, restr, inf, nk in E[s]["reb"]:
                    rd[hira(reb)].append((s, bool(restr) and W not in restr, inf))
            if hira(ans["text"]) not in rd: warn.append((q["id"], "정답 읽기가 JMdict에 (W,R) 없음", W, ans["text"]))
            for c in ch:
                if c["ans"]: continue
                if hira(c["text"]) in rd: hits.append((q["id"], "①", W, "오답=정답 읽기 후보", c["text"], rd[hira(c["text"])], "ans=" + ans["text"]))
        elif typ == "orthography":
            R = hira(q["R"]); W = q["W"]
            if W not in by_keb or R not in {hira(r[0]) for s in by_keb[W] for r in E[s]["reb"]}: warn.append((q["id"], "정답 (W,R) JMdict에 없음", W, R))
            for c in ch:
                if c["ans"]: continue
                x = c["text"]
                ss = [s for s in by_keb.get(x, ()) if any(hira(r[0]) == R and (not r[1] or x in r[1]) for r in E[s]["reb"])]
                if ss: hits.append((q["id"], "②", R, "오답=같은 읽기 표기", x, [(s, [ (k,i) for k,i in E[s]["keb"] if k == x]) for s in ss], "ans=" + W))
                ss2 = [s for s in by_keb.get(x, ())]
                # 읽기 R 과 무관해도 x 가 실재 표기인가(정보)
        elif typ.startswith("meaning"):
            A = E[q["seq"]]; texts = [c["text"] for c in ch]
            for c in ch:
                if c["ans"] or c["src"] is None: continue
                B = E[c["src"]]
                t1 = toks(A, STOP_SPEC) & toks(B, STOP_SPEC)
                t2 = toks(A, STOP_MIN, False) & toks(B, STOP_MIN, False)
                ph = phrases(A) & phrases(B)
                xr = []
                for (X, Y, sx) in ((A, B, "A->B"), (B, A, "B->A")):
                    for s in X["senses"]:
                        for x in s["xref"]:
                            t = [p for p in x.split("・") if not p.isdigit()]
                            head = t[0]; second = t[1] if len(t) > 1 else None
                            if head in [k for k,_ in Y["keb"]] + [r[0] for r in Y["reb"]] and (second is None or second in [r[0] for r in Y["reb"]]):
                                xr.append((sx, x))
                # 같은 항목(동일 seq)?
                tag = []
                if t1: tag.append("spec-en:" + ",".join(sorted(t1)))
                if ph: tag.append("same-gloss:" + "|".join(sorted(ph)))
                if xr: tag.append("xref:" + str(xr))
                if tag: hits.append((q["id"], "③", q["W"], "오답 B=%d" % c["src"], tag))
                elif t2: warn.append((q["id"], "③ 완화 토큰만 겹침(스펙 규칙은 통과)", q["W"], c["src"], sorted(t2)))
            # 한국어 보기끼리 포함/동일 (표시 문자열 기준)
            if typ == "meaning-ja-ko":
                def kp(s): return {re.sub(r"[\s~〜…·]", "", p) for p in re.split(r"[,;/]", re.sub(r"\([^)]*\)", "", s)) if re.sub(r"[\s~〜…·]", "", p)}
                for c in ch:
                    if c["ans"]: continue
                    a, b = kp(ans["text"]), kp(c["text"])
                    if any(x == y or x in y or y in x for x in a for y in b): hits.append((q["id"], "③ko", q["W"], "한국어 뜻 포함/동일", ans["text"], c["text"]))
    print("문항 수", dict(cnt)); print("\n== 정답 둘 후보(hits)", len(hits))
    for h in hits: print(h)
    print("\n== 경고(warn)", len(warn))
    for w in warn: print(w)

if __name__ == "__main__": main(sys.argv[1], sys.argv[2])
