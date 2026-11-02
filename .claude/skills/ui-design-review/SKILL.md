---
name: ui-design-review
description: "JLPT 학습 앱의 UI 디자인 기준과 디자인 검증 절차. 플래시카드·복습·문법·모의고사·청해 화면의 가독성·조작성 기준, 일본어 글꼴 로캘과 후리가나, Material 3 토큰, Roborazzi 상태별 스크린샷 만들기, ECC 리뷰어(ecc:a11y-architect, ecc:kotlin-reviewer, ecc:architect)와 ECC 스킬(ecc:accessibility, ecc:make-interfaces-feel-better, ecc:design-system)로 디자인 명세와 스크린샷을 검토하는 방법을 정한다. ui-designer·feature-engineer·디자인 리뷰어가 따르는 기준이며, 디자인 명세 작성, 스크린샷 생성, 디자인 기준 질문에 사용한다. '디자인 검증 다시' 같은 검증 실행 요청 자체는 jlpt-app-orchestrator로 시작한다. 코드 통합 정합성 검사는 android-integration-qa를 쓴다."
---

# UI Design Review — 디자인 기준과 검증

이 앱은 한국어 화자가 짧은 틈에 한 손으로 카드를 넘기며 쓴다. 화면 문구는 한국어, 학습 대상은 일본어다. 일반 앱 디자인 리뷰 기준만으로는 이 맥락의 결함(한국식 한자 글자 모양, 후리가나 겹침, 평가 버튼의 결과를 모르는 채 누르기)을 놓친다. 그래서 ECC의 범용 기준 위에 아래 앱 전용 기준을 얹는다.

## 1. 앱 전용 디자인 기준

| ID | 기준 | 이유 |
|----|------|------|
| D1 | 카드 앞면의 학습 대상(단어·한자)이 화면에서 가장 크다. 한자 카드는 `displayMedium` 이상 | 한자의 획을 구별해 읽어야 한다 |
| D2 | 일본어 텍스트는 일본어 로캘(`ja-JP`)로 그려 일본식 한자 글자 모양이 나온다. 한국어 문장 속 일본어 구간도 같다 | 한자 통합 때문에 한국어 기기에서 한국식 글자 모양(예: 直, 骨, 角)이 나오면 틀린 형태를 외운다 |
| D3 | 후리가나는 본문 글자 위에 본문의 약 50% 크기로, 글꼴 200%와 줄바꿈에서도 본문과 어긋나거나 겹치지 않는다. 켜고 끌 수 있다 | 읽기를 확인하는 핵심 정보다 |
| D4 | 평가 버튼(예: 다시·어려움·알맞음·쉬움)은 최소 48 dp, 엄지가 닿는 하단, 각 버튼에 다음 간격 미리보기(예: "10분", "3일") | 결과를 알고 눌러야 평가가 정확해진다 |
| D5 | 라이트·다크 테마 모두 본문 대비 4.5:1, 큰 글자·UI 요소 3:1 이상 | 지하철·야간 등 다양한 조명에서 쓴다 |
| D6 | 정답·오답 피드백은 색 + 아이콘 + 글자로 표시. 색만으로 구분하지 않는다 | 색각 이상 사용자와 밝은 조명 |
| D7 | 글꼴 크기 200%에서 카드·선택지·해설·버튼 글자가 잘리지 않는다. 긴 가타카나 단어처럼 넘치는 경우의 대응(줄바꿈 또는 `maxLines = 1` + 자동 축소)을 명세에 적는다 | 시스템 글꼴 확대 사용자 |
| D8 | TalkBack: 일본어 구간은 일본어로 낭독되게 로캘을 건다. 카드 뒤집기·정답/오답은 `Polite` 알림, 평가 버튼은 `onClickLabel`에 다음 간격 포함, 토글(후리가나 등)은 `stateDescription` | 화면을 보지 않는 사용자도 학습할 수 있어야 한다 |
| D9 | 모든 화면 상태에 문구와 다음 행동 버튼: 콘텐츠 로딩, 오늘 복습 완료, 새 카드 한도 도달, 급수 미선택(콘텐츠 없음), DB 열기 실패, TTS 준비 중·일본어 음성 데이터 없음·엔진 없음, 모의고사 진행 중 이탈·재개, 결과 | 정의 안 된 상태는 빈 화면이 된다 |
| D10 | 세션 진행(남은 카드·문항 수)이 항상 보인다. 평가는 즉시 저장되므로 중간 이탈에 확인 대화상자를 띄우지 않는다(모의고사는 예외: 시간 기록 때문에 확인) | 짧은 틈에 쓰는 앱이다 |
| D11 | 저작자 표시·고지 화면은 설정에서 두 번 이내 이동으로 닿고, "JLPT 공식 아님", 한국어 뜻 생성 방식, TTS 음성 안내를 포함한다. 검수 전 뜻의 표시는 `content-licensing` 4절 결정을 따른다 | 라이선스 의무와 사용자 신뢰 |

