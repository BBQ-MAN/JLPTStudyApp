# UI 디자인 명세

작성: 2026-11-02 · 작성자: ui-designer · 상태: 초안(설계 검증 지적 반영, 사용자 승인 전)

- 입력: `docs/plan.md`, `docs/spec/architecture.md`, `docs/content/sources.md`(2026-11-02 license-verify 1차 반영본), `docs/content/decisions.md`(D-01·D-02 결정, D-03·D-04·D-06 미정), `docs/content/manifest.json`
- 기준: `.claude/skills/ui-design-review/SKILL.md` D1~D11. 이 문서의 모든 값은 그 기준을 만족하도록 정했다. 충돌하면 그 스킬이 우선한다.
- 이 문서는 코드가 아니다. 값과 이름은 Compose·Material 3에 그대로 옮길 수 있게 적었다.
- `docs/spec/architecture.md`와 대조했다(architecture.md 11.1 정본 기준). **화면 표시 규칙(문구, 반올림처럼 사용자에게 보이는 값, 레이아웃)·화면 이름·탭 라벨·화면 상태의 종류·스크린샷 이름은 이 문서**, **타입·상태 이름·필드·데이터 형식(저장 스키마, `attribution.json`, 콘텐츠 형식, 누가 조립하는가)은 architecture.md**. 13절은 그 이름을 그대로 옮긴 대조표다. 서로 다르면 두 문서를 함께 고친다.
- 사용자 승인 항목: 이 문서에만 있는 항목은 `UI-Uxx`, architecture.md와 겹치는 결정은 architecture.md의 `AR-Uxx` 하나로만 부른다(14절).

## 0. 기준 대응표

| 기준 | 이 문서의 위치 |
|------|----------------|
| D1 학습 대상이 가장 크다 | 3.2 `jpKanjiHero`·`jpHeadword`, 6.3 |
| D2 일본어 로캘 | 4절 전체, 3.2 |
| D3 후리가나 | 3.2.2 ruby, 5.1, 5.2 |
| D4 평가 버튼 | 5.3 |
| D5 대비 | 3.1.4, 3.1.5(포커스 링) |
| D6 피드백 색+아이콘+글자 | 5.5 |
| D7 글꼴 200% | 5.2·5.3·6절 각 화면, 11.2 |
| D8 TalkBack | 4.3, 11.1 |
| D9 모든 상태 | 7절 화면 × 상태 표 |
| D10 진행 표시·이탈 | 5.6, 6.3, 6.10 |
| D11 저작자 표시·고지 | 9절 |

## 1. 화면 목록과 이동

### 1.1 화면 목록

| ID | 화면 | 들어오는 길 | 순위(plan.md) |
|----|------|-------------|---------------|
| S01 | 급수 선택(첫 실행) | `user_settings.onboarding_done = 0`일 때 앱 시작 화면(라우트 `LevelSelect`). "시작"을 누르면 1로 저장 | MVP |
| S02 | 홈(오늘의 학습) | 내비게이션 "홈" | MVP |
| S03 | 복습 세션(플래시카드) | S02 "학습 시작" | MVP |
| S04 | 세션 완료 | S03 대기열이 끝났을 때 | MVP |
| S05 | 사전: 단어·한자 목록(탭 두 개) | 내비게이션 "사전" | MVP |
| S06 | 단어 상세 | S05, S07의 "이 한자가 쓰인 단어", S04·S12 결과의 단어 링크 | MVP |
| S07 | 한자 상세 | S05, S06의 "쓰인 한자" | MVP |
| S08 | 설정 | 내비게이션 "설정" | MVP |
| S09 | 출처와 라이선스 | S08 "출처와 라이선스"(한 번 이동), S02 상단 메뉴 "정보"(두 번 이동), 7절 오류 화면의 "출처 보기" | MVP |
| S10 | 문법 목록 | 내비게이션 "문법" | 2순위 |
| S11 | 문법 상세 | S10 | 2순위 |
| S12 | 연습 홈(유형별 연습·모의고사·청해) | 내비게이션 "연습" | 3·4순위 |
| S13 | 문제 풀이(연습) | S12 유형 선택 | 3순위 |
| S14 | 모의고사 진행 | S12 "모의고사" | 3순위 |
| S15 | 결과·해설 | S13·S14 종료 | 3순위 |
| S16 | 청해 문제 | S12 "청해" | 4순위 |
| S90 | 콘텐츠 열기 실패(전체 화면) | 콘텐츠 DB·학습 기록 DB를 열지 못했을 때 어느 화면에서든 | MVP |

### 1.2 내비게이션 구조

- `NavigationSuiteScaffold`(이미 의존성에 있는 `material3-adaptive-navigation-suite`)를 쓴다. 폭이 Compact면 하단 바, Medium 이상이면 내비게이션 레일.
- 목적지: 홈 · 사전 · 문법 · 연습 · 설정. **구현되지 않은 순위의 목적지는 숨긴다**(비활성으로 두지 않는다). MVP에서는 홈 · 사전 · 설정 세 개. 라벨은 이 이름 그대로 쓴다. S09로 가는 설정 행 이름은 "출처와 라이선스"다("정보·출처"가 아니다).
- 아이콘과 라벨을 항상 함께 보인다(`alwaysShowLabel = true`). 선택된 항목은 채운 아이콘.
- 항목 색·굵기(바·레일 같은 값, `NavigationSuiteDefaults.itemColors(navigationBarItemColors = NavigationBarItemDefaults.colors(…), navigationRailItemColors = NavigationRailItemDefaults.colors(…))`). 선택은 색만이 아니라 **표시자 + 채운 아이콘 + 굵은 라벨** 세 가지로 구분한다. 대비는 3.1.4 "내비게이션" 행.

| 요소 | 토큰(인자) | 라이트 | 다크 | 굵기·모양 |
|------|-----------|--------|------|-----------|
| 바 배경 | `surfaceContainer`(M3 기본) | `#EFEDE8` | `#1F1F23` | |
| 레일 배경 | `surface`(M3 기본) | `#FBF9F4` | `#131316` | |
| 선택 표시자 | `indicatorColor = primary` | `#2F4A7A` | `#ADC6FF` | M3 기본 알약 |
| 선택 아이콘 | `selectedIconColor = onPrimary` | `#FFFFFF` | `#102F5E` | 채움 1(`ic_ms_<이름>_fill`) |
| 선택 라벨 | `selectedTextColor = onSurface` | `#1B1C1F` | `#E4E2E6` | labelSmall, **700**(`FontWeight.Bold`) |
| 비선택 아이콘 | `unselectedIconColor = onSurfaceVariant`(M3 기본) | `#46474F` | `#C6C6CF` | 채움 0 |
| 비선택 라벨 | `unselectedTextColor = onSurfaceVariant`(M3 기본) | `#46474F` | `#C6C6CF` | labelSmall, 500 |

  - 라벨 굵기는 색 인자로 못 바꾸므로 `item(label = { Text(라벨, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) })`로 준다. 다크에서 선택·비선택 라벨 색 차가 1.32:1(라이트 1.85:1)뿐이라 굵기가 라벨 쪽 구분을 맡는다.
  - `secondaryContainer`·`secondary`를 표시자·라벨에 쓰지 않는다: 표시자 `secondaryContainer`는 바 대비 1.10:1(라이트)·1.76:1(다크)로 3:1 미달이고, 라벨 `secondary`(`#5A5F71`)는 라이트에서 비선택 라벨(`#46474F`)보다 옅어 선택이 약해 보인다.
- 라벨은 두 글자 이하로 정한다(글꼴 200%에서 labelSmall 22 sp 두 글자 ≈ 44 dp라 항목 폭 안에 든다). 라벨에 `maxLines`·말줄임을 걸지 않는다(줄바꿈 허용). 글꼴 200% 스크린샷 `nav_shell_home_light_font200`으로 확인한다(12절).
- S03·S13·S14·S16(세션 화면)은 내비게이션 바를 숨긴 전체 화면이다. 엄지 영역을 평가 버튼·선택지에 준다.
- 이동 흐름

```
첫 실행(onboarding_done = 0) ─▶ S01 ─(시작: onboarding_done = 1)─▶ S02
어느 화면이든 DB 열기 실패 ─▶ S90 ─(출처 보기)─▶ S09
S02 ─(학습 시작)─▶ S03 ─(대기열 끝)─▶ S04 ─(홈으로)─▶ S02
S03 ─(시스템 뒤로)─▶ S02          // 확인 대화상자 없음(D10)
S02 상단 메뉴 ─▶ 정보 ─▶ S09
S08 ─▶ S09                          // D11: 설정에서 한 번
S05 ─▶ S06 ⇄ S07
S12 ─▶ S13 ─▶ S15 / S13 ─(이탈: 확인 없음)─▶ S12
S12 ─▶ S14 ─(이탈: 확인)─▶ S12(재개 카드 표시)
```

## 2. 학습 흐름(복습 세션)

1. S02가 오늘 대기열 요약을 보인다: 복습 기한 카드 수, 오늘 남은 새 카드 수(한도 − 오늘 학습한 새 카드). 둘 다 앱 계산 값(`:core:srs`의 학습일 경계 기준).
2. "학습 시작"을 누르면 S03. 대기열 순서는 `:core:srs`가 정한 대로이며 화면은 바꾸지 않는다.
3. 카드 앞면 → "답 보기"(또는 카드 아무 곳 탭) → 뒷면. 뒷면이 나타나면 같은 자리(하단)에 평가 버튼 줄이 나타난다.
4. 평가를 누르면 즉시 저장(카드 상태 + 복습 로그, 한 트랜잭션)하고 다음 카드로 간다. 저장이 끝나기 전에는 다음 카드로 넘어가지 않는다. 저장소(`StudyRepository.applyReview`)는 실패하면 예외를 던지고, ViewModel이 잡아 카드·미리보기를 그대로 두고 `saveFailed = 누른 평가`로 바꾼다(7절 "저장 실패", 13.1).
5. 상단 "되돌리기"로 마지막 평가를 취소하고 그 카드로 돌아간다(세션 안에서 여러 번 가능, 세션 시작 전 평가는 되돌리지 않는다).
6. 언제든 시스템 뒤로/상단 닫기로 나간다. 확인하지 않는다. 다시 "학습 시작"하면 남은 대기열부터.
7. 대기열이 끝나면 S04.

### 2.1 카드 종류

| 카드 종류 | 앞면 | 뒷면 | 기본값 |
|-----------|------|------|--------|
| 단어 인식(일→한, `vr`) | 표기(한자가 있으면 한자 표기, 없으면 가나) | 표기 + 후리가나, 읽기, 품사, 한국어 뜻, 뜻별 출처·검수 칩 | 켬 |
| 한자(`kr`) | 한자 한 글자 | 한국어 음, 음독, 훈독, 획수, 이 한자가 쓰인 단어 최대 3개(한국어 뜻 포함) | 켬 |
| 단어 회상(한→일, `vp`) | 한국어 뜻(첫 뜻 하나) | 표기 + 후리가나, 읽기, 한국어 뜻 전체, 출처·검수 칩 | 끔(UI-U02) |
| 문법(`gr`, 2순위, M17) | 6.9.1 | 6.9.1 | 켬(문법 콘텐츠가 번들에 있을 때만 설정에 보임) |

- 같은 단어의 다른 방향 카드는 별개 카드다(`compose-app-dev` 7절).
- 한자 카드에는 뜻 행이 없다(architecture.md 4.3, AR-U04 A). KANJIDIC2 영어 뜻은 번들하지 않고, 한국어 뜻·훈(예: "먹을")은 출처에 없다. 대신 쓰인 단어의 한국어 뜻으로 뜻을 짐작하게 한다.

## 3. 디자인 토큰

### 3.1 색

동적 색(Material You)은 쓰지 않는다. 기기 배경화면에서 뽑은 색은 아래 대비 계산을 보장하지 못한다(UI-U03).

#### 3.1.1 `lightColorScheme`

| 역할 | 값 | 쓰는 곳 |
|------|-----|---------|
| primary | `#2F4A7A` | 주 버튼, 링크, 진행 표시, "알맞음" 버튼, 내비게이션 선택 표시자(1.2) |
| onPrimary | `#FFFFFF` | 내비게이션 선택 아이콘 |
| primaryContainer | `#D8E2FF` | 선택된 급수 칩, 강조 카드 |
| onPrimaryContainer | `#0F1E3D` | |
| inversePrimary | `#ADC6FF` | 스낵바 동작 버튼 |
| secondary | `#5A5F71` | 보조 아이콘 |
| onSecondary | `#FFFFFF` | |
| secondaryContainer | `#DEE2F5` | "어려움" 버튼, 진행 표시 트랙(내비게이션 선택 표시자에는 쓰지 않는다, 1.2) |
| onSecondaryContainer | `#171B2C` | |
| tertiary | `#1E6B60` | |
| onTertiary | `#FFFFFF` | |
| tertiaryContainer | `#C3EDE3` | "쉬움" 버튼 |
| onTertiaryContainer | `#00201B` | |
| error | `#BA1A1A` | 오답 아이콘·글자, 오류 문구 |
| onError | `#FFFFFF` | |
| errorContainer | `#FFDAD6` | "다시" 버튼, 오답 선택지 배경 |
| onErrorContainer | `#410002` | |
| background / surface / surfaceBright | `#FBF9F4` | 화면 배경 |
| onBackground / onSurface | `#1B1C1F` | 본문, 일본어 학습 대상, 내비게이션 선택 라벨 |
| surfaceVariant | `#E3E1DC` | |
| onSurfaceVariant | `#46474F` | 후리가나, 보조 문구, 출처·검수 칩 글자, 내비게이션 비선택 아이콘·라벨 |
| surfaceContainerLowest | `#FFFFFF` | |
| surfaceContainerLow | `#F3F1EC` | 플래시카드 면, 목록 카드 |
| surfaceContainer | `#EFEDE8` | 내비게이션 바(레일은 surface) |
| surfaceContainerHigh | `#E9E7E2` | 대화상자, 하단 시트 |
| surfaceContainerHighest | `#E3E1DC` | 출처·검수 칩 배경, 입력란 |
| surfaceDim | `#DBD9D4` | |
| surfaceTint | `#2F4A7A` | |
| inverseSurface | `#303034` | 스낵바 |
| inverseOnSurface | `#F2F0F4` | |
| outline | `#76777F` | 선택지 버튼 테두리, 입력란 테두리(UI 경계 전용, 글자에 쓰지 않음) |
| outlineVariant | `#C6C6CF` | 구분선(장식 전용, 의미 있는 경계에 쓰지 않음) |
| scrim | `#000000` | |

#### 3.1.2 `darkColorScheme`

| 역할 | 값 |
|------|-----|
| primary | `#ADC6FF` |
| onPrimary | `#102F5E` |
| primaryContainer | `#2C4675` |
| onPrimaryContainer | `#D8E2FF` |
| inversePrimary | `#2F4A7A` |
| secondary | `#C2C6D9` |
| onSecondary | `#2C3042` |
| secondaryContainer | `#424659` |
| onSecondaryContainer | `#DEE2F5` |
| tertiary | `#8AD3C4` |
| onTertiary | `#003731` |
| tertiaryContainer | `#005046` |
| onTertiaryContainer | `#C3EDE3` |
| error | `#FFB4AB` |
| onError | `#690005` |
| errorContainer | `#93000A` |
| onErrorContainer | `#FFDAD6` |
| background / surface / surfaceDim | `#131316` |
| onBackground / onSurface | `#E4E2E6` |
| surfaceVariant | `#353438` |
| onSurfaceVariant | `#C6C6CF` |
| surfaceContainerLowest | `#0E0E11` |
| surfaceContainerLow | `#1B1B1F` |
| surfaceContainer | `#1F1F23` |
| surfaceContainerHigh | `#2A2A2D` |
| surfaceContainerHighest | `#353438` |
| surfaceBright | `#39393C` |
| surfaceTint | `#ADC6FF` |
| inverseSurface | `#E4E2E6` |
| inverseOnSurface | `#303034` |
| outline | `#90909A` |
| outlineVariant | `#46474F` |
| scrim | `#000000` |

#### 3.1.3 확장 색(`ColorScheme`에 없는 역할)

정답 표시용 녹색은 M3 역할에 없으므로 `CompositionLocal`(`LocalExtendedColors`)로 둔다.

| 이름 | 라이트 | 다크 | 쓰는 곳 |
|------|--------|------|---------|
| correct | `#1B6B3A` | `#8AD6A0` | 정답 아이콘·글자 |
| correctContainer | `#D3F0DC` | `#0F5129` | 정답 선택지 배경 |
| onCorrectContainer | `#0B2E17` | `#D3F0DC` | 정답 선택지 글자 |

#### 3.1.4 대비 표(`python3 -I .claude/skills/ui-design-review/scripts/contrast.py` 출력, 2026-11-02)

라이트

| 쌍(전경 / 배경) | 출력 | 판정 |
|-----------------|------|------|
| onSurface / surface | `1B1C1F on FBF9F4: 16.19:1` | 본문 통과 |
| onSurface / surfaceContainerLow(카드) | `1B1C1F on F3F1EC: 15.09:1` | 본문 통과 |
| onSurface / surfaceContainerHighest | `1B1C1F on E3E1DC: 13.04:1` | 본문 통과 |
| onSurfaceVariant / surface | `46474F on FBF9F4: 8.77:1` | 본문 통과 |
| onSurfaceVariant / surfaceContainerLow(카드 위 후리가나) | `46474F on F3F1EC: 8.18:1` | 본문 통과 |
| onSurfaceVariant / surfaceContainerHighest(칩) | `46474F on E3E1DC: 7.06:1` | 본문 통과 |
| secondary / surface | `5A5F71 on FBF9F4: 6.03:1` | 본문 통과 |
| primary / surface(링크) | `2F4A7A on FBF9F4: 8.38:1` | 본문 통과 |
| primary / surfaceContainerLow(카드 안 링크) | `2F4A7A on F3F1EC: 7.81:1` | 본문 통과 |
| onPrimary / primary("알맞음") | `FFFFFF on 2F4A7A: 8.82:1` | 본문 통과 |
| onPrimaryContainer / primaryContainer | `0F1E3D on D8E2FF: 12.76:1` | 본문 통과 |
| onSecondaryContainer / secondaryContainer("어려움") | `171B2C on DEE2F5: 13.26:1` | 본문 통과 |
| onTertiaryContainer / tertiaryContainer("쉬움") | `00201B on C3EDE3: 13.53:1` | 본문 통과 |
| onErrorContainer / errorContainer("다시", 오답 선택지) | `410002 on FFDAD6: 13.26:1` | 본문 통과 |
| error / surface(오류 문구) | `BA1A1A on FBF9F4: 6.14:1` | 본문 통과 |
| error / errorContainer(오답 아이콘) | `BA1A1A on FFDAD6: 5.00:1` | 본문 통과 |
| correct / surface | `1B6B3A on FBF9F4: 6.22:1` | 본문 통과 |
| correct / correctContainer(정답 아이콘) | `1B6B3A on D3F0DC: 5.38:1` | 본문 통과 |
| onCorrectContainer / correctContainer | `0B2E17 on D3F0DC: 12.18:1` | 본문 통과 |
| inverseOnSurface / inverseSurface(스낵바) | `F2F0F4 on 303034: 11.61:1` | 본문 통과 |
| inversePrimary / inverseSurface(스낵바 동작) | `ADC6FF on 303034: 7.70:1` | 본문 통과 |
| outline / surface(선택지 테두리) | `76777F on FBF9F4: 4.23:1` | UI 3:1 통과, 본문 실패 → 글자에 쓰지 않음 |
| outline / surfaceContainerLow | `76777F on F3F1EC: 3.95:1` | UI 3:1 통과, 본문 실패 → 글자에 쓰지 않음 |
| primary / secondaryContainer(진행 표시 / 트랙) | `2F4A7A on DEE2F5: 6.85:1` | UI 통과 |
| outlineVariant / surface(구분선) | `C6C6CF on FBF9F4: 1.61:1` | 실패 → 장식 전용, 경계·글자에 쓰지 않음 |
| 내비게이션: primary / surfaceContainer(선택 표시자 / 바) | `2F4A7A on EFEDE8: 7.54:1` | UI 3:1 통과 |
| 내비게이션: primary / surface(선택 표시자 / 레일) | `2F4A7A on FBF9F4: 8.38:1` | UI 3:1 통과 |
| 내비게이션: onPrimary / primary(선택 아이콘 / 표시자) | `FFFFFF on 2F4A7A: 8.82:1` | UI 3:1 통과 |
| 내비게이션: onSurface / surfaceContainer(선택 라벨 / 바) | `1B1C1F on EFEDE8: 14.56:1` | 본문 통과 |
| 내비게이션: onSurface / surface(선택 라벨 / 레일) | `1B1C1F on FBF9F4: 16.19:1` | 본문 통과 |
| 내비게이션: onSurfaceVariant / surfaceContainer(비선택 아이콘·라벨 / 바) | `46474F on EFEDE8: 7.89:1` | 본문 통과 |
| 내비게이션: onSurfaceVariant / surface(비선택 아이콘·라벨 / 레일) | `46474F on FBF9F4: 8.77:1` | 본문 통과 |
| 참고: onSurface / onSurfaceVariant(선택 라벨 색 / 비선택 라벨 색) | `1B1C1F on 46474F: 1.85:1` | 색 차만으로 부족 → 굵기 700/500으로 구분(1.2) |
| 쓰지 않음: secondaryContainer / surfaceContainer(옛 표시자) | `DEE2F5 on EFEDE8: 1.10:1` | 실패 → 표시자에 쓰지 않음 |

