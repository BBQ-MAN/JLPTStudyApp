# JLPTStudyApp

한국어 화자를 위한 일본어능력시험(JLPT N5~N1) 학습 Android 앱. Kotlin + Jetpack Compose.

> **상태:** 개발 준비 단계. 개발 하네스(에이전트·스킬·워크플로)와 빌드 골격만 구성되어 있고, 앱 기능 코드는 아직 없다. 기능은 아래 계획대로 마일스톤 단위로 만든다.

## MVP와 마일스톤 계획

기능 우선순위:

1. **단어·한자 플래시카드 + 간격 반복(SRS)** — MVP. 스케줄러는 순수 Kotlin 모듈 `:core:srs`, 테스트 우선. 알고리즘(FSRS 계열 / SM-2 계열)은 설계 단계에서 근거를 들어 고른다.
2. 문법 학습과 예문
3. 급수별 모의고사·문제 풀이
4. 청해(듣기) 연습 — 기기 TTS(ja-JP)

**MVP:** 1순위 전체 + 급수 선택 + 저작자 표시 화면 + 오프라인 동작(콘텐츠는 앱에 번들된 DB, 학습 기록은 Room).

| 단계 | 내용 |
|------|------|
| 준비(완료) | 하네스, Gradle 골격, 빌드·테스트 확인 |
| A. 콘텐츠 근거 | 후보 자료의 출처·라이선스를 원 출처로 확인, 한국어 뜻풀이 방식 결정 |
| B. 설계 | 아키텍처·UI 디자인 → ECC 검증 → 승인 |
| MVP | SRS 스케줄러 → 콘텐츠 파이프라인 v1 → 앱 골격 → 복습·플래시카드 → 단어·한자 화면 → 저작자 표시 → 실기기 확인 |
| 이후 | 문법(2) → 모의고사(3) → 청해(4), 급수는 N5부터 N1까지 콘텐츠 마일스톤으로 확장 |

자세한 계획과 설계 단계에서 정할 결정은 [docs/plan.md](docs/plan.md)에 있다. 마일스톤은 설계 단계에서 `docs/spec/milestones.json`으로 만들어진다.

## 콘텐츠와 라이선스

- JLPT 공식 어휘·문법 목록은 없다. 급수 분류는 커뮤니티 목록에 근거하며 앱에 "공식 아님"을 밝힌다.
- 기출 문제·시판 교재 문장을 복제하지 않는다. 모의고사·문법 설명·예문은 직접 작성하거나 허용 라이선스 자료만 쓴다.
- 청해 음성은 녹음 자료 없이 기기 TTS(ja-JP) 또는 라이선스가 확인된 음성만 쓴다.
- 후보 자료: JMdict·KANJIDIC2(EDRDG), KanjiVG, Tatoeba 예문, 커뮤니티 JLPT 급수 목록. 라이선스는 개발 1단계에서 원 출처로 확인해 `docs/content/sources.md`에 기록한다(현재 **미확인**).
- 콘텐츠는 재현 가능한 파이프라인(`content/pipeline/`)으로만 만들고, 출처·라이선스 매니페스트(`docs/content/manifest.json`)에서 앱 내 저작자 표시 화면 데이터를 만든다.

## 개발 환경

