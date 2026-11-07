package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.toPoolItem
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.quiz.ExamPart
import com.jlptstudy.quiz.ExamSection
import com.jlptstudy.quiz.ExamTemplate
import com.jlptstudy.quiz.Quiz
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** M19c: S14 시작 화면(ExamStartViewModel)·모의고사 진행(QuizViewModel mode EXAM)·S12 confirmReplace. 가짜 Clock. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExamViewModelTest {
    private val vocab = (1..3).map { qc("q-n5-vocab-000$it", choices = listOf("か$it", "き$it", "く$it", "け$it")) }
    private val grammar = qc("q-n5-grammar-0001", section = Section.GRAMMAR, type = "grammar-form", target = null, choices = listOf("が", "を", "に", "で"), answer = 2)
    private val reading = qc("q-n5-reading-0001", section = Section.READING, type = "short", target = null, choices = listOf("一", "二", "三", "四"), answer = 1)
    private val listening = (1..2).map { qc("ls-n5-000$it", section = Section.LISTENING, type = "task", target = null) }
    private val tpl = ExamTemplate(
        5,
        listOf(
            ExamSection("vocab", "언어지식(문자·어휘)", 20, listOf(ExamPart(Section.VOCAB, "kanji-reading", 2))),
            ExamSection("grammar-reading", "언어지식(문법)·독해", 40, listOf(ExamPart(Section.GRAMMAR, "grammar-form", 1), ExamPart(Section.READING, "short", 1))),
            ExamSection("listening", "청해", 30, listOf(ExamPart(Section.LISTENING, "task", 2))),
        ),
    )
    private val content = FakeQuizContent(vocab + grammar + reading + listening, mapOf(JlptLevel.N5 to tpl))
    private val quiz = FakeQuiz()
    private val clock = TickClock()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun start() = ExamStartViewModel(JlptLevel.N5, content, quiz, clock)
    private fun quizVm(id: Long) = QuizViewModel(id, QuizSessionLoader(quiz, content), quiz, MemStudy(), clock)
    private val examIds = listOf(vocab[0].id, vocab[1].id, grammar.id, reading.id)
    private fun examAttempt(order: List<String> = examIds) = runBlocking { quiz.startAttempt("exam-n5", 42L, order, clock.ms) }

    // ---- S14 시작 화면 ----

    @Test fun `시작 화면은 청해 영역을 뺀 영역 줄과 합계·기준 시간을 보이고 시작 전에는 시도 행이 없다`() {
        val s = start().state.value
        assertEquals(ExamStartStatus.LOADED, s.status)
        assertEquals(listOf(ExamSectionLine("언어지식(문자·어휘)", 2, 20), ExamSectionLine("언어지식(문법)·독해", 2, 40)), s.sections)
        assertEquals(4, s.total)
        assertEquals(s.sections.sumOf { it.count }, s.total)
        assertEquals(60 * 60_000L, s.limitMs)
        assertFalse(s.hasShortfall)
        assertTrue("시작 전에는 시도 행이 없다", quiz.attempts.isEmpty())
    }

    @Test fun `풀이 모자라면 count는 실제 고른 수이고 hasShortfall이다`() {
        content.items = vocab.take(1) + grammar + listening
        val s = start().state.value
        assertEquals(listOf(1, 1), s.sections.map { it.count })
        assertEquals(2, s.total)
        assertTrue(s.hasShortfall)
        assertEquals(60 * 60_000L, s.limitMs)
        val v = start()
        v.start()
        assertEquals("모자라면 시작하지 않는다", 0, quiz.startCalls)
        assertNull(v.state.value.startedAttemptId)
    }

    @Test fun `시작을 두 번 빠르게 눌러도 startAttempt는 한 번이고 그 seed·questionIds 시도로 간다`() {
        val loadedAt = clock.ms
        val v = start()
        clock.ms += 5_000
        quiz.startGate = CompletableDeferred()
        v.start()
        assertTrue(v.state.value.starting)
        v.start()
        quiz.startGate!!.complete(Unit)
        assertEquals(1, quiz.startCalls)
        // 이동할 시도는 상태로 나간다(화면 콜백을 보관하지 않아 회전 뒤 새 화면이 이어받는다). 소비하면 비운다
        val id = v.state.value.startedAttemptId!!
        val a = quiz.attempts[id]!!
        v.startedConsumed()
        assertNull(v.state.value.startedAttemptId)
        assertEquals("exam-n5", a.examId)
        assertEquals(loadedAt, a.seed)
        val expected = Quiz.buildSet(content.items.map { it.toPoolItem() }, tpl, loadedAt, excluded = setOf(Section.LISTENING)).questionIds
        assertEquals(expected, a.questionOrder)
        assertTrue("청해 문항이 없다", a.questionOrder.none { it.startsWith("ls-") })
    }

    @Test fun `템플릿이 없으면 EMPTY이다`() {
        content.templates = emptyMap()
        assertEquals(ExamStartStatus.EMPTY, start().state.value.status)
    }

    @Test fun `템플릿·풀 예외면 FAILED이고 다시 시도는 다시 불러 LOADED가 되며 startAttempt는 부르지 않는다`() {
        content.failTemplate = IllegalStateException("template_json 깨짐")
        val v = start()
        assertEquals(ExamStartStatus.FAILED, v.state.value.status)
        assertFalse(v.state.value.startFailed)
        assertTrue(quiz.attempts.isEmpty())
        content.failTemplate = null
        content.failPool = java.io.IOException("x")
        v.load()
        assertEquals(ExamStartStatus.FAILED, v.state.value.status)
        content.failPool = null
        v.load()
        assertEquals(ExamStartStatus.LOADED, v.state.value.status)
        assertEquals(3, content.templateCalls)
        assertEquals(2, content.poolCalls)
        assertEquals(4, v.state.value.total)
        assertEquals(0, quiz.startCalls)
        assertTrue(quiz.attempts.isEmpty())
    }

    @Test fun `startAttempt 예외면 LOADED 그대로 startFailed이고 다시 시도는 같은 seed·questionIds로 startAttempt만 부른다`() {
        val loadedAt = clock.ms
        val v = start()
        quiz.failStart = java.io.IOException("x")
        v.start()
        assertEquals(ExamStartStatus.LOADED, v.state.value.status)
        assertNull(v.state.value.startedAttemptId)
        assertTrue(v.state.value.startFailed)
        assertFalse(v.state.value.starting)
        assertTrue(quiz.attempts.isEmpty())
        clock.ms += 60_000
        quiz.failStart = null
        v.retryStart()
        assertEquals(2, quiz.startCalls)
        assertEquals(1, content.templateCalls)
        val a = quiz.attempts[v.state.value.startedAttemptId!!]!!
        assertEquals(loadedAt, a.seed)
        assertEquals(Quiz.buildSet(content.items.map { it.toPoolItem() }, tpl, loadedAt, excluded = setOf(Section.LISTENING)).questionIds, a.questionOrder)
    }

    // ---- 진행(QuizViewModel, EXAM) ----

    @Test fun `limitMs는 청해 제외 minutes 합이고 넘어도 자동 제출하지 않으며 sectionStarts는 영역마다 첫 순번이다`() {
        val v = quizVm(examAttempt())
        v.onStart()
        val s = v.state.value
        assertEquals(QuizMode.EXAM, s.mode)
        assertEquals(60 * 60_000L, s.limitMs)
        assertEquals(listOf(0, 2), s.sectionStarts)
        assertEquals(listOf("언어지식(문자·어휘)", "언어지식(문법)·독해"), s.sectionTitles)
        clock.ms += 60 * 60_000L + 1_000
        v.tick()
        assertTrue(v.state.value.elapsedMs > s.limitMs!!)
        assertEquals(QuizStatus.ANSWERING, v.state.value.status)
        assertTrue(quiz.finishCalls.isEmpty())
    }

    @Test fun `피드백 없이 선택만 저장하고 제출 전까지 바꿀 수 있다`() {
        val v = quizVm(examAttempt())
        v.choose(1)
        assertEquals(QuizStatus.ANSWERING, v.state.value.status)
        assertEquals(1, v.state.value.chosenIndex)
        v.choose(1)
        assertEquals("같은 선택지는 다시 저장하지 않는다", 1, quiz.saveCalls)
        v.choose(3)
        assertEquals(2, quiz.saveCalls)
        assertEquals(Quiz.shuffle(vocab[0].toQuestion(), 42L).toOriginal(3), quiz.answerRows.single().chosenIndex)
        assertEquals(3, v.state.value.unansweredCount)
    }

    @Test fun `다시 만든 ViewModel은 같은 문항·선택지 순서와 current_index·elapsed_ms에서 잇고 고른 답을 보인다`() {
        val id = examAttempt()
        val first = quizVm(id)
        first.onStart()
        val questions = (0 until 4).map { first.goTo(it); first.state.value.question!! }
        first.goTo(0)
        first.choose(2)
        first.goTo(3); first.choose(0)
        first.goTo(2)
        clock.ms += 95_000
        first.onStop()

        val again = quizVm(id)
        again.onStart()
        val s = again.state.value
        assertEquals(2, s.index)
        assertEquals(95_000L, s.elapsedMs)
        assertEquals(setOf(0, 3), s.answeredIndices)
        assertEquals(2, s.unansweredCount)
        assertEquals(questions[2], s.question)
        again.goTo(0)
        assertEquals(questions[0], again.state.value.question) // 같은 문항·같은 선택지 순서
        assertEquals(2, again.state.value.chosenIndex)
        val row = quiz.answerRows.first { it.questionId == vocab[0].id }
        assertEquals(Quiz.shuffle(vocab[0].toQuestion(), 42L).toShown(row.chosenIndex), again.state.value.chosenIndex)
        again.goTo(3)
        assertEquals(0, again.state.value.chosenIndex)
    }

    @Test fun `ON_STOP 뒤 10분이 지나고 ON_START해도 시간이 늘지 않고 ON_STOP마다 saveProgress가 불린다`() {
        val id = examAttempt()
        val v = quizVm(id)
        v.onStart()
        clock.ms += 5_000
        v.onStop()
        assertEquals(Triple(id, 0, 5_000L), quiz.progressCalls.last())
        clock.ms += 10 * 60_000L
        v.onStart()
        assertEquals(5_000L, v.state.value.elapsedMs)
        clock.ms += 1_000
        v.tick()
        assertEquals(6_000L, v.state.value.elapsedMs)
        v.onStop()
        assertEquals(2, quiz.progressCalls.count { it.first == id })
        assertEquals(6_000L, quiz.attempts[id]!!.elapsedMs)
    }

    @Test fun `미응답이 있으면 제출은 CONFIRM_SUBMIT이고 돌아가서 풀기는 finish 없이 첫 미응답으로 그대로 제출은 SUBMITTED`() {
        val v = quizVm(examAttempt())
        v.choose(0)
        v.goTo(1)
        v.requestSubmit()
        assertEquals(QuizStatus.CONFIRM_SUBMIT, v.state.value.status)
        assertEquals(3, v.state.value.unansweredCount)
        v.cancelSubmit()
        assertEquals(QuizStatus.ANSWERING, v.state.value.status)
        assertEquals(1, v.state.value.index)
        assertTrue(quiz.finishCalls.isEmpty())
        v.requestSubmit()
        v.confirmSubmit()
        assertEquals(QuizStatus.SUBMITTED, v.state.value.status)
        assertEquals(1, quiz.finishCalls.size)
    }

    @Test fun `모두 답했으면 대화상자 없이 SUBMITTED이고 걸린 시간이 저장된다`() {
        val id = examAttempt()
        val v = quizVm(id)
        v.onStart()
        (0 until 4).forEach { v.goTo(it); v.choose(0) }
        clock.ms += 30_000
        v.requestSubmit()
        assertEquals(QuizStatus.SUBMITTED, v.state.value.status)
        assertEquals(0, v.state.value.unansweredCount)
        assertEquals(30_000L, quiz.attempts[id]!!.elapsedMs)
        assertNotNull(quiz.attempts[id]!!.finishedAtEpochMs)
    }

    @Test fun `제출 finish가 실패하면 saveFailed·finishFailed이고 다시 시도는 같은 제출이다`() {
        val v = quizVm(examAttempt())
        (0 until 4).forEach { v.goTo(it); v.choose(0) }
        quiz.failFinish = java.io.IOException("x")
        v.requestSubmit()
        assertTrue(v.state.value.saveFailed && v.state.value.finishFailed)
        assertEquals(QuizStatus.ANSWERING, v.state.value.status)
        quiz.failFinish = null
        v.retry()
        assertEquals(QuizStatus.SUBMITTED, v.state.value.status)
    }

    @Test fun `선택 저장이 실패하면 저장될 때까지 다른 문항으로 가지 않는다`() {
        val v = quizVm(examAttempt())
        quiz.failSave = java.io.IOException("x")
        v.choose(1)
        assertTrue(v.state.value.saveFailed)
        v.goTo(1)
        assertEquals(0, v.state.value.index)
        quiz.failSave = null
        v.retry()
        v.goTo(1)
        assertEquals(1, v.state.value.index)
        assertEquals(setOf(0), v.state.value.answeredIndices)
    }

    @Test fun `뒤로·닫기는 CONFIRM_EXIT이고 계속 풀기는 ANSWERING, 멈추고 나가기는 saveProgress 뒤 S12이며 미완료로 남는다`() {
        val id = examAttempt()
        val v = quizVm(id)
        v.onStart()
        var left = 0
        v.exit { left++ }
        assertEquals(QuizStatus.CONFIRM_EXIT, v.state.value.status)
        assertEquals(0, left)
        v.continueExam()
        assertEquals(QuizStatus.ANSWERING, v.state.value.status)
        v.goTo(2)
        clock.ms += 12_000
        v.exit { left++ }
        val before = quiz.progressCalls.size
        v.stopAndExit { left++ }
        assertEquals(1, left)
        assertEquals(before + 1, quiz.progressCalls.size)
        assertEquals(Triple(id, 2, 12_000L), quiz.progressCalls.last())
        assertTrue(quiz.finishCalls.isEmpty())
        assertEquals(id, runBlocking { quiz.unfinishedExam() }!!.id)
        clock.ms += 60_000
        v.onStop() // 화면이 내려간 뒤에도 시간은 늘지 않는다
        assertEquals(12_000L, quiz.attempts[id]!!.elapsedMs)
    }

    @Test fun `콘텐츠에서 빠진 ID는 건너뛰고 total·unansweredCount에서 빠지며 그 자리를 가리키면 다음 남은 문항에서 잇는다`() {
        val id = examAttempt(listOf(vocab[0].id, "q-n5-vocab-gone", vocab[1].id, grammar.id))
        runBlocking { quiz.saveProgress(id, 1, 0) }
        val s = quizVm(id).state.value
        assertEquals(3, s.total)
        assertEquals(3, s.unansweredCount)
        assertEquals(1, s.index)
        assertEquals(vocab[1].id, s.question!!.id)
    }

    @Test fun `재개할 문항이 하나도 없으면 EMPTY이고 그 시도를 닫는다`() {
        val id = examAttempt(listOf("q-n5-vocab-gone"))
        val s = quizVm(id).state.value
        assertEquals(QuizStatus.EMPTY, s.status)
        assertEquals(QuizMode.EXAM, s.mode)
        assertNull(runBlocking { quiz.unfinishedExam() })
    }

    // ---- S12 confirmReplace(AR-U14 A) ----

    @Test fun `미완료 모의고사가 있으면 시작은 confirmReplace만 띄우고 새로 시작은 시작 화면의 시작에서만 이전 시도를 닫는다`() = runBlocking {
        val old = quiz.startAttempt("exam-n5", 1L, examIds, 10L)
        quiz.saveAnswer(QuizAnswerRecord(old, examIds[0], 0, true, 11L))
        val baseline = quiz.startCalls
        val home = PracticeHomeViewModel(content, quiz, MemStudy(UserSettings(onboardingDone = true, targetLevels = setOf(JlptLevel.N5))), clock)
        val toStart = mutableListOf<JlptLevel>()
        home.requestStartExam { toStart += it }
        assertEquals(old, home.state.value.confirmReplace!!.attemptId)
        assertTrue(toStart.isEmpty())
        assertEquals(baseline, quiz.startCalls)

        home.cancelReplace() // 취소: 아무것도 부르지 않는다
        assertNull(home.state.value.confirmReplace)
        assertTrue(quiz.finishCalls.isEmpty())
        assertEquals(baseline, quiz.startCalls)

        home.requestStartExam { toStart += it }
        var resumed = 0L
        home.resumeFromReplace { resumed = it }
        assertEquals(old, resumed)
        assertNull(home.state.value.confirmReplace)

        home.requestStartExam { toStart += it }
        home.replaceWithNew { toStart += it }
        assertEquals(listOf(JlptLevel.N5), toStart)
        assertTrue("새로 시작은 finish를 부르지 않는다", quiz.finishCalls.isEmpty())
        assertEquals(baseline, quiz.startCalls)

        start() // 시작 화면을 열었다가 뒤로 가면 이전 시도는 그대로 미완료
        assertEquals(old, quiz.unfinishedExam()!!.id)

        clock.ms += 1_000
        val sv = start()
        sv.start()
        val started = sv.state.value.startedAttemptId
        assertEquals(baseline + 1, quiz.startCalls)
        assertEquals(clock.ms, quiz.attempts[old]!!.finishedAtEpochMs)
        assertEquals("이전 시도의 답 행이 남는다", 1, quiz.answers(old).size)
        assertEquals(started, quiz.unfinishedExam()!!.id)
    }

    @Test fun `미완료가 없으면 시작은 바로 시작 화면으로 간다`() {
        val home = PracticeHomeViewModel(content, quiz, MemStudy(UserSettings(onboardingDone = true, targetLevels = setOf(JlptLevel.N5))), clock)
        val toStart = mutableListOf<JlptLevel>()
        home.requestStartExam { toStart += it }
        assertEquals(listOf(JlptLevel.N5), toStart)
        assertNull(home.state.value.confirmReplace)
    }
}
