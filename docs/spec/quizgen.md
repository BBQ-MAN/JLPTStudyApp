# 자동 생성 연습 문제(quizgen) — 설계

작성: 2026-11-08 · 작성자: android-architect · 상태: 잠정 확정(결정은 `docs/content/decisions.md` Q-01~Q-10, 사용자 최종 검토 대기)
입력: `docs/spec/architecture.md`(4.2·4.5·6·8·9.2), `docs/content/decisions.md`, `core/quiz/.../Quiz.kt`, `app/.../data/content/{ContentEntities,ContentDao,ContentDatabase,QuizContentRepository}.kt`, `app/.../data/QuizSessionLoader.kt`, `app/.../ui/practice/*`, `app/.../data/study/QuizRepository.kt`, `content/pipeline/{build.py,pl_db.py,pl_parse.py,pl_snapshot.py,check.py,levels/*}`, `content/ko-gloss/N{4,5}.jsonl`, `_workspace/03_content-engineer_T10_refresh.md`

LLM·네트워크 없이 번들 콘텐츠 DB만으로 4지선다 연습 문제를 규칙으로 만든다. 정답은 늘 DB 값이고, 오답은 유형마다 정한 **배제 규칙**을 통과한 것만 쓴다. 배제 규칙의 입력(JMdict 전체에서 뽑은 표기·읽기 쌍, 뜻 충돌 목록)은 파이프라인이 만들어 콘텐츠 DB v3에 넣는다.

## 0. 결정 요약

| 항목 | 결정 | 절 | 결정 ID |
|------|------|----|---------|
| 배제 데이터 | 파이프라인이 전체 JMdict에서 파생한 **`jm_form`(표기·읽기 쌍)·`vocab_conflict`(뜻 충돌 목록)** 두 표를 콘텐츠 DB v3에 넣는다. 영어 gloss·xref 원문은 번들하지 않는다(AR-U04 유지) | 3 | Q-01 |
| 원본 범위 | 기존 S-01 스냅샷은 급수 목록 항목(7,745)뿐이라 동음·동형 항목이 없다. 새 스냅샷 파일 `S-01-<판>-related.jsonl.gz`(동형·동음 1단계 + xref)를 더한다. 기존 스냅샷 파일은 바이트 그대로 | 3.2 | Q-01, Q-10 |
| 모듈 | 새 Gradle 모듈 없이 `:core:quiz`에 패키지 `com.jlptstudy.quiz.gen`을 더한다 | 5 | Q-02 |
| 유형 | ① 한자 읽기 `kanji-reading` ② 표기 `orthography` ③ 뜻 `meaning-ja-ko`·`meaning-ko-ja`. ④ 문맥 빈칸은 **만들지 않는다**(결정 기록만) | 4 | Q-04 |
| 미검수 뜻 | `reviewed`만 쓴다. `sampled`는 "그에 해당"하지 않는다(행 자체는 검수되지 않음) — 뜻 유형의 정답·오답·문제·해설 어디에도 쓰지 않는다 | 4.5, 6.4 | Q-03 |
| 정답 위치 | 세션 단위 균등 배치(10문항이면 한 자리 최대 3 = 30%). 생성 문항은 `QuizQuestion.fixedOrder = true`로 `Quiz.shuffle`이 순서를 바꾸지 않는다 | 5.3 | Q-06 |
| 문항 ID·재생성 | `gq:<유형>:<자리>:<시드36>:<어휘ID>`. 문항 내용을 저장하지 않고 ID로 다시 만든다 → 학습 기록 DB 스키마 변경 없음 | 6.1 | Q-07 |
| 학습 기록 연결 | 해설의 "관련 단어 보기"(S06)로만 잇는다. 퀴즈 결과로 FSRS 카드 상태·복습 로그를 쓰지 않는다 | 6.6 | Q-05 |
| 전수 검사 | `:app` Robolectric 테스트가 번들 `content.db`를 열어 N5·N4 전 단어 × 유형 × 시드 8개로 불변식 검사(새 의존성 없음) + 파이프라인 `check.py` 표 무결성 검사 | 7 | Q-08 |
| 대상 어휘 | `jm:` 어휘만(직접 작성 `av-…`는 JMdict 배제 데이터가 없어 제외). 오답은 같은 급수에서만 | 4.1 | Q-09 |

새 라이브러리 없음. 버전은 `gradle/libs.versions.toml` 그대로.

## 1. 범위와 금지

- 문제 본문은 단어 하나(또는 그 한국어 뜻 하나)뿐이다. 문장·지문을 만들지 않으므로 기출 문장을 복제할 경로가 없다. 시험 문항 번호·배점·척도 점수·합격 판정을 만들거나 보이지 않는다(결과는 기존 `Score`의 맞은 수/전체만, architecture 9.2.5).
- 생성 문항은 문제 화면(S13)·해설(S15)·연습 홈(S12) 행에 **"자동 생성 연습 문제"** 표시를 단다(6.5).
- 모의고사(S14)에는 들어가지 않는다(생성 문항은 `question` 풀에 없다).

## 2. 확인한 사실과 바로잡은 것(2026-11-08, Bash 없이 파일로 확인)

| 전달받은 사실 | 확인 결과 |
|---------------|-----------|
| `vocab`은 읽기 1개·표기 1개(nullable) | 맞음(`ContentEntities.kt` `VocabEntity`, architecture 4.2) |
| N5 667, N4 630 | `content/ko-gloss/N5.jsonl` 667행, `N4.jsonl` 630행(T10 보고서 vocab 1,297과 합 일치). "N5 표기 있음 502"는 DB를 열지 못해 확인하지 못함 — QG3 전수 검사 보고에서 확정 |
| 영어 gloss·다른 읽기·이표기·xref는 DB에 없음 | 맞음 |
| **"원본 전체 JMdict는 `content/snapshot/S-01-2026-11-01.jsonl.gz`에 있다"** | **틀림.** 스냅샷은 급수 목록에 채택된 항목과 unmatched 후보만 담는다(`pl_snapshot.refresh_snapshot`: `entries = kept if seq in keep`, T10 보고서 7,745항목 / JMdict 전체 219,002). 그래서 はし의 端·嘴 같은 목록 밖 동음 항목, 上手(うわて) 같은 목록 밖 동형 항목은 스냅샷에 없다 → 3.2 새 스냅샷 파일이 필요하다 |
| `pl_parse.py`가 xref를 안 읽음 | 맞음(keb·reb·첫 sense pos·sense uk·gloss·stagk·stagr만) |
| 전체 JMdict 원본 | 메인 체크아웃 `content/raw/S-01/JMdict_e.gz`가 있다(gitignore). 2026-11-01 판(sha256 `cda250dc…2829`)인지는 Bash가 없어 확인하지 못함. T10은 `/tmp` worktree에서만 새 판을 받았으므로 그대로일 가능성이 높다. 아니면 3.2의 FAIL 경로 |
| N4 뜻 `reviewed` 63·`sampled` 567, N5 667 전부 `reviewed` | 맞음(ko-gloss 파일 `reviewStatus` 개수) |
| 고정 문항·`QuizQuestion`·`QuizViewModel`·`StudyDatabase` v2 | 맞음 |

## 3. 배제 데이터 — 콘텐츠 DB v3

### 3.1 대안 비교(Q-01)

| 안 | 내용 | 정답 둘 방지 | 크기·라이선스 | 판정 |
|----|------|--------------|---------------|------|
| **A. 파생 표 2개(채택)** | 파이프라인이 전체 JMdict(동형·동음 1단계)로 `jm_form`(표기·읽기 쌍), 한국어 뜻·영어 gloss·xref로 `vocab_conflict`(같은 급수 안 충돌 목록)를 만들어 DB v3에 넣는다 | 동음 표기(はし→端), 동형 읽기(上手→うわて), 영어로만 겹치는 뜻(父/パパ "father"), xref 동의어를 막는다 | 추정 +0.9 MiB(아래), 영어 원문은 번들 안 함 | 채택 |
| B. DB 안 데이터만으로 보수 배제 | `vocab`의 표기·읽기 1쌍과 한국어 뜻만 사용 | 목록 밖 동음·동형 항목, 같은 항목의 다른 읽기(こんにち), 이표기(逢う)를 **모른다** → 표기 유형에서 端·嘴 같은 오답이 사실은 정답이 될 수 있다(고정 문항 4건과 같은 종류의 사고). 막으려면 한자 치환 오답을 아예 못 쓴다 | 증가 없음 | 기각(①② 안전성 부족) |
| C. 영어 gloss·xref 원문 번들 | `vocab_gloss_en`·`xref` 원문 표, 앱이 규칙 계산 | A와 같음 | 영어 원문이 앱 DB에 들어감 → AR-U04 변경, 규칙이 Kotlin·Python 두 곳 | 기각 |

