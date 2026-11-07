# 사용: python3 -I make_review_20261107.py — docs/review/JLPTStudyApp-review-20261107.html 한 파일(외부 리소스 없음, 이미지 인라인)을 만든다.
# 출력은 메인 체크아웃(커밋하지 않음). 스크린샷은 dev/app worktree의 screenshots/에서 읽는다.
import base64, html, os

OUT = '/Users/bbqman/HermesAgents/ETC/JLPTStudyApp/docs/review/JLPTStudyApp-review-20261107.html'
SHOTS = '/Users/bbqman/HermesAgents/ETC/JLPTStudyApp-wt/app/screenshots'
E = html.escape


def img(name, cap):
    p = os.path.join(SHOTS, name + '.png')
    if not os.path.exists(p):
        return ''
    b = base64.b64encode(open(p, 'rb').read()).decode()
    return f'<figure><img alt="{E(cap)}" src="data:image/png;base64,{b}"><figcaption>{E(cap)} <code>{name}.png</code></figcaption></figure>'


def table(head, rows):
    h = ''.join(f'<th>{E(c)}</th>' for c in head)
    r = ''.join('<tr>' + ''.join(f'<td>{c}</td>' for c in row) + '</tr>' for row in rows)
    return f'<table><thead><tr>{h}</tr></thead><tbody>{r}</tbody></table>'


def gallery(items):
    return '<div class="gallery">' + ''.join(img(n, c) for n, c in items) + '</div>'


decisions = [
    ('U-1', '콘텐츠 DB 스키마를 한 번에 v2로', 'A 한 번에', '실행 완료(T05). 기능 갈래가 서로 다른 파일만 건드려 병렬로 돌릴 수 있었음'),
    ('U-2', 'KanjiVG(S-03) 확정 방법', 'A 핵심 행 재검증', '실행 완료(S-03 confirmed). 기준 문서 선택(COPYING 기준)은 여전히 잠정'),
    ('U-3', '문법 급수 근거', 'A 작성자 판단 + "공식 아님"', '실행 완료(문법 24개). 급수 판단은 작성자 몫이라 표본 확인을 권함'),
    ('U-4', '직접 쓴 콘텐츠·N4 뜻의 검수', 'A 에이전트 검수 + 표본 도구', '실행 완료. 사람 검수는 원할 때 <code>review-sample</code>로(reviewerKind=human)'),
    ('U-5', '묶음 우선순위', 'A 문법→문제→청해, N4·필순 병렬', '실행 완료'),
    ('U-6', '갈래별 로컬 브랜치 커밋', 'A', '실행 완료. main 커밋·push 없음'),
    ('U-7', '실기기 확인은 개발 종료 후 한 번에', '사용자 변경안', '4절 체크리스트로 준비됨'),
    ('U-8', '모의고사 템플릿 문항 수', 'A 연습용 값 + <code>countNote</code>', '<b>새 결정.</b> jlpt.jp에 문항 수가 없어 앱이 정한 값을 채우고 "공식 값 아님"을 표시. 값은 문항 풀 이하. 공식 값을 알려 주시면 N5.json만 바꾸면 됨'),
    ('U-9', '필순 그림 수와 획수가 다른 한자(飴·噌)', 'A 그림(paths) 개수를 "{n}획"으로', '<b>새 결정.</b> T09에서 앱에 반영·테스트됨'),
    ('U-10', 'EDRDG 새 판(2026-11-06) 반영', 'A 지금은 반영 보류', '<b>새 결정.</b> 새 판은 사라진 ID 0·새 ID 0. 반영하려면 analyst의 매니페스트 sha256 갱신과 스냅샷 커밋 승인이 필요'),
    ('AR-U11~U15, UI-U07~U13', '설계 검증에서 나온 결정(청해 화면, 모의고사 시간 멈춤 등)', '각각 A', '구현됨. 표는 <code>docs/content/decisions.md</code>'),
    ('D-04', '청해는 기기 TTS', 'A', '구현됨. <b>가장 큰 미확인</b>: 실기기에서 일본어 음성이 실제로 나오는지는 4절에서 확인'),
]

