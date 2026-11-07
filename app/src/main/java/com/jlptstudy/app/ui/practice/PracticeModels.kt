package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.app.speech.Voice
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.quiz.Score
import com.jlptstudy.quiz.Section

// architecture.md 9.2.6 S12·S13. 계약 밖 필드는 주석으로 표시한다.
enum class PracticeHomeStatus { LOADING, LOADED, EMPTY }
data class PracticeTypeUi(val section: Section, val type: String, val count: Int)
data class ResumeExamUi(val attemptId: Long, val level: JlptLevel, val answered: Int, val total: Int, val elapsedMs: Long)
data class ExamCardUi(val level: JlptLevel, val questionCount: Int, val limitMs: Long, val hasShortfall: Boolean)

data class PracticeHomeUiState(
    val status: PracticeHomeStatus = PracticeHomeStatus.LOADING,
    val level: JlptLevel = JlptLevel.N5,
    val levels: List<JlptLevel> = emptyList(),
    val types: List<PracticeTypeUi> = emptyList(),
    val exam: ExamCardUi? = null,
    val resume: ResumeExamUi? = null,
    val confirmReplace: ResumeExamUi? = null,
    val confirmAbandon: Boolean = false,
    val startFailed: Boolean = false,
    val abandonFailed: Boolean = false, // 계약 밖: finish 예외(포기 실패 스낵바)
)

// S14 시작 화면. 라우트 ExamStart(level) — 시도 행이 아직 없다
enum class ExamStartStatus { LOADING, LOADED, EMPTY, FAILED }
data class ExamSectionLine(val titleKo: String, val count: Int, val minutes: Int)
data class ExamStartUiState(
    val status: ExamStartStatus = ExamStartStatus.LOADING,
    val level: JlptLevel = JlptLevel.N5,
    val sections: List<ExamSectionLine> = emptyList(),
    val total: Int = 0,
    val limitMs: Long = 0,
    val hasShortfall: Boolean = false,
    val starting: Boolean = false,
    val startFailed: Boolean = false,
    /** startAttempt 성공 시 만든 시도. Route가 한 번 소비해 Quiz(attemptId)로 간다. */
    val startedAttemptId: Long? = null,
)

enum class QuizStatus { LOADING, EMPTY, ANSWERING, FEEDBACK, CONFIRM_EXIT, CONFIRM_SUBMIT, SUBMITTED }

/** 청해 대본 한 줄(architecture.md 9.3.1). speakText = 읽는 문자열(ScriptSpeech 규칙). */
data class ListeningLineUi(val voice: Voice, val text: List<RubySegment>, val speakText: String)

data class QuizQuestionUi(
    val id: String, val section: Section, val type: String, val target: String?,
    val stem: List<RubySegment>, val passage: List<RubySegment>?,
    val choices: List<List<RubySegment>>, // 섞은 순서
    val answerIndex: Int, // 섞은 뒤 색인
    val explanationKo: String, val whyWrongKo: List<String?>, // 섞은 순서, 정답 자리 null
    val targets: List<String>, val provenance: MeaningUi?, // null = 이 문항에 AI가 쓴 한국어가 없음 → 칩을 그리지 않는다
    val needsReview: Boolean,
    val lines: List<ListeningLineUi>? = null, // 청해만(대본). 계약은 passage 뒤지만 기존 위치 인자 호출을 지키려고 끝에 둔다
    val generated: Boolean = false, // 규칙으로 만든 문항(QG4가 "자동 생성 연습 문제" 라벨을 그린다)
)

data class QuizUiState(
    val status: QuizStatus = QuizStatus.LOADING, val mode: QuizMode = QuizMode.PRACTICE,
    val attemptId: Long = 0, val index: Int = 0, val total: Int = 0,
    val question: QuizQuestionUi? = null,
    val chosenIndex: Int? = null,
    val answeredIndices: Set<Int> = emptySet(),
    val sectionStarts: List<Int> = emptyList(),
    val elapsedMs: Long = 0, val limitMs: Long? = null,
    val unansweredCount: Int = 0,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val showRuby: Boolean = true, // 계약 밖: 설정의 본문 후리가나(상단 토글)
    val finishFailed: Boolean = false, // 계약 밖: saveFailed 중 finish 예외(스낵바 문구를 "제출하지 못했습니다"로)
    val sectionTitles: List<String> = emptyList(), // 계약 밖: EXAM sectionStarts와 짝인 템플릿 영역 이름(번호 줄·현재 영역)
)

// S15 결과. 라우트 QuizResult(attemptId) — 연습·모의고사·청해 공통. 해설 보기는 하위 라우트가 아니라 이 라우트 안 상태(reviewIndex)
enum class QuizResultStatus { LOADING, LOADED, EMPTY }
enum class ResultFilter { WRONG, ALL }
data class QuizReviewItemUi(
    val index: Int, val question: QuizQuestionUi, val chosenIndex: Int?, // 섞은 뒤 색인, null = 미응답
    val correct: Boolean, val scriptShown: Boolean,
)

/** 해설의 "관련 단어·문법 보기" 링크. kind가 목적지(VocabDetail·KanjiDetail·GrammarDetail)를 정한다. */
enum class TargetKind { VOCAB, KANJI, GRAMMAR }
data class TargetLink(val kind: TargetKind, val id: String, val label: String)

data class QuizResultUiState(
    val status: QuizResultStatus = QuizResultStatus.LOADING, val mode: QuizMode = QuizMode.PRACTICE,
    val level: JlptLevel? = null, val type: String? = null,
    val score: Score? = null,
    val elapsedMs: Long? = null, val limitMs: Long? = null,
    val scriptShownCount: Int = 0,
    val retiredCount: Int = 0,
    val filter: ResultFilter = ResultFilter.WRONG,
    val items: List<QuizReviewItemUi> = emptyList(),
    val reviewIndex: Int? = null, // null = 목록, 값 = items 순번의 해설 보기
    val targetLinks: Map<String, List<TargetLink>> = emptyMap(), // 계약 밖: 문항 ID → 번들에 있는 관련 항목 링크(해설을 열 때 채운다)
    val showRuby: Boolean = true, // 계약 밖: 설정의 본문 후리가나
) {
    /** 필터를 적용한 목록(틀림 + 답하지 않음 / 전체). */
    val visibleItems: List<QuizReviewItemUi>
        get() = if (filter == ResultFilter.ALL) items else items.filter { !it.correct || it.chosenIndex == null }
}
