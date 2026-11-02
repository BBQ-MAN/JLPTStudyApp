#!/usr/bin/env bash
# 사용: wfwait.sh <runId> <taskId> [초]  — 워크플로가 끝나거나 시간이 다 될 때까지 기다리고 진행 요약을 출력한다.
R=$1; T=$2; S=${3:-570}
D=/Users/bbqman/.claude/projects/-Users-bbqman-HermesAgents-ETC-JLPTStudyApp/0b7bcf04-7b73-4104-a3b9-4fcf6ce1cf77/subagents/workflows/$R/journal.jsonl
O=/private/tmp/claude-501/-Users-bbqman-HermesAgents-ETC-JLPTStudyApp/0b7bcf04-7b73-4104-a3b9-4fcf6ce1cf77/tasks/$T.output
end=$((SECONDS+S)); while [ $SECONDS -lt $end ]; do [ -s "$O" ] && break; sleep 20; done
date "+%H:%M"
python3 -I - "$D" <<'PY'
import json,sys,collections
c=collections.Counter(); last=[]
for l in open(sys.argv[1]):
    j=json.loads(l); c[(j.get('type'),j.get('phase'))]+=1
    if j.get('type')=='started': last.append(j.get('label'))
print(dict(c)); print('최근:', last[-4:])
PY
grep -o 'M[0-9A-Z-]* 라운드[^"\\]*' "$D" | tail -4
[ -s "$O" ] && echo DONE || echo RUNNING
