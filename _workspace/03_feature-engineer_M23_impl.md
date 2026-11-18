# M23 청해 화면(S16)·S12 청해 진입 구현 요약

- 스키마 변경 없음: ContentDatabase 2.json·StudyDatabase 2.json 그대로(`git diff --exit-code app/schemas` 0).
- 추가: `ui/listening/ScriptSpeech.kt`(SpeechLine을 만드는 앱 코드 유일한 곳), `ListeningModels.kt`(ListeningUiState·ListeningStatus), `ListeningViewModel.kt`(M22 뼈대를 채움), `ListeningScreen.kt`(S16 화면·PlaybackControl·대본 패널·N-12 줄), `res/drawable/ic_ms_volume_up.xml`, `nav` 라우트 `Listening(attemptId)`.
- 변경: S12(`PracticeHomeViewModel`이 청해 풀을 포함 → 청해 영역·유형 행, 행은 `Listening(attemptId)`로), `QuizQuestionUi.lines`/`ListeningLineUi`, `QuizContent.toUi`가 대본 줄을 채움, `QuizTypeLabels.instruction` 청해 6유형 지시문, 설정 "청해 대본 먼저 보기"(`hasListening()`이 참일 때만 보임), `QuizContentRepository.hasListening()`(기본 false, Room은 `countListening() > 0`).
- 동작: 자동 재생 없음, TalkBack 켜짐이면 재생 뒤 1,000 ms(`TalkBackSpeakDelayMs`, 코루틴 delay)에 speak(그 사이 화면은 "재생 중"), ON_START에서 MissingJapaneseData·NoEngine·Error면 shutdown 후 팩토리로 새로 만듦(Ready·Initializing은 그대로, ON_RESUME 미사용), 뒤로·ON_STOP·컴포지션 이탈·onCleared에서 stop. 답하기 전에 대본을 한 번이라도 펼쳤거나 처음부터 펼쳤으면(listening_script_first, NoEngine) script_shown = 1. 결과는 M19d S15 그대로(`scriptShown` → "대본으로 풂").
- 명세와 다른 점(계약 보완): `QuizQuestionUi.lines`는 계약 위치(passage 뒤) 대신 끝에 기본값 null로 둠(기존 위치 인자 호출 보존). `ListeningUiState`에 계약 밖 필드 `showRuby`·`finishFailed`. `ListeningViewModel` 생성자는 (attemptId, loader, quiz, study, attribution, clock, speakerFactory, touchExploration). 설치·설정 인텐트는 화면(Route)이 `startExternal`로 열고 결과를 `externalOpened(ok)`로 알림. 속도 칩·"처음부터"는 Ready·Playing에서만 보임. NoEngine의 "대본 보기"는 접지 않고 펼치기만 함.
- 청해 audioNotice는 attribution.json notices에서 id == "N-12"를 찾음(번들 attribution.json에는 아직 없음 → M24 전까지 줄 숨김).
- 번들 에셋의 listening 테이블은 비어 있음(S12에 청해 유형 없음). 콘텐츠는 코드에 쓰지 않았고 테스트 픽스처는 합성 문장이다.
- 스크린샷: `screenshots/listening_{ready,playing,no_engine,script_open,missing_voice,error}_{light,dark}.png`(12장). 명세 12절의 그 밖의 상태(initializing, feedback, external_open_failed, missing_voice/feedback `_font200`, quiz result_listening, settings with_listening)는 이번 작업 범위 밖이라 찍지 않았다.
- 실기기 확인(unverified, 개발 종료 후 일괄): TalkBack이 대본 일본어 구간을 일본어로 읽는다. TalkBack 켬 상태에서 재생 버튼 뒤 약 1초 늦게 음성이 시작해 '재생 중' 알림과 겹치지 않는다.
