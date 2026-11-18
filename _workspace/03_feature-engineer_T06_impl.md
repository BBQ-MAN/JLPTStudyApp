# T06 — 학습 기록 DB v2 마이그레이션과 테스트 장치 (feature-engineer)

## 바꾼 파일
- `app/src/main/java/com/jlptstudy/app/data/study/StudyEntities.kt`: `QuizAttemptEntity`, `QuizAnswerEntity` 추가(9.2.4 표 그대로, DEFAULT 열은 `@ColumnInfo(defaultValue = "0")`).
- `app/src/main/java/com/jlptstudy/app/data/study/StudyDatabase.kt`: version 2, 엔티티 두 개 추가, `open`에 `.addMigrations(MIGRATION_1_2)`.
- `app/src/main/java/com/jlptstudy/app/data/study/StudyMigrations.kt`(새 파일): `MIGRATION_1_2`. 2.json의 createSql 문자열 그대로 두 줄만 실행하고, 기존 테이블은 건드리지 않는다.
- `app/schemas/com.jlptstudy.app.data.study.StudyDatabase/2.json`(KSP 생성). `1.json`은 바뀌지 않았다(`git diff --exit-code` 0).
- `gradle/libs.versions.toml`: `androidx-room-testing`(version.ref room). `app/build.gradle.kts`: `testImplementation(libs.androidx.room.testing)`.
- `app/src/main/assets/licenses/libraries.json`: `androidx-room-testing` 행(distributed false).
- `app/src/test/resources/migration/study_v1_fixture.sql`: card_state 4행(phase 1·2·3, kind vr·vp·kr, step NULL/값, last_review NULL 1행), review_log 6행(undone 0·1, 실제 CardState JSON), user_settings id=1(300/25/extra 5·study_day 20394/'5,4'/dark/onboarding 1/script_first 1 등 기본값과 다른 값).
- `app/src/test/java/com/jlptstudy/app/data/study/StudyMigrationTest.kt`: 테스트 5개.
  1. 1·2단계: 행 수·quote() SHA-256 체크섬이 마이그레이션 전후로 같고, quiz 테이블 두 개가 0행으로 있고, script_shown 없이 INSERT하면 값이 0이다.
  2. 2.json의 v1 세 테이블 createSql·indices가 1.json과 글자 그대로 같다.
  3. 3단계: `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`로 열면 `settings().first()`와 `states(...)`가 픽스처와 같다(로그 JSON도 CardState로 디코드된다).
  4. 4단계: addMigrations 없이 열면 IllegalStateException이 나고, 파일은 v1·card_state 행 수 그대로다.
  5. (추가) 운영 경로 `StudyDatabase.open`이 v1 study.db를 v2로 올리고 체크섬을 유지한다. `open`에서 addMigrations를 빼면 이 테스트가 실패하는 것을 확인했다.

## 명세와 다른 점
- **스키마 에셋 경로**: 9.2.4에 적힌 `sourceSets { getByName("test").assets.srcDir(...) }`로는 `MigrationTestHelper`가 `1.json`을 찾지 못했다(FileNotFoundException). Robolectric은 `test_config.properties`의 `android_merged_assets=…/mergeDebugAssets`만 읽고, AGP는 test 소스 세트 에셋을 그 경로에 병합하지 않는다. Room 플러그인도 unit test 에셋에 schemas/를 넣어 주지 않는다. 그래서 `getByName("debug").assets.srcDir("$projectDir/schemas")`로 바꿨다. 결과로 **debug APK**에 `com.jlptstudy.app.data.study.StudyDatabase/{1,2}.json`과 ContentDatabase 스키마 JSON이 들어간다(수 KB). 릴리스 APK에는 들어가지 않는다. architecture.md 9.2.4의 해당 줄을 고쳐야 한다(설계 담당).
- `androidx.test:core`는 더하지 않았다. `InstrumentationRegistry`는 Robolectric이 가져오는 의존으로 컴파일된다.
- `pstack:blast-radius` 스킬은 이 세션의 스킬 목록에 없어서 부를 수 없었다. 대신 직접 grep으로 확인했다: StudyDatabase를 쓰는 곳은 `AppContainer`(open), Home/Onboarding Robolectric 테스트(open), StudyRepository/ReviewPersistence 테스트(inMemory), StudyDbCorruptionTest. 모두 통과했다. `fallbackToDestructiveMigration(`는 0건이다.

## 검증
- `verify.sh` 종료 코드 0, 로그 `_workspace/build/verify_20261103_121803.log`.
- `:app:testDebugUnitTest` 222개, 실패 0개(기준선 214 이상). StudyRepositoryTest 7개, AboutFilesTest(M7 카탈로그 대조) 통과.
- 워크트리에 원래 있던 T05a/T05b 변경(content DB v2 등)은 손대지 않았다.
