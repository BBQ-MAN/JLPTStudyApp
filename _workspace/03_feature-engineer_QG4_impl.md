# QG4 구현 보고 — 자동 생성 연습 화면(S12 진입점·S13/S15 표시)

날짜 2026-11-08 · worktree JLPTStudyApp-wt/quizgen(dev/quizgen) · 커밋 안 함

## 결과
- verify.sh 종료 코드 0(`_workspace/build/verify_20261108_081158.log`), `./gradlew verifyRoborazziDebug` 통과, `git diff --exit-code app/schemas` 0.
- 생성 로직·저장 스키마·`:core:quiz` 변경 없음.
- TDD: 테스트(`PracticeHomeGeneratedTest`, `GeneratedScreenUiTest`, `QuizTypeLabelsTest` 추가분)를 구현 전에 썼다. 다만 구현 전에 "컴파일 실패"를 따로 실행해 확인하지는 않았다(쓴 순서만 지킴, 심볼이 없어 컴파일이 안 되는 상태였음).

## 만든 것
- `PracticeConfig.GEN_MIN_TARGETS = 10`, `GeneratedTypeUi`, `PracticeHomeUiState.generated`.
- `PracticeHomeViewModel(…, gen: QuizGenRepository? = null)`: 급수별 `availability`를 한 번 읽어 캐시(은행 캐시도 같이 데워짐). `levels` = 고정 문항 급수 ∪ 활성 유형이 있는 급수, `EMPTY` = `types.isEmpty() && generated.none{enabled}`. `startGenerated(type, onStarted)`: `startPractice`와 같은 `starting` 잠금, `newSession` 빈 목록이면 `startFailed`(시도 안 만듦), `startAttempt(null, seed, ids, now)`. 모르는 유형 코드는 무시.
- S12 `GeneratedSection`(머리 heading + 설명 + 행 4개): 비활성은 `clickable(enabled=false)`(Disabled 시맨틱) + 이유 글자, 낭독 "{유형}, 자동 생성, 단어 {n}개[, 사용할 수 없음, {이유}]"(`clearAndSetSemantics`).
- `QuizTypeLabels`: 뜻 고르기·단어 고르기 이름·지시문, `koreanChoices`·`koreanStem`.
- S13·S15: `GeneratedLabel`(Box+Text, 테두리, 동작·Role 없음, 포커스 받음; 문항이 바뀌면 포커스는 라벨), `QuestionStem`(한국어 지문 titleLarge + `JpSpans.annotate`), `ChoiceButton(korean=)`(bodyLarge + `JpSpans.annotate`, 낭독도 ko-KR + 일본어 구간 ja-JP), `FeedbackLine` 정답 낭독 로캘, `ExplanationCard(onRelatedWord)`의 "관련 단어 보기" 행(S13: `QuizActions.onWord` → AppRoot `VocabDetail(id)`, S15: `targetLinks`의 VOCAB 링크 → 기존 `onTarget`), 칩·"검토 중" 둘 다 없으면 칩 줄 생략.
- S15 제목: `items[0].question.generated`이면 "자동 생성 · …", 낭독은 contentDescription "자동 생성, …".
- `DictShots.shots(lightOnly=)` 인자 추가(font200을 light만 찍기 위함, 기존 호출 영향 없음).

## 시작 지연 측정
`QuizGenLatencyTest`(환경 변수 `QUIZGEN_LATENCY_OUT`이 있을 때만 실행, verify에서는 skipped). Robolectric JVM·번들 content.db, 급수마다 새 저장소로 3회:
| 단계 | N5 | N4 | N3~N1 |
|---|---|---|---|
| 은행 만들기+availability | 콜드 90 ms, 이후 15~19 ms | 19~23 ms | 3~8 ms(빈 급수) |
| newSession(10문항) | 1~11 ms | 1~3 ms | 0 |
| contents(10) | 7~12 ms | 10~17 ms | 0 |
Robolectric 값이라 실기기는 느릴 수 있다(참고용, 실기기 수치 `unverified`). 판단: `starting` 상태를 추가하지 않았다. 은행 만들기는 S12 로딩(`refresh`, 기존 LOADING 뼈대가 보임)에서 availability와 함께 끝나므로 행을 누를 때는 캐시된 은행으로 `newSession`만 돈다(콜드에도 11 ms 이하). 실기기에서 S12 로딩이 길면 그때 `availability`를 고정 문항 표시 뒤로 미룬다(미루면 급수 칩이 뒤늦게 늘어남).

