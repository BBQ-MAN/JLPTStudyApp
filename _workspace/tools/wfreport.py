# 사용: python3 -I wfreport.py <taskId>  — implement-milestones 결과를 마일스톤별로 요약한다.
import json,sys
o=json.load(open(f"/private/tmp/claude-501/-Users-bbqman-HermesAgents-ETC-JLPTStudyApp/{sys.argv[2] if len(sys.argv)>2 else "dfa294f8-6757-4357-aed3-794c642a65e7"}/tasks/{sys.argv[1]}.output"))
r=o['result']; r=json.loads(r) if isinstance(r,str) else r
print(o.get('logs',[])[-3:])
if 'error' in r: print('ERROR',r['error'])
for m in r.get('report',[]):
    acc=m.get('acceptance',[]); c={k:sum(a['result']==k for a in acc) for k in ('pass','fail','unverified')}
    print(f"\n## {m['id']} {m['status']} rounds={m.get('rounds')} verify={m.get('verifyPassed')} qa={m.get('qaVerifyPassed')} missing={m.get('missingReviewers')} acc={c}")
    for k in ('residual','uncertain','rejected'):
        for f in m.get(k,[]) or []: print(f"  [{k}] {f.get('severity','')} {f.get('title','')[:160]}")
    print(f"  low {len(m.get('lowFindings',[]))}건; shots {len(m.get('screenshots',[]))}")
    for a in acc:
        if a['result']!='pass': print('  acc',a['result'],a['criterion'][:120],'|',(a.get('note') or '')[:200])
    print('  요약:',(m.get('summary') or '')[:400])
