# QG3 구현 보고 — 자동 생성 문제 앱 데이터 연결·전수 검사·표본 파일

날짜 2026-11-08 · worktree `JLPTStudyApp-wt/quizgen`(dev/quizgen) · 정본 milestones.json QG3, quizgen.md 6.1·6.2·7절·9절, decisions Q-03·05·07·08

## 결과
- **status: 통과(needs-decision 없음).** `verify.sh` 종료 코드 0(`PASS: content/pipeline/check.sh + test lintDebug assembleDebug`, 로그 `_workspace/build/verify_20261108_075926.log`).
- 하한 충족: N5 ① 100%·② 100%·③ 두 방향 각 97.4%(하한 90·80·60). 규칙은 QG2 그대로이고 느슨하게 한 곳이 없다. 불변식 I1~I10 위반 0건(검사한 문항 27,120).
- TDD: `QuizGenRepositoryTest`·`GeneratedTextsTest`를 구현 전에 써서 컴파일 실패(`RoomQuizGenRepository`·`GeneratedTexts`·`QuizContent.generated` 없음)를 확인한 뒤 구현했다. 전수 검사의 오라클이 실제로 잡는지 변이 실험을 했다: 어댑터가 `jm_form`에서 자기 쌍 밖의 행을 빼고 `vocab_conflict`를 비우게 바꾸자 위반 4,752건(`I3 … 오답 いっち 가 jm_form (一, いっち)` 등)이 나왔고, 원복 후 0건. 원복은 `diff`로 확인했다.

## 만든 것·바꾼 것
| 파일 | 내용 |
|------|------|
| `app/.../data/content/ContentDao.kt` | `quizVocab`·`quizMeanings`·`quizKanji`·`quizForms`·`quizConflicts`·`vocabLevel`(6.2 그대로, SELECT만) |
| `app/.../data/content/QuizGenRepository.kt`(새) | `GenTypeAvailability`, `QuizGenRepository`, `RoomQuizGenRepository`(급수별 `QuizBank` Mutex 캐시, 은행 만들기·생성은 `Dispatchers.Default`, 전수 검사용 `bank(level)`), `GeneratedQuestion → QuizContent` 변환(③ 일본어 쪽에만 `{표기|읽기}` 루비, ①②는 루비 없음, `provenance`는 뜻이 보일 때만) |
| `app/.../data/content/GeneratedTexts.kt`(새) | 해설·오답 이유 문구(6.2 표) |
| `app/.../data/content/QuizContentRepository.kt` | `QuizContent.provenance: MeaningUi?` + `generated: Boolean = false`, `toQuestion()`이 `fixedOrder = generated`, `RoomQuizContentRepository(dao, gen = RoomQuizGenRepository(dao))`: `gq:` ID는 `gen.contents`로 위임해 합침(`q-`/`ls-` 경로는 그대로) |
| `app/.../data/content/ContentRepository.kt` | `RoomContentRepository`가 `QuizGenRepository` 하나를 만들어 `RoomQuizContentRepository`와 공유하고 `quizGen`으로 노출(공개 생성자 `RoomContentRepository(dao)`는 그대로) |
| `app/.../AppContainer.kt` | `quizGenRepository` 추가(open에서 채우고 `contentReadFailed`에서 비움) |
| `app/.../ui/practice/PracticeModels.kt`·`QuizViewModel.kt`·`QuizScreen.kt` | `QuizQuestionUi.provenance: MeaningUi?`·`generated`, `toUi`가 전달, 칩은 `provenance != null`일 때만 |
| 테스트 | `QuizGenRepositoryTest`(11)·`GeneratedTextsTest`(2) 새, `QuizGenExhaustiveTest`(1)·`QuizGenSampleTest`(1) 새, `ChunkingTest`(가짜 DAO에 6개 메서드), `PracticeScreenUiTest`·`QuizResultUiTest`·`ListeningScreenUiTest`(`provenance` 널 허용에 맞춘 `!!` 3곳) |

`app/schemas`·학습 기록 DB·`core/quiz` 변경 없음(`git diff --exit-code app/schemas` 종료 0). 화면 레이아웃(S12 영역, 라벨 칩, 유형 이름)은 QG4 몫이라 건드리지 않았다. `docs/spec/ui-design.md`는 건드리지 않았다.

