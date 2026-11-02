# M9 jlpt-content-author 요약: N5 한국어 뜻 생성(D-02 방식 A)

## 입력 자료(이것만 썼다)
- `content/work/en-gloss/N5.jsonl` 667줄, sha256 `1d1bb3f65fe430af7b8b6c5d8e6a11320ca97e57dc48c40503d712305536255d`. 이 파일은 지금 커밋된 스냅샷으로 export-gloss-input을 다시 돌린 결과와 같다(임시 폴더에서 다시 만들어 해시 비교).
- 이 파일의 원본은 JMdict 스냅샷 `content/snapshot/S-01-2026-11-01.jsonl.gz`(S-01 JMdict, 판 2026-11-01, `JMdict_e.gz` sha256 `cda250dc…2829`)다.
- **진술:** 한국어 뜻은 위 en-gloss 파일의 영어 sense(`senses`)와 표기·읽기·품사만 보고 만들었다. 다른 사전(일한사전, 국어사전, 웹 사전)이나 시판 교재의 한국어 뜻풀이를 보거나 옮기지 않았다. 외부 서비스·네트워크도 쓰지 않았다.

## 생성 방법
- 만든 쪽: 이 작성 에이전트(LLM, Claude Opus 5.5), 2026-11-02. 모든 줄에 `method: "llm:claude-opus-5-5@2026-11-02"`, `koSource: "jmdict-translated"`가 있다(파생물 표기 D-02). 사람이 쓰지 않았으므로 `human`으로 적지 않았다.
- 절차: en-gloss를 4묶음(약 170줄씩)으로 나눠 읽고, 줄마다 sense를 한국어로 번역해 `meanings` 배열에 넣었다. 그다음 원문 sense 전체와 다시 대조했다. 영어 sense에 없는 뜻이 들어간 줄(예: 후보에 없던 `(병에) 걸리다`, `(맛이) 담백하다`, `등`, 테니스 `코트`, `(표를) 끊다` 오역)은 고치거나 뺐다.
- 조립: 번역 결과(vocabId → 뜻 목록)에 export의 `enHash`를 그대로 붙이고 `reviewStatus: "unreviewed"`, `reviewedAt: null`, `reviewerKind: null`로 `content/ko-gloss/N5.jsonl`을 만들었다. 줄 순서는 en-gloss와 같고, 키는 정렬했다(파이프라인 `jdump` 형식).
- 뜻을 고른 규칙
  - 배열 원소 하나가 뜻 하나다. 카드 뒷면에는 3개까지 보이고, 단어 회상 카드는 첫 뜻만 쓴다. 그래서 N5 학습에서 쓰는 뜻을 맨 앞에 두었다(예: 早い는 "(시간이) 이르다"를 "(속도가) 빠르다"보다 앞에, 背는 "키"를 앞에, 方(かた)는 "분"을 앞에).
  - 드물거나 전문적인 sense는 뺐다: 말 털색, 화투 패, 옛 시각 체계, 바둑·마작 용어, 생물 학명, 속어·성적 의미 등.
  - 한 줄에 뜻은 최대 7개다. 분포는 1개 256줄, 2개 150줄, 3개 94줄, 4개 78줄, 5개 45줄, 6개 27줄, 7개 17줄이다.
  - 동사·형용사는 한국어 기본형(만나다, 파랗다)으로 썼다. 쓰임이 갈리는 뜻에는 괄호로 대상을 달았다(예: "(전화를) 걸다").
  - 뜻 안의 일본어는 표시 기호 없이 썼다. 5줄에만 있다: いる・来る・する・欲しい・見る의 "(~ている 꼴로)" 같은 문형 설명. 한국어 낱말을 한자로 쓴 곳은 없다(CJK 한자 검사 0건). 루비 `{…|…}`와 강조 `[[…]]`도 없다.

