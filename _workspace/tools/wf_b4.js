export const meta = {
  name: 'jlpt-implement-milestones',
  description: '마일스톤마다 구현 → 빌드 검증 → QA·ECC 코드/디자인·콘텐츠 리뷰 → 지적 교차 검증 → 수정 루프를 돈다',
  phases: [
    { title: '구현', detail: 'feature-engineer, content-engineer 또는 jlpt-content-author가 구현하고 verify.sh를 실행한다' },
    { title: '리뷰', detail: 'android-qa-inspector, 코드 리뷰어(Kotlin/Python), UI 마일스톤은 접근성·시각 디테일, 콘텐츠 마일스톤은 콘텐츠 리뷰' },
    { title: '지적 검증', detail: 'low가 아닌 지적, 완료 조건 실패, 이전 라운드 미해결 지적을 검증자가 다시 판정한다' },
    { title: '수정', detail: '확정 지적, 완료 조건 실패, 빌드 실패를 담당 구현자가 고친다' },
  ],
}

// 세션 중간에 만든 .claude/agents/ 유형은 다음 세션부터 로드된다. 유형을 못 찾으면 정의 파일을 읽는 기본 에이전트로 대신 실행한다.
const call = (prompt, opts) => agent(prompt, opts).catch(e => {
  if (!opts || !opts.agentType || !/not found/i.test(String((e && e.message) || e))) throw e
  const rest = Object.assign({}, opts)
  delete rest.agentType
  log(`${opts.agentType} 유형을 찾지 못함 — .claude/agents/${opts.agentType}.md 를 읽는 기본 에이전트로 대신 실행`)
  return agent(`먼저 .claude/agents/${opts.agentType}.md 를 Read로 읽고, 그 문서의 역할·작업 원칙·입출력 규칙을 따르는 ${opts.agentType} 에이전트로서 아래 작업을 한다.\n\n${prompt}`, rest)
})

