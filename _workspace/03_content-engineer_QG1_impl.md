# QG1 구현 요약 (content-engineer, 2026-11-08, worktree dev/quizgen)

정본: `docs/spec/milestones.json` QG1, `docs/spec/quizgen.md` 3절·8.2·9절, `docs/content/decisions.md` Q-01·Q-10. TDD: `test_quizgen.py`·`ContentSchemaV3Test.kt`·기존 테스트 단언을 먼저 고쳐 실패(import 오류·버전 단언)를 확인한 뒤 구현했다.

## 원본 확인
- `content/raw/S-01/JMdict_e.gz`는 메인 체크아웃 파일을 심볼릭 링크로 걸어 썼다(sha256 `cda250dc3be14e3b04b584c512f42a0ab510bbe5c61d8d575ca2f023b63c2829` = 매니페스트 S-01과 같음, 2026-11-01 판, `<entry>` 219,002). `/content/raw/`는 .gitignore 대상이라 커밋 대상 아님. 작업 뒤 링크를 지웠다(원본은 그대로).

## 만든 것
| 파일 | 내용 |
|------|------|
| `content/pipeline/pl_parse.py` | `parse_jmdict(path, xref=False)`: `xref=True`일 때만 `"xref"`(모든 sense `<xref>` 원문, 나온 순서, 중복 제거) |
| `content/pipeline/pl_common.py` | `hira()`(U+30A1–30F6 → −0x60) |
| `content/pipeline/pl_snapshot.py` | `related_file`, `write_related`(raw sha256 ≠ 매니페스트면 `FAIL: refresh-related S-01 raw sha256 differs from manifest`), `refresh_snapshot` 끝에서 호출, `read_snapshot`이 related를 읽고 없으면 `FAIL: snapshot …-related.jsonl.gz missing (run build.py refresh-related)` |
| `content/pipeline/build.py` | `refresh-related` 하위 명령 |
| `content/pipeline/pl_quizgen.py`(새) | `en_tokens`·`ko_phrases`·`ko_overlap`·`jm_form_rows`·`vocab_conflicts`(en·ko·xref)·`derive`·`conflicts_tsv` — 3.3·3.4 그대로 |
| `content/pipeline/pl_db.py` | Dataset에 두 표, `release_dataset`이 related를 `add_input`(content_version에 반영)·`sources.S-01.related` 해시·`derive` 호출, `build_db` 표 목록·충돌 보고서 |
| `content/pipeline/check.py` | `check_quizgen(con)`(7.2), `check_snapshot`이 `sources.S-01.related`를 파일 해시와 대조 |
| `content/pipeline/support.py` | TempRoot가 가장 큰 번호 스키마를 복사 |
| `content/pipeline/config.json` | `milestone` M26b → QG1(보고서 파일 이름 접두어만 바뀐다) |
| `app/.../data/content/ContentEntities.kt` | `JmFormEntity`·`VocabConflictEntity` |
| `app/.../data/content/ContentDatabase.kt` | 두 엔티티, `version = 3` (DAO 변경 없음) |
| `app/schemas/…ContentDatabase/3.json` | `:app:kspDebugKotlin`이 생성(14 엔티티, version 3). 1·2.json 무변경 |
| `content/snapshot/S-01-2026-11-01-related.jsonl.gz` | 새 스냅샷(커밋은 메인 세션) |
| `app/src/main/assets/content/{content.db,content.db.sha256,attribution.json}` | 재생성(kind release 유지). `attribution_fallback.xml` 무변경 |
| 테스트 | `test_quizgen.py`(새, 20개), `ContentSchemaV3Test.kt`(새, 3개), `ContentDbTest.kt`(version 3 + 두 표 count 테스트 추가), 기존 `test_{grammar,listening,question,strokes,build,release,check}.py` 단언을 v3로 |

## 실측 수치
| 항목 | 값 |
|------|----|
| related 스냅샷 | 18,336 행, gzip 290,373 B(비압축 1,361,029 B). sha256 `718c8b1b488a54757a3c0644b7b62d92bcc54b0421d7ba6828d26fc218aba935`. `refresh-related` 3회 동일. 설계 추정(3~5만 행, 0.6~1 MB)보다 작다 |
| jm_form | 4,498 행 (추정 7~10천보다 적음) |
| vocab_conflict | 1,297 행 = `jm:` 어휘 수(vocab 1,297, 착수 전과 같음). 대칭 쌍 16,813 |
| 충돌 수 | 평균 25.93(N5 667어 28.92, N4 630어 22.76), 최대 202(`本`), 0개인 어휘 73 |
| conflict_ids JSON 길이 | 평균 338 B, 최대 2,627 B |
| 충돌 이유(쌍) | en만 11,139 · ko만 4,006 · en+ko 1,600 · xref 포함 68 |
| content.db | 2,547,712 B (착수 전 1,691,648 B → +856,064 B, +0.82 MiB; 12 MiB 예산의 21%). jm_form 300 KB(표+PK 인덱스+reading 인덱스), vocab_conflict 557 KB |
| 충돌 보고 | `_workspace/03_content-engineer_QG1_quizgen_conflicts.tsv`(16,813행+머리, 커밋 안 함) |
| 재현성 | raw 없이 `python3 -I content/pipeline/build.py` 2회 content.db sha256 `e7723db8981193daf399baf1d17511e9f36654ea259e5d9dcee357cedd5b53cb` 동일, `content.db.sha256`과 같음. stale gloss 0행, `PRAGMA user_version` 3 |
| 기존 스냅샷·스키마 | `git diff --exit-code`로 S-01·S-02·S-03·levels 스냅샷과 1·2.json 바이트 그대로(종료 0). `content/ids`·`sources.json` 변경 없음 |