## 스크린샷(새 9개, `screenshots/`)
practice_home_generated_{light,dark}, practice_home_generated_light_font200, quiz_generated_kanji_reading_{light,dark}, quiz_generated_meaning_ja_ko_{light,dark}, quiz_result_generated_{light,dark}. 직접 열어 확인: 라벨이 지시문 위, 한국어 선택지·일본어 구간 정상, 결과 제목 접두, 일본어 글리프 정상. 기존 스크린샷은 하나도 바뀌지 않았다(verify 통과, 기존 PNG 덮어쓰기 없음).

## 설계와 다르게 한 점·해석
1. `generated`는 "그 급수의 4유형 대상이 모두 0이면" 빈 목록(영역 숨김)으로 판정한다(spec의 "생성 표가 없는 DB"를 대상 수 0으로 구현). 4행이 모두 "단어 0개"인 화면은 나오지 않는다.
2. S13의 "관련 단어 보기"는 `retired` 확인을 하지 않는다: 생성 문항은 은행(retired 제외)에서만 만들어지므로 항상 해결된다. S15는 `targetLinks`로 해결 여부를 확인해 빠지면 줄째 숨긴다.
3. 비활성 이유 문구는 유형 코드로 고른다(뜻 유형 → "검수된 뜻이…", 한자 유형 → "대상 단어가…").
4. 글꼴 200% 스크린샷 높이를 1300 dp로 했다(내용 길이에 맞춤).

## 위험·unverified
- unverified(실기기): 한국어 TalkBack이 뜻 선택지를 한국어로, 일본어 구간·지문 단어를 일본어로 읽는지, 라벨이 문항마다 한 번만 읽히는지, S06에서 뒤로 왔을 때 포커스 위치, 실기기 시작 지연.
- S13 포커스를 라벨로 보내는 것은 Compose `requestFocus`이며 TalkBack 포커스와 같은지는 실기기에서 확인해야 한다.
- 위험: N4 ①② 칩 없음은 의도(provenance null). 매니페스트 S-01 갱신(QG3 9절 5)은 여전히 출시 전 필요.

## 한눈 요약
1. 테스트: app 540 → 576(+36, skipped 2 = 표본·지연 측정), 실패 0. core:quiz 70 그대로.
2. 스크린샷 9개: 위 목록.
3. 시작 지연: Robolectric 콜드 N5 은행 90 ms, 이후 ~20 ms, newSession ≤11 ms → `starting` 미추가(실기기 unverified).
4. 위험: TalkBack 실기기 미확인, S12 로딩 지연은 실기기 미측정.
5. 수정 파일(main): `nav/AppRoot.kt`, `ui/practice/{PracticeConfig,PracticeHomeScreen,PracticeHomeViewModel,PracticeModels,QuizResultScreen,QuizScreen,QuizTypeLabels}.kt`, `res/values/strings.xml`.
6. 수정 파일(test): `ui/dictionary/DictScreenshotTest.kt`(lightOnly), `ui/practice/{PracticeFixtures,QuizTypeLabelsTest}.kt`, `data/QuizGenSampleTest.kt`(지시문을 QuizTypeLabels로 합침); 새: `PracticeHomeGeneratedTest`, `GeneratedScreenUiTest`, `GeneratedScreenshotTest`, `data/QuizGenLatencyTest`.
7. 설계와 달리: 위 1~4.
