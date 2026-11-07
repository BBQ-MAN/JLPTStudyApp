# M20 구현 요약 (파이프라인: 문항·모의고사 템플릿)

- 새 `content/pipeline/pl_question.py`: 형식 검사(9.2.2), question·exam_template 행 만들기(9.2.3). 공용 출처 필드 검사는 `pl_grammar.common_problems`로 뺐다(문법 동작 그대로).
- `pl_db.py`: `add_questions`(targets는 이 빌드의 vocab·grammar·kanji ID), question·exam_template 적재, `content/ids/<kind>/question.txt` 갱신. `build.py` authored 단계, `check.py`(ID 목록, 파일 형식, DB의 question targets·answer_index) 연결.
- N-11: 기존 `pl_attr`가 grammar 또는 question 행이면 넣는다(M15 로직 재사용). 문항만 있는 합성 빌드 테스트 추가.
- 샘플: `content/authored/sample/questions/` 4개, `sample/exams/N5.json`, `content/ids/sample/question.txt`. 샘플 시험 템플릿의 문항 수는 샘플용 값이며 jlpt.jp 안내에서 확인한 값이 아니다(uncertain, M21에서 실제 값 작성).
- 테스트 `test_question.py`; `test_grammar.py`는 어휘·문법을 바꾸는 테스트에서 샘플 문항을 지우도록 고침(targets 때문).
- 스키마 v2 그대로(user_version 2, 새 스키마 JSON 없음). 릴리스 `content.db`는 문항 파일이 없어 다시 만들지 않았다(바이트 동일).
- 한계: 선택지 "모두 맞음/정답 없음" 판정은 정규식 목록(일본어·한국어 대표 표현)이다.
- verify.sh: PASS, 로그 `_workspace/build/verify_20261103_124941.log`