### 3.2 새 스냅샷 파일 `content/snapshot/S-01-<version>-related.jsonl.gz`

- 내용: 전체 JMdict 항목 중 다음 둘의 합집합, `seq` 오름차순 한 줄 하나 `{"seq": int, "keb": [..], "reb": [..], "xref": [..]}`(JSON 키 정렬, gzip `mtime=0` — 6.7 결정성 규칙 그대로).
  1. 기존 S-01 스냅샷의 모든 항목(xref를 얻으려고).
  2. 키 집합 K와 표기 또는 읽기를 공유하는 항목. K = S-01 스냅샷 모든 항목의 `keb` ∪ `hira(reb)`. 항목 E는 `E.keb ∩ K ≠ ∅` 또는 `hira(E.reb) ∩ K ≠ ∅`이면 들어간다(1단계만, 반복하지 않는다).
- `xref` = 그 항목 모든 sense의 `<xref>` 원문(예 `母・はは・1`), 나온 순서, 중복 제거. sense에 영어 gloss가 없어도 읽는다.
- `hira(s)`: 가타카나 U+30A1–U+30F6 → −0x60(히라가나). `ー` 등 나머지는 그대로. 파이프라인 `pl_common.hira`, Kotlin `Kana.hira` 두 곳에 같은 정의(단위 테스트 기대값 공유: `hira("カン")=="かん"`, `hira("コーヒー")=="こーひー"`, `hira("ヴ")=="ゔ"`).
- 만드는 명령: `build.py refresh-related`(새 하위 명령). `content/raw/S-01/<files[0].path>`의 sha256이 매니페스트 S-01 `sha256`과 같을 때만 돈다. 다르면 `FAIL: refresh-related S-01 raw sha256 differs from manifest`로 멈춘다(새 판 채택은 U-10 사용자 결정). 기존 S-01·S-02·S-03·levels 스냅샷은 읽기만 하고 쓰지 않는다. `refresh-snapshot`도 끝에서 같은 함수를 불러 다음 판 갱신 때 함께 만든다.
- `pl_parse.parse_jmdict(path, xref=False)`: `xref=True`일 때만 항목에 최상위 키 `"xref"`를 붙인다. **S-01 스냅샷을 쓸 때는 `xref=False` 결과를 쓴다** — 기존 스냅샷 파일·`enHash`가 바이트 그대로여야 한다(QG1 acceptance).
- 빌드 입력: `build.py`(기본)는 related 파일을 읽고 `add_input`에 넣는다(content_version이 바뀐다). `content_meta.sources["S-01"]`에 `"related": "<sha256>"`를 더하고 `check_snapshot`이 그 값이 있으면 파일 해시와 대조한다. 파일이 없으면 `FAIL: snapshot S-01-<v>-related.jsonl.gz missing (run build.py refresh-related)`.
- 스냅샷 커밋: D-08 A(S-01 스냅샷 커밋)와 같은 취급. 커밋은 메인 세션이 사용자 지시로 한다(이 설계는 커밋을 지시하지 않는다).
- 크기 추정: 급수 목록 읽기의 동음 항목(한자어 읽기 こうしょう 등은 수십 개)을 합쳐 3~5만 항목, 비압축 3~4 MB, gzip 0.6~1 MB. QG1 보고서에 실측.

### 3.3 테이블 정의(`ContentDatabase` version 2 → 3)

Room 엔티티가 원본이다(architecture 4.2). 파이프라인은 `3.json`의 `createSql`을 그대로 실행한다. 모든 열 NOT NULL.

**`jm_form`** — 엔티티 `JmFormEntity`(`data/content/ContentEntities.kt`)

| 열 | 타입 | 내용 |
|----|------|------|
| `written` | TEXT | JMdict `keb` 원문 |
| `reading` | TEXT | JMdict `reb`의 `hira()` |
PK `(written, reading)`. 인덱스 `Index("reading")`.

```kotlin
@Entity(tableName = "jm_form", primaryKeys = ["written", "reading"], indices = [Index("reading")])
data class JmFormEntity(val written: String, val reading: String)
```

행 규칙(결정적): related 파일 모든 항목 E, `k ∈ E.keb`, `r ∈ E.reb`의 모든 조합 `(k, hira(r))` 중 `k ∈ Wdb` 또는 `hira(r) ∈ Rdb`인 것. `Wdb` = 이 DB `vocab` 중 `id LIKE 'jm:%' AND written IS NOT NULL`의 `written` 집합, `Rdb` = 같은 행들의 `hira(reading)` 집합. `re_restr`(읽기가 특정 표기에만 붙는 제한)는 읽지 않는다 — 실제로는 없는 조합까지 넣는 **과배제**이고 안전 쪽이다. 정렬 `(written, reading)`.

뜻: 표기 W의 가능한 읽기 전부 = `SELECT reading FROM jm_form WHERE written = W`(W ∈ Wdb일 때 완전), 읽기 R의 가능한 표기 전부 = `SELECT written FROM jm_form WHERE reading = R`(R ∈ Rdb일 때 완전).

**`vocab_conflict`** — 엔티티 `VocabConflictEntity`

| 열 | 타입 | 내용 |
|----|------|------|
| `vocab_id` | TEXT PK | `jm:` 어휘 ID |
| `conflict_ids` | TEXT | JSON 문자열 배열(정렬·중복 없음, 자기 자신 없음). 같은 `level`의 `jm:` 어휘 중 3.4 규칙으로 충돌하는 것. 없으면 `[]` |

```kotlin
@Entity(tableName = "vocab_conflict")
data class VocabConflictEntity(@PrimaryKey @ColumnInfo(name = "vocab_id") val vocabId: String, @ColumnInfo(name = "conflict_ids") val conflictIds: String)
```

DB의 모든 `jm:` 어휘가 정확히 한 행을 가진다. 대칭이다(b ∈ conflicts(a) ⇔ a ∈ conflicts(b)). JSON은 9.0 공용 직렬화 함수(`jdump`). 쌍마다 행을 두지 않은 이유: 쌍 표는 PK 인덱스까지 행당 ~75 B라 N5+N4 추정 2만 쌍에 ~1.8 MiB, 목록 표는 ~0.45 MiB.

크기 추정(N5+N4, 1,297어): `jm_form` 7~10천 행 × ~60 B ≈ 0.5 MiB, `vocab_conflict` 1,297행 × 평균 충돌 25개 × 13 B ≈ 0.45 MiB. 합 +~0.9 MiB. 예산(architecture 6.3: N5만 2 MiB, 전체 12 MiB)은 그대로 `check_size`가 본다. shortcut: 충돌은 같은 급수 전 품사 쌍이다. N3 이상을 번들해 크기 예산 50%를 넘기면 같은 품사군(5.4 `posGroup`) 쌍으로 줄이고 `vocab.pos_group` 열을 더하는 v4를 만든다.

### 3.4 파생 규칙(파이프라인 `content/pipeline/pl_quizgen.py`)

입력: release 데이터셋의 `vocab`·`vocab_meaning`(모든 검수 상태 — 충돌 판정은 보수적으로 모든 뜻 행을 쓴다), S-01 스냅샷 항목(영어 gloss, 모든 sense — `stagk`·`stagr` 제한 무시), related 파일(keb·reb·xref). sample 빌드(`av-sample-…`만)에서는 두 표가 0행이다.

```python
EN_STOP = {"a","an","the","to","of","be","is","are","am","was","were","been","being","one","ones","someone","something",
           "somebody","somewhere","oneself","etc","eg","ie","in","on","at","by","for","with","from","into","onto","as","or",
           "and","not","it","its","that","this","than","up","out","off","over","so","such","very","esp","usu","oft"}
def en_tokens(entry):                 # 항목 하나의 모든 sense, 모든 gloss
    out = set()
    for s in entry["senses"]:
        for g in s["gloss"]:
            g = re.sub(r"\([^)]*\)", " ", g.lower())          # 괄호 설명 제거
            for t in re.findall(r"[a-z]+", g):
                if t.endswith("ies") and len(t) > 4: t = t[:-3] + "y"
                elif t.endswith("s") and not t.endswith("ss") and len(t) > 3: t = t[:-1]
                if len(t) > 1 and t not in EN_STOP: out.add(t)
    return out

def ko_phrases(texts):                 # 어휘 하나의 모든 vocab_meaning.text_ko
    out = set()
    for text in texts:
        for p in re.split(r"[,;/]", re.sub(r"\([^)]*\)", "", text)):
            p = re.sub(r"[\s~〜…·]", "", p)
            if p: out.add(p)
    return out

def ko_conflict(A, B): return any(a == b or a in b or b in a for a in A for b in B)

def xref_links(vocab_by_level, entry_of, xref_of):    # 같은 급수 안, 양방향, 1단계
    for v in jm_vocab:
        for x in xref_of[v.seq]:
            t = [s for s in x.split("・") if not s.isdigit()]
            head, second = t[0], (t[1] if len(t) > 1 else None)
            for w in same_level(v):
                e = entry_of[w.seq]
                if head in e["keb"] + e["reb"] and (second is None or second in e["reb"]):
                    link(v, w)                          # 대칭으로 둘 다

conflict(a, b) = a.level == b.level and a != b and (
    ko_conflict(ko_phrases(a), ko_phrases(b)) or en_tokens(a) & en_tokens(b) or xref_linked(a, b))
```

