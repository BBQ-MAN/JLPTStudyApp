# M21 jlpt-content-author 요약: N5 문항·모의고사 템플릿

## 한 일

- `content/authored/questions/N5/` 62개를 직접 썼다(jlpt-content-authoring 1·2·5절, architecture.md 9.2.2).
  - vocab 29: kanji-reading 8(0001~0008), orthography 7(0009~0015), context 9(0016~0024), paraphrase 5(0025~0029)
  - grammar 21: grammar-form 11(0001~0011), sentence-order 6(0012~0017, ★ 자리 4지선다, ★ 위치는 1·2·3·4번째 칸이 섞여 있다), text-grammar 4(0018~0021)
  - reading 12: short 6, mid 3, info-search 3. 지문 하나에 문항 하나(같은 `passage`를 두 파일에 넣으면 T07 유사 검사가 WARN을 내므로)
  - N5에 없는 유형(word-formation, usage, long, integrated, thematic)은 쓰지 않았다(jlpt.jp 표에서 N5 칸이 ―).
  - 모든 문항: `status: draft`, `needsReview: false`, `sources: []`, `method: llm:claude-opus-5-5@2026-11-03`, `reviewStatus: unreviewed`. 오답 3개마다 `whyWrongKo`가 있고, `targets`는 모두 1개 이상이다(N5 어휘 `jm:*`, 문법 `gr-n5-*`, 한자 글자).
  - 한자 읽기·표기·유의 표현은 `stem`에 `[[…]]` 1개와 `target`이 있다. 한자 읽기의 `[[…]]` 안에는 루비가 없다. 빈칸은 `＿＿`·`＿★＿`만 썼다.
  - 정답 위치: vocab 0/1/2/3 = 7/8/7/7, grammar 5/6/5/5, reading 3/3/3/3(어느 위치도 40%를 넘지 않는다).
- `content/authored/exams/N5.json`: sourceUrl `https://www.jlpt.jp/e/guideline/testsections.html`, checkedAt `2026-11-03`. 영역과 시간은 언어지식(문자·어휘) 20분, 언어지식(문법)·독해 40분, 청해 30분이다. 문제 유형은 같은 페이지 "Composition of test items" 표에서 N5 칸이 ○인 것만 넣었다. 청해 영역(task, point, utterance, quick-response)도 넣었다. 유형별 목적은 같은 사이트의 `pdf/n5_e_revised.pdf`("N5 Purposes of test items")에서 다시 확인했다.
- 파이프라인: `pl_question.exam_problems`가 이제 `parts[].count`가 없어도 받아들인다. 값이 있으면 지금처럼 1 이상의 정수여야 한다. 테스트로 `ExamFormatTest.test_count_may_be_absent`와 `count=None` FAIL 경우를 더했다. `RepoTest.test_committed_n5_questions_and_exam`도 더했다. 이 테스트는 문항이 60개 이상인지, 출처·작성 방법·검수 상태, targets가 비어 있지 않은지, 문항 유사 검사 0건, 템플릿의 jlpt.jp 출처, 시간 20/40/30, 청해 영역이 있는지 본다.
- 바꾸지 않은 것(메인 지시): worktree의 `app/src/main/assets/content/`, `content/ids/`. `authored` 단계가 만드는 `_workspace/03_content-engineer_M26a_ko_spans_authored.tsv`는 지웠다.

## uncertain: 사용자 확인 요청

