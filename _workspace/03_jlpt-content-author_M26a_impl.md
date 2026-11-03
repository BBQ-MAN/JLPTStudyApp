# M26a jlpt-content-author 요약: N4 영어 뜻 추출과 한국어 뜻 생성(D-02 방식 A)

## 착수 시 D-03 결정(인용)

`docs/content/decisions.md` 잠정 결정 표(2026-11-02, 사용자 최종 검토 대기)의 D-03 행:

> N5는 에이전트(LLM) 전수 검수, N4 이상은 에이전트 표본 검수(묶음당 10%, 오류율 5% 넘으면 묶음 재생성·재검수). 사람 검수는 아직 없음(`reviewerKind=agent`, 화면에 "AI 검수"로 표시)

이 결정대로 N4는 검수 전 상태로 만든다. 모든 행은 `reviewStatus: "unreviewed"`, `reviewedAt: null`, `reviewerKind: null`이다. 표본 검수(10%)와 릴리스 빌드는 M26b가 한다. 이 에이전트가 생성 중에 한 자체 대조는 검수로 기록하지 않았다.

## 입력 자료(이것만 썼다)

- `content/work/en-gloss/N4.jsonl` 630줄. 처음 생성 때 sha256 `ce2bef1bf4774bca9b20646d1292e7934315c3010327fd475774ef021a85274d`, 리뷰 수정(아래 '리뷰 지적 반영') 뒤 다시 내보낸 판 sha256 `2762ec407b5279e3a940e03f6f6b476614469af6207ae55ea6c641f4dd7a36af`.
- 이 파일의 원본은 커밋된 스냅샷이다.
  - JMdict: `content/snapshot/S-01-2026-11-01.jsonl.gz`(S-01, 판 2026-11-01, `JMdict_e.gz` sha256 `cda250dc…2829`)
  - 급수 목록: `content/snapshot/levels-S-06-b062d4e_2025-08-26.tsv`(S-06 yomitan-jlpt-vocab 커밋 b062d4e)
- **진술:** 한국어 뜻은 en-gloss/N4.jsonl의 영어 sense(`senses`)와 표기·읽기·품사만 보고 만들었다. 다른 사전(일한사전, 국어사전, 웹 사전)이나 시판 교재의 한국어 뜻풀이를 보거나 옮기지 않았다. 외부 서비스와 네트워크도 쓰지 않았다.

## export-gloss-input (content/raw 없이)

- `content/pipeline/config.json`을 `{"kind":"release","levelSource":"S-06","levels":[5,4],"milestone":"M26a"}`로 바꿨다(levels [5,4], 보고서 파일 이름용 milestone).
- 확인 방법: 작업 트리를 `content/raw`를 뺀 임시 폴더로 복사하고 `python3 -I content/pipeline/build.py export-gloss-input`을 돌렸다. 종료 코드 0, `content/work/en-gloss/N4.jsonl` 630줄과 N5 667줄이 생겼다. fetch는 없었다. 작업 트리에서 다시 돌린 결과와 해시가 같다.
- 출력: `export-gloss-input: 727 kanji derived, 0 kanji missing from KANJIDIC2`
- N5 export는 지금 ko-gloss/N5.jsonl과 enHash가 모두 같다(stale 0). M9 보고서의 en-gloss/N5 파일 sha256(`1d1bb3…`)과 지금 파일 해시(`f9c49f…`)는 다르다. 그래도 vocabId와 enHash는 모두 같으므로 M9 뒤에 export 파일의 다른 필드가 바뀐 것으로 본다. 원인은 따로 확인하지 않았다.
- N4와 N5의 vocabId는 겹치지 않는다(0건).

## 생성 방법

- 만든 쪽: 이 작성 에이전트(LLM, Claude Opus 5.5), 2026-11-03. 모든 줄에 `method: "llm:claude-opus-5-5@2026-11-03"`과 `koSource: "jmdict-translated"`가 있다(D-02 파생물 표기). 사람이 쓰지 않았으므로 `human`으로 적지 않았다.
- 절차:
  1. en-gloss를 6묶음(약 110줄씩)으로 읽었다.
  2. 줄마다 sense를 한국어로 옮겨 vocabId → 뜻 목록 작업 파일(저장소 밖 임시 파일)에 적었다.
  3. export의 `enHash`를 그대로 붙여 `content/ko-gloss/N4.jsonl`로 조립했다. 줄 순서는 en-gloss와 같고 키는 정렬했다(`jdump` 형식).
  4. 조립한 뒤 다시 읽어 아래 줄을 고쳤다.
     - 뜻이 8개인 7줄을 7개로 줄였다.
     - 内: "(~ 가운데)"를 "(여럿) 가운데, 중"으로 고쳤다.
     - もらう: 어색한 번역 한 개를 뺐다.
     - スーツ: 뜻이 모호한 카드 용어를 뺐다.