보고서(커밋 안 함): `_workspace/03_content-engineer_<마일스톤>_quizgen_conflicts.tsv` — 머리 `a\tb\tlevel\treasons\tevidence`(reasons는 `en`·`ko`·`xref`를 쉼표로, evidence는 겹친 토큰·구절·xref 원문 하나), `_workspace/…_quizgen_forms.txt`(행 수·크기). 앱은 이유를 쓰지 않으므로 DB에는 넣지 않는다.

### 3.5 라이선스·저작자 표시 영향

- 새로 쓰는 원 필드: JMdict `sense/xref`. 새 범위: 급수 목록 밖 항목의 `keb`·`reb`(동형·동음). 같은 자료(S-01, CC BY-SA 4.0, `confirmed`)이므로 새 출처·새 라이선스 판단은 없다. 영어 gloss는 기존대로 번들하지 않는다(파생 결과 `vocab_conflict`만 번들).
- 매니페스트·sources.md는 analyst 몫이다(architecture 11). QG1이 보고서로 요청한다: S-01 `usedFields`에 `sense/xref` 추가, `modifications`에 "급수 목록 항목과 표기·읽기를 공유하는 항목의 표기·읽기 쌍, 뜻 겹침·상호참조로 만든 단어 충돌 목록(연습 문제 오답 배제용)을 추가" 취지 문장 추가. 이 갱신이 출시 전에 있어야 CC BY-SA §3(a)(1)(B) "변경 표시"가 정확하다. 개발·검사는 막지 않는다(`check.py`가 `usedFields`를 대조하지 않음).
- architecture 4.2 "쓰는 필드" 문장과 6.7 스냅샷 목록은 이 문서를 가리키도록 한 줄씩 고쳤다.

### 3.6 영향 범위(v3를 올릴 때 바뀌는 곳 — 전부 QG1)

| 대상 | 변경 |
|------|------|
| `app/.../data/content/ContentEntities.kt` | `JmFormEntity`, `VocabConflictEntity` 추가 |
| `app/.../data/content/ContentDatabase.kt` | `entities`에 두 개 추가, `version = 3` |
| `app/schemas/com.jlptstudy.app.data.content.ContentDatabase/3.json` | 새로 생성(1.json·2.json 무변경) |
| `app/src/test/.../data/ContentDbTest.kt` | `assertEquals(2, …version)` → 3 |
| `app/src/test/.../data/ContentSchemaV3Test.kt`(새) | 3.json의 두 표 열·PK·인덱스 기대값 |
| `content/pipeline/pl_parse.py` | `xref` 인자 |
| `content/pipeline/pl_snapshot.py` | `write_related`, `snapshot_files`에 related 경로, `read_snapshot`이 related 읽음 |
| `content/pipeline/build.py` | `refresh-related` 하위 명령 |
| `content/pipeline/pl_quizgen.py`(새) | 3.3·3.4 |
| `content/pipeline/pl_db.py` | `build_db` 표 목록에 두 표, `ds.sources["S-01"]["related"]`, `add_input` |
| `content/pipeline/pl_common.py` | `hira` |
| `content/pipeline/check.py` | `check_quizgen`(7.2), `check_snapshot` related |
| `content/pipeline/support.py` | 픽스처가 `2.json` 고정 → 가장 큰 번호 스키마 |
| `content/pipeline/test_{listening,question,grammar,build}.py` | `["1.json","2.json"]`·`version == 2` 단언 → 3 |
| `content/pipeline/test_quizgen.py`(새) | 8.2 |
| `content/snapshot/S-01-2026-11-01-related.jsonl.gz`(새) | 3.2 |
| `app/src/main/assets/content/{content.db,content.db.sha256,attribution.json}` | 재생성(4.5 "버전을 올릴 때") |

학습 기록 DB(`StudyDatabase` v2)는 바꾸지 않는다 — 마이그레이션 마일스톤 없음.

## 4. 생성 알고리즘과 배제 규칙

### 4.1 공통

- **대상 은행(QuizBank)**: 한 급수 L의 `jm:` 어휘(retired 제외), 그 어휘들의 ord 0 한국어 뜻(`reviewed`일 때만 값, 아니면 null), 급수 ≥ L(같거나 쉬운) 한자, L 어휘 관련 `jm_form` 행, L 어휘의 `vocab_conflict`. 오답은 **같은 급수 L 어휘**에서만 고른다.
- **정규화**: 비교는 모두 NFC 문자열 그대로, 읽기 비교만 `hira()` 뒤. "보이는 글자"(visible) = 루비 표기 `{a|b}`에서 `a`만 남긴 문자열(`[[`·`]]` 제거).
- **난수**: `java.util.Random(seed)` + `java.util.Collections.shuffle`(기존 `Quiz`와 같음). 후보는 먼저 문자열 정렬한 뒤 섞는다(입력 순서에 따라 결과가 바뀌지 않게).
- **고르기**: 후보를 섞은 순서대로 보며 아래 "유효" 조건을 모두 만족하는 것만 받는다. 유효 오답이 3개 미만이면 그 단어는 **생성 불가**(`Skip.TOO_FEW_DISTRACTORS`). 규칙을 느슨하게 해서 채우지 않는다.
- **공통 유효 조건**: 보이는 글자가 비지 않음(공백 제거 후), 정답·이미 고른 오답과 보이는 글자가 다름(읽기는 `hira` 뒤 비교).

### 4.2 ① 한자 읽기 `kanji-reading`

- 대상: `written`에 CJK 한자(U+4E00–9FFF, U+3400–4DBF)가 1자 이상, `written != reading`, `reading`이 히라가나(U+3041–3096, `ー`)만. 아니면 `Skip.NOT_TARGET`.
- 문제: `stem = "[[" + W + "]]"`(루비 없음), `target = W`, 정답 `R = reading`.
- 배제 집합 `X1 = { r | (W, r) ∈ jm_form }`(그 단어 항목의 모든 읽기 + 같은 표기를 가진 다른 항목의 읽기 전부).
- 후보 C1(소리 변형, 4.6) → C2(같은 급수 다른 어휘의 읽기: 히라가나만, 글자 수가 |R|±1, W와 한자를 하나 이상 공유하는 단어 먼저, 그다음 나머지).
- 고르기: C1을 섞고 **종류가 다른 것 먼저**(첫 바퀴는 종류당 하나, 둘째 바퀴는 아무것) 최대 3개, 모자라면 C2를 섞은 순서로 채운다.
- 유효: 공통 + `hira(d) ∉ X1` + 변형이면 4.6 형식 규칙 통과.
- 이유(`DistractorKind`): `LONG_VOWEL`·`VOICING`·`SOKUON`·`YOON`·`OTHER_READING(sourceVocabId)`.

### 4.3 ② 표기 `orthography`

- 대상: ①과 같은 조건.
- 문제: `stem = "[[" + R + "]]"`, `target = R`, 정답 `W`.
- 배제 집합 `X2 = { w | (w, hira(R)) ∈ jm_form }`(같은 읽기를 가진 모든 항목의 표기 — はし → 橋·箸·端·嘴…, 그 단어의 이표기 — 会う → 逢う·遭う).
- 후보 C1(한자 치환): W의 각 한자 위치 i(그 글자가 은행 한자에 있을 때), 은행 한자 k′ ≠ W[i] 중 `on_readings`의 `hira` 값을 하나라도 공유하는 것(없으면 `kun_readings`의 `.` 앞부분·`-` 제거 값을 공유하는 것)으로 바꾼 문자열. C2(같은 급수 다른 어휘의 `written`: 글자 수가 |W|와 같고, 끝 가나 부분(마지막 한자 뒤 문자열)이 W와 같은 것 먼저).
- 고르기: C1을 섞고 **위치가 다른 것 먼저** 최대 3개, 모자라면 C2.
- 유효: 공통 + `x ∉ X2` + `x`에 한자 1자 이상.
- 이유: `KANJI_SWAP(from, to)`·`OTHER_WRITTEN(sourceVocabId)`.

### 4.4 ③ 뜻 `meaning-ja-ko`(일→한), `meaning-ko-ja`(한→일)

