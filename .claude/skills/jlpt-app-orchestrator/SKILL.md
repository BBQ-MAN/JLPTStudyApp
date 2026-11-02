---
name: jlpt-app-orchestrator
description: "JLPT 학습 Android 앱(Kotlin + Compose, 한국어 화자용, N5~N1)의 개발을 조율한다. 콘텐츠 출처·라이선스 확정 → 아키텍처·UI 디자인 설계(SRS 알고리즘 선택 포함)와 ECC 설계·디자인 검증 → 마일스톤별 구현·QA·ECC 코드/디자인·콘텐츠 리뷰 → 실기기 확인까지 진행한다. '앱 개발 시작', '다음 마일스톤', 'M3 구현', '기능 추가', '화면 추가', '문법 콘텐츠 추가', '모의고사 문제 추가', '출처·라이선스 다시 확인', '새 자료 도입', '설계 다시', '디자인 검증 다시', 'QA 다시', '콘텐츠 검토 다시', '실기기 확인 결과 반영', 그리고 앱 코드·콘텐츠·docs 산출물에 대한 '재실행', '이어서', '수정', '보완' 요청에 사용한다. .claude/ 아래 하네스 파일(에이전트·스킬·워크플로)을 바꾸는 요청은 harness:harness 또는 harness:evolve를 쓴다. 단순 질문이나 한두 줄 수정은 직접 처리한다."
---

# JLPT 학습 앱 오케스트레이터

## 실행 모드: 혼합

| 단계 | 실행 모드 | 이유 |
|------|-----------|------|
| 0 상태 확인 | 메인 직접 | 어디서 시작할지 정하고 실행 동의를 받는다 |
| 1 출처·라이선스 확정 | 워크플로 `workflows/license-verify.js` | 작성자와 분리된 추출자가 뽑은 주장을 코드로 나눠 원 출처로 교차 검증한다 |
| 2 설계 | 서브에이전트 2명 병렬 → 워크플로 `workflows/design-review.js` → 사용자 승인 | 설계는 독립 작업, 검증은 정해진 관점 × 지적 |
| 3 구현 | 워크플로 `workflows/implement-milestones.js` | 구현 → 리뷰 → 검증 → 수정 반복과 통과 조건을 코드로 정한다 |
| 4 실기기 확인 | 메인 + 사용자 | TTS 음성, 기기 글꼴, TalkBack은 실제 기기에서만 확인된다 |

워크플로 호출은 모두 `Workflow({ scriptPath: ".claude/skills/jlpt-app-orchestrator/workflows/<파일>", args })` 형식이다.

## 에이전트 구성

| 역할 | 유형 | 모델 | 스킬 | 산출물 |
|------|------|------|------|--------|
| 출처·라이선스 기록 | `content-licensing-analyst` | opus | content-licensing | `docs/content/sources.md`, `manifest.json` |
| 주장 추출·검증 | 기본 워크플로 에이전트 | 세션 모델 | — | 주장·판정(반환값) |
| 아키텍처 | `android-architect` | opus | compose-app-dev, srs-scheduler, content-pipeline | `docs/spec/architecture.md`, `milestones.json` |
| UI 디자인 | `ui-designer` | opus | ui-design-review + ECC 디자인 스킬 | `docs/spec/ui-design.md` |
| 앱·순수 모듈 구현 | `feature-engineer` | sonnet(마일스톤 `model`로 상향) | compose-app-dev, srs-scheduler, ui-design-review, android-build-verify | 코드, 스크린샷, `_workspace/03_feature-engineer_*` |
| 콘텐츠 파이프라인 | `content-engineer` | sonnet(마일스톤 `model`로 상향) | content-pipeline, content-licensing, android-build-verify | `content/pipeline/`, 번들 DB, `_workspace/03_content-engineer_*` |
| 콘텐츠 작성·검수 | `jlpt-content-author` | opus | jlpt-content-authoring, content-licensing | `content/authored/`, `_workspace/03_jlpt-content-author_*` |
| 통합 QA | `android-qa-inspector` | opus | android-integration-qa | 지적·완료 조건 판정·`verifyPassed`(반환값) |
| 설계 리뷰(ECC) | `ecc:architect` | ECC 정의 | ecc:android-clean-architecture(참고) | 지적 목록 |
| 접근성 리뷰(ECC) | `ecc:a11y-architect` | ECC 정의 | ecc:accessibility | 지적 목록 |
| 코드 리뷰(ECC) | `ecc:kotlin-reviewer` / `ecc:python-reviewer` | ECC 정의 | — | 지적 목록 |
| 시각 디자인 리뷰 | 기본 워크플로 에이전트 | 세션 모델 | ecc:make-interfaces-feel-better, ecc:design-system | 지적 목록 |
| 콘텐츠 리뷰 | 기본 워크플로 에이전트 | 세션 모델 | jlpt-content-authoring 8절, content-licensing | 지적 목록 |