- **영역별 문항 수(`parts[].count`)**: jlpt.jp의 testsections 페이지와 N5 PDF(영어판·일본어판 `n5_revised.pdf`)에는 영역, 유형, 목적, 시간만 있고 문항 수는 없다. architecture.md 9.2.1("확인하지 못한 값은 채우지 않고 uncertain")에 따라 count를 비워 두었고 다른 근거를 지어내지 않았다.
  - 영향: 앱의 `ExamPart(count: Int)`가 이 템플릿을 읽으면 count가 없어 파싱에 실패한다. 9.0 규칙에 따라 그 급수의 모의고사가 없는 것으로 처리된다(S12 모의고사 카드 숨김). 유형별 연습은 영향을 받지 않는다.
  - 결정이 필요하다. (A) 앱이 정한 연습용 문항 수를 "공식 아님"으로 표시하고 채운다. (B) 다른 공식 자료(예: 공식 문제집)의 문항 수를 사용자가 확인해 알려 준다. (C) count가 없으면 그 유형의 모든 문항을 쓰도록 앱 규칙을 바꾼다.

## 검사 결과

- `python3 -I content/pipeline/build.py authored`: 종료 코드 0. `WARN: similar`, `FAIL: authored duplicate`, `FAIL: authored <id> blank`가 없다.
- `content/pipeline/check.sh`: 종료 코드 0(unittest 159개 OK, 정답 위치 분포 검사 포함).
- 릴리스 빌드: `/tmp/m21/rel`(worktree 사본, `content/raw`·`.git`·빌드 산출물 제외)에서 `python3 -I content/pipeline/build.py`를 돌렸다. 종료 코드 0이고 출력은 `build: … 62 question, 1 exam …`, `check: OK`다.
  - `SELECT count(*) FROM question WHERE level=5` = **62**(조건 ≥ 60)
  - `exam_template`: level 5, source_url jlpt.jp, checked_at 2026-11-03
- verify.sh: PASS(종료 코드 0), 로그 `_workspace/build/verify_20261103_133947.log`.

## 독창성

기출·교재 문항을 옮기지 않았다. 문장, 인물(森さん·ポチ·リーさん 등), 수치(값, 시간표)는 모두 새로 짰다. 짧은 기초 문형(〜てください 등)은 누구나 같은 형태로 쓰는 것이고, 그것을 둘러싼 장면은 새로 썼다. 외부 문장(Tatoeba 등)은 쓰지 않았다(`sources: []`).

## 급수 판단

N5 어휘 목록(S-06 릴리스 빌드의 level=5 vocab)과 M16 N5 문법 범위 안에서 썼다. 정답 판단에 필요하지 않은 낱말 중 N5를 넘는 것(お知らせ, 水泳, 空港, 東京, 一日目 등)에는 루비를 달았다. 가능형, 〜ことができる, 〜ので처럼 N4로 본 형식은 지문에서 뺐다.

## 리뷰 지적 수정 (2026-11-07, `_workspace/review_M16_M21_findings.json`)

문항·항목 ID는 모두 그대로다(아직 git 추적 전). 정답 위치(answerIndex)는 하나도 바꾸지 않아 분포(vocab 7/8/7/7, grammar 5/6/5/5, reading 3/3/3/3)가 유지된다. 고친 문항은 메인 체크아웃의 `content/raw/S-01/JMdict_e.gz`로 정답이 하나뿐인지 직접 확인했다.

### M21 문항
- q-n5-vocab-0005(high): 오답 いりくち·はいりぐち가 JMdict 1582820에 실린 入口 읽기였다 → いれぐち·にゅうぐち(JMdict 어느 항목에도 없음)로 교체. 해설의 "ぐち로 탁음이 된다"·"いりぐち로만 읽는다" 단정을 "보통 いりぐち", "흔히 ぐち"로 고침.
- q-n5-grammar-0008(high): 暖か는 な형용사(JMdict 2081300)라 に도 성립 → な형용사 형태가 없는 暑い로 바꿈(夏に なって、暑＿＿ なりました). targets jm:1586420 → jm:1343460.
- q-n5-vocab-0004(medium): whyWrongKo[2]를 "だいにん은 요금표 등에서 쓰는 읽기(JMdict 2860067), 사람을 말할 때는 おとな"로 정정.
- q-n5-vocab-0022(medium): stem 앞에 "私は 今 日本語を 習って います"를 넣고 오답을 授業(가르치는 쪽)·先生(선생님으로 일함)·散歩로 바꿈. 勉強·質問·話는 정답이 둘이 되므로 쓰지 않음.
- q-n5-vocab-0021: 오답 ひき → びき(さん 뒤 연탁형).
- q-n5-vocab-0002: whyWrongKo[2]에 "ずしょかん은 지금은 쓰지 않는 옛 읽기(JMdict ok)" 추가.
- q-n5-grammar-0021: 0005와 같은 より/から/まで 구성이 겹쳐, 문항이 없던 gr-n5-0022 〜でしょう 대상의 새 글(소풍 안내, たぶん 雨＿＿)로 바꿈. 오답 でした(시제)·じゃありません(우산 문장과 모순)·ましょう(でしょう와 헷갈리는 권유형). targets gr-n5-0022, jm:1407980.
- q-n5-vocab-0012: "右の てで" → "ごはんの 前に、[[て]]を 洗って ください".
- q-n5-reading-0005: 메모 서명 母 → お母さんより(본문 지칭과 맞춤).