- 적격(정답·오답 공통): ord 0 뜻이 `reviewed`이고 비지 않음(은행의 `meaningKo != null`). 아니면 정답으로는 `Skip.UNREVIEWED`, 오답 후보에서는 뺀다.
- 표시: 일본어 쪽 visible = `written ?: reading`, 문자열은 `{표기|읽기}` 루비 표기(앱 어댑터가 `Furigana.fromHeadword`로 나눠 만든다, 6.2). 한국어 쪽 = ord 0 `text_ko` 그대로.
- `meaning-ja-ko`: `stem = "[[" + 일본어 + "]]"`, `target = visible`, 선택지 = 한국어 뜻 4개, 정답 = A의 뜻.
- `meaning-ko-ja`: `stem = A의 한국어 뜻`(한국어 필드, 루비·`[[ ]]` 없음), `target = null`, 선택지 = 일본어 4개, 정답 = A.
- 후보: 같은 급수, `posGroup(pos)`(5.4)이 A와 같고, 적격인 어휘 B ≠ A.
- 유효: 공통(보이는 글자 — ja-ko는 한국어 뜻, ko-ja는 일본어 visible — 가 서로 다름) + `B ∉ conflicts(A)`.
- 다의어: 표시는 ord 0만, 충돌 판정은 모든 뜻 행(3.4) — 정답의 둘째 뜻이 오답의 뜻과 같아도 걸린다.
- 이유: `OTHER_MEANING(sourceVocabId)`(ja-ko), `OTHER_WORD(sourceVocabId)`(ko-ja).

### 4.5 ④ 문맥 빈칸 — 만들지 않음(Q-04)

권고: **보류.** 근거: (1) 번들에 예문 자료가 없다(Tatoeba S-04 `unconfirmed`, 문법 예문은 문법 항목용 직접 작성 소량). (2) "품사·활용형이 달라 문법적으로 들어갈 수 없는 오답만"을 판정하려면 문장의 빈칸 자리 품사·활용을 알아야 하는데 형태소 분석기가 없고, 같은 품사 오답은 의미상 들어갈 수 있는지 규칙으로 판정할 수 없다. (3) 다른 품사만 오답으로 쓰면 품사만 보고 풀리는 문제가 된다. 다시 열 조건: 빈칸 자리에 품사군·활용형 주석을 단 직접 작성 문장 틀(`content/authored`, 사람 또는 에이전트 검수)과, 오답을 "자리 품사군과 다른 품사군"으로만 제한하는 규칙을 함께 설계할 때. 코드·유형 코드·화면 자리를 지금 만들지 않는다.

### 4.6 소리 변형(Kotlin `KanaVariants.of(reading): List<Pair<String, DistractorKind>>`)

입력은 히라가나 읽기 R. 한 번에 한 곳만 바꾼다. 결과는 중복 제거, 원래 R 제외, 정렬.

| 종류 | 규칙 |
|------|------|
| `VOICING` | 각 위치의 か행·さ행·た행(ち·つ 포함)·は행 ↔ 탁음, は행 ↔ ぱ행(は·ば·ぱ 세 꼴 서로). う↔ゔ 없음 |
| `SOKUON` | `っ` 하나 지우기 / `っ` → `つ`·`く` / 위치 i(1 ≤ i < |R|)의 글자가 か·さ·た·ぱ행 청음이고 앞 글자가 `っ`·`ん`·`ー`·작은 글자가 아니면 그 앞에 `っ` 넣기 / 뒤 글자가 か·さ·た·ぱ행 청음인 `く`·`き`·`つ`·`ち`(첫 글자 아님)를 `っ`로(がくせい → がっせい) |
| `LONG_VOWEL` | 지우기: 앞 글자 모음이 お·う단인 `う`, え·い단인 `い`, 같은 모음 뒤 `あ`·`え`·`お`, `ー`. 넣기: 모음이 お·う단인 글자(작은 ゃゅょ이면 그 모음) 뒤 `う`, え단 뒤 `い`(`ん`·`っ`·`ー`·문장 끝 뒤에는 넣지 않음) |
| `YOON` | 작은 ゃ·ゅ·ょ ↔ 큰 や·ゆ·よ / 작은 ゃ·ゅ·ょ 서로 바꾸기 / 작은 ゃ·ゅ·ょ 지우기 / い단 자음 글자(き·ぎ·し·じ·ち·ぢ·に·ひ·び·ぴ·み·り) 뒤 큰 や·ゆ·よ → 작게 |

형식 규칙(어기면 버림): 첫 글자가 `っ`·`ー`·작은 글자가 아님, 끝 글자가 `っ`가 아님, `っっ` 없음, `っ` 바로 뒤가 모음·`ん`·`っ`·`ー`·작은 글자가 아님, 작은 ゃゅょ는 위 い단 자음 글자 뒤에만.

### 4.7 정답이 둘이 되는 경우 → 막는 규칙

| # | 유형 | 경우(예) | 막는 규칙 | 남는 위험 |
|---|------|----------|-----------|-----------|
| 1 | ① | 그 단어의 다른 읽기(今日 → こんにち, 一昨日 → おとつい) | X1(같은 표기의 모든 항목) | — |
| 2 | ① | 같은 표기 다른 항목(上手 じょうず/うわて, 生物 せいぶつ/なまもの) | X1 + related 스냅샷(목록 밖 항목 포함) | — |
| 3 | ① | 변형이 우연히 맞는 읽기(日本 にほん → 촉음 넣기 にっぽん) | 변형도 X1 검사 | — |
| 4 | ① | 가타카나·히라가나 차이(カン/かん) | `hira` 뒤 비교 | — |
| 5 | ① | JMdict에 없는 관용·방언 읽기 | 못 막음 | 표본 검수(7.5) |
| 6 | ② | 동음 표기(はし → 橋·箸·端) | X2(같은 읽기의 모든 항목) + related | — |
| 7 | ② | 그 단어의 이표기·송り가나 변형(会う/逢う, 終わる/終る, 見る/観る) | X2(같은 항목의 keb) | — |
| 8 | ② | 한자 치환이 실재 표기(会う → 合う, 早い → 速い) | 치환 결과도 X2 검사 | — |
| 9 | ② | JMdict에 없는 이체자·구자체(學) | 치환 한자를 은행 한자(급수 어휘에 쓰인 상용 한자)로만 제한 | 표본 검수 |
| 10 | ③ | 같은 뜻 다른 단어(母/お母さん "어머니") | ko 같음 | — |
| 11 | ③ | 포함 관계(本 "책" / 本棚 "책장", 行く "가다" / 出かける "나가다") | ko 포함 | 과배제(안전) |
| 12 | ③ | 한국어는 다르나 영어가 같음(父 "아버지"/パパ "아빠" — father) | en 토큰 | — |
| 13 | ③ | JMdict 상호참조 동의어 | xref(양방향 1단계) | — |
| 14 | ③ | 다의어의 둘째 뜻 겹침(暑い/熱い "(마음이) 뜨겁다, 열렬하다") | 모든 뜻 행 비교 | — |
| 15 | ③ | 동형이의(方 かた/ほう)가 같은 글자로 보임 | 보이는 글자 서로 다름 | — |
| 16 | ③ | 미검수 오역 때문에 정답이 틀림 | `reviewed`만 | 에이전트 검수 오류(D-03) |
| 17 | ③ | 한국어·영어·xref 모두 안 겹치는 유의어 | 못 막음 | 표본 검수, 오류 보고 시 충돌 덮어쓰기 표(9절) |
| 18 | 전체 | 중복 보기 | 보이는 글자 서로 다름 | — |

## 5. 순수 Kotlin(`:core:quiz`, 패키지 `com.jlptstudy.quiz.gen`)

### 5.1 모듈 위치(Q-02)

새 모듈을 만들지 않는다. `:core:quiz`는 의존이 없고 테스트가 빠르며, 생성기는 `QuizQuestion`·`Section`을 쓰고 `fixedOrder`를 `Quiz.shuffle`에 더해야 해 같은 모듈이 자연스럽다. 새 모듈은 `settings.gradle.kts`·빌드 파일만 늘린다. 의존 추가 없음(JUnit만).

### 5.2 기존 코드 변경(`Quiz.kt`)

```kotlin
data class QuizQuestion(val id: String, val section: Section, val type: String, val choices: List<String>, val answerIndex: Int,
                        val fixedOrder: Boolean = false)   // true: 생성 문항 — 정답 자리를 생성기가 정했다
object Quiz {
    /** fixedOrder면 원래 순서 그대로(originalIndexOf = [0,1,2,3]). 아니면 지금과 같다. */
    fun shuffle(question: QuizQuestion, attemptSeed: Long): ShuffledQuestion
}
```

### 5.3 시그니처(`core/quiz/src/main/kotlin/com/jlptstudy/quiz/gen/`)

