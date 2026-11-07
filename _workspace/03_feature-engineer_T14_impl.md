# T14 구현 요약 — MVP 화면 접근성 보강

verify.sh 종료 코드 0 (로그 `_workspace/build/verify_20261107_102323.log`), 단위 테스트 500개 전부 통과. 이번 작업으로 9개 추가, 삭제 0. `git diff --exit-code app/schemas` = 0.

## 변경
- S03: `Flashcard.kt` — `review_card` 노드 하나에 앞면·뒷면 모두 `liveRegion = Polite`. `FocusRequester`·`focusable()`·`requestFocus` 삭제(grep 0건).
  뒷면 낭독(`backText`)에 11.1대로 "뜻 {뜻 1}. {칩 문구}"를 더했다(단어 카드, 이전에는 "뒷면. 읽기 …"만).
- `ui/components/NumberStepper.kt` 신규(OutlinedIconButton 모양 −/+ 48 dp, 5~50 단위 5, 범위 끝 비활성, 버튼 stateDescription "{n}장", 값 노드 liveRegion 없음, `isTraversalGroup`, 글꼴이 크면 FlowRow로 줄바꿈). S01(`LevelSelectScreen`)·S08(`SettingsScreen`)이 사용. `ui/common/Stepper.kt` 삭제.
- S08 하루 시작 시각: 행(`row_day_start`, stateDescription "{h}시", onClickLabel "바꾸기", 값 tnum) → `ModalBottomSheet`의 `DayStartSheetContent`(라디오 13개 00:00~12:00, 각 행 contentDescription "{h}시"). 고르면 저장·닫힘·입력 포커스는 행으로. 옛 −/+ 시작 시각 스테퍼 삭제. TimePicker 없음.
- S05: `AndroidManifest.xml` MainActivity `windowSoftInputMode="adjustResize"`. `BrowseScreen` 본문(탭·칩·결과)을 `Column(weight(1f)).imePadding()`으로 감쌌다(검색란은 위 고정). `AppRoot`의 NavigationSuiteScaffold 콘텐츠 Box가 하단 바가 차지한 높이를 `consumeWindowInsets`로 소비해(onGloballyPositioned로 측정) `imePadding()`이 바 높이를 두 번 빼지 않게 했다.
- 문자열: `stepper_value`, `settings_day_start_note`, `settings_day_start_hour`, `action_change`.

## 테스트(신규)
- `ReviewLiveRegionTest`: review_card Polite, 앞·뒷면·다음 카드·되돌리기·문법 카드 전후 `SemanticsNode.id` 동일, text가 11.1 문구로 바뀜.
- `SettingsA11yUiTest`: S01·S08 스테퍼(5에서 −, 50에서 +가 비활성, +로 15, 두 버튼 "15장", contentDescription, 값 노드 LiveRegion·ProgressBarRangeInfo 없음), S08 Room 저장(누를 때마다 new_cards_per_day, 06:00 → 360분), 시트(행 stateDescription·시트 안 Role.RadioButton 13개·Selected 1개·00:00/12:00·고르면 닫힘·행 포커스).
- `BrowseImeUiTest`: IME 인셋(WindowInsetsCompat를 AndroidComposeView에 dispatch)을 흉내 내 목록 마지막 행·빈 결과 "필터 지우기"가 키보드 위에 있음.
- `OnboardingNavTest`: S01은 "시작" 전에 new_cards_per_day가 저장되지 않음 확인 추가. `ManifestTest`: adjustResize. `HomeScreenshotTest`: `settings_day_start_sheet`.

## 스크린샷(screenshots/)
- 새로 기록: `settings_day_start_sheet_{light,dark}`(04:00 선택됨), `settings_loaded_{light,dark}`, `level_loaded_{light,dark}`(새 NumberStepper).
- `review_front_word`·`review_back_word`는 다시 기록했지만 픽셀이 같아 바뀌지 않았다(의미 정보만 바뀜).
- 임시로 `_font200`(S01·S08)을 찍어 확인: S08은 [− 15장 +]가 한 줄에 들어가되 빠듯하다(값 좌우 8 dp). S01 `_font200`은 목록이 잘리는 기존 상태(T12 몫).

## 명세와 다른 점·주의
- 시트 라디오 개수 검사는 시트 안(`day_start_sheet` 자손)으로 한정했다: 설정 화면의 테마 분할 버튼 3개도 `Role.RadioButton`이라 화면 전체로는 16개다.
- 행 포커스: 터치 모드에서는 clickable이 `FocusRequester.requestFocus()`를 거절한다(Robolectric로 확인). 코드 포커스는 키보드·D-pad 입력 모드에서만 동작하고, TalkBack 낭독 포커스 복귀는 시트가 닫힐 때 플랫폼이 돌려준다(ui-design 11.1). 테스트는 키보드 입력 모드로 확인했다. 기존 "학습 급수" 행 포커스 코드(S08)도 같은 한계가 있다.
- `Flashcard` 뒷면은 `semantics`(병합 안 함) 그대로라 칩·설명 노드는 별도로 남는다(M4 계약 유지).
- 실기기 확인(unverified, 개발 종료 후 체크리스트): 한국어 TalkBack에서 뒤집기·다음 카드·되돌리기 때 review_card Polite 알림이 한 번씩, '답 보기'가 사라진 뒤 뒷면 낭독, 스테퍼 값 "{n}장" 한 번 읽힘, 시트를 닫은 뒤 포커스가 "하루 시작 시각" 행으로 돌아오는지, 실제 키보드가 열린 S05(하단 바 있는 상태)에서 결과·"필터 지우기"가 가려지지 않는지.
