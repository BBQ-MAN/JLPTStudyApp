# 사용: python3 -I make_review_quizgen_20261108.py <quizgen worktree> <출력 html>
# 한 파일(외부 리소스 없음, 스크린샷 인라인). 표본 예시는 _workspace/quizgen_sample.md에서 유형별 첫 문항을 읽는다.
import base64, html, os, re, sys

WT, OUT = sys.argv[1], sys.argv[2]
E = html.escape


def img(name, cap):
    p = os.path.join(WT, 'screenshots', name + '.png')
    b = base64.b64encode(open(p, 'rb').read()).decode()
    return f'<figure><img alt="{E(cap)}" src="data:image/png;base64,{b}"><figcaption>{E(cap)} <code>{name}.png</code></figcaption></figure>'


def table(head, rows):
    h = ''.join(f'<th>{E(c)}</th>' for c in head)
    r = ''.join('<tr>' + ''.join(f'<td>{c}</td>' for c in row) + '</tr>' for row in rows)
    return f'<table><thead><tr>{h}</tr></thead><tbody>{r}</tbody></table>'


sample = open(os.path.join(WT, '_workspace/quizgen_sample.md'), encoding='utf-8').read()
blocks = re.split(r'\n#### ', sample)[1:]


def example(prefix):
    """N5 해당 유형의 첫 문항 -> (지문, 보기[(글, 정답, 이유)])"""
    for b in blocks:
        if b.startswith(prefix + '\n'):
            stem = re.search(r'\| 지시문 \| (.*?) \|', b).group(1)
            word = re.search(r'\| 지문 \| (.*?) \|', b).group(1)
            rows = re.findall(r'\| (\d) \| (.*?) \| (✔ 정답)? ?\| (.*?) \| (.*?) \|', b)
            return stem, word, rows
    raise SystemExit('no sample ' + prefix)


def ex_html(title, prefix, rules):
    stem, word, rows = example(prefix)
    t = table(['보기', '정답', '오답 이유'], [[E(r[1]), '✔' if r[2] else '', E(r[4])] for r in rows])
    return f'<h3>{E(title)}</h3><p class="q"><b>지시문</b> {E(stem)}<br><b>지문</b> <span class="jp">{E(word)}</span></p>{t}<p><b>오답 배제 규칙</b></p>{rules}'


decisions = [
    ('Q-01', '배제 규칙용 데이터 배치', 'A 파이프라인이 전체 JMdict에서 <code>jm_form</code>(표기·읽기 쌍)·<code>vocab_conflict</code>(뜻 충돌)를 만들어 콘텐츠 DB v3에 번들', '앱 크기 +0.82 MiB. 대안 B(DB 안 데이터만)는 목록 밖 동음어(はし→端)를 못 막아 기각'),
    ('Q-02', '생성 로직 모듈', 'A <code>:core:quiz</code>의 <code>gen</code> 패키지', '새 모듈은 빌드 파일만 늘려서 안 만듦'),
    ('Q-03', '<code>sampled</code> 뜻을 검수된 것으로 볼지', 'A 보지 않음(<code>reviewed</code>만)', '<b>영향 큼:</b> N4 뜻 문제는 63어(생성 52)뿐. N4 뜻을 전수 검수하면 늘어남'),
    ('Q-04', '④ 문맥 빈칸', 'A 만들지 않음(결정만 기록)', '예문 자료(S-04) 미확정, 품사·활용 판정 도구 없음. 다시 열 조건은 decisions.md'),
    ('Q-05', '결과와 학습 기록 연결', 'A 해설 "관련 단어 보기"로 단어 상세만 연결, SRS 기록은 안 씀', '4지선다 정오를 FSRS 평가로 바꾸면 기억 모델이 오염됨. "틀린 단어 카드 복습"(C안)은 원하면 별도 마일스톤'),
    ('Q-06', '정답 위치', 'A 세션 단위 균등 배치 + <code>fixedOrder</code>', '10문항이면 한 자리 최대 3개(30%)'),
    ('Q-07', '생성 문항 저장', 'A ID만 저장하고 열 때 다시 생성', '학습 기록 DB 변경 없음. 콘텐츠 DB 판이 바뀌면 지난 시도의 오답 글자가 달라질 수 있음(정오는 그대로)'),
    ('Q-08~10', '전수 검사 위치, 대상 어휘(<code>jm:</code>만, 같은 급수), JMdict 사용 필드 확장', '각각 A', 'Q-10은 출시 전 analyst의 매니페스트 갱신이 필요(아래)'),
]
pending = [
    ('출시 전 게이트(high)', 'S-01 <code>usedFields</code>에 <code>sense/xref</code>, <code>modifications</code> 문구, <code>attribution.json</code> 재생성이 안 됨. 앱 저작자 표시(S09)의 변경 표시가 사실과 다르므로 <b>이 갱신 전에는 출시하면 안 됨</b>. analyst 요청으로 보고서에 있음'),
    ('스냅샷 커밋', '<code>content/snapshot/S-01-2026-11-01-related.jsonl.gz</code>(18,336행, 0.28 MB)를 재현 빌드를 위해 <b>dev/quizgen 로컬에만</b> 커밋했다. 공개 저장소 반영 여부는 D-08 기준 사용자 확인 필요'),
    ('원어민 표본 검수', '<code>_workspace/quizgen_sample.md</code>(N5·N4 × 4유형 × 30문항 = 240). 규칙으로 못 막는 경우(JMdict에 없는 읽기, 이체자, 겹침 없는 유의어)는 이것으로만 잡힌다'),
    ('병합', 'dev/quizgen → dev/app 병합은 하지 않았다. 사용자 지시 대기'),
]