```kotlin
package com.jlptstudy.quiz.gen

enum class GenType(val code: String) { KANJI_READING("kanji-reading"), ORTHOGRAPHY("orthography"),
    MEANING_JA_KO("meaning-ja-ko"), MEANING_KO_JA("meaning-ko-ja");
    companion object { fun of(code: String): GenType? } }

/** 앱 Room 엔티티와 분리한 입력. meaningKo는 ord 0 뜻이 reviewed일 때만 값(어댑터가 거른다 — 코어는 검수 상태를 모른다). */
data class QuizVocab(val id: String, val written: String?, val reading: String, val level: Int, val pos: String, val meaningKo: String?)
data class QuizKanji(val literal: String, val level: Int, val on: List<String>, val kun: List<String>)
class QuizBank(
    val level: Int,
    val vocab: List<QuizVocab>,                    // 이 급수 jm: 어휘(retired 제외)
    val kanji: List<QuizKanji>,                    // 급수 >= level
    val forms: Set<Pair<String, String>>,          // jm_form (written, hira reading)
    val conflicts: Map<String, Set<String>>,       // vocab_conflict
)

enum class DistractorKind { LONG_VOWEL, VOICING, SOKUON, YOON, OTHER_READING, KANJI_SWAP, OTHER_WRITTEN, OTHER_MEANING, OTHER_WORD }
data class Distractor(val text: String, val kind: DistractorKind, val sourceVocabId: String? = null, val swap: Pair<String, String>? = null)

/** choices는 보이는 순서(fixedOrder). why[i] == null ⇔ i == answerIndex. text는 4.2~4.4의 문자열 그대로(루비 표기는 어댑터가 덧씌운다, 6.2). */
data class GeneratedQuestion(val id: String, val type: GenType, val vocabId: String, val stem: String, val target: String?,
    val choices: List<String>, val answerIndex: Int, val why: List<Distractor?>) {
    fun toQuizQuestion(): QuizQuestion = QuizQuestion(id, Section.VOCAB, type.code, choices, answerIndex, fixedOrder = true)
}
enum class SkipReason { NOT_TARGET, UNREVIEWED, TOO_FEW_DISTRACTORS, UNKNOWN_VOCAB }
sealed interface GenResult { data class Ok(val q: GeneratedQuestion) : GenResult; data class Skip(val reason: SkipReason) : GenResult }

data class GenId(val type: GenType, val slot: Int, val seed: Long, val vocabId: String)

object QuizGen {
    fun eligible(bank: QuizBank, type: GenType): List<String>          // 대상 조건(4.2~4.4)만 본 ID, 정렬. 오답 충분 여부는 보지 않음
    fun generate(bank: QuizBank, type: GenType, vocabId: String, seed: Long, slot: Int): GenResult
    fun buildSession(bank: QuizBank, type: GenType, sessionSeed: Long, size: Int): List<GeneratedQuestion>
    fun idOf(g: GenId): String                                         // "gq:<code>:<slot>:<Long.toUnsignedString(seed,36)>:<vocabId>"
    fun parseId(id: String): GenId?                                    // 형식이 아니면 null. split(":", limit = 5)
    fun regenerate(bank: QuizBank, id: String): GenResult              // parseId → generate. 같은 bank → 같은 결과
}
object Kana { fun hira(s: String): String }                           // 3.2 정의
object KanaVariants { fun of(reading: String): List<Pair<String, DistractorKind>> }   // 4.6
fun posGroup(pos: String): String                                      // 5.4
```

**시드 규칙**
- `generate`: 후보 섞기 `Random(seed)`. 같은 (bank, type, vocabId, seed, slot) → 같은 `GeneratedQuestion`(equals). `slot`은 정답 자리만 정하고 오답 선택에 영향이 없다: 고른 오답 3개를 고른 순서대로 slot을 뺀 자리 0..3에 채운다.
- `buildSession`: (1) `eligible` ID를 정렬해 `Random(sessionSeed)`로 섞는다. (2) 순서대로 `qSeed_k = mix(sessionSeed, k)`(k = 그 후보의 섞인 순번, `mix` = SplitMix64 `z = sessionSeed + (k+1)·0x9E3779B97F4A7C15`, 표준 SplitMix64 finalizer)로 오답 선택을 시도해 성공한 것을 `size`개까지 모은다(실패는 건너뜀). (3) 모인 개수 m에 대해 `slots = (0 until m).map { it % 4 }`를 `Random(sessionSeed xor 0x5DEECE66DL)`로 섞는다. (4) j번째 문항 = `generate(…, qSeed, slots[j])`, ID에 slot·qSeed가 들어간다. 같은 sessionSeed → 같은 목록.
- 정답 위치 보장: 세션 안 한 자리의 문항 수 ≤ ⌈m/4⌉(m=10이면 3 = 30% < 40%). m ≤ 4면 자리가 모두 다르다. m = 5면 2/5 = 40%가 가능한 최소다(작은 세트의 한계, 7.1 I6은 세션 크기 10과 전체 집계로 본다). 셔플만 쓰면 10문항 세션의 약 3분의 2에서 한 자리가 4개 이상이 된다 — 그래서 라운드로빈 + 섞기.
- `Quiz.shuffle`과의 관계: 생성 문항은 `fixedOrder = true`라 `Quiz.shuffle(q, attemptSeed)`가 순서를 바꾸지 않는다. `QuizViewModel`·`QuizResultViewModel`의 기존 호출(`Quiz.shuffle(it.toQuestion(), seed)`)은 고치지 않는다.

### 5.4 품사군 `posGroup(pos)`

`pos`의 첫 코드 `f = pos.substringBefore(',').trim()`. `verb`: `f`가 `v1`·`v2`·`v4`·`v5`·`vk`·`vs-`·`vz`로 시작하거나 `vn`·`vr` / `adj-i`: `adj-i`·`adj-ix` / `adj-na`: `adj-na` / `adv`: `adv`·`adv-to` / `noun`: `n`으로 시작하거나 `pn` / 그 밖: `f` 그대로(`exp`, `int`, `conj`, `prt`, `adj-pn`, `adj-no`, `ctr`, `pref`, `suf` 등 — 작은 품사군은 오답이 모자라 대개 생성 불가가 된다).

## 6. 앱 연결(`:app`)

### 6.1 문항 ID와 재생성(Q-07)

- 시도는 기존대로 `quiz_attempt.question_order_json`에 생성 문항 ID를 담고, 답은 `quiz_answer`(원래 색인 = 보이는 색인, `fixedOrder`)에 담는다. **학습 기록 DB 스키마 변경 없음.** `exam_id = NULL`, `seed` = 세션 시드.
- 문항 내용은 저장하지 않는다. 읽을 때 `QuizContentRepository.contents(ids)`가 `gq:` ID를 `QuizGenRepository`로 넘겨 다시 만든다. `QuizSessionLoader`는 바꾸지 않는다. 결과의 `AttemptMeta`는 첫 항목에서 `PRACTICE`, `Section.VOCAB`, `type` = GenType 코드로 정해진다.
- 콘텐츠 DB가 바뀐 뒤(새 판) 지난 시도를 열면: 어휘가 retired/없음이면 기존 규칙대로 빠진다(`retiredCount`). 남은 문항은 정답 자리(ID의 slot)와 정오(`correct` 열, `chosen_index == slot`)는 같고, **오답 글자는 달라질 수 있다**(배제 표가 바뀌면). 생성 불가가 되면(`Skip`) 빠진 문항으로 센다. shortcut: 지난 시도 해설의 오답 글자 보존이 필요해지면 `quiz_answer`에 보기 글자 열을 더하는 학습 기록 v3를 만든다.

### 6.2 데이터 계층

```kotlin
// data/content/ContentDao.kt에 추가(SELECT만, 급수 하나라 바인드 변수 한도 문제 없음)
@Query("SELECT * FROM vocab WHERE level = :level AND id LIKE 'jm:%' AND id NOT IN (SELECT id FROM retired) ORDER BY id")
suspend fun quizVocab(level: Int): List<VocabEntity>
@Query("SELECT m.* FROM vocab_meaning m JOIN vocab v ON v.id = m.vocab_id WHERE v.level = :level AND m.ord = 0")
suspend fun quizMeanings(level: Int): List<VocabMeaningEntity>
@Query("SELECT * FROM kanji WHERE level >= :level AND literal NOT IN (SELECT id FROM retired)")
suspend fun quizKanji(level: Int): List<KanjiEntity>
@Query("SELECT * FROM jm_form WHERE written IN (SELECT written FROM vocab WHERE level = :level AND written IS NOT NULL) " +
       "OR reading IN (SELECT reading FROM vocab WHERE level = :level AND written IS NOT NULL)")
suspend fun quizForms(level: Int): List<JmFormEntity>
@Query("SELECT c.* FROM vocab_conflict c JOIN vocab v ON v.id = c.vocab_id WHERE v.level = :level")
suspend fun quizConflicts(level: Int): List<VocabConflictEntity>
@Query("SELECT level FROM vocab WHERE id = :id")
suspend fun vocabLevel(id: String): Int?

// data/content/QuizGenRepository.kt(새)
data class GenTypeAvailability(val type: GenType, val targets: Int)        // QuizGen.eligible 개수
interface QuizGenRepository {
    suspend fun availability(level: JlptLevel): List<GenTypeAvailability>  // GenType 선언 순서. 표가 비었으면(sample DB) 모두 0
    suspend fun newSession(level: JlptLevel, type: GenType, seed: Long, size: Int = PRACTICE_SIZE): List<String>   // ID 목록, 비면 빈 목록
    suspend fun contents(ids: List<String>): Map<String, QuizContent>     // gq: ID만. 형식 오류·Skip·없는 어휘는 결과에서 빠짐
}
class RoomQuizGenRepository(private val dao: ContentDao, private val texts: GeneratedTexts = GeneratedTexts) : QuizGenRepository
// 급수별 QuizBank를 Mutex로 한 번만 만들고 캐시(앱 수명 = 콘텐츠 DB 수명). 생성·은행 만들기는 Dispatchers.Default.
```

