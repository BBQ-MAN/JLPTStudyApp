#!/usr/bin/env bash
# 콘텐츠 검사(content-pipeline 5절, architecture.md 6.5). 네트워크·content/raw 없이 커밋된 파일만으로 돈다.
# 실패마다 'FAIL: <검사> <이유>' 한 줄, 하나라도 있으면 종료 코드 1.
cd "$(dirname "$0")/../.." || exit 2
rc=0
python3 -I -m unittest discover -s content/pipeline -q >/tmp/jlpt_unittest.$$ 2>&1 || { echo "FAIL: unit-tests python3 -I -m unittest discover -s content/pipeline"; tail -40 /tmp/jlpt_unittest.$$; rc=1; }
rm -f /tmp/jlpt_unittest.$$
python3 -I content/pipeline/check.py || rc=1
exit $rc