ECC 에이전트에는 Skill 도구가 없을 수 있다. 워크플로 프롬프트가 스킬 파일 경로를 함께 주므로 Read로 읽는다.

## 0단계: 상태 확인과 실행 동의

1. 확인: `docs/plan.md`, `docs/content/{sources.md, manifest.json}`, `docs/spec/{architecture.md, ui-design.md, milestones.json}`, `_workspace/run_meta.json`, `_workspace/04_device_*`.
2. 시작 단계를 정한다.
   - `sources.md` 없음, 또는 사용자가 새 자료 도입·라이선스 재확인을 요청 → 1단계
   - `architecture.md` 또는 `ui-design.md` 없음 → 2단계
   - 사용자가 `milestones.json`에 없는 기능·화면·콘텐츠를 요청 → 2단계(architect가 마일스톤 추가, 화면이면 ui-designer가 `ui-design.md` 갱신, 새 자료가 필요하면 먼저 1단계) → `design-review`(`only` 관점) → 승인 → 3단계
   - 이미 `done`/`awaiting-device`인 마일스톤의 "QA 다시", "디자인 검증 다시", "콘텐츠 검토 다시", 그리고 리뷰어 누락(`missingReviewers`)만으로 `blocked`가 된 마일스톤 → 3단계를 `reviewOnly: true`로(해당 마일스톤만, UI면 `_workspace/03_report_*.md`에 기록한 `screenshots`를, 없으면 `screenshots/` 아래 해당 화면 파일을 마일스톤 객체의 `screenshots`에 넣어 넘긴다)
   - `milestones.json`에 진행 가능한 `todo` 있음 → 3단계(사용자가 특정 마일스톤을 지정하면 그것만)
   - `needs-decision` 마일스톤 있음 → 갈린 지적과 양쪽 근거를 보여 주고 사용자 결정(수정 지시 / 지적 기각 후 `done`)을 받는다
   - `blocked`만 남음 → 원인을 보고하고 재시도·재계획(architect) 중 사용자 결정을 받는다
   - 실기기 확인 결과를 받았거나 확인 요청 → 4단계
   - "설계 다시", "디자인만 다시" → 2단계의 해당 부분만
3. 계획 자체가 바뀌어 처음부터 다시 해야 하면 `_workspace/`를 `_workspace_{YYYYMMDD_HHMMSS}/`로 옮기고 시작한다. `docs/`는 옮기지 않는다(각 에이전트가 기존 문서를 읽고 고친다).
4. **실행 동의:** 시작 단계, 대상 마일스톤, 실행할 워크플로와 대략의 에이전트 수(아래 표)를 사용자에게 알리고 동의를 받는다. 같은 세션에서 동의받은 범위 안의 반복 실행(수정 후 재실행 등)은 다시 묻지 않는다.

| 워크플로 | 대략의 에이전트 수 |
|----------|---------------------|
| license-verify | 3 + 주장 수 × 2. 2026-11-02 첫 실행 실측: 후보 11종, 주장 278건 → 559. 일부 자료만 바뀌어도 전체를 다시 검증한다 |
| design-review | 4 + 지적 수 + 반영 최대 3 + 재확인 1 |
| implement-milestones | 마일스톤당 (구현 1 + 리뷰 2~4 + 검증 n) × (1 + 수정 라운드) |

### 재개(resume) 주의

`resumeFromRunId`는 프롬프트가 같은 `agent()` 호출의 **캐시된 결과**를 돌려준다. 이 하네스의 프롬프트에는 문서 내용이 아니라 경로만 들어 있으므로, 문서나 코드가 바뀐 뒤 재개하면 바뀌기 전의 결과가 재사용된다. 재개는 같은 실행이 중간에 끊겼고 그 뒤 파일을 고치지 않았을 때만 쓴다. 그 외에는 새로 실행한다.

`_workspace/run_meta.json` 형식: `{ "runs": [ { "workflow": "license-verify", "runId": "wf_...", "date": "YYYY-MM-DD", "args": {...} } ] }` — 실행마다 항목을 덧붙인다.

## 1단계: 출처·라이선스 확정 — 실행 모드: 워크플로

