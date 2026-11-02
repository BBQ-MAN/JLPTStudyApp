export const meta = {
  name: 'jlpt-license-verify',
  description: 'JLPT 앱 콘텐츠 출처·라이선스 기록을 작성하고, 작성자와 분리된 추출자가 뽑은 주장마다 원 출처와 라이선스 전문으로 교차 검증한다',
  phases: [
    { title: '기록 작성', detail: 'content-licensing-analyst가 원 출처를 열어 sources.md와 manifest.json을 만든다' },
    { title: '주장 추출', detail: '작성자가 아닌 에이전트가 기록 전체에서 검증할 주장을 빠짐없이 뽑는다' },
    { title: '주장 검증', detail: '주장마다 서로 다른 관점의 검증자 2명이 원 출처로 반박을 시도한다' },
    { title: '반영', detail: '검증 결과를 확정·미확정 표시에 반영하고 스크립트가 대조한다' },
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

// args: { feedback?: string, candidates?: string[] }  — candidates: 이번에 새로 조사할 자료 이름(없으면 스킬 2절 후보 전체)
const A = args || {}
const SOURCES = 'docs/content/sources.md'
const MANIFEST = 'docs/content/manifest.json'
const SKILL = '.claude/skills/content-licensing/SKILL.md'
const extra = [
  A.feedback ? `사용자 피드백: ${A.feedback}` : '',
  (A.candidates || []).length ? `이번에 조사할 자료: ${A.candidates.join(', ')}` : '',
].filter(Boolean).join('\n')

const CLAIMS = {
  type: 'object', required: ['claims'],
  properties: { claims: { type: 'array', items: { type: 'object', required: ['id', 'statement', 'sourceRef'], properties: {
    id: { type: 'string' }, statement: { type: 'string' }, sourceRef: { type: 'string' } } } } },
}
const VERDICT = {
  type: 'object', required: ['status', 'reason'],
  properties: { status: { type: 'string', enum: ['confirmed', 'refuted', 'uncertain'] }, reason: { type: 'string' } },
}
const MARKED = {
  type: 'object', required: ['marked', 'untracked'],
  properties: {
    marked: { type: 'array', items: { type: 'object', required: ['id', 'status'], properties: {
      id: { type: 'string' }, status: { type: 'string', enum: ['확정', '미확정'] } } } },
    untracked: { type: 'array', items: { type: 'string' } },
  },
}

phase('기록 작성')
const draft = await call(
  `${SKILL} 를 읽고 따른다. docs/plan.md 의 콘텐츠 요구를 함께 읽는다.
${extra}
후보 자료마다 원 출처(배포 페이지, 라이선스 파일, 라이선스 법률 전문)를 WebFetch로 직접 열어 ${SOURCES} 와 ${MANIFEST} 를 작성하거나 갱신한다.
docs/content/decisions.md 의 사용자 결정(D-01 콘텐츠 DB CC BY-SA, D-02 JMdict 영어 뜻 번역 허용)을 전제로 쓰고, 적용할 CC BY-SA 버전과 자료별 호환 근거를 sources.md 표의 행으로 적어 검증받는다. 미정 항목(D-03 이후)은 확정하지 않는다.
끝나면 바뀐 절과 열지 못한 출처를 한 문단으로 요약해 반환한다.`,
  { agentType: 'content-licensing-analyst', phase: '기록 작성' })
if (!draft) return { error: '기록 작성 에이전트가 결과를 반환하지 않았다.' }

// 작성자가 주장을 고르면 뽑지 않은 내용이 검증 없이 확정처럼 남는다. 그래서 추출은 다른 에이전트가 한다.
phase('주장 추출')
const extracted = await call(
  `${SOURCES} 와 ${MANIFEST} 를 읽고 검증할 주장을 빠짐없이 뽑아라. 파일을 수정하지 않는다.
- "0. 상태 요약" 표의 모든 행(판, 라이선스, 상태)
- 자료별 표의 모든 행(라이선스와 버전, 저작자 표시 문구·요구, 동일조건변경허락 의무, 쓰는 필드, 쓰지 않는 부분과 이유, 판·내려받기 URL)
- 매니페스트 항목마다 license·licenseUrl·shareAlike·downloadUrl·status 가 sources.md 와 같다는 주장
- "JLPT 공식 목록 없음", 음성 제외처럼 문서가 사실로 적은 고지
한 주장에는 사실 하나만 담는다. id는 고유해야 한다. 표의 ID가 있으면 그대로 쓰고(예: S-01.license), 매니페스트 대조는 "M:<id>.<필드>"로 쓴다.
표의 한 행이 주장 여러 개로 나뉘면 반영 단계는 그 주장이 모두 confirmed일 때만 행을 확정으로 둔다.
sourceRef에는 문서가 적은 근거 URL과 인용 문장을 옮기고 근거가 없으면 "없음"으로 둔다.`,
  { label: '주장 추출', phase: '주장 추출', schema: CLAIMS })
if (!extracted || !extracted.claims.length) return { error: '추출된 주장이 0건이다. 기록이 비었거나 추출에 실패했다.', draft }
// 반영 대조가 id로 이뤄지므로 id가 겹치면 뒤쪽에 번호를 붙여 고유하게 만든다.
const seenIds = {}
const claims = extracted.claims.map(c => {
  seenIds[c.id] = (seenIds[c.id] || 0) + 1
  return seenIds[c.id] > 1 ? Object.assign({}, c, { id: `${c.id}#${seenIds[c.id]}` }) : c
})
const dupIds = Object.keys(seenIds).filter(k => seenIds[k] > 1)
log(`주장 ${claims.length}건 추출${dupIds.length ? ` (중복 id ${dupIds.length}개에 번호를 붙임: ${dupIds.join(', ')})` : ''}`)

phase('주장 검증')
const LENSES = [
  '자료 배포처의 원 페이지·저장소 라이선스 파일(sourceRef의 URL, 없으면 자료의 공식 홈페이지)을 WebFetch로 직접 열어 문장 단위로 대조하는',
  '라이선스 법률 전문(예: creativecommons.org 의 legalcode)과 자료가 밝힌 라이선스 표기를 직접 열어, 버전·의무(저작자 표시, 동일조건변경허락, 변경 표시)·URL이 실제와 맞는지 대조하는',
]
const judged = await parallel(claims.map(c => () =>
  parallel(LENSES.map((lens, i) => () => call(
    `다음 콘텐츠 출처·라이선스 주장을 ${lens} 방식으로 검증하라. 반박하려는 자세로 본다. 파일을 수정하지 않는다. 기억이나 2차 글로 판정하지 않는다.
주장: ${JSON.stringify(c)}
기록: ${SOURCES}, ${MANIFEST}
원 출처가 주장을 직접 뒷받침하면 confirmed, 모순되면 refuted. 페이지를 열지 못했거나 원 출처가 침묵하거나 해석이 둘 이상이면 uncertain. 확신이 없으면 uncertain을 고른다. reason에 연 URL과 근거 문장을 적는다.`,
    { label: `검증:${c.id}#${i}`, phase: '주장 검증', schema: VERDICT, effort: 'high' })))
    .then(vs => {
      const ok = vs.filter(Boolean)
      const status = ok.length === LENSES.length && ok.every(v => v.status === 'confirmed') ? 'confirmed'
        : ok.some(v => v.status === 'refuted') ? 'refuted' : 'unconfirmed'
      return Object.assign({}, c, { status, verdicts: ok })
    })))
const results = judged.filter(Boolean)
const partial = results.filter(r => r.verdicts.length < LENSES.length).length + (claims.length - results.length)
if (partial) log(`검증자 결과가 빠진 주장 ${partial}건 — 확정되지 않는다`)
const by = s => results.filter(r => r.status === s)
log(`확정 ${by('confirmed').length} / 미확정 ${by('unconfirmed').length} / 반박 ${by('refuted').length}`)

phase('반영')
const applied = await call(
  `${SKILL} 를 따른다. 검증 결과를 ${SOURCES} 와 ${MANIFEST} 에 반영하라.
- confirmed: 확정
- unconfirmed: 미확정 + 확인할 방법
- refuted: 검증 근거로 기록을 고치고, 고친 행은 "미확정(수정됨, 재검증 필요)"으로 둔다. 다음 실행에서 다시 검증된다
- 아래 결과 목록에 없는데 확정으로 표시된 행은 미확정으로 바꾸고 untracked에 적는다
- 매니페스트 status는 그 자료의 핵심 행(라이선스, 저작자 표시, 동일조건변경허락, 판·URL)이 모두 확정일 때만 confirmed로 둔다
반환: 결과 목록의 id마다 반영 후 상태(marked), 그리고 untracked.
결과: ${JSON.stringify(results.map(r => ({ id: r.id, statement: r.statement, status: r.status, reasons: r.verdicts.map(v => v.reason) })))}`,
  { agentType: 'content-licensing-analyst', phase: '반영', schema: MARKED })
if (!applied) return { error: '반영 에이전트가 결과를 반환하지 않았다. 기록의 확정 표시를 믿지 말고 다시 실행한다.', results }

const markOf = id => (applied.marked.find(x => x.id === id) || {}).status
const mismatches = results.filter(r => (r.status === 'confirmed') !== (markOf(r.id) === '확정'))
  .map(r => ({ id: r.id, verdict: r.status, marked: markOf(r.id) || '(없음)' }))
if (mismatches.length) log(`반영 불일치 ${mismatches.length}건 — 반환값 mismatches 확인`)

return {
  sourcesPath: SOURCES, manifestPath: MANIFEST,
  confirmed: by('confirmed'), unconfirmed: by('unconfirmed'), refuted: by('refuted'),
  partialVerdicts: partial, mismatches, untracked: applied.untracked, draftSummary: draft,
}