### M16 문법
- 0021: 동사 비교 접속 행(辞書形＋より)과 예문 삭제, 명사 예문(このかばんより あのかばんの ほうが 軽い)으로 대체.
- 0004: 예문의 なくて(N4) → 忙しかったですから.
- 0012: posKo 부정·과거 → 명사(부정)·명사(과거), 부정 행 form 名詞＋はほしくないです + 설명에 "부정문에서는 は를 쓰는 일이 많다" 추가.
- 0015: 번역 "잊지 말고 가져오세요" → "잊지 마세요".
- 0013·0014·0016: 제목 꼬리표(理由·逆接·目的) 제거. 구분은 meaningKo에 있음.
- 0016: examples[1] 이동 동사 帰ります도 [[…]]로 표시.
- 0002: meaningKo를 명사·な형용사 부정으로 넓힘, 비교 쌍을 今日は日曜日で(はありません)す로 바꿈. 0001 접속 예를 これは私の本です로 바꿈(兄・父は医者 반복 해소).

### 파이프라인
- test_grammar `test_committed_n5_grammar`, test_question `test_committed_n5_questions_and_exam`: reviewStatus를 허용 값 집합(unreviewed·sampled·reviewed)으로 검사해 review-apply 뒤에도 통과. 문법 예문 출처 키는 source·ref·author·license 모두 검사.
- test_question: `test_count_may_be_absent`를 test_fields 끝 뒤로 옮겨 기존 FAIL 검사를 test_fields로 되돌림. N5 릴리스 템플릿은 count 전부 있음·countNote에 "공식 값이 아님"·exam_rows 1행을 검사.
- pl_question의 count 없는 part 허용(행은 쓰지 않음)은 하위 호환으로 그대로 둠.

### count 결정(잠정 A)
- `content/authored/exams/N5.json`에 연습용 count를 채우고 최상위 `countNote`("공식 값이 아님")를 넣음. 위 uncertain 절의 결정 대기는 이것으로 대체됨.
- 문자·어휘 7/5/6/3(풀 8/7/9/5), 문법 9/4/4(풀 11/6/4), 독해 3/2/1(풀 6/3/3), 청해 7/6/5/6(풀 없음 → 앱 shortfall). architecture.md 9.2.1에 한 줄 기록.

### 건너뛴 것
- 0016 제목의 行 한자 표기(0018 まえに와 표기 불일치): 行은 N5 한자라 유지.
- M16 "검토 범위" 항목: 조치 불요. 사람 검수(D-03) 때 0008·0011 서술 확인 권고는 그대로 남김.

### 검사
- `python3 -I content/pipeline/build.py authored` 종료 코드 0(FAIL·WARN 없음). 생긴 `_workspace/03_content-engineer_M26a_ko_spans_authored.tsv`는 지움.
- `content/pipeline/check.sh` 종료 코드 0. `verify.sh` 종료 코드 0(로그 `_workspace/build/verify_20261107_085402.log`).
