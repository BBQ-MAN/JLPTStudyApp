# M15 구현 요약 (콘텐츠 엔지니어)

- 새 모듈 `content/pipeline/pl_grammar.py`: 형식 검사(공통 필드, ID 형식·중복·파일명 일치, 예문 3개 이상, 루비 `{…|…}`·`[[…]]` 짝, connections 세 필드, contrasts 대상 ID·자기 자신·pair 2개, sources·예문 source의 매니페스트 confirmed, 예문 authored/S-04 열 규칙, 한국어 필드 금지 기호)와 DB 행 만들기(sort_key = floor(i×V/G), V=0이면 i, retired는 제외).
- `pl_db.py`: sample은 `content/authored/sample/grammar/*.json`, release는 `content/authored/grammar/N<급>/*.json`을 읽어 `grammar`·`grammar_example`(ord 0부터)에 적재. 문법 ID 목록 `content/ids/<kind>/grammar.txt`는 문법 행이 있을 때만 갱신. 스키마·user_version(2) 변경 없음, `app/schemas` 새 파일 없음.
- `pl_attr.py`: grammar 행이 있으면 N-11(기존) + N-09 body 앞에 `notices.N-09.grammarPrefix` + 공백. 키가 없으면 빌드 FAIL. 문법 행이 없으면 붙이지 않음.
- `check.py`: authored 검사에 문법 추가, `ids/grammar.txt` 안정성, `grammar_example` 출처 열 규칙(authored면 ref·author·license NULL, 아니면 전부 NOT NULL), N-09가 grammarPrefix로 시작하는지.
- 샘플 문법 2개 `content/authored/sample/grammar/gr-n5-0001.json`(〜てください), `gr-n5-0002.json`(〜ましょう), 둘 다 llm 작성·unreviewed·needsReview. ID는 `content/ids/sample/grammar.txt`.
- 테스트 `content/pipeline/test_grammar.py`(25개). `test_check.py`의 문법 가정 2곳 수정(샘플 DB에 이제 문법 행이 있음).

## 다른 문서와 다른 점 / 요청
- `content/notices.ko.json`에 `notices.N-09.grammarPrefix`를 추가했다. 문구는 sources.md N-09.not-official에 적힌 문장 그대로("문법 항목의 급수는 공개된 목록이 아니라 이 앱 작성자가 판단해 정한 것입니다(화면 표시 '비공식').", analyst 확인 필요).
- 본문과 접두 문장 사이는 공백 한 칸(ui-design 9.2 표에 구분자 명시 없음).
- 릴리스 에셋(content.db 등)은 문법 파일이 없어 재생성하지 않았다(커밋본 그대로). 릴리스 grammar.txt는 첫 릴리스 문법 적재 때 생긴다.

## 한국어 필드의 일본어 구간(샘플 문법)
없음 — 샘플 2개의 한국어 필드(meaningKo, explanationKo, posKo, 예문 ko, pair ko)에 일본어 구간이 있는 것은 기대 범위: 「てください」 류는 쓰지 않았고, 보고서 파일은 빌드 때 `_workspace/03_content-engineer_<M>_ko_spans_authored.tsv`로 생성된다. 샘플 빌드의 목록은 `ます`/`ましょう` 등 `explanationKo`에 나오는 일본어(て형, ください, ます형, ましょう)이다.
