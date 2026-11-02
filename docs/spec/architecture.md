# JLPT 학습 앱 — 아키텍처

작성: 2026-11-02 · 작성자: android-architect · 입력: `docs/plan.md`, `docs/content/{sources.md, decisions.md, manifest.json}`, `docs/spec/ui-design.md`, `.claude/skills/{compose-app-dev, srs-scheduler, content-pipeline, jlpt-content-authoring, content-licensing}`, `gradle/libs.versions.toml`

> 콘텐츠 상태: `manifest.json`의 자료가 모두 `unconfirmed`다. 원본을 내려받는 콘텐츠 마일스톤(M8 이후 실데이터)은 해당 자료가 `confirmed`가 되고 D-06이 정해질 때까지 진행할 수 없다. 이 조건은 `milestones.json`의 게이트 마일스톤(`G-…`, 10절)이 `dependsOn`으로 막는다. 그 전 마일스톤(SRS, 앱 골격, 샘플 콘텐츠 화면, 파이프라인 골격)은 막히지 않는다.

## 0. 결정 요약

| 항목 | 결정 | 근거 절 |
|------|------|---------|
| 모듈 | `:app`, `:core:srs`, `:core:quiz`(현재 골격 유지), 파이프라인은 Gradle 밖 `content/pipeline/` | 1 |
| SRS | FSRS-6, py-fsrs(MIT) v6.3.2를 `:core:srs`에 직접 이식. 외부 라이브러리 없음 | 2 |
| 시간 | 저장은 UTC epoch ms, 학습일은 사용자 시간대 지역 시각 기준 + 경계 04:00(설정), 일 간격·경과 일수는 학습일 차이, 학습 단계는 `Duration` | 3 |
| DB | 콘텐츠 DB(번들, 읽기 전용, CC BY-SA 4.0)와 학습 기록 DB(Room, 마이그레이션) 분리. 콘텐츠 DB 버전 번호는 미리 정함 | 4, 5 |
| 콘텐츠 ID | 어휘 `jm:<ent_seq>` / 직접 작성 `av-…`, 한자는 문자 자체, 직접 작성 콘텐츠는 고정 ID | 4.1 |
| DB 보호 | 암호화·난독화·추가 조건 없음(L-01.nodrm). 평문 SQLite 에셋 | 4.6 |
| 파이프라인 | Python 3 표준 라이브러리, `python3 -I`. 원본은 커밋한 스냅샷으로 판 고정 | 6, 6.7 |
| 급수 매핑 | 입력 어댑터 교체식(S-06이면 `jmdict_seq` 직결, 그 외 표기·읽기 매칭). 선택은 D-06 | 6.3 |
| 한자 급수 | 확정된 한자 급수 목록이 없으므로 "그 급수 어휘에 쓰인 한자"로 파생(승인 항목 AR-U03) | 4.3 |
| 영어 뜻 | 번들하지 않음(어휘 영어 gloss, KANJIDIC2 영어 meaning 모두). 한자 카드에 뜻 행 없음(AR-U04) | 4.2, 4.3 |
| 글꼴 | 기기 글꼴 + `ja-JP` 로캘로 시작, 스크린샷·실기기 확인에서 실패하면 번들로 전환(AR-U05 C) | 7 |
| 정본 기준 | 타입·상태 이름·필드·저장 스키마·파이프라인·`attribution.json` 형식·조립 주체는 이 문서. 화면 표시 규칙(반올림·문구·레이아웃)은 ui-design.md. 매니페스트·sources.md는 analyst | 11 |
| DI·내비게이션 | 수동 DI(`AppContainer`), Navigation Compose 2.10.2(카탈로그 버전), `NavigationSuiteScaffold` 탭 홈·사전·설정 | 1, 7 |
| 백업 | Auto Backup 켬. 학습 기록 DB만 포함, 콘텐츠 DB는 포함하지 않음 | 5.1 |

새로 추가하는 라이브러리는 없다. 버전은 `gradle/libs.versions.toml`(2026-11-02 기준: AGP 9.4.0, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5, Navigation Compose 2.10.2, Lifecycle 2.11.0, coroutines 1.11.0, serialization-json 1.12.0, Robolectric 4.17, Roborazzi 1.77.0)을 그대로 쓴다. `:core:srs`에 카탈로그의 `kotlin-serialization` 플러그인과 `kotlinx-serialization-json`만 추가한다(복습 로그의 상태 직렬화, 5절).

## 1. 모듈과 패키지

| 모듈 | 종류 | 패키지 | 의존 |
|------|------|--------|------|
| `:core:srs` | 순수 Kotlin JVM | `com.jlptstudy.srs` | kotlinx-serialization-json만 |
| `:core:quiz` | 순수 Kotlin JVM | `com.jlptstudy.quiz` | 없음(3순위에서 채움) |
| `:app` | Android | `com.jlptstudy.app` | `:core:srs`, `:core:quiz`, Compose, Room, Navigation |
| `content/pipeline/` | Python 스크립트 | — | 표준 라이브러리 |

`:app` 내부 패키지(기능별 하위 모듈로 나누지 않는다. 빌드가 느려지면 그때 나눈다):

```
com.jlptstudy.app
  AppContainer.kt          수동 DI: clock, zone, 두 DB, 저장소, Scheduler, speakerFactory, AppState
  data/content/            ContentDatabase, 엔티티, DAO(SELECT만), ContentRepository
  data/study/              StudyDatabase, 엔티티, DAO, StudyRepository, 마이그레이션
  ui/theme/                테마, JpTypography·JpSpan(ja-JP), 토큰
  ui/common/               RubyText·Furigana(후리가나), JpText, JpSpans(혼용 문장), ProvenanceLabel, StatusScreen
  ui/level/ ui/home/ ui/review/ ui/browse/ ui/settings/ ui/about/ ui/error/   화면 + ViewModel
  nav/                     라우트(@Serializable), NavHost
```

시간·시계: `AppContainer`가 `clock: Clock = Clock.systemUTC()`와 `zone: () -> ZoneId = { ZoneId.systemDefault() }`를 가진다. 여행 중 시간대가 바뀌면 다음 계산부터 새 시간대가 쓰인다. `System.currentTimeMillis()` 직접 호출 금지.

`Speaker`는 앱 범위 객체가 아니다. `AppContainer`는 `speakerFactory: () -> Speaker`만 가지고, 청해 ViewModel이 생성 때 하나 만들어 `onCleared`에서 그 인스턴스만 `shutdown()`한다(8.2).

## 2. SRS 알고리즘 선택: FSRS-6

### 2.1 비교(srs-scheduler 1절 기준, 확인일 2026-11-02)