// args: { milestones: Milestone[] (milestones.json 항목, 의존 순서대로), maxFixRounds?: number, reviewOnly?: boolean }
// reviewOnly: 이미 구현된 마일스톤을 다시 리뷰만 한다("QA 다시", "디자인 검증", "콘텐츠 검토 다시"). 확정 지적이 있으면 수정 라운드는 돈다.
const A = {"milestones": [{"id": "M5", "title": "[MVP] 급수 선택(S01, 첫 실행)·홈·설정 화면", "owner": "feature-engineer", "dependsOn": ["M1", "M2", "M3", "M4"], "ui": true, "content": false, "scope": "architecture.md 7절 LevelUiState·HomeUiState·SettingsUiState와 ui-design.md S01·S02·S08. 첫 실행(onboarding_done == 0)이면 시작 라우트가 LevelSelect(S01). 급수 선택은 content_meta.levels에 없는 급수를 비활성 + '콘텐츠 준비 중'(ui-design 5.9). 단, 콘텐츠 업데이트로 사라진 급수가 이미 선택돼 있으면 홈의 NO_CONTENT_FOR_LEVELS로 처리한다. 홈: HomeStatus 여섯 상태, dueCount·nextDueAtEpochMs·nextDueCount는 M4의 SessionCards.load 결과로만 계산(architecture.md 8.2), 급수별 진도, 첫 안내 카드(firstNotice, 닫으면 first_notice_dismissed — 이 마일스톤에서 AttributionRepository.load()와 AttributionData(architecture.md 7·8.2, attribution.json 파싱·실패 시 예외)를 만들고 M7이 그것을 쓴다), '새 카드 5장 더'(extra_new_cards), 상단 메뉴 '정보' → About, NO_LEVEL_SELECTED의 '급수 고르기' → Settings(openLevelPicker = true). 설정: 학습 급수, 하루 새 카드 5~50(5 단위), 하루 시작 시각(정시 00:00~12:00), 카드 종류 스위치 셋(단어 인식·한자·단어 회상, 모두 끄면 저장 안 함), 앞면·본문 후리가나, 테마, '출처와 라이선스' 행(About로 한 번), 앱 버전·콘텐츠 판. 목표 기억률은 화면에 두지 않는다.", "acceptance": ["ViewModel 테스트: 고정 Clock에서 dueCount·newTodayCount가 SessionPlanner 결과와 같고, 설정 변경(급수, 새 카드 한도, 경계)이 홈 값에 반영된다", "ViewModel 테스트: retired·콘텐츠에 없는 카드, 꺼진 카드 종류의 카드, 대상 급수 밖 카드는 기한이어도 dueCount·nextDueAtEpochMs·nextDueCount에 들어가지 않고, 같은 픽스처로 ReviewViewModel 대기열 길이가 홈 dueCount와 같다(SessionCards 하나를 공유)", "Robolectric 테스트: assets/content/attribution.json을 읽지 못하거나 깨졌으면 AttributionRepository.load()가 예외를 던지고 HomeUiState.firstNotice == null, 첫 안내 카드 없이 홈 상태는 정상(READY 등)이다. 정상 에셋이면 firstNotice가 attribution.json firstNotice와 같다", "Compose UI 테스트: NO_LEVEL_SELECTED의 '급수 고르기'를 누르면 설정 탭으로 가고 LevelPicker 하단 시트가 열려 있다(Settings(openLevelPicker = true)). 설정 탭을 직접 누르면 시트가 닫혀 있다", "ViewModel 테스트: HomeStatus가 급수 0개 → NO_LEVEL_SELECTED, 고른 급수가 모두 콘텐츠 밖 → NO_CONTENT_FOR_LEVELS, 할 것 있음 → READY, 한도만 다 씀 → NEW_LIMIT_REACHED, 남은 새 카드 없음 → ALL_DONE이고, ALL_DONE·NEW_LIMIT_REACHED에서 nextDueAtEpochMs·nextDueCount가 채워진다", "Robolectric 테스트: '새 카드 5장 더'를 누르면 오늘 한도가 5 늘고 앱 재생성 후에도 같은 학습일에는 유지되며, 다음 학습일에는 설정값으로 돌아간다. new_cards_per_day는 바뀌지 않는다", "Robolectric 테스트: onboarding_done == 0이면 시작 화면이 S01이고 '시작'을 누르면 target_levels·new_cards_per_day·onboarding_done = 1이 저장되어 다음 실행은 홈으로 시작한다", "Compose UI 테스트: content_meta.levels에 없는 급수 행은 선택할 수 없고(toggleable 비활성) '콘텐츠 준비 중'이 보인다", "Robolectric 테스트: 설정 값이 user_settings에 저장되고 앱 재생성 후 유지된다. 범위 밖 값과 카드 종류 모두 끔은 저장되지 않는다", "content_meta.kind == sample이면 홈에 샘플 띠가 보인다(스크린샷 home_sample_banner)", "ViewModel 테스트: 가짜 저장소가 빈 target_levels를 내면(손상된 복원 값, 방어용) HomeStatus가 NO_LEVEL_SELECTED이고 '급수 고르기' 행동이 있다. 설정 화면의 급수 0개 상태에서는 '저장'이 비활성이다(Compose UI 테스트)", "Compose UI 테스트: 설정 탭에서 '출처와 라이선스' 항목을 한 번 누르면 About 라우트로, 홈 상단 메뉴 '정보'를 누르면 About 라우트로 이동한다", "Roborazzi 스크린샷(ui-design 12절 이름, 각 _light·_dark): level_loading, level_empty_selection, level_loaded, level_unavailable, home_loading, home_ready, home_no_level, home_no_content, home_all_done, home_new_limit, home_first_notice, home_sample_banner, settings_loaded, home_new_limit_light_font200"], "status": "todo"}, {"id": "M6", "title": "[MVP] 사전(단어·한자 목록)·상세 화면", "owner": "feature-engineer", "dependsOn": ["M2", "M3"], "ui": true, "content": false, "scope": "architecture.md 7절 BrowseUiState(단어/한자 탭, 급수 다중 필터 levels, 표기·읽기·한국어 뜻 검색, NO_RESULT·NO_CONTENT_FOR_FILTER 구분), VocabDetailUiState(표제어 Furigana.fromHeadword 루비, 품사 한국어 이름표, 뜻 목록과 ProvenanceChip, 포함 한자, 카드 상태 요약, RETIRED), KanjiDetailUiState(획수, 음·훈 읽기, 한국어 음, 쓰인 단어, 뜻 행 없음, 필순 절은 데이터 없으면 숨김, RETIRED). 영어 원문 뜻 절은 두지 않는다.", "acceptance": ["ViewModel 테스트: 급수 여러 개 필터, 한국어 뜻으로 검색(text_ko 일치)되는 단어, 검색어 있음·결과 0 → NO_RESULT, 검색어 없음·결과 0 → NO_CONTENT_FOR_FILTER, 없는·retired ID 상세는 status RETIRED", "JMdict 품사 코드 → 한국어 이름표 표가 한 파일에 있고, 표에 없는 코드는 원 코드를 그대로 보인다(테스트)", "상세의 뜻마다 ProvenanceLabel 문구의 칩이 붙는다(다섯 가지 검수 상태 픽스처 스크린샷)", "KanjiDetailUiState.strokes == null이면 필순 절이 그려지지 않는다(Compose UI 테스트)", "목록은 LazyColumn/LazyVerticalGrid에 안정 key(id)를 쓴다(코드 리뷰 확인), 3,500행 픽스처 목록 스크린샷이 생성된다", "Roborazzi 스크린샷(ui-design 12절 이름, 각 _light·_dark): dict_words, dict_kanji, dict_no_result, dict_no_content_for_filter, word_detail_loaded, kanji_detail_loaded, kanji_detail_no_korean_reading. _font200: word_detail_loaded(루비가 본문과 어긋나지 않음), dict_kanji(격자 칸이 heightIn(min = 64.dp)로 늘어나 한국어 음이 잘리지 않음)"], "status": "todo"}, {"id": "M7", "title": "[MVP] 출처와 라이선스(S09, 저작자 표시·고지) 화면", "owner": "feature-engineer", "dependsOn": ["M2", "M3", "M4", "M5"], "ui": true, "content": false, "scope": "설정 행·홈 메뉴·S90에서 들어가는 별도 화면(S-01.attribution-place)과 출처·검수 칩의 하단 시트(ui-design 5.7: attribution.json meaningSourceTexts에서 ProvenanceLabel.sheetText로 문장 선택, '출처와 라이선스 보기' → About). M5의 AttributionRepository.load()를 그대로 쓰고 openSourceLicenses()(assets/licenses/)를 더한다(별도 파서를 만들지 않는다). About 상태는 AttributionRepository만으로 정하고, meaningReviewCounts가 실패하거나 ContentDbFailed라 ContentRepository가 없으면 reviewCounts = null로 집계 줄만 숨긴다(architecture.md 7절). About(anchor)로 오면 그 고지·자료 카드로 스크롤한다. assets/content/attribution.json과 assets/licenses/만 읽어 고지 카드(notices의 제목·본문을 받은 순서 그대로 — 앱은 문장을 조립하지 않는다, N-10 아래 meaningReviewCounts 집계 줄), 콘텐츠 DB 라이선스(dbLicense, N-08), 자료별 이름·판·저작자 표시 문구·라이선스와 링크·변경 내용·extraCredits(N-07), 콘텐츠 판, 아이콘·오픈소스 라이선스(py-fsrs MIT, apache-2.0.txt, libraries.json)를 보인다. 에셋을 읽지 못하면 FAILED 상태로 R.string.attribution_fallback만 보인다. 링크는 ACTION_VIEW를 ui/common/ExternalIntents.kt의 startExternal로 넘기고(미리 resolveActivity로 판정하지 않음), ActivityNotFoundException이면 안내 문구를 보인다(architecture.md 5.1·7절). architecture.md 6.6 끝의 assets/licenses/ 파일을 손으로 커밋한다.", "acceptance": ["Robolectric 테스트: 번들 콘텐츠 DB의 모든 source_id·level_source_id가 attribution.json sources에 있고 화면 상태 AboutUiState.sources에 나타난다", "app/src/main/java 와 app/src/main/res(생성 파일 values/attribution_fallback.xml 제외) 아래에 'EDRDG', 'JMdict', 'KANJIDIC', 'Tatoeba', 'KanjiVG', 'Waller' 문자열이 없다(grep, 출처 문구 하드코딩 금지)", "AboutUiState.notices가 attribution.json notices의 id·title·body 그대로(순서 포함)이고 N-08이 없으며, dbLicense(이름·URL)가 따로 있다. modelNames가 attribution.json modelNames 그대로다. reviewCounts가 ContentRepository.meaningReviewCounts()와 같다(테스트)", "AboutUiState.sources[].extraCredits[].text가 attribution.json(= 매니페스트) 값과 글자 그대로 같다(번역·가공 없음, 테스트)", "단위 테스트: ProvenanceLabel.sheetText가 architecture.md 6.6 표의 키 7개(translated-llm, translated-llm-nomodel, translated-human, authored-llm, authored-llm-nomodel, authored-human, unknown)를 ko_method·ko_source 조합대로 고르고, {model}을 ko_method의 모델 이름으로 채우며, 모델 이름이 비면 -nomodel 문장을 써서 빈 괄호 'AI()'가 나오지 않는다", "Compose UI 테스트: 출처·검수 칩을 누르면 하단 시트가 열리고 '출처와 라이선스 보기'를 누르면 About 라우트로 간다", "단위 테스트(architecture.md 6.6): res/drawable/ic_ms_*.xml마다 'ic_ms_'·'.xml'을 떼고 끝의 '_fill'도 뗀 기본 이름이 docs/content/manifest.json S-11 usedFields(기본 이름 배열)에 있다. 목록 밖 이름(예: ic_ms_foo.xml, ic_ms_foo_fill.xml)을 넣은 픽스처는 실패한다", "Robolectric 테스트: attribution.json이 깨졌으면 status FAILED이고 화면 문자열이 R.string.attribution_fallback과 같다", "Robolectric 테스트: AppState.ContentDbFailed에서 S90 '출처 보기' → About이면 status LOADED, sources·notices가 attribution.json대로 보이고 reviewCounts == null(집계 줄 없음). 정상 DB에서 meaningReviewCounts가 예외를 던지는 가짜 저장소여도 LOADED이고 reviewCounts == null이다", "Compose UI 테스트: About(anchor = \"S-03\")(합성 attribution에 S-03 카드)로 열면 그 카드가 화면에 보이도록 스크롤되어 있고, 없는 anchor·null이면 맨 위다", "Robolectric 테스트: 링크를 열 Activity가 없는 환경에서 라이선스 링크를 누르면 앱이 죽지 않고 안내 문구가 보인다. app/src/main/java에 resolveActivity·queryIntentActivities 호출이 없다(grep)", "assets/licenses/py-fsrs.txt에 MIT 고지 전문, apache-2.0.txt에 Apache-2.0 전문이 있고 화면의 오픈소스 라이선스 항목에 나온다", "단위 테스트: gradle/libs.versions.toml의 [libraries] 키가 모두 assets/licenses/libraries.json에 있고, Material Symbols 항목이 있다", "병합된 매니페스트에 INTERNET 권한이 없고 링크는 Intent ACTION_VIEW로 연다(코드 리뷰, android-intent-security 기준)", "Roborazzi 스크린샷(ui-design 12절 이름, 각 _light·_dark): attribution_loaded(샘플 attribution, 긴 목록 스크롤 위치 2곳), attribution_error(R.string.attribution_fallback 문단), attribution_loaded_light_font200"], "status": "todo"}], "maxFixRounds": 3, "reviewOnly": false}
const milestones = A.milestones || []
const maxFix = Number.isInteger(A.maxFixRounds) && A.maxFixRounds >= 0 ? A.maxFixRounds : 2
const reviewOnly = !!A.reviewOnly
// 담당자별 기본 코드 리뷰어. 마일스톤의 codeReviewer(null 포함)가 있으면 그것을 쓴다.
const CODE_REVIEWER = { 'feature-engineer': 'ecc:kotlin-reviewer', 'content-engineer': 'ecc:python-reviewer', 'jlpt-content-author': null }
if (!milestones.length) return { error: 'args.milestones가 비어 있다. milestones.json의 todo 항목을 넘겨야 한다.' }
const noAcc = milestones.filter(m => !(m.acceptance || []).length).map(m => m.id)
if (noAcc.length) return { error: `완료 조건(acceptance)이 없는 마일스톤: ${noAcc.join(', ')}. architect에게 판정 가능한 완료 조건을 받아야 한다.` }
const badOwner = milestones.filter(m => !(m.owner in CODE_REVIEWER)).map(m => `${m.id}(${m.owner})`)
if (badOwner.length) return { error: `알 수 없는 owner: ${badOwner.join(', ')}. ${Object.keys(CODE_REVIEWER).join(' | ')} 중 하나여야 한다.` }

