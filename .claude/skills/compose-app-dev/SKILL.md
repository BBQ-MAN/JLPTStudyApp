---
name: compose-app-dev
description: "JLPT 학습 앱의 Kotlin·Jetpack Compose 구현 규칙: Gradle 골격과 버전 카탈로그, 모듈 구성(:core:srs, :core:quiz, :app), 시간·학습일 처리, UDF 상태 관리(ViewModel + StateFlow), 번들 콘텐츠 DB와 학습 기록 Room의 분리, 일본어 표시(일본어 로캘, 후리가나), 플래시카드 복습 세션, TTS 청해, 저작자 표시 화면, 계층별 테스트, Android 공식 스킬 연결. 구현자·설계자·리뷰어가 따르는 기준이며, 모듈·계층 결정은 ecc:android-clean-architecture보다 이 스킬 2절을 우선한다. 새 기능·화면 추가 요청 자체는 jlpt-app-orchestrator로 시작한다. SRS 알고리즘 규칙은 srs-scheduler, 콘텐츠 DB 생성은 content-pipeline, 시각 디자인 기준은 ui-design-review를 쓴다."
---

# Compose App Dev — 앱 구현 규칙

이 앱에서 사용자를 떠나게 만드는 결함은 세 가지다: 복습 기록이 사라지거나 날짜가 어긋나는 것, 일본어가 틀린 글자 모양이나 깨진 글자로 보이는 것, 콘텐츠를 고칠 때 학습 기록이 끊기는 것. 아래 규칙은 이 셋을 막는 데 맞춰져 있다.

## 1. 프로젝트 골격

- Gradle Kotlin DSL + `gradle/libs.versions.toml` 버전 카탈로그. 모든 의존성은 카탈로그를 거친다.
- 현재 버전은 TimemoreDotScaleApp에서 검증된 조합(AGP 9.4.0, Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5, Robolectric 4.17, Roborazzi 1.77.0, Gradle 9.8.2)이다. 버전을 올리거나 새 라이브러리를 넣을 때는 기억으로 적지 않는다. 공식 릴리스 노트에서 현재 안정 버전을 확인하고, 확인한 날짜를 `architecture.md`에 적는다.
- AGP 9는 Kotlin을 내장한다. 앱 모듈에 `org.jetbrains.kotlin.android` 플러그인을 따로 넣지 않는다(`agp-9-upgrade` 공식 스킬).
- Robolectric은 `app/src/test/resources/robolectric.properties`의 `sdk=35`로 돈다. Robolectric 4.17이 compileSdk 37을 아직 지원하지 않기 때문이다. Robolectric을 올리면 이 값을 다시 확인한다.
- 기본 의존성: Compose + Material 3, Navigation Compose, Lifecycle(`collectAsStateWithLifecycle`), Room + KSP, kotlinx-coroutines, kotlinx-serialization. DI는 작은 앱이므로 수동 DI(앱 범위 컨테이너)를 기본으로 하고, Hilt가 필요해지면 이유를 적는다.
- 테스트 의존성: JUnit, kotlinx-coroutines-test, Turbine, Robolectric + Roborazzi(스크린샷).
- 앱은 인터넷 권한을 요청하지 않는다(오프라인 우선). 네트워크가 필요한 기능이 생기면 설계 변경으로 올린다.

## 2. 모듈 구성(기본안)

| 모듈 | 종류 | 내용 | 이유 |
|------|------|------|------|
| `:core:srs` | 순수 Kotlin(JVM) | 간격 반복 스케줄러, 카드 상태, 평가 | 알고리즘을 Android 없이 수 초 안에 테스트(`srs-scheduler`) |
| `:core:quiz` | 순수 Kotlin(JVM) | 문제 세트 구성, 선택지 섞기, 채점, 모의고사 진행 | 채점 규칙을 화면과 분리해 테스트 |
| `:app` | Android 앱 | 화면, ViewModel, Room(콘텐츠·학습 기록), TTS, DI, 내비게이션 | 나머지는 하나로 둔다 |
| `content/pipeline/` | Gradle 밖 | 원본 → 번들 DB 스크립트(`content-pipeline`) | 앱 빌드와 독립적으로 재현 |

기능이 늘어 `:app` 빌드가 느려질 때만 `:feature:*`로 나눈다. 나누는 이유가 없으면 나누지 않는다. 순수 모듈에는 Android·Room 의존성을 넣지 않는다.

## 3. 시간과 학습일

- 시각은 `java.time.Clock`을 생성자로 주입받아 `Instant`로 읽는다. `System.currentTimeMillis()`를 직접 부르면 테스트에서 날짜를 고정할 수 없다.
- 저장은 UTC epoch ms(`Long`). 사용자에게 보이는 "오늘"은 사용자 시간대와 학습일 경계(기본 04:00, `srs-scheduler` 3절)로 계산한다. 경계 계산은 한 곳(`:core:srs`)에만 둔다.
- 시간대 변경·서머타임을 고려한다. 한국은 서머타임이 없지만 사용자가 여행 중 시간대를 바꿀 수 있다.
- 계층 경계의 타입에 단위를 드러낸다(예: `reviewedAtEpochMs: Long`, `intervalDays: Int`).

