# T09 MVP 낮은 지적·린트 정리

## 결과
- verify.sh 종료 코드 0 (`_workspace/build/verify_20261107_110957.log`). lint Warning 3(AGP 버전, androidx.core 버전, 생성 파일 attribution_fallback.xml 대시), Error 0. T01 기준선 8보다 적다.
- `git diff --exit-code app/schemas` = 0. 스키마·기능 변경 없음.
- `./gradlew cleanTestDebugUnitTest verifyRoborazziDebug`(--no-build-cache, 변경 없이) 연속 3회: 1회 통과(25s), 2회 통과(23s), 3회 통과(23s). 빌드 캐시가 테스트 결과를 되살려 가짜 통과가 나오므로 clean + --no-build-cache로 매번 실제 실행했다.
- 스크린샷 변경 전 이미지는 `_workspace/T09_before/`, 변경 후는 `screenshots/`(같은 파일명).

## 5절 low 8건
| # | 지적 | 조치 | 파일 | 전 → 후 |
|---|------|------|------|---------|
| 1 | 0% 진행 막대에 끝점만 보임 | `drawStopIndicator = {}` | ui/home/HomeScreen.kt | `_workspace/T09_before/home_ready_light.png` → `screenshots/home_ready_light.png` (dark 동일). 세션·문제 막대는 범위 밖이라 그대로 |
| 2 | 홈(단어만)과 세션 완료(단어+한자) 수치 범위가 다름 | 고치지 않음. 홈 진도 줄은 "학습한 단어 n/m"이라고 이미 밝히고(ui-design 6.2: vr 카드 수), 홈 요약·완료 화면은 카드(장) 단위다. 같은 단위로 맞추려면 설계 결정이 필요해 U-항목 후보 | - | - |
| 3 | 라이선스 URL 줄바꿈에 '/'만 남음 | 보이는 글자에만 U+2060(앞에서 끊지 않음)·U+200B(단일 '/' 뒤에서 끊음)를 넣는 `breakFriendly`. 복사·열기·낭독 원문은 그대로. 테스트는 보이는 URL 비교에서 두 글자를 빼도록 고침 | ui/about/AboutScreen.kt, AboutScreenUiTest.kt | `_workspace/T09_before/attribution_loaded_sources_light.png` → `screenshots/attribution_loaded_sources_light.png` |
| 4 | 출처 화면 '앱이 바꾼 것'에 내부 파일명 노출 | 고치지 않음. 문구 원천이 `docs/content/manifest.json`의 yomitan 출처 `modifications`("…(level-overrides.tsv)…")와 거기서 생성되는 attribution.json·attribution_fallback.xml이다. 앱 코드 밖(content-engineer·출처 문서) 영역이고 재생성하면 콘텐츠 DB 해시도 바뀐다 | - | - |
| 5 | 설정 '학습 급수' 행에 누를 수 있다는 표시 없음 | ClickRow 끝에 꺾쇠(`ic_chevron_right`, 직접 그린 선 두 개라 Material Symbols 아님). 하루 시작 시각·출처와 라이선스 행도 같음. 낭독은 그대로(행 전체 버튼) | ui/settings/SettingsScreen.kt, res/drawable/ic_chevron_right.xml | `_workspace/T09_before/settings_loaded_light.png` → `screenshots/settings_loaded_light.png` |
| 6 | 급수 체크박스 상태 낭독 문구 | 고치지 않음. "선택됨/선택 안 됨/콘텐츠 준비 중, 선택할 수 없음"은 ui-design 6.9가 정한 문구이고 TalkBack 실기기 확인(U-7) 전에는 바꿀 근거가 없다 | - | - |
| 7 | 카드 스크롤바 항상 표시 | 스크롤 중과 멈춘 뒤 0.7초만 보이고 0.3초에 사라짐. 막대를 내용 영역 바깥 오른쪽 여백(6dp)에 그림. 뒷면 영역은 막대가 스크롤 내용과 함께 밀려 가던 그리기 순서(verticalScroll 뒤에 draw)도 바로잡음 | ui/review/Flashcard.kt | `_workspace/T09_before/review_back_word_light_font200.png`, `review_back_grammar_light_font200.png`, `review_back_grammar_scrolled_light.png` → `screenshots/` 같은 이름 |
| 8 | 가로 화면 스크롤바가 N5 칩과 겹침 | 7번과 같은 수정(막대가 칩 영역 밖 여백, 평소엔 안 보임) | ui/review/Flashcard.kt | `_workspace/T09_before/review_back_word_light_land_font200.png` → `screenshots/review_back_word_light_land_font200.png` |

