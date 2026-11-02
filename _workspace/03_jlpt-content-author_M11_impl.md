# M11 jlpt-content-author 요약: N5 한국어 뜻 검수(D-03 범위)

## D-03 (착수 시 인용, docs/content/decisions.md 잠정 결정 표)

> N5는 에이전트(LLM) 전수 검수, N4 이상은 에이전트 표본 검수(묶음당 10%, 오류율 5% 넘으면 묶음 재생성·재검수). 사람 검수는 아직 없음(`reviewerKind=agent`, 화면에 "AI 검수"로 표시)

D-03의 상태는 "잠정 채택(사용자 최종 검토 대기)"이다. G-D03은 "결정"을 요구하지만, 2026-11-01 사용자 지시(잠정안으로 진행하고 끝난 뒤 검토)에 따라 진행했다.

## 한 일

- `content/ko-gloss/N5.jsonl`: 667행 전부를 en-gloss export의 영어 sense와 대조해 검수했다. 모든 행을 `reviewStatus: "reviewed"`, `reviewedAt: "2026-11-02"`, `reviewerKind: "agent"`로 바꿨다. 9행은 뜻도 고쳤다(兄·弟·少し·空·卵·寝る·悪い, 리뷰 후 太い·アパート). `content/notices.ko.json`의 `reviewPolicy`도 D-03에 맞게 고쳤다. 고치기 전과 후, 이유, 검수한 ID 전체는 `_workspace/03_jlpt-content-author_M11_review.md`에 있다.
- 검사 추가
  - `content/pipeline/pl_authored.py` `meaning_problems`: `sampled`나 `reviewed`인데 `reviewerKind`가 null이면 `FAIL: … reviewerKind missing`. 누가 검수했는지 밝히지 않은 행은 검수된 것으로 보이지 않게 한다. ko-gloss와 authored 어휘 모두에 적용된다. 지금 authored 파일은 모두 `unreviewed`라 이 검사에 걸리는 행이 없다.
  - `content/pipeline/test_kanji_authored.py` `test_format_errors`: 위 규칙에 대한 경우를 하나 더했다.
  - `content/pipeline/test_release.py` `test_committed_ko_gloss_format`: 커밋된 N5 667행이 모두 `reviewed`이고 `agent`이며 날짜 형식이 맞는지 검사한다. `human`이 한 행이라도 있으면 실패한다. check.sh의 unittest 단계에서 돈다.

## 검사 결과

- `content/raw`를 잠시 다른 곳으로 옮기고 `python3 -I content/pipeline/build.py`를 돌렸다. 종료 코드 0, "build: 667 vocab, 411 kanji, version v5571c961f231, 0 without gloss", "check: OK". `content/pipeline/check.sh`도 종료 코드 0이었다. 끝난 뒤 `content/raw`는 제자리로 돌려놓았다.
- 번들 DB `vocab_meaning`: 1656행 전부 `reviewed | 2026-11-02 | agent`.
- verify.sh: 종료 코드 0, 로그 `_workspace/build/verify_20261102_095304.log`.

## 남은 일과 위험

- 뜻을 만든 모델과 검수한 모델이 같다(Claude Opus 5.5). 그래서 같은 계열의 오류가 남았을 수 있다. 사람 검수 범위는 사용자가 정한다(D-03).
- `config.json`의 `milestone`은 여전히 `M10`이다. 그래서 다시 빌드한 보고서는 `_workspace/03_content-engineer_M10_*`를 덮어썼다. 내용은 같다.
