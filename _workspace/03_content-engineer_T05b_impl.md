# T05b 구현 요약
- 파이프라인은 이미 최대 번호 스키마(2.json)로 빈 테이블 6개를 만든다. 빌드 2회 sha256 동일(724f58f7…), content.db.sha256과 일치.
- check.py: `check_refs` 추가(kanji_stroke.literal ⊂ kanji, grammar_example.grammar_id ⊂ grammar). 출처 열 P 검사는 기존 `check_meanings`가 행이 있을 때만 적용.
- 9.0 JSON 직렬화는 `pl_common.jdump` 하나(test_common.py).
- 테스트 픽스처를 2.json 기준으로 바꿈(support.py), v1 에셋·ko_method 빈 값·참조 위반 테스트 추가. 84개 통과.
- 스키마 불일치 없음. 생략: attribution.json은 사람이 읽는 들여쓰기 형식이라 jdump 대상 아님(9.0은 *_json 열 규칙).