pending = [
    ('병합 전 사용자 실행', '<code>/pstack:blast-radius</code> — dev/base 병합 전에 사용자가 직접 실행(Skill로 부를 수 없음). 대기 중'),
    ('analyst 요청 대기', '매니페스트 S-01·S-02 version·sha256 갱신(T10 보고서 6절). U-10 A라서 서두르지 않음'),
    ('공개 출시 결정(T11·T13)', 'G-S12(아이콘 권리, <code>needs-decision</code>), D-05(LICENSE), AR-U07(DB 공개 위치), 서명 키. 개발은 막지 않으나 공개 출시 전 필요'),
]

body = []
body.append('<h1>JLPTStudyApp 개발 재개 결과 (2026-11-07)</h1>')
body.append('<p class="lead">2026-11-03에 중단된 MVP 이후 개발을 이어서 끝냈다. 남은 일은 <b>실기기 확인</b>과 <b>사용자 결정 검토</b>다. 아래 수치는 모두 이 세션에서 직접 다시 돌려 확인한 값이다. main에는 커밋·병합하지 않았고 push도 하지 않았다.</p>')

body.append('<h2>1. 결정 대기(잠정 채택) 목록</h2>')
body.append('<p>하네스 추천안을 잠정 채택해 진행했다. 바꾸려면 ID와 고를 선택지를 알려 주면 된다. 상세·되돌리는 비용은 <code>docs/content/decisions.md</code>.</p>')
body.append(table(['ID', '결정', '채택안(잠정)', '상태'], [[a, b, c, d] for a, b, c, d in decisions]))
body.append('<h3>그 밖의 대기 항목</h3>')
body.append(table(['항목', '내용'], [[a, b] for a, b in pending]))

body.append('<h2>2. 갈래별 결과와 검증 수치</h2>')
body.append(table(['갈래', '브랜치(최종 커밋)', '한 일', '검증'], [
    ['기반', 'dev/base <code>8dffbf5</code>', '설계 확정(T04), 스키마 v2 일괄(T05)·기록 DB 마이그레이션(T06), 워크플로 사본 sonnet 고정, 마일스톤 상태·결정 U-8~U-10 기록', 'blast-radius <b>사용자 실행 대기</b>'],
    ['콘텐츠', 'dev/content <code>d28613d</code>', '검수 도구(T07), 문법·문항·청해 검사기(M15·M20·M24), <b>N5 문법 24개(예문 85)·문항 62개·모의고사 템플릿·청해 대본 17개</b>(M16·M21·M25), N4 뜻 630행과 표본 검수 63행(오류 1, 1.6%)(M26), KanjiVG 필순 727자(M13), EDRDG 재빌드 연습(T10)', 'check.sh PASS, 파이프라인 단위 테스트 165개 OK, verify.sh PASS'],
    ['앱', 'dev/app <code>e2d8143</code>(base·content 모두 포함)', '필순 표시(M12), 문법 목록·상세·카드(M14·M17), 채점 모듈(M18), 연습 데이터 계층·연습 홈·문제 풀이(M19a·b), 모의고사(M19c), 결과 화면(M19d), 청해 화면(M23), 접근성 보강(T14), 글꼴 200% 스크린샷(T12), 정리(T09)', 'verify.sh PASS, 앱 테스트 521·core:srs 33·core:quiz 15개 모두 통과, Roborazzi 검증 3회 연속 통과, 린트 경고 8→3, 스키마 JSON 변경 없음'],
]))
body.append('<h3>번들 콘텐츠 DB(<code>content.db</code> 1.69 MB, 예산 12 MiB)</h3>')
body.append(table(['테이블', '행 수'], [['vocab(N5 667 + N4 630)', '1,297'], ['kanji / kanji_stroke', '727 / 727'], ['grammar / grammar_example', '24 / 85'], ['question', '62'], ['exam_template', '1'], ['listening', '17']]))
body.append('<h3>알려 둘 것</h3><ul>'
            '<li>문항 62개 중 오답이 사전에 있는 읽기라서 정답이 둘이 되던 문항들(vocab-0002·0004·0005, grammar-0008)을 리뷰에서 찾아 고쳤다. 고친 문항은 JMdict로 다시 확인했지만 <b>원어민 검수는 아니다</b>. 표본 확인을 권한다.</li>'
            '<li>N4 뜻은 63행만 표본 검수했다(reviewerKind=agent). 나머지 567행은 <code>sampled</code>로 화면에 "AI 검수" 수준으로 보인다.</li>'
            '<li>필순 스크린샷의 가장자리 픽셀이 실행마다 달라서(기준 커밋에서도 재현) 해당 6장에만 0.1% 허용 오차를 걸었다. 원인(애니메이션 시점) 제거는 남은 정리 거리다.</li>'
            '<li>알려진 low 지적(새 카드 단계 상수가 두 곳에 있음, 하루 시작 시각 시트 숨김 애니메이션 없음 등)은 기능에 영향이 없어 남겼다.</li>'
            '</ul>')