다크

| 쌍(전경 / 배경) | 출력 | 판정 |
|-----------------|------|------|
| onSurface / surface | `E4E2E6 on 131316: 14.41:1` | 본문 통과 |
| onSurface / surfaceContainerLow(카드) | `E4E2E6 on 1B1B1F: 13.34:1` | 본문 통과 |
| onSurface / surfaceContainerHighest | `E4E2E6 on 353438: 9.60:1` | 본문 통과 |
| onSurfaceVariant / surface | `C6C6CF on 131316: 10.93:1` | 본문 통과 |
| onSurfaceVariant / surfaceContainerLow | `C6C6CF on 1B1B1F: 10.12:1` | 본문 통과 |
| onSurfaceVariant / surfaceContainerHighest(칩) | `C6C6CF on 353438: 7.28:1` | 본문 통과 |
| secondary / surface | `C2C6D9 on 131316: 10.93:1` | 본문 통과 |
| primary / surface | `ADC6FF on 131316: 10.86:1` | 본문 통과 |
| primary / surfaceContainerLow | `ADC6FF on 1B1B1F: 10.05:1` | 본문 통과 |
| onPrimary / primary | `102F5E on ADC6FF: 7.73:1` | 본문 통과 |
| onPrimaryContainer / primaryContainer | `D8E2FF on 2C4675: 7.25:1` | 본문 통과 |
| onSecondaryContainer / secondaryContainer | `DEE2F5 on 424659: 7.24:1` | 본문 통과 |
| onTertiaryContainer / tertiaryContainer | `C3EDE3 on 005046: 7.40:1` | 본문 통과 |
| onErrorContainer / errorContainer | `FFDAD6 on 93000A: 7.24:1` | 본문 통과 |
| error / surface | `FFB4AB on 131316: 10.92:1` | 본문 통과 |
| error / errorContainer | `FFB4AB on 93000A: 5.51:1` | 본문 통과 |
| correct / surface | `8AD6A0 on 131316: 10.78:1` | 본문 통과 |
| correct / correctContainer | `8AD6A0 on 0F5129: 5.48:1` | 본문 통과 |
| onCorrectContainer / correctContainer | `D3F0DC on 0F5129: 7.75:1` | 본문 통과 |
| inverseOnSurface / inverseSurface | `303034 on E4E2E6: 10.21:1` | 본문 통과 |
| inversePrimary / inverseSurface | `2F4A7A on E4E2E6: 6.85:1` | 본문 통과 |
| outline / surface | `90909A on 131316: 5.87:1` | 통과 |
| outline / surfaceContainerLow | `90909A on 1B1B1F: 5.43:1` | 통과 |
| primary / secondaryContainer(진행 표시 / 트랙) | `ADC6FF on 424659: 5.46:1` | UI 통과 |
| outlineVariant / surface(구분선) | `46474F on 131316: 2.01:1` | 실패 → 장식 전용 |
| 내비게이션: primary / surfaceContainer(선택 표시자 / 바) | `ADC6FF on 1F1F23: 9.62:1` | UI 3:1 통과 |
| 내비게이션: primary / surface(선택 표시자 / 레일) | `ADC6FF on 131316: 10.86:1` | UI 3:1 통과 |
| 내비게이션: onPrimary / primary(선택 아이콘 / 표시자) | `102F5E on ADC6FF: 7.73:1` | UI 3:1 통과 |
| 내비게이션: onSurface / surfaceContainer(선택 라벨 / 바) | `E4E2E6 on 1F1F23: 12.76:1` | 본문 통과 |
| 내비게이션: onSurface / surface(선택 라벨 / 레일) | `E4E2E6 on 131316: 14.41:1` | 본문 통과 |
| 내비게이션: onSurfaceVariant / surfaceContainer(비선택 아이콘·라벨 / 바) | `C6C6CF on 1F1F23: 9.68:1` | 본문 통과 |
| 내비게이션: onSurfaceVariant / surface(비선택 아이콘·라벨 / 레일) | `C6C6CF on 131316: 10.93:1` | 본문 통과 |
| 참고: onSurface / onSurfaceVariant(선택 라벨 색 / 비선택 라벨 색) | `E4E2E6 on C6C6CF: 1.32:1` | 색 차만으로 부족 → 굵기 700/500으로 구분(1.2) |
| 쓰지 않음: secondaryContainer / surfaceContainer(옛 표시자) | `424659 on 1F1F23: 1.76:1` | 실패 → 표시자에 쓰지 않음 |

- 비활성 컨트롤은 M3 기본(`onSurface` 38%)을 쓴다. WCAG 1.4.3은 비활성 요소를 예외로 둔다. 비활성 상태만으로 이유를 전하지 않고 이유 문구를 함께 둔다(예: 급수 칩 "콘텐츠 준비 중").
- 글자가 있는 채운 버튼은 버튼 배경과 화면 배경의 대비를 요구하지 않는다(글자가 컨트롤을 식별한다). 테두리로만 구별되는 선택지 버튼은 `outline`(3:1 이상)을 쓴다.

#### 3.1.5 포커스 표시(외장 키보드·D-pad·스위치 접근, WCAG 2.4.7·1.4.11)

- 모든 클릭 가능한 요소(직접 만든 카드·선택지·칩·번호 이동 줄 포함)는 `focusable`과 기본 `indication`을 유지한다. `indication = null`로 지우지 않는다.
- 그 위에 포커스 링을 그린다: 두께 **3 dp**, 색 `primary`, 요소 바깥쪽 **2 dp 띄워서**(요소 모양을 따라 모서리 반경 + 2 dp). `Modifier.onFocusChanged`로 `isFocused`일 때만. 링은 요소 바깥에 그리므로 맞닿는 색은 화면 배경·카드 면이다.
- 선택지 버튼의 "선택됨"(5.4: 안쪽 2 dp `primary` 테두리 + 채운 번호 원 + check 아이콘)과 구분된다: 포커스 링은 바깥에 띄워 그리고 더 두껍다. 선택되고 포커스된 버튼은 둘 다 보인다.
- M3 기본 포커스 상태 층(onSurface 약 10%)만으로는 1.22:1 안팎이라 기준에 못 미친다. 링을 반드시 함께 그린다.

| 쌍(링 / 맞닿는 면) | 출력 | 판정 |
|--------------------|------|------|
| primary / surface(라이트) | `2F4A7A on FBF9F4: 8.38:1` | UI 3:1 통과 |
| primary / surfaceContainerLow(라이트 카드) | `2F4A7A on F3F1EC: 7.81:1` | UI 3:1 통과 |
| primary / primaryContainer(라이트 선택된 선택지 면) | `2F4A7A on D8E2FF: 6.82:1` | UI 3:1 통과 |
| primary / surface(다크) | `ADC6FF on 131316: 10.86:1` | UI 3:1 통과 |
| primary / surfaceContainerLow(다크 카드) | `ADC6FF on 1B1B1F: 10.05:1` | UI 3:1 통과 |
| primary / primaryContainer(다크 선택된 선택지 면) | `ADC6FF on 2C4675: 5.49:1` | UI 3:1 통과 |

### 3.2 타이포그래피

- 글꼴: **기기 글꼴 + 로캘**(기본안). 한국어 스타일은 `localeList = LocaleList("ko-KR")`, 일본어 스타일은 `LocaleList("ja-JP")`. `FontFamily.Default`로 두어 기기가 로캘에 맞는 CJK 글꼴(대부분 Noto Sans CJK의 KR·JP 변형)을 고르게 한다. 번들 여부는 AR-U05(이 문서는 C 추천, 14절).
- 일본어 스타일은 굵기 400 미만을 쓰지 않는다(획이 많은 한자가 뭉개진다).
- 숫자가 바뀌는 자리(남은 카드 수, 타이머, 점수, 간격 미리보기)는 `fontFeatureSettings = "tnum"`.
- 줄바꿈: 모든 본문 스타일에 `lineBreak = LineBreak.Paragraph`. 일본어 스타일은 `LineBreak.Paragraph.copy(strictness = LineBreak.Strictness.Strict)`로 행두·행말 금칙(작은 가나, 장음 부호 `ー`, 닫는 괄호가 줄 머리에 오지 않게)을 건다. 한국어·일본어 모두 `WordBreak.Phrase`는 API 33 이상에서만 효과가 있으므로 걸되, 결과는 스크린샷으로 확인한다.

#### 3.2.1 한국어 UI(`MaterialTheme.typography`, `ko-KR`)

M3 기본 크기를 쓰고, 한글 본문은 줄 간격만 늘렸다.

| 스타일 | 크기 / 줄 높이(sp) | 굵기 | 쓰는 곳 |
|--------|---------------------|------|---------|
| displaySmall | 36 / 44 | 400 | S04 완료 숫자 |
| headlineMedium | 28 / 36 | 400 | 상태 화면 제목(빈 상태·오류) |
| koPrompt(= headlineMedium, `ko-KR`) | 28 / 36 | 400 | 단어 회상 카드 앞면의 한국어 뜻(학습 대상, 5.1) |
| headlineSmall | 24 / 32 | 400 | S02 "오늘 복습 12장" |
| titleLarge | 22 / 28 | 400 | 상단 바 제목 |
| titleMedium | 16 / 24 | 500 | 카드·절 제목, 목록 항목 제목 |
| titleSmall | 14 / 20 | 500 | 표 머리 |
| bodyLarge | 16 / **26** | 400 | 한국어 뜻(카드 뒷면), 해설 본문 |
| bodyMedium | 14 / **22** | 400 | 일반 본문, 고지 문구 |
| bodySmall | 12 / **18** | 400 | 출처 표기, 보조 설명 |
| labelLarge | 14 / 20 | 500 | 버튼 라벨, 평가 버튼 이름 |
| labelMedium | 12 / 16 | 500 | 칩, 평가 버튼의 간격 미리보기 |
| labelSmall | 11 / 16 | 500 | 내비게이션 라벨(선택 항목만 700, 1.2) |

#### 3.2.2 일본어(`JpTypography`, `ja-JP`, 테마에 한 번 정의해 `LocalJpTypography`로 제공)

| 스타일 | 크기 / 줄 높이(sp) | 굵기 | 쓰는 곳 | 기준 |
|--------|---------------------|------|---------|------|
| jpKanjiHero | 88 / 104 | 400 | 한자 카드 앞면, 한자 상세 머리 | D1(displayMedium 45 이상) |
| jpHeadword | 45 / 56 | 400 | 단어 카드 앞면·뒷면 표기, 단어 상세 머리 | D1(displayMedium과 같음) |
| jpTitle | 24 / 36 | 500 | 문법 항목 제목(〜ために), 문제 지문의 대상 | |
| jpBodyLarge | 20 / 32 | 400 | 예문, 문제 지문, 선택지(5.4), 청해 대본 | 한자가 한국어 본문보다 커야 획을 구별한다 |
| jpBody | 16 / 26 | 400 | 읽기(음독·훈독), 목록의 표기, 문장 배열 조각 | 후리가나를 얹지 않는다 |
| jpBodySmall | 14 / 22 | 400 | 목록 보조(읽기) | |
| ruby | max(기반 스타일 × 0.5, **11 sp**)(jpBodyLarge 위 11 sp, jpTitle 위 12 sp, jpHeadword 위 22.5 sp, jpKanjiHero 위에는 얹지 않음) | 400 | 후리가나 | D3 |

- 한국어 문장 속 일본어 구간(4절)은 둘러싼 한국어 스타일의 크기를 그대로 쓰고 로캘만 바꾼다(`SpanStyle(localeList = ja-JP)`). 크기를 바꾸면 줄 높이가 흔들린다.
- 후리가나를 얹을 수 있는 기반 스타일은 **jpHeadword·jpTitle·jpBodyLarge 셋뿐**이다. jpBody·jpBodySmall에는 얹지 않고 읽기를 따로 보인다(8 sp 이하 가나는 ば/ぱ, ゃ/や를 가리기 어렵다). 후리가나가 필요한 자리는 jpBodyLarge로 올린다(선택지가 그래서 jpBodyLarge다).
- 영어 원문(EDRDG 등 저작자 표시 문구)은 `en` 로캘의 bodyMedium. 사전 영어 뜻은 번들하지 않으므로 화면에 없다(architecture.md 4.2).
- Android 14 이상의 비선형 글꼴 확대로 88sp 같은 큰 글자는 200%에서도 2배가 되지 않는다. 그래도 아래 배치 규칙은 선형 2배를 가정해 정했다.

### 3.3 간격

4 dp 단위. Compose에서 `object Spacing { val xs = 4.dp ... }`.

| 이름 | 값 | 쓰는 곳 |
|------|-----|---------|
| xs | 4 dp | 아이콘과 글자 사이, 후리가나와 본문 사이(0 dp가 아니라 2 dp — 5.2) |
| sm | 8 dp | 칩 사이, 평가 버튼 사이 |
| md | 12 dp | 카드 안 항목 사이 |
| lg | 16 dp | 화면 좌우 여백(Compact), 카드 안쪽 여백 |
| xl | 24 dp | 절 사이, 화면 좌우 여백(Medium 이상) |
| xxl | 32 dp | 상태 화면의 아이콘·제목·문구 사이 |

- 본문 최대 폭 600 dp. 넓은 화면에서는 가운데 정렬.
- 하단 고정 영역(평가 버튼 줄, "답 보기", 선택지 확인)은 `WindowInsets.navigationBars` 위에 놓는다(edge-to-edge).

### 3.4 모양

| 토큰(`Shapes`) | 값 | 쓰는 곳 |
|----------------|-----|---------|
| extraSmall | 4 dp | 후리가나 켜기 등 작은 표식 |
| small | 8 dp | 칩(급수, 출처·검수) |
| medium | 12 dp | 평가 버튼, 선택지 버튼 |
| large | 20 dp | 플래시카드, 목록 카드(안쪽 여백 16 dp − 칩 8 dp ≈ 12 dp라 동심 반경을 엄밀히 맞추지 않고 칩을 별도 면으로 본다) |
| extraLarge | 28 dp | 대화상자, 하단 시트(M3 기본) |

- 그림자 없음. 면 구분은 `surfaceContainer*` 단계로 한다.

### 3.5 아이콘

- Material Symbols Rounded, 굵기 400, 채움 0(선택 상태만 채움 1), 24 dp. 필요한 것만 벡터 XML로 `res/drawable`에 넣는다(material3 최신판은 아이콘 묶음을 끌어오지 않으므로 의존성을 늘리지 않는다). 아이콘 라이선스는 출처 기록 대상 S-11(UI-U04). 파일은 `res/drawable/ic_ms_<이름>.xml`, 채움 1 변형(내비게이션 선택 항목, 켜진 후리가나 토글)은 `ic_ms_<이름>_fill.xml`, 고지는 `assets/licenses/`(architecture.md 6.6, 9.2 항목 5).
- 쓰는 아이콘(이 목록이 매니페스트 S-11 `usedFields`와 `ic_ms_*` 대조 테스트의 기준이다. 목록에 없는 아이콘을 화면에 쓰지 않는다): home, menu_book(사전), school(문법), edit_note(연습, 직접 작성 칩), settings, arrow_back, close, undo, info, check(선택된 선택지 5.4, 답한 문항 칩 6.10), check_circle, cancel, play_arrow, stop, replay, volume_up, error, schedule, search, open_in_new, content_copy, translate(번역 출처 칩), more_vert, language_japanese_kana(후리가나 토글 5.2·6.3, 켜짐이면 채움 1).
- 아이콘만 있는 버튼은 모두 `contentDescription`(11.1).

### 3.6 움직임

| 동작 | 값 |
|------|-----|
| 카드 뒤집기 | 3D 회전 없음. 앞면 표기는 제자리(같은 y 좌표, 같은 글자 크기)에 두고 루비와 뒷면 정보가 페이드 인 150 ms(`tween(150, easing = FastOutSlowInEasing)`). 5.1 |
| 다음 카드 | 현재 카드 페이드 아웃 100 ms → 다음 카드 페이드 인 150 ms |
| 평가 버튼 줄 등장 | 페이드 인 150 ms. 등장 후 **300 ms 동안 평가 입력 무시**("답 보기"를 두 번 눌러 평가가 눌리는 것을 막는다) |
| 다음 카드 등장 | 등장 후 **300 ms 동안 "답 보기"와 카드 탭 입력 무시**(평가를 두 번 눌러 다음 카드 뒷면이 바로 열리는 것을 막는다) |
| 피드백(문제) | 선택지 배경색 전환 150 ms |
| 로딩 표시 | 300 ms 안에 끝나는 로딩은 표시기를 띄우지 않는다(깜빡임 방지) |

- 두 입력 무시 시간은 테마의 상수 하나 `InputGuardMs = 300`이다. ViewModel이 마지막 화면 전환 시각을 들고 있다가 300 ms 안의 `reveal`·`rate` 호출을 무시한다. ViewModel 테스트(고정 시계)로 "평가 직후 300 ms 안의 reveal은 무시, 300 ms 뒤는 동작"과 그 반대 방향을 확인한다.
- 시스템 "애니메이션 삭제"가 켜져 있으면 모두 즉시 전환(입력 무시 시간은 그대로). 스크린샷 테스트는 애니메이션을 끈다.

