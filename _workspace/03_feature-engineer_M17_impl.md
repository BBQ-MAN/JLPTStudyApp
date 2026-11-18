# M17 문법 카드 SRS 연동 구현 요약

- 데이터: `ContentDao`에 grammarOrder·grammarLevelsOf·grammarByIds·grammarFirstExamples 추가. `RoomContentRepository`의 newCardOrder·cardLevels·cardContents가 gr를 처리(정렬은 level desc, sort_key, kind ordinal vr→vp→kr→gr, id). example·exampleKo = grammar_example ord 0, summaryKo = explanation_ko 첫 문단, provenance = grammar `ko_*`. 예문 0행인 카드는 건너뜀.
- 모델·화면: `ReviewCardUi.Grammar`, `Flashcard`의 문법 앞·뒷면(title 축소 규칙 60%, 예문 body_furigana, 요약 4줄 + "자세히" → GrammarDetail, ProvenanceChip + "설명 검토 중"), TalkBack 문구. 뒷면은 문법 카드에서 늘 카드 전체 스크롤(형식 줄바꿈·글꼴 200%에서 뒷면 영역 높이가 0이 되는 것을 막음).
- 설정: `SettingsUiState.grammarAvailable`(번들에 문법 행이 있을 때만 스위치 표시). 스키마 변경 없음(StudyDatabase 2.json, ContentDatabase 2.json 그대로).
- 테스트: GrammarCardViewModelTest(대기열·평가 저장·끔/켬·홈 dueCount), GrammarCardContentTest(인메모리 ContentDatabase 정렬·내용), GrammarCardUiTest(TalkBack 문구, 앞뒤 y·크기, emphasis 굵기 500·밑줄, 자세히, 설정 스위치). `ChunkingTest`의 가짜 DAO에 새 메서드 4개 추가(기존 단언은 그대로). 스크린샷 테스트 추가.
- 스크린샷: review_front_grammar, review_back_grammar, review_back_grammar_scrolled, review_front_grammar_long_title, review_back_grammar(_font200), settings_with_grammar (각 _light/_dark).

## 명세와 다른 점·메모
- 뒷면 TalkBack 노드: 과제 문구는 '뒷면. 뜻. 예문 뜻. {칩}'이지만 ui-design 6.9.1은 summaryKo를 칩 앞에 넣는다. ui-design을 따랐다(테스트도 그 문구).
- 저장소 검증 `(enabledKinds - GR).isNotEmpty()`는 그대로 뒀다(기존 HomeRobolectricTest가 단어 인식·한자·단어 회상을 모두 끄면 거부하는 것을 단언). 그래서 "문법 카드만 켬"은 저장되지 않는다. 필요하면 별도 마일스톤에서 저장소 검증과 그 테스트를 함께 바꾼다.
- `RubyText` 밑줄 Box에 testTag `ruby_underline`을 달았다(emphasis 시험용, 화면 변화 없음).
- 콘텐츠 DB 결함은 보지 못했다(번들 문법 행은 읽지 않고 합성 행으로 시험).
- 기존 `scrollPx` 스크린샷 도우미의 swipe 횟수를 scrollPx/200으로 일반화(기존 600 → 3회 그대로).
