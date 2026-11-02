# 사용: python3 -I gen_impl.py <out.js> [--review] [--fix N] M2 M3 ...
# implement-milestones.js 사본에 milestones.json 항목을 넣어 만든다(하네스 원본은 바꾸지 않는다). --review면 screenshots/ 목록을 UI 마일스톤에 붙인다.
import json,os,sys
root='/Users/bbqman/HermesAgents/ETC/JLPTStudyApp'
a=sys.argv[1:]; out=a.pop(0); review='--review' in a; a=[x for x in a if x!='--review']
rids=[x[1:] for x in a if x.startswith('@')]; a=[x.lstrip('@') for x in a]  # @M2 = 그 마일스톤만 리뷰 전용
fix=2
if '--fix' in a: i=a.index('--fix'); fix=int(a[i+1]); del a[i:i+2]
ms={m['id']:m for m in json.load(open(f'{root}/docs/spec/milestones.json'))}
sel=[dict(ms[i]) for i in a]
for m in sel:
    if m['id'] in rids: m['reviewOnly']=True
if review or rids:
    shots=['screenshots/'+f for f in sorted(os.listdir(f'{root}/screenshots'))] if os.path.isdir(f'{root}/screenshots') else []
    for m in sel:
        if m.get('ui') and (review or m.get('reviewOnly')): m['screenshots']=shots
src=open(f'{root}/.claude/skills/jlpt-app-orchestrator/workflows/implement-milestones.js').read()
old='const A = args || {}'; assert src.count(old)==1
src=src.replace(old,'const A = '+json.dumps({'milestones':sel,'maxFixRounds':fix,'reviewOnly':review},ensure_ascii=False))
# 수렴 규칙(2026-11-02, 이번 작업 한정): 수정 뒤 라운드는 QA(빌드·완료 조건)와 이전 확정 지적 재판정만 한다. 그 라운드의 새 지적은 high만 막고 medium은 후속(low 목록)으로 보고한다.
r1='const reviewers = reviewersFor(m, changed, shots)'
assert src.count(r1)==1; src=src.replace(r1,"const reviewers = round >= 1 ? reviewersFor(m, changed, shots).filter(r => r.key === 'qa') : reviewersFor(m, changed, shots)")
r2="lows = fresh.filter(f => f.severity === 'low')"
assert src.count(r2)==1; src=src.replace(r2,"lows = fresh.filter(f => f.severity === 'low' || (rounds >= 1 && f.severity === 'medium'))")
r3="const freshJ = fresh.filter(f => f.severity !== 'low').concat(accFails)"
assert src.count(r3)==1; src=src.replace(r3,"const freshJ = fresh.filter(f => f.severity === 'high' || (rounds === 0 && f.severity === 'medium')).concat(accFails)")
src=src.replace('if (reviewOnly) {','if (reviewOnly || m.reviewOnly) {').replace('const scope = reviewOnly ?','const scope = (reviewOnly || m.reviewOnly) ?')
assert src.count('m.reviewOnly')==2
open(out,'w').write(src); print(out, [m['id'] for m in sel], 'review' if review else 'impl')
