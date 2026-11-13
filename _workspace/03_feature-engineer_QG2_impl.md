# QG2 구현 보고 — `:core:quiz` `com.jlptstudy.quiz.gen`

날짜 2026-11-08 · 브랜치 dev/quizgen · 정본 quizgen.md 4·5절, 8.3, 9절, decisions Q-02·03·04·06·07·09

## 결과
- `./gradlew :core:quiz:test` 70건 통과(기존 M18 15건 수정 없이 통과 + 신규 55건). `verify.sh` 종료 코드 0(`_workspace/build/verify_20261108_074726.log`, check.sh + test + lintDebug + assembleDebug). verify는 같은 worktree에 있던 content-engineer의 미커밋 변경(QG1)이 섞인 상태에서 돌렸다. 통과했으므로 구분할 실패는 없다.
- TDD: 8.3 테스트를 먼저 쓰고 컴파일 실패(구현 없음)를 확인한 뒤 구현했다. 구현 후 변이 실험(아래)으로 테스트가 실제로 막는지 확인했다.

## 파일(전부 core/quiz 아래)
- 수정: `core/quiz/src/main/kotlin/com/jlptstudy/quiz/Quiz.kt` — `QuizQuestion.fixedOrder = false`(기본), `Quiz.shuffle`은 fixedOrder면 섞지 않는다.
- 신규 main(`.../quiz/gen/`): `GenModel.kt`(GenType, QuizVocab·QuizKanji·QuizBank, Distractor(Kind), GeneratedQuestion, SkipReason, GenResult, GenId), `QuizGen.kt`(eligible/generate/buildSession/idOf/parseId/regenerate), `Kana.kt`(Kana.hira, KanaVariants.of, posGroup).
- 신규 test(`core/quiz/src/test/kotlin/com/jlptstudy/quiz/gen/`): `GenTestData.kt`(합성 은행), `FixedOrderTest`(3), `KanaTest`(10), `QuizGenTest.kt`(KanjiReadingTest 10·OrthographyTest 10·MeaningTest 8·SkipAndDeterminismTest 9), `QuizGenSessionTest`(5).

## 안전 규칙(오답이 사실은 정답인 경우 방지)
spec 4.2~4.4 그대로 + 아래 "더 엄격하게 한 것". 규칙을 느슨하게 해서 채운 곳은 없다. 후보가 3개 미만이면 `Skip(TOO_FEW_DISTRACTORS)`.
- ① 오답 읽기 d: `hira(d) ∉ X1`(jm_form의 같은 표기 모든 읽기), 정답·이미 고른 것과 hira 뒤 다름. 소리 변형도 같은 검사.
- ② 오답 표기 x: `x ∉ X2`(같은 읽기 모든 표기), 한자 1자 이상, 치환 한자는 은행 한자만.
- ③ 오답 B: 같은 급수·같은 posGroup·검수된 뜻·`B ∉ conflicts(A)`·한국어 뜻과 일본어 글자 둘 다 서로 다름.

### 설계보다 엄격하게 한 것(전부 "의심스러우면 버림" 방향, 생성 가능 수만 줄 수 있다)
1. X1·X2에 jm_form뿐 아니라 **같은 표기/같은 읽기를 가진 은행 어휘**도 합친다(forms가 불완전해도 은행 안의 동음·동표기는 막는다).
2. 자기 (표기, 읽기) 쌍이 `forms`에 없는 어휘는 ①② 대상이 아니다(`NOT_TARGET`, `eligible`에서도 빠짐). 어댑터가 forms를 안 읽었거나 비어 있으면 아무 배제 없이 문항이 나오는 사고를 막는 안전장치다. 파이프라인은 QG1 acceptance로 모든 jm: 어휘의 자기 쌍을 넣으므로 정상 데이터에서는 영향이 없다. spec 8.3 3번 예시처럼 forms에 다른 읽기만 적은 은행은 이제 건너뛴다(테스트 은행은 자기 쌍을 자동으로 넣는다).
3. `conflicts`에 정답 어휘 행이 없으면 ③ 대상이 아니고(`NOT_TARGET`), 후보 B의 행이 없으면 B는 쓰지 않는다. 충돌은 한쪽(A→B 또는 B→A)에만 적혀 있어도 충돌로 본다(DB는 대칭이지만 입력을 믿지 않는다).
4. ③은 한국어 뜻이 같아도(충돌 행 누락 가정) 일본어 글자가 같아도(方 かた/ほう) 정답과 같은 후보로 본다. ja-ko에서도 일본어 글자가 같은 후보를 뺀다(spec은 ja-ko에서 한국어 뜻만 비교).
5. 대상·후보는 `level == bank.level`인 `jm:` 어휘만(`av-`·다른 급수는 `UNKNOWN_VOCAB`). 은행 한자는 `level >= bank.level`만 다시 거른다.
6. 비교 키는 NFC(눈에 같은 글자가 다른 코드열인 중복 보기 방지). 가타카나 forms 행도 `hira` 뒤 비교.
7. 장음 넣기는 바로 뒤에 같은 글자(おう → おうう)나 작은 글자가 있으면 만들지 않는다.
8. `parseId`는 `idOf`로 되돌렸을 때 글자가 같은 것만 받는다(대문자 시드·`+`·앞 0 거부).

