# M13 필순 데이터(KanjiVG) 구현 요약

## 착수 시 상태(인용)
- manifest S-03: `status: confirmed`, `checkedAt: 2026-11-03`, `version: r20260714`, `sha256: null`, `pinning.method: snapshot`.
- G-S03(`docs/spec/milestones.json`): `status: done`, note "2026-11-03 T03: S-03.attribution·sharealike·pinning 재검증 확정, 매니페스트 S-03 confirmed(P-S03 기준 문서 잠정)".

## 한 일
- `content/pipeline/pl_strokes.py`(새): main 묶음 SVG에서 `kvg:StrokePaths_<코드>` 안 `path id="kvg:<코드>-s<n>"`의 `d`만 읽고 n 숫자 오름차순으로 낸다. `kvg:element`·`kvg:radical`·`kvg:type` 속성과 `kvg:StrokeNumbers` 그룹은 읽지 않는다. viewBox가 `0 0 109 109`가 아니면 `FAIL: parse S-03 viewBox <값>`. 획 번호가 1..N이 아니거나 중복·path id 불일치도 FAIL.
- `pl_snapshot.py`: refresh-snapshot이 S-03을 fetch(체크섬 대조)하고 `S-03-r20260714.jsonl.gz`를 쓴다. 한자 집합은 S-02 스냅샷의 한자 전부(S-01·S-02가 이미 급수 목록의 모든 급수를 담는 것과 같은 방식이라 N3 이후 확장에도 다시 받지 않는다). DB 한자는 그 부분집합이고 build가 그중 DB에 있는 것만 `kanji_stroke`에 넣는다. `read_snapshot`이 `strokes`를 돌려준다. 스냅샷 `LICENSE.md`·`sources.json`에 S-03이 들어갔다.
- `pl_db.py`: `kanji_stroke` 행(`paths_json`은 `jdump`한 JSON 문자열 배열, `source_id` = S-03)과 `_workspace/03_content-engineer_M13_strokes_missing.tsv`(머리말 없음, 행 = 한자\tN급수) 생성.
- `check.py`: `kanji_stroke.paths_json`이 비지 않은 문자열 배열인지 검사 추가(literal ⊂ kanji와 source_id 확정 검사는 기존 것이 처리).
- `config.json`: `milestone` M26a → M13(보고 파일 이름용, content_version에 영향 없음).
- 테스트: `test_strokes.py`(합성 SVG의 element·radical·type·StrokeNumbers 부재, `-s10`·`-s11`이 s2 뒤에 오는 숫자 순서, viewBox FAIL, 번호 불연속 FAIL, 릴리스 흐름에서 스냅샷 결정성·kanji_stroke 수 = kanji 수 − missing 행 수·source_id·user_version 2·attribution S-03·check 통과). `support.py`에 합성 S-03 항목 추가, `test_release.py`의 자료 목록 기대값에 S-03 반영. 파이썬 테스트 164개 통과.
- `ContentDbTest.kt`: "새 여섯 테이블 count가 모두 0" 단언이 재빌드된 DB(문법·문항·청해·필순 행 있음)에서 깨져 `kanji_stroke > 0`, 나머지는 쿼리가 실행됨(≥ 0)으로 바꿈. 이 테스트는 HEAD DB가 N5만이던 시절의 값이었다.

