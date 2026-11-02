---
name: content-pipeline
description: "JLPT 학습 앱의 콘텐츠 파이프라인 규칙: 매니페스트에 고정한 원본 자료와 content/authored의 직접 작성 콘텐츠를 재현 가능한 스크립트로 앱 번들 DB(Room createFromAsset)와 저작자 표시 데이터로 만드는 디렉터리 구조, 단계, 결정적 빌드, Room 스키마 일치, 급수 매핑, 한국어 뜻 결합, 오프라인 검사(check.sh). 파이프라인 구현·리뷰, 콘텐츠 DB 오류, 원본 판 갱신, 스키마 불일치 문제에 사용한다. 출처·라이선스 판정은 content-licensing, 콘텐츠 작성은 jlpt-content-authoring, 마일스톤 실행 자체는 jlpt-app-orchestrator를 쓴다."
---

# Content Pipeline — 원본에서 앱 DB까지

콘텐츠 DB는 사람이 손으로 고치는 파일이 아니라 빌드 산출물이다. 손으로 고친 행은 다음 빌드에서 사라지고, 출처를 잃는다. 그래서 모든 행은 스크립트가 매니페스트의 원본과 `content/authored/`에서 만들고, 같은 입력이면 같은 DB가 나와야 한다.

## 1. 디렉터리

| 경로 | 내용 | 커밋 |
|------|------|------|
| `content/pipeline/` | 파이프라인 스크립트, 테스트, `check.sh` | 예 |
| `content/raw/` | 매니페스트 URL에서 내려받은 원본 | 아니오(`.gitignore`) |
| `content/authored/` | 직접 작성한 콘텐츠(`jlpt-content-authoring` 형식) | 예 |
| `content/ids/` | 배포한 콘텐츠 ID 목록(안정성 검사 기준) | 예 |
| `app/src/main/assets/content/` | 번들 DB, 저작자 표시 데이터 | 예(앱 빌드가 파이프라인 실행 없이 되도록) |

경로를 바꾸면 `architecture.md`와 이 표를 함께 고친다.

## 2. 언어와 의존성

- 기본: Python 3 표준 라이브러리만(`xml.etree.ElementTree.iterparse`, `sqlite3`, `csv`, `json`, `hashlib`, `urllib.request`, `unittest`). 서드파티 패키지를 쓰지 않으면 다른 컴퓨터에서도 같은 결과를 얻기 쉽다. 실행은 `python3 -I`.
- Kotlin(JVM) 도구 모듈 등 다른 선택을 하면 이유를 `architecture.md`에 적고, 마일스톤에 `codeReviewer`를 맞춘다.
- 기계 번역 등 외부 서비스를 부르는 단계는 결과를 `content/` 아래 파일로 저장해 커밋하고, 이후 빌드는 그 파일을 읽는다. 외부 서비스는 다시 부를 때 같은 결과를 보장하지 않기 때문이다.

## 3. 단계

1. **fetch**: 매니페스트에서 `status: confirmed`인 자료만 `downloadUrl`로 받아 `sha256`을 대조한다. 다르면 멈춘다. `sha256`이 비어 있으면(첫 도입) 계산해 출력하고, 매니페스트 갱신을 보고서에 요청한다.
2. **parse**: 원본을 중간 형식으로 읽는다. 큰 XML(JMdict)은 스트리밍 파싱한다.
3. **level map**: 확정된 급수 목록과 사전 항목을 맞춘다. 표기·읽기가 여러 항목과 맞거나 하나도 맞지 않으면 추측하지 않고 `_workspace/03_content-engineer_{마일스톤ID}_unmatched.tsv`에 남긴다. 해소 규칙(예: 읽기까지 일치해야 함)은 `architecture.md`에 적는다.
4. **ko gloss**: 한국어 뜻을 `content-licensing` 4절 방식대로 결합하고 출처·생성 방법·검수 상태 열을 채운다.
5. **authored**: `content/authored/`를 읽고 형식 검사(필수 필드, ID 중복, 급수 값, 정답 색인 범위)를 한다.
6. **build**: SQLite DB를 만든다(4절). 저작자 표시 데이터(JSON 등)를 매니페스트에서 만든다.
7. **check**: 5절 검사.