| 기준 | FSRS-6 | SM-2(원형) | 근거 |
|------|--------|------------|------|
| 기억 예측 정확도 | srs-benchmark 공식 표(같은 날 복습 제외, 9,999 컬렉션·349,923,850 평가 복습): FSRS-6(21 파라미터, 사용자별 최적화) Log Loss 0.3460±0.0042, RMSE(bins) 0.0653±0.0011, AUC 0.7034±0.0023. FSRS-4.5 0.3625. **SM-2는 현재 공식 표에 행이 없다.** 공식 표 밖 자료로 벤치마크 기여자 블로그가 "FSRS-6 (with recency weighting) has a 99.6% superiority over Anki SM-2"라고 적고 SM-2에는 "not-so-rigorous interval-to-probability converter"를 얹었다고 밝힌다 | [srs-benchmark README](https://github.com/open-spaced-repetition/srs-benchmark) ; [Expertium Benchmark](https://expertium.github.io/Benchmark.html)(2차 자료, 보조 근거로만) |
| 기본값만으로 쓸 수 있는가 | 기본 파라미터 21개가 참조 구현에 들어 있다. 공식 표에 FSRS-6 기본 파라미터 행은 없고 FSRS-7 기본 파라미터 행(0.3620)만 있어, 기본값 FSRS-6의 정확도는 공식 수치로 확인하지 못했다. 기기 내 최적화는 하지 않는다(복습 로그를 남겨 나중에 가능하게, 5절) | [py-fsrs README](https://github.com/open-spaced-repetition/py-fsrs) "a set of 21 model weights" |
| 구현·검증 난도 | 상태 변수: 안정성·난이도·단계·기한·마지막 복습. 부동소수 계산(`pow`, `exp`). 참조 구현과 테스트 값 있음: py-fsrs `tests/test_basic.py` `test_review_card` 간격 `[0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21]`, `test_memo_state` 안정성 53.62691·난이도 6.3574867(abs 1e-4) | [py-fsrs tests/test_basic.py](https://github.com/open-spaced-repetition/py-fsrs/blob/main/tests/test_basic.py) |
| 참조 구현 라이선스 | py-fsrs: MIT, "Copyright (c) 2022 Open Spaced Repetition". 이식한 코드·옮긴 테스트 값에 저작권·허락 고지를 포함해야 한다 | [py-fsrs LICENSE](https://github.com/open-spaced-repetition/py-fsrs/blob/main/LICENSE) "shall be included in all copies or substantial portions of the Software." |
| 평가 버튼 수 | 4단계(Again 1, Hard 2, Good 3, Easy 4) | py-fsrs README `Rating.Again (==1)` … |
| 저장 상태 | 카드별 안정성·난이도·단계·기한. 복습 로그(평가·시각)만 있으면 다른 알고리즘으로 다시 계산 가능 | — |
| SM-2 측 | 0~5 6단계 평가, I(1)=1, I(2)=6, I(n)=I(n-1)·EF, EF 초기 2.5·하한 1.3, q<3이면 처음부터. 단순하고 정수 위주. 확률 출력이 없어 정확도 비교가 어렵다 | [SuperMemo SM-2 설명](https://super-memory.com/english/ol/sm2.htm)(Wozniak 1990, 웹판 1998) |

**결정: FSRS-6.** 공개 벤치마크에서 SM-2 대비 정확도 근거가 있고(공식 표의 SM-2 행 부재는 한계로 남김), 4단계 버튼이 초보 학습자에게 SM-2 6단계보다 고르기 쉽고, MIT 참조 구현과 테스트 값이 있어 테스트 우선 이식이 가능하다. 결정은 승인 항목 AR-U01이다(평가 버튼 수 포함, ui-design 14절은 이 ID로 의견만 적는다).

**최신 판 관련 주의:** srs-benchmark에는 FSRS-7 행이 있으나 py-fsrs 최신 릴리스(v6.3.2) 노트와 알고리즘 위키([awesome-fsrs The-Algorithm](https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm))는 FSRS-6까지만 다룬다. 공개된 참조 구현이 있는 FSRS-6을 고른다. `schedulerVersion`으로 판을 기록하므로 나중에 FSRS-7로 바꿔도 로그로 다시 계산할 수 있다.

**쓰지 않는 것:** 커뮤니티 Kotlin 라이브러리(FsrsKt 등)는 공식 저장소가 아니고 kotlinx-datetime 의존이 생겨 쓰지 않는다. 직접 이식은 수백 줄이고 테스트 값으로 고정된다.

### 2.2 이식 기준

- 기준 코드: py-fsrs v6.3.2 `fsrs/scheduler.py`. 구현자는 이식 시점의 커밋 해시를 `Fsrs6Scheduler.kt`와 테스트 파일 머리에 MIT 고지 전문과 함께 적는다.
- 기본값(py-fsrs 그대로): 파라미터 21개, `desiredRetention = 0.9`, 학습 단계 1분·10분, 재학습 단계 10분, `maximumIntervalDays = 36500`, 퍼즈 켬. 학습·재학습 단계의 Hard 규칙도 그대로 옮긴다: 단계 0에서 단계가 2개 이상이면 `(steps[0] + steps[1]) / 2`(기본값으로 5분 30초), 단계가 1개면 `steps[0] × 1.5`. 그래서 단계 간격은 정수 분이 아니라 `Duration`이다(3절).
- py-fsrs와 **의도적으로 다른 점**(테스트에 명시):
  1. 경과 일수 = `studyDayOf(reviewedAt) − studyDayOf(lastReviewAt)`(학습일 차이). py-fsrs는 `(review − last_review).days`(실시간 차 내림). 같은 학습일이면 0 → 같은 날 공식(short-term)을 탄다.
  2. 복습 상태 기한 = `startOfStudyDay(studyDayOf(reviewedAt) + scheduledDays)`. py-fsrs는 `review + interval`. 그날 학습일이 시작되면 바로 기한이 된다. 학습·재학습 단계 기한은 py-fsrs와 같이 `reviewedAt + 단계 간격`.
  3. 퍼즈: py-fsrs의 `FUZZ_RANGES`(2.5–7: 0.15, 7–20: 0.1, 20–: 0.05, 2.5일 미만 퍼즈 없음, 하한 2) 계산식은 그대로, 난수만 `java.util.Random(seed)`, `seed = cardId.hashCode().toLong() * 31 + reps`로 결정적으로.
  4. py-fsrs의 UTC 강제(`ValueError`)는 `Instant` 타입으로 대신한다.
- 참조 벡터 비교 대상(퍼즈 끔, 시간대 UTC, 경계 0분):
  - `test_review_card`: 각 평가의 `ReviewRecord.scheduledDays` 열이 `[0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21]`과 같다(학습 단계는 0). 다음 복습 시각은 직전 결과의 이 앱 `dueAt`이다. **`dueAt` 시각 자체는 py-fsrs와 다르다**(복습 상태 기한이 학습일 시작 00:00Z로 당겨짐). 그래서 `(dueAt − lastReview)`를 일로 내림한 값은 비교하지 않는다. 대신 "py-fsrs 방식 기한(`reviewedAt + scheduledDays일`)의 학습일 = 이 앱 `dueAt`의 학습일"을 각 단계에서 확인한다.
  - `test_memo_state`: 테스트에 적힌 `timedelta(days=ivl)` 간격(`[0, 0, 1, 3, 8, 21]`)대로 `reviewedAt`을 옮겨 넘긴다(`dueAt`에 복습하지 않는다). 최종 stability 53.62691·difficulty 6.3574867(abs 1e-4).
  - 이 조건에서 같아야 하는 것은 `scheduledDays`·stability·difficulty이고, 다른 것은 복습 상태 `dueAt`의 하루 중 시각뿐이다. 다르면 차이 원인을 보고한다(테스트를 구현에 맞춰 고치지 않는다).

## 3. 시간과 학습일(계약)

| 값 | 타입·단위 | 기준 |
|----|-----------|------|
| 모든 저장 시각 | `Long` epoch ms, UTC | `*EpochMs` 접미사 |
| 학습일 | `Long` = `LocalDate.toEpochDay()` | 아래 정의(지역 시각 기준) |
| 학습일 경계 | `Int` 분, 기본 240(04:00), 저장 범위 0..720. 화면은 정시만 고른다(0..720 중 60의 배수, 00:00~12:00) | 설정 |
| 일 간격 | `Int` 일(학습일 수) = `scheduledDays` | |
| 학습·재학습 단계 간격 | `java.time.Duration`(ms 정밀도) | 기한 = `reviewedAt + 간격` |
| 오늘 기한 판정 | 복습 상태: `dueAtEpochMs < startOfStudyDay(today + 1)` / 학습·재학습: `dueAtEpochMs <= now`이면 기한, `now` 뒤 `now + learnAheadMinutes`(기본 20) 이하이면 learn-ahead(새 카드 뒤에 놓는다) | `SessionPlanner` |
| 오늘 새 카드 한도 | `new_cards_per_day + (extra_new_cards_study_day == today ? extra_new_cards : 0)` − 오늘 도입 수, 0 미만 없음 | 5절 `user_settings`, `SessionPlanner.selectNew` |

정의(지역 시각 기준, Instant에서 빼지 않는다):

```kotlin
studyDayOf(t)       = t.atZone(zone).toLocalDateTime().minusMinutes(boundary).toLocalDate().toEpochDay()
startOfStudyDay(d)  = LocalDate.ofEpochDay(d).atStartOfDay().plusMinutes(boundary).atZone(zone).toInstant()
```

불변식(테스트): 모든 `t`에서 `startOfStudyDay(studyDayOf(t)) <= t < startOfStudyDay(studyDayOf(t) + 1)`.

서머타임: `startOfStudyDay`의 `atZone`은 존재하지 않는 지역 시각을 `ZonedDateTime` 규칙(뒤로 밀림), 겹치는 시각은 앞 오프셋으로 정한다. 테스트는 `America/New_York` 2025-03-09(봄)·2025-11-02(가을) 전환일과 경계 0·120·240분에서, 전환일 전후 3일을 1분 간격으로 훑어 불변식이 성립함을 확인한다.

경계 계산은 `:core:srs`의 `StudyDayRule` 한 곳에만 둔다. `:app`은 이를 호출만 한다.

## 4. 콘텐츠 DB(번들, 읽기 전용)

### 4.1 안정 ID

| 대상 | ID 형식 | 예 | 안정성 근거 |
|------|---------|----|-------------|
| JMdict 어휘 | `jm:<ent_seq>` | `jm:1358280` | JMdict `ent_seq`는 판이 바뀌어도 유지 |
| 직접 작성 어휘(사전에 없는 항목, S-06 N1의 빈 `jmdict_seq` 14행 등) | `av-n<급>-<4자리>` | `av-n1-0003` | 작성자가 고정 |
| 한자 | 문자 자체 | `水` | 문자 |
| 문법 | `gr-n<급>-<4자리>` | `gr-n5-0012` | jlpt-content-authoring 3절 |
| 문항 | `q-n<급>-<영역>-<4자리>` | `q-n5-vocab-0040` | 같음 |
| 청해 | `ls-n<급>-<4자리>` | `ls-n5-0003` | 같음 |
| 샘플(개발용) | `jm:<ent_seq>` 또는 `av-sample-<4자리>` | | 릴리스 DB에 섞이지 않음(`kind`) |

카드 ID = `"<카드종류>:<콘텐츠ID>"`. 카드 종류: `vr`(어휘 일→한 인식, 기본 켬), `vp`(어휘 한→일 회상, 기본 끔), `kr`(한자 → 읽기·한국어 음·쓰인 단어, 기본 켬), `gr`(문법, 2순위, 기본 켬). 예: `vr:jm:1358280`, `kr:水`. 같은 단어의 다른 방향은 다른 카드다. 켬/끔은 `user_settings`(5절).

`content/ids/<kind>/<종류>.txt`(kind는 sample·release)에 배포한 ID를 커밋하고, 사라진 ID는 `retired` 표에 남긴다(검사 6.5).

### 4.2 테이블(Room 엔티티 = 파이프라인 스키마의 원본)

`ContentDatabase` 버전 번호는 미리 정한다. 콘텐츠 DB 버전을 올리는 마일스톤은 `dependsOn`으로 한 줄로 선다.

| 버전 | 마일스톤 | 추가 테이블 |
|------|----------|-------------|
| 1 | M2 | 아래 표 전부 |
| 2 | M12 | `kanji_stroke`(9.4) |
| 3 | M14 | `grammar`, `grammar_example`(9.1) |
| 4 | M19 | `question`, `exam_template`(9.2) |
| 5 | M23 | `listening`(9.3) |

**출처 열 묶음 P**(직접 작성 콘텐츠 행 공통 — `grammar`·`question`·`listening`): `source_id TEXT NOT NULL`, `ko_source TEXT NOT NULL`, `ko_method TEXT NOT NULL`, `ko_review_status TEXT NOT NULL`, `ko_reviewed_at TEXT NULL`, `ko_reviewer_kind TEXT NULL`, `needs_review INTEGER NOT NULL`. `ko_*`는 `vocab_meaning`과 같은 이름·허용 값이고 그 행의 한국어 텍스트(설명·해설·번역) 전체의 출처다. 작성 파일의 `method`·`reviewStatus`·`reviewedAt`·`reviewerKind`·`needsReview`가 그대로 들어가고 `ko_source`는 `authored`다.

파이프라인은 손으로 `CREATE TABLE`을 쓰지 않는다. `app/schemas/com.jlptstudy.app.data.content.ContentDatabase/`에서 **가장 큰 번호의 `<버전>.json`**(= `@Database(version)`, 버전은 내려가지 않는다)의 `createSql`·`indices`·`setupQueries`를 그대로 실행하고 `PRAGMA user_version = <database.version>`을 맞춘다. 새 엔티티의 빈 테이블은 파이프라인 코드 변경 없이 생긴다. `check.sh`는 에셋의 `user_version`·`room_master_table` 해시·`sqlite_master`를 그 JSON과 대조한다. 모든 열 `NOT NULL`은 표시한 것만.

**`content_meta`** — `key TEXT PK`, `value TEXT NOT NULL`
| key | value |
|-----|-------|
| `kind` | `sample` / `release` |
| `content_version` | 입력 해시에서 만든 문자열(빌드 시각 넣지 않음). 화면의 "콘텐츠 판" |
| `levels` | 포함 급수, 예 `5,4` |
| `level_source` | 급수 목록 매니페스트 ID(D-06 선택, 샘플은 `authored`) |
| `sources` | JSON `{"S-01":{"version":"…","files":{"JMdict_e.gz":"<sha256>"},"snapshot":"<sha256>"},…}` |

**`vocab`**
| 열 | 타입 | 내용 |
|----|------|------|
| `id` | TEXT PK | 4.1 |
| `written` | TEXT NULL | 대표 표기(한자 포함). 가나 전용 어휘는 NULL |
| `reading` | TEXT NOT NULL | 대표 읽기(가나) |
| `level` | INTEGER NOT NULL | 5=N5 … 1=N1. 여러 급수 목록에 나오면 가장 쉬운 급수(큰 수) |
| `sort_key` | INTEGER NOT NULL | 급수 안 새 카드 도입 순서(급수 목록 순서, 동률은 `id`) |
| `pos` | TEXT NOT NULL | 첫 sense의 JMdict 품사 코드, 쉼표 구분(예 `n,vs`). 한국어 이름표는 앱의 고정 표 |
| `source_id` | TEXT NOT NULL | `S-01` 또는 `authored` |
| `level_source_id` | TEXT NOT NULL | 급수 근거 매니페스트 ID 또는 `authored` |
인덱스: `(level, sort_key)`

**`vocab_meaning`** — 한국어 뜻(K-05 행 기록)
| 열 | 타입 | 내용 |
|----|------|------|
| `vocab_id`, `ord` | TEXT, INTEGER, PK(둘) | 뜻 순서(학습에 필요한 뜻 먼저) |
| `text_ko` | TEXT NOT NULL | 한국어 뜻. 일본어 구간은 4.4 규칙으로 찾는다(표시 기호 없음) |
| `source_id` | TEXT NOT NULL | `S-01`(번역 파생) 또는 `authored` |
| `ko_source` | TEXT NOT NULL | `jmdict-translated` / `authored` / 자료 ID |
| `ko_method` | TEXT NOT NULL | `llm:<모델>@<YYYY-MM-DD>` / `human` |
| `ko_review_status` | TEXT NOT NULL | `unreviewed` / `sampled` / `reviewed` |
| `ko_reviewed_at` | TEXT NULL | `YYYY-MM-DD` |
| `ko_reviewer_kind` | TEXT NULL | `agent` / `human` / NULL. D-03 기본값 제안이 채택되면 쓰고, 아니어도 NULL로 둘 수 있게 NULL 허용 |

한국어 뜻이 하나도 없는 어휘는 릴리스 DB에 넣지 않는다(영어 뜻은 번들하지 않는다). 그래서 릴리스 DB에서 "한국어 뜻 없는 어휘"는 생기지 않는다(생기면 빌드 결함, 검사 6.5). 급수 확장은 뜻이 준비된 급수 단위로 한다. 화면 이름 대응: ui-design의 `generationMethod` = `ko_method`, `meaningSource` = `ko_source`.

**`kanji`**
| 열 | 타입 | 내용 |
|----|------|------|
| `literal` | TEXT PK | 문자(4.3의 한자 판정 규칙을 통과한 것만) |
| `level` | INTEGER NOT NULL | 그 한자를 `written`에 포함한 어휘 중 가장 쉬운 급수(4.3) |
| `stroke_count` | INTEGER NOT NULL | KANJIDIC2 `misc/stroke_count` 첫 값 |
| `freq` | INTEGER NULL | KANJIDIC2 `misc/freq`(정렬 보조) |
| `on_readings` | TEXT NOT NULL | JSON 배열(`ja_on`) |
| `kun_readings` | TEXT NOT NULL | JSON 배열(`ja_kun`, 원문 표기 `.`·`-` 유지) |
| `korean_readings` | TEXT NOT NULL | JSON 배열(`korean_h`, 없으면 `[]`). 출처 화면에 S-02.korean-credit 표시 |
| `sort_key` | INTEGER NOT NULL | 급수 안 순서(4.3) |
| `source_id` | TEXT NOT NULL | `S-02` 또는 `authored`(샘플) |
인덱스: `(level, sort_key)`

**`vocab_kanji`** — `kanji TEXT`, `vocab_id TEXT`, PK(둘), `source_id TEXT NOT NULL`(`S-01` 또는 `authored`(샘플)). 인덱스 `(kanji)`. 한자 상세의 "이 한자가 쓰인 단어".

**`retired`** — `id TEXT PK`, `retired_in TEXT NOT NULL`(content_version). 학습 기록이 가리키는 옛 ID를 화면에서 빼는 근거.

2순위 이후 테이블(문법·예문·문항·청해·필순)은 9절 초안을 따라 위 버전 표의 마일스톤에서 엔티티를 추가하고 `ContentDatabase` 버전을 올린다. 콘텐츠 DB는 매번 통째로 교체하므로(4.5) 마이그레이션이 필요 없다. 대신 **버전을 올리는 마일스톤은 같은 마일스톤 안에서 번들 에셋을 다시 만든다**(4.5 "버전을 올릴 때").

**쓰는 필드:** KANJIDIC2는 `literal`, `misc/stroke_count`, `misc/freq`, `ja_on`, `ja_kun`, `korean_h`만 쓴다(매니페스트 S-02 `usedFields`와 같음). JMdict는 `ent_seq`, `keb`, `reb`, 첫 sense `pos`, sense `misc`(`uk`), `stagk`·`stagr`, 영어 `gloss`만 쓴다. `ke_pri`·`re_pri`는 쓰지 않는다(매니페스트 S-01 `usedFields`와 같음).

### 4.3 한자 급수(파생)

확정 가능한 새 급수 한자 목록이 후보에 없다(S-02.jlpt 구 4단계 사용 안 함, S-08 제외 권고, S-05 한자 페이지는 보관본만). 그래서 한자 급수는 "선택한 급수 목록의 어휘 표기에 처음 나오는 급수"로 파생하고, 화면·고지에 "이 급수 단어에 쓰인 한자"로 적는다(N-09 고지에 함께). 한자 목록 자료가 따로 확정되면 `kanji.level`의 입력만 바꾼다. 승인 항목 AR-U03.

파생 규칙(결정적):
- 한자 = `written` 안의 문자 중 **KANJIDIC2 `literal`에 있는 문자만**. `々`·`ヶ`·`〆` 같은 기호와 KANJIDIC2에 없는 문자는 넣지 않고, KANJIDIC2에 없는 CJK 문자는 `_workspace/03_content-engineer_<마일스톤>_kanji_missing.tsv`(문자, 어휘 ID)에 남긴다.
- `sort_key` = 그 한자가 처음 나오는 어휘의 `sort_key`. 다시 번호를 매기지 않는다(어휘와 같은 번호 체계여야 `newCardOrder`에서 단어 뒤에 한자가 온다). 같은 단어에서 나온 한자끼리의 동률은 앱이 `id`로 정한다.

한자 카드·상세에 뜻 행을 두지 않는다(한국어 훈도 영어 뜻도 없음). D-02는 JMdict 영어 뜻 번역만 허용했고 KANJIDIC2 영어 `meaning`은 번들하지 않는다. 대신 읽기, 한국어 음(`korean_h`), 획수, 쓰인 단어(한국어 뜻 포함)를 보인다. 승인 항목 AR-U04. 영어 뜻을 보이기로 승인되면 `vocab_gloss_en`·`kanji.meanings_en` 열, parse 범위, 크기 예산을 이 문서에 더하는 새 마일스톤을 만든다.

### 4.4 후리가나와 한·일 혼용 텍스트

- 어휘 표제어: `:app`의 `Furigana.fromHeadword(written, reading): List<RubySegment>` 하나가 송리가나를 나눈다. `written`과 `reading`의 **앞뒤 공통 가나**를 잘라 내어 가운데 구간에만 루비를 얹고, 앞뒤 일치로 나눌 수 없으면 단어 전체를 한 구간(묶음 루비)으로 둔다. 외부 대응 자료를 쓰지 않고 파이프라인도 계산하지 않는다. 기대값(단위 테스트): `食べる/たべる` → `[食/た, べる/-]`, `お茶/おちゃ` → `[お/-, 茶/ちゃ]`, `今日/きょう` → `[今日/きょう]`, `引き出し/ひきだし` → `[引き出/ひきだ, し/-]`.
- 직접 작성 문장(문법 예문·문항·청해 대본): 본문에 `{漢字|かんじ}` 표기. `{`·`}`·`|`는 본문에 쓰지 않는다. `Furigana.parse(text): List<RubySegment>`(`data class RubySegment(val base: String, val ruby: String?, val emphasis: Boolean = false)`) 하나가 해석한다. 파이프라인 형식 검사가 괄호 짝을 확인한다.
- **대상 구간(밑줄) 표기 `[[…]]`**: 일본어 필드에서 밑줄 2 dp + 굵기 500으로 그릴 구간(ui-design 6.9.1 문법 카드 예문의 문법 형식 부분, 6.10 문항 지문의 대상 부분). 루비를 안에 둘 수 있다(`[[{漢字|かんじ}]]`). 루비 base 안에 `[[`를 두지 않는다(중첩 금지). `Furigana.parse`는 구간 경계에서 세그먼트를 나누고 안쪽 세그먼트에 `emphasis = true`를 준다. 짝이 안 맞으면 루비와 같이 원문 그대로 둔다. `[[`·`]]`는 이 뜻 말고 본문에 쓰지 않는다. 짝·개수 규칙은 파이프라인 `authored` 단계가 검사한다(9.1·9.2). `RubyText`가 `emphasis` 세그먼트에 밑줄·굵기를 그린다.
- 그리기는 `RubyText` 컴포저블 하나(ui-design 5.2).
- **한국어 텍스트 안 일본어 구간**(한국어 필드: `text_ko`, `meaningKo`, `explanationKo`, `whyWrongKo`, 예문 번역 `ko`, 접속 표 `posKo`, 비교 `pair[].ko`): 표시 기호를 쓰지 않고 결정적 규칙으로 찾는다. 문자 집합 **J** = 히라가나(U+3040–309F), 가타카나(U+30A0–30FF, `ー`·`・` 포함), CJK 통합 한자(U+4E00–9FFF, 확장 A U+3400–4DBF), `々`, `〜`(U+301C), `「」『』`. J에 드는 글자가 한 글자 이상 이어진 구간 = `ja-JP`. 공백·한글·라틴 문자·숫자·그 밖의 문장 부호에서 구간이 끝난다. 이 집합은 ui-design 4.4와 같아야 한다(정본은 이 문서).
- **한국어 필드에는 루비 표기 `{…|…}`와 대상 구간 `[[…]]`를 쓰지 않는다.** 한국어 필드는 `JpSpans.annotate`(AnnotatedString)로만 그리며 `RubyText`를 거치지 않는다. 읽기가 필요한 일본어는 예문·접속 표의 `example`(일본어 필드)에 둔다. 파이프라인 `authored`·`ko-gloss` 단계는 한국어 필드에 `{`·`|`·`}`가 있으면 `FAIL: authored <id> ruby in ko field`, `[[`·`]]`가 있으면 `FAIL: authored <id> emphasis in ko field`로 멈춘다.
- 이 앱의 한국어 문장은 한자를 쓰지 않으므로(작성 규칙) 오판이 적다. 구현은 `:app`의 `JpSpans.annotate(text): AnnotatedString` 한 곳. 파이프라인 `authored`·`ko-gloss` 단계는 한국어 필드에서 찾은 일본어 구간을 보고서에 나열한다(검토용, 실패 아님). "한국어 필드에 한자가 있으면 경고" 검사는 두지 않는다(일본어 구간에는 원래 한자가 있어 정상 콘텐츠에서도 늘 울린다). ui-design의 `[ja]…[/ja]`는 문서 표기일 뿐이다.

### 4.5 열기와 교체

- 에셋: `app/src/main/assets/content/content.db`, `content.db.sha256`(16진 64자, 파이프라인이 씀), `attribution.json`.
- 앱은 `content.db.sha256` 앞 12자로 파일 이름 `content_<hash12>.db`를 정해 `Room.databaseBuilder(...).createFromAsset("content/content.db")`로 연다. 해시가 바뀌면(앱 업데이트·개발 중 DB 교체) 새 파일로 복사되고, 열기에 성공한 뒤 다른 `content_*.db`를 지운다. 학습 기록 DB는 별도 파일이라 영향 없다.
- 앱 시작 때 `AppContainer`가 두 DB를 `Dispatchers.IO`에서 실제로 연다(`openHelper.writableDatabase`). 실패하면 `AppState.ContentDbFailed`/`RecordDbFailed`(7절, 화면 S90). "다시 시도"는 같은 열기를 다시 한다. 학습 기록 DB 실패 때 기록을 지우는 경로를 두지 않는다.
- DAO는 `@Query SELECT`만 둔다.
- 학습 기록과 콘텐츠는 다른 DB라 SQL 조인하지 않는다(`ATTACH`는 Room 밖이라 쓰지 않는다). **바인드 변수 한도:** minSdk 26 기기의 SQLite는 쿼리당 변수 999개가 한도이고 Room은 `IN (:list)`를 나눠 보내지 않는다. 그래서 (1) ID 목록으로 조회하는 DAO 호출(`cardContents`, `states`)은 저장소가 **900개 단위로 나눠** 부르고, (2) "이미 카드가 있는 ID 제외"는 SQL에 넘기지 않고 메모리에서 한다(8.2 `newCardOrder`). 없거나 `retired`인 ID는 결과에서 뺀다(학습 기록은 지우지 않는다). 홈 수치와 복습 대기열이 쓰는 기한 카드 집합도 이 규칙을 거친 한 함수(8.2 `SessionCards.load`)로만 만든다.
- **버전을 올릴 때:** `ContentDatabase` 버전을 올리는 마일스톤은 같은 마일스톤에서 (1) 새 스키마 JSON을 내보내고, (2) 현재 에셋과 같은 `kind`로 `python3 -I content/pipeline/build.py`(스냅샷 이후 단계, 6.2)를 돌려 `content.db`를 다시 만들어 에셋에 넣고 `content.db.sha256`을 갱신하고, (3) 번들 DB를 `createFromAsset`으로 여는 Robolectric 테스트를 통과시킨다. 에셋의 `user_version`이 옛 버전으로 남으면 Room이 열기에 실패한다(마이그레이션 없음).

### 4.6 라이선스 반영(D-01, L-01, N-02)

- **L-01.nodrm:** 콘텐츠 DB와 `attribution.json`은 평문으로 둔다. SQLCipher 등 DB 암호화, 에셋 난독화·압축 독자 형식, DB 이용을 제한하는 약관 문구를 쓰지 않는다. R8은 에셋을 건드리지 않는다. 리뷰 기준에 포함한다.
- **D-01 분리:** DB 산출물은 `app/src/main/assets/content/`에만 두고 앱 소스와 섞지 않는다. 예외는 저작자 표시 최소 문자열 `app/src/main/res/values/attribution_fallback.xml`(6.6, 파이프라인 생성) 하나다. 파이프라인은 배포용 묶음 `content/dist/jlpt-content-<content_version>.zip`(커밋 안 함: `content.db`, `attribution.json`, `LICENSE.md` — CC BY-SA 4.0 URI, 자료별 출처·라이선스·변경 내용)을 만든다. 어디에 공개할지는 승인 항목 AR-U07.
- **N-02.checksum:** fetch는 매니페스트 `sha256`(자료에 `files`가 있으면 파일마다)과 대조하고 다르면 멈춘다. 비어 있으면 계산값을 출력하고 매니페스트 갱신을 보고서로 요청한다(파이프라인이 매니페스트를 고치지 않는다). **JMdict·KANJIDIC2의 원 URL은 매일, Tatoeba는 매주 덮어써지므로 판을 고정하지 않는다.** 판 고정은 매니페스트 `pinning`(6.7)에 적은 방법으로 한다. 기본은 커밋한 스냅샷(방식 A)이고, `content_meta.sources`에 판·원본 해시·스냅샷 해시를 남긴다.
- **D-07 대비:** `build.py refresh-snapshot --refresh <id>`가 최신 파일을 받아 새 해시·헤더 판을 `_workspace/`에 보고하고 새 스냅샷을 만든다. 매니페스트를 갱신하고 새 스냅샷을 커밋하는 것이 콘텐츠 갱신이다(stale gloss 처리 6.2). 주기는 정하지 않는다.
- 앱 코드 라이선스(D-05)는 정하지 않는다. 앱에 들어가는 제3자 코드·자산의 고지는 `app/src/main/assets/licenses/`에 손으로 커밋한다(6.6 끝).

## 5. 학습 기록 DB(Room)

`StudyDatabase` 버전 1, 파일 이름 `study.db`, `exportSchema = true`(`app/schemas/`), 릴리스에서 `fallbackToDestructiveMigration` 금지. 스키마를 바꾸는 마일스톤은 `Migration`과 Robolectric 마이그레이션 테스트를 함께 낸다(M19가 첫 사례: v1→v2).

**`card_state`**
| 열 | 타입 | 내용 |
|----|------|------|
| `card_id` | TEXT PK | 4.1 |
| `kind` | TEXT NOT NULL | `vr`/`vp`/`kr`/`gr` |
| `content_id` | TEXT NOT NULL | 콘텐츠 안정 ID |
| `phase` | INTEGER NOT NULL | 0 새 카드 · 1 학습 · 2 복습 · 3 재학습 |
| `step` | INTEGER NULL | 학습·재학습 단계 색인 |
| `stability` | REAL NULL | FSRS 안정성(일) |
| `difficulty` | REAL NULL | FSRS 난이도(1..10) |
| `due_at_epoch_ms` | INTEGER NOT NULL | |
| `last_review_at_epoch_ms` | INTEGER NULL | |
| `reps`, `lapses` | INTEGER NOT NULL | |
| `scheduler_version` | TEXT NOT NULL | 예 `fsrs6/py-fsrs-6.3.2` |
인덱스: `(due_at_epoch_ms)`, `(kind, content_id)`

카드 행은 첫 평가 때 만든다. "새 카드"는 대상 급수 콘텐츠 중 `card_state`가 없는 항목이다.

**`review_log`**(추가만)
| 열 | 타입 | 내용 |
|----|------|------|
| `id` | INTEGER PK 자동 | 내부 키(콘텐츠를 가리키지 않으므로 허용). 단조 증가 — 세션 경계로 쓴다 |
| `card_id` | TEXT NOT NULL | |
| `reviewed_at_epoch_ms` | INTEGER NOT NULL | |
| `rating` | INTEGER NOT NULL | 1..4 |
| `phase_before` | INTEGER NOT NULL | 오늘 도입한 새 카드 수 계산용 |
| `elapsed_days` | INTEGER NOT NULL | 학습일 차이 |
| `scheduled_days` | INTEGER NOT NULL | 학습 단계면 0 |
| `state_before_json`, `state_after_json` | TEXT NOT NULL | `CardState` 직렬화 |
| `scheduler_version` | TEXT NOT NULL | |
| `undone` | INTEGER NOT NULL DEFAULT 0 | 되돌린 평가 |
인덱스: `(card_id)`, `(reviewed_at_epoch_ms)`

- 평가 한 번 = `card_state` upsert + `review_log` insert를 한 `@Transaction`으로 즉시.
- 되돌리기: 세션 시작 때 `SELECT MAX(id) FROM review_log`(없으면 0)를 기억한다. 되돌리기 = `id > 그 값 AND undone = 0`인 로그 중 **`id`가 가장 큰 것**의 `state_before_json`을 복원(새 카드였으면 `card_state` 행 삭제) + 그 로그 `undone = 1`, 한 트랜잭션. 로그는 지우지 않는다. 기기 시각이 바뀌어도 대상이 흔들리지 않는다(시각으로 고르지 않는다).
- 오늘 도입한 새 카드 = `undone = 0 AND phase_before = 0 AND reviewed_at` ∈ 오늘 학습일 구간인 로그의 `card_id` 목록(수와 콘텐츠 ID를 함께 쓴다, 8.1 `selectNew`).

**`user_settings`**(단일 행 `id = 1`). 화면이 쓰는 설정은 모두 v1에 둔다(DataStore를 추가하지 않으려고 Room에 둔다):

| 열 | 타입·기본값 | 범위·뜻 |
|----|-------------|---------|
| `day_boundary_minutes` | INT 240 | 0..720, 화면은 60의 배수만 |
| `new_cards_per_day` | INT 10 | 5..50, 5의 배수(ui-design S01·S08과 같음, AR-U06) |
| `extra_new_cards` | INT 0 | "새 카드 5장 더"로 오늘만 늘린 수 |
| `extra_new_cards_study_day` | INT NULL | `extra_new_cards`가 유효한 학습일. 오늘이 아니면 무시 |
| `target_levels` | TEXT '5' | 쉼표 구분. 첫 실행 S01의 미리 선택 값 |
| `desired_retention` | REAL 0.9 | 0.70..0.97. 화면에 두지 않는다(MVP) |
| `max_interval_days` | INT 36500 | |
| `learn_ahead_minutes` | INT 20 | |
| `vocab_recognition_enabled` | INT 1 | `vr` |
| `kanji_cards_enabled` | INT 1 | `kr` |
| `vocab_recall_enabled` | INT 0 | `vp` |
| `grammar_cards_enabled` | INT 1 | `gr`(M17부터 화면에 보임) |
| `front_furigana` | INT 0 | 앞면 후리가나 |
| `body_furigana` | INT 1 | 본문 후리가나 |
| `listening_script_first` | INT 0 | 청해 대본 먼저 보기(ui-design 6.8·6.11, 청해 콘텐츠가 번들에 있을 때만 화면에 보임) |
| `theme` | TEXT 'system' | `system`/`light`/`dark` |
| `onboarding_done` | INT 0 | 0이면 시작 화면이 S01 |
| `first_notice_dismissed` | INT 0 | 홈 첫 안내 카드 닫힘 |

- 카드 종류 네 가지가 모두 꺼지는 값은 저장하지 않는다(저장소가 거부, 화면 문구는 ui-design 6.8).
- `target_levels`가 비는 값(급수 0개)도 저장하지 않는다(저장소가 거부. 화면은 ui-design 5.9대로 "시작"/"저장"을 비활성). 읽을 때는 `1..5` 밖의 값을 버린다. 그래서 홈 `NO_LEVEL_SELECTED`(7절)는 정상 경로로는 생기지 않고, 백업 복원 등으로 손상된 값이 들어왔을 때의 **방어용 상태**다.
- "새 카드 5장 더": `extra_new_cards_study_day == today`면 `extra_new_cards += 5`, 아니면 `(today, 5)`로 쓴다. 프로세스가 죽어도 그날 한도가 유지된다.

### 5.1 매니페스트: 패키지 공개 범위·기기 백업

**패키지 공개 범위 선언(`<queries>`):** targetSdk 37(API 30+ 패키지 공개 범위 규칙)에서 `TextToSpeech`가 엔진 서비스를 찾고 연결하려면 `AndroidManifest.xml`에 `<queries><intent><action android:name="android.intent.action.TTS_SERVICE" /></intent></queries>`(= `TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE`)가 있어야 한다. 이것은 권한(`<uses-permission>`)이 아니라 다른 앱(TTS 엔진)이 보이게 하는 선언이며 사용자 동의·스토어 권한 표시가 없다. 근거: [Package visibility use cases — Connect to a text-to-speech engine](https://developer.android.com/training/package-visibility/use-cases)(확인 2026-11-02, "If your app interacts with a text-to-speech (TTS) engine, include the following `<intent>` element as part of the `<queries>` element"). M22에서 넣는다. 다른 `<queries>`는 두지 않는다: 외부 앱을 여는 인텐트(라이선스 링크 `ACTION_VIEW`, TTS 데이터 설치 `ACTION_INSTALL_TTS_DATA`, TTS 설정 열기)는 미리 `resolveActivity`/`queryIntentActivities`로 판정하지 않고 **`startActivity`를 부른 뒤 `ActivityNotFoundException`을 잡는다**(같은 문서 "the most straightforward approach is to invoke the intent and handle the ActivityNotFoundException"). 버튼은 늘 보이고, 예외가 나면 그 자리에 "열 수 있는 앱이 없습니다" 안내를 보인다(문구는 ui-design).

`AndroidManifest.xml` 백업 속성: `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"`(API 31+, `cloud-backup`과 `device-transfer` 모두), `android:fullBackupContent="@xml/backup_rules"`(API 26–30). 두 규칙 파일 모두 `domain="database"`의 `study.db`, `study.db-wal`, `study.db-shm`만 `<include>`한다. include가 있으면 목록 밖 파일은 백업되지 않으므로 `content_*.db`(최대 12 MiB, 에셋에서 다시 만들 수 있음)는 들어가지 않는다. 기기를 바꾸거나 다시 설치하면 학습 기록·설정이 복원되고, 콘텐츠 DB는 첫 열기 때 에셋에서 다시 복사된다. 복원된 기록이 가리키는 ID가 새 콘텐츠에 없으면 4.5 규칙대로 화면에서 빠진다.

## 6. 콘텐츠 파이프라인

### 6.1 언어·위치

Python 3 표준 라이브러리, 실행은 `python3 -I`. 디렉터리는 `content-pipeline` 1절 그대로(`content/pipeline/`, `content/raw/`(gitignore), `content/authored/`, `content/ids/`, `content/work/`(gitignore, 중간 산출물), `content/ko-gloss/`(커밋), `content/dist/`(gitignore)) + `content/snapshot/`(커밋, 6.7). 진입점 `content/pipeline/build.py`, 단계별 실행 `build.py <단계>`. 설정 `content/pipeline/config.json`:

```json
{ "kind": "release", "levelSource": null, "levels": [5] }
```

`levelSource`는 D-06 결정 전까지 `null`이고, `null`이면 `release` 빌드는 멈춘다(`sample`은 동작).

### 6.2 단계

`content-pipeline` 3절의 1~7을 따른다. 이 앱의 구체 규칙:

| 단계 | 규칙 |
|------|------|
| fetch | `status == "confirmed"`인 자료만. 아니면 `FAIL: fetch <id> not confirmed`로 멈춤. 4.6 체크섬 규칙. 자료에 `files: [{path, url, sha256}]`가 있으면 파일마다 받고 파일마다 대조한다(S-06 n1~n5.csv, S-04 내보내기 여러 파일). 받은 응답이 HTML(디렉터리·웹 페이지)이면 `FAIL: fetch <id> not a data file`. `content/raw/`에 이미 같은 경로 파일이 있으면 받지 않고 그 파일 해시를 대조한다(`--refresh`일 때만 다시 받음). 받는 주소: `pinning.method == "archive"`면 `pinning.pinnedCopy`(고정 사본 URL)를 먼저 받고, 그 밖에는 `files[].url` 또는 `downloadUrl`(원 URL, 매니페스트에서 바꾸지 않음)을 받는다(6.7) |
| parse | JMdict는 `iterparse`로 스트리밍, `gloss`는 `xml:lang` 없음(영어)만. 영어 외 gloss·`lsource`·JMnedict 읽지 않음. KANJIDIC2는 4.2 필드만(`query_code`, `dic_ref`, `misc/jlpt`, 영어·비영어 `meaning`, pinyin·vietnam 읽기 읽지 않음) |
| level map | 6.3 |
| snapshot | 6.7. 여기까지(fetch~snapshot)는 `build.py refresh-snapshot`으로만 돈다 |
| export-gloss-input | 스냅샷에서 급수 매핑된 어휘의 영어 gloss를 `content/work/en-gloss/N<급>.jsonl`로 낸다: `{"vocabId","written","reading","pos","senses":[["gloss",…],…],"enHash"}`. 번역 작성자의 입력 |
| ko gloss | `content/ko-gloss/N<급>.jsonl`(6.4)을 결합. 현재 `enHash`와 다르면(스냅샷을 새 JMdict 판으로 바꿔 영어 뜻이 바뀜) `_workspace/03_content-engineer_<마일스톤>_stale_gloss.tsv`에 남기고 그 뜻은 그대로 쓴다(경고) |
| authored | `content/authored/` 형식 검사(필수 필드, ID 중복, 급수 값, 후리가나 괄호, 대상 구간 `[[…]]` 짝·개수(9.1·9.2), 정답 색인, 출처 필드 `method`·`reviewStatus` 허용 값, 한국어 필드의 `{`·`|`·`}`·`[[`·`]]` 금지 — 4.4) |
| build | 4.2·4.5 산출물 + `attribution.json`·`attribution_fallback.xml`(6.6) + dist 묶음. 입력 정렬, `VACUUM`, 시각 값 없음 |
| check | 6.5 |

`build.py`(인자 없음) = export-gloss-input 이후 단계 전부. 스냅샷·`content/ko-gloss/`·`content/authored/`만 읽으므로 **네트워크와 `content/raw/` 없이** 돈다. 마일스톤의 "다시 빌드"는 이 명령이다.

### 6.3 급수 매핑(D-06에 독립)

`content/pipeline/levels/`에 입력 어댑터를 둔다. 모든 어댑터는 같은 행을 낸다: `LevelRow(level:int, row_no:int, jmdict_seq:int|None, kanji:str|None, kana:str)`.

| 어댑터 | 대상 자료 | 매칭 규칙 |
|--------|-----------|-----------|
| `seq` | S-06(`original_data/n*.csv` 다섯 파일 — 매니페스트 `files`, 급수는 파일명) | `jmdict_seq`가 있으면 그 항목. 단 그 항목의 `reb`에 `kana`가 있어야 채택(없으면 unmatched). `jmdict_seq`가 비면 `form` 규칙으로 |
| `form` | S-05, S-09 등 표기·읽기 목록 | `kanji`가 있으면 `keb == kanji AND reb == kana`, 없으면 `reb == kana AND`(`keb` 없음 또는 해당 sense `misc`에 `uk`). 정확히 한 항목일 때만 채택 |

- 여러 항목이 맞거나 하나도 없으면 추측하지 않고 `_workspace/03_content-engineer_<마일스톤>_unmatched.tsv`(row_no, level, kanji, kana, 후보 seq들)에 남긴다. 사람이 정한 해소는 `content/authored/level-overrides.tsv`(`levelSource`, `level`, `row_no`, `jmdict_seq` 또는 `av-…`, 사유)로 커밋하고 다음 빌드가 읽는다.
- 대표 표기·읽기는 JMdict 항목을 따른다(S-06.used 주의: CSV 표기는 정규화 전 값이 있음). 첫 sense가 `uk`이거나 읽기가 가타카나뿐이면 표기는 NULL(가나 표기). 목록의 `kanji`가 비어 있으면(목록이 가나로 씀) 표기는 NULL. 아니면 `content/authored/written-overrides.tsv`(`vocabId`, `written`, 사유)가 있으면 그것을, 없으면 목록의 `kanji`가 그 항목의 `keb` 중 하나면 그것을, 아니면 첫 `keb`. 대표 읽기는 목록 `kana`(그 항목의 `reb`에 있을 때). 같은 seq에 목록 행이 여럿이고 첫 행의 읽기가 부적절하면 `content/authored/reading-overrides.tsv`(`vocabId`, `reading`, 사유)로 고른다. export-gloss-input은 대표 표기·읽기에 적용되지 않는 sense(`stagk`·`stagr` 제한)를 뺀다.
- 같은 `jmdict_seq`가 여러 급수에 나오면 가장 쉬운 급수.
- 행 수 범위(검사): 급수별 채택 어휘 수가 그 급수 목록 행 수의 90% 이상 100% 이하. 90% 미만이면 `FAIL: count N<급>`.
- 크기 예산: `content.db` N5만 2 MiB 이하, N5~N1 전체 12 MiB 이하.

### 6.4 한국어 뜻 파일(`content/ko-gloss/N<급>.jsonl`)

한 줄에 어휘 하나:

```json
{"vocabId":"jm:1358280","meanings":["물"],"koSource":"jmdict-translated","method":"llm:<모델>@2026-11-10","enHash":"<export 때 값>","reviewStatus":"unreviewed","reviewedAt":null,"reviewerKind":null}
```

- 외부 서비스(LLM) 결과는 이 파일로 커밋하고 빌드는 파일만 읽는다(재현성).
- 직접 집필 뜻은 `koSource: "authored"`, `method: "human"` 또는 작성 에이전트의 `llm:…`(사람이 쓰지 않았으면 `human`으로 적지 않는다).
- 검수 결과는 `reviewStatus`·`reviewedAt`·`reviewerKind`를 고친다. 검수 범위·검수자 종류는 D-03이 정한다(미정). 에이전트 검수는 `reviewerKind: "agent"`이며 사람 검수로 적지 않는다.
- 뜻 안의 일본어(예: `〜さん보다 정중한 호칭`)는 표시 기호 없이 그대로 쓴다(4.4 규칙). 루비 표기 `{…|…}`를 쓰지 않는다. 한국어 낱말을 한자로 쓰지 않는다.

### 6.5 검사(`content/pipeline/check.sh`, 오프라인)

`content-pipeline` 5절 표 전부 + 이 앱의 추가:

| 검사 | 실패 조건 |
|------|-----------|
| 종류 | `release` 빌드인데 `content_meta.kind != release` 또는 `levelSource`가 `confirmed` 아님 |
| 스키마 | 에셋 `user_version`·`room_master_table` 해시·`sqlite_master`가 가장 큰 번호의 스키마 JSON과 다름 |
| 보호조치 | 에셋 `content.db`가 SQLite 헤더(`SQLite format 3\0`)로 시작하지 않음(L-01.nodrm) |
| 해시 | `content.db.sha256`이 실제 해시와 다름 |
| 뜻 행 | `vocab_meaning`의 `ko_*` 필수 열이 비었거나 허용 값 밖, `ko_review_status != 'unreviewed'`인데 `ko_reviewed_at`이 비었음, 뜻 행이 없는 `vocab` |
| 출처 열 | `grammar`·`question`·`listening` 행의 출처 열 묶음 P(4.2)가 비었거나 허용 값 밖, `ko_review_status != 'unreviewed'`인데 `ko_reviewed_at`이 비었음 |
| 저작자 표시 | `attribution.json`에 DB의 `source_id`·`level_source_id`가 빠짐, S-01·S-02를 쓰는데 그 항목 `links`가 비었음(S-01.docs), `attribution_fallback.xml`이 `attribution.json`으로 만든 값과 다름, `notices[].body`·`firstNotice.body`에 채우지 않은 `{…}` 자리가 남음, `meaningSourceTexts`에 6.6의 키 7개 중 하나라도 빠짐, `grammar` 또는 `question` 행이 있는데 `notices`에 N-11 없음, `listening` 행이 있는데 N-12 없음, `release` 빌드에서 `sources[]`(`authored` 제외)의 `attribution`·`licenseUrl`·`modifications` 중 하나라도 null·빈 문자열(값은 analyst가 매니페스트에 채운다 — 7절 `SourceUi`가 non-null) |
| 앱 자산 | `release` 빌드인데 매니페스트에서 `scope`가 `app-asset`으로 시작하는 자료(S-11 Material Symbols 등, 파이프라인이 받지 않고 손으로 `res/`에 넣는 자료)가 `confirmed` 아님 |
| 스냅샷 | `content_meta.sources`의 스냅샷 해시가 `content/snapshot/` 파일과 다름 |
| 크기 | 6.3 예산 초과 |

영어 외 gloss·제외 필드를 읽지 않는다는 것은 DB 검사가 아니라 파서 단위 테스트(합성 픽스처에 비영어 gloss·`query_code`·영어 `meaning`을 넣고 결과에 없음을 확인)로 확인한다.

### 6.6 `attribution.json`(출처와 라이선스 화면 데이터)

파이프라인이 매니페스트와 `content/notices.ko.json`(고지 문구 틀, 커밋)으로 만든다. 화면 코드(`app/src/main/java`와 `app/src/main/res`의 손으로 쓴 파일)에 출처·고지 문구를 쓰지 않는다.

```json
{
  "contentVersion": "…",
  "kind": "release",
  "dbLicense": {"name": "CC BY-SA 4.0", "url": "https://creativecommons.org/licenses/by-sa/4.0/"},
  "levelListName": "…",
  "modelNames": ["…"],
  "reviewPolicy": "…",
  "sources": [
    {"id": "S-01", "name": "JMdict", "version": "2026-11-01", "attribution": "…매니페스트 그대로…",
     "license": "CC BY-SA 4.0", "licenseUrl": "…", "homepage": "…", "modifications": "…",
     "links": ["https://www.edrdg.org/edrdg/licence.html", "…"],
     "extraCredits": [{"text": "…", "url": null}]}
  ],
  "notices": [{"id": "N-09", "title": "…", "body": "…"}, {"id": "N-10", "title": "…", "body": "…"}],
  "firstNotice": {"title": "…", "body": "…"},
  "meaningSourceTexts": {
    "translated-llm": "…{model}…", "translated-llm-nomodel": "…", "translated-human": "…",
    "authored-llm": "…{model}…", "authored-llm-nomodel": "…", "authored-human": "…", "unknown": "…"
  }
}
```

- `sources`는 DB에 실제로 쓰인 `source_id`·`level_source_id`의 자료만, 모두 `confirmed`여야 한다(검사). `authored`는 "이 앱 작성 콘텐츠"로 고정 항목.
- **`notices`는 파이프라인이 본문을 완성한다.** 항목은 `{id, title, body}`뿐이고 `showWhen` 같은 조건 필드는 두지 않는다. ui-design 9.2의 고지 1~4(N-09, N-10, N-11, N-12)를 `content/notices.ko.json`(문구 틀)으로 만들며 `{levelListName}`·`{modelNames}`·`{reviewPolicy}` 자리와 N-10의 "`ko_source`별 문장 중 DB에 있는 종류만" 이어 붙이기도 빌드 때 파이프라인이 한다. 넣을지 여부도 빌드 때 판정한다: N-11은 `grammar` 또는 `question` 행이 DB에 있을 때(문법을 처음 적재하는 M15부터), N-12는 `listening` 행이 있을 때(D-04 결정 뒤, M24)만 넣는다. 앱은 `notices`를 받은 순서 그대로 보이고 문장을 조립하지 않는다. **콘텐츠 DB 라이선스(N-08)는 `notices`에 넣지 않고 `dbLicense`에만 둔다.**
- 앱이 DB에서 세는 것은 N-10 아래 **검수 집계 줄 하나**뿐이다(`ContentRepository.meaningReviewCounts(): ReviewCounts`). `ko_source` 종류는 세지 않는다(본문에 이미 반영됨). 집계 줄의 틀("한국어 뜻 {n}개 — …", ui-design 8절)은 출처 문구가 아니라 앱 문자열 리소스다.
- `modelNames`: 파이프라인이 DB `ko_method`(`llm:<모델>@<날짜>`)에서 모은 중복 없는 목록(정렬). 앱은 이 값을 그대로 쓰고 DB에서 다시 모으지 않는다.
- `links`·`extraCredits`는 매니페스트 자료 항목의 같은 이름 필드를 **그대로** 옮긴다(파이프라인이 번역·가공하지 않는다). 화면은 `extraCredits[].text`를 원문 그대로 보인다(한글이 있으면 `ko-KR`, 없으면 `en` 로캘). 그래서 S-02 한국어 음 크레딧의 표시 문구는 매니페스트 S-02 `extraCredits[0].text` 값 자체인 "한국어 음: Charles Muller 제공(KANJIDIC2 경유)"(`ko-KR`)이며, 문구를 바꾸려면 analyst가 매니페스트를 고친다. S-02 `attribution` 원문에는 한국어 음 문장이 없다.
- `firstNotice`: 홈 첫 안내 카드(ui-design 6.2) 문구. `kind`와 DB의 `ko_source`에 맞게 파이프라인이 고른다(샘플 DB는 "샘플 콘텐츠" 문구, AI 번역 뜻이 없으면 AI 번역 문장을 넣지 않는다).
- `meaningSourceTexts`: 출처·검수 칩 하단 시트(ui-design 5.7)의 생성 방법 문장. 키는 칩 앞 부분 규칙과 같은 (생성 방법 × 출처) 조합이다:

  | 키 | `ko_method` | `ko_source` |
  |----|-------------|-------------|
  | `translated-llm` | `llm:<모델>@…`(모델 있음) | `jmdict-translated` |
  | `translated-llm-nomodel` | `llm:`로 시작하나 모델 이름이 비었음 | `jmdict-translated` |
  | `translated-human` | `human` | `jmdict-translated` |
  | `authored-llm` | `llm:<모델>@…`(모델 있음) | 그 밖의 값 |
  | `authored-llm-nomodel` | `llm:`로 시작하나 모델 이름이 비었음 | 그 밖의 값 |
  | `authored-human` | `human` | 그 밖의 값 |
  | `unknown` | 비었거나 알 수 없는 값 | 아무 값 |

  키 선택과 `{model}` 채우기는 칩 문구와 같은 객체 `ProvenanceLabel`의 `sheetText(meaning: MeaningUi, texts: Map<String, String>): String` 한 곳에서 한다(키 7개 전부 단위 테스트). 문장 자체는 ui-design 5.7이 정하고 `content/notices.ko.json`에 둔다.
- 최소 표시: 파이프라인이 같은 입력으로 `app/src/main/res/values/attribution_fallback.xml`(문자열 하나 `attribution_fallback`: **DB에 쓰인 자료**의 `attribution` 원문 + `licenseUrl` + "콘텐츠 DB: {dbLicense.name} {dbLicense.url}")을 만든다. 특정 자료(S-01)를 고정으로 넣지 않으며, 샘플 DB(`authored`만)에서도 생성된다. 자료가 `confirmed`인지는 생성 단계가 아니라 `release` 빌드의 검사(6.5 "종류"·"저작자 표시")가 판정한다. 앱은 `attribution.json`을 읽지 못하면 이것만 보인다(ui-design 9.4, S09 실패 상태 문구도 이것).
- 앱 쪽 라이선스(`app/src/main/assets/licenses/`, 손으로 커밋)가 **S-11(Material Symbols)과 의존 라이브러리의 고지 경로**다: `py-fsrs.txt`(MIT 전문, 2.1), `apache-2.0.txt`(Apache-2.0 전문 — S-11과 AndroidX 등 Apache-2.0 라이브러리 공용), `libraries.json`(`[{"name","license","url","distributed"}]` — `gradle/libs.versions.toml`의 `[libraries]` 항목마다 한 줄, 시험 전용은 `distributed: false`, 그리고 S-11 Material Symbols 한 줄). 라이브러리는 sources.md 행을 두지 않고 `libraries.json`으로만 기록한다. 아이콘 파일 이름은 ui-design 3.5와 같다: 채움 0은 `res/drawable/ic_ms_<이름>.xml`, 채움 1 변형은 `ic_ms_<이름>_fill.xml`. 단위 테스트가 (1) `libs.versions.toml`의 `[libraries]` 키가 모두 `libraries.json`에 있는지, (2) `ic_ms_*.xml` 파일마다 앞의 `ic_ms_`와 끝의 `.xml`을 떼고, 남은 이름이 `_fill`로 끝나면 그것도 뗀 기본 이름이 매니페스트 S-11 `usedFields`(기본 이름 배열)에 있는지 확인한다. `_fill` 파일은 같은 기본 이름의 `ic_ms_<이름>.xml`이 없어도 된다. S-11은 fetch를 거치지 않으므로 "confirmed 아니면 받지 않는다"는 안전장치가 닿지 않는다. 그래서 개발(M2~M7)은 S-11 `unconfirmed`로도 진행하되, `release` 빌드 검사 "앱 자산"(6.5)과 게이트 `G-S11`(10절)이 출시 DB 빌드 전에 확정을 요구한다. S-11이 끝내 확정되지 않으면 아이콘을 다른 확정 자료로 바꾸는 마일스톤을 새로 만든다.

### 6.7 원본 고정(스냅샷)

JMdict·KANJIDIC2는 같은 URL이 매일, Tatoeba는 매주 덮어써지므로 `sha256`을 적은 다음 날(주)이면 같은 URL로 같은 파일을 받을 수 없다. 그래서 원본 판은 저장소에 커밋한 스냅샷으로 고정한다(방식 A).

매니페스트 `pinning` 형식(파이프라인이 읽는 값, 채우는 일은 analyst):

| `pinning.method` | 뜻 | `pinnedCopy` | fetch가 받는 곳 |
|------------------|----|--------------|-----------------|
| `url` | URL 자체가 판을 고정(커밋 해시 URL 등, `urlPinsVersion: true`) | `null` | `files[].url`/`downloadUrl` |
| `snapshot` | 방식 A. 판 고정은 `content/snapshot/`의 커밋 파일 | `"content/snapshot/"`(기록용, fetch는 읽지 않음) | 원 URL(`refresh-snapshot`일 때만. 기본 빌드는 fetch하지 않음) |
| `archive` | 방식 B. 원본 사본을 저장소 밖 고정 위치에 판별로 둠 | 고정 사본 URL(`https://…`) | `pinnedCopy`. `downloadUrl`은 원 URL로 그대로 둔다 |

`method`가 없으면 fetch는 `FAIL: fetch <id> pinning method missing`으로 멈춘다(`confirmed` 자료만 받으므로 sample 빌드에는 영향 없음). 자료별 값: S-01·S-02 `snapshot`, S-06 `url`, S-03 `snapshot`(M13), S-04 `snapshot`(예문을 쓰는 마일스톤에서, 아래).

- `build.py refresh-snapshot`(fetch → parse → level map → snapshot)이 `content/snapshot/`에 쓴다:
  - `S-01-<version>.jsonl.gz`: 급수 목록 행에 채택되었거나 unmatched 후보인 JMdict 항목만(급수 목록의 **모든 급수**, 그래서 N4 이후 확장도 다시 받지 않는다). 필드는 `ent_seq`, `keb`·`reb` 목록, 첫 sense 품사, sense별 `misc`의 `uk` 여부, `stagk`·`stagr`(있을 때만), 영어 gloss만.
  - `S-02-<version>.jsonl.gz`: 위 항목들의 `keb`에 나오는 문자의 KANJIDIC2 4.2 필드만.
  - `levels-<levelSource>-<version>.tsv`: 급수 목록 행(`LevelRow`). 공개 저장소에 커밋하지 않기로 하면(decisions.md D-08, 미정) 이 파일 대신 방식 B — 원본 사본을 저장소 밖 고정 위치(릴리스 첨부)에 판별로 두고 매니페스트 `pinning.method = "archive"`, `pinning.pinnedCopy` = 그 사본 URL로 적는다(`downloadUrl`·`files[].url`은 원 URL 그대로).
  - `S-03-<version>.jsonl.gz`(M13): DB 한자의 `kvg:StrokePaths` 경로만(`kvg:StrokeNumbers`는 읽지 않는다. 획 번호는 앱이 계산한다, 9.4).
  - `S-04-<version>.jsonl.gz`(Tatoeba 예문을 처음 쓰는 마일스톤, S-04 `confirmed` 뒤 — 지금 마일스톤 목록에는 없다): DB에 넣는 문장 행만(`id`, `lang`, `text`, `username`, 문장별 라이선스, jpn–kor 연결). 매주 덮어써지는 원 URL과 무관하게 다시 빌드할 수 있게 한다. 이 마일스톤을 만들 때 refresh-snapshot 대상에 S-04를 더한다.
  - `LICENSE.md`: 자료별 저작자 표시 문구·라이선스 URI·"원 자료에서 일부 필드만 뽑음"이라는 변경 내용(CC BY-SA 4.0 재배포 조건).
- 스냅샷은 결정적이다: 행은 키 순서로 정렬, JSON 키 정렬, gzip `mtime=0`. 같은 원본으로 두 번 만들면 sha256이 같다.
- 빌드(6.2 `build.py`)는 스냅샷만 읽는다. `check`·다시 빌드·새 작업 공간·다른 컴퓨터에서 fetch가 필요 없다. 스냅샷을 바꾸는 것(D-07)은 콘텐츠 갱신 작업이며 ID 안정성 검사(6.5)와 stale gloss 보고를 거친다.

## 7. 앱 화면 구조와 UI 상태 모델

내비게이션: `NavigationSuiteScaffold`(하단 탭 ↔ 큰 화면 레일) 목적지 `홈`(Home), `사전`(Dictionary, 단어·한자 두 탭), `설정`(Settings). 2순위 이후 `문법`·`연습`이 더해진다(구현 전에는 숨김). 전체 화면 라우트: `LevelSelect`(S01, 첫 실행), `Review`(S03, 끝나면 S04 완료 화면), `VocabDetail(id)`(S06), `KanjiDetail(literal)`(S07), `About(anchor: String? = null)`(S09 "출처와 라이선스" — 설정 행에서 한 번, 홈 상단 메뉴 "정보"에서 두 번, S90의 "출처 보기"), `AppError`(S90). 라우트는 `@Serializable` 객체·data class(Navigation Compose 2.10.2 타입 안전 라우트).

- **위치 인자:** `About.anchor`는 고지 ID(`N-09` 등) 또는 자료 ID(`S-03` 등)다. 화면이 LOADED가 되면 그 카드로 스크롤하고, 그런 카드가 없거나 `null`이면 맨 위다(ui-design 5.9·5.11·6.6·6.7.1의 "S09의 해당 고지·자료 카드로"). 설정 탭 목적지는 `Settings(openLevelPicker: Boolean = false)`이고 `true`로 오면 학습 급수 `LevelPicker` 하단 시트를 연 채로 시작한다(홈 `NO_LEVEL_SELECTED`의 "급수 고르기"). 탭을 직접 누르면 인자는 기본값이다.
- **2순위 이후 목적지:** `문법` 탭은 M14, `연습` 탭(S12)은 M19에서 보이게 하고, 청해 진입(S12의 청해 영역)은 M23에서 더한다.
- **시작:** `AppState.Opening` 동안에는 `NavHost`를 만들지 않고 전체 화면 `StatusScreen` 로딩(300 ms가 지나면 "학습 콘텐츠를 준비하고 있습니다" — 첫 실행의 에셋 복사가 여기서 일어난다, 4.5)을 그린다. `Ready(needsOnboarding = true)`면 시작 라우트 `LevelSelect`, `Ready(false)`면 홈. `ContentDbFailed`·`RecordDbFailed`면 **같은 `NavHost`**를 시작 라우트 `AppError`로 만들고, 그 그래프에 `About`이 있어 S90의 "출처 보기"로 간다(탭 목적지는 그리지 않는다). 그래서 S01의 `LevelStatus.PREPARING`은 에셋 복사가 아니라 `levelCounts()` 계산 중이다.

화면 상태의 종류는 ui-design 13.1을 따른다. 데이터 필드 이름은 이 문서를 따른다. 모든 화면 상태는 `data class XxxUiState` + 화면별 상태 enum, `StateFlow`를 `stateIn(viewModelScope, WhileSubscribed(5_000), 초기값)`.

일본어 텍스트: 테마의 `JpTypography`(스타일 묶음, `localeList = LocaleList("ja-JP")`, `LocalJpTypography`로 제공)·`JpSpan`(미리 만든 `SpanStyle`)과 `JpText`만 쓴다. 섞인 문장은 `JpSpans.annotate`(4.4) 또는 `buildAnnotatedString` + `JpSpan`. **낭독 문구:** 일본어가 섞이는 접근성 문구(카드 앞·뒷면 낭독, 정답·오답 알림, 후리가나 노드의 ", 읽기 …")는 `contentDescription`이 아니라 `Modifier.semantics { text = <AnnotatedString, 일본어 구간에 JpSpan> }`(필요하면 `clearAndSetSemantics`)로 만들고 `liveRegion`도 그 노드에 건다. `contentDescription`·`onClickLabel`·`stateDescription`·`announceForAccessibility`는 String이라 로캘을 담지 못하므로 일본어를 넣지 않는다.

```kotlin
enum class JlptLevel(val n: Int) { N5(5), N4(4), N3(3), N2(2), N1(1) }
enum class CardKind(val code: String) { VR("vr"), VP("vp"), KR("kr"), GR("gr") }

// 앱 전체(S90)
sealed interface AppState {
    data object Opening : AppState          // 두 DB 여는 중(첫 실행 에셋 복사 포함). 화면: StatusScreen 로딩(7절 "시작")
    data class Ready(val needsOnboarding: Boolean) : AppState
    data object ContentDbFailed : AppState
    data object RecordDbFailed : AppState
}

// S01
enum class LevelStatus { PREPARING, READY, NO_CONTENT }   // PREPARING = levelCounts() 계산 중(DB는 이미 열림), NO_CONTENT = 번들 콘텐츠 0행(빌드 결함)
data class LevelOption(val level: JlptLevel, val wordCount: Int, val kanjiCount: Int, val available: Boolean)
data class LevelUiState(val status: LevelStatus = LevelStatus.PREPARING, val options: List<LevelOption> = emptyList(),
    val selected: Set<JlptLevel> = setOf(JlptLevel.N5), val newCardsPerDay: Int = 10)
// available == false(content_meta.levels에 없음)인 급수는 선택할 수 없다(ui-design 5.9).

// S02 — ui-design 13.1 Home의 상태와 1:1
enum class HomeStatus { LOADING, READY, NO_LEVEL_SELECTED, NO_CONTENT_FOR_LEVELS, ALL_DONE, NEW_LIMIT_REACHED }
data class LevelProgress(val level: JlptLevel, val learnedWords: Int, val totalWords: Int)
data class HomeUiState(
    val status: HomeStatus = HomeStatus.LOADING,
    val dueCount: Int = 0,                 // 오늘 기한(복습+학습). SessionCards.load(8.2) 결과로만 센다 — Review 대기열과 같은 집합
    val newTodayCount: Int = 0,            // 오늘 남은 새 카드(한도 − 오늘 도입, 남은 새 카드 수로 상한)
    val newLimit: Int = 10,                // 오늘 한도(설정값 + 오늘 추가분)
    val targetLevels: Set<JlptLevel> = emptySet(),
    val levelProgress: List<LevelProgress> = emptyList(),
    val nextDueAtEpochMs: Long? = null,    // ALL_DONE·NEW_LIMIT_REACHED 안내. SessionCards.load 결과 중 오늘 기한 밖 카드의 최소 dueAt
    val nextDueCount: Int = 0,             // 같은 결과에서 그 학습일(studyDayOf(nextDueAt))이 끝나기 전에 기한이 되는 카드 수
    val isSampleContent: Boolean = false,  // content_meta.kind == sample 이면 배너
    val showFirstNotice: Boolean = false,  // first_notice_dismissed == 0
    val firstNotice: NoticeUi? = null,     // AttributionRepository.load().firstNotice. load()가 예외면 null(카드 숨김), 홈 상태는 그대로
)
// 상태 판정: 급수 0개 → NO_LEVEL_SELECTED / 고른 급수가 모두 content_meta.levels 밖 → NO_CONTENT_FOR_LEVELS /
// dueCount > 0 또는 newTodayCount > 0 → READY / 남은 새 카드 있음(한도만 다 씀) → NEW_LIMIT_REACHED / 그 외 → ALL_DONE
// NO_LEVEL_SELECTED는 방어용(5절: 저장소가 빈 target_levels를 거부하므로 손상된 복원 값에서만 생긴다).
// HomeStatus에 ERROR는 없다. DB 실패는 AppState → S90이 맡는다.

// S03·S04
enum class ReviewStatus { LOADING, READY, EMPTY }
data class ReviewUiState(
    val status: ReviewStatus = ReviewStatus.LOADING,
    val emptyReason: HomeStatus? = null,   // EMPTY일 때 ALL_DONE / NEW_LIMIT_REACHED
    val card: ReviewCardUi? = null,
    val revealed: Boolean = false,         // false = 앞면(Front), true = 뒷면(Back)
    val previews: Map<Rating, IntervalPreview> = emptyMap(), // :core:srs의 IntervalPreview 하나만 쓴다
    val saving: Boolean = false,           // 저장 300 ms 초과 시 화면이 평가 줄 비활성
    val saveFailed: Rating? = null,        // 저장 실패한 평가(재시도 대상). 카드는 그대로
    val remainingReview: Int = 0,
    val remainingNew: Int = 0,
    val reviewedInSession: Int = 0,
    val newInSession: Int = 0,
    val canUndo: Boolean = false,
    val finished: Boolean = false,         // S04로
    val newCardsLeftAfterSession: Int = 0, // finished일 때: 대상 급수·켜진 카드 종류 중 아직 시작하지 않은 새 카드 수(오늘 한도와 무관).
                                           // > 0이면 S04가 "새 카드 {k}장을 더 할 수 있어요"와 보조 버튼 "새 카드 5장 더 학습"
                                           // (홈의 "새 카드 5장 더"와 같은 extra_new_cards 쓰기 후 새 세션), 0이면 둘 다 없음(ui-design 6.4)
)
// 입력 무시 시간(ui-design 3.6, InputGuardMs = 300)의 기준 시각은 UiState에 두지 않고 ReviewViewModel의 private 필드
// (마지막 화면 전환 시각, 주입한 Clock 기준)로 둔다. 300 ms 안의 reveal()·rate() 호출은 ViewModel이 무시한다.
sealed interface ReviewCardUi {
    val cardId: String
    data class Vocab(override val cardId: String, val direction: VocabDirection, val level: JlptLevel,
        val written: String?, val reading: String, val ruby: List<RubySegment>, val pos: List<String>, // 한국어 품사 이름표
        val meanings: List<MeaningUi>) : ReviewCardUi
    data class Kanji(override val cardId: String, val level: JlptLevel, val literal: String, val strokeCount: Int,
        val on: List<String>, val kun: List<String>, val koreanReadings: List<String>, val words: List<WordRef>) : ReviewCardUi
    // 2순위(M17): 앞 = title + example(루비), 뒤 = meaningKo + exampleKo + summaryKo + provenance 칩(ui-design 6.9.1)
    data class Grammar(override val cardId: String, val level: JlptLevel, val title: String,
        val example: List<RubySegment>,    // examples[0].ja를 Furigana.parse. 문법 형식 부분은 emphasis = true(4.4 [[…]])
        val exampleKo: String, val meaningKo: String,
        val summaryKo: String,             // explanation_ko의 첫 문단("\n\n" 앞), 저장소가 자른다
        val provenance: MeaningUi,         // grammar 행의 ko_* 열(9.1). textKo = summaryKo
        val needsReview: Boolean) : ReviewCardUi
}
enum class VocabDirection { JA_TO_KO, KO_TO_JA }
data class WordRef(val vocabId: String, val written: String?, val reading: String, val meaningKo: String)
data class MeaningUi(val textKo: String, val koSource: String, val koMethod: String, val reviewStatus: String,
    val reviewedAt: String?, val reviewerKind: String?)
// 칩 문구는 ui/common/ProvenanceLabel.of(MeaningUi) 한 함수가 ui-design 5.7 표대로 만든다(표의 모든 행을 단위 테스트).

// S05
enum class BrowseStatus { LOADING, RESULTS, NO_RESULT, NO_CONTENT_FOR_FILTER }
data class BrowseUiState(
    val status: BrowseStatus = BrowseStatus.LOADING,
    val tab: BrowseTab = BrowseTab.VOCAB, val levels: Set<JlptLevel> = emptySet(), // 빈 집합 = 번들된 급수 전부
    val query: String = "", val items: List<BrowseItem> = emptyList(),
)
// 결과 0개일 때: query.isNotBlank() → NO_RESULT, 아니면 NO_CONTENT_FOR_FILTER
// 초기값: BrowseViewModel은 처음 열 때 levels = user_settings.target_levels ∩ content_meta.levels(ui-design 6.5 "학습 급수 전부")로 정하고,
// 그 교집합이 비면 빈 집합(= 번들된 급수 전부)으로 둔다. 사용자가 칩을 모두 끈 상태도 빈 집합 = 번들 급수 전부.

// S06·S07
enum class DetailStatus { LOADING, LOADED, RETIRED }
data class VocabDetailUiState(val status: DetailStatus = DetailStatus.LOADING, val written: String? = null,
    val reading: String = "", val ruby: List<RubySegment> = emptyList(), val level: JlptLevel? = null,
    val pos: List<String> = emptyList(), val meanings: List<MeaningUi> = emptyList(),
    val kanji: List<String> = emptyList(), val cardStates: List<CardStateSummary> = emptyList())
data class KanjiDetailUiState(val status: DetailStatus = DetailStatus.LOADING, val literal: String = "",
    val strokeCount: Int = 0, val on: List<String> = emptyList(), val kun: List<String> = emptyList(),
    val koreanReadings: List<String> = emptyList(), val words: List<WordRef> = emptyList(),
    val strokes: List<String>? = null) // null = 필순 데이터 없음 → 절 숨김(S-03 확정 전)

// S08
enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class SettingsUiState(val loading: Boolean = true, val targetLevels: Set<JlptLevel> = emptySet(),
    val newCardsPerDay: Int = 10,          // 5..50, 5 단위
    val dayBoundaryHour: Int = 4,          // 0..12, 저장은 분(× 60)
    val vocabRecognitionEnabled: Boolean = true, val kanjiCardsEnabled: Boolean = true,
    val vocabRecallEnabled: Boolean = false, val grammarCardsEnabled: Boolean = true, // 문법 스위치는 M17부터 보임
    val frontFurigana: Boolean = false, val bodyFurigana: Boolean = true,
    val listeningScriptFirst: Boolean = false,  // 청해 콘텐츠가 번들에 있을 때만 화면에 보임(M23)
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val contentVersion: String = "", val saveFailed: Boolean = false)
// desiredRetention은 화면 상태에 없다(UI-U06 A). B가 승인되면 0.70..0.97 필드를 더한다.

// S09
enum class AboutStatus { LOADING, LOADED, FAILED }  // FAILED(= AttributionRepository.load() 예외) → R.string.attribution_fallback만
data class NoticeUi(val id: String, val title: String, val body: String)   // attribution.json notices 그대로. firstNotice는 id = "firstNotice"
data class LinkUi(val label: String, val url: String)
data class CreditUi(val text: String, val url: String?)                    // 매니페스트 extraCredits 그대로
data class SourceUi(val id: String, val name: String, val version: String?, val attribution: String,
    val license: LinkUi, val homepage: String?, val modifications: String, val links: List<String>,
    val extraCredits: List<CreditUi>)
// attribution·license.url(= licenseUrl)·modifications는 non-null이다. release 빌드에서 쓰인 자료의 이 값이 비면 6.5 "저작자 표시"가 FAIL이므로
// 번들에는 null이 오지 않는다. 그래도 파싱 실패는 화면 전체 FAILED(fallback 문단)로 처리한다(부분 표시 없음).
// authored 고정 항목은 파이프라인이 세 값을 채운다(licenseUrl = dbLicense.url).
data class AttributionData(val contentVersion: String, val kind: String, val dbLicense: LinkUi, val levelListName: String?,
    val modelNames: List<String>, val reviewPolicy: String?, val sources: List<SourceUi>, val notices: List<NoticeUi>,
    val firstNotice: NoticeUi?, val meaningSourceTexts: Map<String, String>)          // attribution.json(6.6)을 그대로 옮긴 값
data class LicenseUi(val name: String, val license: String, val url: String?, val fullTextAsset: String?) // assets/licenses/
data class ReviewCounts(val total: Int, val human: Int, val agent: Int, val sampled: Int, val unreviewed: Int, val reviewerUnrecorded: Int)
data class AboutUiState(val status: AboutStatus = AboutStatus.LOADING, val contentVersion: String = "",
    val dbLicense: LinkUi? = null, val notices: List<NoticeUi> = emptyList(), val sources: List<SourceUi> = emptyList(),
    val reviewCounts: ReviewCounts? = null,               // 앱이 DB에서 세는 유일한 값(meaningReviewCounts)
    val modelNames: List<String> = emptyList(),           // attribution.json modelNames 그대로(6.6)
    val openSourceLicenses: List<LicenseUi> = emptyList())
// AboutViewModel: status는 AttributionRepository 하나로 정한다(load() 성공 → LOADED, 예외 → FAILED).
// reviewCounts는 별도로 runCatching { contentRepository?.meaningReviewCounts() }.getOrNull() — 실패하거나
// AppState가 ContentDbFailed라 ContentRepository가 없으면(null) reviewCounts = null이고 집계 줄만 숨긴다. LOADED는 유지.
// 그래서 S90 → "출처 보기"에서도 자료별 카드(notices·sources·extraCredits)가 보인다(attribution.json은 콘텐츠 DB와 별도 에셋).
```

- 저장 실패: `StudyRepository` 쓰기 함수는 실패 시 예외를 던진다(8.2). ViewModel이 잡아 `saveFailed = rating`, 카드·`previews`·대기열을 그대로 둔다. "다시 시도"는 처음 계산한 `ScheduleResult`를 그대로 다시 저장한다. 저장이 끝나기 전에는 다음 카드로 가지 않는다.
- 세션 순서는 `SessionPlanner`가 정하고 화면은 바꾸지 않는다. 평가(또는 되돌리기)마다 ViewModel이 `plan`을 `now`로 다시 호출하고 결과의 첫 원소를 다음 카드로 보인다(8.1).
- 평가 버튼 이름표와 낭독 문구는 `ui/review/RatingLabels.kt` 한 곳: `AGAIN 다시`, `HARD 어려움`, `GOOD 알맞음`, `EASY 쉬움`, `onClickLabel` 틀 "평가: {이름}, 다음 복습 {미리보기} 후"(조사를 붙이지 않는다 — "다시으로" 방지), 미리보기가 "1분 미만"이면 "평가: {이름}, 다음 복습 1분 안에"(ui-design 5.3).
- 간격 미리보기 글자는 그 옆 `ui/review/IntervalFormat.kt`의 `object IntervalFormat { fun format(preview: IntervalPreview): String }` 한 곳이 만든다(복습은 `scheduledDays`, 학습은 `stepDuration`). **표시 규칙(반올림·단위·문구)의 정본은 ui-design 5.3 표**다: 분은 반올림(30초는 올림, 5분 30초 → "6분"), 반올림을 먼저 하고 단위를 고른다(59분 30초 → "1시간"). 단위 테스트는 ui-design 5.3의 표 각 행과 경계값(59초, 5분 30초, 59분 30초, 23시간, 29일, 30일, 364일, 365일)을 고정한다. `dueAtEpochMs`는 반올림하지 않는다.
- 외부 링크(라이선스 URL)는 앱이 인터넷 권한 없이 `ACTION_VIEW` Intent로 브라우저에 넘긴다(`android-intent-security`). 인터넷 권한은 요청하지 않는다. 링크를 열 앱이 있는지 미리 판정하지 않고 `startActivity` 후 `ActivityNotFoundException`을 잡는다(5.1). 외부 인텐트 호출은 `ui/common/ExternalIntents.kt`의 `fun Context.startExternal(intent: Intent): Boolean`(예외면 false) 한 곳으로 한다.

## 8. 계층 계약(Kotlin 시그니처)

### 8.1 `:core:srs`

```kotlin
package com.jlptstudy.srs

enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }
enum class Phase(val value: Int) { NEW(0), LEARNING(1), REVIEW(2), RELEARNING(3) }

@Serializable
data class CardState(
    val cardId: String,
    val phase: Phase,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val dueAtEpochMs: Long,
    val lastReviewAtEpochMs: Long?,
    val reps: Int,
    val lapses: Int,
    val schedulerVersion: String,
) { companion object { fun new(cardId: String, createdAt: Instant, schedulerVersion: String): CardState } }

data class StudyDayRule(val zone: ZoneId, val boundaryMinutes: Int = 240) {
    fun studyDayOf(at: Instant): Long          // 3절 정의(지역 시각에서 경계분을 뺀 날짜)
    fun startOfStudyDay(studyDay: Long): Instant // 3절 정의, 불변식 성립
}

data class SrsConfig(
    val parameters: List<Double> = Fsrs6.DEFAULT_PARAMETERS, // 21개, py-fsrs v6.3.2
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val maximumIntervalDays: Int = 36500,
    val enableFuzz: Boolean = true,
    val studyDay: StudyDayRule,
)

/** 복습 상태 결과: scheduledDays >= 1, stepDuration == null / 학습·재학습 결과: scheduledDays == 0, stepDuration = 단계 간격. */
data class IntervalPreview(val dueAtEpochMs: Long, val scheduledDays: Int, val stepDuration: Duration?)

data class ReviewRecord(
    val cardId: String, val reviewedAtEpochMs: Long, val rating: Rating, val phaseBefore: Phase,
    val elapsedDays: Int, val scheduledDays: Int, val before: CardState, val after: CardState,
    val schedulerVersion: String,
)
data class ScheduleResult(val state: CardState, val record: ReviewRecord)

interface Scheduler {
    val version: String
    fun schedule(card: CardState, rating: Rating, reviewedAt: Instant, config: SrsConfig): ScheduleResult
    fun preview(card: CardState, reviewedAt: Instant, config: SrsConfig): Map<Rating, IntervalPreview>
}
class Fsrs6Scheduler : Scheduler  // version = "fsrs6/py-fsrs-6.3.2"

object SessionPlanner {
    /**
     * 도입 순서대로 놓인 새 카드 후보에서 오늘 도입할 카드를 고른다.
     * 뺀다: startedCardIds에 있는 카드, 콘텐츠 ID가 introducedTodayCardIds의 콘텐츠 ID와 같은 카드,
     * 같은 콘텐츠 ID의 두 번째 이후 카드(같은 학습일에 한 콘텐츠는 한 종류만 — 후보 순서상 vr이 vp보다 앞).
     * 개수 = max(0, effectiveNewCardsPerDay − introducedTodayCardIds.size).
     */
    fun selectNew(orderedCandidates: List<String>, startedCardIds: Set<String>,
        introducedTodayCardIds: List<String>, effectiveNewCardsPerDay: Int): List<String>

    /**
     * 기한 카드(복습: 오늘 학습일 끝 전, 학습·재학습: now 이하)를 dueAt 오름차순, 그 뒤 newCardIds 순서, 맨 뒤에 learn-ahead(학습·재학습, now 뒤~now+learnAhead 이하, dueAt 오름차순). 버튼에 보인 간격("10분 후")을 새 카드보다 먼저 깨지 않고, 남은 카드가 그것뿐일 때만 앞당긴다.
     * pinned가 있으면 맨 앞에 두고 중복을 뺀다(되돌리기 직후 그 카드).
     * 평가·되돌리기마다 now로 다시 부른다. 결과가 비면 세션 끝(now+learnAhead를 넘는 학습 카드만 남은 경우 포함, 기다리지 않는다).
     */
    fun plan(existing: List<CardState>, newCardIds: List<String>, now: Instant,
        learnAheadMinutes: Int, rule: StudyDayRule, pinned: String? = null): List<String>
}
```

`:core:srs`는 Android·Room·시스템 시계를 쓰지 않는다. `preview`는 `schedule`과 같은 계산(같은 시드)으로 나와야 한다(버튼에 보인 간격과 실제 결과 일치).

세션 흐름(ViewModel): 시작 때 `maxLogId()`를 기억하고 새 카드를 `selectNew`로 고른다. 평가 → 저장 → `existing`을 갱신해 `plan(now)` → 첫 원소 표시. 세션 중 학습일 경계를 넘으면(다시 계산한 `today`가 바뀌면) 오늘 도입 목록과 한도를 새 학습일로 다시 구해 `selectNew`를 다시 한다. AGAIN·GOOD으로 학습·재학습 단계(1분·10분)에 들어간 카드는 다른 카드가 남아 있으면 새 카드 뒤로 가고, 남은 카드가 그것뿐이고 learnAhead(20분) 안이면 앞당겨 나온다. 시계가 그 시각을 지나면 기한 카드로 앞에 선다.

### 8.2 `:app` 저장소

모든 쓰기 함수는 실패 시 예외를 던지고(Result로 감싸지 않는다), 호출한 ViewModel이 잡아 화면 상태로 바꾼다. ID 목록을 받는 함수는 안에서 900개 단위로 나눠 조회한다(4.5).

```kotlin
data class NewCardCandidate(val cardId: String, val level: JlptLevel)

interface ContentRepository {
    suspend fun meta(): ContentMeta                                  // kind, contentVersion, levels
    suspend fun levelCounts(): Map<JlptLevel, LevelCounts>           // S01 개수, 진도 분모
    /** 대상 급수·종류의 모든 카드 ID를 도입 순서(level desc, sort_key, kind vr→vp→kr→gr, id)로. 바인드 변수는 급수·종류뿐. 제외는 호출자가 메모리에서(SessionPlanner.selectNew). */
    suspend fun newCardOrder(levels: Set<JlptLevel>, kinds: Set<CardKind>): List<NewCardCandidate>
    suspend fun cardContents(cardIds: List<String>): Map<String, ReviewCardUi> // 900개씩, 없는·retired ID는 빠짐
    fun browse(kind: BrowseTab, levels: Set<JlptLevel>, query: String): Flow<List<BrowseItem>> // 표기·읽기·한국어 뜻(text_ko LIKE) 일치
    suspend fun vocab(id: String): VocabDetail?
    suspend fun kanji(literal: String): KanjiDetail?
    suspend fun meaningReviewCounts(): ReviewCounts                  // 번들 전체 기준 GROUP BY. ko_source 종류는 세지 않는다(6.6)
    suspend fun cardLevels(cardIds: List<String>): Map<String, JlptLevel> // 900개씩, 없는·retired ID는 빠짐(SessionCards용, 내용은 읽지 않음)
}
data class UndoResult(val cardId: String, val restored: CardState?) // restored == null: 새 카드였음(행 삭제)
interface StudyRepository {
    suspend fun applyReview(result: ScheduleResult)                 // 한 트랜잭션, 실패 시 예외
    suspend fun maxLogId(): Long                                     // 세션 시작 때, 로그 없으면 0
    suspend fun undoLast(afterLogId: Long): UndoResult?              // id > afterLogId, undone = 0 중 id 최대. 없으면 null
    suspend fun startedCardIds(): Set<String>                        // SELECT card_id FROM card_state
    suspend fun states(cardIds: Collection<String>): Map<String, CardState> // 900개씩
    suspend fun statesOfKinds(kinds: Set<CardKind>): List<CardState>  // card_state WHERE kind IN (:kinds). SessionCards만 부른다
    suspend fun introducedToday(fromEpochMs: Long, toEpochMs: Long): List<String> // 오늘 도입한 새 카드 ID(5절)
    fun settings(): Flow<UserSettings>
    suspend fun updateSettings(transform: (UserSettings) -> UserSettings) // 범위 밖·카드 종류 전부 끔이면 IllegalArgumentException
}
/**
 * 홈 수치(dueCount·nextDueAt·nextDueCount)와 복습 대기열(SessionPlanner.plan의 existing)이 쓰는 유일한 카드 집합.
 * = card_state 중 kind ∈ kinds(켜진 카드 종류) ∧ 콘텐츠에 있고 retired 아님 ∧ 급수 ∈ levels(target_levels ∩ content_meta.levels).
 * 기한 판정은 하지 않는다(SessionPlanner.plan이 한다). HomeViewModel과 ReviewViewModel은 card_state를 직접 세지 않는다.
 * 정책: 꺼진 카드 종류·대상에서 뺀 급수의 카드는 새 카드뿐 아니라 복습도 멈춘다. 기록은 남고, 다시 켜면 원래 기한(밀렸으면 연체)대로 돌아온다.
 * shortcut: 호출마다 켜진 종류의 card_state 전부와 그 급수를 읽는다(N5~N1 전체도 수만 행 이하). 홈 열기가 300 ms를 넘으면 content_version별 급수 캐시로 바꾼다.
 */
class SessionCards(private val study: StudyRepository, private val content: ContentRepository) {
    suspend fun load(kinds: Set<CardKind>, levels: Set<JlptLevel>): List<CardState> // statesOfKinds → cardLevels로 거름
}
interface AttributionRepository {
    suspend fun load(): AttributionData             // assets/content/attribution.json. 읽기·파싱 실패는 예외(M5에서 만든다)
    suspend fun openSourceLicenses(): List<LicenseUi> // assets/licenses/(M7에서 만든다)
}
interface Speaker {                                               // 4순위, D-04와 무관하게 이 인터페이스 뒤
    val state: StateFlow<SpeakerState>
    fun speak(utteranceId: String, text: String, rate: Float = 1.0f) // rate: 1.0 = 보통, ui-design 5.8 토글은 0.8f/1.0f.
                                                                  // 기기 TTS는 setSpeechRate(rate), 사전 생성 음성은 재생기 속도.
                                                                  // Ready → Playing(utteranceId) → 끝나거나 stop() → Ready
    fun stop()
    fun shutdown()                                                // 이 인스턴스만 해제. 이후 재사용하지 않는다
}
sealed interface SpeakerState {
    data object Initializing : SpeakerState  // 10초 안에 초기화가 끝나지 않으면 구현이 Error로 바꾼다
    data object Ready : SpeakerState
    data class Playing(val utteranceId: String) : SpeakerState
    data object MissingJapaneseData : SpeakerState
    data object NoEngine : SpeakerState
    data object Error : SpeakerState
}
// AppContainer.speakerFactory: () -> Speaker. 청해 ViewModel이 생성 때 하나 만들고 onCleared에서 그것만 shutdown.
// AudioFileMissing(ui-design 5.8)은 D-04 B(사전 생성 음성) 구현이 생길 때 더한다.
```

## 9. 2~4순위 초안(해당 마일스톤 착수 때 확정)

### 9.1 문법(2순위)

- 직접 작성 파일 `content/authored/grammar/N<급>/<id>.json`: 공통 필드(`id`, `level`, `status`, `needsReview`, `sources`) + 출처 필드(`method`: `llm:<모델>@<YYYY-MM-DD>` / `human` — 사람이 쓰지 않았으면 `human`으로 적지 않는다, `reviewStatus`, `reviewedAt`, `reviewerKind` — 6.4와 같은 값) + `title`(일본어 형식, 예 `〜てください`), `meaningKo`, `explanationKo`(마크다운 문단, 일본어 구간은 4.4 규칙, **루비 표기 없음**), `connections`(`[{"posKo": "동사", "form": "て形", "example": "{食|た}べて"}]` — ui-design 6.9 접속 표의 3열 품사(ko)/형태(ja)/예(ja)와 1:1. `posKo`는 한국어 필드, `form`·`example`은 일본어 필드라 루비 허용), `examples`(3개 이상, `[{"ja": "…[[{後|あと}で]]…", "ko": "…"}]` — `ja`만 루비 허용, 각 `ja`에 문법 형식 부분을 `[[…]]`로 **1개 이상** 표시(4.4, 카드 앞면 밑줄)), `contrasts`(선택, `[{"grammarId": "gr-n5-0013", "pair": [{"ja": "…", "ko": "…"}, {"ja": "…", "ko": "…"}]}]` — ui-design 6.9 "비슷한 문법"(예문 쌍). `pair[0]`은 이 문법, `pair[1]`은 `grammarId` 문법의 예문, `ja`는 루비·`[[…]]` 허용, `ko`는 한국어 필드).
- `authored` 검사(9.1 몫): `examples[].ja`마다 `[[…]]` 짝이 맞고 1개 이상, `contrasts[].grammarId`가 같은 빌드의 grammar ID에 있고 자기 자신이 아님, `pair`가 정확히 2개, `connections[]`에 세 필드가 모두 있음. 어기면 `FAIL: authored <id> <이유>`.
- 콘텐츠 DB(v3, M14): `grammar(id PK, level, sort_key, title, meaning_ko, explanation_ko, connections_json, contrasts_json TEXT NOT NULL, + 출처 열 묶음 P)` — `connections_json`은 `[{posKo, form, example}]`, `contrasts_json`은 `[{grammarId, pair:[{ja,ko},{ja,ko}]}]`(없으면 `[]`) 그대로. P(4.2)의 `ko_*`는 설명·예문 번역·비교 번역 전체의 출처다. `grammar_example(grammar_id, ord, ja, ko, source_id, source_ref)`. Tatoeba 문장(S-04)을 쓰면 `source_id = S-04`, `source_ref` = 문장 ID, `license`·`author` 열 추가(L-03.tatoeba-row) — S-04 `confirmed` 전에는 쓰지 않는다.
- 화면: S11 상세의 `ProvenanceChip`은 grammar 행의 `ko_*`로 만든 `MeaningUi`를 `ProvenanceLabel.of`에 넘긴다. `needs_review = 1`이면 "설명 검토 중" 칩(ui-design 6.9).
- 카드 종류 `gr:<id>`, UI 모델 `ReviewCardUi.Grammar`(7절). 켬/끔은 `user_settings.grammar_cards_enabled`(v1에 있음).

### 9.2 문항·모의고사(3순위)

- 시험 구성 근거: 주최 측 안내 [JLPT Test Sections and Test Times](https://www.jlpt.jp/e/guideline/testsections.html)(확인 2026-11-02): N5 언어지식(문자·어휘) 20분 / 언어지식(문법)·독해 40분 / 청해 30분, N4 25/55/35, N3 30/70/40, N2 언어지식·독해 105 / 청해 50, N1 110 / 55. 페이지는 "The time allotted for each section may change."라고 밝힌다. 영역별 문제 유형·문항 수는 이 문서에서 확인하지 않았다 — 문항 작성 마일스톤(M21)에서 같은 사이트의 급수별 안내를 열어 모의고사 템플릿(`content/authored/exams/N<급>.json`, 출처 URL·확인일 포함)으로 작성하고, 앱은 템플릿을 데이터로 읽는다. 앱에 "실제 시험과 다를 수 있음"(N-11과 함께)을 보인다.
- 문항 파일 `content/authored/questions/N<급>/<id>.json`: 공통 필드 + 출처 필드(9.1과 같은 `method`, `reviewStatus`, `reviewedAt`, `reviewerKind`) + `section`(`vocab`/`grammar`/`reading`/`listening`), `type`(jlpt-content-authoring 5절 유형 이름), `stem`, `passage`(선택), `choices`(4개), `answerIndex`(0..3), `explanationKo`, `whyWrongKo`(오답별), `targets`(관련 어휘·문법 ID — 화면 S15 "관련 단어·문법 보기"용), `target`(선택, 일본어 필드, 루비 없음 — ui-design 6.10 지시문의 `{대상}`에 넣는 글자).
- 대상 구간 규칙(`authored` 검사): 유형 **한자 읽기·표기·유의 표현**은 `stem`(없으면 `passage`)에 `[[…]]`가 정확히 1개, `target`이 필수이고 그 구간 base 글자를 이은 문자열과 같다. **한자 읽기** 문항의 `[[…]]` 안에는 루비를 쓰지 않는다(정답 노출). **용법**은 `target`이 필수이고 `[[…]]`는 없어도 된다. 그 밖의 유형은 `target`이 없고 `[[…]]`는 0개 이상(짝만 검사). `targets`(ID 목록)와 `target`(지시문 글자)은 다른 필드다.
- 콘텐츠 DB(v4, M19): `question(id PK, level, section, type, sort_key, stem, passage NULL, target NULL, choices_json, answer_index, explanation_ko, why_wrong_ko_json, targets_json, + 출처 열 묶음 P)`, `exam_template(level PK, template_json, source_url, checked_at)`. 화면 S15 해설 카드(5.5)에 P로 만든 `MeaningUi`의 `ProvenanceChip`을 붙인다(ui-design 5.7 — AI가 쓴 해설을 검수 상태 없이 보이지 않는다).
- **문장 배열**도 4지선다다: `stem`에 빈칸 넷과 `★` 자리를 두고, `choices`는 조각 넷, `answerIndex`는 ★ 자리에 들어갈 조각(실제 시험 형식과 같은 채점). 전체 순서 채점 필드는 두지 않는다(ui-design 6.10과 같음).
- `:core:quiz`: `fun score(attempt: Attempt): Score`(영역별 정답 수·비율, 척도 점수 없음), `fun shuffle(question, seed): Question`(정답 색인 함께 이동, 시드 결정적), `fun buildSet(pool, template, seed): List<QuestionId>`.
- 학습 기록 DB v2(M19): `quiz_attempt(id PK 자동, exam_id TEXT NULL, seed INTEGER NOT NULL, question_order_json TEXT NOT NULL, current_index INTEGER NOT NULL DEFAULT 0, elapsed_ms INTEGER NOT NULL DEFAULT 0, started_at_epoch_ms, finished_at_epoch_ms NULL)`, `quiz_answer(attempt_id, question_id, chosen_index, correct, answered_at_epoch_ms, script_shown INTEGER NOT NULL DEFAULT 0)`. `chosen_index`는 **섞기 전 원래 선택지 색인**이다. `script_shown`은 청해 문항을 대본이 펼쳐진 상태에서 답했는지("대본으로 풂", ui-design 6.11. 청해가 아니면 0). 재개는 `seed`로 같은 섞기를 다시 만들고 `current_index`·`elapsed_ms`에서 이어 간다(백그라운드로 가면 경과 시간을 저장하고 멈춤, UI-U05 A). v1→v2 `Migration` + 테스트.

### 9.3 청해(4순위)

- 대본 파일 `content/authored/listening/N<급>/<id>.json`: 공통 필드 + `type`, `lines`(`[{"speaker": "M"|"F"|"N", "text": "…", "ttsText": "…(선택)"}]`), `question`, `choices`, `answerIndex`, `explanationKo`. 콘텐츠 DB v5(M23).
- 재생은 `Speaker` 뒤. 첫 구현은 기기 `TextToSpeech(Locale.JAPAN)`(D-04의 A/C 어느 쪽이든 필요한 부분). 엔진에 연결하려면 매니페스트 `<queries>` `TTS_SERVICE` 선언이 필요하다(5.1, M22). 일본어 데이터 설치(`ACTION_INSTALL_TTS_DATA`)·TTS 설정 열기(`com.android.settings.TTS_SETTINGS`)는 `startExternal`(7절)로 부르고 예외를 잡는다.
- DB(v5, M23): `listening(id PK, level, sort_key, type, lines_json, question, choices_json, answer_index, explanation_ko, + 출처 열 묶음 P)`. 작성 파일에는 9.1과 같은 출처 필드(`method`, `reviewStatus`, `reviewedAt`, `reviewerKind`)가 있다. 화면 S16 해설 카드에 P로 만든 `MeaningUi`의 `ProvenanceChip`을 붙인다(ui-design 5.7). 사전 생성 음성(B)이 채택되면 같은 인터페이스의 다른 구현과 `assets/` 음성 허용 검사 변경을 새 마일스톤으로 추가한다. 그 전까지 `check.sh`는 음성 파일을 금지한다.

### 9.4 필순(KanjiVG, S-03 확정 시)

`kanji_stroke(literal PK, paths_json TEXT NOT NULL, source_id 'S-03')`(콘텐츠 DB v2, M12) — main 묶음 SVG의 `kvg:StrokePaths_*` 경로 `d` 문자열을 획 순서대로 담는다. 부수·요소 속성과 `kvg:StrokeNumbers_*`(획 번호 글자·위치)는 읽지 않는다. 화면의 획 번호(ui-design 6.7.1)는 앱이 각 경로의 순서(번호)와 시작점(`d`의 첫 `M`)에서 계산한다. 화면은 `paths_json`을 `PathParser`로 그린다. 데이터가 없으면 절을 숨긴다.

## 10. 콘텐츠 마일스톤의 선행 조건(게이트 마일스톤)

워크플로는 `dependsOn`만 읽는다. 그래서 마일스톤 밖의 조건(자료 확정, 사용자 결정)은 **게이트 마일스톤**으로 둔다. 게이트는 코드가 없고 `status: "needs-decision"`으로 시작하므로 워크플로가 고르지 않는다. 조건이 충족되면 사용자 또는 메인 세션이 `acceptance`의 확인 방법대로 보고 `done`으로 바꾼다. 그러면 기다리던 마일스톤이 `dependsOn`으로 자연히 풀린다. 구현자가 "선행 조건 미충족"으로 `failed`를 보고해 `blocked`로 굳는 경로는 쓰지 않는다.

| 게이트 | 조건(확인 방법) | 기다리는 마일스톤 |
|--------|------------------|-------------------|
| `G-D06` | `docs/content/manifest.json`에서 S-01·S-02와 D-06에서 고른 급수 목록 자료가 `status == "confirmed"`이고 세 자료 모두 `pinning.method`가 있음(6.7), `docs/content/decisions.md` D-06이 **결정** | M8 → M9 → M10 → M11, M13, M26 |
| `G-D03` | decisions.md D-03이 **결정** | M11, M26 |
| `G-S03` | manifest S-03이 `confirmed` | M13 |
| `G-D04` | decisions.md D-04가 **결정** | M24 → M25 |
| `G-S11` | manifest S-11(Material Symbols, `scope` `app-asset`)이 `confirmed`이고 license·attribution·version이 sources.md에서 확정 | M10(첫 release DB 빌드) → M11, M13, M26 |

현재 막힌 사슬: M8·M9(G-D06), M10(G-D06 사슬 + G-S11), M11·M26(G-D06·G-S11 사슬 + G-D03), M13(G-S03 + M10을 거쳐 G-D06·G-S11), M24·M25(G-D04). 막히지 않는 것: M1~M7(샘플 DB로 MVP 화면 전부 — S-11 아이콘은 `unconfirmed`인 채 개발에 쓴다), M12, M14~M23.

fetch가 `confirmed` 아닌 자료를 받지 않으므로(6.2) 게이트가 잘못 풀려도 원본은 들어오지 않는다. 손으로 `res/`에 넣는 앱 자산(S-11)은 fetch를 거치지 않으므로 이 안전장치 대신 `release` 빌드 검사 "앱 자산"(6.5)이 막는다.

## 11. 문서 사이 대조(2026-11-02 design-review 후속 정리)

### 11.1 정본 기준

| 무엇 | 정본 | 다른 문서는 |
|------|------|-------------|
| 타입·상태 이름·필드, 저장 스키마(두 DB), 파이프라인 단계, `attribution.json` 형식, 누가 조립하는가(파이프라인/앱) | 이 문서 | ui-design·milestones가 이 이름을 그대로 쓴다 |
| 화면 표시 규칙(반올림처럼 사용자에게 보이는 값, 문구, 레이아웃), 화면 상태의 **종류**, 스크린샷 이름 | ui-design.md | 이 문서·milestones 완료 조건이 그 규칙을 그대로 인용한다 |
| 매니페스트·sources.md(출처·라이선스·판 고정 값) | analyst(`docs/content/`) | 이 문서는 고치지 않고 11.3에 넘긴다 |

### 11.2 ui-design.md가 이 문서에 맞출 것(ui-designer 몫, 정확한 이름)

1. 2절 4단계, 7절 S03 "저장 실패", 13.1 Review 행·저장소 계약: `saveError` → **`saveFailed: Rating?`**.
2. 13.1 Home 행: `status(LOADING, READY, ERROR) + emptyReason: HomeEmpty?(…)` → **`status: HomeStatus`**(`LOADING`, `READY`, `NO_LEVEL_SELECTED`, `NO_CONTENT_FOR_LEVELS`, `ALL_DONE`, `NEW_LIMIT_REACHED`). `ERROR` 삭제(DB 실패는 S90). `newRemainingToday` → **`newTodayCount`**, `newLimitToday` → **`newLimit`**, `LevelProgress(level, learned, total)` → **`LevelProgress(level, learnedWords, totalWords)`**, `firstNotice: NoticeUi?` 추가. 6.2 요약 카드의 `newRemainingToday` → `newTodayCount`, 진도 줄의 `{learned}/{total}` 설명은 `learnedWords`/`totalWords`.
3. 6.2: `ContentRepository.countByLevel()` → **`levelCounts()`**.
4. 13.1 Review 행: `emptyReason: HomeEmpty?` → **`emptyReason: HomeStatus?`**(`ALL_DONE`/`NEW_LIMIT_REACHED`만). `IntervalPreview(dueAtEpochMs, scheduledDays: Int?, stepDuration)` → **`scheduledDays: Int`**(학습 단계면 0, 8.1). `lastTransitionAtEpochMs`는 UiState 필드가 아니라 ViewModel 내부 값(7절 주석) — 행에서 지운다.
5. 13.1 VocabDetail/KanjiDetail 행: `READY` → **`DetailStatus.LOADED`**.
6. 13.1 LevelSelect 행: sealed `Preparing/Ready/NoBundledContent` → **`LevelUiState(status: LevelStatus { PREPARING, READY, NO_CONTENT }, options: List<LevelOption>, selected, newCardsPerDay)`**.
7. 13.1 ReviewCardUi 행: `Vocab`에 `level`, `ruby: List<RubySegment>` 추가, `Kanji`에 `level` 추가, `Grammar`는 7절 시그니처(`exampleKo`, `provenance: MeaningUi`, `needsReview` 포함). 6.9.1 "상태 모델" 문장도 이 시그니처로.
8. 5.8 표·7절 S16·13.1 Playback: `Preparing` → **`Initializing`**. 5.8 끝 문장과 13.1의 "architecture.md에 `Playing`을 더해야"는 지운다(8.2에 이미 있음).
9. 5.3: `IntervalFormatter.format(preview)` → **`IntervalFormat.format(preview)`**(`ui/review/IntervalFormat.kt`). 표 내용(30초 올림 → 6분)은 ui-design이 정본이라 그대로 둔다.
10. 4.4: 문자 집합에 **`・`**를 더해 7절 정본 집합(히라가나·가타카나(`ー`·`・` 포함)·CJK 통합 한자·`々`·`〜`·`「」『』`)과 같게 쓴다. "한국어 필드에는 `{…|…}`를 쓰지 않는다"는 유지(이 문서 4.4·9.1도 같게 고침). "파이프라인 검사가 한국어 필드에 한자가 있으면 경고"(4.4 둘째 항목)와 13.2의 같은 요청은 "**`authored`·`ko-gloss` 단계가 한국어 필드의 일본어 구간을 보고서에 나열(실패 아님), `{`·`|`·`}`는 FAIL**"로 바꾼다.
11. 5.7 하단 시트: 문장을 **`meaningSourceTexts` 키 7개**(`translated-llm`, `translated-llm-nomodel`, `translated-human`, `authored-llm`, `authored-llm-nomodel`, `authored-human`, `unknown`, 6.6 표) 각각에 대해 적는다. 지금 없는 `translated-human`(예: "JMdict(EDRDG)의 영어 뜻을 바탕으로 사람이 한국어로 번역했습니다.")·`authored-llm-nomodel`·`unknown` 문장을 더한다. 선택 함수는 `ProvenanceLabel.sheetText`라고 적는다.
12. 9.2 고지 1번 머리 문장의 `{id, title, body, showWhen}` → **`{id, title, body}`**, "`showWhen` 조건이 있는 고지는…" → "넣을지는 파이프라인이 빌드 때 판정". 785–787행: `{modelNames}`는 "앱 계산 값"이 아니라 **파이프라인이 모아 `attribution.json` `modelNames`에 넣은 값**, "DB에 있는 `ko_source` 종류"도 **파이프라인이 빌드 때 판정해 본문을 완성**. 앱이 DB에서 세는 것은 집계 줄(`ReviewCounts`)뿐. 13.1 About 행의 `reviewCounts`에서 "`ko_source` 종류"를 지우고, 926행 "6.6 `notices`를 `{id, title, body, showWhen}`로" 요청과 13.2 저작자 표시 에셋 항목의 `showWhen`도 지운다. 6.2 첫 안내 카드 본문은 `attribution.json` `firstNotice`(파이프라인이 고름)라고 적는다.
13. 9.2 원 자료 KANJIDIC2 항목(805행): "영어 `attribution` 원문 안의 "Korean readings: …"는 원문 그대로 보인다" 삭제(그 문장은 원문에 없다). `extraCredits` 표시 문구는 **매니페스트 S-02 `extraCredits[].text`를 그대로** 보인다고 바꾼다(값 "한국어 음: Charles Muller 제공(KANJIDIC2 경유)").
14. 9.4: "S-01.attribution 원문 + … S-01이 `confirmed`가 아니면 생성 단계에서 멈춘다" → "**DB에 쓰인 자료의 `attribution` 원문 + `licenseUrl` + 콘텐츠 DB 라이선스. 샘플 DB에서도 생성되고, `confirmed` 확인은 release 빌드의 6.5 검사가 한다**". "그 문서에 예외로 적는다(13.1)"는 지운다(6.6·4.6에 이미 있음).
15. 6.9 S11·6.9.1: 칩 입력은 grammar 행 `ko_*` 열(9.1), "설명 검토 중"은 `needs_review`(9.1)라고 적는다.
16. 7절 S02 "급수 미선택" 조건: "학습 급수 0개(설정에서 모두 해제 등)" → "**저장된 `target_levels`가 비었거나 1..5 밖 값뿐(손상된 복원 값, 방어용 — 5절)**".
17. 13.1 user_settings 끝 문장: "`SessionPlanner.plan`의 `newCardsPerDay` 인자" → **`SessionPlanner.selectNew`의 `effectiveNewCardsPerDay` 인자**. "이로써 M17은 스키마를 바꾸지 않는다" 뒤 `listening_script_first`도 v1에 있음(5절)은 그대로.
18. 이미 반영된 요청 문장 삭제: 1.2 "architecture.md 7절의 "오늘"·"단어·한자", M2·M5 문구는 이 이름으로 고친다", 4.3 "(architecture.md 7절·M2의 `JapaneseTextStyle`은 이 이름으로 바꾼다)", 4.3 끝 "milestones.json 수정 요청, 13.3", 5.9 "(milestones.json M5의 "선택은 되지만"은 이 규칙으로 고친다)", 6.5 "architecture.md `BrowseUiState.level` 하나를 집합으로 바꾼다"·"한국어 뜻 검색은 … 추가할 항목(13.1)", 6.8 "architecture.md `SettingsUiState`·M5 범위에서 뺀다(13.1)", 9.2 6번 "목록을 만드는 방법은 architecture.md가 정한다(13.1)"(6.6: 손 커밋 `assets/licenses/`), 13.1 926행 전체(이름 바꾸기 요청은 모두 반영됨), 13.2 "analyst는 manifest S-02 `usedFields`를 … 줄인다"·"manifest.json에 `links`·`extraCredits` 필드를 analyst가 채운다"·"analyst가 S-11 행을 `unconfirmed`로 추가"(매니페스트에 있음), 13.3 전체(milestones.json 반영 끝, 아래 11.4).
19. 승인 항목 ID: 머리말 9행·14절의 "architecture.md의 `U-xx`"와 본문의 `U-01`·`U-04`·`U-05`·`U-06`(92·269·432·568·931·955–958행) → **`AR-U01`·`AR-U04`·`AR-U05`·`AR-U06`**. 이 문서에만 있는 `UD-01`…`UD-06` → **`UI-U01`…`UI-U06`**(번호 그대로, 88·98·341·408·521·646·750·811·964–969행). 14절 의견 표의 U-06 행 "architecture.md의 0..100은 이 범위로 고친다"는 지운다(5절이 5..50).
20. 3.5 아이콘 목록: 5.4·6.10 S14가 쓰는 **`check`**와 5.2 후리가나 토글 아이콘 이름을 더한다(또는 그 자리를 목록 안 아이콘으로 바꾼다). 이 목록이 매니페스트 S-11 `usedFields`와 `res/drawable/ic_ms_*` 대조 테스트(6.6)의 기준이 된다.
21. 1.2 `nav_shell_light_font200` ↔ 12절 `nav_shell_home`(+`_font200`): 이름 하나로(**`nav_shell_home`**, M2 완료 조건이 이 이름). 12절에 M17 설정 스크린샷 **`settings_with_grammar`**(문법 스위치 보임)를 더한다. 12절 level의 state `level_unavailable`은 `{screen}_{state}` 규칙으로 `level_level_unavailable`이 되므로 state를 **`unavailable`**로(M5는 `level_unavailable`). M14(문법 목록·상세)·M23(청해 대본 보기·설치 안내·오류) 스크린샷 이름을 12절에 더한다.
22. 5.3 경계값 59초: 표 1행("1분 미만")과 "반올림을 먼저 하고 단위를 고른다"(59초 → 1분 → "1분")가 서로 다른 답을 낸다. 기대값 하나를 표에 적는다(M4 테스트가 그 값을 쓴다).

2026-11-02 설계 검증(2차) 후속:

23. 5.8 `NoEngine`의 "음성 설정 열기(열 수 없으면 버튼을 숨긴다)"·`MissingJapaneseData`의 "일본어 음성 설치"·7절 S09 "링크를 열 앱 없음": 미리 판정해 숨기지 않는다. **버튼은 늘 보이고, 누른 뒤 `ActivityNotFoundException`이면 그 자리에 안내 문구**(예: "열 수 있는 앱이 없습니다")를 보인다(5.1·7절 `startExternal`). 문구는 ui-design이 정한다.
24. 6.9 S11: 접속 표 3열은 `connections[].posKo`/`form`/`example`, "비슷한 문법"은 `contrasts[].grammarId`·`pair[0..1]`(9.1)이라고 적는다.
25. 6.9.1 앞면 예문의 "문법 형식 부분", 6.10 지문의 "대상 부분": `[[…]]` 대상 구간(`RubySegment.emphasis`, 4.4)이라고 적는다. 지시문 `{대상}`은 문항 `target` 필드(9.2)다.
26. 5.5 해설 카드·6.10 S15·6.11 S16 해설: 출처 열 묶음 P(4.2)로 만든 `ProvenanceChip`(5.7)을 둔다. ui-design 8.1 칩 위치 목록에 "문항·청해 해설"을 더한다.
27. 6.8 카드 종류 스위치 설명: "끄면 그 종류의 새 카드와 복습이 함께 멈춥니다. 기록은 남고 다시 켜면 이어집니다." 학습 급수에서 뺀 급수도 같다(8.2 `SessionCards` 정책). 문구는 ui-design이 다듬는다.
28. 7절에 **전체(Opening)** 행: `AppState.Opening` → 300 ms가 지나면 `StatusScreen` 로딩 "학습 콘텐츠를 준비하고 있습니다"(7절 "시작"). S01 `PREPARING`은 "급수별 개수 계산 중"으로, 6.1의 "첫 실행 콘텐츠 준비(에셋 복사)가 끝나기 전에는" 문장과 10절 "S01(뒤에서 콘텐츠 준비)"을 이 뜻으로 고친다. 12절에 스크린샷 **`app_opening`**을 더한다(M2).
29. 5.9·5.11·6.6·6.7.1 "S09의 해당 고지·자료 카드로": `About(anchor = <고지 ID 또는 자료 ID>)`(7절). 7절 S02 `NO_LEVEL_SELECTED` "급수 고르기": `Settings(openLevelPicker = true)`.
30. 5.8 속도 토글: `Speaker.speak(…, rate)`의 `0.8f`/`1.0f`(8.2). 토글 값은 화면을 떠나면 1.0으로 돌아가는지 유지되는지 ui-design이 정한다(설정 열은 두지 않는다).
31. 13.1 Review 행에 **`newCardsLeftAfterSession`**(7절)을 더한다. 13.1 MockExam/Practice 행의 S12 "이어서 풀기"·"포기하기"는 M19 범위다.
32. 12절 3순위 행에 S12·S15 이름 **`practice_home`**(유형 목록, N-11 줄), **`practice_home_resume`**("이어서 풀기" 카드), **`quiz_result`**(S15)를 더한다(M19 완료 조건이 이 이름).

### 11.3 analyst(content-licensing)에게 넘길 것(매니페스트·sources.md는 이 문서가 고치지 않는다)

1. **판 고정(6.7):** 완료(2026-11-02). 매니페스트 `pinning.method`: S-01·S-02·S-03·S-04 `snapshot`(`pinnedCopy: "content/snapshot/"`), S-06 `url`.
2. **급수 목록·S-04 스냅샷 공개 커밋(6.7):** 미정 — decisions.md **D-08**(사용자 결정)을 따른다. 커밋하지 않기로 정하면 해당 자료 `pinning.method`를 `archive`로 바꾸고 `pinnedCopy`에 사본 URL을 적는다(6.7 방식 B).
3. **S-02 한국어 음 크레딧 문구:** 완료. 매니페스트 S-02 `extraCredits[0].text` = "한국어 음: Charles Muller 제공(KANJIDIC2 경유)"(6.6).
4. **S-09 `extraCredits`:** 완료(Waller 크레딧).
5. **S-01 `usedFields`:** 완료.
6. **S-11:** `usedFields`·고지 경로·파일 이름은 완료(기본 이름 배열 24개, `assets/licenses/`, `ic_ms_<이름>.xml`·`ic_ms_<이름>_fill.xml`, 6.6). **남은 일:** license-verify로 S-11 license·attribution·version을 확정해 `status`를 정한다. 확정되면 게이트 `G-S11`(10절)이 풀리고, 확정 전에는 `release` 빌드 검사 "앱 자산"(6.5)이 FAIL이다.
7. **AndroidX 등 라이브러리:** sources.md 행을 만들지 않는다. `assets/licenses/libraries.json`(6.6)이 기록이다. 이전 요청("AndroidX 출처 행")은 철회.
8. **S-03 `usedFields`·sources.md S-03.used:** 설계는 `kvg:StrokePaths`만 쓰고 획 번호는 앱이 경로 순서·시작점에서 계산한다(6.7·9.4). "stroke order numbers(`kvg:StrokeNumbers_*`)"를 빼거나 "앱이 경로 순서에서 계산"으로 고치고, S-03.used의 "미확정(앱 사용 여부는 설계 단계 결정)"을 이 결정으로 바꾼다.
9. **sources.md S-02.excluded:** "architecture.md 4.3 U-04" → "architecture.md 4.3 **AR-U04**".
10. **D-06 후보의 빈 값(S-05·S-09):** release 빌드는 쓰인 자료의 `attribution`·`licenseUrl`·`modifications`가 비면 FAIL이다(6.5). S-05(CC BY, 버전 미상)를 고르면 표시할 라이선스 URL을 sources.md L-04.waller에 정해 매니페스트 `licenseUrl`에 적고, S-09를 고르면 `attribution`·`modifications`를 채운다. 확정 전 값 채우기는 G-D06 확인 항목이다.

### 11.4 milestones.json에 반영한 것

ui-design 12·13.3이 요구한 테스트·스크린샷 이름을 M4·M5·M6·M12·M17 완료 조건에 넣었다(M4: 입력 무시 300 ms 양방향, 앞·뒷면 표제어 위치, `review_front_recall`·`review_back_word_font200`·`review_front_word_light_land_font200`·`review_back_word_light_land_font200`, 간격 미리보기 5분 30초 → 6분 / M5: `home_sample_banner` / M6: `dict_no_content_for_filter`·`dict_kanji_font200` / M12: 6.7.1 동작·TalkBack·`kanji_detail_strokes`(+`_font200`) / M17: `review_front_grammar`·`review_back_grammar`·`settings_with_grammar`). 이번 정리로 바뀐 계약(문법 출처 열, `listening_script_first`, `quiz_answer.script_shown`, `meaningSourceTexts` 키, `notices` 파이프라인 완성, `pinning.method`, 한국어 필드 루비 금지)도 M2·M3·M7·M14·M15·M16·M19·M23·G-D06에 반영했다. 출처·검수 칩의 하단 시트는 `attribution.json`(`AttributionRepository`)과 About 라우트가 필요해 M7로 옮기고 M7 `dependsOn`에 M4를 더했다.

설계 검증(2차) 반영: `<queries>` TTS_SERVICE(M22), 외부 인텐트 예외 처리(M7·M22·M23), 게이트 `G-S11`(M10 `dependsOn`)과 "앱 자산" 검사(M3), grammar `contrasts_json`·`connections` 3필드(M14·M15), `[[…]]` 대상 구간·문항 `target`(M2·M15·M16·M20), 출처 열 묶음 P(M19·M20·M23·M24), N-11을 M15로(M15·M20), `SessionCards`(M4·M5·M17), About의 ContentDbFailed 동작·`About(anchor)`(M7), Opening 화면(M2), `Settings(openLevelPicker)`(M5), `Speaker.speak(rate)`(M22·M23), `AttributionRepository.load`를 M5로(M5, M7 `dependsOn` M5), M19 S12·연습 탭·S13·S14·스크린샷 이름, M23 S12 청해 진입·N-12 줄·1초 지연·이탈 정지.

## 12. 사용자 승인 항목

ID는 `AR-Uxx`(이 문서)와 `UI-Uxx`(ui-design.md 14절)로 나눈다. 두 문서에 걸친 결정은 **AR-U 하나로만** 부르고 ui-design 14절은 그 ID에 의견만 적는다(AR-U01·AR-U04·AR-U05·AR-U06). 아래에서 사용자가 정할 미정 결정(D-03·D-04·D-06)은 확정하지 않는다.

| ID | 항목 | 선택지 | 추천 | 근거 |
|----|------|--------|------|------|
| AR-U01 | SRS 알고리즘과 평가 단계(버튼 수) | A. FSRS-6(py-fsrs v6.3.2 이식), 4단계 / B. SM-2 원형, 6단계 / C. FSRS-7(공식 참조 구현 확인 후) | **A** | 2.1: 공개 벤치마크 정확도, 4단계 버튼, MIT 참조 구현·테스트 값. 한계: 공식 표에 SM-2·FSRS-6 기본값 행 없음. 로그로 나중 교체 가능 |
| AR-U02 | 경과 일수·기한 기준 | A. 학습일 차이·학습일 시작에 기한(2.2) / B. py-fsrs 그대로(실시간 차) | **A** | "새벽 1시 복습은 어제" 규칙(srs-scheduler 3절)과 맞고, "오늘 할 카드" 수가 하루 안에서 바뀌지 않는다. 참조 벡터 조건에서 `scheduledDays`·안정성·난이도는 같고 복습 기한의 하루 중 시각만 다르다 |
| AR-U03 | 한자 급수 | A. 급수 어휘에 쓰인 한자로 파생(4.3) / B. 한자 급수 목록 자료를 새로 찾아 1단계 검증 / C. MVP에서 한자 카드 제외 | **A** | 확정 가능한 한자 목록 후보가 없다(S-08 제외 권고, S-02 jlpt 구 체계). A는 추가 자료 없이 동작하고 나중에 입력만 바꾼다 |
| AR-U04 | 영어 뜻·한자 뜻 | A. 넣지 않음(어휘 영어 원문, 한자 뜻 모두. 한자는 읽기·한국어 음·쓰인 단어로 대신) / B. KANJIDIC2 영어 meaning 번역을 D-02처럼 허용(decisions.md 새 결정) / C. 영어 원문을 그대로 번들해 보임(크기 예산·스키마 추가) | **A**(MVP) | D-02는 JMdict 영어 뜻 번역만 다룬다. B·C를 고르면 4.3 끝의 스키마 추가 마일스톤을 만든다 |
| AR-U05 | 일본어 글꼴 | A. 기기 글꼴 + `ja-JP` 로캘 / B. Noto Sans JP 등 번들(S-10 조사 필요, APK 증가) / C. A로 시작, M2 스크린샷·실기기 확인에서 일본식 자형이 안 나오거나 글리프가 없으면 B로 전환 | **C** | 추가 라이선스 기록 없이 시작하고, 실패하면 수정 마일스톤으로 B |
| AR-U06 | 하루 새 카드 기본값·범위 | 기본 10 / 20, 범위 5~50(5 단위) | **10, 5~50** | "하루 몇 분" 목표(plan 1절). 0(새 카드 끔)이 필요하면 ui-design에 상태를 더해야 하므로 지금은 두지 않는다 |
| AR-U07 | 콘텐츠 DB 공개 위치(D-01 공개 배포) | A. 저장소 릴리스 첨부(`content/dist` zip) / B. 별도 데이터 저장소 / C. 앱 출시 때 정함 | **C**(구조는 A·B 모두 지원) | 파이프라인이 라이선스 고지를 포함한 묶음을 만든다. 위치는 구현을 막지 않는다 |
| AR-U08 | D-03 기본값 채택 여부(미정, 사용자 결정) | decisions.md D-03 기본값 제안(N5만, 에이전트 전수 검수, `reviewerKind` 기록) 채택 / 다른 수준 | 기본값 채택을 추천(결정은 사용자) | M11이 게이트 G-D03을 기다린다. 스키마는 어느 선택이든 수용(`ko_reviewer_kind` NULL 허용) |
| AR-U09 | D-06 급수 목록(미정, 사용자 결정) | S-06 / S-05 / S-09 (S-07·S-08 비추천) | 설계는 어느 쪽이든 동작. 매칭 품질만 보면 `jmdict_seq`가 있는 S-06이 동형이의어 오매칭이 적다(매니페스트 S-06은 이미 파일별 `files` 형식) | 6.3. 라이선스 판단은 sources.md·analyst 몫. G-D06이 기다린다 |
| AR-U10 | D-04 청해 음성 방식(미정, 사용자 결정) | decisions.md D-04 A. 기기 TTS / B. 사전 생성 음성 / C. 둘 다 | 결정하지 않는다. 설계는 어느 쪽이든 `Speaker`(8.2) 뒤에서 수용: A는 M22 구현 그대로, B·C는 `Speaker`의 다른 구현·`AudioFileMissing` 상태·`check.sh` 음성 허용 변경을 새 마일스톤으로 추가(9.3) | 청해는 4순위. G-D04가 M24·M25를 막는다. M22·M23은 A에 필요한 부분만이라 막히지 않는다 |

ui-design.md 14절의 UI-U 항목(그 문서가 정본, 이 문서에 걸리는 곳만 적음):

| ID | 항목(ui-design 14절) | 이 문서에 걸리는 곳 |
|----|----------------------|---------------------|
| UI-U01 | 검수 칩 숨기기 설정 | 없음(설정 열 없음) |
| UI-U02 | 단어 회상(`vp`) 카드 기본 끔 | 5절 `vocab_recall_enabled` 기본 0 |
| UI-U03 | 동적 색 안 씀 | 없음 |
| UI-U04 | 아이콘(Material Symbols 벡터 XML) | 6.6 `assets/licenses/`·`ic_ms_*` 대조 테스트, 매니페스트 S-11 |
| UI-U05 | 모의고사 시간(떠나면 멈춤) | 9.2 `quiz_attempt.elapsed_ms` |
| UI-U06 | 목표 기억률을 설정에 둘지 | 5절 `desired_retention`(화면 없음), 7절 `SettingsUiState` |

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-11-02 | 처음 작성 |
| 2026-11-02 | 설계 검증 반영: 콘텐츠 DB 버전 고정·버전 올림 시 에셋 재생성(4.2·4.5), 원본 스냅샷(6.7), 학습일 지역 시각 정의·불변식(3), 참조 벡터 비교 대상(2.2), 학습 단계 `Duration`·`IntervalPreview` 하나로(3·8.1), 세션 다시 계획·형제 카드 규칙(8.1), `user_settings` v1 열 확장(5), UI 상태를 ui-design 13.1 기준으로(7), 저장 실패 계약(7·8.2), 바인드 변수 한도(4.5·8.2), 되돌리기 로그 ID 기준(5), 게이트 마일스톤(10), 한자 파생 경계 규칙(4.3), 백업(5.1), 송리가나 분리·혼용 문장 규칙(4.4), 저작자 표시 형식(6.6), Speaker 팩토리·상태(8.2), 문항 재개 열·문장 배열(9.2), 다중 파일 fetch(6.2), 승인 항목 ID 접두어(12), ui-design 대조(11) |
| 2026-11-02 | design-review 후속(wf_31d285cb-453): 정본 기준(0·11.1), 일본어 구간 문자 집합·한국어 필드 루비 금지(4.4·6.2·6.4·9.1), `pinning.method`와 방식 B `pinnedCopy`·S-04 스냅샷(4.6·6.2·6.7·10), `notices` 파이프라인 완성·`showWhen` 없음·`modelNames` 파이프라인 수집·`extraCredits` 원문 그대로·`meaningSourceTexts` 키 7개·최소 표시는 쓰인 자료 기준·S-11 고지 경로와 아이콘 대조 테스트(6.5·6.6), `listening_script_first`·빈 `target_levels` 거부·`NO_LEVEL_SELECTED` 방어용(5·7), 간격 미리보기 표시 정본은 ui-design 5.3·`IntervalFormat`·"1분 안에"(7), `ReviewCardUi.Grammar` 출처·예문 번역·`needsReview`와 grammar `ko_*`·`needs_review` 열(7·9.1), `quiz_answer.script_shown`(9.2), About 보조 타입(7), 11절 ui-designer·analyst 목록 갱신, 승인 ID `AR-Uxx`/`UI-Uxx`·AR-U10(D-04)(12) |
| 2026-11-02 | 설계 검증(2차) 반영: `<queries>` TTS_SERVICE·외부 인텐트는 `startActivity` + `ActivityNotFoundException`(5.1·7·9.3), 출처 열 묶음 P와 `question`·`exam_template`·`listening` 열(4.2·9.2·9.3), `[[…]]` 대상 구간·`RubySegment.emphasis`·문항 `target`(4.4·9.1·9.2), grammar `contrasts_json`·`connections {posKo, form, example}`(9.1), 6.5 검사 "출처 열"·"앱 자산"·N-11/N-12·빈 저작자 값, N-11 포함 시점(6.6), `G-S11`(10), 시작·Opening·S90 NavHost·`About(anchor)`·`Settings(openLevelPicker)`(7), About의 reviewCounts 실패 처리·`AttributionData`(7), `SessionCards`·`statesOfKinds`·`cardLevels`(dueCandidates·nextDueAt·countDueBefore 대체, 8.2), `AttributionRepository` 분리, `Speaker.speak(rate)`(8.2), 획 번호 앱 계산(6.7·9.4), 낡은 참조(4.2), 11.2 23~32·11.3 6·8~10 |