const RESULT = {
  type: 'object', required: ['status', 'summary', 'filesChanged', 'verifyPassed', 'verifyExit'],
  properties: {
    status: { type: 'string', enum: ['done', 'failed'] },
    summary: { type: 'string' },
    filesChanged: { type: 'array', items: { type: 'string' } },
    verifyPassed: { type: 'boolean' },
    verifyExit: { type: 'integer', enum: [0, 1, 2] },
    verifyLog: { type: 'string' },
    screenshots: { type: 'array', items: { type: 'string' } },
    rejectedFindings: { type: 'array', items: { type: 'object', required: ['title', 'reason'], properties: {
      title: { type: 'string' }, reason: { type: 'string' } } } },
  },
}
const FINDING_ITEMS = { type: 'array', items: { type: 'object', required: ['title', 'severity', 'file', 'evidence', 'fix'], properties: {
  title: { type: 'string' }, severity: { type: 'string', enum: ['high', 'medium', 'low'] },
  file: { type: 'string' }, evidence: { type: 'string' }, fix: { type: 'string' } } } }
const FINDINGS = { type: 'object', required: ['findings'], properties: { findings: FINDING_ITEMS } }
// QA는 통과 판정의 근거이므로 빌드 결과와 완료 조건 판정을 반드시 내야 한다.
const QA_FINDINGS = {
  type: 'object', required: ['findings', 'verifyPassed', 'acceptance'],
  properties: {
    findings: FINDING_ITEMS,
    verifyPassed: { type: 'boolean' },
    acceptance: { type: 'array', minItems: 1, items: { type: 'object', required: ['criterion', 'result'], properties: {
      criterion: { type: 'string' }, result: { type: 'string', enum: ['pass', 'fail', 'unverified'] }, note: { type: 'string' } } } },
  },
}
const VERDICT = {
  type: 'object', required: ['status', 'reason'],
  properties: { status: { type: 'string', enum: ['confirmed', 'refuted', 'uncertain'] }, reason: { type: 'string' } },
}