## 2. 디자인 명세(`docs/spec/ui-design.md`)에 들어갈 것

- 화면 목록과 이동 흐름(홈·오늘의 복습, 플래시카드, 단어·한자 목록·상세, 문법 목록·상세, 문제 풀이·모의고사·결과, 청해, 설정, 저작자 표시)
- 화면 × 상태 표(D9 상태 전부), 상태마다 문구와 행동
- 토큰: `ColorScheme` 역할별 라이트·다크 값(대비 계산 결과와 함께), `Typography` 스타일(크기 sp, 굵기, 일본어용·한국어용 구분과 로캘), 간격 단위(dp), 모서리, 아이콘 세트
- 컴포넌트: 플래시카드(앞·뒤), 후리가나 텍스트, 평가 버튼 줄, 선택지 버튼, 정답/오답 피드백, 진행 표시, TTS 재생 컨트롤, 급수 선택
- 한·일 혼용 표시 규칙: 어느 텍스트가 일본어인지(로캘을 걸 곳)
- 접근성 요구사항: 화면별 TalkBack 문구, 알림 시점, 글꼴 확대 시 배치

## 3. 스크린샷 만들기(구현 단계)

디자인 리뷰어는 코드가 아니라 스크린샷을 보고 판단한다. 스크린샷이 없으면 디자인 검증을 할 수 없으므로 마일스톤이 실패 처리된다.

1. Robolectric + Roborazzi로 JVM 스크린샷 테스트를 쓴다(Roborazzi 설정 방법은 현재 공식 README로 확인한다). Robolectric SDK는 `robolectric.properties`의 값을 쓴다.
2. 화면 × 상태 × {라이트, 다크}마다 한 장. 모든 화면에서 가장 긴 문구가 나오는 상태에 글꼴 2.0배 한 장을 더 만든다(D7).
3. 결정적으로 만든다: 테스트용 작은 콘텐츠 픽스처, 고정 시계, 애니메이션 끔. 실행마다 달라지면 리뷰와 회귀 비교가 무의미해진다.
4. 일본어 글자 확인: 첫 화면 마일스톤에서 스크린샷의 일본어가 네모(글리프 없음)로 나오지 않는지, 한자가 일본식 글자 모양인지 직접 열어 확인한다. 테스트 환경에 일본어 글꼴이 없어 네모로 나오면 그 사실을 보고하고, 글꼴 번들 여부를 설계 변경으로 올린다. 네모 스크린샷으로 디자인 검증을 통과시키지 않는다.
5. 파일 이름: `screenshots/{screen}_{state}_{light|dark}[_font200].png` (프로젝트 루트 기준). 예: `screenshots/review_card_back_dark.png`
6. `verify.sh recordRoborazziDebug`로 기록하고, 이후 변경에서는 `verify.sh verifyRoborazziDebug`로 의도하지 않은 변화를 잡는다.
7. 구현 결과의 `screenshots` 필드에 만든 파일 경로를 모두 적는다.

## 4. 디자인 검증 관점과 ECC 연결

