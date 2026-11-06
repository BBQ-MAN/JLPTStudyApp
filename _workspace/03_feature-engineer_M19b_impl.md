# M19b 구현 요약
- 라우트: Practice(탭), Quiz(attemptId), QuizResult(attemptId, 빈 화면 M19d), ExamStart(level, 빈 화면 M19c). 연습 탭은 홈·사전·문법·연습·설정 순.
- ui/practice: PracticeConfig, QuizTypeLabels(이름·지시문·후리가나 끔 규칙), QuizStem.split, PracticeModels, PracticeHomeViewModel/Screen(S12), QuizViewModel/Screen(S13, ChoiceButton).
- 문자열 리소스: practice_*, quiz_*. 드로어블 ic_ms_cancel.
- 테스트: PracticeHomeViewModelTest, QuizViewModelTest, PracticeScreenUiTest, QuizTypeLabelsTest, PracticeScreenshotTest(+ScreenshotTest nav_shell_all_tabs). 스크린샷 practice_* 와 nav_shell_* 갱신.
## 명세와 다른 점
- 연습 탭은 문항이 번들에 없어도 숨기지 않는다(문법 탭과 같음). S12가 EMPTY를 그린다.
- 청해 영역·유형은 M23까지 S12에서 뺀다(pool에서 LISTENING 제외).
- 계약 밖 필드: PracticeHomeUiState.abandonFailed(포기 finish 예외 스낵바), QuizUiState.showRuby.
- S13: 긴 지문 탭("지문"/"문제")과 관련 단어·문법 링크(targets)는 M19d로 미룸. 이어서 풀기는 Quiz(attemptId)로만 이동하며 EXAM 시도는 LOADING에 머문다(M19c). 시작 버튼은 ExamStart로 가며 confirmReplace는 M19c.
- 해설 카드 정답 근거 `explanationKo` 첫 문단을 MeaningUi.textKo로 쓰는 M19a 변환을 그대로 둠.
## 실기기 확인(unverified)
- 한국어 TalkBack에서 지시문 일본어 구간·선택지 일본어가 일본어 음성으로 낭독되는지.

## 리뷰 지적 수정 (_workspace/review_M19ab_findings.json, 19건)

verify.sh(인자 없음) 종료 코드 0, 로그 `_workspace/build/verify_20261107_085315.log`, 단위 테스트 369개 통과. 스키마 JSON·의존성 변경 없음.

고친 것
- #13 high: `QuizViewModel.exit`에 `exiting` 가드. 닫기와 뒤로를 연달아 눌러도 onDone·finish는 한 번. 회귀 테스트 `닫기와 뒤로를 연달아 눌러도...`.
- #14 medium: `startPractice`에 `starting` 가드. 성공하면 화면이 돌아올 때(`reload`)까지, 실패하면 즉시 풀린다. 회귀 테스트 `유형 행을 더블탭해도...`(기존 두 번 시작 테스트는 사이에 reload 추가).
- #15 medium: `refresh`를 Mutex로 직렬화(init·ON_START 겹침). 풀은 급수마다 한 번만 읽는다. 회귀 테스트(`poolGate`로 겹침 재현).
- #5 medium: `QuizBody`가 문항 id가 바뀔 때 진행 표시("3/10")로 포커스를 보낸다(`focusRequester` + `focusable`). UI 테스트 `다음 문항으로 바뀌면 접근성 포커스가...`.
- #0 finishAttempt, #1 saveAnswer·saveProgress: `WHERE finished_at_epoch_ms IS NULL`, `upsertAnswer`는 끝난 시도면 무시(없는 시도는 기존대로 FK 예외). `insertAnswer`를 분리(ChunkingTest의 가짜 DAO 갱신). 테스트 2개.
- #2 contents: ID 접두사 분기를 없애고 두 테이블을 모두 조회(청해 쿼리 호출이 늘어 QuizContentConversionTest 합계 4000 -> 6000).
- #3 pool: `questionPool`/`listeningPool`이 choices_json·answer_index도 읽고 contents가 버릴 행을 풀에서 뺀다(`decodeChoices` 공용). QuizContentDbTest 기대값 갱신.
- #4: QuizRepositoryTest 완료 조건 1을 픽스처 content DB 문항에서 시작하게 바꿈(answer_index 이동까지 확인).
- #6 onClickLabel(`practice_type_start`), #8 `quiz_choice_reading` 리소스, #9 자세히 버튼 contentDescription("출처·라이선스 자세히 보기", 보이는 글자를 포함), #10 경과 시간 contentDescription("22분 5초"), #17 mmss `Locale.ROOT`, #11 진행 막대는 `clearAndSetSemantics {}`로 숨기고 저장 중 막대는 "저장 중"으로 이름 부여.
- #7·#18: 채점 뒤·저장 중 선택지는 `selectable(enabled=false)` 대신 Role·Selected만 남는 노드로 바꿨다(비활성 낭독 제거). 저장 실패 뒤 다른 선택지로 바꾸는 길이 열려 `choose`에 `chosenIndex != null` 가드를 더함.
- #16: 시험 모드 시도를 열면 LOADING 대신 EMPTY(닫기 동선 있는 화면)로 간다. M19c에서 시험 풀이로 교체한다.

건너뛴 것
- #11 일부(진행 막대 트랙색을 outlineVariant 등으로): 색 토큰은 디자인 명세 소관이고 채움 부분은 보여 1.4.11 경계 사례라 ui-designer 판단으로 남긴다.
- #12: 실기기 TalkBack(ja-JP) 낭독은 unverified(awaiting-device). 11.1 체크리스트에 passage·stem·해설 카드 항목을 더하는 일은 ui-designer 영역.

Roborazzi 참고: `verifyRoborazziDebug`는 실행마다 서로 다른 DictScreenshotTest(필순 애니메이션)·ReviewScreenshotTest 몇 개가 실패한다(두 번 실행해 실패 목록이 달랐다). 이번에 고친 파일과 무관한 비결정성이며 연습 화면 스크린샷은 한 번도 실패하지 않았다. 스크린샷은 갱신하지 않았다.
