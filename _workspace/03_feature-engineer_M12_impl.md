# M12 필순 표시 구현 요약

- ui/detail/StrokeOrder.kt: StrokeGeometry.start, StrokePlayback(rememberSaveable), StrokeOrder(그림, 재생 버튼, 획 번호 스위치, 출처 줄). 애니메이션 삭제는 ANIMATOR_DURATION_SCALE==0을 화면이 열릴 때 한 번 읽는다(도중 변경은 다음 진입부터).
- ContentRepository.kanjiDetail: kanji_stroke 읽기, JSON 실패·빈 배열·경로 하나라도 해석 실패면 strokes=null. ContentDao.strokePaths 추가. DB 버전·스키마 변경 없음(2.json diff 0).
- KanjiDetailUiState.strokeSourceName: KanjiDetailViewModel이 주입받은 람다(AppRoot가 attribution.json S-03 name을 읽음)에서 채움. 출처 줄은 About("S-03")으로 이동. 화면 코드에 "KanjiVG" 없음(테스트가 확인).
- 계약과 다른 점: 저장소 메서드 이름은 기존대로 kanjiDetail(architecture는 kanji). 현재 번들 attribution.json에 S-03이 없어 실기기에서는 S-03 확정 전까지 출처 줄이 숨는다.
- 재생 시간: 획 n개면 n*400 + (n-1)*150 ms(마지막 획 뒤에는 쉼 없음).
- 스크린샷: kanji_detail_strokes_{light,dark}, kanji_detail_strokes_{light,dark}_font200, kanji_detail_loaded_{light,dark}(필순 없음). 합성 픽스처(authored).
- 시험 메모: 시계를 멈춘 Compose 테스트에서 performClick 뒤에 waitForIdle + 한 프레임이 필요하다.
