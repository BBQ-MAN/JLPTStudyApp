# M3 구현 요약 (content-engineer)

verify.sh 종료 코드 0 — 로그 `_workspace/build/verify_20261102_063655.log` (check.sh 포함, 단위 테스트 73개).

## 만든 것
- `content/pipeline/`: `build.py`(진입점), `check.py`+`check.sh`, `pl_*.py`(common·fetch·parse·snapshot·authored·db·attr), `levels/`(seq·form 어댑터, S-06 읽기), `config.json`, `test_*.py`·`support.py`.
- `content/notices.ko.json`(고지·meaningSourceTexts 문구 틀), `content/authored/sample/{vocab,kanji}.jsonl`(N5 어휘 20, 한자 8), `content/ids/sample/`.
- 에셋: `app/src/main/assets/content/{content.db, content.db.sha256, attribution.json}`, `app/src/main/res/values/attribution_fallback.xml`.
- `.gitignore`: `/content/work/`, `/content/dist/` 추가(`content/raw`는 있었음, `content/snapshot`은 없음).
- 앱 테스트: `ContentDbTest`에 번들 에셋 열기 테스트 추가. 에셋이 생겨 "에셋 없으면 예외" 테스트는 삭제했고, `AppStartupTest`의 ContentDbFailed 테스트는 던지는 `openContent`로 바꿨다.

## architecture.md와 다른 점 / 판단한 점 (확인 필요)
1. `config.json`은 `kind: sample`로 커밋했다(6.1 예시는 release). 커밋된 샘플 에셋을 `build.py`가 그대로 다시 만들려면 sample이어야 한다. 또 `milestone` 키(보고서 파일명용)를 더했다.
2. `content/snapshot/sources.json`(자료별 판·원본 파일 sha256)을 스냅샷에 더했다. 기본 빌드는 content/raw 없이 `content_meta.sources.files`를 채워야 하기 때문이다. 6.7 목록에 없음.
3. 스냅샷 JSON 키는 `seq`(ent_seq 아님), 한자 키는 `stroke_count`·`ja_on`·`ja_kun`·`korean_h`.
4. `content/ids/<kind>/{vocab,kanji}.txt`, `retired.tsv`: 빌드가 합집합으로 갱신하고, 사라진 ID는 자동으로 `retired`에 넣는다. `check`는 ids 파일의 ID가 DB 또는 retired에 없으면 실패한다. 자동 retire이므로 실수로 사라진 ID는 빌드 로그·retired.tsv diff로 봐야 한다.
5. N-10의 "번역에는 AI(…)를 썼습니다"는 jmdict-translated 행의 모델만 쓴다. `modelNames`는 명세대로 DB 전체 `ko_method`의 모델(정렬).
6. 샘플 뜻의 `ko_method`는 `llm:claude-sonnet-5-5@2026-11-02`, `unreviewed`(내가 쓴 것이라 human으로 적지 않음).
7. N-12 문구는 D-04가 미정이라 A안(기기 TTS) 문장만 틀에 넣었다. 청해 테이블이 생기기 전에는 쓰이지 않는다.
8. `authored` 단계의 형식 검사는 어휘·한자·ko-gloss·level-overrides까지만 구현했다. 문법·문항(후리가나 괄호, `[[…]]`, 정답 색인)은 해당 테이블이 생기는 마일스톤(M14, M19)에서 더한다. 스키마 JSON이 바뀌어도 빈 새 테이블은 코드 변경 없이 만들어지고 `check`의 P 열 묶음 검사는 grammar·question·listening 테이블이 생기면 자동 적용된다.
9. 행 수 범위(6.3)는 "채택 어휘 수 ≥ 목록 행 수의 90%"를 급수별 DB 행 수로 센다. 같은 항목이 여러 급수에 나오면 쉬운 급수에만 세므로 N4 이후 목록에 N5 중복이 많으면 오탐 가능성이 있다(명세 그대로 구현, 실제 데이터 도입 때 확인).
10. 파일 이름 안의 판 문자열은 `[^0-9A-Za-z.-]+ → _`로 바꾼다(`2026-305 (2026-11-01)` → `2026-305_2026-11-01`).

## 하지 않은 것
- 실제 원본 다운로드·실제 스냅샷 생성(합성 픽스처로만 시험). 매니페스트의 S-01·S-02 sha256은 여전히 null이라, 실제 `refresh-snapshot`이 계산값을 출력하면 analyst가 채워야 한다.
- release 빌드 실행(levelSource D-06 미정).

## 라이선스 메모
없음. 샘플 한자 읽기·획수는 직접 쓴 값이고 KANJIDIC2에서 가져오지 않았다(source_id authored).