회귀 스크린샷 1건: 연체사 품사 표시(adj-pn → "연체사") — `ReviewScreenshotTest.review_back_word_adnominal`, `screenshots/review_back_word_adnominal_light.png`·`_dark.png` 신규.

## 린트 (15 → 3)
고침: MissingKeepAnnotation(JlptLevel @Keep), VisibleForTests 2(QuizResultViewModel의 SavedStateHandle 기본값 제거, 테스트 호출부가 직접 넘김), ModifierParameter 3(NoticeCard 인자 순서 modifier 먼저·ResultRow 이름), UnusedResources 3(문자열 3개 삭제), UseKtx(`String.toUri`), ObsoleteSdkInt(`mipmap-anydpi-v26` → `mipmap-anydpi`), MonochromeLauncherIcon(`ic_launcher_monochrome.xml` 직접 그린 단색 아이콘).
남김: AndroidGradlePluginVersion·GradleDependency(버전 올림은 이번 범위 밖), TypographyDashes(`attribution_fallback.xml`은 파이프라인이 생성하는 파일이라 손으로 고치지 않음).

## 추가 범위
(a) 비결정 스크린샷: 원인은 눌림 물결(ripple)의 플랫폼 그리기. compose 시계(mainClock)로 멈출 수 없어 click이 있는 필순 화면(kanji_detail_strokes_done/numbers*, dark)의 버튼·스위치 가장자리 픽셀이 실행마다 달랐다(compare 이미지에서 확인). `DictShots`에서 `LocalRippleConfiguration`을 null로 주고 T12의 허용 오차(changeThreshold 0.001)를 없앴다. 임계값 0으로도 연속 통과. 필순 애니메이션 자체는 mainClock 수동 진행이라 원래 결정적이다. 기록한 이미지: kanji_detail_strokes_done_dark, kanji_detail_strokes_numbers_dark(+font200), kanji_detail_strokes_numbers_dense_dark(+font200). review_empty_*는 T12의 Asia/Seoul 고정으로 전체 실행 3회 모두 통과(재현 안 됨). 이미지 변경 없음.
(b) U-9: `ContentRepository.kanjiDetail`·카드(`cardContents`)의 strokeCount를 필순 행이 있으면 paths 개수로(카드는 해석 없이 JSON 개수만). 테스트 `KanjiStrokeCountTest`(실제 번들 DB, 飴 13→14·噌 14→15, 저장소·ViewModel·화면 '14획'/'15획'·TalkBack '필순 그림, 14획' 확인).
(c) S03 뒷면: `review_card`를 앞면처럼 `clearAndSetSemantics` 단일 노드로(text+liveRegion). 안에 있던 누를 것(출처 칩, "뜻 n개 더", 문법 "자세히")은 이 노드의 사용자 지정 동작으로 낸다(문구: "만든 방법 자세히 보기", "n번 뜻 …", "뜻 n개 더", "문법 자세히 보기"). 설계 문서 4.3과 달라지는 점: 뒷면 하위 노드가 TalkBack 병합 트리에서 사라지고 동작 메뉴로 간다. 화면 터치는 그대로. 테스트: ReviewLiveRegionTest 2개 추가(clearing, 하위 0개, 동작 목록·실행·더 보기 후 5개). 하루 시작 시각·급수 시트는 고른 뒤 `sheet.hide()` 애니메이션이 끝나고 닫는다. NumberStepper는 최신 값에서 이어 계산해 프레임 사이 빠른 연속 탭을 잃지 않는다(SettingsA11yUiTest에 20→25→30→25).
(d) `RetiredEndToEndTest`: 실제 번들 콘텐츠 DB + 학습 기록 Room. 두 카드를 공부한 뒤 retired 행을 넣으면 SessionCards 대기열·cardContents·vocabDetail·newCardOrder에서 빠지고 card_state 2·review_log 2행은 남으며, retired를 지우면 대기열이 돌아온다.