// 도구가 제한된 에이전트도 스킬을 읽을 수 있게 파일 경로를 함께 준다.
const PS = n => `.claude/skills/${n}/SKILL.md`
const ECC = n => `ECC 스킬 ${n}(Skill 도구로 ecc:${n}를 불러온다. Skill 도구가 없으면 Glob으로 ~/.claude/plugins/cache/ecc/ecc/*/skills/${n}/SKILL.md 를 찾아 Read로 읽는다. 둘 다 안 되면 그 사실을 low 지적 하나로 보고한다)`
const SPECS = '명세: docs/spec/architecture.md, docs/spec/ui-design.md, docs/content/sources.md, docs/content/manifest.json'
const VERIFY = '.claude/skills/android-build-verify/scripts/verify.sh'
const briefOf = m => `마일스톤 ${m.id} — ${m.title}\n범위: ${m.scope}\n완료 조건:\n${(m.acceptance || []).map(a => '- ' + a).join('\n')}`
const implOpts = (m, label, ph) => Object.assign({ agentType: m.owner, label, phase: ph, schema: RESULT }, m.model ? { model: m.model } : {})
const RESULT_RULE = `${VERIFY} 를 실행하고 종료 코드를 verifyExit에, 출력된 로그 경로(_workspace/build/verify_*.log)를 verifyLog에 적는다(verifyPassed는 종료 코드 0일 때만 true).`
const implPrompt = m => `${briefOf(m)}\n${SPECS}\n구현하고 테스트를 작성한 뒤 ${RESULT_RULE} 요약을 _workspace/03_${m.owner}_${m.id}_impl.md 에 남긴다.` +
  (m.ui ? `\n화면 마일스톤이다. ${PS('ui-design-review')} 3절대로 상태별 스크린샷을 만들고 경로를 screenshots에 모두 적는다.` : '')
