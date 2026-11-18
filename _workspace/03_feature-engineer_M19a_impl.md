# M19a 구현 요약
- ContentDao: questionPool, listeningPool, questionsByIds, listeningByIds, examTemplateRow, examTemplateLevels. StudyDao: 시도·답 쿼리(삭제 없음), startAttempt 트랜잭션.
- data/content/QuizContentRepository.kt (RoomQuizContentRepository, toQuizContent 변환, toQuestion/toPoolItem), data/study/QuizRepository.kt, data/QuizSessionLoader.kt.
- RoomContentRepository·RoomStudyRepository가 위임으로 구현; AppContainer.quizContentRepository·quizRepository(가짜 저장소면 null).
- 테스트: data/QuizRepositoryTest(Robolectric), QuizSessionLoaderTest(단위·변환·900 분할), QuizFixtures. ChunkingTest의 가짜 DAO에 새 메서드 추가.
- 스키마 JSON 변경 없음. QuizMode는 data 패키지(QuizSessionLoader.kt)에 정의 — M19b에서 UI 모듈로 옮길지 결정.
- 가정: listening lines_json 키 speaker/text/ttsText, template_json {"sections":[{id,titleKo,minutes,parts[{section,type,count}]}]}.