body.append('<h2>3. 화면 스크린샷(light, 대표)</h2>')
body.append('<h3>연습·문제 풀이(M19a·b)</h3>' + gallery([('practice_home_light', '연습 홈(S12)'), ('practice_choice_light', '문제 풀이(S13)'), ('practice_feedback_light', '채점 뒤 해설 카드')]))
body.append('<h3>모의고사·결과(M19c·d)</h3>' + gallery([('mock_exam_intro_light', '모의고사 시작(영역별 줄·청해 제외 고지)'), ('mock_exam_intro_shortfall_light', '문항 부족 시 시작 비활성'), ('mock_exam_nav_light', '풀이 중 번호 이동 줄'), ('mock_exam_confirm_submit_light', '미응답 제출 확인'), ('quiz_result_light', '결과(원점수·고정 안내)'), ('quiz_result_explanation_light', '결과 안 해설')]))
body.append('<h3>청해(M23)</h3>' + gallery([('listening_ready_light', '청해 준비'), ('listening_script_open_light', '대본 펼침'), ('listening_missing_voice_light', '일본어 음성 없음 — 설치 안내')]))
body.append('<h3>필순·접근성·글꼴(M12·T14·T12)</h3>' + gallery([('kanji_detail_strokes_done_light', '한자 필순'), ('settings_loaded_light', '설정(새 스테퍼)'), ('settings_day_start_sheet_light', '하루 시작 시각 시트'), ('review_back_word_light', '카드 뒷면'), ('level_loaded_light_font200', '급수 선택 글꼴 200%')]))
body.append('<p>다크 모드와 글꼴 200% 쌍을 포함한 전체 264장은 dev/app의 <code>screenshots/</code>에 있다.</p>')

body.append('<h2>4. 실기기 확인 체크리스트(U-7: 개발 종료 후 한 번에)</h2>')
body.append('<p>기기 후보: Galaxy Z Fold8(Android 17), Fold5(Android 16). 결과는 <code>_workspace/04_device_results.md</code>에 기기·버전과 함께 적고, 통과하면 해당 마일스톤을 <code>done</code>으로 바꾼다. 에뮬레이터에서는 아래 1~2번 일부만 대체 확인했다(2026-11-02).</p>')
body.append(table(['#', '준비', '할 동작', '기대 결과', '마일스톤'], [
    ['1', '설정 → 시스템 언어 한국어', '앱 첫 화면·사전에서 直·骨·角을 본다', '일본식 자형으로 그려진다. 아니면 글꼴 번들 마일스톤 추가', 'M2'],
    ['2', '카드 학습 시작', '카드 한 장을 평가한 직후 앱을 강제 종료하고 다시 연다', '마지막 평가까지 기록이 남아 있다', 'M4'],
    ['3', 'TalkBack 켜기(한국어)', '복습 카드 앞면·뒷면을 차례로 듣는다', '앞면 한자가 일본어 음성으로, 뒷면 "읽기 …"의 일본어도 일본어로 읽힌다', 'M4'],
    ['4', 'TalkBack 켜기', '카드를 뒤집고 다음 카드로 넘기고 되돌리기를 한다', '각 단계에서 알림이 한 번씩만 나오고 뒷면이 이어서 읽힌다', 'T14'],
    ['5', 'TalkBack 켜기', '설정·급수 선택의 "하루 새 카드" − / + 를 누른다', '값이 "{n}장"으로 한 번 읽힌다', 'T14'],
    ['6', 'TalkBack 켜기', '연습 문제 풀이에서 지시문·선택지를 듣는다', '일본어 구간이 일본어 음성으로 읽힌다', 'M19b'],
    ['7', 'TalkBack 켜기', '결과 화면에서 영역별 점수와 해설을 듣는다', '점수 줄이 한 덩어리로 읽히고 해설의 일본어가 일본어로 읽힌다', 'M19d'],
    ['8', '일본어 음성 데이터 설치된 상태', '연습 → 청해 → 재생 버튼', '일본어 음성이 재생된다. 화면을 나갔다 다시 들어와도 재생된다', 'M22·M23'],
    ['9', '일본어 음성 데이터 삭제(설정 → TTS)', '청해 화면 열기', '"일본어 음성 없음" 설치 안내가 나오고 대본이 펼쳐진다', 'M22·M23'],
    ['10', '남성·여성이 나오는 청해 문항', '재생해 화자별 목소리를 듣는다', '화자별 음높이 차이가 들린다', 'M22'],
    ['11', 'TalkBack 켜고 청해', '재생 버튼을 누른다', '약 1초 뒤 음성이 시작되어 "재생 중" 알림과 겹치지 않는다', 'M23'],
    ['12', '청해 문항 0003·0007·0008', '숫자·날짜·上·下가 나오는 줄을 듣는다', '의도한 읽기(いえ, はつか·じゅうはちにち, うえ·した)로 들린다. 틀리면 <code>ttsText</code>를 고친다', 'M25'],
    ['13', '글꼴 크기 최대(200%)', '홈·급수·설정·모의고사 시작을 본다', '잘림·겹침이 없다(스크린샷으로는 확인됨)', 'T12'],
]))