const codeReviewerOf = m => (m.codeReviewer !== undefined ? m.codeReviewer : CODE_REVIEWER[m.owner])
const CODE_FOCUS = {
  'ecc:kotlin-reviewer': `코루틴·Flow 안전성(취소, 디스패처, 수명주기), Compose 모범 사례(상태 읽기 범위, 재구성 비용, 안정성), Room 트랜잭션, Android 함정. ${PS('compose-app-dev')} 와 ${PS('srs-scheduler')} 를 읽고 그 규칙(시계 주입, 학습일 경계, 일본어 로캘)을 기준에 포함한다.`,
  'ecc:python-reviewer': `재현성(체크섬 대조, 판 고정, 결정적 출력), 오류 처리(예외를 삼키지 않음), 스트리밍 파싱, 표준 라이브러리만 사용, 네트워크 없이 도는 검사. ${PS('content-pipeline')} 를 읽고 그 규칙을 기준에 포함한다.`,
}

function reviewersFor(m, changed, shots) {
  const scope = (reviewOnly || m.reviewOnly) ? '리뷰 전용 실행이다. 이 마일스톤 범위의 현재 산출물 전체를 검토한다.' : `변경 파일: ${changed.join(', ') || '(보고 없음)'}`
  const ctx = `${briefOf(m)}\n${scope}\n구현 요약: _workspace/03_${m.owner}_${m.id}_impl.md\n${SPECS}\n파일을 수정하지 말고 지적만 반환한다. 지적마다 파일:줄(콘텐츠면 파일과 항목 id)과 근거를 적는다.`
  const list = [
    { key: 'qa', agentType: 'android-qa-inspector', schema: QA_FINDINGS,
      prompt: `${PS('android-integration-qa')} 를 읽고 그 대조표로 통합 정합성을 검사하라. ${VERIFY} 를 직접 실행해 종료 코드 0이면 verifyPassed=true로 반환한다. 위 완료 조건 ${(m.acceptance || []).length}개 각각에 대해 criterion에 조건 문장을 그대로 옮기고 pass | fail | unverified 판정을 반환하라. 하나도 빠뜨리지 않는다.\n${ctx}` },
  ]
  const cr = codeReviewerOf(m)
  if (cr) {
    list.push({ key: 'code', agentType: cr,
      prompt: `변경된 코드를 리뷰하라: ${CODE_FOCUS[cr] || '언어 관례, 오류 처리, 테스트 가능성.'} 프로젝트 스킬과 일반 관례가 충돌하면 프로젝트 스킬이 우선한다. 계층 간 계약 대조는 QA가 맡으므로 제외한다.\n${ctx}` })
  }
  if (m.content) {
    list.push({ key: 'content',
      prompt: `${PS('jlpt-content-authoring')} 와 ${PS('content-licensing')} 를 읽고 이 마일스톤의 콘텐츠를 검토하라: 일본어의 정확성과 자연스러움, 급수 적합성, 정답 유일성과 오답 설계, 한국어 설명·뜻의 정확성, 독창성(기출·시판 교재 복제 의심은 일치하는 원문을 웹 검색으로 찾았을 때만 high), 외부 문장의 출처 기록, 한국어 뜻의 출처·검수 상태 표시. 심각도는 jlpt-content-authoring 8절을 따른다. 전수가 어려우면 표본을 고르고 표본 기준과 크기를 low 지적 하나로 남긴다.\n${ctx}` })
  }
  if (m.ui) {
    const s = shots.length ? shots.join(', ') : '(보고된 스크린샷 없음 — 이 자체를 high 지적으로 보고하라)'
    list.push(
      { key: 'a11y', agentType: 'ecc:a11y-architect',
        prompt: `${ECC('accessibility')} 를 기준으로, ${PS('ui-design-review')} 의 D4·D5·D6·D7·D8 기준과 함께 WCAG 2.2 AA의 Android 적용을 검토하라. 스크린샷 PNG를 Read로 직접 열어 보고, Compose 코드의 semantics(contentDescription, stateDescription, liveRegion, role, onClickLabel, 일본어 구간 localeList, 터치 영역)를 함께 확인한다.\n스크린샷: ${s}\n${ctx}` },
      { key: 'visual',
        prompt: `${ECC('make-interfaces-feel-better')} 와 ${ECC('design-system')} 를 불러오고 ${PS('ui-design-review')} 를 따른다. 스크린샷 PNG를 Read로 직접 열어 보고 docs/spec/ui-design.md 의 토큰·상태 정의와 대조해 시각 디자인(D1·D2·D3·D9·D10·D11, 간격·위계·정렬·토큰 일관성)을 검토하라. 일본어가 네모(글리프 없음)로 보이거나 한자가 한국식 글자 모양이면 high다. 색 대비 수치가 필요하면 python3 -I .claude/skills/ui-design-review/scripts/contrast.py 로 계산한다.\n스크린샷: ${s}\n${ctx}` },
    )
  }
  return list
}