body = []
body.append('<h1>JLPTStudyApp — 규칙 기반 자동 생성 연습 문제 (2026-11-08)</h1>')
body.append('<p class="lead">LLM·네트워크 없이 콘텐츠 DB의 검증된 데이터만으로 문제를 만드는 기능을 <code>dev/quizgen</code>에 구현했다. 유형은 한자 읽기·표기·뜻 고르기(일→한)·단어 고르기(한→일) 4가지다. <b>정답이 둘이 되는 문항은 전수 검사와 독립 점검 모두에서 0건</b>이었다. main 커밋·병합·push는 하지 않았다.</p>')

body.append('<h2>1. 결정 대기(잠정 채택) 목록</h2>')
body.append('<p>하네스 추천안을 잠정 채택해 진행했다. 바꾸려면 ID와 고를 선택지를 알려 주면 된다. 상세는 <code>docs/content/decisions.md</code> Q-01~Q-10.</p>')
body.append(table(['ID', '결정', '채택안(잠정)', '영향·비고'], [[a, b, c, d] for a, b, c, d in decisions]))
body.append('<h3>그 밖의 대기 항목</h3>')
body.append(table(['항목', '내용'], [[a, b] for a, b in pending]))

body.append('<h2>2. 유형별 예시 문제와 배제 규칙</h2>')
body.append('<p>아래 예시는 표본 파일의 N5 첫 문항이다. 보기 4개는 모두 달라야 하고, 정답은 정확히 1개여야 한다.</p>')
body.append(ex_html('① 한자 읽기', 'N5 ① · 01',
    '<ul><li>그 단어의 JMdict 항목에 있는 <b>모든 읽기</b>(今日 きょう/こんにち)</li><li><b>같은 표기를 가진 다른 항목</b>의 읽기(上手 じょうず/うわて) — 목록 밖 항목은 related 스냅샷으로 포함</li><li>변형 결과가 우연히 실제 읽기가 된 경우(日本 にほん→にっぽん)도 같은 검사로 제외</li></ul>'))
body.append(ex_html('② 표기', 'N5 ② · 01',
    '<ul><li><b>같은 읽기를 가진 모든 항목</b>의 표기(はし → 橋·箸·端)</li><li>그 단어의 <b>이표기</b>(会う/逢う)와 한자 치환 결과가 실재 표기인 경우(会う→合う)</li><li>치환 한자는 급수 어휘에 쓰인 상용 한자로만 제한(이체자 방지)</li></ul>'))
body.append(ex_html('③-1 뜻 고르기(일→한)', 'N5 ③-1 · 01',
    '<ul><li>한국어 뜻이 같거나 <b>포함 관계</b>(本 "책"/本棚 "책장")인 단어</li><li><b>영어 gloss 단어가 겹치는</b> 단어(父 "아버지"/パパ "아빠" — father)</li><li>JMdict <b>상호참조(xref)</b>로 이어진 동의어(양방향)</li><li>다의어는 모든 뜻 행을 비교, 같은 품사군·같은 급수, <code>reviewed</code> 뜻만 사용</li></ul>'))
