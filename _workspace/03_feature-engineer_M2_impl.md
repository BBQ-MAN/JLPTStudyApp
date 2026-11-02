# M2 구현 요약 — 앱 골격(테마·일본어 표시·내비게이션·두 DB·S90)

## 결과
- `verify.sh` 종료 코드 0, PASS: `test lintDebug assembleDebug`. 로그: `_workspace/build/verify_20261102_043326.log`
- 단위·Robolectric 테스트 25개 통과(Manifest 2, Chunking 2, ContentDb 2, StudyRepository 7, AppStartup 3, Furigana 7, JpSpans 2) + 스크린샷 테스트 11개
- 스키마: `app/schemas/com.jlptstudy.app.data.{content.ContentDatabase,study.StudyDatabase}/1.json`. 열·NOT NULL·PK·인덱스·기본값을 4.2·5절 표와 대조했다. `user_settings` 기본값은 `PRAGMA table_info` 테스트로도 고정.
- 스크린샷 20장: `screenshots/` (nav_shell_home, nav_shell_home_font200(라이트·다크), status_loading/empty/error, app_opening, error_content_db(+font200), error_record_db, jp_display_sample). `verify.sh recordRoborazziDebug`로 기록.
- 일본어 글리프: `jp_display_sample_*`를 열어 확인했다. 네모 없음, 直·骨·角이 일본식 자형. 단 이것은 Robolectric 렌더링이다. 한국어 기기 실기기 확인은 `unverified`로 남는다.

## 명세와 다르게 한 것·미룬 것
1. 아이콘은 Material Icons(Apache-2.0) 채움 벡터 5개(home, menu_book, settings, error, info)다. 명세의 Material Symbols Rounded가 아니고 선택/비선택 변형이 같다. S-11 출처 기록(`assets/licenses/`)과 교체는 아이콘을 정식으로 들이는 마일스톤 몫.
2. `needsOnboarding = true`여도 시작 라우트는 홈이다(S01 `LevelSelect` 화면이 M2 범위 밖).
3. 테마는 시스템 설정을 따른다(`user_settings.theme` 연결은 S08 때).
4. `ContentRepository`는 M2에서 meta/levelCounts/newCardOrder/cardContents/cardLevels만 구현했다. browse·vocab·kanji·meaningReviewCounts는 사전·출처 마일스톤이 더한다. `cardContents`의 품사는 JMdict 코드 그대로(한국어 이름표 표는 M4).
5. `StudyRepository.updateSettings`는 target_levels 비어 있음 검사를 "급수를 바꿀 때만" 한다. 손상된 복원 값(읽을 때 빈 집합)에서도 테마 등 다른 설정은 저장되게 하기 위함.
6. `RubyText`의 긴 표기 폭 기준 글자 축소(5.1)는 M4로 미뤘다(주석 shortcut).
7. 콘텐츠 에셋 `assets/content/content.db`가 아직 없어 앱은 실행하면 ContentDbFailed(S90)로 간다. 파이프라인 마일스톤 전까지 정상 동작이다. 해시 이름 파일 `createFromAsset` 열기의 성공 경로는 에셋이 생기는 마일스톤에서 Robolectric 테스트로 더한다(architecture.md 4.5 "버전을 올릴 때"). M2 테스트는 실패 경로와 옛 파일 삭제만 검증한다.
8. 스크린샷 로딩 원호는 애니메이션 중간 프레임으로 고정되어 작은 호로 보인다. 결정적이긴 하나 보기 좋지 않다면 스크린샷에서만 정지 표시기로 바꿔야 한다.
9. 한국어 제목이 음절 중간에서 줄바꿈된다("못했습/니다"). Robolectric 네이티브 렌더러의 동작일 수 있어 실기기에서 확인 필요(Compose에는 한국어 keep-all이 없다).

