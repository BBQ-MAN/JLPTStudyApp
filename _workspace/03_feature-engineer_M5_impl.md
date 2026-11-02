# M5 구현 요약 — 급수 선택(S01)·홈(S02)·설정(S08)

## 결과
- `verify.sh` 종료 코드 0. 로그: `_workspace/build/verify_20261102_075541.log` (스크린샷은 `verify.sh recordRoborazziDebug`로 기록)
- 만든 것: `AttributionRepository`·`AssetAttributionRepository`·`AttributionData`(파싱 실패는 예외), `HomeViewModel`(+`HomeUiState`·`LevelProgress`), `HomeScreen`, `LevelSelectViewModel`·`LevelSelectScreen`·`LevelPicker`, `SettingsViewModel`·`SettingsScreen`(급수 하단 시트 포함), `StepperRow`, `StatusContent`(StatusScreen 내용만 분리), 시작 라우트 `LevelSelect`, `Settings(openLevelPicker)` 연결, `AppContainer.theme`·`attributionRepository`(테마는 MainActivity가 JlptTheme에 적용), `ic_ms_more_vert`(S-11 usedFields에 있음).
- 홈 수치: `SessionCards.load` → `SessionPlanner.plan`(기한 집합) / `selectNew`. `ReviewViewModel`과 같은 집합이라 `remainingReview == dueCount`를 테스트로 고정.
- 테스트: `HomeViewModelTest`(8), `HomeRobolectricTest`(5: attribution 정상·실패 3종, 새 카드 5장 더 영속, 설정 영속, 범위 밖·종류 모두 끔), `OnboardingNavTest`(6: S01 시작·저장·재시작, 급수 고르기→시트, 탭 직접→시트 닫힘, About 두 경로, 학습 시작→Review), `LevelScreensUiTest`(7), `HomeScreenUiTest`(6).
- 스크린샷(`screenshots/`, 각 _light·_dark): level_loading, level_empty_selection, level_loaded, level_unavailable, home_loading, home_ready, home_no_level, home_no_content, home_all_done, home_new_limit(+ `_font200`), home_first_notice, home_sample_banner, settings_loaded. `nav_shell_home*`는 홈 로딩 자리표시로 다시 기록됨.

## 명세와 다르게 한 것·미룬 것
1. `SettingsUiState`에 계약 밖 필드 `levelOptions`(급수 시트용)·`kindsWarning`, `LevelUiState`에 `saveFailed`를 더했다. 별도 StateFlow로 빼지 않은 것은 코드를 줄이려는 선택이다.
2. 첫 안내 카드 "자세히"는 `About("N-10")`, 급수 고지 "자세히"는 `About("N-09")`로 간다. About 화면은 아직 자리표시(M7).
3. 설정은 값을 바꿀 때마다 즉시 저장한다(별도 저장 버튼은 급수 시트만). 저장 실패 스낵바 "다시 시도"는 마지막 변경을 다시 저장한다.
4. 시작 시각은 시간 선택 대화상자 대신 −/＋ 단계 조절(00:00~12:00)이다.
5. 급수 시트의 사라진 급수(이미 선택됐으나 콘텐츠 밖)는 시트에서 선택 해제로 보이고, 저장하면 빠진다.
6. 다음 복습 시각 문구는 S03과 같은 `M월 d일 HH:mm`, 시스템 시간대 기준이다("하루 시작 시각 기준" 표시는 아님). 스크린샷은 UTC로 고정.
7. `home_new_limit_font200`는 라이트·다크 모두 만들었다. 홈 목록에서 본 "사전 보기"는 사전 탭(M8)이 없어 자리표시 화면으로 간다.
8. 홈 `HomeStatus.LOADING`은 계산 전 초기값이고, 읽기 예외가 나면 직전 값을 그대로 둔다(명세에 ERROR 없음).
9. 탭 `nav_shell_home`은 컨테이너 없이 그려 로딩 자리표시 상태다(실데이터 홈은 `home_*`).

## 확인 못 한 것(unverified)
- 실기기: 설정 시트 TalkBack 포커스 복귀, 한국어 TalkBack의 급수 행 낭독, 시트의 실제 뒤로 동작. 급수 시트 자체의 스크린샷은 없다(Roborazzi `onRoot`는 대화상자 창을 담지 않는다. 열림은 UI 테스트로 확인).
- 홈의 `levelProgress`는 호출마다 `startedCardIds`를 읽는다(SessionCards와 같은 shortcut 한계).

## 콘텐츠 DB 결함
- 해당 없음(샘플 DB `kind = sample`, `attribution.json`은 정상으로 파싱됨).

## 후속 수정 (Q14, M7 검증 공백, D-09 앱 아이콘)
- Q14: AppContainer.onboardingCompleted() 추가, LevelSelect onStarted에서 호출(state를 Ready(needsOnboarding=false)로). OnboardingNavTest에 "시작 저장 뒤 같은 컨테이너로 다시 열면 홈으로 시작한다" 추가.
- M7: app/build.gradle.kts Test 태스크에 inputs.file(docs/content/manifest.json, gradle/libs.versions.toml).
- D-09: mipmap-anydpi-v26/ic_launcher.xml, mipmap-*/ic_launcher_foreground.webp, values/ic_launcher_colors.xml 추가, Manifest에 icon/roundIcon. 스크린샷 변경 없음.
- verify.sh 종료 코드 0.
