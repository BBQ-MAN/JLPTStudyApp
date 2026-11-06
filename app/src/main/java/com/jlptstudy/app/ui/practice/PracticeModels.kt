package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.app.ui.review.MeaningUi
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
    val confirmReplace: ResumeExamUi? = null, // M19c
    val confirmAbandon: Boolean = false,
    val startFailed: Boolean = false,
    val abandonFailed: Boolean = false, // 계약 밖: finish 예외(포기 실패 스낵바)
)

enum class QuizStatus { LOADING, EMPTY, ANSWERING, FEEDBACK, CONFIRM_EXIT, CONFIRM_SUBMIT, SUBMITTED }

data class QuizQuestionUi(
    val id: String, val section: Section, val type: String, val target: String?,
    val stem: List<RubySegment>, val passage: List<RubySegment>?,
    val choices: List<List<RubySegment>>, // 섞은 순서
    val answerIndex: Int, // 섞은 뒤 색인
    val explanationKo: String, val whyWrongKo: List<String?>, // 섞은 순서, 정답 자리 null
    val targets: List<String>, val provenance: MeaningUi, val needsReview: Boolean,
)

// 청해 대본(lines)은 M23에서 더한다.
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
)