1. `Workflow({ scriptPath: ".claude/skills/jlpt-app-orchestrator/workflows/license-verify.js", args: { feedback?, candidates? } })`
2. 결과 보고: `confirmed / unconfirmed / refuted` 개수, 미확정 항목과 확인 방법, `partialVerdicts`(검증자 결과가 빠진 주장 수), `mismatches`(판정과 기록 표시가 다른 항목), `untracked`(검증 목록 밖이라 미확정으로 내린 항목). `error`가 있으면 성공으로 보지 않는다. `mismatches`가 있으면 해당 항목을 직접 확인해 기록을 맞추거나 다시 실행한다.
3. **사용자 승인 항목:** `docs/content/decisions.md`의 미정 항목(급수 목록 자료 선택, 한국어 뜻 검수 수준, 청해 음성 방식 등). 한국어 뜻풀이 방식(D-02)과 콘텐츠 DB 배포 조건(D-01)은 2026-11-01에 결정됐다. 새 결정은 `decisions.md`에 적고 analyst가 `sources.md`에 반영하게 한다.
4. 어휘·한자 핵심 자료가 확정되지 않으면 2단계는 진행하되, 콘텐츠 마일스톤은 해당 자료 없이 진행할 수 없음을 보고한다.

## 2단계: 설계 — 실행 모드: 서브에이전트 → 워크플로 → 사용자 승인

1. **설계 작성(서브에이전트, 한 메시지에서 병렬):**
   - `Agent({ subagent_type: "android-architect", description: "아키텍처·마일스톤 설계", prompt: "docs/plan.md 와 docs/content/sources.md 를 읽고 architecture.md 와 milestones.json 을 작성하라. SRS 알고리즘은 srs-scheduler 1절 기준으로 근거를 들어 고른다. 화면 마일스톤은 ui: true, 콘텐츠 마일스톤은 content: true, 스케줄러처럼 알고리즘이 핵심인 마일스톤은 model: \"opus\"를 붙인다." })`
   - `Agent({ subagent_type: "ui-designer", description: "UI 디자인 명세 작성", prompt: "docs/plan.md 와 docs/content/sources.md 를 읽고 ui-design.md 를 작성하라." })`
2. **ECC 설계 검증(워크플로):** `Workflow({ scriptPath: ".claude/skills/jlpt-app-orchestrator/workflows/design-review.js" })`. 일부 관점만 다시 할 때는 `args: { only: ["a11y", "visual"] }`.
3. 결과 처리:
   - `failedReviewers`가 있으면 승인 전에 그 관점만 `only`로 다시 돌린다. 리뷰가 빠진 설계를 "지적 없음"으로 보고하지 않는다.
   - `failedRevisions`(반영 에이전트 실패)는 해당 설계자를 다시 호출해 반영한다.
   - `postRevisionFailed`가 true면 `only: ["consistency"]`로 정합성만 다시 돌린다.
   - `declined`(설계자가 반영을 거부한 지적), `unapplied`(반영 여부를 보고하지 않은 지적 — 문서를 직접 열어 확인), `uncertain`, `postRevision`(반영 후 새로 생긴 불일치)은 사용자 판단 항목으로 보고한다.
4. **사용자 승인:** 핵심 결정(SRS 알고리즘과 근거, 콘텐츠 DB·학습 기록 DB 분리와 안정 ID, 파이프라인 언어, 일본어 글꼴 번들 여부), 마일스톤 목록, 화면 구성과 토큰·대비 표 요약, 남은 판단 항목을 보여 주고 승인을 받는다. 승인 전에는 3단계로 가지 않는다. 피드백이 오면 해당 설계자를 새로 호출해 반영하고 2번을 다시 실행한다(재개 말고 새로 실행).

## 3단계: 구현 — 실행 모드: 워크플로