## 완료 조건 대조
| 조건 | 결과 |
| --- | --- |
| 착수 시 상태 인용 | 위 |
| fetch 체크섬 | 리뷰 수정으로 매니페스트 S-03 `sha256`에 `b5df6cd2bc249dd49b8041eb99e38ba9f8dc6b9ea57962a80084b4a315d1a7fc`를 기록함(이제 fetch가 대조·불일치 시 FAIL). 이 값은 sources.md S-03.pinning에 적힌 GitHub 자산 digest(`sha256:b5df6cd2…d1a7fc`)와 전체가 같음(`shasum -a 256` 직접 계산도 동일). (content-licensing-analyst 영역의 파일을 고쳤으니 analyst가 확인할 것) |
| S-03 스냅샷 결정적 | 같은 zip에서 두 번 만든 sha256 둘 다 `5219538c5c0daf7d3b21840bf118cc03baa522762e8848d8b0ecaeed0e263a1f`, 커밋된 파일과 같음 |
| kanji_stroke ⊂ kanji, source_id S-03 | sqlite3: literal이 kanji에 없는 행 0, `SELECT DISTINCT source_id` = S-03 |
| 행 수 | sqlite3: `kanji_stroke` 727 = `kanji` 727 − strokes_missing 0행 |
| 빠진 한자 목록 | 없음(DB 한자 727개 모두 KanjiVG에 있음). `strokes_missing.tsv`는 0바이트. 참고: S-02 스냅샷 한자 2896 중 KanjiVG에 없는 39개는 N5·N4 DB에 들어오지 않는 한자라 해당 없음 |
| 파서 테스트 | `test_strokes.py` 통과 |
| attribution.json S-03 | 저작자 표시 "KanjiVG © 2009-2026 Ulrich Apel, CC BY-SA 3.0 (https://kanjivg.tagaini.net/)", licenseUrl `https://creativecommons.org/licenses/by-sa/3.0/`, 변경 내용 "SVG 획 경로·획 순서를 앱 형식으로 변환". attribution_fallback.xml·dist 묶음도 같은 입력으로 갱신 |
| user_version, 스키마 | `PRAGMA user_version` = 2, `app/schemas/…ContentDatabase/`에 새 파일 없음 |
| raw 없이 전체 빌드·check | `content/raw`를 치우고 `python3 -I content/pipeline/build.py` 통과(`check: OK`), 두 번 빌드한 content.db sha1 동일(`7661413d…`). content.db 크기 1,675,264 바이트(예산 12 MiB). S-03 스냅샷 1,057,466 바이트 |
| verify.sh | 종료 코드 0, 로그 `_workspace/build/verify_20261107_091723.log` |

## 알아 둘 것
- (해소) 매니페스트 S-03 `sha256`이 null이었다. 그대로면 같은 URL의 자산이 바뀌어도 fetch가 못 잡는다. content-licensing-analyst가 위 sha256(GitHub digest와 일치 확인됨)을 매니페스트에 기록해야 한다. sources.md는 "파이프라인이 채운다"고 적었지만 fetch 코드는 매니페스트를 고치지 않는다.
- 커밋된 `S-01-2026-11-01.jsonl.gz`는 현재 코드·raw로 refresh-snapshot을 하면 2항목(kana 전용 `こと`·`もし` 등)이 빠진 다른 파일이 나온다(이미 있던 불일치, M13 변경과 무관). "나머지는 스냅샷에서 다시 빌드"에 따라 S-01 스냅샷은 커밋본으로 되돌렸다. 필요하면 별도 갱신 작업으로 다룬다.
- 재빌드한 번들 DB는 HEAD의 DB(N5 667어휘, 문법 0)와 내용이 크게 다르다(1297어휘, 727한자, 문법 24, 문항 62, 청해 17). HEAD DB가 현재 config·ko-gloss·authored보다 오래된 것이었다. `content/ids/release/{kanji,vocab}.txt`가 늘고 `grammar·question·listening.txt`가 새로 생겼다.
- `architecture.md`와 다른 점은 없다(스키마 변경 없음). 6.7·sources.md S-03.pinning의 "DB 한자" 문구를 "S-02 스냅샷 한자 중 KanjiVG에 있는 것(모든 급수, DB 한자는 부분집합)"으로 고쳤다(리뷰 지적 반영, 문구만 바뀌고 스키마·코드 변화 없음). architecture/analyst 담당이 이 문구 수정을 확인해야 한다.

## 리뷰 수정 (2차)
- 획 수 불일치: KANJIDIC2 `kanji.stroke_count`와 KanjiVG 경로 수가 다른 한자 2개 - 飴(13 vs 14), 噌(14 vs 15). 그림은 KanjiVG 기준이라 둘 다 kanji_stroke에 둠(strokes_missing으로 돌리지 않음). check는 한 줄이라도 내면 실패라 목록용으로 쓰지 못해, release 빌드가 `_workspace/03_content-engineer_M13_strokes_count_mismatch.tsv`(한자\tstroke_count\t경로 수)를 남기게 함. 화면의 "{n}획"을 어느 값으로 쓸지는 android-architect/feature-engineer 판단.
- 스냅샷 범위(2857자 vs DB 727자): 고치지 않음(rejectedFindings). 명세 6.7·sources.md S-03.pinning의 문구 수정은 architecture/analyst 몫이다.