body.append(ex_html('③-2 단어 고르기(한→일)', 'N5 ③-2 · 01', '<p>③-1과 같은 규칙. 보이는 글자가 같은 동형이의어(方 かた/ほう)도 함께 제외.</p>'))
body.append('<p>④ 문맥 빈칸은 <b>만들지 않았다</b>(Q-04). 예문 자료가 없고, 다른 품사만 오답으로 쓰면 품사만 보고 풀리기 때문이다.</p>')
body.append('<p class="note"><b>품질 메모:</b> ①의 일부 오답(<code>ううしろ</code>, <code>ほうんだな</code>)은 실제로 없는 읽기라 눈에 띈다. 정답이 둘이 되는 문제는 아니고 지시한 변형 규칙(장음·탁음·촉음·요음)대로지만, 학습 효과를 위해 "실재할 법한 변형만" 쓰도록 조일지 결정할 수 있다.</p>')

body.append('<h2>3. 검증 수치</h2>')
body.append(table(['검증', '결과'], [
    ['테스트', '앱 576(실패 0, 건너뜀 2: 표본 생성·지연 측정은 환경 변수가 있을 때만), <code>:core:quiz</code> 70(신규 55), 파이프라인 185(신규 20 포함) = <b>831</b>'],
    ['<code>verify.sh</code>', 'PASS (check.sh + test + lintDebug + assembleDebug), <code>verifyRoborazziDebug</code> PASS'],
    ['전수 검사(번들 DB, 오라클은 생성기를 부르지 않고 원시 SQL 조회)', 'N5·N4 × 4유형 × 시드 8 + 세션 시드 200 = <b>27,120문항, 불변식 I1~I10 위반 0</b>. 배제 데이터를 일부러 망가뜨리면 위반 4,752건이 나와 검사기가 실제로 작동함을 확인'],
    ['독립 점검(QA)', '원본 JMdict_e.gz를 직접 파싱해 <code>jm_form</code> 4,498행과 <code>vocab_conflict</code> 16,813쌍을 다시 계산 → 번들 DB와 <b>정확히 일치</b>. 표본 ①②③ 240문항 중 정답 둘 0건(음성 대조군은 잡힘)'],
    ['정답 위치 분포', '세션 단위 균등 배치: 10문항에서 한 자리 최대 3개(30% < 40%), 시드 1..500 속성 테스트'],
    ['콘텐츠 DB', '2.55 MB(+0.82 MiB), 예산 12 MiB 이내. 같은 입력으로 두 번 빌드한 해시 동일, 기존 스냅샷·스키마 1·2.json 바이트 불변, 학습 기록 DB 변경 없음'],
]))
body.append('<h3>유형별 생성 가능 단어 수 (건너뜀은 규칙을 느슨하게 하지 않고 제외한 수)</h3>')
body.append(table(['급수', '유형', '단어', '대상', '생성 가능', '건너뜀 사유'], [
    ['N5', '① 한자 읽기', '667', '502', '<b>502 (100%)</b>', '표기 없는 가나 단어 165(대상 아님)'],
    ['N5', '② 표기', '667', '502', '<b>502 (100%)</b>', '위와 같음'],
    ['N5', '③-1 일→한', '667', '667', '<b>650 (97.4%)</b>', '오답 후보 부족 17(접속사·감탄사 등 작은 품사군)'],
    ['N5', '③-2 한→일', '667', '667', '<b>650 (97.4%)</b>', '위와 같음'],
    ['N4', '①②', '630', '491', '491', '대상 아님 139(표기 없음)'],
    ['N4', '③ 두 방향', '630', '63', '52', '미검수 뜻 567(<code>sampled</code>, 사용 안 함), 오답 부족 11'],
]))
body.append('<p>설계 하한(N5 ① 90%·② 80%·③ 60%)을 모두 넘었다. N4 뜻 문제는 검수된 뜻이 63어뿐이라 작고, 같은 오답이 반복된다(예: "그치다, 멈추다"가 30문항 중 6번). N4 뜻을 전수 검수(<code>review-sample</code> 도구)하면 해소된다.</p>')

