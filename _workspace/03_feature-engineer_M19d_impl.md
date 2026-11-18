# M19d S15 결과 화면 구현 요약

- verify.sh 종료 코드 0, 로그 `_workspace/build/verify_20261107_093445.log`.
- 추가: `ui/practice/Percent.kt`, `QuizResultViewModel.kt`, `QuizResultScreen.kt`, `PracticeModels.kt`(QuizResultUiState 등), `nav/Routes.kt`(TargetLink.toRoute), AppRoot QuizResult 라우트.
- QuizViewModel의 문항 UI 변환을 `QuizContent.toUi(ShuffledQuestion)`로 빼 S13·S15가 공유. `Instruction`·`StemView`·`ExplanationCard`·`PassageCard`는 internal로 열어 해설 보기가 재사용.
- 계약 밖 필드(주석 표시): `QuizResultUiState.targetLinks`(해설을 열 때 해소한 관련 링크), `showRuby`(설정의 본문 후리가나, 토글은 두지 않음).
- 명세와 다른 점: 고지 카드는 ui-design 6.10 전체 문구를 쓴다(작업 지시의 고정 문구는 그 앞부분). 링크 해소 규칙은 jm:·av- = 어휘, gr- = 문법, 글자 하나 = 한자, 그 밖의 모양은 숨김.
- 해설 보기에는 청해 PlaybackControl·대본이 없다(M23에서 더한다). 프로세스 재시작 복원은 reviewIndex만(필터는 복원하지 않음).
- 스크린샷: screenshots/quiz_result*(라이트·다크, quiz_result·quiz_result_explanation은 _font200 포함).
- 실기기 확인(unverified): 한국어 TalkBack에서 영역별 점수·해설 일본어 구간 낭독.
