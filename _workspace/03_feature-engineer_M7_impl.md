# M7 구현 요약: 출처와 라이선스(S09)

- 새 파일: ui/about/{AboutViewModel,AboutScreen,ProvenanceSheet}.kt, assets/licenses/{py-fsrs.txt,apache-2.0.txt,libraries.json}
- 바뀐 파일: AttributionRepository(openSourceLicenses, LicenseUi), ContentRepository/ContentDao(meaningReviewCounts), ProvenanceLabel(sheetText, reviewLine), AppRoot(About 라우트, 칩 시트 연결), DetailActions(onChipClick), PosLabels 주석(금지 단어 제거)
- 시험: ui/about/ 아래 6개 파일(시트 문장 7키, 파일·grep·아이콘·libs 대조, ViewModel, 화면, 내비게이션, 스크린샷)
- 스크린샷: screenshots/attribution_{loaded,loaded_sources,loaded_licenses,error}_{light,dark}.png, attribution_loaded_{light,dark}_font200.png

## 명세와 다른 점·선택
- libraries.json에 `key`(toml 키)와 선택 필드 `fullText`를 더했다. `name`은 모듈 좌표. py-fsrs 항목을 같은 파일에 둔다.
- 화면의 URL 옆 content_copy 버튼과 길게 눌러 복사는 만들지 않았다(아이콘 벡터 없음). 복사는 링크 실패 스낵바의 "주소 복사"만.
- 라이선스 전문은 접지 않고 항상 보인다(그 밖의 라이선스 절 끝).
- 고지 N-10 카드에 집계 줄을 붙이는 기준은 id "N-10" 고정이다.
- 글꼴(S-10)·앱 코드 라이선스(D-05) 카드는 없다(해당 결정 전).
