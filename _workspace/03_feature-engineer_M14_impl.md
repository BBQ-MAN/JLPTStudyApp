# M14 구현 요약 (문법 목록 S10 · 상세 S11)

- 데이터: data/content/GrammarRepository.kt (계약 타입, RoomGrammarRepository, NoGrammarRepository). RoomContentRepository가 위임으로 구현. ContentDao에 grammar 조회 5개 추가. 스키마·버전 변경 없음(2.json 그대로).
- 화면: ui/grammar/ (GrammarModels, GrammarViewModels, GrammarScreens). 상세 틀은 ui/detail의 DetailFrame·SectionTitle·LevelButton을 internal로 열고 FuriganaToggle로 추출해 재사용.
- 내비: Grammar 탭(아이콘 school, S-11 usedFields), GrammarDetail(id) 라우트, AppContainer.grammarRepository.
- 계약과 다른 점: GrammarListStatus에 ERROR 추가(읽기 실패 -> S90, Browse와 같음), GrammarListUiState.availableLevels, GrammarDetailUiState.showRuby 추가. 접속 표/카드 전환은 폭(sp 환산) 320 미만 휴리스틱. S10 급수 묶음 머리는 누르기 동작 없음(안내 줄 링크가 N-09로 감).
- 테스트: GrammarViewModelTest(11), GrammarScreenUiTest(6), 합성 픽스처. 스크린샷 screenshots/grammar_*.
- 콘텐츠 DB 결함 보고 없음(grammar 0행 상태).