## 4. 한·일 혼용 표시 규칙(D2, D8)

### 4.1 표기법

이 문서의 문구에서 `[ja]…[/ja]`는 일본어 로캘을 걸 구간, `{이름}`은 데이터로 채우는 자리다. `[ja]{표기}[/ja]`는 "데이터로 채우고 일본어 로캘을 건다"는 뜻이다. 구간 표시가 없는 한국어 문구는 `ko-KR`. `[ja]`는 **이 문서의 표기일 뿐** 콘텐츠·문자열 리소스 형식이 아니다(콘텐츠 안 구간은 4.4).

### 4.2 로캘을 거는 자리

| 자리 | 일본어 부분 | 방법 |
|------|-------------|------|
| 카드 앞·뒤 표기, 읽기, 한자 | 전부 | `JpText`/`RubyText` |
| 카드 뒷면 "읽기 [ja]{읽기}[/ja]" | 값만 | 한국어 라벨 + 일본어 값은 별도 `Text`로 나눈다(한 `Text`에 섞지 않는다) |
| 한국어 뜻 | 없음. 단, 뜻 안에 일본어가 들어 있으면(예: "[ja]〜さん[/ja]보다 정중한 호칭") 4.4 규칙으로 찾은 구간 | `JpSpans.annotate(text)` |
| 문제 지시문 | "밑줄 친 [ja]{대상}[/ja]의 읽기로 알맞은 것을 고르세요." | `AnnotatedString` |
| 문제 지문·선택지 | 전부 | `JpText` |
| 문법 제목·접속 표의 형태 열 | 전부 | `JpText` |
| 문법 설명 본문·해설·오답 이유 | 4.4 규칙으로 찾은 구간 | `JpSpans.annotate(text)` |
| 예문 / 번역 | 예문 전부 / 번역 없음 | 두 `Text` |
| 해설 | "정답 [ja]{선택지}[/ja]: …" | `AnnotatedString` |
| 급수 이름 "N5" | 없음(로마자) | |
| 검색어 입력 | 입력란 `KeyboardOptions(hintLocales = LocaleList("ja-JP", "ko-KR"))`, 입력 글자는 `ja-JP` 로캘로 그린다(가나·한자가 섞이므로) | |
| EDRDG 등 영어 표시 문구 | 없음 | `en` 로캘 |

### 4.3 공통 컴포저블 규칙

- `JpText(text, style)`: `style`에 `localeList = ja-JP`를 넣고, 동시에 전체 범위에 `SpanStyle(localeList = ja-JP)`를 건 `AnnotatedString`으로 그린다. 앞은 글리프 선택, 뒤는 접근성 텍스트의 `LocaleSpan`(TalkBack 낭독 언어)을 위해서다. 실제 TalkBack 언어 전환은 실기기에서만 확인되므로 완료 조건에 `unverified`로 남긴다(`compose-app-dev` 6절).
- 혼용 문장은 `buildAnnotatedString`으로 만들고 일본어 구간에만 `SpanStyle(localeList = ja-JP)`를 건다. 한국어 부분에는 `ko-KR`.
- 화면 코드에서 로캘을 직접 만들지 않는다. 테마의 `JpTypography`(`LocalJpTypography`)와 `JpSpan`(미리 만든 `SpanStyle`)만 쓴다. 루비 컴포저블 `RubyText`, 해석기 `Furigana.parse`·`Furigana.fromHeadword`(architecture.md 4.4·7절).
- **TalkBack 낭독 문구에 일본어가 섞이면**: `contentDescription`·`onClickLabel`·`stateDescription`·`announceForAccessibility`는 `String`이라 로캘 구간을 담지 못한다. 그래서
  1. 일본어가 섞이는 낭독 문구(11.1의 `[ja]`가 있는 행)는 그 노드에 `Modifier.clearAndSetSemantics { text = buildAnnotatedString { … } }`로 넣는다. 한국어 부분은 `ko-KR`, 일본어 부분은 `JpSpan`. 접근성 서비스에는 `LocaleSpan`으로 전달된다.
  2. `liveRegion`이 필요하면 같은 노드에 건다(문구를 따로 알리지 않는다).
  3. `String` 속성(`contentDescription`·`onClickLabel`·`stateDescription`)에는 일본어를 넣지 않는다.
  4. 어쩔 수 없이 `String`에 일본어를 넣어야 하면 한자 없이 **가나 읽기만** 쓴다(한국어 음성이 한자를 한국 한자음으로 읽는 일을 막는다).
- 실기기 TalkBack 언어 전환 확인(`unverified`)은 카드 세션 마일스톤(M4)에도 넣는다: "앞면 일본어 표기가 일본어 음성으로 낭독된다", "뒷면 '읽기 …'의 일본어 부분이 일본어로 낭독된다". 청해 마일스톤에만 두지 않는다.

### 4.4 한국어 텍스트 안 일본어 구간(콘텐츠 형식에 표시 없음)

- 콘텐츠는 한국어 필드(`text_ko`, `meaningKo`, `explanationKo`, `whyWrongKo`, 예문 번역 `ko`)에 구간 표시를 넣지 않는다. 앱이 결정적 규칙으로 찾는다(문자 집합 정본은 architecture.md 4.4): **히라가나, 가타카나(`ー`·`・` 포함), CJK 통합 한자(확장 A 포함), `々`, `〜`, `「」『』`가 한 글자 이상 이어진 구간 = `ja-JP`**. 공백·한글·라틴 문자·숫자·그 밖의 문장 부호에서 구간이 끝난다.
- 이 앱의 한국어 문장은 한자를 쓰지 않으므로 오판이 적다. 파이프라인 `authored`·`ko-gloss` 단계는 한국어 필드에서 찾은 일본어 구간을 보고서에 나열한다(검토용, 실패 아님). 한국어 필드에 `{`·`|`·`}`가 있으면 FAIL이다(architecture.md 4.4).
- 함수 하나: `:app`의 `JpSpans.annotate(text: String): AnnotatedString`(한국어 부분 `ko-KR`, 일본어 구간 `JpSpan`). 단위 테스트: "〜さん보다 정중한 호칭", "〜て형 뒤에 붙는다", "「食べる」의 정중형", "コーヒー・紅茶 중에서", 『』로 묶인 구간, 일본어 없는 문장.
- 문법 예문·문항·청해 대본·접속 표 `example`(일본어 필드)의 후리가나는 architecture.md 4.4의 `{漢字|かな}` 형식이고 `Furigana.parse`가 해석한다. 한국어 필드에는 `{…|…}`를 쓰지 않는다. 한국어 필드는 `JpSpans.annotate`로만 그리고 `RubyText`를 거치지 않는다.
- **밑줄 칠 대상 구간**(문법 카드 예문의 문법 형식 부분, 문항 지문의 대상 부분)은 일본어 필드 안 `[[…]]` 표기다(architecture.md 4.4. 루비와 겹쳐 쓸 수 있다: `[[{行|い}けば]]`. 한국어 필드에는 쓰지 않는다). `Furigana.parse` 결과의 `RubySegment.emphasis = true` 구간을 `RubyText`가 밑줄 2 dp + 굵기 500으로 그린다(색만으로 표시하지 않는다). 앱은 문법 제목과 예문을 맞춰 보아 대상 구간을 추측하지 않는다(활용·루비 분할 때문에 틀린다). `[[`가 없으면 밑줄을 그리지 않는다.

## 5. 컴포넌트

### 5.1 플래시카드(`Flashcard`)

- 면: `surfaceContainerLow`, 모양 large(20 dp), 안쪽 여백 16 dp, 화면 좌우 여백 16 dp, 세로는 상단 진행 표시와 하단 고정 영역 사이를 채운다. 내용이 넘치면 카드 안에서 세로 스크롤.
- 앞면: 학습 대상을 카드 세로 가운데에서 약간 위(카드 높이의 40% 지점)에 둔다. 다른 요소는 왼쪽 위 카드 종류 라벨("단어" / "한자" / "뜻 → 일본어" / "문법", labelMedium, onSurfaceVariant)과 오른쪽 위 급수 칩만.
  - 단어 인식: 표기 jpHeadword. 한자: jpKanjiHero.
  - 단어 회상: 한국어 뜻 **첫 뜻 하나만** `koPrompt`(28/36 sp). 여러 뜻을 한꺼번에 보이면 정답이 더 모호해진다(UI-U02). 긴 뜻은 줄바꿈(최대 3줄, 그 뒤는 카드 안 스크롤), 축소하지 않는다.
- **앞·뒷면 표제어 위치는 같다**(3.6). 플래시카드의 표제어(jpHeadword)는 앞·뒷면 모두 루비 줄 높이(22.5 sp × 1.2 + 2 dp ≈ 29 dp)를 **언제나** 확보한다. 앞면에서는 루비 글자를 투명(알파 0, 접근성 트리에서도 숨김)으로 두고, 뒷면에서 같은 자리에 150 ms 페이드 인한다. 표제어의 세로 위치는 앞면 기준(40% 지점)으로 고정하고, 뒷면 정보는 그 **아래**에 나타난다(뒷면에서 표제어를 위로 끌어올리지 않는다). 내용이 카드보다 길면 표제어 아래 영역만 스크롤한다. 앞면에서 축소 규칙(아래 "긴 표기")이 걸린 크기를 뒷면도 그대로 쓴다. 5.2의 "끄면 위 여백을 없앤다"는 플래시카드 표제어에 적용하지 않는다(설정에서 후리가나를 꺼도 줄 높이는 남는다).
- 뒷면(단어): 표제어(jpHeadword + 루비) 아래로 읽기(jpBody) → 품사(labelMedium 칩, 한국어, `pos` 코드의 한국어 이름표) → 구분선 → 한국어 뜻(bodyLarge, 뜻이 여럿이면 번호 목록 최대 3개, 나머지는 "뜻 {n}개 더" 접기, 뜻마다 아래에 출처·검수 칩 5.7).
- 뒷면(한자): 한자(jpKanjiHero, 제자리) 아래로 표: "한국어 음"(값 한국어) / "음독"(값 일본어, 가타카나) / "훈독"(값 일본어, 송리가나는 괄호: [ja]た(べる)[/ja]. 접두·접미 표시 '-'는 '～'로: [ja]～びら(き)[/ja], [ja]あお～[/ja]. 읽기 하나는 줄 가운데서 끊지 않는다) / "획수" → "쓰인 단어"(최대 3개, jpBody + 한국어 뜻 bodyMedium). 뜻 행은 없다(2.1). 값이 없는 행은 행째 숨긴다(예: 한국어 음은 KANJIDIC2에서 13,108자 중 6,293자에만 있다 — S-02.korean).
- 뒷면(단어 회상): 앞면 뜻(koPrompt, 제자리) 아래로 표기(jpHeadword + 루비) → 읽기 → 한국어 뜻 전체(번호 목록)와 칩.
- 긴 표기(D7): 앞면 표기는 `maxLines = 1`로 시작해 폭에 맞을 때까지 글자 크기를 줄인다(최저 기준 크기의 60%, jpHeadword는 27 sp). 그래도 넘치면 2줄까지 금칙 규칙으로 줄바꿈. 예: [ja]コミュニケーション[/ja](9자)는 360 dp 폭·글꼴 100%에서 45 sp로 넘치므로 축소된다. 이 축소는 폭 기준이다. 높이가 모자라는 경우는 6.3의 낮은 높이 배치가 맡는다.

### 5.2 후리가나 텍스트(`RubyText`, D3)

- 입력: `List<RubySegment(base, ruby?, emphasis)>`(architecture.md 4.4. `emphasis`는 4.4 `[[…]]` 대상 구간, 밑줄 2 dp + 굵기 500). 구간은 콘텐츠에 저장하지 않고 앱이 만든다.
  - 어휘 표제어: `Furigana.fromHeadword(written, reading): List<RubySegment>`(앱 계산 값, `:app` 한 곳). `written`과 `reading`의 **앞뒤 공통 가나를 잘라 내고** 가운데 한자 구간에만 루비를 얹는다(예: [ja]食べる[/ja] → 食|た + べる, [ja]お茶[/ja] → お + 茶|ちゃ, [ja]引き出し[/ja] → 引き出|ひきだ + し). 남은 가운데(가나가 섞여 있어도)는 한 구간이다. 잘라 낼 공통 가나가 없으면 단어 전체가 한 구간이 되어 읽기를 단어 위에 가운데 정렬한다(묶음 루비, 예: [ja]今日[/ja]). `written`이 NULL(가나 전용)이면 루비 없음. 외부 대응 자료는 쓰지 않는다. 단위 테스트: 食べる, お茶, 今日, 引き出し, 小さい, お母さん, 가나 전용 어휘(루비 없음).
  - 문장(문법 예문·문항·청해 대본): `Furigana.parse(text)`(`{漢字|かな}` 형식).
- 후리가나 크기 = 3.2.2 `ruby`(max(본문 × 0.5, 11 sp)), 색 onSurfaceVariant, 본문과의 간격 2 dp. 얹을 수 있는 본문은 jpHeadword·jpTitle·jpBodyLarge뿐이다(3.2.2).
- 줄 높이: 후리가나가 켜져 있으면 줄마다 `후리가나 줄 높이(ruby 크기 × 1.2) + 2 dp`를 위에 확보한다(jpBodyLarge 위 11 × 1.2 + 2 ≈ 15 dp, jpHeadword 위 ≈ 29 dp). 그래서 후리가나가 윗줄 본문과 겹치지 않는다.
- 후리가나가 본문보다 넓으면(예: [ja]今日[/ja] 위 [ja]きょう[/ja]) 본문 쪽에 좌우 여백을 넣어 구간 폭을 후리가나에 맞춘다. 후리가나를 줄이지 않는다.
- 줄바꿈은 구간 경계에서만 한다. 한 구간(본문+후리가나)은 줄을 넘어 나뉘지 않는다. 구간 하나가 한 줄보다 넓으면 그 구간만 글자 크기를 줄인다(5.1의 축소 규칙과 같다).
- 글꼴 200%: 본문과 후리가나가 함께 커진다(둘 다 sp, 11 sp 하한도 22 sp가 된다). 위 규칙이 그대로 적용된다. 스크린샷 `word_detail_loaded_light_font200` 등으로 확인(12절).
- 켜고 끄기: 설정에 "앞면 후리가나"(`front_furigana`, 기본 끔 — 읽기가 정답의 일부이므로)와 "본문 후리가나"(`body_furigana`, 뒷면·상세·예문·문제 지문, 기본 켬). 세션·상세 화면 상단 바에 토글 아이콘 버튼(language_japanese_kana, "후리가나", `stateDescription` "켜짐"/"꺼짐")을 두어 그 자리에서 바꾼다. 끄면 위 여백을 없앤다(토글이 명시적 동작이므로 배치가 바뀌어도 된다). **예외: 플래시카드 표제어는 줄 높이를 남긴다(5.1).**
- 문제의 "한자 읽기"·"표기" 유형 지문과 선택지에는 후리가나를 **언제나 끈다**(정답이 드러난다). 토글은 그 화면에서 숨긴다.
- 후리가나가 없는 문장(예: Tatoeba 예문은 읽기 정보가 없다)은 본문만 그리고 위 여백을 두지 않는다. 그 문장 아래에 "이 예문에는 읽기 표시가 없습니다"를 bodySmall로 한 번 보인다.
- TalkBack: 후리가나 글자는 접근성 트리에서 숨기고(`invisibleToUser`), 구간 묶음 전체를 하나의 노드로 합친다. 노드 문구는 4.3의 방법(`clearAndSetSemantics { text = AnnotatedString }`)으로 만든다: [ja]{본문}[/ja] + 후리가나가 켜져 있으면 ", 읽기 [ja]{전체 읽기}[/ja]"(한국어 ", 읽기 "는 `ko-KR`). `contentDescription`으로 덧붙이지 않는다(로캘이 사라진다). 후리가나가 꺼져 있으면 읽기를 붙이지 않는다.

### 5.3 평가 버튼 줄(`RatingBar`, D4)

- 네 버튼: **다시 · 어려움 · 알맞음 · 쉬움**(왼쪽부터) = `Rating.AGAIN·HARD·GOOD·EASY`. architecture.md가 FSRS-6(4단계 평가)으로 정했다(AR-U01과 함께 승인). 이름표는 `ui/review/RatingLabels.kt` 한 곳.
- 각 버튼: 위 줄 이름(labelLarge), 아래 줄 다음 간격 미리보기(labelMedium, `tnum`). 미리보기는 `:core:srs`의 `IntervalPreview`(architecture.md 8.1의 하나뿐인 타입: `dueAtEpochMs`, 복습이면 `scheduledDays: Int`, 학습·재학습 단계면 `stepDuration: Duration`)를 받아 아래 형식으로 바꾼다. UI 쪽에 따로 `IntervalPreview` 타입을 두지 않는다. 변환은 `RatingLabels.kt` 옆 `ui/review/IntervalFormat.kt`의 `IntervalFormat.format(preview): String` 한 곳에서 한다. 이 절이 표시 규칙의 정본이다.

규칙(위에서부터 처음 맞는 행. 반올림은 모두 0.5 올림):

| # | 조건 | 표시 | 예 |
|---|------|------|-----|
| 1 | `stepDuration`이 **반올림 전 값으로** 60초 미만 | "1분 미만" | 30초, 59초 |
| 2 | `stepDuration`을 분으로 반올림(30초 올림)한 값 m < 60 | "{m}분" | 10분, 5분 30초 → "6분" |
| 3 | `stepDuration`을 시간으로 반올림(30분 올림)한 값 h < 24 | "{h}시간" | 5시간, 59분 30초 → "1시간" |
| 4 | `stepDuration` 그 밖 | "{일로 반올림}일" | |
| 5 | `scheduledDays` < 30 | "{n}일" | 3일 |
| 6 | `scheduledDays` < 365 | "{n ÷ 30을 소수 첫째 자리로 반올림}개월"(.0은 생략) | 63일 → "2.1개월" |
| 7 | `scheduledDays` 그 밖 | "{n ÷ 365를 소수 첫째 자리로 반올림}년"(.0은 생략) | 438일 → "1.2년" |

- 1행만 반올림 전 값으로 판정한다. 2행부터는 반올림을 먼저 하고 단위를 고른다(59분 30초 → 60분 → 2행 아님 → "1시간"). 미리보기 표시만 반올림하며 기한(`dueAtEpochMs`)은 반올림하지 않는다.
- 경계값 기대값(M4 단위 테스트가 이 값을 그대로 쓴다):

| 입력 | 표시 |
|------|------|
| 30초 | "1분 미만" |
| 59초 | "1분 미만" |
| 60초 | "1분" |
| 5분 30초 | "6분" |
| 10분 | "10분" |
| 59분 30초 | "1시간" |
| 5시간 | "5시간" |
| 23시간 | "23시간" |
| `scheduledDays` 3 | "3일" |
| 29 | "29일" |
| 30 | "1개월" |
| 63 | "2.1개월" |
| 364 | "12.1개월" |
| 365 | "1년" |
| 438 | "1.2년" |

| 버튼 | 색(배경 / 글자) | 모양 |
|------|-----------------|------|
| 다시 | errorContainer / onErrorContainer | medium 12 dp |
| 어려움 | secondaryContainer / onSecondaryContainer | |
| 알맞음 | primary / onPrimary | |
| 쉬움 | tertiaryContainer / onTertiaryContainer | |

