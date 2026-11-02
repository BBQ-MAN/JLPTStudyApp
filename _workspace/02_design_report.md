# 2단계 설계·검증 보고 (2026-11-02)

## 실행
| 단계 | run | 결과 | 에이전트 |
|------|-----|------|----------|
| license-verify | wf_ceec91c4-2d1 | 주장 278: 확정 197 / 미확정 63 / 반박 18, mismatches 0, untracked 0, partial 0 (두 번 중단 후 재개) | 559 |
| 설계 작성 | android-architect, ui-designer | architecture.md, milestones.json(30), ui-design.md | 2 |
| design-review(전체) | wf_31d285cb-453 | 지적 79: 확정 68 / 기각 11, 반영 112, declined 17, unapplied 10, postRevision 16 | 87 |
| 후속 정리 | architect → ui-designer·analyst → architect | postRevision 16·unapplied 10 정리, D-08 추가 | 4 |
| design-review(consistency) | wf_b3f77f2d-fa5 | 지적 18: 확정 16(high 0) / 기각 2, 반영 28, declined 4, unapplied 8(대부분 문서에 반영 확인), postRevision 8 | 23 |

## 남은 불일치(postRevision, 구현 전 정리 권장)
- medium: ui-design 5.8 NoEngine "열 수 없으면 버튼 숨김" ↔ architecture 5.1·9.3·M7 "미리 판정 금지(startActivity + ActivityNotFoundException)"
- low: ui-design `StudyPlanRepository.dueForSession` ↔ architecture `SessionCards.load`
- low: M19 스크린샷 이름(practice_home·practice_home_resume·quiz_result)이 ui-design 12절에 없음
- low: `connections[].posKo`가 ui-design 4.4 한국어 필드 목록·4.2 로캘 표에 없음
- low: S-11 고지 파일 커밋 시점(ui-design 9.2-5 ↔ architecture 6.6·M7)
- low: sources.md S-11 채움 파일 이름이 `ic_ms_<이름>_fill.xml`이 아님, "architect 반영 대기" 문장 잔존
- low: architecture 11.2·11.3에 이미 반영된 요청이 할 일로 남음
- low: architecture 6.5 새 검사 '출처 열'을 시험하는 완료 조건 없음

## 하네스에서 발견한 문제(harness:evolve 후보)
1. license-verify 추출 단계가 "상태" 칸(검증 대기 등 내부 값)까지 주장으로 뽑아, 원 출처가 없으니 영원히 미확정 → 매니페스트 status가 confirmed가 될 수 없다.
2. 반박·수정된 행만 다시 검증하는 방법이 없다(일부만 바뀌어도 전체 재검증 ≈ 560 에이전트).
3. milestones.json의 `blockedBy`를 implement-milestones.js·오케스트레이터 마일스톤 선택이 읽지 않는다.
4. 백그라운드 워크플로가 메인 턴이 끝나고 약 10분 뒤 "[Request interrupted by user]"로 두 번 중단됐다(원인 미확인, 턴 안에서 대기하자 완료됨).