## 완료 조건 대조
- vocabId 집합: ko-gloss 667 = en-gloss 667. 빠진 항목과 남는 항목은 모두 0이다.
- 모든 줄에 koSource `jmdict-translated`, method `llm:claude-opus-5-5@2026-11-02`, export와 같은 enHash, reviewStatus `unreviewed`가 있다. 줄마다 확인하는 일은 `--check`가 한다(stale 0).
- `python3 -I content/pipeline/build.py ko-gloss --check` 출력은 `ko-gloss --check: OK (667 rows, 0 missing, 0 stale)`, 종료 코드는 0이다.

## 코드 변경(파이프라인)
- 완료 조건에 있는 `ko-gloss --check` 명령이 파이프라인에 없어서(전에는 `--check`를 알 수 없는 단계로 보고 실패했다) 최소한으로 더했다.
  - `content/pipeline/pl_db.py`: `check_ko_gloss()`. kind와 상관없이 돈다. 형식·허용 값 검사는 기존 `load_ko_gloss`(meaning_problems, vocabId 중복)를 그대로 쓴다. 여기에 enHash 형식(64자 hex)과 export에 없는 vocabId를 FAIL로 더했다. 뜻이 빠진 항목과 stale enHash는 build 단계의 처리(architecture 6.2, 경고)에 맞춰 WARN과 개수로만 알린다. ko-gloss 파일이나 en-gloss export가 없으면 FAIL이다.
  - `content/pipeline/build.py`: `ko-gloss --check` 인자 처리와 사용법 한 줄.
  - `content/pipeline/test_release.py`: `test_ko_gloss_check`(파일 없음은 실패, 1개 빠짐과 1개 stale은 경고 뒤 통과, 허용 값 밖 koSource·enHash null·export에 없는 ID는 실패). `test_committed_ko_gloss_format`(커밋한 N5 파일 667줄이 형식 검사를 통과).
- architecture.md는 고치지 않았다. 6.2 단계 표에 `--check` 옵션 한 줄을 넣을지는 content-engineer와 설계 담당이 정한다.

## 검사
- `python3 -I -m unittest discover -s content/pipeline`: 79개 OK.
- verify.sh 결과는 반환 데이터(`verifyExit`, `verifyLog`)에 적는다.

## 남은 일과 위험
- 모든 줄은 `unreviewed`다. 검수(D-03 범위)는 M11에서 한다. 이 에이전트의 자체 대조는 검수로 기록하지 않았다.
- config.json이 `kind: sample`이므로 지금 번들 DB에는 이 뜻이 들어가지 않는다. M10(release 빌드)에서 처음 쓰인다.
- 검수 때 특히 볼 것: 여러 뜻 가운데 무엇을 앞에 둘지 판단이 갈리는 줄(早い, 背, 方, 先, 上げる, かける, 取る 등), 그리고 JMdict가 한 항목으로 묶은 동음 표기(明い/明るい, 終る/終わる, 曲る/曲がる).

## 리뷰 반영 (2026-11-02)

- 上げる(jm:1352320): '(남에게) 주다'를 1번째로 올리고 '드리다'(겸양어, さしあげる에 가까움)를 뺐다.
- かける(jm:1207610): '(전화를) 걸다', '(안경을) 쓰다'를 2·3번째로 올렸다. 뜻 개수는 그대로다.
- つける(jm:1495770): 영어 sense 'to turn on (a light)'를 '(전등·텔레비전 등을) 켜다'로 옮겨 2번째에 넣고, '(가구 등을) 갖추다'를 뺐다. 消す의 '끄다'와 짝이 맞는다.
- よく(jm:1605870): 감사 뜻을 '(감사할 때) ~해 줘서 고맙다(예: 잘 와 주었다)'로 바꿔 비꼬는 뜻의 '잘도'와 구분했다.
- 네 줄 모두 vocabId·enHash·koSource·method·reviewStatus(unreviewed)는 그대로다. 입력은 이번에도 en-gloss/N5.jsonl의 영어 sense만 썼다.
