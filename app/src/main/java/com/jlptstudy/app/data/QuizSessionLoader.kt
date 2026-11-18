package com.jlptstudy.app.data

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.QuizContent
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.QuizAttemptRecord
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.quiz.Section

enum class QuizMode { PRACTICE, EXAM, LISTENING }

data class AttemptMeta(val mode: QuizMode, val level: JlptLevel, val section: Section?, val type: String?)

data class LoadedAttempt(
    val record: QuizAttemptRecord,
    val meta: AttemptMeta?, // null: 남은 문항이 0이라 정할 수 없음
    val items: List<QuizContent>,
    val answers: Map<String, QuizAnswerRecord>,
    val retiredCount: Int,
)

/** 시도를 읽는 한 곳(architecture.md 9.2.6). 콘텐츠에서 빠진 문항은 items·answers에서 뺀다. */
class QuizSessionLoader(private val quiz: QuizRepository, val content: QuizContentRepository) {
    suspend fun load(attemptId: Long): LoadedAttempt? {
        val record = quiz.attempt(attemptId) ?: return null
        val contents = content.contents(record.questionOrder)
        val items = record.questionOrder.mapNotNull { contents[it] }
        val answers = quiz.answers(attemptId).filter { it.questionId in contents }.associateBy { it.questionId }
        return LoadedAttempt(record, meta(record.examId, items.firstOrNull()), items, answers, record.questionOrder.size - items.size)
    }

    companion object {
        /** S12 이어서 풀기 카드의 급수도 같은 규칙으로 읽는다. */
        fun meta(examId: String?, first: QuizContent?): AttemptMeta? {
            if (examId != null) {
                val level = examId.removePrefix("exam-n").toIntOrNull()?.let(JlptLevel::fromN) ?: return null
                return AttemptMeta(QuizMode.EXAM, level, null, null)
            }
            first ?: return null
            val mode = if (first.lines != null) QuizMode.LISTENING else QuizMode.PRACTICE
            return AttemptMeta(mode, first.level, first.section, first.type)
        }
    }
}
