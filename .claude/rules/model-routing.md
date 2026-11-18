# 작업별 모델 배정 (크레딧 최적화, 2026-11-07)

비싼 모델은 결과가 틀리면 되돌리기 어려운 판단·콘텐츠에만 쓴다. 나머지는 sonnet, 기계적 작업은 haiku.
Agent 호출에서 `model`을 생략하면 에이전트 frontmatter 값, 그것도 없으면 메인 세션 모델을 따른다. 아래 표와 다르게 부를 때만 `model`을 명시한다. pstack 역할별 모델은 `pstack-models.md`가 따로 정한다.

| 등급 | 모델 | 작업 | 에이전트 |
|------|------|------|----------|
| 판단·콘텐츠 | opus | 출처·라이선스 판단, 구조 설계(DB·마이그레이션), 학습 콘텐츠 작성(문법 설명·예문·문항·청해 대본 — 틀리면 학습자가 잘못 배운다) | `content-licensing-analyst`, `android-architect`, `jlpt-content-author` |
| 구현·리뷰 | sonnet | 기능 구현, 파이프라인·검사기, 화면 설계 보완, QA, 워크플로 안의 추출·검증·리뷰 호출(1d163c6), 콘텐츠 형식 검사 | `feature-engineer`, `content-engineer`, `ui-designer`, `android-qa-inspector` |
| 기계적 | haiku | 진행 표·문서 갱신, 로그·테스트 결과 요약, 파일 목록, 스크린샷 목록 정리 | `model: "haiku"`를 준 일반 하위 에이전트 |

규칙
- 메인 세션(오케스트레이터)은 sonnet으로 실행한다. 조율·병합·verify 실행·작은 보정은 sonnet으로 충분하다. 설계 판단이 막히면 `android-architect`(opus)에 넘긴다.
- 워크플로 사본(`_workspace/tools/wf_wt.js`, `wf_design_wt.js`)도 main의 워크플로와 같이 agentType 없는 호출을 sonnet으로 고정한다.
- 콘텐츠 **검수**(작성자가 아닌 쪽)는 sonnet으로 하고, 사실 오류가 의심되는 행만 opus로 재확인한다.
- 같은 작업을 opus로 다시 돌리는 것은 sonnet이 두 번 연속 같은 지점에서 실패했을 때만 한다. 진행 표 메모에 한 줄 남긴다.