- `RoomQuizContentRepository(dao, gen: QuizGenRepository)`: `contents(ids)`는 `gq:`로 시작하는 ID를 `gen.contents`로, 나머지를 지금처럼 처리해 합친다. `pool()`은 바꾸지 않는다(생성 문항은 모의고사·고정 연습 풀에 없음). `AppContainer`에 `quizGenRepository`를 더한다.
- 은행 어댑터 규칙: `QuizVocab.meaningKo` = ord 0 행이 `ko_review_status == "reviewed"`이고 `text_ko.isNotBlank()`일 때만 `text_ko`, 아니면 null. `QuizKanji.on`/`kun` = `on_readings`/`kun_readings` JSON 배열. `forms` = `(written, reading)` 쌍. `conflicts` = JSON 배열 → Set.
- `QuizContent` 변경(9.2.6 계약 확장): `provenance: MeaningUi?`(null = 이 문항에 AI가 쓴 한국어가 없음 — 고정 문항·청해는 지금처럼 늘 값), `generated: Boolean = false`. `toQuestion()`은 `fixedOrder = generated`를 넘긴다. `QuizQuestionUi`도 `provenance: MeaningUi?`, `generated: Boolean`을 갖는다. 칩은 `provenance != null`일 때만 그린다.
- `GeneratedQuestion` → `QuizContent`(어댑터, `data/content/QuizGenRepository.kt` 안):
  - `level` = 은행 급수, `section` = VOCAB, `type` = 코드, `passage`/`lines` = null, `targets = [vocabId]`, `needsReview = false`, `generated = true`.
  - `stem`/`choices`: ③의 일본어 쪽은 `Furigana.fromHeadword(written, reading)` 세그먼트를 `{base|ruby}`(ruby 없으면 base)로 이은 문자열. ①②는 생성기 문자열 그대로(①은 루비 금지, `QuizTypeLabels.alwaysNoRuby`).
  - `explanationKo`/`whyWrongKo`: `GeneratedTexts`(아래)로 만든다. 한국어 필드 규칙(architecture 4.4: `{`·`|`·`}`·`[[`·`]]` 금지)을 지킨다 — 일본어는 표시 기호 없이 그대로.
  - `provenance` = 해설이나 선택지·지문에 한국어 뜻을 보이면 정답 어휘 ord 0 뜻의 `MeaningUi`(모두 `reviewed`라 같은 종류의 칩), 보이지 않으면 null.

```kotlin
// data/content/GeneratedTexts.kt — 문구 한 곳(QuizTypeLabels와 같은 방식의 Kotlin 상수). 문구 확정은 ui-design(6.7)
object GeneratedTexts {
    fun explanation(type: GenType, written: String?, reading: String, meaningKo: String?): String
    // ① "「{W}」는 「{R}」로 읽습니다."  ② "「{R}」는 「{W}」로 씁니다."  ③ "「{visible}」({R}): {meaning}"
    // ①②에서 meaningKo != null이면 "\n\n뜻: {meaningKo}"를 붙인다(sampled·unreviewed면 null이라 붙지 않는다)
    fun whyWrong(d: Distractor, source: QuizVocab?): String
    // LONG_VOWEL "장음(길게 읽는 소리)이 다릅니다." VOICING "탁음·반탁음 표시가 다릅니다." SOKUON "촉음(っ)이 다릅니다."
    // YOON "요음(ゃ·ゅ·ょ)이 다릅니다." OTHER_READING "「{source.written ?: source.reading}」의 읽기입니다."
    // KANJI_SWAP "「{from}」 대신 「{to}」를 썼습니다." OTHER_WRITTEN "「{source.reading}」로 읽는 다른 단어입니다."
    // OTHER_MEANING "「{source visible}」의 뜻입니다." OTHER_WORD "뜻: {source.meaningKo}"
}
```

### 6.3 S12 연습 홈 상태(9.2.6 확장)

```kotlin
data class GeneratedTypeUi(val type: String,      // GenType 코드
    val targets: Int,                              // 고른 급수의 대상 단어 수(GenTypeAvailability.targets)
    val enabled: Boolean)                          // targets >= GEN_MIN_TARGETS(= 10, PracticeConfig.kt)
data class PracticeHomeUiState(
    …기존 필드 그대로…,
    val generated: List<GeneratedTypeUi> = emptyList())  // GenType 선언 순서. 읽기 실패·v3 표가 빈 DB면 빈 목록(섹션 숨김)
// levels = 고정 문항이 있는 급수 ∪ generated 중 enabled가 하나라도 있는 급수(N4는 고정 문항이 없어도 칩이 생긴다)
// status: EMPTY = types.isEmpty() && generated.none { it.enabled }
// PracticeHomeViewModel.startGenerated(type: String, onStarted: (Long) -> Unit):
//   startPractice와 같은 잠금·실패 처리. seed = clock.millis(), ids = quizGen.newSession(level, GenType.of(type)!!, seed),
//   ids가 비면 startFailed. quiz.startAttempt(null, seed, ids, clock.millis()) → Quiz(attemptId)
```

- 한 세션 문항 수 = `PRACTICE_SIZE`(10, UI-U08). 생성 가능한 단어가 적으면 그만큼.
- 결과 화면은 S15를 그대로 쓴다(`QuizResult(attemptId)`). 채점은 기존 `Quiz.score`.
- **미검수 뜻이 있는 급수(N4):** 급수를 막지 않는다. ①② 는 뜻을 쓰지 않으므로 N4 전 단어가 대상이고, 해설에 뜻을 붙이지 않는다. ③은 `reviewed` 단어(63)만 대상·오답이 되고, 같은 품사군 오답이 모자란 단어는 건너뛴다. `targets < 10`이면 행을 비활성으로 두고 이유를 보인다(6.5).

### 6.4 `sampled` 판정(Q-03)

D-03 잠정 채택: "N4 이상은 에이전트 표본 검수(묶음당 10%, 오류율 5% 넘으면 묶음 재생성·재검수)", T07 `review-apply --batch-sampled`는 "표본 밖 행 중 unreviewed인 것만 sampled". 즉 `sampled` 행 자체는 아무도 읽지 않았고, 묶음 오류율 5% 이하만 보증된다. 뜻 문제에서 오역 하나는 "정답이 틀리거나 오답이 정답"이 되므로 사용자 지시의 "reviewed(또는 그에 해당)"에 들지 않는다 → 쓰지 않는다. N4 뜻 문제를 늘리려면 N4 뜻을 `reviewed`로 올리는 검수(T07 도구, 사람 또는 에이전트 전수)가 먼저다.

### 6.5 화면 표시와 접근성(ui-design에 더할 것 — QG4 착수 전 ui-designer가 6.10에 반영)

- S12: "유형별 연습" 아래 영역 머리 **"자동 생성 연습"**(titleSmall, `heading()`) + 행 4개(최소 64 dp): 유형 이름 + "단어 {targets}개"(`tnum`). 비활성 행은 이유 "검수된 뜻이 부족해 만들 수 없습니다"(③) / "대상 단어가 부족합니다"(①②). TalkBack: "{유형}, 자동 생성, 단어 {n}개" / 비활성은 "…, 사용할 수 없음, {이유}". 머리 아래 bodySmall 한 줄 "단어 데이터에서 규칙으로 만든 연습 문제입니다. 실제 시험 문제와 형식이 다릅니다."
- 유형 이름(`QuizTypeLabels`, `Section.VOCAB` 표 끝에 추가): `meaning-ja-ko` "뜻 고르기", `meaning-ko-ja` "단어 고르기". 지시문: ja-ko "밑줄 친 {대상}의 뜻으로 알맞은 것을 고르세요.", ko-ja "이 뜻을 가진 일본어를 고르세요." ①②는 기존 지시문 그대로.
- 언어 구간: `QuizTypeLabels.koreanChoices(type) = type == "meaning-ja-ko"`, `koreanStem(type) = type == "meaning-ko-ja"`. 한국어 선택지·지문은 `RubyText`가 아니라 `JpSpans.annotate`(`ko-KR`, 일본어 구간 `ja-JP`)로 그린다 — TalkBack이 한국어 음성으로 읽어야 한다.
- S13·S15 해설: `generated == true`면 상단 문제 판 아래에 라벨 칩 **"자동 생성 연습 문제"**(labelMedium, onSurfaceVariant, 테두리 small, 장식 아이콘 없음). TalkBack은 문항이 바뀔 때 지시문 앞에서 한 번 읽는다(라벨 노드를 지시문 노드와 합치지 않고 순서만 앞). S15 결과 제목 "{유형} 연습" 앞에 "자동 생성 · ".
- 공통 고지(6.10 D11) 그대로. 척도·합격 문구 없음(기존 S15 고지 카드 유지).
- 오프라인: DB 조회와 계산만. 네트워크 없음.

