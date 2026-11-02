# M10 content-engineer 요약
- content/pipeline/config.json: kind=release, levelSource=S-06, levels=[5], milestone=M10 (파이프라인 코드는 변경 없음, M8까지 구현된 release 경로 사용).
- python3 -I content/pipeline/build.py 전체 통과: vocab 667, kanji 424, 뜻 없는 vocab 0, 버전 v92c85a587ee4, content.db 434176 B(<2 MiB).
- content/raw를 치우고 두 번 빌드: content.db sha256 동일 8c524cbf...3d044c.
- 산출물: app/src/main/assets/content/{content.db,attribution.json,content.db.sha256}, app/src/main/res/values/attribution_fallback.xml, content/dist/jlpt-content-v92c85a587ee4.zip(content.db, attribution.json, LICENSE.md). 이전 sample 판 zip 3개는 삭제.
- content_meta: kind release, levels 5, level_source S-06, sources에 S-01·S-02·S-06 판·파일 sha256·스냅샷 sha256.
- ContentDbTest(Robolectric)를 kind release 기대로 수정.
- architecture.md와 다른 점: 완료 조건은 content/ids/vocab.txt라 적었으나 구현은 content/ids/<kind>/ (release/vocab.txt, kanji.txt)이고 check가 그 경로를 읽음. 경로 변경 없이 유지.
- 한국어 뜻은 전부 reviewStatus=unreviewed(AI 번역), attribution.json N-10에 밝힘. N-09 '공식 자료가 아닙니다' + 급수 목록 이름 포함.
- verify.sh 종료 코드 0, 로그 _workspace/build/verify_20261102_093739.log

## 후속 수정: content_version 입력 해시 (medium)
- content/pipeline/pl_db.py release_dataset: level-overrides.tsv만 해시에 넣던 것을 reading-overrides.tsv·written-overrides.tsv까지 확장(build 입력인 authored 파일 전부; vocab/*.jsonl, ko-gloss, 스냅샷은 이미 포함).
- content/pipeline/test_release.py: test_authored_override_files_change_content_version 추가(덮어쓰기 파일에 한 줄 추가하면 content_version이 달라짐).
- 새 content_version v0fbcbcedfd73, content.db sha256 99dcfcfa04c72e41962048fa42e251b5193c51fe592a8dbdb4bca06b8fec6e2e (content/raw 없이 두 번 빌드 동일).
- unittest 80개 OK, verify.sh 종료 코드 0 (_workspace/build/verify_20261102_100144.log).
- content/dist에 이전 판 zip 3개가 남아 있음(생성물, 삭제하지 않음).