- 색은 보조 단서다. 이름 글자가 항상 있다(D6 원칙).
- 크기: 높이 최소 **64 dp**(두 줄 라벨), 폭은 줄을 4등분(사이 8 dp). 터치 영역 48 dp 이상을 넘는다. 화면 하단, 내비게이션 바 인셋 위 16 dp.
- 글꼴 확대(D7): 버튼 하나라도 이름이나 미리보기가 한 줄에 들어가지 않으면 줄 전체를 **2×2 격자**로 바꾼다(윗줄 다시·어려움, 아랫줄 알맞음·쉬움). 격자에서도 안 들어가면 버튼 안에서 줄바꿈하고 높이를 늘린다. 글자를 자르거나 말줄임하지 않는다. 판단은 측정으로 한다(글꼴 배율 값으로 하지 않는다).
- "답 보기" 버튼: 앞면일 때 같은 자리에 폭 전체, 높이 56 dp, primary 채운 버튼. 뒤집으면 평가 줄로 바뀐다(3.6의 300 ms 입력 무시).
- 저장 중: 누른 버튼에 진행 표시를 겹치지 않는다(저장은 보통 즉시 끝난다). 300 ms를 넘으면 줄 전체를 비활성하고 줄 위에 얇은 `LinearProgressIndicator`.
- 저장 실패: 7절. 카드·미리보기는 그대로, 평가 줄은 다시 누를 수 있다.
- TalkBack: `onClickLabel` = "평가: {이름}, 다음 복습 {미리보기} 후"(받침에 따라 바뀌는 조사를 쓰지 않는다). 예: "평가: 다시, 다음 복습 10분 후". 미리보기가 "1분 미만"이면 "평가: {이름}, 다음 복습 1분 안에". 버튼에 보이는 글자(이름·미리보기)는 그대로 낭독되게 둔다(합치거나 지우지 않는다). 네 버튼은 하나의 순회 묶음(`isTraversalGroup = true`)으로 왼쪽부터 읽힌다.

### 5.4 선택지 버튼(`ChoiceButton`)

- 폭 전체, 최소 높이 56 dp, 모양 medium, 배경 surface, 테두리 1 dp outline. 왼쪽에 번호 표식(1~4, labelLarge), 오른쪽에 선택지 글(jpBodyLarge, 후리가나는 유형 규칙 5.2를 따름, 줄바꿈 허용, 줄 수 제한 없음).
- 번호 표식은 고정 크기가 아니다: `defaultMinSize(28.dp, 28.dp)`, 모양은 둥근 사각(반경 14 dp = 100% 크기에서 원)으로 글자에 맞춰 커진다(지름 = max(28 dp, 줄 높이 + 8 dp), 글꼴 200%에서 약 48 dp).
- 선택(모의고사, 아직 채점 전): 배경 primaryContainer, 안쪽 테두리 2 dp primary, 번호 표식 채움, 선택지 오른쪽 끝 check 아이콘. 포커스 링(3.1.5)과 구분된다.
- 네 선택지는 `Modifier.selectableGroup()` 안의 `selectable(role = Role.RadioButton)`이다(TalkBack이 "4개 중 2번째", "선택됨"을 읽는다). 11.1.
- 선택지 사이 8 dp.

### 5.5 정답·오답 피드백(D6)

연습 모드에서 선택 즉시 보인다(모의고사는 결과 화면에서만).

| 상태 | 배경 / 글자 | 아이콘(24 dp, 선택지 오른쪽 끝) | 글자(선택지 아래, labelLarge) |
|------|-------------|-------------------------------|-------------------------------|
| 고른 답이 정답 | correctContainer / onCorrectContainer | check_circle(correct) | "정답" |
| 고른 답이 오답 | errorContainer / onErrorContainer | cancel(error) | "오답 · 내가 고른 답" |
| 오답을 골랐을 때의 정답 | correctContainer / onCorrectContainer | check_circle(correct) | "정답" |
| 나머지 | 변화 없음 | 없음 | 없음 |

- 그 아래 해설 카드(surfaceContainerLow): 정답 근거, 오답별 이유, 관련 단어·문법 링크, 카드 맨 아래 `ProvenanceChip`(5.7, 해설 작성 방법). 칩 입력은 문항 행(청해는 청해 행)의 `ko_*` 열로 만든 `MeaningUi`(architecture.md 9.2·9.3, grammar 9.1과 같은 열 묶음). 행의 `needs_review = 1`이면 칩 옆에 "해설 검토 중" 칩(6.9 "설명 검토 중"과 같은 모양). 본문 안 일본어는 4.4. 이 카드는 S13 피드백, S15 틀린 문항 해설, S16 청해 해설에서 같은 모양이다.
- 상태 글자("정답", "오답 · 내가 고른 답")는 그 선택지 노드의 낭독 문구에 합친다(선택지 글 뒤, 한국어). S15 해설에서 다시 볼 때도 같다.
- TalkBack: 선택 직후 피드백 노드(선택지 아래 글자 줄) 하나에 `liveRegion = Polite`를 걸고, 문구는 4.3의 `AnnotatedString` 방법으로: "정답입니다" 또는 "오답입니다. 정답은 {번호}번, [ja]{정답}[/ja]". `announceForAccessibility`(String)를 쓰지 않는다.

### 5.6 진행 표시(`SessionProgress`, D10)

- 세션 화면 상단 바 아래 고정: 왼쪽 "남은 {n}장"(titleMedium, `tnum`), 오른쪽 "복습 {a} · 새 카드 {b}"(labelMedium, onSurfaceVariant), 아래 `LinearProgressIndicator`(높이 4 dp, primary / secondaryContainer 트랙).
- 문제·모의고사: "{현재}/{전체}"와 같은 진행 막대. 모의고사는 오른쪽에 경과 시간 "{mm:ss}"(`tnum`).
- "다시"로 같은 세션에 다시 나오는 카드는 남은 수에 포함한다(수가 늘 수 있다).
- 진행 표시는 스크롤로 사라지지 않는다. 예외: 6.3의 낮은 높이 배치에서는 카드 스크롤 영역 맨 위로 들어간다.
- 값: `remainingReview`(a)·`remainingNew`(b), 남은 수 n = a + b(13.1).
- TalkBack: 진행 노드는 따로 알리지 않는다. 카드 앞면 노드 문구 끝에 "남은 {n}장"을 붙인다(11.1). 진행 노드에 포커스하면 "남은 {n}장, 복습 {a}, 새 카드 {b}".

### 5.7 출처·검수 칩(`ProvenanceChip`, D-03 수용)

한국어 뜻과 직접 작성한 설명(문법·해설)에 붙는다. D-03이 정해지지 않았으므로 **행의 기록 값만으로** 표시를 정한다. 원칙: 사람이 검수하지 않은 글을 검수된 것처럼 보이게 하지 않는다. "검수 완료"라는 말을 사람 검수에만 쓴다.

입력은 `MeaningUi`(architecture.md 7절: `textKo`, `koSource`, `koMethod`, `reviewStatus`, `reviewedAt`, `reviewerKind` — architecture.md 4.2 `vocab_meaning`의 `ko_source`·`ko_method`·`ko_review_status`·`ko_reviewed_at`·`ko_reviewer_kind`). 칩 문구 = **앞 부분(생성) + " · " + 뒷 부분(검수)**. 두 축은 서로 독립이고, 모든 조합이 아래 두 표로 정해진다.

앞 부분(생성)

| `ko_method` | `ko_source` | 앞 부분 | 아이콘 | 하단 시트 키(`meaningSourceTexts`) |
|------|------|------|------|------|
| `llm:*` | `jmdict-translated` | "AI 번역" | translate | `translated-llm`(모델 이름 있음) / `translated-llm-nomodel` |
| `llm:*` | `authored` 또는 그 밖 | "AI 작성" | edit_note | `authored-llm` / `authored-llm-nomodel` |
| `human` | `jmdict-translated` | "사람 번역" | translate | `translated-human` |
| `human` | `authored` 또는 그 밖 | "직접 작성" | edit_note | `authored-human` |
| 값 없음·알 수 없는 값 | 아무 값 | "만든 방법 미기록" | edit_note | `unknown` |

뒷 부분(검수)

| `ko_review_status` | `ko_reviewer_kind` | 뒷 부분 |
|------|------|------|
| `unreviewed` | 아무 값 | "검수 전" |
| `sampled` | 아무 값 | "표본 검수한 묶음" |
| `reviewed` | `human` | "사람 검수 완료" |
| `reviewed` | `agent` | "AI 검수" |
| `reviewed` | NULL·알 수 없는 값 | "검수자 미기록"(사람 검수로 보지 않는다) |
| 값 없음·알 수 없는 값 | 아무 값 | "검수 상태 미기록" |

- 예: "AI 번역 · 검수 전", "직접 작성 · AI 검수", "직접 작성 · 표본 검수한 묶음", "AI 작성 · 사람 검수 완료".
- 문구는 함수 하나 `ProvenanceLabel.of(meaning: MeaningUi): String`(앞 5 × 뒤 6 = 30 조합 전부를 단위 테스트), 짧은 형태(5.11)도 같은 함수.
- 칩은 언제나 보인다(사람 검수 완료도). 칩 유무로 상태를 전하면 "칩 없음 = 검수됨"으로 읽힐 수 있기 때문이다. 숨기는 설정을 두지 않는다(UI-U01).
- 모양: small 8 dp, 배경 surfaceContainerHighest, 글자 labelMedium onSurfaceVariant, 왼쪽 아이콘 16 dp. **높이는 최소값** `heightIn(min = 32.dp)`, 위아래 여백 6 dp, 글자 줄바꿈 허용(`maxLines` 없음, 글꼴 200%나 좁은 폭에서 두 줄이 된다). 터치 영역 48 dp(`minimumInteractiveComponentSize`). 경고색을 쓰지 않는다(정보다, 오류가 아니다).
- 누르면 하단 시트: 제목 "이 뜻은 어떻게 만들었나요", 본문 — 생성 방법 문장(아래 표), 검수 상태와 날짜("{ko_reviewed_at} AI 검수" 등, 날짜가 없으면 날짜 없이), 버튼 "출처와 라이선스 보기"(S09로). 영어 원문 뜻은 번들하지 않으므로 보이지 않는다.
- 생성 방법 문장은 `attribution.json` `meaningSourceTexts`(architecture.md 6.6, 문구 틀 `content/notices.ko.json`)에서 읽는다. 키 선택과 `{model}` 채우기는 `ProvenanceLabel.sheetText(meaning, texts)` 한 곳이 한다(칩 문구와 같은 객체, 키 7개 전부 단위 테스트). 문장(이 표가 정본):

| 키 | 문장 |
|----|------|
| `translated-llm` | "JMdict(EDRDG)의 영어 뜻을 바탕으로 AI({model})가 한국어로 번역했습니다." |
| `translated-llm-nomodel` | "JMdict(EDRDG)의 영어 뜻을 바탕으로 AI로 번역했습니다." |
| `translated-human` | "JMdict(EDRDG)의 영어 뜻을 바탕으로 사람이 한국어로 번역했습니다." |
| `authored-llm` | "AI({model})가 작성했습니다." |
| `authored-llm-nomodel` | "AI로 작성했습니다." |
| `authored-human` | "사람이 직접 작성했습니다." |
| `unknown` | "이 뜻을 만든 방법이 기록되어 있지 않습니다." |

- `{model}`은 `ko_method` 값(`llm:<모델>@<날짜>`)의 모델 부분이다. 모델 부분이 비었으면 `-nomodel` 키를 써서 빈 괄호 "AI()"를 만들지 않는다.
- TalkBack: 칩 `role = Button`, 라벨은 칩 문구 그대로(한국어뿐), `onClickLabel` = "만든 방법 자세히 보기".

### 5.8 TTS 재생 컨트롤(`PlaybackControl`, D-04 수용)

음성 출처와 관계없이 같은 컨트롤을 쓴다. 상태는 architecture.md 8.2 `SpeakerState`를 그대로 쓴다(이름: `Initializing`, `Ready`, `Playing(utteranceId)`, `MissingJapaneseData`, `NoEngine`, `Error`. `AudioFileMissing`은 D-04 B 구현이 생길 때 더해지는 상태다).

| 상태 | 주 버튼(56 dp 원형, primary) | 옆 문구(bodyMedium) | 행동 |
|------|------------------------------|---------------------|------|
| 준비 중(`Initializing`) | 비활성 + 원형 진행 표시 | "음성을 준비하고 있습니다" | 없음. 10초가 지나면 `Error`(타임아웃은 `Speaker` 구현 책임) |
| 재생 가능(`Ready`) | play_arrow "재생" | "{길이}" 또는 없음 | 재생 |
| 재생 중(`Playing`) | stop "정지" | "재생 중" | 정지 |
| 일본어 음성 데이터 없음(`MissingJapaneseData`, 기기 TTS) | 비활성 volume_up | "기기에 일본어 음성이 없습니다." | 버튼 "일본어 음성 설치"(`TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`) |
| TTS 엔진 없음(`NoEngine`, 기기 TTS) | 비활성 volume_up | "음성 합성 엔진이 없습니다. 대본을 읽고 풀 수 있습니다." | 버튼 "대본 보기"(대본을 펼친다) + "음성 설정 열기"(시스템 TTS 설정을 여는 인텐트, 열 수 없으면 버튼을 숨긴다) |
| 음성 파일 없음(`AudioFileMissing`, 사전 생성 음성, D-04 B 구현 이후) | 비활성 volume_up | "이 문제의 음성 파일이 없습니다. 대본을 읽고 풀 수 있습니다." | "대본 보기" |
| 오류(`Error`) | replay "다시 시도" | "음성을 재생하지 못했습니다." | 다시 시도 |

- 보조 버튼: "처음부터"(replay, 48 dp), 속도 "0.8×/1.0×" 토글 칩(최소 높이 48 dp 터치 영역, `stateDescription` "0.8배속"/"보통 속도"). 값은 재생을 시작할 때 architecture.md 8.2 `Speaker.speak(utteranceId, text, rate)`의 `rate`(0.8f/1.0f)로 넘긴다. 기기 TTS는 `setSpeechRate`, 사전 생성 음성은 재생기 속도로 바꾸는 것은 `Speaker` 구현 몫이고 화면은 같은 버튼이다. 재생 중에 바꾸면 다음 재생부터 적용한다. 값은 저장하지 않는다(화면을 다시 열면 1.0×).
- **자동 재생하지 않는다.** 화면이 열리거나 다음 문항으로 넘어가도 재생은 사용자가 주 버튼을 누를 때만 시작한다(실제 시험의 자동 재생을 따르지 않는다).
- 앱이 백그라운드로 가거나 화면을 떠나면 정지(`compose-app-dev` 8절).
- TalkBack: 주 버튼 라벨은 표의 버튼 이름. 상태 알림은 옆 문구 노드에 `liveRegion = Polite` 하나로, 문구는 **"재생 중"**(시작)과 **"정지됨"**(정지·끝) 두 가지뿐이다. TalkBack이 켜져 있으면(`AccessibilityManager.isTouchExplorationEnabled`) 버튼을 누른 뒤 **1초 늦게** 음성을 시작해 "재생 중" 알림이 음성 첫머리를 덮지 않게 한다.

### 5.9 급수 선택(`LevelPicker`)

- N5 · N4 · N3 · N2 · N1 다섯 개를 세로 목록(행 높이 최소 64 dp)으로. 각 행: 체크박스, "N5"(titleMedium), "단어 {n} · 한자 {m}"(bodySmall, 앱 계산 값: 번들 콘텐츠 행 수).
- 번들 콘텐츠가 없는 급수: 체크박스 비활성(**선택할 수 없다**) + "콘텐츠 준비 중"(bodySmall). 이 문서가 기준이다. 이미 선택된 급수가 콘텐츠 업데이트로 사라진 경우만 7절 S02 "선택 급수에 콘텐츠 없음"으로 처리한다. 앱 코드는 다섯 급수를 모두 다루고 콘텐츠는 마일스톤마다 늘어난다(plan.md 3절).
- 여러 개 선택 가능. 하나도 고르지 않으면 "시작"/"저장" 비활성 + "급수를 하나 이상 고르세요".
- 목록 아래 고지(N-09 요약): "급수 분류는 공식 자료가 아닙니다. 자세히" → `About(anchor = "N-09")`(architecture.md 7절 라우트, 아래 "S09 위치 이동").
- TalkBack: 행 전체가 하나의 `toggleable` 노드, `stateDescription` "선택됨"/"선택 안 됨", 준비 중이면 "콘텐츠 준비 중, 선택할 수 없음".

### 5.10 상태 화면(`StatusScreen`)

빈 상태·오류·완료·로딩 자리표시(7절 공통 규칙)에 공통으로 쓴다: 가운데 아이콘(48 dp, onSurfaceVariant), 제목(headlineMedium, 줄바꿈 허용), 문구(bodyMedium), 주 버튼(채운 버튼), 보조 버튼(텍스트 버튼). 버튼은 폭 전체(최대 400 dp), 높이 48 dp 이상. 7절의 모든 상태가 이 틀을 쓴다. 상태 화면이 열리면 제목에 접근성 포커스를 준다.

### 5.11 예문 출처 줄

예문 바로 아래 bodySmall onSurfaceVariant 한 줄: Tatoeba는 "Tatoeba #{문장 ID} · {작성자} · CC BY 2.0 FR"(CC0면 "CC0"), 직접 작성은 `ProvenanceLabel.of`의 문구("AI 작성 · 검수 전" 등). 누르면 `About(anchor = "{자료 ID}")`(Tatoeba면 `"S-04"`)로 그 자료 카드에 간다.

**S09 위치 이동**(5.9·5.11·6.6·6.7·6.7.1): 라우트 `About(anchor: String? = null)`(architecture.md 7절). `anchor`는 고지 `notices[].id`(예 `"N-09"`) 또는 자료 ID(`sources[]`의 ID, 예 `"S-03"`). S09가 `LOADED`가 되면 그 카드를 목록 맨 위로 스크롤하고(움직임 없이 바로, 3.6) 카드 제목 노드에 접근성 포커스를 준다. 그 ID가 에셋에 없거나 `anchor`가 null이면 맨 위에서 시작한다. `FAILED`(9.4)면 무시한다.

## 6. 화면별 명세

### 6.1 S01 급수 선택(첫 실행)

- 상단: "어느 급수를 공부할까요?"(headlineSmall), "나중에 설정에서 바꿀 수 있어요."(bodyMedium)
- 가운데: `LevelPicker`
- 아래: "하루 새 카드" 단계 조절(5~50, 5 단위, 기본 10, `tnum`) — 설정과 같은 값(`new_cards_per_day`, 범위는 AR-U06과 함께 승인)
- "시작"을 누르면 급수·새 카드 수와 `onboarding_done = 1`을 한 번에 저장하고 S02로.
- 하단 고정: "시작"(폭 전체 56 dp)
- 급수별 개수(`levelCounts()`)를 세는 동안(`PREPARING`) 개수 자리에 자리표시 막대를 보이고 "시작"은 비활성(7절). 에셋 복사는 S01보다 먼저 `AppState.Opening`에서 끝난다(7절 "전체(Opening)").

### 6.2 S02 홈

