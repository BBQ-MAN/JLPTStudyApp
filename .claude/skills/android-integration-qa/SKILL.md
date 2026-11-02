---
name: android-integration-qa
description: "JLPT 학습 앱의 통합 정합성 검사 목록. 출처 문서 ↔ 매니페스트 ↔ 파이프라인 ↔ 번들 DB ↔ Room 엔티티, 콘텐츠 행 ↔ 출처 ID ↔ 저작자 표시 화면, 콘텐츠 안정 ID ↔ 학습 기록, SRS 스케줄러 ↔ 저장소 ↔ 복습 화면, 문제 데이터 ↔ 채점 ↔ 결과 화면, TTS 인터페이스 ↔ 화면 상태, 일본어 텍스트 ↔ 로캘을 양쪽 코드를 함께 읽어 대조한다. android-qa-inspector가 따르는 검사 기준이며, 통합 버그 조사와 계약 불일치 질문에도 사용한다. 'QA 다시' 같은 마일스톤 재검사 실행 요청 자체는 jlpt-app-orchestrator로 시작한다. 시각 디자인 리뷰는 ui-design-review, 콘텐츠의 언어적 정확성은 jlpt-content-authoring 8절을 쓴다."
---

# Android Integration QA — 연결 지점 대조 목록

빌드와 단위 테스트는 각 부품이 스스로 맞는지만 확인한다. 이 앱에서 실제로 사용자를 괴롭히는 결함은 부품 사이에서 생긴다: 파이프라인이 만든 DB의 열 하나가 Room 엔티티와 달라 앱이 시작하자마자 죽거나, 스케줄러는 학습일 경계를 04시로 보는데 홈 화면은 자정으로 세거나, DB에는 출처가 있는데 저작자 표시 화면에는 빠지거나. 그래서 검사는 항상 **생산자와 소비자를 함께 열어** 대조한다.

## 절차

1. 마일스톤의 변경 파일 목록을 받는다.
2. 변경 파일이 아래 표의 어느 연결 지점에 속하는지 찾고, 반대편 코드·문서를 `Grep`으로 찾아 함께 읽는다.
3. 표의 대조 항목을 확인하고 결과를 지적 또는 통과로 기록한다.
4. `android-build-verify`의 `scripts/verify.sh`를 실행해 구현자 보고와 비교하고, 종료 코드 0이면 `verifyPassed: true`로 반환한다. 구현자 보고와 다르면 `high` 지적이다.
5. 마일스톤 `acceptance` 항목마다 `pass | fail | unverified`를 판정한다. 실기기가 필요한 항목(TTS 음성, 기기 글꼴, TalkBack 언어 전환)은 `unverified`로 두고 이유를 적는다.

설계 단계(코드 없음)에서 호출되면 같은 표를 문서끼리 적용한다: `ui-design.md` 상태 ↔ `architecture.md` 상태 모델, 화면 표시 값 ↔ 콘텐츠 DB 스키마가 제공하는 값, `architecture.md`가 쓰는 출처 ↔ `sources.md`의 `확정` 출처, `milestones.json` ↔ 모든 화면·계약·콘텐츠.

## 연결 지점 대조표

