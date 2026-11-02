# M8 content-engineer 요약

## 착수 상태
- 매니페스트: S-01·S-02·S-06 status=confirmed. S-01·S-02 sha256=null이었음(아래에서 기록함. S-06 files[]는 sha256 있음, 모두 일치).
- decisions.md D-06: "잠정 채택(조건부, 사용자 최종 검토 대기)". 본문 절은 아직 "미정" 문구가 남아 있음(analyst가 정리 필요). D-08 잠정 채택(급수 목록 스냅샷 A 방식 커밋).
- config.json: kind=sample 유지(committed 샘플 DB와 check 일치), levelSource=S-06, levels=[5], milestone=M8.

## 계산 sha256 (매니페스트 갱신 요청: content-licensing-analyst)
- S-01 JMdict_e.gz cda250dc3be14e3b04b584c512f42a0ab510bbe5c61d8d575ca2f023b63c2829
- S-02 kanjidic2.xml.gz 68c2e554bab8ad39a054cc6e964b8307787a833a7ffbb3ab4d25ccb946808d1e
- 갱신 완료: 위 두 값을 docs/content/manifest.json에 기입했다(재검증: content/raw 파일 sha256 직접 계산과 일치). 매니페스트 갱신은 원래 content-licensing-analyst 영역이므로 값 확인·승인을 요청한다. 기입 뒤 `python3 -I content/pipeline/build.py refresh-snapshot`을 content/raw로 다시 실행(다시 받지 않음)했고 null 경고 없이 통과, 스냅샷 파일 전체 sha256이 실행 전과 같다.
- 주의: 두 URL은 매일 덮어쓰이므로 이 값은 오늘 받은 판이다(매니페스트 version은 2026-11-01). 이미 받은 content/raw로 재실행해 같은 값 확인(다시 받지 않음). 매니페스트 갱신 전에는 null 경고만 나온다.
- S-06 n5..n1.csv 5개 sha256 매니페스트와 일치.

## 스냅샷 (content/snapshot/)
S-01-2026-11-01.jsonl.gz(7745 항목), S-02-2026-305_2026-11-01.jsonl.gz(2896 한자), levels-S-06-b062d4e_2025-08-26.tsv(8293행, N5~N1), sources.json, LICENSE.md. 두 번 만든 sha256 동일(S-01 5c3ccfe7..., 급수 f99477f2...). 커밋은 하지 않음(저장소에 커밋이 아직 없고 사용자 지시 없음).

## N5
- 급수 목록 N5 행 684, 채택 667 (97.5%).
- unmatched.tsv(_workspace/03_content-engineer_M8_unmatched.tsv) 22행: N1 21행, N3 1행, N5 0행. N5 unmatched 0행은 level-overrides.tsv(content/authored/)로 급수 목록의 잘못된 jmdict_seq 7건을 고친 결과다(출처 S-06 전부, 행번호: 이전 seq → 바른 seq): 570행 釦 1182880(牡丹)→1123880, 253행 此れ 2216120(감탄사 hey)→1628530, 347행 其れ 2216210(there! look!)→1006970, 41행 彼 2847612(huh? eh?)→1000580, 245행 洋杯 2846389(cop)→1050390, 374행 段々 2546180(방언 thank you)→1597350, 591행 燐寸 2784220(match 경기)→1128430(성냥). N1·N3 해소는 해당 급수 마일스톤에서.
- content/work/en-gloss/N5.jsonl 667줄(vocabId, written, reading, pos, senses 영어만, enHash). content/raw를 치운 상태에서 같은 sha256(bd8a7ca3...).
- N5 한자 424자 파생(실데이터 재계산: 어휘 667, 한자 424, 누락 0; stroke_count·on·kun·korean 전부 채워짐, freq 비어 있는 것 4자)(KANJIDIC2 4.2 필드), kanji_missing.tsv 0행(_workspace/03_content-engineer_M8_kanji_missing.tsv).

## 코드 변경
- pl_db.py: export_gloss_input이 한자 파생(gloss_input_dataset)과 kanji_missing.tsv도 낸다.
- build.py: levelSource가 있으면 kind=sample에서도 export-gloss-input 실행.
- test_kanji_authored.py: KANJIDIC2 4.2 필드(stroke_count, freq, on, kun, korean)가 kanji 행에 실리는지 검사 추가.
- test_release.py: raw 없이 export 시 kanji_missing 보고 테스트 추가.
- architecture.md와 다른 점 없음.

## 검사
unittest 77개 OK, verify.sh 종료 코드 0.