### 설계 해석·보충
- `generate`의 `slot`은 0..3이 아니면 `IllegalArgumentException`. 잘못된 ID는 `regenerate`가 `Skip(UNKNOWN_VOCAB)`로 돌려준다(SkipReason 열거형은 spec 그대로 4개).
- ③ 일본어 쪽 문자열은 `written ?: reading`(보이는 글자). 루비 `{표기|읽기}`는 어댑터(QG3) 몫이다.
- 소리 변형 종류 우선순위는 같은 문자열이 여러 종류로 나오면 VOICING > SOKUON > LONG_VOWEL > YOON 중 먼저 나온 것.
- LONG_VOWEL 넣기에서 "문장 끝 뒤에는 넣지 않음"은 문자열 맨 끝 뒤 삽입 금지로 읽었다.
- C2(①②) 우선순위 그룹은 섞은 뒤 안정 정렬한다(같은 시드 → 같은 결과, 입력 순서 무관 테스트 있음).

## 테스트가 확인하는 사례(합성 데이터)
今日 きょう/こんにち(별개 항목)·日本 にほん/にっぽん, 橋/箸/端(은행·forms 각각), 会う/合う/逢う/遇う(한자 치환 후보를 만들어 보이고 forms 행이 막는 것 확인), 父/パパ·母/お母さん 충돌(대조군: 충돌 목록을 빼면 실제로 나온다), 方 かた/ほう, 미검수(null·공백), 품사군, 다른 급수·`av-`, 가타카나 reading, 한자 없는 표기, forms/conflicts 행 누락, 소리 변형 형식 위반 없음, 입력 순서 무관 결정성, ID 왕복·형식 거부.
속성 테스트: 4유형 × 시드 1..500 세션(size 10) — 보기 4개 서로 다름·빈 값 없음·오라클(정답 정확히 1개, 은행 값만으로 판정)·`answerIndex == slot`·한 자리 최대 ≤ ⌈m/4⌉(m=10이면 ≤ 3)·전체 집계 각 자리 < 40%·같은 시드 같은 ID 목록·`regenerate` 동일·한 세션에 같은 단어 중복 없음. 모든 대상 어휘 × 40시드도 Ok이면 안전, Skip이면 TOO_FEW만.

## 변이 실험(구현 일부를 지워 테스트가 실패하는지 확인)
X1 제거, X2 제거, 충돌 양방향·한쪽, posGroup, 같은 글자·같은 뜻 후보 거부, 미검수 거부, conflicts 행 누락 신뢰, forms 자기 쌍 요구, 한자 필수, on 우선·kun 대체, `jm:` 필터, 정답 자리 분배, 장음 중복 삽입 — 전부 테스트가 실패했다. 살아남은 것: 세션 슬롯 섞기 시드(분배에 영향 없음), KanaVariants.valid의 "첫 글자 っ/ー/작은 글자"·"작은 ゃゅょ는 い단 뒤"(현재 규칙들로는 위반이 만들어지지 않아 방어 코드. 형식 검사 테스트는 별도로 있음).

## 위험·남은 일
- 실제 DB로는 돌려 보지 않았다(QG3 `QuizGenExhaustiveTest`). 실제 생성 가능 수는 QG3에서 확정. N4 ③은 `reviewed` 63어뿐이라 짧은 세션이 많을 수 있다(spec 9.2). 이때도 규칙을 느슨하게 하지 말고 사용자 판단을 받는다.
- 4.7 #5·#9·#17(JMdict에 없는 읽기·이체자·유의어)은 규칙으로 못 막는다 — 원어민 표본 검수 몫.
- 성능: ② 한자 치환이 은행 한자 전체를 매번 훑는다(O(K)). 단어 하나당 문제는 없으나 QG3 전수 검사에서 느리면 은행에 on/kun 색인을 미리 만든다(shortcut).
- QG1·QG3과의 계약: forms 자기 쌍·conflicts 모든 jm: 행이 있어야 대상이 된다(위 2·3). QG3 어댑터는 `retired` 제외 후 은행을 만들되 forms·conflicts는 급수 전체 행을 넣어야 한다.
- 이 worktree는 content-engineer의 QG1 변경(app/·content/)이 같은 작업 트리에 미커밋으로 있다. 건드리지 않았다. 커밋은 core/quiz 파일만 따로 골라야 한다.