## 4. 상태 관리

- 화면마다 `data class XxxUiState` 하나, ViewModel은 `StateFlow<XxxUiState>`를 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 초기값)`으로 내보낸다.
- 사용자 동작은 ViewModel 함수 호출(`onRate(Rating.Good)`)로 받는다. 일회성 이벤트가 꼭 필요할 때만 `Channel`을 쓴다.
- 화면은 `collectAsStateWithLifecycle()`로 수집한다.
- 로딩·빈 상태·오류를 `UiState`에 명시한다(`ui-design-review` D9 상태 목록과 1:1).

## 5. 데이터: 콘텐츠 DB와 학습 기록 DB를 나눈다

| | 콘텐츠 DB | 학습 기록 DB |
|---|---|---|
| 내용 | 단어, 한자, 문법, 예문, 문제, 청해 대본, 출처 ID | 카드 상태, 복습 로그, 모의고사 결과, 설정 |
| 만드는 쪽 | 파이프라인(`content-pipeline`)이 에셋으로 번들 | 앱 |
| 열기 | Room `createFromAsset`, 읽기 전용으로 다룬다 | Room, 스키마 내보내기 + 마이그레이션 |
| 갱신 | 앱 업데이트 때 통째로 교체 | 마이그레이션만. 릴리스에서 `fallbackToDestructiveMigration` 금지 |

- 둘을 나누는 이유: 콘텐츠를 고칠 때 DB를 교체해도 사용자의 학습 기록이 지워지지 않게 하기 위해서다.
- 학습 기록은 콘텐츠를 **안정 ID**(JMdict `ent_seq`, 한자 문자, 직접 작성 콘텐츠 ID)로 가리킨다. SQLite rowid나 자동 증가 키로 가리키지 않는다. 콘텐츠 판이 바뀌면 rowid가 바뀐다.
- 콘텐츠에서 사라진(`retired`) ID를 가리키는 학습 기록은 지우지 않고 화면에서 제외한다.
- Room은 미리 만든 DB의 스키마가 엔티티와 다르면 열 때 실패한다. 콘텐츠 엔티티를 바꾸면 파이프라인이 Room이 내보낸 스키마 JSON(`app/schemas/`)의 `createSql`을 그대로 쓰게 하고(`content-pipeline` 4절), 번들 DB를 Room으로 열어 쿼리하는 Robolectric 테스트를 둔다.
- 복습 한 번의 평가는 카드 상태 갱신과 복습 로그 추가를 한 트랜잭션으로 즉시 쓴다. 세션 끝에 몰아서 쓰면 앱이 종료될 때 기록이 사라진다.
- DB 접근은 메인 스레드 밖(`Dispatchers.IO` 또는 Room의 suspend/Flow)에서 한다. 첫 실행 때 에셋 복사도 마찬가지다.

## 6. 일본어 표시

- **글꼴 로캘:** 한·중·일 한자는 같은 코드 포인트를 쓰므로(한자 통합) 한국어 기기에서는 일본어 한자가 한국식 글자 모양으로 그려질 수 있다. 일본어 텍스트에는 `TextStyle(localeList = LocaleList("ja-JP"))`를, 한국어와 섞인 문장은 `AnnotatedString`의 일본어 구간에 `SpanStyle(localeList = …)`를 건다. 일본어용 `TextStyle`은 테마에 한 번 정의해 재사용한다.
- 글꼴을 앱에 번들할지(예: Noto Sans JP)는 설계에서 정한다. 번들하면 글꼴도 출처·라이선스 기록 대상이다(`content-licensing`).
- **후리가나:** Compose `Text`에는 루비 지원이 없다. 콘텐츠 DB의 후리가나 구간 형식(`architecture.md`)을 읽어 글자 위에 읽기를 배치하는 컴포저블을 하나 만들고 모든 화면이 그것을 쓴다. 글꼴 200%에서 줄바꿈될 때 읽기가 본문과 어긋나지 않는지 스크린샷으로 확인한다.
- **TalkBack:** 일본어 구간에 로캘을 걸어 두면 화면 낭독기가 일본어 음성으로 읽을 수 있다. 실제 동작은 실기기에서만 확인할 수 있으므로 완료 조건에 `unverified`로 남긴다.

## 7. 복습 세션

- 세션 대기열은 `:core:srs`가 계산한 "오늘 기한 카드 + 오늘 새 카드 한도"로 만든다. 화면은 순서를 바꾸지 않는다.
- 평가 버튼은 스케줄러의 평가 값과 1:1로 대응하고, 각 버튼에 다음 간격 미리보기를 보여 준다(`ui-design-review` D4).
- 마지막 평가 되돌리기는 복습 로그를 근거로 이전 카드 상태를 복원한다.
- 같은 단어의 다른 방향 카드(예: 일→한, 한→일)는 별개 카드 ID를 갖는다.

## 8. 청해(TTS)

- 녹음 자료를 에셋에 넣지 않는다. 음성은 기기 `TextToSpeech`(Locale.JAPAN)로 만든다(`content-licensing` 1절). 사전 생성 음성은 `docs/content/decisions.md` D-04에서 채택되면 같은 인터페이스의 다른 구현으로 더한다.
- `TextToSpeech`는 인터페이스(예: `Speaker`) 뒤에 두고 테스트에서는 가짜를 쓴다. Robolectric에서 실제 음성 합성은 확인할 수 없다.
- 초기화는 비동기다. 상태(준비 중, 사용 가능, 일본어 데이터 없음, 엔진 없음, 오류)를 `UiState`로 내보내고, 일본어 데이터가 없으면 설치 안내(`TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`)를 행동 버튼으로 준다.
- 화면을 떠나거나 ViewModel이 정리될 때 `stop()`·`shutdown()`을 부른다.
- 청해 대본의 `ttsText`(읽기 힌트를 반영한 문장)가 있으면 표시 문장 대신 그것을 읽는다(`jlpt-content-authoring` 6절).

## 9. 저작자 표시 화면

- 파이프라인이 매니페스트에서 만든 저작자 표시 데이터(에셋)만 읽어 그린다. 화면 코드에 출처 문구를 직접 쓰지 않는다. 출처가 바뀔 때 화면만 옛 문구로 남는다.
- 설정에서 두 번 이내 이동으로 닿게 한다. "JLPT 공식 자료 아님" 고지와 한국어 뜻 생성 방식 안내를 함께 둔다(`content-licensing` 5절).
- 테스트: 콘텐츠 DB에 쓰인 모든 출처 ID가 화면 데이터에 있는지 확인한다.

## 10. 테스트 배치

| 대상 | 방법 |
|------|------|
| SRS 스케줄러 | JVM 테스트, 고정 시계, 참조 벡터(`srs-scheduler` 4절), 테스트 먼저 |
| 채점·문제 세트 | JVM 테스트(`:core:quiz`) |
| ViewModel | `runTest` + Turbine + 가짜 저장소·가짜 `Speaker` |
| Room DAO·마이그레이션·번들 DB 열기 | Robolectric |
| 화면 | Roborazzi 스크린샷(상태별, `ui-design-review` 3절), 테스트용 작은 콘텐츠 픽스처 |

실기기에서만 확인할 수 있는 항목(TTS 음성, 기기 글꼴의 한자 모양, TalkBack 언어 전환)은 오케스트레이터 4단계로 넘기고, 단위 테스트로 흉내 낸 것을 실기기 확인으로 보고하지 않는다.

## 11. Android 공식 스킬(android-skills 플러그인)

AGP 9·edge-to-edge·Navigation 3처럼 최근에 바뀐 영역은 기억으로 구현하면 틀리기 쉽다. 아래 주제를 다룰 때 해당 공식 스킬을 먼저 읽는다.

| 주제 | 공식 스킬 | 언제 |
|------|-----------|------|
| Gradle·AGP 골격 | `agp-9-upgrade` | 1절 골격을 바꾸거나 AGP·Kotlin 플러그인 버전을 정할 때(빌트인 Kotlin, KSP) |
| 시스템 바·인셋 | `edge-to-edge` | 화면 골격(Scaffold, 인셋 처리)을 만들 때. targetSdk 35 이상은 edge-to-edge가 강제된다 |
| 가로·큰 화면 | `adaptive` | 태블릿·가로 배치, 하단 탭 ↔ `NavigationRail` 전환 |
| 화면 이동 | `navigation-3`, `navigation-event` | 내비게이션을 새로 고르거나 뒤로 가기(Predictive Back)를 구현할 때 |
| 재구성 비용 | `jetpack-compose-performance` | 단어 목록 같은 긴 `LazyColumn`, 카드 넘김 애니메이션 |
| 테스트 구성 | `testing-setup` | 10절 테스트 배치를 처음 만들 때 |
| 메인 스레드 차단 | `anr-audit-and-fix` | 번들 DB 첫 복사·큰 쿼리·TTS 초기화 |
| 외부 Intent | `android-intent-security` | TTS 데이터 설치 Intent, 라이선스 링크 열기 |
| 릴리스 축소 | `r8-analyzer` | 릴리스 빌드의 R8·keep 규칙(Room, serialization) |

불러오는 법: Skill 도구로 `android-skills:<이름>`을 부른다. 도구가 없거나 이름을 못 찾으면 `~/.claude/plugins/cache/android-skills/android-skills/*/` 아래에서 Glob `**/<이름>/SKILL.md`로 찾아 Read로 읽는다(플러그인 판 번호가 바뀌어도 찾히게 `*`를 쓴다). 둘 다 안 되면 그 사실을 보고하고 이 스킬만으로 진행한다.

공식 스킬은 Android 일반 모범 사례다. 이 스킬과 충돌하면 이 스킬이 우선한다. 이 스킬에는 이 앱의 데이터·콘텐츠 제약과 `architecture.md`에서 이미 정한 결정이 들어 있기 때문이다. 공식 스킬 때문에 이미 정한 결정(예: 내비게이션 라이브러리, DI 방식)을 바꾸고 싶으면 코드에서 바꾸지 말고 지적·설계 변경으로 올린다.