1. **환경 사전 확인**(`android-build-verify`의 `references/env-setup.md` 6절 순서): JDK, SDK, `./gradlew`, 파이프라인이 있으면 `python3`. 무엇이든 없으면 env-setup을 보여 주고 사용자가 준비하게 한다. 설치 명령은 사용자 승인 없이 실행하지 않는다.
2. **마일스톤 선택:** `milestones.json`에서 `todo`이고 `dependsOn`이 모두 `done`·`awaiting-device`이거나 같은 묶음 앞쪽에 있는 항목을 의존 순서(위상 순서)로 고른다. 기본은 한 번에 최대 3개다. 앞 마일스톤 결과가 뒤 계획을 바꿀 수 있어서다. 사용자가 "전부"라고 하면 모두 넘긴다.
3. `Workflow({ scriptPath: ".claude/skills/jlpt-app-orchestrator/workflows/implement-milestones.js", args: { milestones: [...], maxFixRounds: 2 } })`
4. **결과 처리:**
   - 반환값에 `error: "ENV"`가 있으면 1번(환경)으로 돌아간다. 다시 실행하지 않는다. 다른 `error`(완료 조건 없음, 알 수 없는 owner)는 메시지대로 architect에게 보완을 맡긴다.
   - `milestones.json`의 `status` 갱신: `done` → `done`, `awaiting-device` → `awaiting-device`, `needs-decision` → `needs-decision`, `failed` → `blocked`, `env-missing`·`skipped` → `todo` 유지(환경을 고치거나 선행 마일스톤이 풀리면 다시 선택된다).
   - `_workspace/03_report_{runId}.md`에 마일스톤별 상태, `missingReviewers`, 남은 지적(`residual`, `uncertain`, `lowFindings`), 구현자가 거부한 지적(`rejected`), 완료 조건 판정, `screenshots`를 적고 사용자에게 보고한다. 리뷰어가 빠진 마일스톤은 통과로 보고하지 않는다.
   - `acceptance`의 `unverified` 항목은 4단계 확인 항목으로 연결해 보고한다.
5. 실패한 마일스톤은 원인(빌드, 확정 지적, 완료 조건 실패, 리뷰어 누락, 에이전트 결과 없음)과 함께 보고하고, 사용자 지시에 따라 같은 마일스톤만 다시 넘기거나 architect에게 재계획을 맡긴다.

## 4단계: 실기기 확인 — 실행 모드: 메인 + 사용자

1. `./gradlew :app:installDebug`로 설치한다(`adb devices`로 기기 확인, env-setup 5절).
2. `awaiting-device` 마일스톤의 `unverified` 완료 조건을 사용자에게 단계별로 안내한다. 기본 확인 항목:
   - 한국어 기기 설정에서 일본어 한자가 일본식 글자 모양인지(예: 直, 骨, 角)
   - 비행기 모드에서 모든 학습 기능이 동작하는지(오프라인 우선)
   - 일본어 TTS 음성이 있는 기기와 없는 기기에서 청해 화면 상태(재생, 설치 안내)
   - TalkBack이 일본어 구간을 일본어로 읽는지
   - 앱 종료 직후 다시 열었을 때 마지막 평가까지 복습 기록이 남았는지
3. 결과를 `_workspace/04_device_results.md`에 항목별(통과/실패, 기기·Android 버전, 메모)로 적는다. 화면 확인이 필요하면 `adb exec-out screencap -p > _workspace/04_device_<항목>.png`로 받는다.
4. 통과한 항목에 해당하는 `awaiting-device` 마일스톤은 `done`으로 바꾼다. 실패는 수정 마일스톤 `{ id: "FIX-<n>", title, owner, dependsOn: [], ui, content, scope, acceptance, status: "todo" }`을 `milestones.json`에 추가하고 3단계로 간다.

## 데이터 전달

| 경로 | 만드는 쪽 | 읽는 쪽 |
|------|-----------|---------|
| `docs/plan.md` | 사용자·메인 | architect, ui-designer, analyst |
| `docs/content/sources.md`, `manifest.json` | analyst | architect, ui-designer, content-engineer, author, QA, 리뷰어 |
| `docs/spec/architecture.md`, `milestones.json` | architect | 구현자, QA, 오케스트레이터(3단계 args) |
| `docs/spec/ui-design.md` | ui-designer | feature-engineer, 디자인 리뷰어 |
| `content/authored/` | author | content-engineer(파이프라인), 콘텐츠 리뷰어 |
| `app/src/main/assets/content/` | content-engineer(파이프라인) | feature-engineer, QA |
| `app/schemas/` | Room(앱 빌드) | content-engineer(파이프라인), QA |
| `screenshots/*.png` | feature-engineer | ECC 디자인 리뷰어 |
| `_workspace/01_*`, `03_*_impl.md`, `03_report_*.md` | analyst, 구현자, 메인 | 다음 단계, 사후 확인 |
| `_workspace/04_device_*` | 메인 + 사용자 | 메인, architect |
| `_workspace/build/verify_*.log` | `verify.sh` | 구현자, QA |
| 워크플로 반환값 | 워크플로 | 메인(보고, `milestones.json` 갱신) |

## 오류 처리

