# M18 구현 요약: :core:quiz 채점과 문제 세트 구성

## 바뀐 파일
- `core/quiz/src/main/kotlin/com/jlptstudy/quiz/Quiz.kt`(새 파일): architecture 9.2.5 시그니처를 그대로 구현했다(Section, QuizQuestion, ShuffledQuestion, ExamPart, ExamSection, ExamTemplate, PoolItem, Shortfall, BuiltSet, AnswerInput, SectionScore, ExamSectionScore, Score, object Quiz).
- `core/quiz/src/test/kotlin/com/jlptstudy/quiz/QuizTest.kt`(새 파일): 테스트 15개. 테스트를 먼저 썼고, 구현 전에는 컴파일 실패(Unresolved reference)로 실패하는 것을 확인했다.
- `content/pipeline/test_check.py`(1줄과 주석): 아래 "범위 밖 수정" 참고.

## 구현 결정
- `shuffle`: `Collections.shuffle([0..3], Random(attemptSeed * 31 + id.hashCode()))`를 쓴다. 고정 값은 id `q-n5-vocab-0001`, 시드 42일 때 `originalIndexOf = [2, 3, 0, 1]`, 섞은 뒤 `answerIndex = 3`이다(java.util.Random과 Collections.shuffle의 동작은 API 문서가 고정한다).
- `buildSet`: 호출마다 `Random(seed)` 하나를 만들어 parts 순서대로 이어 쓴다. 각 part의 후보는 `(section, type)`이 맞는 항목을 id 중복 제거·정렬한 뒤 섞는다. 그래서 풀 순서와 관계없이 결과가 같다. 앞 part에서 이미 고른 ID는 뺀 다음 `available`을 센다.
- `score`: 같은 `(section, type)`이 템플릿의 여러 영역에 있으면 앞 영역에만 센다(명세에 정하지 않은 경우).
- 리플렉션 테스트는 Java 리플렉션(`declaredFields`)으로 했다. kotlin-reflect 의존을 더하지 않기 위해서다.

## 완료 조건 확인
- `./gradlew :core:quiz:test`: 15개 테스트 통과(BuildSet 5, Score 4, Shuffle 4, Validation 2).
- `:core:quiz:dependencies --configuration runtimeClasspath`: `org.jetbrains.kotlin:kotlin-stdlib:2.4.21` 하나와, 그 표준 라이브러리가 끌어오는 `org.jetbrains:annotations:13.0`만 있다. android·androidx 좌표는 없다. `org.jetbrains:annotations`를 문자 그대로 "org.jetbrains.kotlin 밖 좌표"로 보면 조건에 걸릴 수 있다. 하지만 kotlin-stdlib 자체의 전이 의존이고 `:core:srs`에도 똑같이 있어서 빼지 않았다.
- `core/quiz/src/main`에서 `System.currentTimeMillis|Clock.system|Instant.now|Random()`을 grep하면 0건이다.
- verify.sh 종료 코드는 0이다(`_workspace/build/verify_20261103_114334.log`).

## 범위 밖 수정(보고)
- 처음 verify.sh는 종료 코드 1로 실패했다. 원인은 `content/pipeline/test_check.py`의 `test_not_confirmed`다. 이 테스트는 실제 manifest를 복사해 쓰면서 S-03이 미확정이라고 가정했는데, 기준 커밋 9d23477(T03)이 S-03을 confirmed로 바꿨다. 즉 M18 이전부터 있던 실패다.
- 테스트를 지우지 않고, 테스트 출처 ID를 영구히 `excluded`인 S-08로 바꿨다. 검사하려는 의도("confirmed가 아닌 출처는 FAIL")는 그대로다. content-engineer가 확인해 주기 바란다.

## 명세와 다른 점
- 없음.
