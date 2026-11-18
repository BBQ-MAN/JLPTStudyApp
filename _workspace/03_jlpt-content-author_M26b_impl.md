# M26b 구현 요약 — N4 표본 검수와 릴리스 빌드

- 작업 트리는 M13(필순) 산출물이 커밋 안 된 채 있는 상태였고 그 위에서 작업함. config.json milestone M13 -> M26b.
- 표본 검수: seed 20261107, ko-gloss/N4 630행 중 63행(ceil 63). 오류 1(ご存じ), 오류율 1.6% <= 5%이므로 재생성 없음. 결과: reviewed 63 / sampled 567, 전부 reviewerKind agent, reviewedAt 2026-11-07. 상세: `_workspace/03_jlpt-content-author_M26b_review.md`
- check.py: `check_review_gate`(release에서 level<=4 vocab_meaning이 unreviewed면 `FAIL: review-gate ...`). 테스트: test_release `test_review_gate_blocks_unreviewed_n4_in_release`(unreviewed -> FAIL, sampled·reviewed -> 통과, N5 unreviewed는 대상 아님), `test_committed_ko_gloss_n4`를 검수 후 상태(human 없음, 표본 >= ceil(10%), ids 파일 일치)로 갱신. 합성 픽스처(write_gloss, test_strokes)는 N4를 sampled로 씀.
- 릴리스 빌드(`content/raw` 치운 채 `python3 -I content/pipeline/build.py`): 1297 vocab(N4 630, N5 667), 727 kanji, check OK. 급수 목록 N4 640행 중 630 채택(98.4%). 사라진 ID: vocab 0, kanji 0. content.db 1,691,648 B (한도 12,582,912). 사람 표시(human) 뜻 행 0.
- 참고: 과제문의 "N4 576행"과 달리 실제 N4 gloss는 630행이라 표본은 63행이다.
- verify.sh 종료 코드 0, 로그 `_workspace/build/verify_20261107_093056.log`.
