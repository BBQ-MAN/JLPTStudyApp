# 사용: python3 -I make_report.py  — docs/review/JLPTStudyApp-review-YYYYMMDD.html 한 파일(외부 리소스 없음)을 만든다.
# 입력: docs/content/{decisions.md,manifest.json}, docs/spec/milestones.json, 번들 콘텐츠 DB, content/ko-gloss/*.jsonl,
#       screenshots/*.png, _workspace/emu/*.png, _workspace/report/{summary,verification,reviews,shots}.json
import base64, datetime, glob, html, json, os, re, sqlite3

ROOT = '/Users/bbqman/HermesAgents/ETC/JLPTStudyApp'
R = lambda p: os.path.join(ROOT, p)
E = html.escape
today = datetime.date.today().strftime('%Y%m%d')
jload = lambda p, d=None: json.load(open(R(p))) if os.path.exists(R(p)) else d

summary = jload('_workspace/report/summary.json', {})
verification = jload('_workspace/report/verification.json', [])
reviews = jload('_workspace/report/reviews.json', [])
shots = jload('_workspace/report/shots.json', [])   # [{"path","caption"}]


def md_table(text, heading):
    """heading 다음에 나오는 첫 마크다운 표를 행 리스트로 돌려준다."""
    i = text.find(heading)
    if i < 0:
        return []
    rows = []
    for line in text[i:].splitlines()[1:]:
        if line.startswith('|'):
            cells = [c.strip() for c in line.strip().strip('|').split('|')]
            if not set(''.join(cells)) <= set('-: '):
                rows.append(cells)
        elif rows:
            break
    return rows


def md_inline(s):
    s = E(str(s))
    s = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', s)
    return re.sub(r'`(.+?)`', r'<code>\1</code>', s)


def table(head, rows):
    h = ''.join(f'<th>{E(c)}</th>' for c in head)
    b = ''.join('<tr>' + ''.join(f'<td>{c}</td>' for c in r) + '</tr>' for r in rows)
    return f'<table><thead><tr>{h}</tr></thead><tbody>{b}</tbody></table>'


def img(path, caption):
    p = R(path)
    if not os.path.exists(p):
        return f'<figure><figcaption>{E(caption)} (파일 없음: {E(path)})</figcaption></figure>'
    data = base64.b64encode(open(p, 'rb').read()).decode()
    return f'<figure><img src="data:image/png;base64,{data}" alt="{E(caption)}"><figcaption>{E(caption)}</figcaption></figure>'


# 1. 요약
sec1 = f"<p class='lead'>{E(summary.get('oneLine', ''))}</p>" + table(['항목', '내용'], [[E(k), md_inline(v)] for k, v in summary.get('status', {}).items()])

# 2. 결정 대기
dec = open(R('docs/content/decisions.md')).read()
drows = md_table(dec, '## 잠정 결정')
sec2 = '<p>모두 하네스 추천안으로 <b>잠정 채택</b>했다. 바꾸려면 ID와 고를 선택지를 알려 주면 된다.</p>' + table(
    ['ID', '결정', '선택지', '채택안(잠정)', '근거', '바꾸면 드는 비용'], [[md_inline(c) for c in r[:6]] for r in drows[1:] if len(r) >= 6])
extra = summary.get('extraDecisions', [])
if extra:
    sec2 += '<h3>이번 작업 중 추가로 잠정 채택한 판단</h3>' + table(['ID', '내용', '채택안', '근거', '비용'], [[md_inline(x) for x in r] for r in extra])

# 3. 콘텐츠·라이선스
man = jload('docs/content/manifest.json', [])
mrows = []
for m in man:
    url = m.get('homepage') or m.get('downloadUrl') or ''
    st = {'confirmed': '<span class="ok">확인</span>', 'excluded': '제외'}.get(m.get('status'), '<span class="warn">미확인</span>')
    cond = str(m.get('modifications') or m.get('attribution') or '-')
    mrows.append([E(m.get('id', '')), E(m.get('name', '')), f'<a href="{E(url)}">{E(url)}</a>' if url else '-',
                  E(str(m.get('version') or '-')), E(str(m.get('license') or '-')), E(cond[:400]), st])
sec3 = table(['ID', '자료', '출처 URL', '판', '라이선스', '조건·변경', '상태'], mrows)
dbs = sorted(glob.glob(R('app/src/main/assets/content/*.db')))
if dbs:
    con = sqlite3.connect(dbs[0])
    q = lambda s: con.execute(s).fetchall()
    meta = dict(q('select key, value from content_meta')) if q("select name from sqlite_master where name='content_meta'") else {}
    lv = lambda t: dict(q(f'select level, count(*) from {t} group by level'))
    v, k = lv('vocab'), lv('kanji')
    counts = [[f'N{L}', str(v.get(L, 0)), str(k.get(L, 0))] for L in sorted(set(v) | set(k), reverse=True)]
    counts.append(['합계', str(sum(v.values())), str(sum(k.values()))])
    sec3 += f"<h3>콘텐츠 수량 ({E(os.path.basename(dbs[0]))}, kind={E(str(meta.get('kind', '?')))})</h3>" + table(['급수', '단어', '한자'], counts)