- 뜻을 고른 규칙(M9와 같다)
  - 배열 원소 하나가 뜻 하나다. 카드 뒷면에는 3개까지 보이고, 단어 회상 카드는 첫 뜻만 쓴다. 그래서 학습에서 쓰는 뜻을 맨 앞에 두었다. 예를 들어 合う는 "(몸·크기에) 맞다"를, 上る(あがる)는 "오르다"를, 気は "기, 정신, 마음"을 맨 앞에 두었다.
  - 드물거나 전문적인 sense는 뺐다. 바둑·화투·마작 용어, 선(禅) 용어, 옛 연호(文化), 에도 시대 금리, 속어·성적 의미(息子 2번 등), 생물·의학 전문 뜻, 인터넷 속어(草 "LOL" 등)가 그렇다.
  - 한 줄에 뜻은 7개까지다. 분포는 1개 277줄, 2개 148줄, 3개 85줄, 4개 55줄, 5개 27줄, 6개 24줄, 7개 14줄이다(리뷰 수정 반영 뒤).
  - 동사·형용사는 한국어 기본형으로 썼다. 쓰임이 갈리는 뜻에는 괄호로 대상을 달았다(예: "(시험을) 보다"). 경어 동사는 "(言う의 겸양어)"처럼 높임 종류를 적었다.
  - 뜻 안의 일본어는 표시 기호 없이 썼다. N4에는 처음 9줄이 있었고(ko_spans 보고서 `_workspace/03_content-engineer_M26a_ko_spans_ko-gloss.tsv`, 확인용), 리뷰 수정으로 事의 "(~ことだ) …" 1줄이 늘었다. 이 보고서는 빌드 때 만들어지므로 M26b 빌드에서 다시 나온다. 루비 `{…|…}`와 강조 `[[…]]`는 없다.

## 완료 조건 대조

- vocabId 집합: ko-gloss/N4 630 = en-gloss/N4 630. 빠진 항목과 남는 항목은 모두 0이다.
- 모든 줄에 koSource `jmdict-translated`, method `llm:claude-opus-5-5@2026-11-03`, export와 같은 enHash, reviewStatus `unreviewed`가 있다.
- `python3 -I content/pipeline/build.py ko-gloss --check` → `ko-gloss --check: OK (1297 rows, 0 missing, 0 stale)`, 종료 코드 0. N5 667줄과 N4 630줄을 함께 검사한 결과다.
- `app/src/main/assets/content/` 에셋은 다시 만들지 않았다(build 단계를 돌리지 않음). `check.py`는 기존 N5 에셋으로 통과한다.

## 바꾼 파일

- `content/pipeline/config.json`: levels [5,4], milestone M26a
- `content/ko-gloss/N4.jsonl`: 새 파일, 630줄, sha256 `a65a5e15b230ccd5ce90846a283babf48260a73db6b1e9cf2b0864c9bd63b80b`(리뷰 수정 반영 뒤)
- `content/authored/level-overrides.tsv`: N4 2행 추가(리뷰 지적 반영)
- `content/authored/written-overrides.tsv`: N4 9행 추가(리뷰 지적 반영)
- `content/pipeline/test_release.py`: `test_committed_ko_gloss_n4`를 더했다. 630줄인지, N5와 ID가 겹치지 않는지, 모든 줄이 jmdict-translated·unreviewed·reviewedAt/reviewerKind null·`llm:` method·뜻 1~7개인지 검사한다.
- 생성물(커밋 여부는 메인 세션이 정한다): `_workspace/03_content-engineer_M26a_kanji_missing.tsv`(0건), `_workspace/03_content-engineer_M26a_ko_spans_ko-gloss.tsv`

## 검사

- `python3 -I -m unittest discover -s content/pipeline`: 90개 OK
- verify.sh: PASS, 종료 코드 0(`_workspace/build/verify_20261103_122104.log`)

## 리뷰 지적 반영(2026-11-03, 2차)

입력은 처음과 같다(en-gloss export와 JMdict 스냅샷 판 2026-11-01, 급수 목록 S-06 b062d4e). 급수 매핑 판단에는 S-06 원본 `n4.csv`의 `waller_definition` 열을 대조용으로만 봤다(뜻풀이 문장은 옮기지 않음).