- 상단 바: 제목 "오늘의 학습", 오른쪽 more_vert 메뉴 → "정보"(S09). 아이콘 버튼 48 dp.
- 샘플 콘텐츠 띠(`isSampleContent`, 즉 `content_meta.kind == sample`일 때만): 요약 카드 위, 폭 전체, 배경 surfaceContainerHighest, 모양 small, 안쪽 여백 12 dp, 왼쪽 info 아이콘 24 dp(onSurfaceVariant, 장식이라 접근성에서 숨김) + "개발용 샘플 콘텐츠입니다. 실제 학습 데이터가 아닙니다."(bodyMedium onSurface, 줄바꿈 허용). 닫기 없음, 누를 수 없음. 이 띠는 S09 고지를 대신하지 않는다. 대비: `1B1C1F on E3E1DC: 13.04:1`(라이트), `E4E2E6 on 353438: 9.60:1`(다크), 본문 통과. 띠 문구는 앱 상태 표시이므로 문자열 리소스에 둔다(출처 문구가 아니다).
- 요약 카드(surfaceContainerLow): "복습 {a}장 · 새 카드 {b}장"(headlineSmall, `tnum`, a = `dueCount`, b = `newTodayCount`), "학습 급수 N5·N4"(bodyMedium) + "바꾸기"(텍스트 버튼 → `Settings(openLevelPicker = true)`, 설정 탭을 열고 `LevelPicker` 하단 시트를 바로 띄운다), 주 버튼 "학습 시작"(56 dp).
- 복습 수 `dueCount`와 7절의 "다음 복습" `nextDueAtEpochMs`·`nextDueCount`는 S03 대기열과 **같은 집합**에서 센다: architecture.md 8.2 `StudyPlanRepository.dueForSession`(콘텐츠에 있고 `retired`가 아님 ∧ 켜진 카드 종류 ∧ 학습 급수, 6.8 정책). 그래서 홈이 "복습 {a}장"을 보였는데 세션이 빈(`EMPTY`) 일은 시각이 바뀐 경우 말고는 생기지 않는다.
- 급수별 진도(`levelProgress`): 급수마다 한 줄 "N5 · 학습한 단어 {learnedWords}/{totalWords}"(`LevelProgress`, 앱 계산 값: `totalWords` = 그 급수 번들 어휘 수 `ContentRepository.levelCounts()`, `learnedWords` = `card_state`가 있는 `vr` 카드 수) + 진행 막대.
- 첫 안내 카드(`showFirstNotice` = `user_settings.first_notice_dismissed = 0`): 닫으면 1로 저장해 다시 보이지 않는다. 닫아도 S09에 같은 내용이 있다. 제목·본문은 `attribution.json` `firstNotice`(`HomeUiState.firstNotice: NoticeUi?`)를 그대로 보인다. 어떤 문장을 넣을지(콘텐츠 종류, DB에 있는 `ko_source`)는 파이프라인이 빌드 때 고른다(architecture.md 6.6). 문구 틀 초안: 제목 "한국어 뜻 안내", 본문 N-10 짧은 문장 + "뜻마다 만든 방법과 검수 상태를 표시합니다." 버튼 "자세히"(S09), "닫기". 출처 이름(JMdict 등)을 화면 코드나 문자열 리소스에 쓰지 않는다. 이 카드는 출처 표시 의무를 대신하지 않는다(S-01.attribution-place: 시작 화면만으로는 불충분).

### 6.3 S03 복습 세션

- 상단 바(내비게이션 바 없음): 왼쪽 close "학습 끝내기"(누르면 바로 S02, D10), 오른쪽 후리가나 토글, undo "되돌리기"(되돌릴 평가가 없으면 비활성).
- 그 아래 `SessionProgress`, 카드, 하단 고정 "답 보기" ↔ `RatingBar`.
- 되돌리기: 이전 카드를 뒷면 상태로 다시 보인다. 스낵바 "평가를 되돌렸습니다"(동작 없음, 4초).
- 세로 배치 높이 예산(360×640 dp, 글꼴 100%): 상단 바 64 + 진행 48 + 카드(남는 높이, 최소 280) + 평가 줄 64 + 여백 32. 글꼴 200%에서 평가 줄이 2×2(최대 약 2×96)가 되면 카드가 약 230 dp로 줄고 카드 안이 스크롤된다(앞면은 들어가고 뒷면은 스크롤).
- **낮은 높이 배치**: 창 높이가 480 dp 미만(가로 화면 휴대폰 ≈ 360 dp, 분할 화면)이면
  1. 상단 바를 48 dp 줄(`TopAppBar` 대신 아이콘 버튼 줄, 제목 없음)로 줄인다.
  2. `SessionProgress`를 카드 스크롤 영역 맨 위로 옮긴다(고정하지 않는다). 하단 "답 보기"/평가 줄만 고정한다.
  3. 폭이 600 dp 이상이면 두 칸으로: 왼쪽 카드(남는 폭), 오른쪽 고정 칸 폭 280 dp에 "답 보기" 또는 평가 버튼을 세로 2×2 격자로(칸 안에서만 세로 스크롤 허용). 카드는 창 높이 − 48 dp − 여백 16 dp 전부를 쓴다.
  4. 폭이 600 dp 미만이면 1·2만 적용하고 평가 줄은 하단에 둔다.
  확인: 스크린샷 `review_front_word_light_land_font200`, `review_back_word_light_land_font200`(640×360 dp, 12절). 앞면 표기가 카드 안에 다 보여야 한다.
- 가로 화면·Medium 폭 이상(높이 480 dp 이상): 본문 폭 600 dp 가운데, 평가 줄도 같은 폭.
- 카드와 고정 영역은 위아래로 나란히 놓이고 겹치지 않는다(고정 영역이 카드 내용을 덮지 않는다).

### 6.4 S04 세션 완료

`StatusScreen`: 아이콘 check_circle, 제목 "이번 학습을 마쳤어요", 문구 "{n}장 복습 · 새 카드 {m}장"(`tnum`) + 다음 상태 문구(7절 S02 상태와 같은 규칙: 남은 새 카드가 있으면 "새 카드 {k}장을 더 할 수 있어요", k = `newCardsLeftAfterSession`, 13.1). 주 버튼 "홈으로". 보조 버튼은 `newCardsLeftAfterSession > 0`이면 "새 카드 {5}장 더 학습", 0이면 없음.

### 6.5 S05 사전(단어·한자)

- 상단: 검색 입력란(4.2), 그 아래 탭 "단어"/"한자"(`TabRow`, M3 기본 `Role.Tab`), 급수 필터 칩(N5~N1 중 번들된 것, 여러 개, `FilterChip`, 기본은 학습 급수 전부). 상태 값은 `BrowseUiState.levels: Set<JlptLevel>`.
- 단어 행(최소 높이 64 dp): 표기(jpBody) + 읽기(jpBodySmall, onSurfaceVariant) / 한국어 뜻 첫 줄(bodyMedium, 1줄 말줄임 — 목록이므로 허용, 상세에서 전체) / 오른쪽 급수 칩. 글꼴 200%에서는 표기와 뜻이 세로로 쌓인다.
- 한자 탭: `LazyVerticalGrid(GridCells.Adaptive(minSize = max(64 dp, 한자 글자 크기 × 1.5)))`. 칸 안: 한자(jpTitle) 아래 한국어 음(labelMedium). 칸은 정사각을 고집하지 않는다: 폭은 격자가 정하고 높이는 `heightIn(min = 64.dp)`로 내용에 맞춰 늘어난다(글꼴 100%에서 52 dp 내용 + 여백, 200%에서 약 104 dp). 글꼴 200%면 최소 폭이 72 dp가 되어 열 수가 줄어든다.
- 검색: 표기·읽기·한국어 뜻(`vocab_meaning.text_ko` 부분 일치) 중 하나라도 맞으면. 입력 300 ms 멈춤 뒤 검색(`ContentRepository.browse`). 빈 결과의 두 상태는 `query.isNotBlank()`로 가른다: 검색어가 있으면 "검색 결과 없음", 없으면 "급수 필터 결과 없음"(7절).
- `LazyColumn`/`LazyVerticalGrid`에 안정 키(콘텐츠 안정 ID).

### 6.6 S06 단어 상세

- 머리: 표기(jpHeadword, 후리가나 켬) + 읽기(jpBody) + 급수 칩("N5 · 비공식" — 칩을 누르면 `About(anchor = "N-09")`, 5.11 "S09 위치 이동").
- 절: "뜻"(번호 목록, 품사 칩, 뜻마다 `ProvenanceChip`) → "쓰인 한자"(한자 칩 목록 → S07) → "예문"(콘텐츠에 있을 때만, 각 예문 아래 출처 줄 5.11) → "학습 상태"(카드 종류별 "새 카드" / "학습 중" / "다음 복습 {날짜}", 앱 계산 값).
- 콘텐츠에 없는 절은 절째 숨긴다(빈 제목을 남기지 않는다).

### 6.7 S07 한자 상세

- 머리: 한자(jpKanjiHero) + 획수 + 급수 칩 "N5 단어에 쓰인 한자 · 비공식"(한자 급수는 그 급수 어휘에서 파생한 값이다 — architecture.md 4.3. 칩을 누르면 `About(anchor = "N-09")`). 플래시카드 오른쪽 위 칩은 짧게 "N5"만.
- 절: "읽기"(한국어 음, 음독, 훈독 — 5.1 뒷면과 같은 표) → "쓰인 단어"(번들된 단어 중 이 한자를 포함한 것, 각 단어의 한국어 뜻 첫 줄 포함, 앱 계산 값, 최대 20개 + "더 보기") → "필순"(6.7.1). 뜻 절은 없다(2.1).

#### 6.7.1 필순 영역(M12, S-03 `confirmed`이고 `kanji_stroke` 데이터가 있을 때만, 없으면 절째 숨김)

- 절 머리 "필순"(titleMedium). 아래 정사각 그림 영역(폭 = min(본문 폭, 240 dp)), 배경 surfaceContainerLow, 모양 medium, 안쪽 여백 16 dp. 획은 `onSurface` 3 dp 둥근 끝, 그리지 않은 획은 그리지 않는다(바탕에 옅은 전체 모양을 깔지 않는다 — 대비 1.6:1 같은 장식선이 획처럼 보이지 않게).
- 그리기: 화면이 열리면 **자동으로 그리지 않는다**(정지 상태로 완성된 모양을 보인다). "획 순서대로 보기"(play_arrow, 48 dp 텍스트 버튼)를 누르면 지운 뒤 획마다 400 ms로 그리고 획 사이 150 ms 쉰다. 그리는 동안 버튼은 "멈추기"(stop), 끝나면 "다시 보기"(replay). 시스템 "애니메이션 삭제"가 켜져 있으면 그리기 대신 획 번호를 붙인 완성 그림을 보인다.
- 획 번호 보기 스위치(기본 끔): 각 획 시작점 옆 labelSmall 번호(onSurfaceVariant). 
- TalkBack: 그림 노드 `contentDescription` = "필순 그림, {n}획"(한국어, 일본어 없음). 그리기 중에는 알리지 않고 끝나면 `Polite` "필순 보기를 마쳤습니다".
- 출처 줄: 그림 아래 bodySmall "필순: KanjiVG"(문구는 저작자 표시 에셋의 S-03 이름에서 읽는다) — 누르면 `About(anchor = "S-03")`(KanjiVG 카드).
- 스크린샷 `kanji_detail_strokes`(완성 상태), `_font200`.
- 한국어 음 행 아래 bodySmall "한국어 음: Charles Muller 제공(KANJIDIC2)"을 두지 않는다. 그 표시는 S09 KANJIDIC2 카드의 `extraCredits`에 한 번 한다(S-02.korean-credit).

### 6.8 S08 설정

| 묶음 | 항목 | 형식 | 기본값 | `user_settings` 열(13.1) |
|------|------|------|--------|------|
| 학습 | 학습 급수 | 행 → `LevelPicker` 하단 시트 | 첫 실행 선택 | `target_levels` |
| | 하루 새 카드 | 단계 조절 **5~50, 5 단위** | 10 | `new_cards_per_day` |
| | 하루 시작 시각 | 시간 선택, 정시 단위 **00:00~12:00** | 04:00 | `day_boundary_minutes`(0..720, 60 단위) |
| | 카드 종류 | 스위치: 단어 인식, 한자, 단어 회상, 문법(문법 콘텐츠가 번들에 있을 때만 보임) | 켬·켬·끔·켬 | `vocab_recognition_enabled`, `kanji_cards_enabled`, `vocab_recall_enabled`, `grammar_cards_enabled` |
| 표시 | 앞면 후리가나 | 스위치 | 끔 | `front_furigana` |
| | 본문 후리가나 | 스위치 | 켬 | `body_furigana` |
| | 테마 | 시스템 / 라이트 / 다크 | 시스템 | `theme` |
| | 청해 대본 먼저 보기(청해 콘텐츠가 번들에 있을 때만) | 스위치 | 끔 | `listening_script_first` |
| 정보 | 출처와 라이선스 | 행 → S09 | | |
| | 앱 버전 · 콘텐츠 판 | 표시만(콘텐츠 판 = `contentVersion`, S09와 같은 데이터) | | |

- 행 최소 높이 56 dp. 스위치 행은 행 전체가 `toggleable`.
- 카드 종류를 모두 끄면 저장하지 않고 "카드 종류를 하나 이상 켜세요".
- **끈 카드 종류·뺀 급수의 정책**: 새 카드뿐 아니라 이미 시작한 카드의 복습도 멈춘다(`dueForSession`이 빼므로 홈 수치·세션 대기열에 함께 빠진다, 6.2). 학습 기록은 지우지 않고, 다시 켜거나 급수를 다시 고르면 밀린 복습부터 이어진다. 화면 문구: 카드 종류 묶음 아래 bodySmall onSurfaceVariant "끈 종류는 새 카드와 복습이 모두 멈춥니다. 학습 기록은 남고, 다시 켜면 이어집니다.", `LevelPicker` 시트(설정에서 열 때만) 아래 "고르지 않은 급수의 복습은 멈춥니다. 학습 기록은 남습니다."
- 라우트 `Settings(openLevelPicker: Boolean = false)`(architecture.md 7절): 참이면 설정 탭이 열린 뒤 학습 급수 하단 시트를 바로 띄운다(S02 "바꾸기"·`NO_LEVEL_SELECTED`·`NO_CONTENT_FOR_LEVELS`·`ALL_DONE` "급수 추가"). 시트를 닫으면 포커스는 "학습 급수" 행에 둔다.
- 목표 기억률(`desired_retention`)은 화면에 두지 않는다(UI-U06 A 추천: 초보 학습자에게 뜻이 어렵고, 잘못 바꾸면 복습량이 급격히 바뀐다). 저장 열은 기본 0.9로 남고 `SettingsUiState`에 없다. UI-U06에서 B가 승인되면 "학습" 묶음에 단계 조절 행(0.70~0.97, 0.01 단위)을 더한다.
- 화면에 없는 값(범위 밖 수)은 저장하지 않는다. 범위는 위 표가 기준이다.

### 6.9 S10·S11 문법(2순위)

- S10: 급수별 묶음 머리(N5…), 행: 문법 제목(jpTitle 축소판 = jpBody 굵기 500) + 한국어 대응 표현(bodyMedium).
- S11: 제목(jpTitle) → "한국어로는"(대응 표현) → "접속"(표: 품사 `connections[].posKo`(ko, bodyMedium) / 형태 `form`(ja, jpBody) / 예 `example`(ja, jpBody + 후리가나), architecture.md 9.1 `connections_json` 3필드) → 설명(bodyLarge, 일본어 구간 로캘) → "예문"(3개 이상: jpBodyLarge + 후리가나, 아래 번역 bodyMedium, 출처 줄) → "비슷한 문법"(architecture.md 9.1 `contrasts_json`, 항목마다 카드 하나: 비슷한 문법 제목(jpBody 굵기 500, 누르면 그 문법의 S11) 아래 이 문법 예문 / 그 문법 예문 두 줄(각 jpBodyLarge + 후리가나, 줄 앞 labelMedium "이 문법"/"비교" 라벨로 구분 — 색만으로 구분하지 않음), 각 예문 아래 번역 bodyMedium. `contrasts_json`이 비었거나 링크한 문법이 `retired`면 그 항목을, 전부 없으면 절째 숨긴다) → `ProvenanceChip`(설명 작성 방법. 입력은 grammar 행의 `ko_*` 열로 만든 `MeaningUi`, architecture.md 9.1). grammar 행 `needs_review = 1`이면 제목 아래 칩 "설명 검토 중"(같은 칩 모양, architecture.md 9.1).
- 접속 표는 폭이 모자라면(글꼴 200%) 표를 세로 카드 목록으로 바꾼다(행마다 "품사 / 형태 / 예").

#### 6.9.1 문법 카드(`gr`, M17, 초안 — 착수 때 확정)

- 앞면: 카드 종류 라벨 "문법", 급수 칩. 학습 대상 = 문법 형식(`title`, jpTitle 24 sp를 플래시카드에서는 jpHeadword 45 sp로 키움, 40% 지점) 아래 예문 하나(`example`, `examples[0].ja`를 루비 구간으로, jpBodyLarge, 문법 형식 부분 = 예문의 `[[…]]` 구간(`RubySegment.emphasis`, 4.4)은 밑줄 2 dp + 굵기 500, 앞면 후리가나 설정을 따름). 예문 번역은 앞면에 없다.
- 뒷면: 문법 형식·예문 제자리(5.1 위치 규칙) 아래로 한국어 대응 표현(`meaningKo`, bodyLarge) → 예문 번역(`exampleKo`, bodyMedium) → 설명 요약(`summaryKo` = `explanation_ko` 첫 문단, bodyMedium, 일본어 구간 4.4, 최대 4줄 뒤 "자세히" → S11) → `ProvenanceChip`(`provenance`, 설명 작성 방법). `needsReview`가 참이면 그 옆에 "설명 검토 중" 칩(6.9와 같음).
- 평가 줄·진행·저장 상태는 단어 카드와 같다.
- TalkBack: 앞면 "문법 카드, 앞면. [ja]{형식}[/ja]. 예문 [ja]{예문}[/ja]. 남은 {n}장"(4.3 방법), 뒷면 "뒷면. 뜻 {meaningKo}. 예문 뜻 {번역}. {칩 문구}".
- 상태 모델: architecture.md 7절 `ReviewCardUi.Grammar(cardId, level, title, example: List<RubySegment>, exampleKo, meaningKo, summaryKo, provenance: MeaningUi, needsReview)`. 칩 입력은 grammar 행 `ko_*` 열(9.1), "설명 검토 중"은 `needs_review`(9.1).
- 스크린샷 `review_front_grammar`, `review_back_grammar`.

### 6.10 S12~S15 연습·모의고사(3순위)