| 관점 | 실행 주체 | 불러올 ECC 스킬 | 입력 | 보는 것 |
|------|-----------|-----------------|------|---------|
| 접근성 | `ecc:a11y-architect` | `ecc:accessibility` | 명세 또는 스크린샷 + Compose 코드 | D4·D5·D6·D7·D8, semantics(`contentDescription`, `stateDescription`, `liveRegion`, `role`, `onClickLabel`), 터치 영역 |
| 시각 디테일 | 기본 워크플로 에이전트 | `ecc:make-interfaces-feel-better`, `ecc:design-system` | 명세 또는 스크린샷 | D1·D2·D3·D9·D10·D11, 간격·위계·정렬·토큰 일관성 |
| Compose 코드 | `ecc:kotlin-reviewer` | — | 변경 코드 | 재구성 범위, 상태 끌어올리기, 수명주기 수집, 일본어 로캘 적용 위치 |
| 아키텍처 | `ecc:architect` | `ecc:android-clean-architecture`, `ecc:kotlin-coroutines-flows` | `architecture.md` | 계층 의존, 계약, 수명주기 |

- ECC 스킬은 Skill 도구로 불러오고, 도구가 없으면 `~/.claude/plugins/cache/ecc/ecc/*/skills/<이름>/SKILL.md`를 Glob으로 찾아 Read로 읽는다.
- ECC 스킬은 웹(CSS, ARIA) 예시가 많다. 원리만 가져와 Compose 용어로 바꿔 판단한다(ARIA `lang` → `localeList`, ARIA live region → `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`, CSS 토큰 → `MaterialTheme` 역할). 이 스킬의 기준과 충돌하면 이 스킬이 우선한다.
- Android 공식 스킬(android-skills 플러그인)은 아래 판단의 근거로 쓴다: 인셋·시스템 바는 `edge-to-edge`, 태블릿·가로 배치는 `adaptive`, 긴 목록·카드 넘김의 재구성 비용은 `jetpack-compose-performance`. 불러오는 법: Skill 도구로 `android-skills:<이름>`을 부른다. 도구가 없거나 이름을 못 찾으면 `~/.claude/plugins/cache/android-skills/android-skills/*/` 아래에서 Glob `**/<이름>/SKILL.md`로 찾아 Read로 읽는다(플러그인 판 번호가 바뀌어도 찾히게 `*`를 쓴다). 둘 다 안 되면 그 사실을 보고하고 이 스킬만으로 진행한다. 이 스킬의 기준 D1~D11과 충돌하면 이 스킬이 우선한다.
- 리뷰어는 파일을 수정하지 않는다. 지적만 반환한다. Write 권한이 있는 ECC 에이전트(`ecc:a11y-architect`)에도 프롬프트로 읽기 전용을 지시한다.
- 지적 형식: 제목, 심각도(`high` = 기준 D1~D11 위반이나 WCAG AA 위반, `medium` = 특정 상태·테마에서만 위반, `low` = 다듬기), 위치(스크린샷 파일 또는 파일:줄, 명세 절), 근거(무엇이 보이는지), 고칠 방법.
- 스크린샷을 근거로 할 때는 `Read`로 PNG를 직접 열어 본다. 파일 이름만 보고 판단하지 않는다.
- 한 관점 안에서는 같은 원인을 한 건으로 묶는다. 관점 사이 중복은 검증 단계에서 각각 판정되므로 신경 쓰지 않는다.

## 5. 대비 계산

색 대비는 눈으로 판단하지 않는다. 토큰의 hex 값으로 계산하고 결과 수치를 근거에 적는다(명세 단계와 스크린샷 단계 모두).

```bash
python3 -I .claude/skills/ui-design-review/scripts/contrast.py 1C1B1F:FFFBFE E6E1E5:1C1B1F   # 전경:배경 쌍
```

`ui-design.md`에는 라이트·다크 각각 주요 전경·배경 쌍의 대비 표를 이 스크립트 출력으로 남긴다. Bash가 없는 리뷰어(`ecc:a11y-architect`)는 이 표를 근거로 판단하고, 검증 단계가 다시 계산한다.