## 파일
- `app/src/main/java/com/jlptstudy/app/`: Model, AppContainer, JlptApp, MainActivity, nav/{Routes, AppRoot}, data/content/*, data/study/*, ui/theme/*, ui/common/{Furigana, JpSpans, JpText, RubyText, StatusScreen, ExternalIntents}, ui/error/AppErrorScreen, ui/review/ReviewCardUi
- `app/src/main/res`: values/{strings,themes}, values-night/themes, xml/{backup_rules,data_extraction_rules}, drawable/ic_ms_*, AndroidManifest.xml(INTERNET 없음, allowBackup, 규칙 두 개)
- `app/build.gradle.kts`: 매니페스트 테스트용 `processDebugMainManifest` 의존과 시스템 속성. `BuildSmokeTest`는 실제 테스트로 대체되어 삭제.

## 콘텐츠 DB 결함
- 해당 없음(에셋 없음).

## 수정 3라운드 (residual medium 2건)
1. S90 "앱 정보 열기" 실패 알림: `AppRoot.kt`에 SnackbarHost를 두고 `startExternal`이 false를 돌려주면 스낵바 "앱 정보를 열 수 없습니다"(`app_info_unavailable`)를 띄운다. `startExternal`은 SecurityException도 false로 처리한다. 테스트: `AppStartupTest` "앱 정보를 열 수 없으면 스낵바로 알린다"(`checkActivities(true)`로 ActivityNotFoundException 유발).
   - 명세와 다른 점: ui-design.md에 이 실패의 문구가 정의돼 있지 않아 문구를 임의로 정했다(동작 없는 스낵바). ui-designer가 문구를 확정해 7절 표에 추가해야 한다.
2. 다크 스크린샷: `ScreenshotTest.shots`에서 다크 전환 뒤 시계를 100 ms가 아니라 1000 ms 진행한 다음 캡처한다. 모든 `_dark` PNG를 다시 기록했고 nav_shell_home_dark.png를 열어 다크 토큰(어두운 surface, 밝은 아이콘, 어두운 선택 표시)을 확인했다.

## 추가 수정: [Q13] 두 건 (medium)
- KoSpan(Type.kt) 공개 추가. JpSpans.annotate의 한국어 구간과 RubyText 낭독 문구 ", 읽기 "에 적용. JpSpansTest에 ko-KR 구간 단언 추가.
- RubyText: 루비 없는 구간을 splitPlain으로 글자 단위 항목으로 쪼개 FlowRow에 넣음(루비 구간·emphasis는 유지, 밑줄은 글자마다 그려져 줄마다 이어짐). 한계: 금칙 처리 없음(문장부호가 줄머리에 올 수 있음).
- 테스트: RubyTextWrapTest(splitPlain, 10자 폭에서 20자가 정확히 2줄). 스크린샷 jp_display_sample에 긴 혼용 문장+강조 구간 추가(라이트·다크 갱신).
- verify.sh 종료 코드 0.

## 리뷰 3라운드 후 medium 3건 수정
- StudyRepository.validate: 카드 종류 검사에서 GR을 뺌(`enabledKinds - CardKind.GR`, M17에서 번들 기준으로 교체). 테스트: 보이는 셋 off + gr on 거부.
- RubyText: 읽기를 그릴 일이 없는 문자열(루비 없음 또는 showRuby=false)은 Text 하나로 그림(읽기 자리 여백 없음, emphasis는 굵기+밑줄). 읽기가 있는 문장은 기존 조각 경로 유지(그 안의 루비 없는 구간은 여전히 글자 단위: 미해결 비용). 테스트: 읽기 있는 문장 2줄 감김, 읽기 없을 때 높이 작음. 기존 "루비 없는 긴 구간 감김" 테스트는 Robolectric이 CJK 줄바꿈을 못 해(공백에서만 끊김) Text 경로에서 검증 불가라 제거.
- StatusScreen: focusRequester/focusable/requestFocus 제거, 제목은 heading semantics만. 테스트 StatusScreenTest.
- verify.sh 종료 코드 0.
