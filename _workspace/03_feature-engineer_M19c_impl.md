# M19c 구현 요약 — S14 모의고사(시작 화면·진행·재개·대화상자)

verify.sh(인자 없음) 종료 코드 0, 로그 `_workspace/build/verify_20261107_091117.log`, 단위 테스트 402개 통과.

## 구현
- `ExamStartViewModel`(새 파일): 라우트 `ExamStart(level)`. 열 때 `examTemplate`·`pool`을 읽고 seed 하나로 `buildSet(excluded = {LISTENING})`. 영역 줄 `ExamSectionLine`, `total`, `limitMs`, `hasShortfall`. 시도 행은 "시작"에서만 생긴다(`starting` 가드, 성공하면 풀지 않음). 예외면 FAILED, "다시 시도"는 `load()`(새 seed). `startAttempt` 예외면 `startFailed`, 스낵바 "다시 시도"는 같은 seed·questionIds로 `startAttempt`만 다시 부른다.
- 공용 함수(`ExamStartViewModel.kt`): `ExamTemplate.examSections()`(parts가 모두 청해인 영역 제외), `limitMs()`, `indexOfKey`. S12 모의고사 카드도 `limitMs()`를 쓴다.
- `ExamStartScreen`: 영역 줄, 합계, 청해 제외 줄, 고지 카드, 시간 안내, 하단 "시작"(모자라면 비활성 + 이유, 합친 낭독). LOADING 자리표시, EMPTY, FAILED(다시 시도·연습으로).
- `QuizViewModel` EXAM 모드: 피드백 없이 선택만 저장하고 답을 바꿀 수 있음, `goTo`·`prev`·`next`, `requestSubmit`(미응답 > 0이면 CONFIRM_SUBMIT), `cancelSubmit`(첫 미응답 문항으로), `confirmSubmit`, `exit`(EXAM이면 CONFIRM_EXIT), `continueExam`, `stopAndExit`(saveProgress 뒤 onDone). 경과 시간은 `onStart`~`onStop`에만 주입한 Clock으로 더하고, `onStop`마다 saveProgress를 부른다. 제출 직전에 saveProgress로 걸린 시간을 남긴다. 기준 시간이 지나도 자동 제출하지 않는다. 템플릿에서 `limitMs`·`sectionStarts`를 만든다.
- 재개: 같은 seed로 다시 섞어 같은 선택지 순서를 만들고, 답은 `toShown`으로 되살린다. `current_index`는 **question_order 안 위치**로 저장·복원한다(빠진 문항을 가리키면 그 다음 남은 문항). 남은 문항이 0인 모의고사는 EMPTY로 열면서 finish한다.
- `QuizScreen`: EXAM일 때 상단 바(멈추기·경과 시간 mm:ss + "기준 {T}분 지남"·"제출" onClickLabel), 진행 줄의 현재 영역 이름, 문항 번호 이동 줄(`ExamComponents.kt`, 답함 = 채움 + check, 현재 = 2 dp primary 테두리 + 3 dp 막대, 영역 경계 16 dp + 영역 이름), 하단 "이전"·"다음/제출"(글꼴 200%에서 세로로 쌓음), 두 대화상자. Route가 ON_START·ON_STOP을 VM에 넘기고, 1초마다 `tick()`을 부른다.
- S12: `requestStartExam`(미완료가 있으면 `confirmReplace`), `resumeFromReplace`, `replaceWithNew`(finish·startAttempt 없이 ExamStart로), `cancelReplace`. 대화상자 버튼 셋은 세로로 쌓았다.
- AppRoot: `ExamStart` 화면을 연결했다. 시작하면 `Quiz(id)`로 가며 `popUpTo<ExamStart>(inclusive)`로 시작 화면을 백 스택에서 뺀다.
- 아이콘 `ic_ms_schedule`, 문자열 `exam_*`·`practice_replace_*`.

## 테스트
- `ExamViewModelTest`(19개): 완료 조건의 ViewModel 항목 전부(재개, ON_STOP/ON_START, CONFIRM_SUBMIT, CONFIRM_EXIT, 청해 제외, confirmReplace 연쇄, ExamStartUiState, FAILED·startFailed, limitMs·sectionStarts, 빠진 문항). 이 밖에 재개 문항 0, 저장 실패 중 이동 막힘, 제출 실패 뒤 재시도도 넣었다.
- `ExamScreenUiTest`(3개): 번호 칩(check·현재 막대·라벨, 칩을 누르면 이동), 타이머 낭독, 시작 비활성.
- `FakeQuiz`가 실제 저장소처럼 동작하게 했다: `startAttempt(examId)`가 다른 미완료 모의고사를 닫고, 끝난 시도의 saveProgress·finish는 무시한다. `QuizViewModelTest`의 "시험 시도 → EMPTY" 테스트는 EXAM ANSWERING으로 바꿨다.
- `DictShots.shots(dialog = true)`: 대화상자 창까지 담으려고 `captureScreenRoboImage`를 쓴다.

## 스크린샷
mock_exam_intro, _intro_font200(light·dark), _intro_shortfall, _intro_failed, _nav, _nav_font200(light·dark), _over_time, _confirm_exit, _confirm_submit, _resume_unavailable, practice_home_confirm_replace. 모두 light·dark로 찍었다. 기존 practice_* 스크린샷은 바뀌지 않았다.

## 명세와 다른 점
- 계약 밖 필드: `QuizUiState.finishFailed`(스낵바 문구를 "제출하지 못했습니다"로 고르는 데 씀), `QuizUiState.sectionTitles`(sectionStarts와 짝인 영역 이름).
- `QuizSessionLoader.content`를 public으로 바꿨다. QuizViewModel이 EXAM 템플릿을 읽는 데 쓴다.
- `current_index` 의미를 연습 시도에도 "question_order 안 위치"로 통일했다. 빠진 문항이 없으면 이전과 값이 같다.
- confirmReplace 두 번째 버튼 문구는 ui-design 7절의 "새로 시작"을 따랐다. 마일스톤 문구는 "포기하고 새로 시작"이다.
- 모의고사 진행 화면에는 후리가나 토글을 두지 않았다(명세의 S14 상단 바에 없음).
- FAILED는 상단 바를 남긴 채 본문에 StatusContent로 그린다(StatusScreen과 같은 구성).

## 확인하지 않은 것
- 실기기에서 백그라운드로 갔다 돌아올 때 시간이 멈추는지, TalkBack이 칩·타이머를 어떻게 읽는지(awaiting-device).
- 칩 위 영역 이름은 칩 폭을 늘리지 않고 오른쪽으로 넘쳐 그린다. 한 영역에 문항이 1개뿐이면 다음 영역 이름과 겹칠 수 있다.