`content/pipeline/` 안의 진입점 하나(예: `build.py`)가 1~7을 순서대로 돌고, 단계별로도 실행할 수 있게 한다.

## 4. 번들 DB 만들기(Room 호환)

- Room은 `createFromAsset`으로 연 DB의 스키마가 엔티티와 다르면 실패한다. 손으로 쓴 `CREATE TABLE`로 맞추려 하지 않는다. `:app`이 내보낸 Room 스키마 JSON(`app/schemas/<콘텐츠 DB 클래스>/<버전>.json`)의 `entities[].createSql`, `indices[].createSql`, `setupQueries`를 읽어 그대로 실행한다(`${TABLE_NAME}`을 테이블 이름으로 바꾼다). 엔티티를 바꾸면 앱 빌드로 스키마 JSON이 갱신되고, 파이프라인은 그것을 따라간다.
- 결정적 빌드: 입력을 정렬된 순서로 넣고, 생성 시각 같은 값은 넣지 않거나 매니페스트·콘텐츠 판에서 가져온다. 마지막에 `VACUUM`. 같은 입력으로 두 번 빌드해 파일 해시가 같은지 테스트한다(원본이 있을 때만 실행하는 검사).
- 콘텐츠 판은 DB 안의 메타 테이블 또는 `PRAGMA user_version`에 적고, 앱이 표시·비교에 쓴다. 방법은 `architecture.md`에서 정한다.
- 모든 콘텐츠 행에 출처 ID 열(매니페스트 `id` 또는 `authored`)을 둔다.
- 크기 예산(APK 증가량)을 `architecture.md`에 정하고 검사한다. 급수 태그가 있는 항목만 넣는 것이 기본이다.

## 5. 검사(`content/pipeline/check.sh`)

`verify.sh`가 인자 없이 실행될 때 먼저 부른다. **네트워크 없이, `content/raw/` 없이** 커밋된 파일만으로 돌아야 한다.

| 검사 | 실패 조건 |
|------|-----------|
| 파이프라인 단위 테스트 | `python3 -I -m unittest` 실패 |
| 스키마 | 번들 DB의 `sqlite_master`가 Room 스키마 JSON의 `createSql`과 다름 |
| 출처 | 출처 ID가 없는 행, 매니페스트에 없거나 `confirmed`가 아닌 출처 ID |
| 금지 자료 | 에셋에 음성 파일(`.mp3`, `.ogg`, `.wav`, `.m4a` 등), 매니페스트에 없는 데이터 파일 |
| ID 안정성 | `content/ids/`에 있던 ID가 DB에서 사라짐(사라질 때는 `retired` 표시가 있어야 함) |
| 한국어 뜻 출처 | 뜻이 있는데 뜻 출처·검수 상태 열이 비어 있음 |
| 직접 작성 콘텐츠 형식 | 3절 5단계 형식 검사 실패 |
| 저작자 표시 | DB에 쓰인 출처 ID가 저작자 표시 데이터에 없음 |
| 행 수 범위 | 급수별 행 수가 `architecture.md`에 적은 범위를 벗어남 |

`check.sh`는 실패 항목마다 `FAIL: <검사> <이유>`를 한 줄씩 출력하고 0이 아닌 코드로 끝난다.

## 6. 리뷰에서 볼 것

- 최신 판을 받는 URL(`latest`), 체크섬 대조 생략, 예외를 삼키는 파싱(`except: pass`).
- 사전 항목을 표기만으로 맞추는 급수 매핑(동형이의어를 잘못 붙인다).
- DB에 손으로 넣은 행, 테스트에 하드코딩한 원본 경로.