## 전수 검사(`QuizGenExhaustiveTest`, 약 35초)
번들 `content.db`를 `ContentDatabase.open`으로 열고, 오라클은 `db.openHelper.readableDatabase.query(...)` 원시 SQL로 `vocab`·`vocab_meaning`·`jm_form`·`vocab_conflict`·`retired`를 읽은 표다. 생성기 함수는 "검사 대상 문항을 만드는 데"만 부르고 판정은 이 표로만 한다(`posGroup`·`hira`·4.6 형식 규칙·`visible`도 테스트 쪽에 따로 구현). 급수 5·4 × 유형 4 × 대상 단어 전부 × 시드 8(`0,1,2,3,42,20261108,Long.MAX_VALUE,-1`, 자리 = 시드 순번 % 4) + 세션 시드 1..200.
- I1 선택지 4개·빈 값 없음 / I2 `hira` 뒤 중복 없음 / I3 정답 정확히 1개(① 오답마다 `(W, hira(d)) ∉ jm_form`이고 같은 표기 다른 항목의 읽기와도 안 겹침, ② `(x, hira(R)) ∉ jm_form`·같은 읽기 다른 항목의 표기와 안 겹침, ③ 출처 어휘가 같은 급수·같은 품사군·양방향 `vocab_conflict` 밖·reviewed·뜻이 정답의 모든 뜻 행과 다름) / I4 화면의 모든 한국어 뜻이 reviewed `text_ko`와 같은 문자열, ①②는 해설 뜻 줄 유무가 ord 0 reviewed 여부와 일치하고 `provenance`도 일치, 미검수 전용 뜻이 부분 문자열로도 없음 / I5 `generate` 두 번·`regenerate`·`contents` 두 번 동일 / I6 세션 한 자리 ≤ ⌈m/4⌉·같은 단어 중복 없음·급수·유형 전체 집계 각 자리 < 40% / I7 ① 소리 변형 4.6 형식 규칙 / I8 targets·`parseId`·`answerIndex == slot`·메타 / I9 표시 기호 없음·`whyWrong` 모양 / I10 `av-`·retired·다른 급수 없음.
- 대상(eligible) 수를 오라클이 따로 계산해 `QuizGen.eligible`과 정확히 같은지 단언한다(어댑터가 forms·conflicts 행을 빠뜨리면 여기서 걸린다).
- 하한 단언은 "시드 8개 모두에서 Ok"인 단어 수 기준(보수적). "시드 하나라도 Ok"와 값이 같았다(coverage.tsv `generated_any_seed`).

## 급수·유형별 수(`app/build/reports/quizgen/coverage.tsv`)
| 급수 | 유형 | 어휘(`jm:`) | eligible | generated | 건너뜀: NOT_TARGET | UNREVIEWED | TOO_FEW | generated/eligible |
|------|------|------|------|------|------|------|------|------|
| N5 | ① kanji-reading | 667 | 502 | 502 | 165 | 0 | 0 | 100% (하한 90%) |
| N5 | ② orthography | 667 | 502 | 502 | 165 | 0 | 0 | 100% (하한 80%) |
| N5 | ③ meaning-ja-ko | 667 | 667 | 650 | 0 | 0 | 17 | 97.4% (하한 60%) |
| N5 | ③ meaning-ko-ja | 667 | 667 | 650 | 0 | 0 | 17 | 97.4% (하한 60%) |
| N4 | ① | 630 | 491 | 491 | 139 | 0 | 0 | 100% (보고만) |
| N4 | ② | 630 | 491 | 491 | 139 | 0 | 0 | 100% |
| N4 | ③ ja-ko | 630 | 63 | 52 | 0 | 567 | 11 | 82.5% |
| N4 | ③ ko-ja | 630 | 63 | 52 | 0 | 567 | 11 | 82.5% |

- **N5 표기 있는 어휘 502개**(전부 한자 포함, 전부 ①② 대상). NOT_TARGET 165는 표기 없는 가나 어휘(667−502). N4는 표기 있는 어휘 492개 중 1개(`jm:1350080 消しゴム`, vocab.reading `けしゴム`에 가타카나가 섞여 4.2의 "읽기는 히라가나만"에 어긋남)가 NOT_TARGET이라 ①② 대상이 491. 어댑터는 reading을 DB 그대로 넘기고 생성기가 비교 키에만 `hira`를 쓴다(`jm_form`의 `けしごむ`와 잘 맞는다).
- ③ TOO_FEW 건너뜀 원인은 작은 품사군이다. N5 17어: 접속사 5·감탄사 4·조사 2·連体詞 2·`adj-no` 2·접두어 1·`adj-f` 1. N4 11어: 부사 3·`adj-na` 3·`adj-no` 2·`exp`·접속사·`adj-i` 각 1. 규칙을 느슨하게 하지 않았다. N4 ③이 63어뿐인 것은 N4 한국어 뜻이 `reviewed` 63·`sampled` 567이라서다(Q-03). `skipped.tsv`에 단어별 이유가 있다(행 1,798).
- 전수 검사에서 "시드에 따라 됐다 안 됐다"(partial)인 단어는 0개다.