| ID | 생산자 | 소비자 | 대조할 것 |
|----|--------|--------|-----------|
| Q1 | `docs/content/sources.md` | `manifest.json` | 같은 `id`·라이선스·상태인가, `confirmed`는 sources.md에서도 확정인가 |
| Q2 | `manifest.json` | 파이프라인 fetch | `confirmed`만 받는가, URL·체크섬을 매니페스트에서 읽는가(하드코딩 없음), 체크섬 불일치 시 멈추는가 |
| Q3 | Room 콘텐츠 엔티티(스키마 JSON) | 번들 DB | 테이블·열·타입·NOT NULL·기본 키·인덱스가 같은가, 파이프라인이 스키마 JSON의 `createSql`을 쓰는가, 번들 DB를 Room으로 여는 테스트가 있는가 |
| Q4 | 번들 DB 행 | 매니페스트 | 모든 행에 출처 ID가 있고 그 ID가 `confirmed`인가, 한국어 뜻의 출처·생성 방법·검수 상태 열이 채워졌는가 |
| Q5 | 매니페스트·번들 DB | 저작자 표시 데이터·화면 | DB에 쓰인 모든 출처가 화면에 이름·라이선스·링크·저작자 표시·변형 내용과 함께 나오는가, 화면 코드에 출처 문구 하드코딩이 없는가 |
| Q6 | 콘텐츠 안정 ID | 학습 기록 테이블 | 학습 기록이 안정 ID(rowid 아님)로 가리키는가, `retired` ID 처리, 콘텐츠 DB 교체 뒤에도 기록이 이어지는가 |
| Q7 | `:core:srs` 계약 | 저장소·ViewModel | 시각 단위(UTC epoch ms), `Clock` 주입, 학습일 경계·시간대가 한 곳에서 계산되는가, 평가 값 ↔ 버튼 대응이 한 곳에 정의됐는가 |
| Q8 | 평가 처리 | 학습 기록 DB | 카드 상태 갱신과 복습 로그 추가가 한 트랜잭션인가, 평가마다 즉시 저장되는가, 되돌리기가 로그로 복원되는가 |
| Q9 | 학습 기록 Room 엔티티 | 스키마 내보내기·마이그레이션 | 버전을 올리면 마이그레이션과 마이그레이션 테스트가 있는가, `fallbackToDestructiveMigration`이 없는가 |
| Q10 | 문제 데이터(DB) | `:core:quiz` 채점·결과 화면 | 정답 색인 기준(0/1부터), 선택지 섞을 때 정답 대응 유지, 영역별 점수 계산, 원점수만 표시하는가(척도 점수 흉내 없음) |
| Q11 | UI 상태 sealed 타입 | 화면 `when` 분기, `ui-design.md` 상태 표 | 모든 상태가 화면에 그려지는가, 분기의 상태가 실제로 도달 가능한가 |
| Q12 | `Speaker`(TTS) 인터페이스 | 구현·화면 상태 | 일본어 사용 가능 여부 확인, 상태(준비 중·데이터 없음·엔진 없음) ↔ 화면 대응, `stop`/`shutdown` 경로, `ttsText` 우선 사용, 에셋에 음성 파일 없음 |
| Q13 | 일본어 콘텐츠 필드 | `Text`·`AnnotatedString` | 일본어 필드를 그리는 곳에 일본어 로캘 스타일이 걸렸는가, 후리가나 컴포저블을 공통으로 쓰는가 |
| Q14 | 내비게이션 경로 정의 | 화면 이동 호출 | 모든 이동 대상이 등록돼 있고 인자 타입(콘텐츠 ID 등)이 맞는가 |
| Q15 | 콘텐츠 경로 | 앱 코드·리소스 | 학습 콘텐츠(단어 뜻, 예문, 문제)가 코드·`strings.xml`에 하드코딩돼 있지 않은가, 에셋의 데이터 파일이 모두 파이프라인 산출물인가 |
| Q16 | 매니페스트 권한 | 기능 | 인터넷 권한이 없는가(오프라인 우선), 새 권한이 생겼다면 설계 근거가 있는가 |

## Android 공식 스킬 점검

대조표는 부품 사이의 계약을 본다. Android 플랫폼 규칙 위반(권한, Intent, 메인 스레드, 인셋)은 계약이 맞아도 생기므로 해당 파일이 바뀐 마일스톤에서 공식 스킬로 따로 점검한다.

| 바뀐 것 | 공식 스킬 | 보는 것 |
|---------|-----------|---------|
| 매니페스트 | `android-permissions-security` | 선언·`exported`가 최소이고 맞는지 |
| TTS 데이터 설치·외부 링크 열기 | `android-intent-security` | Intent 대상과 처리 가능 여부 확인 |
| Room·에셋 복사·TTS 초기화 | `anr-audit-and-fix` | 메인 스레드에서 막히는 호출 |
| 화면 골격 | `edge-to-edge` | 인셋 누락으로 가려지는 내용 |
| 긴 목록·카드 화면 | `jetpack-compose-performance` | 재구성 범위, `LazyColumn` 키 |

불러오는 법: Skill 도구로 `android-skills:<이름>`을 부른다. 도구가 없거나 이름을 못 찾으면 `~/.claude/plugins/cache/android-skills/android-skills/*/` 아래에서 Glob `**/<이름>/SKILL.md`로 찾아 Read로 읽는다(플러그인 판 번호가 바뀌어도 찾히게 `*`를 쓴다). 둘 다 안 되면 그 사실을 보고하고 이 스킬만으로 진행한다.

공식 스킬에서 나온 지적은 제목 앞에 `[android-skills:<이름>]`을 붙인다. 이 스킬의 대조표나 프로젝트 스킬과 충돌하면 프로젝트 쪽이 우선한다.

## 정적 검사 보조 명령

```bash
grep -rn "currentTimeMillis\|Instant.now()\|LocalDate.now()" --include=*.kt app core          # Q7: 시계 직접 호출
grep -rn "fallbackToDestructiveMigration" --include=*.kt app                                     # Q9
grep -rln "\.mp3\|\.ogg\|\.wav\|\.m4a" app/src/main/assets 2>/dev/null; find app/src/main/assets -type f 2>/dev/null   # Q12·Q15
grep -rn "android.permission" app/src/main/AndroidManifest.xml                                   # Q16
grep -rn "collectAsState(" --include=*.kt app                                                     # 수명주기 미인식 수집
```

명령 결과는 출발점일 뿐이다. 찾은 위치마다 실제 코드를 읽고 판단한다.

## 지적 작성

- 파일:줄, 생산자 쪽 인용, 소비자 쪽 인용, 실제로 생길 오작동(예: "콘텐츠 DB 교체 뒤 복습 기록 120건이 다른 단어를 가리킴"), 고칠 방법.
- 대조표 ID(Q1~Q16)를 제목 앞에 붙인다. 같은 원인은 한 건으로 묶는다.