- S12: "유형별 연습"(영역 → 유형 목록. 유형 이름은 `jlpt-content-authoring` 5절: 문자·어휘 — 한자 읽기, 표기, 단어 형성, 문맥 규정, 유의 표현, 용법 / 문법 — 문법 형식 판단, 문장 배열, 글의 문법 / 독해 — 단문, 중문, 장문, 통합 이해, 주장 이해, 정보 검색 / 청해 — 과제 이해, 포인트 이해, 개요 이해, 발화 표현, 즉시 응답, 통합 이해), "모의고사"(급수 선택), 진행 중인 모의고사가 있으면 맨 위 카드 "이어서 풀기"(7절). 화면 아래 고지 한 줄(N-11 요약): "모든 문제는 이 앱이 직접 만든 것으로 실제 시험 문항이 아닙니다." 콘텐츠가 있는 유형만 보인다.
- S13 문제 풀이: 진행 표시 → 지시문(bodyLarge, 한국어 + 일본어 구간) → 지문(`stem`·`passage`, jpBodyLarge, 대상 부분 = `[[…]]` 구간(`RubySegment.emphasis`, 4.4)은 밑줄 2 dp + 굵기 500, 색만으로 표시하지 않음) → 선택지 4개(5.4) → 고르면 피드백(5.5) + 하단 "다음"(56 dp).
- 지시문(앱이 쓴 한국어 문구, 시험지 문구를 옮기지 않는다). `{대상}`은 문항의 `target` 필드(architecture.md 9.2, 일본어 문자열, 루비 없음)로 채운다. `targets`(관련 어휘·문법 ID, S15 "관련 단어·문법 보기")와 다른 필드다. `{대상}`이 있는 유형(한자 읽기·표기·유의 표현·용법)은 `target`이 반드시 있어야 하고, 지문에 `[[…]]`이 한 번 있어야 한다(용법은 지문이 없으므로 `target`만):

| 유형 | 지시문 |
|------|--------|
| 한자 읽기 | "밑줄 친 [ja]{대상}[/ja]의 읽기로 알맞은 것을 고르세요." |
| 표기 | "밑줄 친 [ja]{대상}[/ja]의 알맞은 표기를 고르세요." |
| 단어 형성 | "빈칸에 붙여 말을 만들 때 알맞은 것을 고르세요." |
| 문맥 규정 | "빈칸에 들어갈 말로 알맞은 것을 고르세요." |
| 유의 표현 | "밑줄 친 말 [ja]{대상}[/ja] — 뜻이 가장 가까운 것을 고르세요." |
| 용법 | "[ja]{대상}[/ja] — 이 말이 바르게 쓰인 문장을 고르세요." |
| 문법 형식 판단 | "빈칸에 들어갈 알맞은 표현을 고르세요." |
| 문장 배열 | "네 조각을 바른 순서로 놓았을 때 굵은 테두리 칸에 들어갈 것을 고르세요." — 지문에 빈칸 넷(그중 하나만 굵은 테두리 2 dp + 칸 아래 "이 칸" labelSmall, 색만으로 표시하지 않음), 아래 선택지 4개(5.4). 채점은 다른 유형과 같은 4지선다(`choices` 4개, `answerIndex`, architecture.md 9.2 형식 그대로). 조각을 끌거나 순서대로 놓는 조작은 두지 않는다 |
| 글의 문법·독해 | 지문 카드(surfaceContainerLow, 세로 스크롤) 위, 문항은 아래. 지문이 화면 절반을 넘으면 "지문 보기/문제 보기" 탭으로 나눈다 |

  일본어 대상 바로 뒤에 받침에 따라 바뀌는 조사(이/가, 과/와, 을/를)를 붙이지 않는다. 받침을 판단할 수 없으므로 "의"만 쓰거나 대시로 끊는다.
- S13 이탈: 상단 close 또는 시스템 뒤로 → **확인 없이** S12로. 고른 답은 고를 때마다 이미 저장되어 있다. 연습은 재개하지 않는다: 나갈 때 그 시도에 `finished_at`을 기록해 "이어서 풀기"(모의고사 전용, `exam_id`가 있는 미완료 시도만)에 잡히지 않게 한다.
- S14 모의고사: S13과 같지만 피드백 없이 선택만 기록, 선택지 위에 문항 번호 이동 줄(가로 스크롤 칩, 답한 문항은 채움 + 체크 아이콘, 현재 문항은 굵은 테두리 2 dp + 아래 막대 — 색만으로 구분하지 않는다. 칩 최소 48 dp, 숫자는 `tnum`, 칩 높이는 최소값). 상단 경과 시간. 시작 화면에 "문항 수·시간·배점은 실제 시험과 다를 수 있습니다." 이탈은 확인한다(D10 예외, 7절).
- S15 결과: 영역별 원점수 "문자·어휘 18/25 (72%)"(`tnum`), 막대. **합격 여부·척도 점수를 보이지 않는다.** 결과 아래 고정 문구 "원점수입니다. 실제 시험의 척도 점수나 합격 여부와 다릅니다." 틀린 문항 목록 → 해설(5.5 형식, 정답·내 답 표시, 해설 카드의 `ProvenanceChip`·"해설 검토 중" 칩 포함), 문항마다 "관련 단어·문법 보기"(콘텐츠의 `targets`).

### 6.11 S16 청해(4순위)

- 위에서부터: 진행 표시 → 질문(bodyLarge, 한국어 지시 + 일본어 질문은 jpBodyLarge) → `PlaybackControl` → 선택지(5.4) → 답한 뒤 해설(5.5 해설 카드, `ProvenanceChip` 입력은 청해 행 `ko_*`, architecture.md 9.3) + "대본 보기"(접힘, 화자 표시 "M:"/"F:"/"내레이션"을 굵기 500으로, 대본은 jpBodyLarge + 후리가나).
- 대본은 답하기 전에는 접혀 있다(청해는 듣기 연습이 본질이므로 기본값으로는 먼저 보이지 않는다). 예외: (1) 설정 "청해 대본 먼저 보기"(`listening_script_first`, 기본 끔 — 소리를 듣기 어려운 사용자용)가 켜져 있으면 처음부터 펼친다. (2) TTS 엔진이 없거나 음성 파일이 없으면 답하기 전에도 펼친다(5.8). 어느 경우든 결과에 "대본으로 풂" 표시를 남겨 듣고 푼 결과와 구분한다.
- 화면이 열려도 음성을 자동 재생하지 않는다(5.8).
- 화면 아래 음성 고지 한 줄(N-12, 9.2의 음성 고지와 같은 데이터).

### 6.12 S09 출처와 라이선스 — 9절

## 7. 화면 × 상태 표(D9)

모든 상태 화면은 5.10 `StatusScreen` 틀이다. "행동"의 첫 버튼이 주 버튼.

**공통 로딩 규칙**(표에 따로 적지 않은 화면 모두): 300 ms 안에 끝나면 아무것도 보이지 않는다. 300 ms를 넘으면 그 화면 모양의 자리표시(surfaceContainerHighest 막대, 움직임 없음): 상세(S06·S07·S11)는 머리 + 절 2개, 목록(S05·S10·S12)은 행 5개, 설정(S08)은 행 6개, S09는 카드 3개. 문구·행동 없음, 상단 뒤로는 동작. TalkBack은 자리표시 묶음 하나에 "불러오는 중".


| 화면 | 상태 | 조건 | 제목 / 문구 | 행동 |
|------|------|------|-------------|------|
| 전체(S90) | 콘텐츠 DB 열기 실패 | 번들 DB 복사·열기·스키마 확인 실패 | "학습 콘텐츠를 열지 못했습니다" / "저장 공간이 부족하거나 설치 파일이 손상되었을 수 있습니다. 학습 기록은 지워지지 않았습니다." | "다시 시도", "앱 정보 열기"(시스템 앱 정보 화면), "출처 보기"(S09는 별도 에셋이라 열린다) |
| 전체(S90) | 학습 기록 DB 열기 실패 | Room 열기·마이그레이션 실패 | "학습 기록을 열지 못했습니다" / "앱을 다시 시작해 보세요. 문제가 계속되면 앱 업데이트를 확인하세요." | "다시 시도", "앱 정보 열기". 기록 삭제 버튼을 두지 않는다 |
| 전체(Opening) | DB 여는 중(`AppState.Opening`) | 앱 시작 때 두 DB를 여는 중(첫 실행이면 콘텐츠 DB 에셋 복사 포함, architecture.md 4.5). 라우트가 정해지기 전이다 | 300 ms 안에 끝나면 surface 빈 화면(첫 프레임 전은 시스템 스플래시). 300 ms를 넘으면 `StatusScreen`: 가운데 원형 진행 표시(48 dp, primary, 아이콘 자리), 제목 "학습 콘텐츠를 준비하고 있습니다", 문구 "처음 실행할 때 잠시 걸릴 수 있습니다." TalkBack은 제목에 포커스(5.10) | 없음(뒤로 = 앱 종료). 끝나면 `Ready`면 시작 라우트, 실패면 아래 S90 |
| S01 | 개수 계산 중(`PREPARING`) | 급수별 단어·한자 수(`levelCounts()`)를 세는 중. 에셋 복사는 Opening에서 이미 끝났다 | 급수 행 개수 자리에 자리표시 막대(300 ms 넘을 때만) | "시작" 비활성 |
| S01 | 급수 미선택 | 체크 0개 | (화면 그대로) "급수를 하나 이상 고르세요" | "시작" 비활성 |
| S01 | 콘텐츠 있는 급수 없음(`NO_CONTENT`) | 번들 콘텐츠 0행(빌드 결함) | "학습할 콘텐츠가 없습니다" / "앱에 포함된 콘텐츠를 찾지 못했습니다. 앱을 업데이트해 주세요." | "앱 정보 열기" |
| S02 | 로딩(`LOADING`) | 대기열 계산 중(300 ms 넘을 때만 표시) | 요약 카드 자리표시 막대 | 없음 |
| S02 | 학습 가능(`READY`) | `dueCount` > 0 또는 `newTodayCount` > 0 | 6.2 | "학습 시작" |
| S02 | 샘플 콘텐츠 | `isSampleContent`(다른 모든 S02 상태와 함께 나타남) | 6.2 샘플 띠 | 없음 |
| S02 | 첫 안내 | `showFirstNotice`(다른 상태와 함께) | 6.2 첫 안내 카드 | "자세히", "닫기" |
| S02 | 급수 미선택(`NO_LEVEL_SELECTED`) | 저장된 `target_levels`가 비었거나 1..5 밖 값뿐(손상된 복원 값, 방어용 — architecture.md 5절. 설정·S01은 급수 0개를 저장하지 않는다) | "학습할 급수를 골라 주세요" / "급수를 고르면 오늘의 카드가 만들어집니다." | "급수 고르기"(`Settings(openLevelPicker = true)`, 6.8) |
| S02 | 선택 급수에 콘텐츠 없음(`NO_CONTENT_FOR_LEVELS`) | 고른 급수가 모두 "준비 중"(콘텐츠 업데이트로 사라진 경우 포함) | "고른 급수의 콘텐츠가 아직 없습니다" / "다른 급수를 고르거나 앱 업데이트를 기다려 주세요." | "급수 바꾸기"(`Settings(openLevelPicker = true)`) |
| S02 | 오늘 복습 완료(`ALL_DONE`) | 복습 0, 오늘 새 카드 0, 남은 새 카드 없음(급수 콘텐츠를 모두 시작함) | "오늘 복습을 모두 마쳤어요" / "다음 복습: {날짜·시각}에 {n}장"(`nextDueAtEpochMs`, `nextDueCount`, 하루 시작 시각 기준) | "급수 추가"(`Settings(openLevelPicker = true)`), 보조 "사전 보기" |
| S02 | 새 카드 한도 도달(`NEW_LIMIT_REACHED`) | 복습 0, 오늘 새 카드 한도(설정값 + 오늘 추가분)를 다 씀, 남은 새 카드 있음 | "오늘 새 카드 {newLimit}장을 모두 학습했어요" / "다음 복습: {날짜·시각}에 {n}장" | "새 카드 5장 더"(오늘만 한도 +5, 설정값은 그대로. `extra_new_cards_study_day` = 오늘 학습일, `extra_new_cards` += 5로 저장 — 앱을 다시 켜도 같은 날이면 유지, 날이 바뀌면 0), 보조 "한도 바꾸기"(S08) |
| S03 | 로딩 | 대기열 불러오는 중 | 카드 자리에 원형 진행 표시(300 ms 뒤) | 상단 닫기는 동작 |
| S03 | 앞면 / 뒷면 | 6.3 | | "답 보기" / 평가 |
| S03 | 저장 중 | 평가 저장 300 ms 초과 | 평가 줄 비활성 + 얇은 진행 막대 | 없음 |
| S03 | 저장 실패 | `applyReview`가 예외를 던짐 | 카드·미리보기는 그대로(다음으로 넘어가지 않음, 대기열 진행 없음), `saveFailed = 누른 평가`, 스낵바 "평가를 저장하지 못했습니다". 평가 줄은 다시 누를 수 있다(다른 평가로 고쳐 눌러도 된다) | 스낵바 "다시 시도"(`saveFailed`의 평가로, 처음 계산한 `ScheduleResult`를 다시 저장, 시간 제한 없음 `SnackbarDuration.Indefinite`) |
| S03 | 대기열 빔(`EMPTY`) | 세션을 열었는데 카드 0(다른 기기 시각 변경 등). `emptyReason`은 `ALL_DONE`/`NEW_LIMIT_REACHED` | S02의 "오늘 복습 완료"/"한도 도달"과 같은 문구 | "홈으로" |
| S03 | 콘텐츠 사라진 카드 | 카드 ID가 콘텐츠에서 `retired` | 화면에 보이지 않는다(대기열에서 제외, 학습 기록은 지우지 않음). 홈 `dueCount`·`nextDueCount`에도 들어가지 않는다(같은 `dueForSession`, 6.2). 끈 카드 종류·뺀 급수의 카드도 같다(6.8) | - |
| S04 | 결과 | 6.4 | | "홈으로" |
| S05 | 로딩 | 300 ms 초과 | 목록 자리표시 행 5개 | - |
| S05 | 검색 결과 없음 | 검색어에 맞는 행 0 | "'{검색어}'에 맞는 단어가 없습니다"(검색어는 `ja-JP`) / "급수 필터를 넓히거나 다른 표기로 찾아 보세요." | "필터 지우기" |
| S05 | 급수 필터 결과 없음 | 고른 급수 콘텐츠 0 | "이 급수의 콘텐츠가 아직 없습니다" | "필터 지우기" |
| S06·S07 | 로딩 | 공통 로딩 규칙 | 자리표시 | 상단 뒤로 |
| S06·S07 | 정상(`DetailStatus.LOADED`) | 6.6·6.7 | | |
| S06·S07 | 항목 없음(`DetailStatus.RETIRED`) | 링크한 ID가 콘텐츠에 없음(`retired`) | "이 항목은 콘텐츠에서 빠졌습니다" / "콘텐츠 업데이트로 삭제되었습니다." | "뒤로" |
| S07 | 한국어 음 없음 / 필순 없음 | 데이터 없음 | 해당 행·절을 숨긴다 | - |
| S08 | 로딩 | 공통 로딩 규칙 | 자리표시 | - |
| S08 | 정상 | 6.8 | | |
| S08 | 저장 실패 | 설정 저장 실패 | 스낵바 "설정을 저장하지 못했습니다", 스위치·값은 저장 전 값으로 되돌린다 | "다시 시도" |
| S09 | 로딩 | 공통 로딩 규칙 | 자리표시 | 상단 뒤로 |
| S09 | 정상 | 9절 | | |
| S09 | 출처 데이터 읽기 실패(`AboutStatus.FAILED`) | 저작자 표시 에셋을 읽지 못함 | "출처 정보를 읽지 못했습니다" / "앱을 다시 설치하거나 업데이트해 주세요." + 그 아래 9.4의 최소 표시 문단 그대로(다른 문구를 따로 두지 않는다) | "다시 시도" |
| S09 | 링크를 열 앱 없음 | `ACTION_VIEW`를 처리할 앱 없음 | 스낵바 "링크를 열 앱이 없습니다" | 스낵바 "주소 복사" |
| S10·S11 | 로딩 | 공통 로딩 규칙 | 자리표시 | 상단 뒤로 |
| S10·S11 | 정상 | 6.9 | | |
| S10 | 급수에 문법 없음 | 고른 급수의 문법 0 | "이 급수의 문법은 아직 준비 중입니다" | "다른 급수 보기" |
| S11 | 항목 없음 | 링크한 ID가 `retired` | S06·S07 "항목 없음"과 같음 | "뒤로" |
| S12 | 진행 중인 모의고사 있음 | 저장된 미완료 시도 중 `exam_id`가 있는 것 | 맨 위 카드 "N4 모의고사 · {k}/{전체} 문항 · {경과}" | "이어서 풀기", 보조 "포기하기"(확인 대화상자 "기록을 지우고 포기할까요?" — 데이터를 지우므로 확인) |
| S12 | 유형에 문항 없음 | | 그 유형을 목록에서 숨긴다. 모든 유형이 없으면 "아직 문제가 없습니다" | "홈으로" |
| S13 | 문항 | 문항 표시 | 6.10 | 선택지 |
| S13 | 피드백 | 선택 직후 | 5.5 | "다음" |
| S13 | 끝 | 마지막 문항 뒤 "다음" | S15 | |
| S13 | 이탈 | 뒤로·닫기 | 확인 없이 S12로(답은 이미 저장됨, 시도에 `finished_at` 기록, 재개 없음 — 6.10) | - |
| S14 | 진행 중 이탈 | 뒤로·닫기 | 대화상자 "모의고사를 멈출까요?" / "지금까지 고른 답과 시간이 저장되고, 연습 화면에서 이어서 풀 수 있습니다." | "계속 풀기"(기본 포커스), "멈추고 나가기" |
| S14 | 앱이 백그라운드로 감 | 홈 키, 전화 | 확인 없이 저장하고 시간 멈춤(UI-U05 A) | - |
| S14 | 재개 | S12 "이어서 풀기" | 마지막으로 보던 문항, 시간 이어서 | - |
| S14 | 제출 전 미응답 | "제출" 시 미응답 문항 있음 | 대화상자 "답하지 않은 문항이 {n}개 있습니다" | "돌아가서 풀기", "그대로 제출" |
| S15 | 결과 | 6.10 | | "틀린 문제 보기", "연습으로" |
| S16 | `Initializing` / `Ready` / `Playing` / `MissingJapaneseData` / `NoEngine` / `AudioFileMissing`(D-04 B 구현 이후) / `Error` | 5.8 표 | 5.8 표 | 5.8 표 |

## 8. 검수 상태 알림(D-03 수용) 정리

- 단위: 뜻 하나(행). 칩 규칙은 5.7.
- 화면 세 곳에서 알린다: (1) 뜻·설명·해설 옆 `ProvenanceChip`(카드 뒷면, 상세, 문법 S11, 문제 해설 카드 5.5 — S13·S15·S16), (2) S02 첫 안내 카드, (3) S09 고지의 집계.
- S09 집계(앱이 DB에서 세는 유일한 값 `ReviewCounts`, `ContentRepository.meaningReviewCounts()`, 학습 급수가 아니라 번들된 전체 기준. 줄의 틀은 앱 문자열 리소스): "한국어 뜻 {total}개 — 사람 검수 {human} · AI 검수 {agent} · 표본 검수 묶음 {sampled} · 검수 전 {unreviewed} · 검수자 미기록 {reviewerUnrecorded}". 0인 항목도 0으로 보인다.
- D-03에서 어느 선택지(전수/표본/혼합, 에이전트/사람)를 고르든 화면 규칙은 바뀌지 않는다. 바뀌는 것은 데이터 값과 S09의 "검수 방식" 문장(9.2 고지 2)뿐이다.
- `ko_reviewer_kind`가 NULL이면(D-03에서 기록하지 않기로 하면) 그 `reviewed` 뜻은 "검수자 미기록"으로 보인다. 사람 검수 완료는 `ko_reviewer_kind = human`일 때만 보인다.

## 9. 출처와 라이선스 화면(S09, D11)

### 9.1 위치와 원칙

