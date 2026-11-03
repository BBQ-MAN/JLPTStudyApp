# M24 구현 요약 (청해 대본 수집·검사)

D-04 결정 인용(docs/content/decisions.md): "A. 기기 TTS(ja-JP), `Speaker` 인터페이스 뒤 — 추가 라이선스·에셋 없음. 사전 생성 엔진의 조건은 미확인이라 채택하지 않음" (상태: 잠정 채택, 사용자 최종 검토 대기). 사전 생성 음성은 포함하지 않으므로 마일스톤 범위 안이다. architect 재계획 불필요.

## 변경
- content/pipeline/pl_listening.py (신규): 형식 검사(id, level, status, 출처 필드, type=9.2.2 listening 코드, lines/speaker/text/ttsText, question 선택, choices, answerIndex, explanationKo/whyWrongKo, targets)와 행 만들기. question 생략 -> NULL, ttsText 없으면 키 생략, sort_key = ID 끝 4자리, ko_source authored.
- pl_db.py: add_listening, listening 테이블 적재, ID 목록(content/ids/<kind>/listening.txt) 갱신. build.py authored 단계에 연결.
- check.py: authored 검사에 listening 포함, ids 소실 검사에 listening, refs(targets, answer_index).
- N-12 고지: pl_attr.py와 content/notices.ko.json에 이미 있어(T05b) 변경 없음. listening 행이 있으면 attribution.json notices에 N-12(title, body)가 들어간다. check.sh는 N-12 없는 경우와 에셋 음성 파일을 FAIL로 막는다(기존 검사, 테스트 추가).
- 샘플 대본 2개: content/authored/sample/listening/ls-n5-0001.json, ls-n5-0002.json (ids/sample/listening.txt).
- 테스트: test_listening.py(형식 14개, 적재, N-12, 오디오 금지, user_version 2·스키마 JSON 2개 그대로). test_check.py의 N-11/N-12 테스트는 listening 열 최소 구성과 N-12 제거로 맞춤.

## 스키마
v2 그대로. PRAGMA user_version 2, app/schemas 새 파일 없음. architecture.md와 다른 점 없음.

## 검증
verify.sh 종료 코드 0, 로그 _workspace/build/verify_20261103_130641.log.
주의: 작업 중 기본 `build.py`를 한 번 돌려 릴리스 에셋이 바뀌었으나 git checkout으로 되돌렸다(content/dist는 git 제외).