## 다른 점·알림
- 리뷰 반영(2차): 스크롤 막대는 여전히 스크롤 중에만 보이지만, 더 있는 쪽 가장자리가 카드 색으로 사라지는 페이드가 항상 보인다(`scrollCue`). 뒷면 영역이 180dp보다 낮은 카드는 카드 전체를 스크롤한다. 200% 뒷면 끝이 비어 보인 근본 원인은 ReviewScreen의 `onSizeChanged`가 padding 바깥에 있어 평가 버튼 높이(218dp)가 스낵바 높이로 잡힌 것(뒷면 끝 빈 여백)이라 안쪽으로 옮겼다. 스크린샷은 스크롤 전/후(`review_back_*_scrolled_*font200`)를 둘 다 찍고 테스트 하네스는 끝 늘어남 효과를 끈다.
- 리뷰 반영(2차): 뒷면 낭독문(`backText`)에 한자(한국어 음·음독·훈독·획수·쓰인 단어)와 어휘(품사·표시 중인 모든 뜻·'뜻 n개 더')를 모두 담았다. 출처 화면 URL은 낭독 Text를 원문 url로 지정하고 복사 버튼 이름을 '주소 복사: {url}'로, 복사 뒤 스낵바 '주소를 복사했습니다'를 낸다. NumberStepper는 마지막 보낸 목표값(1초 유지)에서 이어 계산한다. 실기기 TalkBack(URL·뒷면 낭독) 미확인.
- 실기기 미확인: 사용자 지정 동작 메뉴(TalkBack), 시트 숨김 애니메이션 체감.
- 콘텐츠 DB·스키마는 건드리지 않았다. 지적 4번의 문구는 content-engineer 후속.

## 3차 수정: 글꼴 200% 뒷면 이어짐 단서(D7)
- 원인: 가로 200%에서는 진행 머리글(카드 안)이 추정 40 dp보다 훨씬 커서 compact(카드 전체 스크롤) 판정이 빗나갔고, 문법은 예문이 줄바꿈돼도 추정이 모자랐다. 뒷면 영역이 높이 0이라 페이드·막대도 그릴 곳이 없었다.
- 조치(ui/review/Flashcard.kt): compact 판정에 실측을 쓴다(머리글 실측 높이와 `BackArea` 시작 위치로 잰 표제어 덩어리 높이 중 큰 값). compact일 때 뒤집으면 뒷면 영역 시작이 보이게 자동으로 끌어올린다(표제어 덩어리는 카드 높이의 절반까지 남김; 앞면은 0). 표제어 y 고정(ui-design 5.1)의 예외는 카드가 낮은 경우에만이다.
- 전 → 후: `_workspace/T09_before/review_back_word_light_land_font200.png`(표제어만) → `screenshots/review_back_word_light_land_font200.png`(읽기·품사 칩·1번 뜻 일부와 페이드). 세로 200% 단어·문법·한자(light/dark)도 뜻·읽기가 첫 화면에 들어오고 아래쪽 페이드가 보인다.
- 검증: verify.sh 종료 코드 0(`_workspace/build/verify_20261107_113608.log`), `cleanTestDebugUnitTest verifyRoborazziDebug --no-build-cache` 연속 3회 통과(28s/26s/26s), schemas diff 0, lint Warning 3.
- 미확인: 실기기 TalkBack·자동 끌어올림 체감.