async function review(m, changed, shots, round) {
  const reviewers = round >= 1 ? reviewersFor(m, changed, shots).filter(r => r.key === 'qa') : reviewersFor(m, changed, shots)
  const run = (r, tag) => call(r.prompt,
    Object.assign({ label: `리뷰:${r.key}:${m.id}#${round}${tag}`, phase: '리뷰', schema: r.schema || FINDINGS }, r.agentType ? { agentType: r.agentType } : {}))
    .then(res => res && Object.assign({ key: r.key }, res))
  // QA가 완료 조건을 다 판정하지 않으면 결과가 없는 것과 같다.
  const usable = (r, x) => x && (r.key !== 'qa' || x.acceptance.length >= (m.acceptance || []).length)
  const pick = res => reviewers.map((r, i) => usable(r, res[i]) ? res[i] : null)
  let got = pick(await parallel(reviewers.map(r => () => run(r, ''))))
  const first = reviewers.filter((r, i) => !got[i])
  if (first.length) {
    log(`${m.id} 리뷰어 ${first.map(r => r.key).join(', ')} 결과 없음 또는 불완전 — 한 번 재시도`)
    const again = pick(await parallel(reviewers.map((r, i) => () => got[i] ? Promise.resolve(got[i]) : run(r, '#재시도'))))
    got = got.map((x, i) => x || again[i])
  }
  return { got: got.filter(Boolean), missing: reviewers.filter((r, i) => !got[i]).map(r => r.key) }
}