gl = []
for f in sorted(glob.glob(R('content/ko-gloss/*.jsonl'))):
    rows = [json.loads(l) for l in open(f) if l.strip()]
    stat = {}
    for r in rows:
        key = f"{r.get('reviewStatus', '?')}/{r.get('reviewerKind') or '-'}"
        stat[key] = stat.get(key, 0) + 1
    gl.append([E(os.path.basename(f)), str(len(rows)), E(', '.join(f'{k}: {n}' for k, n in sorted(stat.items())))])
if gl:
    sec3 += '<h3>한국어 뜻 생성·검수 현황</h3>' + table(['파일', '행 수', '검수 상태/검수자'], gl)
sec3 += ''.join(f'<p>{md_inline(p)}</p>' for p in summary.get('glossNotes', []))

# 4. 앱
ms = jload('docs/spec/milestones.json', [])
srows = [[E(m['id']), E(m['title']), E(m.get('status', '')), E(m.get('note', ''))[:300]] for m in ms]
sec4 = '<h3>스크린샷</h3><div class="grid">' + ''.join(img(s['path'], s.get('caption', '')) for s in shots) + '</div>'
sec4 += '<h3>구현된 기능</h3><ul>' + ''.join(f'<li>{md_inline(x)}</li>' for x in summary.get('implemented', [])) + '</ul>'
sec4 += '<h3>구현하지 않은 기능</h3><ul>' + ''.join(f'<li>{md_inline(x)}</li>' for x in summary.get('notImplemented', [])) + '</ul>'
sec4 += '<h3>마일스톤 상태</h3>' + table(['ID', '제목', '상태', '메모'], srows)

# 5. 검증
sec5 = table(['명령·검증', '결과', '비고'], [[md_inline(v['cmd']), md_inline(v['result']), md_inline(v.get('note', ''))] for v in verification])
sec5 += '<h3>디자인 검증·QA 지적과 처리</h3>' + table(['단계', '지적', '처리', '남은 것'], [[md_inline(r['stage']), md_inline(r['found']), md_inline(r['handled']), md_inline(r.get('left', ''))] for r in reviews])
sec5 += '<h3>알려진 문제</h3><ul>' + ''.join(f'<li>{md_inline(x)}</li>' for x in summary.get('knownIssues', [])) + '</ul>'

# 6. 다음 단계
sec6 = '<ol>' + ''.join(f'<li>{md_inline(x)}</li>' for x in summary.get('next', [])) + '</ol>'

css = """body{font-family:-apple-system,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;max-width:1180px;margin:24px auto;padding:0 16px;color:#1b1c1f;background:#fbf9f4;line-height:1.55}
h1{font-size:26px}h2{border-bottom:2px solid #2f4a7a;padding-bottom:4px;margin-top:40px}h3{margin-top:24px}
table{border-collapse:collapse;width:100%;font-size:13px;margin:8px 0;background:#fff}th,td{border:1px solid #ddd;padding:6px;vertical-align:top;text-align:left}th{background:#eef1f8}
.lead{font-size:17px;font-weight:600}.ok{color:#1b6b3a;font-weight:700}.warn{color:#9a4b00;font-weight:700}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px}figure{margin:0;background:#fff;border:1px solid #ddd;padding:6px}figure img{width:100%;height:auto}
figcaption{font-size:12px;color:#46474f}code{background:#eee;padding:0 3px;border-radius:3px}nav a{margin-right:12px}"""
body = f"""<h1>JLPT 학습 앱 — 사용자 검토 보고서 ({datetime.date.today().isoformat()})</h1>
<nav><a href="#s1">1 요약</a><a href="#s2">2 결정 대기</a><a href="#s3">3 콘텐츠·라이선스</a><a href="#s4">4 앱</a><a href="#s5">5 검증</a><a href="#s6">6 다음 단계</a></nav>
<h2 id="s1">1. 한 줄 요약과 현재 상태</h2>{sec1}
<h2 id="s2">2. 사용자 최종 결정 대기 목록</h2>{sec2}
<h2 id="s3">3. 콘텐츠·라이선스</h2>{sec3}
<h2 id="s4">4. 앱</h2>{sec4}
<h2 id="s5">5. 검증 결과</h2>{sec5}
<h2 id="s6">6. 다음 단계 제안</h2>{sec6}
<p style="color:#777;font-size:12px">이 보고서는 근거 기록이며 법률 자문이 아니다. 생성: _workspace/tools/make_report.py</p>"""
page = f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JLPTStudyApp 검토 보고서</title><style>{css}</style></head><body>{body}</body></html>'
# 자체 포함 확인: 외부 스크립트·스타일·이미지를 불러오는 태그가 없어야 한다(문서 링크 <a href>는 허용).
assert not re.search(r'<(script|link)\b|<img [^>]*src="(?!data:)', page), '외부 리소스 참조'
out = R(f'docs/review/JLPTStudyApp-review-{today}.html')
os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, 'w').write(page)
print(out, os.path.getsize(out))
