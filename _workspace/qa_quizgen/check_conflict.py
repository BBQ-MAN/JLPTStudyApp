"""QA 일회성: vocab_conflict 를 원본 JMdict + DB 한국어 뜻으로 독립 재계산(quizgen.md 3.4)해 번들 표와 비교. python3 -I check_conflict.py <JMdict_e.gz> <content.db>"""
import sys, sqlite3, json, re, collections
sys.path.insert(0, __file__.rsplit("/",1)[0])
import check_sample as c
E = c.parse(sys.argv[1]); con = sqlite3.connect("file:%s?mode=ro" % sys.argv[2], uri=True)
V = {i: (l, w, r) for i, l, w, r in con.execute("select id, level, written, reading from vocab where id like 'jm:%'")}
ko = collections.defaultdict(list)
for vid, t in con.execute("select vocab_id, text_ko from vocab_meaning"):
    ko[vid].append(t)
def kop(texts):
    out = set()
    for t in texts:
        for p in re.split(r"[,;/]", re.sub(r"\([^)]*\)", "", t)):
            p = re.sub(r"[\s~〜…·]", "", p)
            if p: out.add(p)
    return out
en = {i: c.toks(E[int(i[3:])], c.STOP_SPEC) for i in V}
kp = {i: kop(ko[i]) for i in V}
exp = set()
ids = sorted(V)
by_level = collections.defaultdict(list)
for i in ids: by_level[V[i][0]].append(i)
for lv, L in by_level.items():
    for x in range(len(L)):
        for y in range(x+1, len(L)):
            a, b = L[x], L[y]
            if en[a] & en[b] or any(p == q or p in q or q in p for p in kp[a] for q in kp[b]): exp.add((a, b))
    # xref
    for a in L:
        for s in E[int(a[3:])]["senses"]:
            for x in s["xref"]:
                t = [p for p in x.split("・") if not p.isdigit()]
                head, second = t[0], (t[1] if len(t) > 1 else None)
                for b in L:
                    if b == a: continue
                    e = E[int(b[3:])]
                    if head in [k for k, _ in e["keb"]] + [r[0] for r in e["reb"]] and (second is None or second in [r[0] for r in e["reb"]]):
                        exp.add(tuple(sorted((a, b))))
act = set()
for a, j in con.execute("select vocab_id, conflict_ids from vocab_conflict"):
    for b in json.loads(j): act.add(tuple(sorted((a, b))))
print("expected pairs", len(exp), "actual", len(act), "missing(미배제)", len(exp - act), "extra", len(act - exp))
print(sorted(exp - act)[:30]); print(sorted(act - exp)[:10])
