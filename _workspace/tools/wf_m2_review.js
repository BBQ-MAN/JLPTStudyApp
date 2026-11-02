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
const A = {"milestones": [{"id": "M2", "title": "[MVP] 앱 골격: 테마·일본어 표시·내비게이션·두 DB·S90", "owner": "feature-engineer", "dependsOn": ["M1"], "ui": true, "content": false, "scope": "architecture.md 1·4.2·4.4·4.5·5·5.1·7절. AppContainer(수동 DI, Clock·zone 주입, 두 DB를 열어 AppState로), 테마와 JpTypography·JpSpan·JpText(ja-JP), JpSpans.annotate, Furigana.parse·Furigana.fromHeadword + RubyText, NavigationSuiteScaffold 탭 3개(홈·사전·설정)와 빈 화면, StatusScreen(로딩·빈·오류), AppState.Opening 동안 NavHost 없이 전체 화면 StatusScreen 로딩(300 ms 뒤 '학습 콘텐츠를 준비하고 있습니다', architecture.md 7절 '시작'), S90 AppError 화면(콘텐츠 DB 열기 실패·학습 기록 DB 열기 실패 — 같은 NavHost의 시작 라우트이고 그 그래프에 About 라우트가 있다), Furigana.parse의 대상 구간 [[…]](RubySegment.emphasis, architecture.md 4.4)과 RubyText의 밑줄 그리기, ContentDatabase v1(4.2 엔티티, SELECT DAO, 해시 이름 파일로 createFromAsset 열기·옛 파일 삭제), StudyDatabase v1(study.db, 5절 엔티티 전부 — user_settings의 모든 열 포함, DAO, StudyRepository: 실패 시 예외, ID 목록 900개 단위 조회), 백업 규칙 파일 두 개.", "acceptance": [".claude/skills/android-build-verify/scripts/verify.sh 종료 코드 0(빌드·단위 테스트)", "app/schemas/com.jlptstudy.app.data.content.ContentDatabase/1.json 과 app/schemas/com.jlptstudy.app.data.study.StudyDatabase/1.json 이 생성되고 테이블·열·기본값이 architecture.md 4.2·5절과 같다", "Robolectric 테스트: applyReview 후 card_state·review_log가 함께 쓰이고, 트랜잭션 중 예외면 둘 다 쓰이지 않고 예외가 호출자에게 전달된다", "Robolectric 테스트: undoLast(afterLogId)가 id > afterLogId 중 id 최대 로그의 이전 상태를 복원하고(새 카드였으면 card_state 행 삭제) 로그를 지우지 않고 undone=1로 표시한다. 고정 Clock을 과거로 돌린 뒤 평가해도 undo 대상이 그 평가이고, 세션 시작 전 로그(id <= afterLogId)는 시각이 미래여도 되돌리지 않는다", "Robolectric 테스트: introducedToday가 undone=0·phase_before=0·학습일 구간 안의 로그만 낸다. user_settings 기본값이 5절 표와 같다(listening_script_first 0 포함). updateSettings가 범위 밖 값(new_cards_per_day 0·51·7), 카드 종류 네 개 모두 끔, 빈 target_levels를 거부하고(IllegalArgumentException), 저장된 target_levels의 1..5 밖 값은 읽을 때 버린다", "단위 테스트(가짜 DAO): states·cardContents에 2,000개 ID를 넘기면 DAO가 900개 이하 인자로만 불린다", "단위 테스트: Furigana.parse가 '{日本|にほん}語' → [日本/にほん, 語/null]을 내고, 짝이 안 맞는 괄호는 예외 없이 원문 그대로 하나의 구간으로 낸다", "단위 테스트(architecture.md 4.4 대상 구간): Furigana.parse('あとで[[{食|た}べて]]ください')가 [あとで/null/false, 食/た/true, べて/null/true, ください/null/false](base/ruby/emphasis)를 내고, 짝이 안 맞는 '[['는 예외 없이 원문 그대로(emphasis false) 낸다", "Robolectric 테스트: 두 DB 열기가 끝나기 전(AppState.Opening)에는 NavHost가 없고 StatusScreen 로딩이 그려지며, 300 ms 전에는 문구가 없고 뒤에는 '학습 콘텐츠를 준비하고 있습니다'가 보인다(가짜 시계·지연시킨 열기)", "Robolectric 테스트: AppState.ContentDbFailed에서 S90의 '출처 보기'를 누르면 About 라우트로 이동한다(같은 NavHost, 탭 목적지는 그리지 않음)", "단위 테스트: Furigana.fromHeadword가 食べる/たべる → [食/た, べる/null], お茶/おちゃ → [お/null, 茶/ちゃ], 今日/きょう → [今日/きょう], 引き出し/ひきだし → [引き出/ひきだ, し/null]", "단위 테스트(architecture.md 4.4 문자 집합): JpSpans.annotate('〜さん보다 정중한 호칭')의 ja-JP 구간이 '〜さん' 하나, '〜て형 뒤에 붙는다'는 '〜て' 하나, '「食べる」의 정중형'은 '「食べる」' 하나, '食べる의 활용'은 '食べる' 하나, 'コーヒー・紅茶 중에서'는 'コーヒー・紅茶' 하나, 『』로 묶인 구간은 괄호 포함 하나, 한국어만 있는 문장은 구간 없음", "Robolectric 테스트: 콘텐츠 에셋(assets/content/content.db)이 없으면 앱이 죽지 않고 AppState.ContentDbFailed가 되어 S90 콘텐츠 상태('다시 시도'·'앱 정보 열기'·'출처 보기')가 그려진다. 학습 기록 DB 열기 실패를 흉내 내면 AppState.RecordDbFailed이고 기록 삭제 버튼이 없다", "병합된 매니페스트(./gradlew :app:processDebugMainManifest 산출물)에 android.permission.INTERNET 이 없고, allowBackup=true와 dataExtractionRules·fullBackupContent가 있으며, 두 규칙 파일은 database 도메인의 study.db·study.db-wal·study.db-shm만 include하고 content_ 로 시작하는 경로를 include하지 않는다", "Roborazzi 스크린샷(ui-design 12절 이름, 각 _light·_dark): nav_shell_home, nav_shell_home_light_font200(탭 라벨 줄바꿈·잘림 없음), StatusScreen 로딩·빈·오류, app_opening(300 ms 뒤 문구), error_content_db, error_record_db", "실기기 확인(unverified): 한국어 기기 설정에서 일본어 텍스트(直, 骨, 角)가 일본식 자형으로 그려진다(실패하면 AR-U05 C에 따라 글꼴 번들 마일스톤을 추가)"], "status": "todo", "screenshots": ["screenshots/app_opening_dark.png", "screenshots/app_opening_light.png", "screenshots/error_content_db_dark.png", "screenshots/error_content_db_dark_font200.png", "screenshots/error_content_db_light.png", "screenshots/error_content_db_light_font200.png", "screenshots/error_record_db_dark.png", "screenshots/error_record_db_light.png", "screenshots/jp_display_sample_dark.png", "screenshots/jp_display_sample_light.png", "screenshots/nav_shell_home_dark.png", "screenshots/nav_shell_home_dark_font200.png", "screenshots/nav_shell_home_light.png", "screenshots/nav_shell_home_light_font200.png", "screenshots/status_empty_dark.png", "screenshots/status_empty_light.png", "screenshots/status_error_dark.png", "screenshots/status_error_light.png", "screenshots/status_loading_dark.png", "screenshots/status_loading_light.png"]}], "maxFixRounds": 2, "reviewOnly": true}
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
  const scope = reviewOnly ? '리뷰 전용 실행이다. 이 마일스톤 범위의 현재 산출물 전체를 검토한다.' : `변경 파일: ${changed.join(', ') || '(보고 없음)'}`
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
  const reviewers = reviewersFor(m, changed, shots)
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
  if (reviewOnly) {
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
      lows = fresh.filter(f => f.severity === 'low')   // low는 검증하지 않고 보고만 한다(비용)
      const freshJ = fresh.filter(f => f.severity !== 'low').concat(accFails)
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