1. **以下(jm:1155060)**: 뜻 2번 "(기준) 이하, 미만"을 "(기준·수준) 이하, 그 아래"로 고쳤다. 以下는 경계값을 포함하고, 경계값을 빼는 '미만'은 未満에 해당한다. ko-gloss 전체에 '미만'은 이제 0건이다. M26b 표본 검수에 반드시 넣는다.
2. **一杯(jm:1165670)**: 뜻 4번 "(능력·한도) 껏, 최대한"을 "한껏, 최대한, (~가 허락하는) 데까지"로 고쳤다. 홀로 못 쓰는 접미사 조각을 뺐다.
3. **급수 매핑 3건(결정과 사유)**. 결정은 이 작성 에이전트가 기존 N5 'S-06 seq 오류' 선례(waller 뜻 기준 다시 매핑)를 따라 내렸다. content-engineer가 M26b 빌드 전에 확인한다.
   - N4 행 228 こと: waller 뜻 "thing,matter"인데 seq 2524270은 문장 끝 조사다. → `level-overrides.tsv`로 jm:1313580(事, 명사)에 다시 매핑했다. 事는 원래 N3 행 583에 있었지만 가장 쉬운 급수 규칙에 따라 N4로 옮겨진다.
   - N4 행 584 もし: waller 뜻 "if"인데 seq 2607730은 감동사 "저기요"다. → jm:1012500(若し, 부사 '만약')에 다시 매핑했다. 若し는 원래 N1 행 3009에 있었지만 N4로 옮겨진다.
   - N4 행 270 しかる: **원 목록대로 둔다.** 목록이 kanji 칸에 然る를 직접 적었고 waller 뜻 "a particular"도 然る(連体詞)를 가리킨다. 叱る(jm:1319580, '꾸짖다')는 같은 목록 N3 행에 따로 있어서, 다시 매핑하면 목록이 N3에 둔 낱말을 N4로 끌어온다. 한국어 뜻 "그러한, 그와 같은"은 しかる 읽기에 남는 JMdict sense("that kind of, such a")와 맞다. 학습 가치가 낮은 항목이므로 M26b 표본 검수에 넣고, 거기서 빼기로 하면 content-engineer가 정한다.
   - ko-gloss에서 jm:2524270·jm:2607730 줄을 지우고 같은 자리에 jm:1313580(뜻 5개)·jm:1012500("만약, 만일") 줄을 새로 만들었다. 두 줄 모두 `llm:claude-opus-5-5@2026-11-03`, `unreviewed`, export의 enHash다. 사용자 학습 기록은 아직 N4를 가리키지 않으므로(N4 에셋 미빌드) ID를 바꿔도 끊기는 기록이 없다.
4. **비표준 대표 표기**: N4 export 전체(630줄)에서 `written`이 JMdict 첫 keb와 다른 줄을 전수 검사했다(16줄). 그중 옛 송り가나·비정규형 9건을 `written-overrides.tsv`에 넣어 첫 keb로 고쳤다: 上る→上がる, 下る→下がる(지적 목록에 없던 같은 유형, 下る는 보통 くだる), 集る→集まる, 起す→起こす, 落る→落ちる, 落す→落とす, 決る→決まる, 楽む→楽しむ, 答→答え. enHash는 keb 전체로 계산하므로 9줄 모두 바뀌지 않았다(ko-gloss stale 0). 나머지 7건(押し入れ, 堅い, 御主人, ご存じ, さ来月, さ来週, 舟)은 표준으로 쓰이는 표기라 그대로 둔다. さ来月·さ来週를 再来月·再来週로 바꿀지는 M26b 검수 때 content-engineer가 정한다.
5. 다시 돌린 검사: `export-gloss-input` 종료 0(N4 630줄, vocabId 2개 교체 외 차이는 위 9건의 written뿐), `ko-gloss --check` → `OK (1297 rows, 0 missing, 0 stale)`, 단위 테스트 90개 OK.

## 검수(M26b) 때 볼 것

- **급수 매핑이 의심되던 줄 3개**(처음 판단, 기록용). 처리는 위 '리뷰 지적 반영' 3번에 적었다.
  - `jm:2524270` こと(prt, 문장 끝 조사): 명사 こと일 수 있다.
  - `jm:1394730` しかる(adj-pn "그러한"): 동사 叱る(꾸짖다)일 가능성이 크다.
  - `jm:2607730` もし(int "저기요"): 부사 もし(만약)일 가능성이 크다.
- 뜻 순서 판단이 갈리는 줄: 気, 付く, 立てる, 止める, 通る, 開く, 受ける, ほど.
- 비표준 대표 표기 9건은 written-overrides로 고쳤다(위 4번). M26b 빌드 뒤 에셋의 written이 표준형인지 content-engineer가 확인한다.
- 직역이라 어색할 수 있는 줄: 君(くん) "(손아랫사람 이름 뒤에서) ~씨". 一杯와 以下는 고쳤지만 검수 표본에 넣는다.
