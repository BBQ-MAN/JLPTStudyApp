"""QA 일회성: 원본 JMdict 에서 jm_form 을 다시 계산해 번들 content.db 와 비교. python3 -I check_jmform.py <JMdict_e.gz> <content.db>"""
import sys, sqlite3, collections
sys.path.insert(0, __file__.rsplit("/",1)[0])
import check_sample as c
E = c.parse(sys.argv[1])
con = sqlite3.connect("file:%s?mode=ro" % sys.argv[2], uri=True)
rows = con.execute("select written, reading from vocab where id like 'jm:%' and written is not null").fetchall()
W = {w for w, r in rows}; R = {c.hira(r) for w, r in rows}
exp = set()
for s, e in E.items():
    for k, _ in e["keb"]:
        for r in e["reb"]:
            h = c.hira(r[0])
            if k in W or h in R: exp.add((k, h))
act = set(con.execute("select written, reading from jm_form").fetchall())
print("expected", len(exp), "actual", len(act), "missing", len(exp-act), "extra", len(act-exp))
print(sorted(exp-act)[:20], sorted(act-exp)[:20])
# 모든 jm: 어휘 id 가 원본 seq 와 같은 (written, reading) 인가
bad = 0
for id_, w, r in con.execute("select id, written, reading from vocab where id like 'jm:%'"):
    e = E.get(int(id_[3:]))
    if e is None: print("no seq", id_); bad += 1; continue
    if w is not None and w not in [k for k, _ in e["keb"]]: print("written not in entry", id_, w); bad += 1
    if c.hira(r) not in [c.hira(x[0]) for x in e["reb"]]: print("reading not in entry", id_, r); bad += 1
print("vocab vs jmdict mismatches", bad)
# vocab_conflict 대칭/동급수
conf = {a: __import__("json").loads(b) for a, b in con.execute("select vocab_id, conflict_ids from vocab_conflict")}
lv = dict(con.execute("select id, level from vocab"))
asym = [(a, b) for a, l in conf.items() for b in l if a not in conf.get(b, [])]
cross = [(a, b) for a, l in conf.items() for b in l if lv[a] != lv[b]]
print("asym", len(asym), "cross-level", len(cross), "self", sum(a in l for a, l in conf.items()))