const NO_VERDICT = '검증 결과 없음'
const judge = (m, round, f, i) => call(
  `다음 리뷰 지적을 반박하려는 자세로 검증하라. 지적한 파일과 상대 코드(스크린샷이면 PNG, 콘텐츠면 해당 항목)를 현재 상태로 직접 읽는다. 실제 오작동·학습 기록 손실·접근성 위반·명세 위반·라이선스 의무 누락·콘텐츠 오류·완료 조건 미충족을 일으키면 confirmed, 근거가 틀렸거나 이미 고쳐졌으면 refuted, 판단할 수 없으면 uncertain.` +
  (f.carried ? '\n이전 라운드에서 확정된 지적이다. 현재 상태에서 아직 유효한지 판정한다.' : '') +
  (f.rejection ? `\n구현자 반박: ${f.rejection}\n반박 근거가 맞으면 refuted, 틀리면 confirmed로 판정하고 이유를 적는다.` : '') +
  `\n지적: ${JSON.stringify(f)}\n${SPECS}`,
  { label: `검증:${m.id}#${round}.${i}`, phase: '지적 검증', schema: VERDICT, effort: 'high' })
const withVerdict = (f, v) => Object.assign({}, f, { verdict: v || { status: 'uncertain', reason: NO_VERDICT } })