body.append('<h2>5. 병합 순서 제안</h2>')
body.append('<ol>'
            '<li><b>dev/base</b> → main: 병합 전 <code>/pstack:blast-radius</code>를 직접 실행한다. 문서·설계·스키마(v2)·마이그레이션이 들어 있다.</li>'
            '<li><b>dev/content</b> → main: 번들 DB(<code>content.db</code>)와 파이프라인. 병합 뒤 <code>python3 -I content/pipeline/build.py</code>를 한 번 더 돌려 <code>content.db.sha256</code>가 같은지, <code>check.sh</code>가 통과하는지 확인한다.</li>'
            '<li><b>dev/app</b> → main: 이미 dev/base·dev/content를 포함하므로 위 둘을 병합한 뒤에는 충돌 없이 합쳐진다(dev/app 하나만 병합해도 세 갈래가 모두 들어간다).</li>'
            '<li>병합 뒤 <code>verify.sh</code>와 <code>./gradlew verifyRoborazziDebug</code>를 main에서 한 번 더 돌린다. 그다음 4절 실기기 확인.</li>'
            '</ol>')
body.append('<p class="note">참고: 이 세션은 main에 커밋하지 않았다. main 체크아웃의 미커밋 변경(<code>.claude/agents/ui-designer.md</code>, <code>.claude/rules/</code>, <code>docs/plan/</code>)은 그대로 보존했다.</p>')

css = '''body{font-family:-apple-system,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;max-width:1100px;margin:2rem auto;padding:0 1rem;line-height:1.6;color:#1b1b1f}
h1{border-bottom:2px solid #1b1b1f;padding-bottom:.3rem}h2{margin-top:2.2rem;border-bottom:1px solid #ccc;padding-bottom:.2rem}
table{border-collapse:collapse;width:100%;margin:.8rem 0;font-size:.92rem}th,td{border:1px solid #ccc;padding:.4rem .6rem;vertical-align:top;text-align:left}th{background:#f1f1f5}
code{background:#f1f1f5;padding:0 .25rem;border-radius:3px;font-size:.88em}.lead{font-size:1.05rem}.note{color:#555}
.gallery{display:flex;flex-wrap:wrap;gap:1rem}figure{margin:0;width:230px}figure img{width:100%;border:1px solid #ccc;border-radius:6px}figcaption{font-size:.8rem;color:#444}'''
doc = f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JLPTStudyApp 개발 재개 결과 2026-11-07</title><style>{css}</style></head><body>{"".join(body)}</body></html>'
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, 'w').write(doc)
print(OUT, len(doc) // 1024, 'KiB')
