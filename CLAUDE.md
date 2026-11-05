# JLPTStudyApp

한국어 화자를 위한 일본어능력시험(JLPT N5~N1) 학습 Android 앱. Kotlin + Jetpack Compose, 오프라인 우선.

## 하네스: JLPT 학습 앱 개발

**목표:** 콘텐츠 출처·라이선스를 원 출처로 확정 → 설계와 ECC 설계·디자인 검증 → 마일스톤 구현·QA·콘텐츠 리뷰 → 실기기 확인 순서로, 복제 콘텐츠 없이 앱을 만든다.

**호출 조건:** 이 앱의 개발 작업(출처·라이선스 확인, 설계, 기능·화면 추가, 콘텐츠 파이프라인, 문법·문제·청해 콘텐츠 작성, 구현, 리뷰, 디자인 검증, 실기기 확인, 앱 산출물의 재실행·수정·보완)을 요청받으면 `jlpt-app-orchestrator` 스킬을 사용한다. `.claude/` 하네스 파일 자체를 바꾸는 요청은 `harness:harness`·`harness:evolve`를 쓴다. 단순 질문에는 직접 답해도 된다.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
| --- | --- | --- | --- |
| 2026-11-05 | 경중별 모델 라우팅: 워크플로의 agentType 없는 추출·검증·리뷰 호출을 sonnet으로 고정(이전에는 세션 모델 opus 상속), android-qa-inspector opus→sonnet | license-verify.js, design-review.js, implement-milestones.js, android-qa-inspector | 사용자 지시. 주간 한도 96% 소진, `/usage`가 workflow-subagent(23%)·android-qa-inspector를 주원인으로 지목 |
| 2026-11-02 | 사용자 결정(2026-11-01) 반영: 콘텐츠 DB는 CC BY-SA 공개, 앱 코드는 분리·별도 라이선스, 한국어 뜻은 JMdict 영어 뜻 번역 허용(파생물 표기). 검수 수준·청해 음성 방식은 미정으로 선택지만 기록(`docs/content/decisions.md`) | content-licensing, content-licensing-analyst, license-verify.js, orchestrator 1단계, jlpt-content-authoring 7절, compose-app-dev 8절, docs/plan.md | 사용자가 라이선스 방향을 정함. 같은 날 license-verify 에이전트 수 추정을 첫 실측(559)으로 고침 |
| 2026-11-02 | Harness v2로 처음 구성(TimemoreDotScaleApp 하네스 구조를 따르되 BLE·프로토콜 부분 제외, 콘텐츠 출처·라이선스 검증 워크플로와 콘텐츠 파이프라인·작성 담당 추가, android-skills 공식 스킬 연결) | 전체 | - |