const report = []
const failed = new Set()
// shortcut: 마일스톤을 순서대로 처리한다(Gradle 설정·콘텐츠 DB 같은 공유 파일 충돌 방지). 독립 마일스톤이 많아지면 dependsOn 기준 병렬화 + isolation: 'worktree'로 바꾼다.
for (const m of milestones) {
  if ((m.dependsOn || []).some(d => failed.has(d))) {
    failed.add(m.id)
    report.push({ id: m.id, status: 'skipped', reason: '선행 마일스톤 실패' })
    log(`${m.id} 건너뜀: 선행 마일스톤 실패`)
    continue
  }
  let impl
  if (reviewOnly || m.reviewOnly) {
    impl = { status: 'done', summary: '리뷰 전용 실행', filesChanged: [], verifyPassed: true, verifyExit: 0, screenshots: m.screenshots || [] }
  } else {
    impl = await call(implPrompt(m), implOpts(m, `구현:${m.id}`, '구현'))
    if (!impl) {
      log(`${m.id} 구현 결과 없음 — 한 번 재시도`)
      impl = await call(implPrompt(m), implOpts(m, `구현:${m.id}#재시도`, '구현'))
    }
  }
  if (impl && impl.verifyExit === 2) {
    report.push({ id: m.id, status: 'env-missing', summary: impl.summary })
    log(`${m.id} 빌드 환경 없음(verify.sh 종료 코드 2) — 워크플로를 멈춘다`)
    return { error: 'ENV', milestone: m.id, report }
  }
  const changed = new Set(impl ? impl.filesChanged : [])
  let shots = (impl && impl.screenshots) || []
  let confirmed = [], uncertain = [], lows = [], acceptance = [], open = [], missing = []
  let qaOk = false, qaVerify = false, rounds = 0
  const rejected = []
  while (impl) {
    if (impl.verifyPassed) {
      const r = await review(m, [...changed], shots, rounds)
      missing = r.missing
      const qa = r.got.find(x => x.key === 'qa')
      acceptance = (qa && qa.acceptance) || []
      qaVerify = !!qa && qa.verifyPassed === true
      qaOk = qaVerify && acceptance.length >= m.acceptance.length
      const fresh = r.got.flatMap(x => (x.findings || []).map(f => Object.assign({}, f, { lens: x.key })))
      // 완료 조건 실패도 지적과 같은 경로(검증, 이월, 구현자 반박 재판정)로 다룬다.
      const accFails = acceptance.filter(a => a.result === 'fail').map(a => ({
        title: `완료 조건 실패: ${a.criterion}`, severity: 'high', file: '-', evidence: a.note || '', fix: '완료 조건을 만족하게 고친다', lens: 'acceptance' }))
      lows = fresh.filter(f => f.severity === 'low' || (rounds >= 1 && f.severity === 'medium'))   // low는 검증하지 않고 보고만 한다(비용)
      const freshJ = fresh.filter(f => f.severity === 'high' || (rounds === 0 && f.severity === 'medium')).concat(accFails)
      const rejectionOf = t => (rejected.find(x => x.title === t) || {}).reason
      const toJudge = freshJ
        .concat(open.filter(o => !freshJ.some(f => f.title === o.title)).map(o => Object.assign({}, o, { carried: true })))
        .map(f => Object.assign({}, f, { rejection: rejectionOf(f.title) }))
      const res = await parallel(toJudge.map((f, i) => () => judge(m, rounds, f, i)))
      const judged = toJudge.map((f, i) => withVerdict(f, res[i]))   // 검증자가 실패해도 지적은 남긴다
      confirmed = judged.filter(f => f.verdict.status === 'confirmed')
      uncertain = judged.filter(f => f.verdict.status === 'uncertain')
      // 검증자가 답하지 못한 지적은 보수적으로 막는다
      open = confirmed.concat(uncertain.filter(f => f.verdict.reason === NO_VERDICT))
    } else {
      qaOk = false   // 빌드가 깨졌으면 리뷰하지 않고 바로 수정한다. open은 다음 리뷰에서 다시 판정된다.
    }
    log(`${m.id} 라운드 ${rounds}: 빌드 ${impl.verifyPassed ? '통과' : '실패'}, QA ${qaOk ? '통과' : '미통과'}, 차단 지적 ${open.length}건${missing.length ? `, 리뷰어 누락 ${missing.join(',')}` : ''}`)
    if (missing.length) break   // 리뷰어가 없으면 판정할 수 없으므로 수정해도 소용없다
    if (impl.verifyPassed && impl.status === 'done' && qaOk && !open.length) break
    if (rounds >= maxFix) break
    rounds++
    const items = impl.verifyPassed ? open : []
    const fix = await call(
      `${briefOf(m)}\n${SPECS}\n아래를 고쳐라. 지적이 틀렸다고 판단하면 고치지 말고 rejectedFindings에 근거를 적는다. 이때 title에는 고칠 항목의 title을 글자 그대로 복사한다(검증자가 근거를 보고 다시 판정한다). 고친 뒤 ${RESULT_RULE}` +
      (m.ui ? ` 화면 마일스톤이므로 스크린샷을 갱신하고 현재 유효한 스크린샷 전체 목록을 screenshots에 적는다.` : '') +
      `\n빌드: ${impl.verifyPassed ? '통과' : '실패 — ' + (impl.verifyLog || '로그 경로 없음 — _workspace/build/ 의 최신 로그를 본다')}` +
      (impl.status === 'failed' ? `\n직전 구현자가 스스로 미완료로 보고했다: ${impl.summary}` : '') +
      (impl.verifyPassed && !qaVerify ? '\nQA가 verify.sh를 직접 실행했을 때 통과하지 않았다. _workspace/build/ 의 최신 로그를 확인한다.' : '') +
      (items.length ? `\n고칠 항목: ${JSON.stringify(items)}` : ''),
      implOpts(m, `수정:${m.id}#${rounds}`, '수정'))
    if (!fix) { log(`${m.id} 수정 결과 없음`); impl = null; break }
    if (fix.verifyExit === 2) {
      report.push({ id: m.id, status: 'env-missing', summary: fix.summary })
      return { error: 'ENV', milestone: m.id, report }
    }
    ;(fix.rejectedFindings || []).forEach(x => rejected.push(x))
    fix.filesChanged.forEach(f => changed.add(f))
    if (m.ui) shots = fix.screenshots || []   // 비어 있으면 다음 리뷰가 스크린샷 없음을 high로 지적한다
    impl = fix
  }
  const passed = !!impl && impl.verifyPassed && impl.status === 'done' && qaOk && !open.length && !missing.length
  const disputed = open.filter(f => rejected.some(x => x.title === f.title))
  const status = passed ? (acceptance.some(a => a.result === 'unverified') ? 'awaiting-device' : 'done')
    : (impl && !missing.length && disputed.length && disputed.length === open.length && impl.verifyPassed && qaOk) ? 'needs-decision'
    : 'failed'
  if (status === 'failed' || status === 'needs-decision') failed.add(m.id)
  report.push({
    id: m.id, status, rounds,
    verifyPassed: !!(impl && impl.verifyPassed), qaVerifyPassed: qaVerify,
    missingReviewers: missing,
    filesChanged: [...changed], screenshots: shots, acceptance,
    residual: open, uncertain, lowFindings: lows, rejected,
    summary: (impl && impl.summary) || '구현 에이전트 결과 없음',
  })
}
const count = s => report.filter(r => r.status === s).length
log(`완료 ${count('done')} / 실기기 대기 ${count('awaiting-device')} / 판단 필요 ${count('needs-decision')} / 실패 ${count('failed')} / 건너뜀 ${count('skipped')}`)
return { report }