| 항목 | 요구 사항 | 이 프로젝트에서 찾는 위치 |
|------|-----------|---------------------------|
| JDK | AGP가 요구하는 버전(최소 17) | `JAVA_HOME`. 없으면 Android Studio 내장 JBR(`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) |
| Android SDK | compileSdk 37 플랫폼, build-tools, platform-tools | `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir` |
| Gradle | 래퍼 9.8.2 | `./gradlew` (시스템 Gradle 쓰지 않음) |
| python3 | 콘텐츠 파이프라인(표준 라이브러리만) | `PATH` |
| 실기기 | Android 폰, 일본어 TTS 음성 데이터 | `adb devices` |

주요 버전: AGP 9.4.0, Kotlin 2.4.21, Compose BOM 2026.09.00, Room 2.8.5, Robolectric 4.17(테스트 SDK 35), Roborazzi 1.77.0 — `gradle/libs.versions.toml`. 자세한 설치 방법은 [env-setup.md](.claude/skills/android-build-verify/references/env-setup.md)에 있다.

`local.properties`는 컴퓨터마다 경로가 다르므로 커밋하지 않는다.

## 빌드와 검증

검증은 항상 아래 스크립트 하나로 한다. 사람이 돌릴 때와 에이전트가 돌릴 때 결과가 같아야 하기 때문이다.

```bash
.claude/skills/android-build-verify/scripts/verify.sh                 # (콘텐츠 검사) + test + lintDebug + assembleDebug
.claude/skills/android-build-verify/scripts/verify.sh :core:srs:test  # 특정 태스크만
```

| 종료 코드 | 뜻 |
|-----------|-----|
| 0 | 통과 |
| 1 | 빌드·테스트·lint·콘텐츠 검사 실패. 로그는 `_workspace/build/verify_*.log` |
| 2 | 환경 없음(JDK, SDK, `./gradlew`, 파이프라인이 있을 때 python3 중 하나) |

직접 돌릴 때는 `JAVA_HOME`을 위 JBR로 지정하고 `./gradlew help`, `./gradlew :app:assembleDebug`, `./gradlew test`를 쓴다. 앱 화면이 생긴 뒤에는 `./gradlew :app:installDebug`로 실기기에 설치한다.

## 개발 방식: Claude Code 하네스

이 저장소는 Claude Code 에이전트 팀이 개발한다. Claude Code에서 "JLPT 앱 개발 시작", "다음 마일스톤", "출처·라이선스 다시 확인", "디자인 검증 다시"처럼 요청하면 `jlpt-app-orchestrator` 스킬이 아래 순서로 진행한다. 각 워크플로를 실행하기 전에는 예상 에이전트 수를 알리고 동의를 받는다.

| 단계 | 내용 | 산출물 |
|------|------|--------|
| 1. 출처·라이선스 확정 | 후보 자료를 원 출처로 기록하고, 주장마다 독립 검증자 2명이 원 페이지·라이선스 전문으로 교차 검증 | `docs/content/sources.md`, `manifest.json` |
| 2. 설계 | 아키텍처(SRS 선택 포함)·UI 디자인 작성 → ECC 리뷰어(`ecc:architect`, `ecc:a11y-architect`, 디자인 스킬)로 검증 → 사용자 승인 | `docs/spec/architecture.md`, `milestones.json`, `ui-design.md` |
| 3. 구현 | 마일스톤마다 구현 → 빌드 → QA·ECC 코드(Kotlin/Python)·디자인·콘텐츠 리뷰 → 지적 검증 → 수정(최대 2회) | 코드, 콘텐츠, 테스트, `screenshots/` |
| 4. 실기기 확인 | 일본어 글자 모양, 오프라인, TTS, TalkBack, 기록 보존 | `_workspace/04_device_*` |

마일스톤 상태(`docs/spec/milestones.json`):

| 상태 | 뜻 |
|------|-----|
| `todo` | 아직 안 함 |
| `done` | 완료 |
| `awaiting-device` | 코드는 완료됐고 실기기 확인을 기다림 |
| `needs-decision` | 구현자와 검증자의 판단이 갈려 사람이 정해야 함 |
| `blocked` | 실패 |

하네스 구성:

| 위치 | 내용 |
|------|------|
| `.claude/agents/` | 전문 에이전트 7종: 출처·라이선스 기록, 아키텍처, UI 디자인, 콘텐츠 작성·검수, 콘텐츠 파이프라인, 앱 구현, 통합 QA |
| `.claude/skills/` | 작업 규칙 스킬 9종(오케스트레이터 포함)과 워크플로 스크립트 3종 |
| `.claude/settings.json` | android-skills 플러그인(Android 공식 스킬) 프로젝트 범위 사용 |
| `CLAUDE.md` | 하네스 호출 조건과 변경 이력 |

하네스 자체를 고칠 때는 `harness:harness` 또는 `harness:evolve` 스킬을 쓴다. 워크플로 판정 로직을 바꿨다면 회귀 테스트를 돌린다.

```bash
node .claude/skills/jlpt-app-orchestrator/workflows/implement-milestones.test.cjs
python3 -I .claude/skills/ui-design-review/scripts/contrast.py 1C1B1F:FFFBFE   # 디자인 토큰 색 대비 계산
```

## 디렉터리

```
app/                    Android 앱(:app). 골격만 있음
core/srs/               간격 반복 스케줄러(:core:srs, 순수 Kotlin). 비어 있음
core/quiz/              문제 풀이·채점(:core:quiz, 순수 Kotlin). 비어 있음
docs/plan.md            제품 계획
docs/content/           출처·라이선스 기록과 매니페스트 (1단계에서 생성)
docs/spec/              설계 문서 (2단계에서 생성)
content/                콘텐츠 파이프라인·직접 작성 콘텐츠 (구현 중 생성, content/raw/는 커밋 안 함)
screenshots/            화면 상태별 스크린샷 (구현 중 생성)
_workspace/             에이전트 중간 산출물, 빌드 로그, 실기기 기록
.claude/                개발 하네스
```
