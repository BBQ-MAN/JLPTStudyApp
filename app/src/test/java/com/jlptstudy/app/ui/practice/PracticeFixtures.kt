package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.QuizContent
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.content.toPoolItem
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.QuizAttemptRecord
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.quiz.ExamPart
import com.jlptstudy.quiz.ExamSection
import com.jlptstudy.quiz.ExamTemplate
import com.jlptstudy.quiz.PoolItem
import com.jlptstudy.quiz.Section
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 합성 문항(실제 콘텐츠가 아니다). */
val provenance = MeaningUi("시험 해설", "authored", "llm:test-model@2026-11-03", "unreviewed", null, null)

fun qc(
    id: String, level: JlptLevel = JlptLevel.N5, section: Section = Section.VOCAB, type: String = "kanji-reading",
    stem: String = "これは[[{漢字|かんじ}]]です。", target: String? = "漢字", passage: String? = null,
    choices: List<String> = listOf("かんじ", "かじ", "かんし", "がんじ"), answer: Int = 0,
    why: List<String?> = listOf(null, "かじは別の語です。", "し は誤りです。", "濁りません。"), needsReview: Boolean = true,
    targets: List<String> = emptyList(),
) = QuizContent(
    id, level, section, type, stem, passage, target, null, choices, answer, "시험 해설 첫 문단\n\n시험 해설 둘째 문단", why, targets, provenance, needsReview,
)

class TickClock(var ms: Long = 1_000_000L) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = Instant.ofEpochMilli(ms)
}

class FakeQuizContent(
    var items: List<QuizContent> = emptyList(),
    var templates: Map<JlptLevel, ExamTemplate> = emptyMap(),
) : QuizContentRepository {
    var poolCalls = 0
    var templateCalls = 0
    var poolGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var failTemplate: Throwable? = null
    var failPool: Throwable? = null
    override suspend fun pool(level: JlptLevel): List<PoolItem> {
        poolCalls++
        poolGate?.await()
        failPool?.let { throw it }
        return items.filter { it.level == level }.map { it.toPoolItem() }
    }
    override suspend fun contents(ids: List<String>): Map<String, QuizContent> = items.filter { it.id in ids }.associateBy { it.id }
    override suspend fun examTemplate(level: JlptLevel): ExamTemplate? {
        templateCalls++
        failTemplate?.let { throw it }
        return templates[level]
    }
    override suspend fun examLevels(): Set<JlptLevel> = templates.keys
}

class FakeQuiz : QuizRepository {
    val attempts = LinkedHashMap<Long, QuizAttemptRecord>()
    val answerRows = mutableListOf<QuizAnswerRecord>()
    val finishCalls = mutableListOf<Pair<Long, Long>>()
    var failSave: Throwable? = null
    var failFinish: Throwable? = null
    var failStart: Throwable? = null
    var saveCalls = 0
    var startCalls = 0
    val progressCalls = mutableListOf<Triple<Long, Int, Long>>()
    var startGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** 실제 저장소처럼 examId가 있으면 다른 미완료 모의고사를 같은 시각으로 닫는다(AR-U14 A). */
    override suspend fun startAttempt(examId: String?, seed: Long, questionOrder: List<String>, startedAtEpochMs: Long): Long {
        startCalls++
        startGate?.await()
        failStart?.let { throw it }
        if (examId != null) attempts.replaceAll { _, a -> if (a.examId != null && a.finishedAtEpochMs == null) a.copy(finishedAtEpochMs = startedAtEpochMs) else a }
        val id = attempts.size + 1L
        attempts[id] = QuizAttemptRecord(id, examId, seed, questionOrder, 0, 0, startedAtEpochMs, null)
        return id
    }
    override suspend fun saveAnswer(answer: QuizAnswerRecord) {
        saveCalls++
        failSave?.let { throw it }
        answerRows.removeAll { it.attemptId == answer.attemptId && it.questionId == answer.questionId }
        answerRows += answer
    }
    override suspend fun saveProgress(attemptId: Long, currentIndex: Int, elapsedMs: Long) {
        progressCalls += Triple(attemptId, currentIndex, elapsedMs)
        if (attempts[attemptId]?.finishedAtEpochMs != null) return // 실제 저장소처럼 끝난 시도는 무시
        attempts[attemptId]?.let { attempts[attemptId] = it.copy(currentIndex = currentIndex, elapsedMs = elapsedMs) }
    }
    override suspend fun finish(attemptId: Long, finishedAtEpochMs: Long) {
        finishCalls += attemptId to finishedAtEpochMs
        failFinish?.let { throw it }
        attempts[attemptId]?.takeIf { it.finishedAtEpochMs == null }?.let { attempts[attemptId] = it.copy(finishedAtEpochMs = finishedAtEpochMs) }
    }
    override suspend fun attempt(attemptId: Long) = attempts[attemptId]
    override suspend fun answers(attemptId: Long) = answerRows.filter { it.attemptId == attemptId }
    override suspend fun unfinishedExam() = attempts.values.lastOrNull { it.examId != null && it.finishedAtEpochMs == null }
}

fun template(level: JlptLevel = JlptLevel.N5, count: Int = 2, minutes: Int = 20) = ExamTemplate(
    level.n,
    listOf(
        ExamSection("vocab", "언어지식(문자·어휘)", minutes, listOf(ExamPart(Section.VOCAB, "kanji-reading", count))),
        ExamSection("listening", "청해", 30, listOf(ExamPart(Section.LISTENING, "task", 3))),
    ),
)

// 화면 상태 픽스처
fun quizQuestion(
    c: QuizContent = qc("q-n5-vocab-0001"), answer: Int = 0, why: List<String?> = c.whyWrongKo,
) = QuizQuestionUi(
    c.id, c.section, c.type, c.target, Furigana.parse(c.stem), c.passage?.let(Furigana::parse), c.choices.map(Furigana::parse), answer,
    c.explanationKo, why, c.targets, c.provenance, c.needsReview,
)