- 설정 → "출처와 라이선스"(한 번 이동), 홈 상단 메뉴 → "정보"(두 번 이동). 메뉴에서 들어가는 별도 화면이다(S-01.attribution-place, N-06). 시작 화면이나 첫 안내 카드는 이것을 대신하지 않는다.
- 화면 문구는 파이프라인이 매니페스트에서 만든 **저작자 표시 에셋**에서만 읽는다(`compose-app-dev` 9절). 아래 9.2·9.3의 문구는 그 에셋이 담아야 할 내용의 초안이며, 화면 코드에 직접 쓰지 않는다. 예외는 9.4의 최소 표시뿐이다.
- 매니페스트 `status`가 `confirmed`가 아닌 자료가 에셋에 들어오면 화면은 그대로 그리되, 그 판정은 빌드·QA가 막는다(화면이 판단하지 않는다).
- URL은 글자로 보이고(링크를 열지 못해도 주소가 남는다), 누르면 `ACTION_VIEW`, 길게 누르거나 옆 content_copy 버튼으로 복사. 앱에 인터넷 권한은 없다(브라우저가 연다).
- 영어 원문(EDRDG 표시 문구 등)은 번역하지 않고 `en` 로캘로 그대로 보이며, 아래에 한국어 풀이를 bodySmall로 덧붙일 수 있다(풀이는 원문을 대신하지 않는다).

### 9.2 구성(위에서부터)

1. **고지**(카드 묶음, 각 카드: 제목 titleMedium + 본문 bodyMedium). `attribution.json`의 `notices[]`(항목 형식 `{id, title, body}`, architecture.md 6.6)를 받은 순서대로 보인다. 아래 표의 "보일 조건"에 따라 넣을지는 파이프라인이 빌드 때 판정하고, 본문의 자리(`{levelListName}` 등)도 파이프라인이 채워 완성한다. 앱은 문장을 조립하지 않는다. 콘텐츠 DB 라이선스(N-08)는 고지가 아니라 2번 카드에만 있다(`notices[]`에 넣지 않는다).

| # | 근거 | 제목 | 본문 초안 | 보일 조건 |
|---|------|------|-----------|-----------|
| 1 | N-09, S-00.official, D-06, architecture.md 4.3 | JLPT 공식 자료가 아닙니다 | "이 앱의 JLPT 급수 분류는 공식 자료가 아니라 커뮤니티 목록 「{levelListName}」에 근거한 추정입니다. 한자의 급수는 그 한자가 쓰인 단어 중 가장 쉬운 급수로 정했습니다(화면 표시 'N5 단어에 쓰인 한자'). JLPT 주최 측은 2010년 개정 이후 어휘·한자·문법 목록을 공개하지 않습니다. 이 앱은 JLPT 주최 측과 관련이 없습니다." | 항상 |
| 2 | N-10, K-04, D-02, D-03 | 한국어 뜻을 만든 방법 | `ko_source`별 문장 중 **DB에 있는 종류만** 이어 붙인다: `jmdict-translated`가 있으면 "한국어 뜻은 JMdict(EDRDG)의 영어 뜻을 바탕으로 한국어로 번역·편집했습니다." + `{modelNames}`가 비어 있지 않으면 "번역에는 AI({modelNames})를 썼습니다." / `authored`가 있으면 (번역 문장이 있으면 "일부 뜻은", 없으면 "한국어 뜻은") "이 앱이 직접 작성했습니다." + 그 `authored` 행의 `ko_method`가 `llm:`이면 "작성에는 AI({modelNames})를 썼습니다."(그 행들의 모델) 그 뒤 "{reviewPolicy} 뜻마다 만든 방법과 검수 상태를 표시합니다." + 8절 집계 줄 | 뜻 행이 있을 때(언제나) |
| 3 | N-11 | 문제와 설명은 직접 만들었습니다 | "문제·문법 설명·해설은 이 앱이 직접 작성했으며 실제 JLPT 시험 문항이 아닙니다. 모의고사의 문항 수·시간·배점은 실제 시험과 다를 수 있고, 결과는 원점수이며 합격 여부를 예측하지 않습니다." | 문법 또는 문항이 번들에 있을 때 |
| 4 | N-12, D-04 | 청해 음성 | D-04 A: "청해 음성은 이 기기의 음성 합성(TTS) 엔진이 대본을 읽은 것입니다. 기기와 엔진에 따라 발음·억양이 다를 수 있고, 실제 시험 음성이 아닙니다." / D-04 B: "청해 음성은 {engineName}으로 미리 합성한 것입니다. {engineCredit}" / C: 두 문장 중 해당하는 것 | 청해 콘텐츠가 번들에 있을 때 |

- 아래 자리는 모두 파이프라인이 빌드 때 채운다(architecture.md 6.6). 채우지 않은 `{…}`가 남으면 6.5 검사가 실패한다.
- `{levelListName}`: D-06에서 고른 목록 이름(예: 자료 이름과 작성자). 값이 없으면 자료 ID를 그대로 넣는다(빈칸을 두지 않는다).
- `{modelNames}`: 파이프라인이 DB 뜻 행의 `ko_method`(`llm:<모델>@<날짜>`)에서 모아 `attribution.json` `modelNames`에 넣은 값. 비어 있으면 그 문장을 뺀다. `{reviewPolicy}`: D-03 결정 문장(예: "N5 뜻은 AI가 전수 검수했고 사람 검수는 하지 않았습니다."). D-03 결정 전에는 "검수 방식은 정해지지 않았습니다."
- "DB에 있는 `ko_source` 종류"도 파이프라인이 빌드 때 판정해 본문을 완성한다. 샘플 DB(`authored`만)에서는 번역 문장이 빠져 "한국어 뜻은 이 앱이 직접 작성했습니다."가 된다. S02 첫 안내 카드(6.2, `firstNotice`)도 파이프라인이 같은 조건으로 고른다.
- 앱이 DB에서 세는 것은 고지 2 카드 아래 붙이는 집계 줄(`ReviewCounts`, 8절) 하나뿐이다.