| 상황 | 대응 |
|------|------|
| 워크플로 안의 `agent()`가 `null` | 리뷰어는 스크립트가 한 번 재시도하고, 그래도 없으면 `missingReviewers`/`failedReviewers`로 반환하며 해당 마일스톤은 통과하지 않는다. 검증자가 답하지 못한 지적은 `uncertain`으로 남고 high면 차단한다 |
| 워크플로가 `error`를 반환 | 성공으로 보지 않는다. 메시지대로 원인을 고친 뒤 새로 실행한다 |
| 워크플로 결과가 비어 있거나 이상함 | 실행 기록 디렉터리의 `journal.jsonl`에서 각 에이전트의 실제 반환값을 확인한다 |
| `error: "ENV"`(verify.sh 종료 코드 2) | 3단계 1번으로 돌아가 환경부터 준비한다. 다시 시도하지 않는다 |
| Workflow 도구를 쓸 수 없음 | 같은 단계를 Agent 도구로 순차 실행한다(같은 `subagent_type`, 검증자는 주장·지적마다 1명으로 축소). 축소 사실을 보고서에 적는다 |
| 원 출처 페이지를 열 수 없음(차단, 404) | 해당 주장은 `uncertain` → 미확정으로 남는다. 우회 근거를 만들지 않고 사용자에게 확인 방법을 묻는다 |
| 사용량 한도·인증 만료·권한 거부 | 다시 시도하지 않는다. 산출물을 직접 열어 어디까지 됐는지 확인하고 `_workspace/`에 누락을 기록한 뒤 보고한다(한도면 풀리는 시각 포함) |
| 리뷰 지적을 구현자가 거부 | 스크립트가 거부 근거와 함께 검증자에게 다시 판정시킨다. 그래도 갈리면 `needs-decision`으로 사용자에게 넘긴다. 거부 기록은 지우지 않는다 |
| 콘텐츠 복제 의심이 확정됨 | 해당 항목을 `retired`로 내리고 새로 작성하는 수정 마일스톤을 만든다. 의심 근거는 보고서에 남긴다 |
| 절반 넘는 마일스톤 실패 | 다음 묶음을 진행하지 않고 사용자에게 알린다 |
| 메인이 빈 곳을 채울 때 | 직접 확인한 사실만 쓴다. 에이전트의 판단(예: 어떤 자료를 왜 제외했는지)을 추측해 채우지 않는다 |

## 테스트 시나리오

### 정상 흐름
1. 사용자: "JLPT 앱 개발 시작". 0단계에서 `docs/content/`·`docs/spec/` 없음 확인 → 1·2단계 계획과 예상 에이전트 수를 알리고 동의를 받는다.
2. `license-verify` 실행: 주장 약 80건, 확정 60 / 미확정 18(급수 목록 라이선스 포함) / 반박 2, `mismatches` 0. 사용자에게 한국어 뜻풀이 방식(A+B 추천)과 급수 목록 선택을 받아 `sources.md`에 기록.
3. 2단계: architect·ui-designer 병렬 작성(SRS는 근거와 함께 한 계열 선택) → `design-review`에서 지적 14건 중 확정 6건 반영, `failedReviewers` 없음 → 사용자 승인.
4. 3단계: 환경 확인 통과 → M1(SRS 스케줄러, `model: opus`), M2(콘텐츠 파이프라인 v1), M3(플래시카드 화면) 넘김 → `done` 2, `awaiting-device` 1(TTS·글꼴 확인), `milestones.json` 갱신, `_workspace/03_report_*.md` 생성.
5. 4단계: 사용자와 실기기 확인, `_workspace/04_device_results.md` 기록, 통과한 `awaiting-device`를 `done`으로.

### 오류 흐름
1. `license-verify`에서 커뮤니티 급수 목록의 라이선스 페이지를 열 수 없어 관련 주장이 모두 `uncertain` → 매니페스트 `unconfirmed` → 사용자에게 다른 목록 후보나 직접 확인을 요청. 해당 자료에 의존하는 콘텐츠 마일스톤은 진행하지 않는다.
2. 3단계 M3(ui)에서 `ecc:a11y-architect`가 두 번 모두 결과 없음 → `missingReviewers: ["a11y"]`, 상태 `failed` → `blocked`로 기록하고 사용자에게 보고, 같은 마일스톤을 `reviewOnly`로 다시 넘긴다.
3. M5(content, 모의고사 문항)에서 콘텐츠 리뷰가 "정답 둘" high 지적 → 작성자가 거부 → 검증자가 confirmed 유지 → `needs-decision` → 0단계 분기로 사용자 결정을 받는다. 의존하는 M6은 `skipped`(`todo` 유지).