body.append('<h2>4. QA 지적 (직접 처리한 것과 남은 것)</h2>')
body.append(table(['심각도', '내용', '처리'], [
    ['high', '저작자 표시의 변경 표시(S-01 usedFields·modifications) 미갱신', '출시 게이트. analyst 몫으로 남김(위 대기 항목)'],
    ['low', '문서 간 이름 불일치(architecture v4 문구, 존재하지 않는 ui-design 6.7 참조, QG3 acceptance 문구)', '수정함'],
    ['low', 'S12 "단어 n개"는 생성 가능 수가 아니라 대상 수(③ 667 vs 650). 대상 ≥10이어도 생성 0이면 행이 켜지는 이론상 경우가 있음', '현재 데이터에서는 발생 안 함. 남김'],
    ['low', '③ 오답끼리 서로 비슷한 뜻이 나올 수 있음(예: "훌륭하다, 멋지다"/"훌륭하다, 좋다")', '정답은 하나. 소거법이 쉬워짐. 남김'],
    ['low', 'むずかしい→むずかし 처럼 어미 い 삭제를 "장음" 이유로 표시', '콘텐츠 리뷰어(원어민) 확인 필요'],
]))

body.append('<h2>5. 스크린샷</h2>')
body.append('<div class="gallery">' + ''.join(img(n, c) for n, c in [
    ('practice_home_generated_light', '연습 홈 — 자동 생성 연습 영역(라이트)'),
    ('practice_home_generated_dark', '연습 홈(다크)'),
    ('practice_home_generated_light_font200', '연습 홈 — 글꼴 200%'),
    ('quiz_generated_kanji_reading_light', '한자 읽기 문제(라이트)'),
    ('quiz_generated_kanji_reading_dark', '한자 읽기 문제(다크)'),
    ('quiz_generated_meaning_ja_ko_light', '뜻 고르기 문제(라이트)'),
    ('quiz_generated_meaning_ja_ko_dark', '뜻 고르기 문제(다크)'),
    ('quiz_result_generated_light', '결과 화면(라이트)'),
    ('quiz_result_generated_dark', '결과 화면(다크)'),
]) + '</div>')

body.append('<h2>6. 실기기 체크리스트 추가분 (개발 종료 후 일괄)</h2>')
body.append('<ol><li>연습 홈 "자동 생성 연습"에서 4개 행이 보이고, 한자 읽기를 눌러 10문항이 시작되는가(첫 진입 지연이 거슬리지 않는가)</li>'
    '<li>비행기 모드에서 생성 연습이 동작하는가</li>'
    '<li>TalkBack: 문항마다 "자동 생성 연습 문제"를 지시문 앞에서 한 번 읽는가</li>'
    '<li>TalkBack: 뜻 고르기 선택지를 한국어 음성으로, 한자 읽기·표기 지문을 일본어 음성으로 읽는가</li>'
    '<li>TalkBack: 비활성 행을 "…, 사용할 수 없음, 이유"로 읽는가</li>'
    '<li>해설의 "관련 단어 보기"가 단어 상세로 가고, 뒤로 오면 포커스가 자연스러운가</li>'
    '<li>한국어 기기 설정에서 문제 지문의 일본어 한자가 일본식 글자 모양인가</li>'
    '<li>앱을 껐다 켠 뒤 지난 생성 시도 결과를 열면 같은 문항이 다시 보이는가</li></ol>')

css = '''body{font-family:-apple-system,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;max-width:1000px;margin:2rem auto;padding:0 1rem;line-height:1.55;color:#1b1b1f}
h1{font-size:1.6rem}h2{margin-top:2.2rem;border-bottom:2px solid #ddd;padding-bottom:.2rem}.lead{font-size:1.05rem;background:#f3f6fb;padding:.8rem 1rem;border-radius:8px}
table{border-collapse:collapse;width:100%;margin:.6rem 0;font-size:.92rem}th,td{border:1px solid #ccd;padding:.35rem .5rem;text-align:left;vertical-align:top}th{background:#eef1f7}
code{background:#f0f0f4;padding:0 .25rem;border-radius:3px;font-size:.88em}.gallery{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:1rem}
figure{margin:0}img{width:100%;border:1px solid #ccd;border-radius:6px}figcaption{font-size:.8rem;color:#555}.q{background:#fafafa;padding:.5rem .8rem;border-left:4px solid #6a8fd8}
.jp{font-size:1.3rem}.note{background:#fff8e6;padding:.6rem .8rem;border-radius:6px}'''
doc = f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>JLPTStudyApp 자동 생성 연습 문제</title><style>{css}</style></head><body>' + '\n'.join(body) + '</body></html>'
open(OUT, 'w', encoding='utf-8').write(doc)
print(OUT, len(doc))
