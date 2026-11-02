# 조사 메모 2026-11-02 (license-verify wf_ceec91c4-2d1)

- 직접 연 출처: edrdg licence.html, sample.html, j_jmdict.html, kanjd2index_legacy.html, kanjivg.tagaini.net, github KanjiVG(README, COPYING, releases API), CC BY-SA 4.0 legalcode, CC BY-SA 3.0 legalcode, CC compatible-licenses, CC FAQ, CC BY 2.0 FR legalcode.fr, tatoeba terms_of_use, downloads, jlpt.jp FAQ, GitHub 저장소 5개(gh api).
- 파일 확인(/tmp/lic_dl_8057, 임시): JMdict.gz 2026-11-01(kor gloss 131, entry 219,002), kanjidic2.xml.gz(2026-305, 13,108자, korean_h 7,060자), jpn-kor_links.tsv.bz2(2,262쌍), yomitan original_data CSV.
- 열지 못함: tanos.co.uk(DNS 실패), web.archive.org(WebFetch 차단 -> curl로 id_ 스냅샷 조회), EDRDG 위키(폐쇄 안내), creativecommons.org/compatiblelicenses/(404 -> 새 경로로 열람), CC FAQ 비교표(셀 내용 추출 안 됨).
- 열지 않음: CC0 1.0 법률 전문, Halpern SKIP 조건 페이지(SKIP 제외라 불필요), 글꼴 라이선스.
- 사용자에게 올릴 것: D-06 급수 목록 선택(S-06 권장 후보, 한자 급수 원천 없음), EDRDG 갱신 의무 충족 방법, 위키 폐쇄로 인한 표시 링크, Waller BY 버전 문의 여부.

## 2026-11-02 license-verify 1차 반영
- 반박 반영(재검증 필요): S-01.attribution(KANJIDIC 포함), attribution-links(위키 정적 사본), S-01.korean·K-03(한국어 gloss 0, kor 131은 lsource), S-02.korean(6,293자), S-02.skip(kanji.org: CC BY-SA 4.0, 직접 재확인), S-04.per-sentence(Licenses.php·weekly_exports.sql 직접 재확인), S-06 판(커밋 b062d4e), S-07 판(1ad6673)·chyyran LICENSE(WTFPL류), S-09 변환 범위(PDF), L-01.code, K-05.row, N-01, manifest S-08 shareAlike→true, S-10 shareAlike→null.
- 매니페스트 status는 모두 그대로(핵심 행이 전부 확정인 자료 없음).
- 남은 결정: S-01 갱신 주기, S-08 제외 확정(D-06), S-03 COPYING 기준 여부, EDRDG 링크 요건 문의.

## 2026-11-02 설계 검증 지적 반영
- 직접 확인: ftp.edrdg.org/pub/Nihongo 목록(날짜별 보관본 없음), S-06 커밋 고정 raw CSV 5개 내려받아 sha256 계산(/tmp/s06_*), Tatoeba per_language jpn·kor 목록·HEAD(kor_sentences_CC0 404), sentences_CC0.tar.bz2 집계(kor 0, jpn 2), google/material-design-icons README·LICENSE·API(Apache-2.0, NOTICE 없음, commit 10ecc14).
- 매니페스트: pinning·files·links·extraCredits 필드 추가, S-02 usedFields 축소, S-11 추가.
- 코드 영향(content-engineer): fetch가 `files`와 `pinning.pinnedCopy`를 읽어야 함. attribution.json이 links·extraCredits를 옮겨야 함. S-11은 scope app-asset이라 DB sources 규칙 밖.

## 2026-11-02 architecture.md 11.3 반영
- 직접 확인: CC BY-SA 4.0 legalcode(§1 Share, §2(a)(1), §3(a)(1)·(2), §3(b)), CC BY 2.0 FR legalcode(3절, 4(a)·(b)), Bluskyo/JLPT_Vocabulary LICENSE(MIT, Copyright (c) 2026 Bluskyo), GitHub Docs 릴리스 관리(immutable 릴리스만 자산 고정), KanjiVG r20260714 releases API(immutable=false, main.zip digest sha256:b5df6cd2…d1a7fc), material-design-icons 10ecc147에서 아이콘 24개 × (기본, fill1) 48개 HTTP 200.
- 매니페스트: pinning.method(S-01·S-02·S-03·S-04 snapshot, S-06 url), S-03 pinning 새로 둠, S-01 usedFields에서 ke_pri·re_pri 뺌, S-02 extraCredits 한국어 문구, S-09 extraCredits(Waller), S-11 usedFields 아이콘 이름 24개 배열(경로는 modifications로).
- decisions.md D-08 추가(결정 안 함).
- 코드 영향: ic_ms_* 대조 테스트가 채움 1 파일 이름(예: ic_ms_home_fill1)을 어떻게 다룰지 architecture가 정해야 함(usedFields는 기본 이름만). S-06은 method url이지만 levels-S-06 tsv 스냅샷 커밋 여부는 D-08.

