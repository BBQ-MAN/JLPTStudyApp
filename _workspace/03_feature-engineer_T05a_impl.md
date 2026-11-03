# T05a 구현 요약
- ContentEntities.kt에 엔티티 6개, ContentDatabase version 2, ContentDao에 count 6개.
- 2.json 내보냄(1.json 변경 없음). 파이프라인 코드 변경 없음, release kind로 build.py 재실행 -> content.db(user_version 2, 새 테이블 0행)·sha256·attribution.json 갱신(content_version 변경).
- 테스트: ContentSchemaV2Test(2.json 대조), ContentDbTest(count 6개 0), ChunkingTest 가짜 DAO에 count 추가.
- 명세와 다른 점 없음.