## 표본 파일
`_workspace/quizgen_sample.md`(4,604줄). N5·N4 × 유형 4 × 30문항 = 240문항, 시드 `20261108`로 `newSession(level, type, 20261108, 30)`. 문항마다 ID·지시문·지문·출처 단어 ID·해설 표, 보기 4개 표(정답 ✔, 오답 이유 종류·이유 문장·출처 단어 ID), 검수 칸 `- [ ] ok / fix:`. 모든 유형의 생성 가능 수가 30 이상이라 "30 미만" 안내가 붙은 곳은 없다(N4 ③은 52개 중 30). 다시 만들기: `QUIZGEN_SAMPLE_OUT=$PWD/_workspace/quizgen_sample.md ./gradlew :app:testDebugUnitTest --tests '*QuizGenSampleTest'`(환경 변수가 없으면 `Assume`로 건너뜀 — verify에서는 돌지 않음, 테스트 수 1 skipped).

## 테스트 수
- `:app:testDebugUnitTest`: 525 → 540(+15: 저장소 11, 문구 2, 전수 1, 표본 1(verify에서는 skipped)). 실패 0. `:core:quiz:test` 70 그대로(QG2). 기존 `QuizViewModel`·`QuizResultViewModel`·`PracticeHomeViewModel` 테스트는 수정 없이 통과(바꾼 기존 테스트는 컴파일 맞춤 4곳뿐: ChunkingTest의 가짜 DAO, `provenance!!` 3곳).

## 인수 조건 대조
1. `QuizGenExhaustiveTest` 통과, 하한 충족 — 위 표.
2. `coverage.tsv`·`skipped.tsv` 생성, N5 표기 있는 어휘 수 502 — 위.
3. 표본 파일 — 위.
4. 단위 테스트: 섞은 `q-…`·`gq:…` 목록에서 둘 다 반환, 형식이 깨진 `gq:` ID(`gq:kanji-reading:9:zz:…`·모르는 유형·5필드 아님·없는 어휘·`av-`)와 retired 어휘는 빠짐 / `QuizSessionLoader.load` → `AttemptMeta(PRACTICE, N5, VOCAB, 유형 코드)`(4유형 모두), `toQuestion().fixedOrder == true`, `Quiz.shuffle` 뒤 `answerIndex == slot`(시드 4개).
5. N4 ①② 문항의 `explanationKo`·`whyWrongKo`에 sampled 뜻 없음·`provenance == null`(합성 은행 단위 테스트 + 실제 N4 전수), 고정 문항 `provenance != null`.
6. `app/schemas` 종료 0, 테스트 수 감소 없음, verify 종료 0.

## 설계와 다르게 한 점·해석
1. `GeneratedTexts.explanation` ③: 표기가 없거나 읽기와 같으면 `「あ」(あ)`처럼 겹치지 않게 `「あ」: 뜻`으로 쓴다. 그 밖은 6.2 그대로.
2. `contents()`는 `gq:`로 시작하는 ID만 `gen.contents`로 넘기고 `q-`/`ls-` 경로의 retired 확인은 고정 문항 ID에만 적용한다. 생성 문항의 retired 처리는 `quizVocab` SQL(`NOT IN retired`)이 담당해 은행에 없으면 `UNKNOWN_VOCAB`로 빠진다.
3. `RoomQuizContentRepository`의 `gen` 인자는 기본값(`RoomQuizGenRepository(dao)`)을 둬 기존 테스트 호출을 안 고쳤다. 앱에서는 `RoomContentRepository`가 같은 인스턴스를 넘겨 은행 캐시를 하나만 쓴다.
4. `provenance`는 ③ 전부와 ① ② 중 해설에 뜻이 붙는 문항(ord 0 reviewed)에서만 값이고, 그 값은 정답 어휘 ord 0 뜻의 `MeaningUi`다(6.2 그대로).
5. 해설 `"뜻:"` 줄을 달지 않는 N4 ①②는 AI 한국어가 없어 칩이 안 나온다(QG4 화면에서 칩 자리가 비는 것을 확인할 것).

