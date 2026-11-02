# M1 구현 요약 — SRS 스케줄러(FSRS-6, `:core:srs`)

작성: 2026-11-02 · feature-engineer

## 결과
- `verify.sh` 종료 코드 0, PASS: `test lintDebug assembleDebug`. 로그: `_workspace/build/verify_20261102_041550.log`
- `:core:srs:test` 32개 통과(Determinism 5, SessionPlanner 11, Property 7, StudyDayTime 7, ReferenceVector 2)
- 테스트 우선 절차: 계약 타입과 `TODO()` 스텁 상태로 테스트를 먼저 돌려 32개 중 31개 실패를 확인한 뒤 구현했다(통과한 1개는 `StudyDayRule` 경계 범위 `require`). 구현에 맞춰 테스트를 고친 곳은 없다.
- `:core:srs:dependencies --configuration runtimeClasspath`: kotlin-stdlib, annotations, kotlinx-serialization-json/core(1.12.0)만 있다. android·androidx·room 좌표는 없다.
- `core/srs/src/main`에서 `System.currentTimeMillis`, `Clock.system`, `Instant.now`, `ZoneId.systemDefault`를 grep하면 0건이다.

## 이식 기준
- py-fsrs v6.3.2, 커밋 `9446cb06605c597a063aeee49f7d188d42e34dc2`, `fsrs/scheduler.py`. 원본을 직접 받아 대조했다. `Fsrs6Scheduler.kt`와 `ReferenceVectorTest.kt` 머리에 커밋 해시, 파일 경로, MIT 고지 전문을 적었다.
- 참조 벡터는 `tests/test_basic.py`(`test_review_card`, `test_memo_state`)에 적힌 값만 썼다. 직접 만든 값은 없다.

## 파일
- `core/srs/build.gradle.kts`: `kotlin-serialization` 플러그인과 `kotlinx-serialization-json`만 추가
- `core/srs/src/main/kotlin/com/jlptstudy/srs/Model.kt`: Rating, Phase, CardState(@Serializable), StudyDayRule, SrsConfig, IntervalPreview, ReviewRecord, ScheduleResult, Scheduler
- `core/srs/src/main/kotlin/com/jlptstudy/srs/Fsrs6Scheduler.kt`: Fsrs6.DEFAULT_PARAMETERS, Fsrs6Scheduler(`fsrs6/py-fsrs-6.3.2`)
- `core/srs/src/main/kotlin/com/jlptstudy/srs/SessionPlanner.kt`: selectNew, plan
- `core/srs/src/test/kotlin/com/jlptstudy/srs/`: ReferenceVectorTest, PropertyTest, StudyDayTimeTest, DeterminismTest, SessionPlannerTest, Fixtures

## 계약에서 정하지 않아 직접 정한 것(리뷰 확인 요청)
1. `CardState.new`는 `phase = NEW, step = null`이다. 계산할 때 NEW는 py-fsrs의 Learning 단계 0으로 취급하고, 결과 phase는 LEARNING(또는 REVIEW)이다.
2. 퍼즈 시드는 `cardId.hashCode().toLong() * 31 + reps`이고, `reps`는 평가 **전** 값이다. preview와 schedule이 같은 계산 함수를 쓰므로 둘의 결과가 같다.
3. `reps`는 평가마다 +1, `lapses`는 REVIEW에서 AGAIN일 때 +1이다(py-fsrs Card에는 두 값이 없다).
4. 경과 일수는 `max(0, 학습일 차이)`다. 시계가 뒤로 가도 음수가 되지 않게 막았다. 회상 확률(retrievability) 계산에도 같은 경과 일수를 쓴다(py-fsrs는 실시간 차를 내림한 값).
5. `reviewedAt`은 ms로 잘라서 계산한다. 그래서 학습 단계에서 항상 `dueAt − reviewedAt == stepDuration`이 성립한다.
6. `plan`은 `existing`에 있는 NEW 외 카드를 `newCardIds`에서 뺀다(EASY로 곧장 복습 상태가 된 새 카드가 반복해서 나오지 않게). `pinned`는 기한 목록에 없어도 맨 앞에 둔다.
7. 파라미터 검사는 개수(21)만 한다. py-fsrs의 파라미터별 상·하한 검사는 옮기지 않았다. 지금은 기본값만 쓰기 때문이다. 기기 내 최적화나 사용자 파라미터를 받게 되면 그때 추가한다.
8. Python `round()`(짝수 쪽 반올림)는 `Math.rint`로 맞췄다.

## 명세와 다른 점
- 없음. 참조 벡터 두 개 모두 architecture.md 2.2의 조건으로 py-fsrs 값과 일치했다(차이 원인 보고 없음).

## 콘텐츠 DB 결함
- 해당 없음(M1은 콘텐츠를 다루지 않는다).
