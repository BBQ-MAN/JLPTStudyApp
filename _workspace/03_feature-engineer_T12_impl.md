# T12 MVP 화면 글꼴 200% 스크린샷 보강

## 추가한 스크린샷(light·dark 쌍, 2.0배)
level_loaded(h1400, 급수 행·새 카드 단계 조절·하단 '시작'), settings_loaded(h1600), done_loaded, kanji_detail_loaded(h1400), app_opening. error_content_db_font200은 이미 있었다.
잘림·겹침 없음 -> 레이아웃 변경 없음. 한자 상세의 '한국어 음' 라벨이 두 줄로 접히지만 읽힌다(잘림 아님).

## 기존 스크린샷이 바뀐 이유(T12 코드 변경 아님, verifyRoborazziDebug가 이미 실패하던 것)
- attribution_loaded/error (light·dark·font200): 통합 때 들어온 attribution.json 문구가 바뀌어 기준 이미지가 낡음 -> 재기록.
- kanji_detail_strokes_done/numbers/numbers_dense(+font200) dark: 다시 보기 아이콘 가장자리 픽셀이 실행마다 달라 verify가 불안정. DictShots에 changeThreshold 0.001 적용 후 재기록.
- review_empty_*: 기준은 KST인데 같은 JVM의 홈 테스트가 시간대를 UTC로 바꿔 "다음 복습" 시각이 달라짐 -> ReviewShots에 Asia/Seoul 고정(이미지 변경 없음).

## 검증
verify.sh PASS(종료 0), verifyRoborazziDebug 통과, app/schemas diff 0.
작업 시작 때 워크트리에 있던 스크린샷 8장의 미커밋 변경(비결정 노이즈)은 git stash@{0}에 남아 있다. 필요 없으면 메인 세션이 버린다.

## 리뷰 수정
- DictShots의 changeThreshold 0.001을 shots(tolerant=true)로 옵트인하게 바꾸고 kanji_detail_strokes_* (reduced_motion 제외) 테스트에만 건다. 나머지 사전 화면은 임계값 0.
- 완료 화면 버튼 문구를 '새 카드 %1$d장 더 학습'으로 바꿔 minOf(left, EXTRA_NEW_STEP)를 넣는다(left=3이면 3장). done_loaded_light/dark 두 장과 font200 한 쌍이 이 문구로 재기록됨. ReviewUiTest에 left=8(5장) 확인 추가.
