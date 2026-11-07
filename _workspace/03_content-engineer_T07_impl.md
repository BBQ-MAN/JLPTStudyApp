# T07 구현 요약 (콘텐츠 검수 도구)
- 새 모듈 content/pipeline/pl_review.py, build.py에 review-sample/review-sheet/review-apply 하위 명령과 authored 단계 유사·중복 검사(WARN은 출력만, FAIL authored duplicate은 단계 실패) 연결.
- 테스트 content/pipeline/test_review.py(6개). 전체 86개 통과.
- ko-gloss 행에는 written/reading이 없어 시트 ja 열은 행에 있으면 그 값, 없으면 vocabId다(스냅샷 조회는 안 함).
- verdict=fix 행도 verdict·reviewer가 있으면 reviewed로 표시한다(6.8 문구 그대로). 원문은 안 바꾸고 -fix.tsv에 남김. 이를 막으려면 6.8 변경 필요.
- --batch-sampled는 시트에 적용된 reviewer가 한 종류일 때만 동작(그 값이 표본의 reviewerKind).
- content/ko-gloss·content/authored 무변경. 스크린샷 _workspace/review/ko-gloss_N5-20261103.png
