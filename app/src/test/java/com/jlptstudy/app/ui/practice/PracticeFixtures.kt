package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.QuizContent
import com.jlptstudy.app.data.content.GenTypeAvailability
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.content.QuizGenRepository
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
import com.jlptstudy.quiz.gen.GenType
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
    override suspend fun hasListening(): Boolean = items.any { it.section == Section.LISTENING }
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
    c.explanationKo, why, c.targets, c.provenance, c.needsReview, generated = c.generated,
)

/** 합성 자동 생성 문항(실제 콘텐츠가 아니다). reviewed 뜻 출처 칩은 provenance로, 없으면 null(칩 없음). */
val genProvenance = MeaningUi("선생님", "authored", "llm:test-model@2026-11-03", "reviewed", "2026-11-05", "agent")

fun gc(
    type: String, id: String = "gq:$type:0:a:jm:1", stem: String, target: String?, choices: List<String>, answer: Int = 0,
    why: List<String?> = listOf(null, "다른 단어의 읽기입니다.", "다른 단어의 읽기입니다.", "다른 단어의 읽기입니다."),
    provenance: MeaningUi? = null, explanation: String = "「橋」의 읽기는 「はし」입니다.", targets: List<String> = listOf("jm:1"),
) = QuizContent(
    id, JlptLevel.N5, Section.VOCAB, type, stem, null, target, null, choices, answer, explanation, why, targets, provenance, false, generated = true,
)

/** 자동 생성 저장소 가짜: 급수별 유형 단어 수와 세션 ID 목록을 정해 준다. */
class FakeQuizGen(
    var targets: Map<JlptLevel, List<Int>> = emptyMap(), // GenType 선언 순서의 단어 수
    var ids: List<String> = (1..10).map { "gq:kanji-reading:0:a:jm:$it" },
) : QuizGenRepository {
    var failAvailability: Throwable? = null
    val sessions = mutableListOf<Triple<JlptLevel, GenType, Long>>()
    var sessionGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override suspend fun availability(level: JlptLevel): List<GenTypeAvailability> {
        failAvailability?.let { throw it }
        val t = targets[level] ?: List(GenType.entries.size) { 0 }
        return GenType.entries.mapIndexed { i, g -> GenTypeAvailability(g, t[i]) }
    }
    override suspend fun newSession(level: JlptLevel, type: GenType, seed: Long, size: Int): List<String> {
        sessions += Triple(level, type, seed)
        sessionGate?.await()
        return ids
    }
    override suspend fun contents(ids: List<String>): Map<String, QuizContent> = emptyMap()
}

// 자동 생성 화면 픽스처(ui-design 12절): 한자 읽기 ① 칩 없음 / 뜻 고르기 ③ 한국어 선택지 중 하나에 일본어 구간 / 단어 고르기 ③ 한국어 지문
val genKanjiQ = quizQuestion(gc("kanji-reading", stem = "[[橋]]", target = "橋", choices = listOf("はし", "きょう", "はね", "かけ")))
val genJaKoQ = quizQuestion(
    gc(
        "meaning-ja-ko", stem = "[[{先生|せんせい}]]", target = "先生", choices = listOf("선생님", "〜さん보다 정중한 호칭", "의사", "학생"), answer = 0,
        why = listOf(null, "「〜さん」보다 정중한 호칭은 다른 단어의 뜻입니다.", "다른 단어의 뜻입니다.", "다른 단어의 뜻입니다."), provenance = genProvenance, explanation = "「先生」의 뜻은 「선생님」입니다.",
    ),
)
val genKoJaQ = quizQuestion(
    gc("meaning-ko-ja", stem = "선생님", target = null, choices = listOf("{先生|せんせい}", "{学生|がくせい}", "{医者|いしゃ}", "{友達|ともだち}"), provenance = genProvenance,
        explanation = "「先生」(せんせい)의 뜻은 「선생님」입니다."),
)

