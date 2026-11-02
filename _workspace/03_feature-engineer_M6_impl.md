# M6 구현 요약 — 사전(S05)·단어 상세(S06)·한자 상세(S07)

## 결과
- `verify.sh` 종료 코드 0. 로그: `_workspace/build/verify_20261102_081559.log` (스크린샷은 `verify.sh recordRoborazziDebug`로 기록)
- 만든 것: `ContentRepository.browse/vocabDetail/kanjiDetail`(Room SQL, LIKE 이스케이프, retired 제외), `BrowseViewModel`(300 ms 디바운스, 급수 다중 필터), `BrowseScreen`(LazyColumn/LazyVerticalGrid, key = id), `VocabDetailViewModel`·`KanjiDetailViewModel`, `VocabDetailScreen`·`KanjiDetailScreen`, 라우트 `VocabDetail(id)`·`KanjiDetail(literal)`, 사전 탭 자리표시 교체, 아이콘 search·arrow_back.
- 테스트: `BrowseViewModelTest`(6), `DetailViewModelTest`(4), `PosLabelsTest`(2), `ContentBrowseDbTest`(2, 번들 샘플 DB), `BrowseScreenUiTest`(3), `DetailScreensUiTest`(4: strokes null이면 필순 없음, 칩 5종, RETIRED).
- 스크린샷(`screenshots/`, 각 _light·_dark): dict_words, dict_words_3500(3,500행 합성 픽스처), dict_kanji(+_font200), dict_no_result, dict_no_content_for_filter, word_detail_loaded(+_font200, 검수 상태 다섯 가지 칩), kanji_detail_loaded, kanji_detail_no_korean_reading.

## 명세와 다르게 한 것
1. `BrowseUiState.availableLevels`, `KanjiDetailUiState.level` 필드를 계약에 더했다(칩 목록, 머리 급수 칩).
2. `VocabDetailUiState.pos`는 `PosLabels` 이름표로 채운다. 복습 카드(`ReviewCardUi.Vocab.pos`)는 코드 그대로이고 화면에서 바꾼다(`PosChips`가 이름표 목록을 받도록 바꿈).
3. 읽기 실패나 없는·retired ID는 상세에서 모두 RETIRED, 목록 읽기 실패는 LOADING으로 둔다(명세에 오류 상태 없음).
4. 단어 상세의 "예문" 절은 만들지 않았다(UiState에 예문이 없다). 칩은 누를 수 없는 표시만(하단 시트는 M7).
5. 필순 절은 `strokes != null`일 때 절 제목만 그린다. 그림은 M12. 현재 DB에 `kanji_stroke`가 없어 `strokes`는 항상 null.
6. 한자 검색은 글자·한국어 음·음독·훈독의 JSON 문자열에 LIKE(shortcut: 괄호·따옴표도 맞을 수 있음). 전체 결과를 메모리에 올린다(3,500행 규모, 페이징 없음).
7. 학습 상태 절은 카드 종류가 꺼져 있고 기록도 없으면 그 행을 뺀다(회상 카드 기본 꺼짐). 날짜는 시스템 시간대 기준 "M월 d일".

## 확인 못 한 것
- 실기기 TalkBack, 검색 입력 중 IME 동작. 3,500행은 스크린샷만이고 실제 스크롤 성능은 측정하지 않았다.
- 글꼴 200% 검색창 안내 문구는 두 줄로 줄바꿈된다(잘리지 않음).

## 콘텐츠 DB 결함
- 해당 없음.