### 6.6 학습 기록 연결(Q-05)

권고·채택: **해설의 "관련 단어 보기"로 S06(단어 상세)에 잇는 것까지만.** 생성 문항의 `targets = [vocabId]`라 기존 `QuizResultViewModel.resolve` → `TargetLink(VOCAB)` → `VocabDetail(id)`가 코드 변경 없이 동작한다. 퀴즈 결과로 `card_state`·`review_log`를 쓰지 않는다. 이유: (1) FSRS 평가는 카드 회상 4단계 평가를 전제한다 — 4지선다 정오를 Again/Good로 바꾸면 기억 모델이 오염된다. (2) 새 카드 도입이 하루 한도(`introducedToday`) 밖에서 생긴다. (3) 학습 기록 스키마·세션 계획기(M4, done) 계약을 건드린다. 보류한 대안: S15 "틀린 단어 카드로 복습" → 지정 카드 목록 복습 세션(`Review(cardIds)`, 사용자가 직접 평가하므로 FSRS 규칙 안). 스키마 변경은 없지만 `ReviewViewModel`·`SessionPlanner` 계약을 바꾸므로 원하면 별도 마일스톤으로 만든다.

## 7. 전수 검사

### 7.1 불변식(생성 문항 하나마다, 오라클은 생성기 함수를 부르지 않고 DB 표를 직접 조회)

| ID | 불변식 |
|----|--------|
| I1 | 선택지 4개, 보이는 글자가 모두 비지 않음 |
| I2 | 보이는 글자 4개가 서로 다름(①은 `hira` 뒤) |
| I3 | 정답이 정확히 하나: ① 모든 오답 d에 `(W, hira(d)) ∉ jm_form`, 정답 = `vocab.reading` / ② 모든 오답 x에 `(x, hira(R)) ∉ jm_form`, 정답 = `vocab.written` / ③ 모든 오답 B에 `B ∉ vocab_conflict(A)`, `B.level == A.level`, `posGroup` 같음 |
| I4 | 화면에 나오는 한국어 뜻(지문·선택지·해설·오답 이유) 전부가 `ko_review_status = 'reviewed'`인 `vocab_meaning.text_ko`와 같은 문자열(①② 해설 뜻 포함). `sampled`·`unreviewed` 텍스트 0건 |
| I5 | 결정성: 같은 (단어, 유형, 시드, 자리)로 두 번 → equals. `regenerate(idOf(…))` == 원래 문항. 저장소 `contents([id])` 두 번 → 같은 `QuizContent` |
| I6 | 정답 위치: `buildSession(size = 10)` 세션마다 한 자리 수 ≤ ⌈m/4⌉. 급수·유형마다 세션 시드 200개의 모든 문항을 모아 각 자리 비율 < 40% |
| I7 | ① 소리 변형 오답이 4.6 형식 규칙을 만족 |
| I8 | `targets == [vocabId]`, `parseId(id)`가 (유형, 자리, 시드, 어휘)를 되돌리고 `answerIndex == slot` |
| I9 | `explanationKo`·`whyWrongKo`에 `{`·`|`·`}`·`[[`·`]]` 없음, `whyWrongKo[answerIndex] == null`, 나머지 비지 않음 |
| I10 | `av-`·retired 어휘가 정답·오답에 없음 |

### 7.2 위치와 방식(Q-08)

- **`:app` Robolectric 테스트** `app/src/test/java/com/jlptstudy/app/data/QuizGenExhaustiveTest.kt`: 기존 `ContentDbTest`처럼 `ContentDatabase.open(context)`로 번들 `content.db`를 연다(JVM에서 SQLite를 여는 장치가 이미 있다 — sqlite-jdbc를 더하지 않는다). 급수 5·4 × 유형 4 × 대상 단어 전부 × 시드 8개(`0, 1, 2, 3, 42, 20261108, Long.MAX_VALUE, -1`, 자리 = 시드 순번 % 4)로 `RoomQuizGenRepository`의 은행과 `QuizGen.generate`를 돌려 I1~I5·I7~I10을 확인하고, I6은 `buildSession` 세션 시드 200개(1..200)로 확인한다. 오라클은 `ContentDao`가 아닌 `db.openHelper.readableDatabase.query(...)`의 원시 SQL로 `jm_form`·`vocab_conflict`·`vocab_meaning`을 읽는다(생성 경로와 다른 읽기).
- 하한(테스트가 단언): N5에서 `generated / eligible`이 ① ≥ 90%, ② ≥ 80%, ③ 두 방향 각각 ≥ 60%. 못 미치면 규칙을 느슨하게 해서 통과시키지 않는다 — 실패 그대로 보고해 `needs-decision`으로 둔다(9절 2). N4는 하한 없이 보고만.
- 보고: 테스트가 `app/build/reports/quizgen/coverage.tsv`(머리 `level\ttype\tvocab\teligible\tgenerated\tskip_not_target\tskip_unreviewed\tskip_too_few`)와 같은 내용 요약을 표준 출력에 쓴다. 생성 불가 단어 ID 목록은 `app/build/reports/quizgen/skipped.tsv`(`level\ttype\tvocab_id\treason`).
- 순수 모듈 테스트(`:core:quiz`)는 합성 은행으로 규칙별 경우를 본다(8.3). 파이프라인 테스트는 파생 표 규칙을 합성 JMdict로 본다(8.2).
- 파이프라인 `check.py`의 `check_quizgen(con)`(release·sample 모두, 표가 0행이면 통과):
  - `jm_form`: `written`·`reading` 비지 않음, `reading`에 가타카나(U+30A1–30F6) 없음, `written IS NOT NULL`인 모든 `jm:` vocab의 `(written, hira(reading))` 행이 있음 → 어기면 `FAIL: quizgen jm_form …`.
  - `vocab_conflict`: `jm:` vocab마다 정확히 한 행, JSON 배열·정렬·중복 없음·자기 자신 없음, 모든 ID가 같은 `level`의 vocab, 대칭 → 어기면 `FAIL: quizgen vocab_conflict …`. `vocab_conflict`의 `vocab_id`가 vocab에 없으면 FAIL.

### 7.3 표본 파일(원어민 검수용)

`app/src/test/java/com/jlptstudy/app/data/QuizGenSampleTest.kt`: 환경 변수 `QUIZGEN_SAMPLE_OUT`(절대 경로)이 있을 때만 돈다(없으면 `Assume`로 건너뜀 — verify에서는 돌지 않음). 시드 `20261108`로 급수별(N5, N4) 유형별 무작위 30문항(N5 120 + N4 120)을 `buildSession`으로 만들어 마크다운으로 쓴다. 문항마다: ID, 지시문, 지문(보이는 글자 + 읽기), 보기 4개(정답 표시), 오답 이유 종류와 출처 단어 ID, 해설. 끝에 검수 칸 `- [ ] ok / fix: …`. 실행: `QUIZGEN_SAMPLE_OUT=$PWD/_workspace/quizgen_sample.md ./gradlew :app:testDebugUnitTest --tests '*QuizGenSampleTest'`. Gradle 테스트 JVM은 환경 변수를 물려받는다.

## 8. 구현 분업

### 8.1 순서와 파일 경계

```
QG1 (content-engineer, opus) ─┐
                              ├─▶ QG3 (feature-engineer, content: true) ─▶ QG4 (feature-engineer, ui: true)
QG2 (feature-engineer)       ─┘
```

QG1과 QG2는 병렬(2명). 겹치는 파일 없음.

