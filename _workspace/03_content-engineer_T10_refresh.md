# T10 EDRDG 최신판 재빌드 연습 (2026-11-07)

임시 worktree `/tmp/jlpt-t10`(detach HEAD c5b0fa2)에서만 작업했다. 메인 체크아웃·content worktree의 소스·스냅샷·에셋은 고치지 않았고 커밋하지 않았다. 새 스냅샷은 버려진다.

## 1. 새 판 (공식 EDRDG URL, 매니페스트에 있는 S-01·S-02만)
| 출처 | 현재 매니페스트 | 새 판 | 새 파일 sha256 |
|------|----------------|-------|----------------|
| S-01 JMdict_e.gz | 2026-11-01 (cda250dc...2829) | `JMdict created: 2026-11-06` (Last-Modified 2026-11-06 03:30 GMT) -> version `2026-11-06` | 5af39046e9579fd5e14d07a048fccce422b8df0d940ba8c086439e5a7617eab6 |
| S-02 kanjidic2.xml.gz | 2026-305 (2026-11-01) (68c2e554...8d1e) | database_version 2026-310, date_of_creation 2026-11-06 -> version `2026-310 (2026-11-06)` | 0ed6f22aebeb1f9cef9b69f2f05407822ca69ed6a8be86fabb74c25eabcfe8fd |

refresh-snapshot은 매니페스트 sha256과 새 파일이 다르면 `sha256 mismatch`로 멈춘다(설계대로). 연습을 이어가려고 임시 worktree의 매니페스트 사본에서만 S-01·S-02의 version·sha256 4줄을 바꿨다. 이 변경은 버렸다.
스냅샷 사본 sha256: S-01-2026-11-06.jsonl.gz b85c5d95...d8f9, S-02-2026-310_2026-11-06.jsonl.gz a260acbd...2a36 (S-02는 이전 스냅샷과 바이트 동일).

## 2. 변경 집계 (실데이터)
- JMdict 원본 전체: seq 219,002 -> 219,028. 사라진 seq 0, 새 seq 26. 우리 급수 목록과 무관.
- 스냅샷(7,743 항목) 대비: 항목 내용이 바뀐 것 0. S-02 한자 2,896 전부 동일.
- 앱 DB(release, N5·N4): vocab 1,297 / vocab_meaning 3,081 / kanji 727 / kanji_stroke 727 / vocab_kanji 1,498 / grammar·question·listening 등 전 테이블 행 내용 이전과 동일. 달라진 것은 content_meta(content_version `vfcb49796cb02` -> `v74a714c68973`, sources의 version·스냅샷 해시)와 attribution.json·LICENSE.md의 판 문자열뿐.
- **사라진 ID 0, 새 ID 0, 바뀐 행 0.**
- retired 행 수 0 (`SELECT count(*) FROM retired` = 0). 이전 content/ids/release 목록 대비 사라진 ID 0. 0 == 0 (acceptance 2 충족).
- stale gloss 0 (`_workspace/03_content-engineer_T10_stale_gloss.tsv`, 머리말만 있고 행 0). 미매칭(unmatched) 22행은 이전과 동일.
- 참고: 이전 스냅샷은 7,745항목이었고 새 스냅샷은 7,743이다. 빠진 2건은 seq 2524270(こと 조사)·2607730(もし 감동사)로, JMdict에 그대로 있다(양쪽 원본에서 확인). level-overrides.tsv가 이 seq를 교정 대상에서 뺀 결과이며 JMdict 변경이 아니다. 두 항목은 DB에 없다.
- 판만 바뀌어도 content_version이 바뀐다(입력 스냅샷 해시를 섞음). 행이 같아도 앱은 새 콘텐츠 판으로 본다.

## 3. retired 경로 합성 검증 (실데이터에는 사라진 ID가 없었으므로)
임시 worktree에서 새 스냅샷의 S-01에서 DB에 있는 실제 vocab 2건(jm:1000320, jm:1000430)을 지워 사라진 ID를 만들었다.
1. 기본 빌드 -> `FAIL: ids jm:1000320 lost (2 total; add to content/ids/release/retired.tsv to retire)` 로 멈춘다(사람이 retired.tsv에 올리기 전에는 진행 불가).
2. retired.tsv에 2줄 추가 후 빌드 -> vocab 1,295, `SELECT count(*) FROM retired` = 2 == 사라진 ID 2, retired 행 (jm:1000320, jm:1000430), check OK.
3. 이후 합성 변경은 전부 되돌려 실제 새 판 상태로 다시 빌드했고, DB가 바이트 동일(`cmp`)하게 재현됨을 확인했다.

## 4. :app 테스트 (합성 retired 2행이 든 새 에셋으로, `./gradlew --offline :app:testDebugUnitTest`)
SessionCardsTest 2, ReviewViewModelTest 17(retired 대기열 제외·card_state·review_log 유지 테스트 포함), ContentSchemaV2Test 2, HomeViewModelTest 8, DetailViewModelTest 4, ChunkingTest 2 = 35건 통과, 실패 0.
주의: SessionCards·ReviewViewModel의 retired 테스트는 FakeContent 픽스처를 쓰며 에셋 DB를 읽지 않는다. 새 에셋과 맞물리는 부분은 ContentSchemaV2Test(스키마·retired 테이블 존재)다. 아직 학습 기록이 있는 실제 ID가 retired가 되는 end-to-end 검사는 없다.

## 5. 완료 조건 확인
1. 보고서 항목: 위 1·2절. 충족.
2. retired 수 == 사라진 ID 수: 실데이터 0 == 0, 합성 2 == 2. 충족.
3. 앱 retired 테스트: 위 4절. 통과.
4. content/raw 없이(`content/raw`를 치우고) `python3 -I content/pipeline/build.py` 와 `check.sh` 통과, `verify.sh` 종료 코드 0(임시 worktree의 새 스냅샷 상태에서: `PASS: content/pipeline/check.sh + test lintDebug assembleDebug`).

## 6. analyst에게 요청 (새 판을 채택할 때)
docs/content/manifest.json S-01·S-02 갱신: S-01 version `2026-11-06`, sha256 `5af39046e9579fd5e14d07a048fccce422b8df0d940ba8c086439e5a7617eab6`; S-02 version `2026-310 (2026-11-06)`, sha256 `0ed6f22aebeb1f9cef9b69f2f05407822ca69ed6a8be86fabb74c25eabcfe8fd`, checkedAt 갱신. 그 뒤 refresh-snapshot -> 스냅샷 커밋(사용자 지시 후).
이번 새 판은 급수 목록 항목에 영향이 없으므로 채택해도 얻는 것은 판 문자열 갱신뿐이다. EDRDG는 매일 다시 생성하므로 채택 시점의 sha256이 위 값과 다를 수 있다(그때 받은 파일로 다시 계산).