## 위험·남은 일
- **9절 5 그대로 남음**: 매니페스트·sources.md S-01 `usedFields`(`sense/xref`)·`modifications` 갱신과 attribution 재생성(analyst)은 이 마일스톤 밖이다. 출시 전에 필요하다.
- **규칙이 못 막는 정답 중복(4.7 #5·#9·#17)**: JMdict에 없는 읽기·이체자·뜻이 안 겹치는 유의어는 규칙·오라클 모두 못 본다. 표본 파일의 원어민 검수 몫이다. 이번 오라클은 `jm_form`·`vocab_conflict`·같은 급수 어휘 표만 기준이라 "표에 없는 사실"은 검증하지 못한다.
- ③ 일본어↔한국어 뜻 문제는 오답이 같은 품사군이라 쉬운 문제(품사만 보고 풀림)는 아니지만, 한국어 뜻풀이가 AI 번역(`reviewed`)이라 뜻풀이 오류 시 정답이 틀릴 수 있다(D-03).
- 전수 검사는 번들 DB가 바뀌면 같은 DB로 다시 돌아 하한을 단언한다. 새 판에서 하한 미달이면 테스트가 실패해 `needs-decision`이 된다(의도).
- S12 홈·라벨 칩·`QuizTypeLabels` 유형 이름 등 QG4 항목은 손대지 않았다. 표본 파일은 ③ 지시문을 테스트 안에 따로 적었다(QG4가 `QuizTypeLabels`에 넣으면 그쪽으로 합칠 것).
- 이 worktree에 QG1·QG2 커밋이 이미 있다. 이번 변경은 모두 미커밋이다(메인 세션이 커밋). `_workspace/` 두 파일은 `git check-ignore`에 걸리지 않는다.

## 한눈 요약(15줄)
1. 테스트 수: app 525 → 540(+15, 표본 1은 verify에서 skipped), core:quiz 70 그대로, 실패 0.
2. N5 ① 502/502(100%, 하한 90) · ② 502/502(100%, 하한 80) · ③ ja-ko 650/667·ko-ja 650/667(97.4%, 하한 60) — 모두 충족.
3. N4 ① 491/491 · ② 491/491 · ③ 52/63(두 방향, 미검수 567은 UNREVIEWED로 대상 밖).
4. 건너뜀 사유: N5 NOT_TARGET 165(①②, 표기 없는 가나 어휘)·TOO_FEW 17(③, 작은 품사군); N4 NOT_TARGET 139(①②)·UNREVIEWED 567(③)·TOO_FEW 11(③).
5. N5 표기 있는 어휘 502(전부 ①② 대상).
6. 불변식 I1~I10 위반 0건(검사한 문항 27,120, 세션 I6 1,600개). 변이 실험으로 오라클이 잡는 것 확인.
7. 표본 파일: `/Users/bbqman/HermesAgents/ETC/JLPTStudyApp-wt/quizgen/_workspace/quizgen_sample.md`(240문항).
8. 보고 파일: `coverage.tsv`·`skipped.tsv`는 `app/build/reports/quizgen/`.
9. verify.sh 종료 코드 0, `git diff --exit-code app/schemas` 0.
10. 위험: 4.7 #5·#9·#17(표에 없는 사실)은 원어민 표본 검수 몫, analyst 매니페스트 갱신 전 출시 금지(QG1 요청 그대로).
11. 위험: N4 ③은 `reviewed` 63어뿐이라 N4 뜻 연습은 짧다(Q-03).
12. 위험: 칩은 `provenance != null`일 때만이라 N4 ①②는 칩이 없다(QG4 화면 확인).
13. 수정한 파일(main): `data/content/{ContentDao,ContentRepository,QuizContentRepository}.kt`, `AppContainer.kt`, `ui/practice/{PracticeModels,QuizViewModel,QuizScreen}.kt`; 새 `data/content/{QuizGenRepository,GeneratedTexts}.kt`.
14. 수정한 파일(test): `data/{ChunkingTest}.kt`, `ui/practice/{PracticeScreenUiTest,QuizResultUiTest}.kt`, `ui/listening/ListeningScreenUiTest.kt`; 새 `data/{QuizGenRepositoryTest,QuizGenExhaustiveTest,QuizGenSampleTest}.kt`.
15. 산출물: `_workspace/quizgen_sample.md`, `_workspace/03_feature-engineer_QG3_impl.md`.