2. **콘텐츠 DB 라이선스**(N-08, D-01) — 따로 카드 하나: "학습 콘텐츠 DB: {dbLicense}" + 링크 `{dbLicenseUrl}`(초안 값 CC BY-SA 4.0, https://creativecommons.org/licenses/by-sa/4.0/ — 버전은 L-01.version 제안) + "원 자료와 변경 내용은 아래에 있습니다." + "콘텐츠 판: {contentVersion}"(architecture.md 4.2·6.6의 입력 해시 값, 빌드 시각이 아니다). 앱 코드 라이선스와 섞지 않는다.

3. **원 자료**(N-07) — 앱이 쓰는 자료마다 카드. 각 카드 필드(매니페스트에서):

| 표시 | 매니페스트 필드 | 비고 |
|------|-----------------|------|
| 자료 이름 | `name` | titleMedium |
| 저작자 표시 문구 | `attribution` | 원문 그대로, `en` |
| 라이선스 | `license` + `licenseUrl` | 링크 |
| 앱이 바꾼 것 | `modifications` | 한국어 |
| 판 | `version` | `tnum` |
| 홈페이지 | `homepage` | 링크 |
| 링크 | `links[]`(문서·라이선스 링크) | 있는 링크만 |
| 추가 표시 | `extraCredits[]`(`{text, url?}`) | `text`를 원문 그대로(한글이 있으면 `ko-KR`, 없으면 `en`), `url`이 있으면 링크 |

   - JMdict(S-01)·KANJIDIC2(S-02): EDRDG 문구(S-01.attribution, KANJIDIC 포함) + 링크 https://www.edrdg.org/edrdg/licence.html 과 문서 사본(S-01.attribution-links 제안: https://www.edrdg.org/wiki/JMdict-EDICT_Dictionary_Project.html , https://www.edrdg.org/wiki/KANJIDIC_Project.html). 두 자료가 같은 문구를 쓰면 카드는 둘로 두되 문구는 한 번만 보여도 된다(구현 선택).
   - KANJIDIC2 카드 `extraCredits`: 매니페스트 S-02 `extraCredits[].text`를 그대로 보인다(2026-11-02 매니페스트 값 "한국어 음: Charles Muller 제공(KANJIDIC2 경유)". 문구를 바꾸려면 analyst가 매니페스트를 고친다). 한국어 음 크레딧은 이 자리 하나에만 둔다(고지 `notices[]`·한자 상세에 넣지 않는다).
   - KanjiVG(S-03): 필순을 쓸 때만 카드가 생긴다. 문구 S-03.attribution.
   - Tatoeba(S-04): 예문을 쓸 때만. 카드에 라이선스 두 개(CC BY 2.0 FR 링크, CC0 1.0 링크)와 "예문 작성자 보기" 행 → 하위 목록(작성자 이름, 문장 ID, 라이선스, `LazyColumn`). 예문이 나오는 자리(5.11)에도 작성자를 보인다.
   - 급수 목록 자료(D-06에서 고른 것, S-05~S-09 중): 카드 하나 + 그것이 파생한 원 목록(예: S-06이면 S-05 Waller 목록, "credit my site" 요구에 따라 사이트 링크)도 `extraCredits`로.

4. **글꼴**(S-10, N-14) — 글꼴을 번들할 때만 카드(이름, 라이선스, 링크). 기기 글꼴을 쓰는 기본안에서는 없다.
5. **아이콘**(UI-U04) — Material Symbols 카드(라이선스 이름·링크·고지 전문 보기). S-11이 `confirmed`가 된 뒤 그 기록으로 `assets/licenses/`에 둔 고지 파일에서 읽는다(아이콘은 콘텐츠 DB에 없으므로 `attribution.json` `sources`가 아니라 py-fsrs와 같은 경로).
6. **앱과 오픈소스 라이브러리** — "앱 소스 코드 라이선스: {appCodeLicense}"(D-05 미정이면 이 행을 숨긴다), "오픈소스 라이브러리 라이선스" 행 → `assets/licenses/`(손으로 커밋, architecture.md 6.6)의 목록: `py-fsrs.txt`(MIT), `apache-2.0.txt`(AndroidX·Kotlin 등과 Material Symbols 공용), `libraries.json`의 `distributed: true` 항목.

### 9.3 배치·접근성

- `LazyColumn`, 카드 사이 12 dp, 절 머리(titleSmall, onSurfaceVariant) "고지" / "학습 콘텐츠 DB" / "원 자료" / "그 밖의 라이선스".
- 링크는 primary 색 + 밑줄(색만으로 링크를 나타내지 않는다), 터치 영역 48 dp 높이. TalkBack에서 `role = Button`이 아니라 링크(`LinkAnnotation.Url`)로 노출하고 라벨은 "{자료 이름} 라이선스 링크, {URL}".
- 긴 URL은 아무 데서나 줄바꿈되게 한다(`/` 뒤에 줄바꿈 기회). 잘라서 말줄임하지 않는다.
- 글꼴 200%에서도 모든 문구가 보인다(카드 높이 제한 없음).

### 9.4 에셋을 읽지 못할 때의 최소 표시

에셋이 깨져도 EDRDG 등 표시 의무를 지키기 위해 문자열 리소스에 한 문단만 둔다(`R.string.attribution_fallback`): **DB에 쓰인 자료의 `attribution` 원문 + `licenseUrl` + 콘텐츠 DB 라이선스**("콘텐츠 DB: {dbLicense.name} {dbLicense.url}"). 이 문단은 손으로 쓰지 않는다: 파이프라인이 `attribution.json`과 같은 입력으로 `app/src/main/res/values/attribution_fallback.xml`(생성 파일)을 만든다. 그래서 출처가 바뀌면 함께 다시 만들어진다. 샘플 DB에서도 생성되고, 자료가 `confirmed`인지는 `release` 빌드의 6.5 검사가 확인한다(architecture.md 6.6). M7 검사의 grep 범위에 `app/src/main/res`를 넣고 이 생성 파일만 뺀다. 7절 S09 실패 화면도 이 문단을 그대로 보인다.

## 10. 첫 실행과 오프라인

- 인터넷 권한이 없다. 네트워크 상태를 보이지 않는다. "오프라인" 표시를 두지 않는다.
- 첫 실행 순서: Opening(콘텐츠 DB 에셋 복사, 7절) → S01 → S02. 권한 요청 없음. 첫 실행 판정은 `onboarding_done`(기본 0) 하나로 한다(`target_levels` 기본값 '5'로 판정하지 않는다).

## 11. 접근성 요구

### 11.1 TalkBack 문구

`[ja]`가 있는 행은 4.3의 방법(`clearAndSetSemantics { text = AnnotatedString }`, 일본어 구간 `JpSpan`)으로 만든다. `contentDescription`·`onClickLabel`·`stateDescription`에는 일본어를 넣지 않는다.

| 화면·요소 | 라벨 / 알림 | 시점 |
|-----------|-------------|------|
| S03 카드 앞면(단어) | "단어 카드, 앞면. [ja]{표기}[/ja]. 남은 {n}장" | 카드가 나타나면 카드 노드로 **접근성 포커스 이동만**(`liveRegion` 없음 — 이동하면 TalkBack이 노드를 읽으므로 알림을 겹치지 않는다) |
| S03 카드 앞면(한자) | "한자 카드, 앞면. [ja]{한자}[/ja]. 남은 {n}장" | 같음 |
| S03 카드 앞면(회상) | "뜻을 보고 일본어 떠올리기 카드, 앞면. {첫 한국어 뜻}. 남은 {n}장" | 같음 |
| S03 뒤집기 | 뒷면 카드 노드: "뒷면. 읽기 [ja]{읽기}[/ja]. 뜻 {뜻 1}. {칩 문구}" | "답 보기"가 사라지므로 포커스를 **뒷면 카드 노드로 옮긴다**(이동만, `liveRegion` 없음). 읽기 순서: 뒷면 카드 → 평가 줄 첫 버튼("다시") → 나머지 평가 버튼 → 상단 바(`traversalIndex`로 카드 0, 평가 줄 1, 상단 바 2) |
| S03 다음 카드 | 앞면 행과 같음 | 평가가 저장되면 다음 카드 앞면 노드로 포커스 이동(첫 화면 상단으로 튀지 않게) |
| S03 저장 실패 | 스낵바 문구 | 스낵바 기본 알림. 포커스는 누른 평가 버튼에 남긴다 |
| "답 보기" | "답 보기", `onClickLabel` "뒷면 보기" | |
| 평가 버튼 | 5.3 | |
| 되돌리기 | "되돌리기", 비활성 시 "되돌릴 평가 없음" | 되돌리면 되살린 카드 뒷면 노드로 포커스 이동 |
| 학습 끝내기 | "학습 끝내기" | |
| 후리가나 토글 | "후리가나", `stateDescription` "켜짐"/"꺼짐" | |
| 진행 | 포커스하면 "남은 {n}장, 복습 {a}, 새 카드 {b}" | 따로 알리지 않는다(앞면 노드 끝에 남은 수) |
| S02 샘플 띠 | "개발용 샘플 콘텐츠입니다. 실제 학습 데이터가 아닙니다." | 순서상 요약 카드 앞 |
| 선택지(S13·S14·S16) | 묶음 `selectableGroup`, 각 `Role.RadioButton`: "{번호}번, [ja]{선택지}[/ja]" + 채점 뒤 ", 정답" / ", 오답, 내가 고른 답" | 선택 상태는 TalkBack이 "선택됨"을 붙인다 |
| 문제 피드백 | 5.5 | 피드백 노드 `liveRegion = Polite` |
| 지시문 | 4.3 방법, 예: "밑줄 친 [ja]{대상}[/ja]의 읽기로 알맞은 것을 고르세요." | 문항이 바뀌면 지시문 노드로 포커스 이동 |
| 모의고사 번호 이동 칩 | "{k}번 문항, 답함" / "{k}번 문항, 답하지 않음" + 현재면 ", 현재 문항" (`stateDescription` 대신 라벨에, 한국어만) | |
| 모의고사 타이머 | 노드 하나, 1초마다 알리지 않는다(`liveRegion` 없음). 포커스하면 "경과 {m}분 {s}초" | |
| 문장 배열 "이 칸" | 지문 노드 안에서 "빈칸 1, 빈칸 2, 고를 칸, 빈칸 4"처럼 칸 위치를 읽는다(빈칸 모양만으로 전하지 않는다) | |
| S05 검색 결과 수 | 결과 목록 위 노드 "{n}개" / 결과 없음 상태 제목 | 검색이 끝날 때 그 노드 `liveRegion = Polite`(검색란 포커스를 빼앗지 않는다. 결과 없음 `StatusScreen`도 이 경우에는 포커스를 옮기지 않고 알림만) |
| S05 급수 필터 칩 | M3 `FilterChip` 기본(선택 상태 포함) | |
| S05 탭 | M3 `Tab` 기본(`Role.Tab`, 선택됨) | |
| 재생 컨트롤 | 5.8 | |
| 출처·검수 칩 | 5.7 | |
| 필순 | 6.7.1 | |
| 상태 화면 | 제목 + 문구를 한 노드로, 열릴 때 포커스 | |

- 한자 읽기 카드의 알려진 한계: TalkBack이 앞면 한자를 일본어로 읽으면 읽기가 드러난다. 화면을 보지 않는 사용자에게 한자 모양을 전할 방법이 없으므로 이 동작을 받아들인다. 단어 인식 카드(뜻이 정답)에는 영향이 적다.
- 제목 계층: 화면 제목·절 머리에 `heading()`.
- 대화상자는 포커스를 가두고, 닫으면 연 버튼으로 포커스를 돌려준다(M3 `AlertDialog` 기본 동작).
- 실기기 확인(`unverified`, 4.3): 카드 앞면·뒷면 일본어 낭독 언어, 지시문의 일본어 구간, 선택지의 일본어. 카드 세션 마일스톤(M4)과 문제 마일스톤에 각각 넣는다.

### 11.2 글꼴 확대·터치

- 모든 터치 대상 48×48 dp 이상(`minimumInteractiveComponentSize`). 평가 버튼 64 dp, 주 버튼 56 dp, 선택지 56 dp.
- 고정 높이·고정 크기 상자에 글자를 넣지 않는다(최소 크기만: `heightIn(min)`·`defaultMinSize`). 이 문서의 모든 dp 크기 중 글자를 담는 것은 최소값이다: 칩 32 dp(5.7), 번호 표식 28 dp(5.4), 한자 격자 칸 64 dp(6.5), 행 높이 56·64 dp, 평가 버튼 64 dp. 글꼴 200%에서 잘리지 않음을 12절 스크린샷으로 확인.
- 키보드·D-pad 포커스는 3.1.5.
- 글자 자르기·말줄임 허용 자리: S05 목록의 뜻 첫 줄(상세에서 전체 확인 가능)뿐.

## 12. 스크린샷 목록(구현 단계, `ui-design-review` 3절)

이름 `screenshots/{screen}_{state}_{light|dark}[_font200].png`. MVP 최소 목록(각각 라이트·다크):

| screen | state |
|--------|-------|
| level | `loading`, `empty_selection`, `loaded`, `unavailable` |
| home | `loading`, `ready`, `no_level`, `no_content`, `all_done`, `new_limit`, `first_notice`, `sample_banner` |
| review | `front_word`, `back_word`, `front_kanji`, `back_kanji`, `front_recall`, `back_word_long_katakana`, `saving`, `save_error`, `furigana_off` |
| done | `loaded` |
| dict | `words`, `kanji`, `no_result`, `no_content_for_filter` |
| word_detail | `loaded` |
| kanji_detail | `loaded`, `no_korean_reading` |
| settings | `loaded`, `with_grammar`(문법 스위치 보임, M17) |
| attribution | `loaded`, `error` |
| error | `content_db`, `record_db` |
| app | `opening`(Opening 300 ms 초과 상태, 7절) |
| nav_shell | `home`(하단 바가 보이는 S02) |

- `_font200` 추가: `review_back_word`(칩 줄바꿈), `review_back_word_long_katakana`, `review_back_kanji`(평가 줄 2×2 확인), `word_detail_loaded`, `dict_kanji`(격자 칸 높이), `attribution_loaded`, `home_new_limit`, `nav_shell_home`.
- 낮은 높이(640×360 dp 가로) `_land_font200`: `review_front_word`, `review_back_word`(6.3 낮은 높이 배치).
- 2순위 이후(해당 마일스톤에서, 각각 라이트·다크):

| 마일스톤 | 이름 |
|----------|------|
| M12 | `kanji_detail_strokes`(+`_font200`) |
| M14 | `grammar_list_loaded`, `grammar_list_empty`(급수에 문법 없음), `grammar_detail_loaded`(예문 3개·비슷한 문법·"설명 검토 중" 칩이 있는 픽스처, +`_font200` — 접속 표가 세로 카드로 바뀜), `grammar_detail_retired` |
| M17 | `review_front_grammar`, `review_back_grammar`, `settings_with_grammar` |
| 3순위 | `practice_choice`·`practice_feedback`(+`_font200`, 번호 표식·선택지), `mock_exam_nav` |
| M23 | `listening_ready`, `listening_playing`, `listening_no_engine`, `listening_script_open`(대본 펼침), `listening_missing_voice`(`MissingJapaneseData` 설치 안내), `listening_error` |
- 앞·뒷면 위치 검사: `review_front_word`와 `review_back_word`에서 표제어 노드의 y 좌표·글자 크기가 같은지 Compose UI 테스트로 확인한다(5.1). 한자·회상 카드도 같다.
- 픽스처에 반드시 넣을 것: 긴 가타카나 단어, 후리가나가 본문보다 넓은 단어([ja]今日[/ja]), 송리가나가 있는 단어([ja]食べる[/ja], [ja]お茶[/ja]), 한국식·일본식 글자 모양이 다른 한자(直, 骨, 角 — D2 확인용), 검수 칩의 생성 5 × 검수 6 조합 중 화면마다 최소 6가지(5.7), 한국어 음 없는 한자, 일본어 구간이 섞인 한국어 뜻("〜さん보다 정중한 호칭").

## 13. 다른 문서와 맞출 것

### 13.1 UI 상태(architecture.md 7·8절과 대조)

기준(architecture.md 11.1): **상태의 종류**(어떤 경우를 구분해 보이는가)는 이 문서 7절, **타입·상태 이름·필드**는 architecture.md 7·8절. 아래는 7절 상태가 어느 이름에 대응하는지 옮긴 표다. 이름이 다르면 architecture.md를 따른다.

| 화면 | 상태·필드(architecture.md 이름) |
|------|-----------|
| 앱 전체(S90) | `AppState.Opening`(7절 "전체(Opening)" 화면) / `Ready(needsOnboarding)` / `ContentDbFailed` / `RecordDbFailed`. DB 실패는 어느 라우트 위든 S90 전체 화면이다(화면별 `ERROR` 상태는 없다) |
| LevelSelect(S01) | `LevelUiState(status: LevelStatus { PREPARING(급수별 개수 계산 중), READY, NO_CONTENT }, options: List<LevelOption(level, wordCount, kanjiCount, available)>, selected: Set<JlptLevel>, newCardsPerDay)`. "급수 미선택"은 `selected.isEmpty()`로 화면이 판단한다 |
| Home(S02) | `status: HomeStatus`(`LOADING`, `READY`, `NO_LEVEL_SELECTED`, `NO_CONTENT_FOR_LEVELS`, `ALL_DONE`, `NEW_LIMIT_REACHED`), `dueCount`, `newTodayCount`, `newLimit`(설정값 + 오늘 추가분), `targetLevels`, `levelProgress: List<LevelProgress(level, learnedWords, totalWords)>`, `nextDueAtEpochMs`, `nextDueCount`, `isSampleContent`, `showFirstNotice`, `firstNotice: NoticeUi?`. `dueCount`·`nextDueAtEpochMs`·`nextDueCount`는 Review 대기열과 같은 `StudyPlanRepository.dueForSession`(architecture.md 8.2)에서 센다 |
| Review(S03·S04) | `status: ReviewStatus`(`LOADING`, `READY`, `EMPTY`), `emptyReason: HomeStatus?`(`ALL_DONE`/`NEW_LIMIT_REACHED`만), `card: ReviewCardUi?`, `revealed`(false = 앞면), `previews: Map<Rating, IntervalPreview>`(`:core:srs`의 `IntervalPreview(dueAtEpochMs, scheduledDays: Int, stepDuration: Duration?)` — 학습 단계면 `scheduledDays` 0), `saving`(300 ms 넘으면 화면에 표시), `saveFailed: Rating?`(재시도할 평가), `remainingReview`, `remainingNew`, `reviewedInSession`, `newInSession`, `canUndo`, `finished`, `newCardsLeftAfterSession`(finished일 때 S04 "새 카드 {k}장을 더"·"새 카드 5장 더 학습" 판정, 6.4). 대기열 = `dueForSession` + 새 카드. 입력 무시 시각(3.6)은 UiState가 아니라 ViewModel 내부 값이다 |
| ReviewCardUi | `Vocab(cardId, direction, level, written, reading, ruby: List<RubySegment>, pos, meanings: List<MeaningUi>)`, `Kanji(cardId, level, literal, strokeCount, on, kun, koreanReadings, words: List<WordRef>)`, `Grammar(cardId, level, title, example, exampleKo, meaningKo, summaryKo, provenance: MeaningUi, needsReview)`(6.9.1) |
| MeaningUi | `textKo`, `koSource`, `koMethod`, `reviewStatus`, `reviewedAt: String?`, `reviewerKind: String?`(architecture.md 4.2 `ko_*` 열 그대로). 칩 문구 `ProvenanceLabel.of`, 시트 문장 `ProvenanceLabel.sheetText`(5.7) |
| Browse(S05) | `status: BrowseStatus`(`LOADING`, `RESULTS`, `NO_RESULT`, `NO_CONTENT_FOR_FILTER`), `tab`, `levels: Set<JlptLevel>`, `query`, `items`. 빈 결과 구분은 `query.isNotBlank()` |
| VocabDetail / KanjiDetail(S06·S07) | `status: DetailStatus`(`LOADING`, `LOADED`, `RETIRED`). `KanjiDetailUiState.strokes == null`이면 필순 절을 숨긴다. 영어 뜻 필드 없음 |
| Settings(S08) | 라우트 `Settings(openLevelPicker: Boolean)`(6.8). `SettingsUiState`(6.8 표의 열 전부, `loading`, `saveFailed: Boolean`). `desiredRetention` 없음(UI-U06 A) |
| About(S09) | 라우트 `About(anchor: String?)`(5.11 "S09 위치 이동"). `status: AboutStatus`(`LOADING`, `LOADED`, `FAILED`), `contentVersion`, `dbLicense`, `notices: List<NoticeUi(id, title, body)>`(파이프라인이 완성한 그대로), `sources: List<SourceUi>`(`links`, `extraCredits: List<CreditUi(text, url?)>` 포함), `reviewCounts: ReviewCounts?`(앱이 DB에서 세는 유일한 값), `modelNames`(`attribution.json` 그대로), `openSourceLicenses`(`assets/licenses/`, 아이콘 포함) |
| Playback(S16) | `SpeakerState`(`Initializing`, `Ready`, `Playing(utteranceId)`, `MissingJapaneseData`, `NoEngine`, `Error`). 속도는 `Speaker.speak(utteranceId, text, rate)`의 `rate`(5.8). `AudioFileMissing`은 D-04 B 구현이 생길 때 더해진다. 준비 10초 타임아웃은 `Speaker` 구현 책임 |
| MockExam(S14, 3순위) | 진행 중 / 이탈 확인 / 제출 확인(미응답 수) / 제출됨 / 재개용 저장 시도. 재개 값은 `quiz_attempt`의 `seed`·`question_order_json`·`current_index`·`elapsed_ms`, `quiz_answer.chosen_index`는 섞기 전 원래 색인(architecture.md 9.2). 연습(S13) 시도는 나갈 때 `finished_at`을 기록한다. 타입 이름은 M19 착수 때 architecture.md가 정한다 |

저장 실패 계약(architecture.md 7·8.2): `StudyRepository.applyReview`는 실패하면 예외를 던진다. `ReviewViewModel`은 잡아서 카드·미리보기·대기열을 그대로 두고 `saveFailed`를 채운다. "다시 시도"는 처음 계산한 `ScheduleResult`를 다시 저장한다.

`user_settings`(StudyDatabase v1, architecture.md 5절): 6.8 표의 열과 `onboarding_done`, `first_notice_dismissed`, `extra_new_cards_study_day`, `extra_new_cards`, `listening_script_first`가 모두 v1에 있다. 범위: `new_cards_per_day` 5..50(5 단위), `day_boundary_minutes` 0..720(60 단위). `SessionPlanner.selectNew`의 `effectiveNewCardsPerDay` 인자 = 설정값 + (`extra_new_cards_study_day == 오늘`이면 `extra_new_cards`, 아니면 0). 이로써 M17은 스키마를 바꾸지 않는다.

### 13.2 콘텐츠 형식(architecture.md 4·6절과 대조)

- 뜻 행: architecture.md 4.2 `vocab_meaning`의 `ko_source`, `ko_method`, `ko_review_status`, `ko_reviewed_at`, `ko_reviewer_kind`. 문법은 grammar 행의 같은 이름 `ko_*` 열과 `needs_review`(9.1). 문항·청해 행도 같은 열 묶음(`source_id`, `ko_*`, `needs_review`, 9.2·9.3)이고 해설 카드 칩(5.5)의 입력이다.
- 문법: `connections_json` 항목 `{posKo, form, example}`(6.9 접속 표 3열), `contrasts_json`(6.9 "비슷한 문법", architecture.md 9.1).
- 대상 구간: 일본어 필드 안 `[[…]]` → `RubySegment.emphasis`(4.4, 6.9.1·6.10). 문항 `target`(지시문 `{대상}`, 6.10)은 `targets`(관련 ID)와 다른 필드.
- 영어 뜻: 번들하지 않는다(architecture.md 4.2·AR-U04 A). 화면에 영어 뜻 절·행이 없다. 영어 원문 표시를 원하면 사용자 승인을 새로 받는다.
- 후리가나: 표제어는 앱이 `Furigana.fromHeadword`로 나눈다(5.2). 일본어 필드 문장은 `{漢字|かな}`. 한국어 필드에는 루비 표기를 쓰지 않는다(4.4).
- 한국어 필드 안 일본어 구간: 표시를 넣지 않는다(4.4). `authored`·`ko-gloss` 단계는 찾은 일본어 구간을 보고서에 나열(실패 아님), `{`·`|`·`}`는 FAIL(architecture.md 4.4). 작성 규칙 "한국어 문장에는 한자를 쓰지 않고, 일본어는 가나·한자 그대로 쓴다"는 architecture.md 6.4에 있고, `jlpt-content-authoring` 스킬에는 아직 없다(하네스 변경은 `harness:evolve` 몫).
- `attribution.json`(architecture.md 6.6): `notices[]`(`{id, title, body}`, 본문과 포함 여부는 파이프라인이 완성), `firstNotice`, `levelListName`, `modelNames`, `reviewPolicy`, `dbLicense`, `contentVersion`, `sources[]`(매니페스트 필드 + `links[]` + `extraCredits[]`), `meaningSourceTexts`(키 7개, 5.7). 문구 틀은 `content/notices.ko.json`.
- 9.4 최소 표시는 파이프라인 생성 파일이다(손으로 쓰지 않음).
- 문장 배열 문항: 4지선다(`choices` 4개, `answerIndex`, 6.10·architecture.md 9.2). 별도 순서 채점 필드를 두지 않는다.

## 14. 사용자 승인 항목

이 문서에만 있는 결정은 `UI-Uxx`(이 문서가 정본). architecture.md와 겹치는 결정은 그 문서의 `AR-Uxx` 하나로 승인하고, 여기서는 이 문서의 의견만 적는다. 사용자가 정할 미정 결정(D-03·D-04·D-06)은 이 문서가 정하지 않는다. 화면은 어느 선택이든 동작하게 적었다(5.7·5.8·9.2).

**architecture.md 항목에 대한 이 문서의 의견**

| ID(architecture.md 12절) | 항목 | 이 문서의 의견 |
|------|------|------|
| AR-U01 | SRS 알고리즘(평가 버튼 수 포함) | A(FSRS-6) — 버튼 4개(5.3)와 맞다. 6단계는 한 손 하단 줄에 48 dp 이상으로 들어가지 않는다 |
| AR-U04 | 한자 한국어 뜻 | A(넣지 않음)에 맞춰 화면에서 뜻 행을 뺐다(2.1, 6.7). 영어 뜻도 보이지 않는다 |
| AR-U05 | 일본어 글꼴 | **C를 추천**: A(기기 글꼴 + `ja-JP`)로 시작하고, 첫 화면 마일스톤(M2)의 스크린샷·실기기 확인에서 일본식 글자 모양이 안 나오거나 글리프가 없으면 B(번들, S-10·N-14 조사)로 전환. |
| AR-U06 | 새 카드 하루 기본값·범위 | 기본 10, 범위 **5~50(5 단위)**(0은 "새 카드 없음" 상태를 따로 만들어야 하고, 50 넘게는 한 손 짧은 학습 목표와 맞지 않는다) |

**이 문서의 항목**

| ID | 항목 | 선택지 | 추천 | 근거 |
|----|------|--------|------|------|
| UI-U01 | 검수 칩 숨기기 설정 | A. 숨길 수 없음 / B. 설정에서 숨기기 허용 | **A** | 사람 검수가 아닌 뜻을 검수된 것처럼 보이게 하지 않는다는 원칙(D-03, `content-licensing` 4절). 칩은 작은 중성색이라 학습을 방해하지 않는다 |
| UI-U02 | 단어 회상(한→일) 카드 | A. 제공, 기본 끔 / B. 기본 켬 / C. 제공 안 함 | **A** | 회상 연습은 효과가 있지만 한국어 뜻이 AI 번역이고 같은 한국어 뜻에 여러 일본어 단어가 대응해 정답이 모호할 수 있다. 사용자가 고를 수 있게만 둔다 |
| UI-U03 | 동적 색(Material You) | A. 쓰지 않음(고정 팔레트) / B. Android 12 이상에서 사용 | **A** | 배경화면에서 뽑은 색은 3.1.4 대비 계산을 보장하지 못하고, 평가 버튼 색 구분이 기기마다 달라진다 |
| UI-U04 | 아이콘 | A. Material Symbols를 벡터 XML로 필요한 것만 넣음(S-11로 라이선스 확인·기록) / B. `material-icons-extended` 의존성 추가 | **A** | 아이콘 20여 개를 위해 큰 의존성을 넣지 않는다. 어느 쪽이든 고지 방법은 analyst의 S-11 확인 결과를 따른다 |
| UI-U05 | 모의고사 시간 | A. 앱을 떠나면 시간 멈춤 / B. 실제 시간으로 계속 흐름 | **A** | 짧은 틈에 쓰는 앱이라 중단이 잦다. 시간은 원점수와 함께 참고 정보로만 보인다 |
| UI-U06 | 목표 기억률을 설정에 둘지 | A. 두지 않음(0.9 고정) / B. 고급 설정으로 0.70~0.97 | **A** | 초보 학습자에게 뜻이 어렵고 복습량을 크게 바꾼다. B를 고르면 S08에 행을 더하고 architecture.md 범위를 쓴다 |

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-11-02 | 처음 작성(plan.md, sources.md 2026-11-02판, decisions.md D-01~D-07 반영) |
| 2026-11-02 | 설계 검증 지적 반영: architecture.md와 대조(타입 이름·영어 뜻 미번들·후리가나 앱 계산·설정 열·UiState 13.1), 포커스 링 3.1.5, 일본어 구간 규칙 4.4·TalkBack 방법 4.3, 검수 칩 두 축 규칙, 최소 크기 규칙, 낮은 높이 배치, 입력 무시 양방향, 샘플 띠, 회상 카드 앞면, 청해 자동 재생 금지·대본 먼저 보기, 필순·문법 카드 초안, 승인 항목 ID `UD-xx` |
| 2026-11-02 | architecture.md 11.2(1~22) 반영: 상태·필드 이름을 architecture.md 7·8절로(2·6.2·6.9.1·7·13.1), `SpeakerState.Initializing`(5.8·7·13.1), `IntervalFormat`과 간격 표시 규칙·경계값 표, 59초 → "1분 미만"(5.3), 일본어 구간 문자 집합·한국어 필드 루비 금지·보고서 나열(4.4·13.2), 칩 앞 부분 "사람 번역" 행과 `meaningSourceTexts` 키 7개 문장·`ProvenanceLabel.sheetText`(5.7), `notices`·`firstNotice`·`modelNames` 파이프라인 완성·`extraCredits` 원문 그대로·최소 표시는 쓰인 자료 기준(6.2·9.2·9.4), 문법 칩 입력 grammar `ko_*`·`needs_review`(6.9·6.9.1), `NO_LEVEL_SELECTED` 방어용 조건(7), 아이콘 목록에 check·language_japanese_kana(3.5), 스크린샷 이름 정리와 M14·M17·M23 추가(1.2·12), 승인 ID `UI-Uxx`/`AR-Uxx`(14), 반영된 요청 문장과 13.3 삭제 |
| 2026-11-02 | 설계 검증 후속 지적 반영(architecture.md 정본 기준): S11 접속 표 `connections[].posKo/form/example`·"비슷한 문법" `contrasts_json`(6.9·13.2), 대상 구간 `[[…]]`·`RubySegment.emphasis`·문항 `target`(4.4·5.2·6.9.1·6.10·13.2), 해설 카드 `ProvenanceChip`·"해설 검토 중"(문항·청해 `ko_*`, 5.5·6.10·6.11·8·13.2), 홈 수치와 대기열을 `dueForSession` 한 집합으로·끈 종류/뺀 급수 복습 멈춤 정책(6.2·6.8·7·13.1), `AppState.Opening` 화면 행·S01 `PREPARING` 뜻 수정(6.1·7·10·12·13.1), `About(anchor)`·`Settings(openLevelPicker)` 위치 이동(5.9·5.11·6.2·6.6·6.7·6.7.1·6.8·7·13.1), 속도 토글 `Speaker.speak(…, rate)`(5.8·13.1), 13.1 Review `newCardsLeftAfterSession`(6.4·13.1) |
| 2026-11-02 | M2 리뷰 지적 반영: 내비게이션 선택 표시자를 `secondaryContainer`(바 대비 1.10:1·1.76:1)에서 `primary`로, 선택 아이콘 `onPrimary`·선택 라벨 `onSurface` 700·비선택 `onSurfaceVariant` 500 확정, 바·레일 항목 토큰 표(1.2), 3.1.1 쓰는 곳, 3.1.4 내비게이션 대비 행, 3.2.1 labelSmall 굵기 |