| 마일스톤 | 쓰는 파일(이것만) |
|----------|-------------------|
| QG1 | 3.6 표 전부: `content/pipeline/**`, `content/snapshot/S-01-2026-11-01-related.jsonl.gz`, `app/src/main/assets/content/*`, `app/src/main/res/values/attribution_fallback.xml`(재생성으로 바뀔 때만), `app/.../data/content/{ContentEntities,ContentDatabase}.kt`, `app/schemas/…ContentDatabase/3.json`, `app/src/test/.../data/{ContentDbTest,ContentSchemaV3Test}.kt`, `content/ids/**`(빌드가 바꿀 때만) |
| QG2 | `core/quiz/**`만 |
| QG3 | `app/.../data/content/{ContentDao,QuizContentRepository,QuizGenRepository,GeneratedTexts}.kt`, `app/.../AppContainer.kt`, `app/.../ui/practice/{QuizViewModel,QuizResultViewModel,PracticeModels}.kt`(provenance nullable·generated 전달만), `app/src/test/.../data/{QuizGenExhaustiveTest,QuizGenSampleTest,QuizGenRepositoryTest}.kt`, 기존 테스트의 `QuizContent` 생성자 호출 |
| QG4 | `app/.../ui/practice/{PracticeHomeViewModel,PracticeHomeScreen,PracticeModels,QuizScreen,QuizResultScreen,QuizTypeLabels,PracticeConfig}.kt`, `app/src/main/res/values/strings.xml`, 해당 화면 테스트·스크린샷 |

### 8.2 QG1 먼저 쓸 실패 테스트(`content/pipeline/test_quizgen.py` 등)

1. `parse_jmdict(…, xref=True)`가 합성 XML의 `<xref>母・はは・1</xref>`를 `entry["xref"] == ["母・はは・1"]`로 읽고, `xref=False`면 키가 없다.
2. `write_related`: 합성 JMdict(橋/はし, 箸/はし, 端/はし, 上手/じょうず, 上手/うわて, 무관 항목 1개)와 스냅샷 항목(橋, 上手 じょうず)으로 → 箸·端·上手(うわて)는 들어가고 무관 항목은 빠진다. 두 번 쓴 파일 sha256 같음.
3. `hira("カン") == "かん"`, `hira("コーヒー") == "こーひー"`.
4. `jm_form` 행: 위 픽스처에서 `(橋,はし)`,`(箸,はし)`,`(端,はし)`,`(上手,うわて)` 존재, Wdb·Rdb 밖 쌍은 없음.
5. `en_tokens`: "to put on (from shoulders down)" → `{"put"}`, "eyes" → `{"eye"}`, "the" 제외. 父("father")·パパ("papa; father") 충돌.
6. `ko_phrases`: "(사고·일 등을) 당하다, 겪다" → `{"당하다","겪다"}`. 本("책")·本棚("책장") 충돌, 暑い·熱い(같은 구절) 충돌, 水("물")·犬("개") 무충돌.
7. xref: A의 xref `B表記・Bよみ`가 같은 급수 B를 가리키면 A·B 양쪽 목록에 서로 있음. 다른 급수면 없음.
8. `vocab_conflict` 대칭·정렬·자기 제외, 모든 `jm:` 어휘에 한 행.
9. `check_quizgen`: 대칭이 깨진 픽스처 → `FAIL: quizgen vocab_conflict …`, 가타카나 reading → `FAIL: quizgen jm_form …`, 0행 표 → 통과.
10. `refresh-related`: raw sha256이 매니페스트와 다르면 `FAIL: refresh-related …`.
11. 스키마 단언 갱신 테스트: 스키마 파일 `["1.json","2.json","3.json"]`, 최신 version 3.

### 8.3 QG2 먼저 쓸 실패 테스트(`core/quiz/src/test/.../gen/`)

1. `Quiz.shuffle`: `fixedOrder = true`면 `originalIndexOf == [0,1,2,3]`, `answerIndex` 그대로. false면 기존 테스트 그대로 통과.
2. `KanaVariants.of("がくせい")`에 `かくせい`(VOICING)·`がっせい`(SOKUON)·`がくせ`(LONG_VOWEL 지우기)가 있고 `っがくせい` 같은 형식 위반이 없다. `of("きょう")`에 `きよう`·`きゅう`(YOON)·`きょ`(LONG_VOWEL).
3. ① 합성 은행: 今日(きょう), forms에 `(今日,こんにち)` → 어떤 시드에서도 오답에 こんにち 없음. 日本(にほん), forms `(日本,にっぽん)` → にっぽん 없음.
4. ② 합성 은행: 橋(はし), forms `(箸,はし)`,`(端,はし)`, 은행 한자에 箸·端·喬 → 오답에 箸·端 없음. 会う(あう) + forms `(合う,あう)`,`(逢う,あう)` → 合う·逢う 없음.
5. ③ 합성 은행: 母·お母さん이 서로 conflicts → 같이 안 나옴. meaningKo null(미검수) 어휘는 정답이면 `Skip(UNREVIEWED)`, 오답 후보에서 빠짐. 품사군이 다른 어휘는 오답이 안 됨. 같은 visible(方 かた/ほう)이 같이 안 나옴.
6. 오답 후보가 3개 미만이면 `Skip(TOO_FEW_DISTRACTORS)`(규칙을 느슨하게 하지 않음).
7. 결정성: 같은 인자 두 번 equals, `regenerate(idOf(...))` 같음, `parseId("gq:kanji-reading:2:abc:jm:1358280")`이 vocabId `jm:1358280`.
8. `buildSession(size = 10)` 시드 1..500: 각 세션 자리 최대 ≤ 3, 같은 시드 두 번 같은 ID 목록, 모든 문항 `answerIndex == slot`.
9. `posGroup`: `"v5r,vi"→verb`, `"adj-i"→adj-i`, `"n,vs"→noun`, `"adj-na,n"→adj-na`, `"exp"→exp`.

### 8.4 QG3·QG4 먼저 쓸 실패 테스트

- QG3: `RoomQuizContentRepository.contents`가 `q-…`와 `gq:…`를 섞은 목록을 둘 다 돌려준다 / `QuizContent.toQuestion().fixedOrder == generated` / 생성 문항을 `QuizSessionLoader.load`로 읽으면 `AttemptMeta(PRACTICE, N5, VOCAB, "kanji-reading")` / `QuizGenExhaustiveTest`(7.2) / N4 ①② 해설에 `sampled` 뜻이 없음 / 기존 고정 문항의 `provenance`가 여전히 non-null.
- QG4: S12에 "자동 생성 연습" 머리와 행 4개, `targets < 10`인 가짜 저장소에서 그 행 비활성 + 이유 / 행 누르면 `startAttempt(null, seed, gq ID 10개, …)` 후 `Quiz(attemptId)` / S13 생성 문항에 "자동 생성 연습 문제" 노드 / `meaning-ja-ko` 선택지 노드의 로캘이 `ko-KR` / 더블탭 시도 1개.

### 8.5 통과 조건

모든 QG 마일스톤: `.claude/skills/android-build-verify/scripts/verify.sh` 종료 코드 0(`check.sh` + test + lint + assemble). 파이프라인 테스트 `python3 -I -m unittest discover -s content/pipeline` 실패 0. 세부는 `milestones.json` QG1~QG4.

## 9. 남은 위험(구현자가 먼저 알 것)

1. **raw JMdict 판**: `refresh-related`는 2026-11-01 판 원본(sha256 `cda250dc…`)이 있어야 한다. 메인 체크아웃 `content/raw/S-01/JMdict_e.gz`를 worktree에 복사(또는 심볼릭 링크)해 쓴다. 해시가 다르면 멈추고 보고한다(새 판 채택은 U-10 사용자 결정 — 섞어 쓰지 않는다).
2. **과배제로 생성 불가 증가**: en 토큰 한 개 겹침은 공격적이다(기본 동사의 sense가 많음). N4 ③은 `reviewed` 63어뿐이라 같은 품사군 오답이 모자라 대부분 건너뛸 수 있다(세션이 10문항보다 짧아짐). 생성 수가 7.2 하한에 못 미치면 규칙을 느슨하게 하지 말고 `skipped.tsv`·`quizgen_conflicts.tsv`를 보고해 사용자 판단을 받는다(`needs-decision`).
3. **규칙 밖 유의어(4.7 #5·#9·#17)**: 표본 검수(7.3)에서 잡히면 고칠 곳은 파이프라인이다. shortcut: 수동 덮어쓰기 표 `content/authored/quizgen-conflict-overrides.tsv`(`a`, `b`, 사유 — 충돌 추가만)는 첫 오류 보고 때 만든다(지금은 만들지 않음).
4. **콘텐츠 판 변경 뒤 지난 시도**: 오답 글자가 바뀔 수 있다(6.1). 정답 자리·정오는 그대로.
5. **analyst 매니페스트 갱신(3.5)** 전에는 출시하지 않는다.
6. **ui-design 갱신**: QG4 전에 ui-designer가 6.5를 ui-design.md 6.10에 반영해야 디자인 검증 기준이 생긴다.

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-11-08 | 처음 작성(Q-01~Q-10, QG1~QG4) |