## 箸·端·橋
- 번들 vocab에 橋/はし(`jm:1237410`)·箸/はし(`jm:1476410`)가 있다(端는 vocab 밖). jm_form `reading='はし'` 행에 (橋)(箸)(端)가 모두 있다. 그 밖에 嘴·喙·梯·階·愛し·段階 등 12행.
- `written IS NOT NULL`인 `jm:` vocab 994개 전부의 (written, hira(reading)) 행이 있다. 단 `jm:1350080 消しゴム`의 vocab.reading은 `けしゴム`(혼합 표기)라 jm_form에는 hira된 `けしごむ`로 들어 있어 raw SQL로 `reading` 문자열 그대로 조회하면 1건이 안 걸린다. 3.3 규칙(reading = hira(reb))대로이며 `check_quizgen`은 hira를 적용해 통과한다. **QG3 어댑터와 오라클은 vocab.reading에 `Kana.hira`를 적용한 뒤 jm_form을 조회해야 한다**(acceptance의 sqlite3 확인은 이 한 건을 hira 적용으로 해석했다).

## 단위 검사 한 줄 요약
- 파이프라인 `python3 -I -m unittest discover -s content/pipeline`: 185개 통과(착수 전 165). `content/pipeline/check.sh` 통과.
- `:app:testDebugUnitTest`: 525개 통과, 실패 0(ContentSchemaV3Test 3, ContentDbTest 4 포함). 처음 한 건 실패는 내가 엔티티 주석에 "JMdict"를 써서 `AboutFilesTest`(앱 코드에 출처 문구 금지)가 잡은 것이라 주석을 고쳤다.

## 결정·편차
1. `check_quizgen`은 "0행이면 통과"를 `jm:` 어휘가 없을 때로 한정했다. `jm:` 어휘가 있는데 두 표가 비면 `FAIL: quizgen … is empty while jm: vocab rows exist`(release 빌드에서 표가 조용히 비는 사고를 막는다). sample(`av-sample-…`만) 빌드는 0행으로 통과한다.
2. `config.json`의 `milestone`을 QG1로 바꿔 이번 빌드의 보고서(kanji_missing 등)가 `03_content-engineer_QG1_*.tsv`로 새로 생겼다(M26b 파일은 그대로 둠).
3. 앱 코드는 `ContentEntities.kt`·`ContentDatabase.kt`(엔티티·버전)와 `3.json`만 바꿨다. `core/quiz`는 건드리지 않았다. 2→3 로딩 경로는 4.5 그대로(에셋 해시로 파일 이름이 바뀌어 새 파일을 만들고 옛 `content_*.db`를 지운다). 마이그레이션 코드 없음.

## 위험(QG3가 먼저 알 것)
- **과배제가 크다(quizgen.md 9절 2).** 충돌 쌍의 66%가 영어 토큰만으로 생겼고 상위 증거가 `get`(516쌍)·`do`(503)·`come`(373)·`go`(323)·`time`(309)·`take`·`make`·`all`·`well`·`way`·`good`이다. 한국어 쪽도 한 글자 구절 부분 문자열(`물` ⊂ `게시물`·`화물`, `이`)로 걸린다. 그래서 `水`는 33개, `本`은 202개와 충돌한다. 이 때문에 ③ 뜻 유형의 같은 품사군 후보가 줄어 7.2의 N5 ③ ≥ 60% 하한을 못 넘을 수 있다. 규칙은 설계 그대로 구현했고 느슨하게 하지 않았다. 못 미치면 `needs-decision`(사용자·architect 판단: en 불용어에 일반 동사(get·do·come·go 등) 추가, 한국어 한 글자 구절 포함 판정 제외 같은 안은 파이프라인 규칙 변경이다).
- 번들 `reviewed` 한국어 뜻은 N4 63어뿐이라 N4 ③은 적게 나온다(설계 9절 2).

## analyst 요청(매니페스트·sources.md·attribution 문구는 고치지 않았다, quizgen.md 3.5)
1. `docs/content/manifest.json` S-01 `usedFields`에 `sense/xref` 추가.
2. S-01 `modifications`에 취지 문장 추가: "급수 목록 항목과 표기·읽기를 공유하는 항목의 표기·읽기 쌍, 뜻 겹침·상호참조로 만든 단어 충돌 목록(연습 문제 오답 배제용)을 추가".
3. 위 갱신 뒤 `build.py`를 다시 돌려 `attribution.json`·`attribution_fallback.xml`을 재생성한다(현재 attribution.json은 contentVersion만 바뀌었다). 출시 전에 반영해야 CC BY-SA §3(a)(1)(B) 변경 표시가 정확하다(quizgen.md 9절 5).
4. `content/snapshot/LICENSE.md`는 `refresh-snapshot`이 매니페스트에서 만드는 파일이라 이번에 다시 만들지 않았다(related 파일 설명 문장은 analyst의 modifications 갱신 뒤 `refresh-snapshot`/수동 갱신 때 들어간다).

## 막힌 점·남은 것
- `verify.sh` 종료 코드 0(`PASS: content/pipeline/check.sh + test lintDebug assembleDebug`, 로그 `_workspace/build/verify_20261108_074819.log`). 앞선 3번의 실패는 QG2 쪽 원인이라 고치지 않았다: 1) `core/quiz/.../gen/Kana.kt:36` 컴파일 오류(작업 중 파일), 2) `:core:quiz:test` `EOFException`, 3) `:app:testDebugUnitTest` 캐시 저장 `results-generic.bin` 파일 모드 오류 — 2·3은 같은 worktree에서 QG2의 Gradle과 동시에 돌아 빌드 폴더가 겹친 경합이다. QG2가 쉬는 사이 다시 돌려 통과.
- 스냅샷·에셋 커밋은 하지 않았다(메인 세션).