## 2026-11-02 license-verify 재검증 반영
- 직접 확인: CC BY 1.0·2.0·2.5·3.0·4.0 legalcode(§4(a) 라이선스 사본/URI, §4(b) 저작권 고지·변형물 크레딧, 3.0 §3(b) 변경 표시, 4.0 §3(a)(1)(A)·(B)·(C)·§3(a)(4)), https://www.edrdg.org/jmdict/edict_doc.html(581 B 안내 페이지, 닫힌 위키로 meta refresh).
- 확정 표시: S-01.attribution·version·pinning, S-02.attribution·version·korean-credit·pinning·skip, S-05.license, S-06.license·origin·content·files·compat, S-11.license·version, L-01.code, K-03.input.
- 미확정: S-06.credit(링크 의무가 BY 판에 따라 다름), S-11.attribution(링크만으로 Apache §4(a) 사본 제공이 되는지).
- 반박 → 수정: S-01.attribution-links(edict_doc.html 뺌, 위키 URL 둘), S-06.compat-allversions(새 행, 표시 의무를 새 행 S-06.waller-notice로 나눔, 검증 대기).
- 매니페스트 status 변경 없음(S-01·S-02·S-03·S-06·S-11 모두 unconfirmed). S-01·S-02는 links 근거 행이 수정됨이라 보수적으로 핵심 행으로 봄.
- decisions.md D-06: 조건 미충족, 채택 보류.
- 코드 영향: S-06 extraCredits 문구 확장(© Jonathan Waller, 변경 표시)은 S-06.waller-notice 확정 뒤 매니페스트에 반영 → 출처 화면 문구 바뀜. S-11은 apache-2.0.txt 사본 동봉을 구현 QA에서 대조.

## 2026-11-02 license-verify 2차 재검증 반영
- 확정: S-01.attribution-links, S-06.credit, S-06.compat-allversions, S-11.attribution. 매니페스트 S-01·S-02·S-11 status → confirmed.
- 반박(검증자 1/2): S-06.waller-notice "판 공통 최대" 목록에 제목(제공 시, 1.0~3.0 §4(b)), 라이선스·보증 부인 고지 유지(1.0~3.0 §4(a), 4.0 §3(a)(1)(A)(iii)(iv)), 라이선서 지정 저작물 URI 누락 → 직접 재열람(legalcode 1.0·2.0·3.0·4.0, 보관본 sharing·홈)으로 추가. 보관본 sharing에 보증 부인 문구 없음. 매니페스트 extraCredits는 기존 항목이 채워 변경 없음.
- S-06 status unconfirmed 유지, decisions.md D-06 "조건 미충족, 채택 보류" 갱신.
- 남은 일: EDRDG licence.html의 "문서·라이선스 파일 사본 동봉(불가 시 링크)" 항목은 S-01.docs가 다룸(앱이 사본을 넣을지 compose-app-dev 확인). 급수별 목록 페이지 제목은 보관본 HTTP 500으로 미확인.

## 2026-11-02 license-verify 3차 재검증 반영(S-06.waller-notice)
- 반박: 급수별 목록 제목 "미확인(HTTP 500)"이 틀림. curl로 2020 보관본 5개 재열람, 모두 HTTP 200, title·H1 "JLPT N5 vocab list"~"JLPT N1 vocab list" (N5 20200728231522, N4 20200629172633, N3 20200629172607, N2 20200629172602, N1 20200218172500). 500은 최신 2023-06-05 스냅샷만.
- 반영: sources.md S-06.waller-notice (2)에 급수별 제목 추가 → 미확정(수정됨, 재검증 필요). manifest S-06 extraCredits[0] 문구에 급수별 제목 추가, S-06.credit 인용 맞춤 → 수정됨. S-06 status unconfirmed, D-06 조건 미충족·채택 보류 유지.
- 코드 영향: 출처 화면이 extraCredits[0].text를 그대로 보이면 문구가 길어진다(줄바꿈 확인 필요). 파이프라인이 매니페스트를 다시 읽기만 하면 됨.
