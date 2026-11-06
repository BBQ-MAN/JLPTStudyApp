package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.study.QuizAnswerRecord
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.quiz.Section
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PracticeHomeViewModelTest {
    private val content = FakeQuizContent()
    private val quiz = FakeQuiz()
    private val clock = TickClock()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg target: JlptLevel) =
        PracticeHomeViewModel(content, quiz, MemStudy(UserSettings(onboardingDone = true, targetLevels = target.toSet())), clock)

    private fun many(n: Int, level: JlptLevel = JlptLevel.N5, type: String = "kanji-reading", section: Section = Section.VOCAB) =
        (1..n).map { qc("q-${level.name.lowercase()}-${section.code}-${type.take(3)}$it", level, section, type) }

    @Test fun `count 0인 유형은 types에 없고 영역과 표 순서로 나온다`() {
        content.items = many(3, type = "usage") + many(2) + many(4, type = "grammar-form", section = Section.GRAMMAR)
        val s = vm().state.value
        assertEquals(PracticeHomeStatus.LOADED, s.status)
        assertEquals(listOf("kanji-reading" to 2, "usage" to 3, "grammar-form" to 4), s.types.map { it.type to it.count })
        assertTrue(s.types.none { it.count == 0 })
    }

    @Test fun `문항이 없거나 청해뿐이면 EMPTY이다`() {
        assertEquals(PracticeHomeStatus.EMPTY, vm().state.value.status)
        content.items = listOf(qc("ls-n5-0001", section = Section.LISTENING, type = "task"))
        val s = vm().state.value
        assertEquals(PracticeHomeStatus.EMPTY, s.status)
        assertTrue(s.types.isEmpty())
    }

    @Test fun `처음 급수는 학습 급수 중 문항이 있는 가장 쉬운 급수이고 없으면 문항이 있는 가장 쉬운 급수이다`() {
        content.items = many(1, JlptLevel.N4) + many(1, JlptLevel.N2)
        assertEquals(JlptLevel.N2, vm(JlptLevel.N3, JlptLevel.N2).state.value.level)
        assertEquals(JlptLevel.N4, vm(JlptLevel.N1).state.value.level)
        val v = vm()
        v.selectLevel(JlptLevel.N2)
        assertEquals(JlptLevel.N2, v.state.value.level)
        assertEquals(listOf(JlptLevel.N4, JlptLevel.N2), v.state.value.levels)
    }

    @Test fun `미완료 모의고사가 있으면 resume이 채워진다`() = runBlocking {
        content.items = many(3, JlptLevel.N4)
        val ids = content.items.map { it.id }
        val id = quiz.startAttempt("exam-n4", 1L, ids, 10L)
        quiz.saveAnswer(QuizAnswerRecord(id, ids[0], 0, true, 11L))
        quiz.saveProgress(id, 1, 65_000L)
        val s = vm().state.value
        assertEquals(ResumeExamUi(id, JlptLevel.N4, 1, 3, 65_000L), s.resume)
    }

    @Test fun `연습 시도는 resume에 잡히지 않는다`() = runBlocking {
        content.items = many(2)
        quiz.startAttempt(null, 1L, content.items.map { it.id }, 10L)
        assertNull(vm().state.value.resume)
    }

    @Test fun `포기하기는 확인해야만 finish하고 행은 남는다`() = runBlocking {
        content.items = many(2, JlptLevel.N4)
        val id = quiz.startAttempt("exam-n4", 1L, content.items.map { it.id }, 10L)
        quiz.saveAnswer(QuizAnswerRecord(id, content.items[0].id, 0, true, 11L))
        val v = vm()
        v.requestAbandon()
        assertTrue(v.state.value.confirmAbandon)
        assertTrue("대화상자만 떴다", quiz.finishCalls.isEmpty())
        v.cancelAbandon()
        assertFalse(v.state.value.confirmAbandon)
        assertTrue("취소하면 finish가 불리지 않는다", quiz.finishCalls.isEmpty())
        assertNotNull(v.state.value.resume)

        v.requestAbandon(); clock.ms += 5_000
        v.confirmAbandon()
        assertEquals(listOf(id to clock.ms), quiz.finishCalls)
        assertNull(v.state.value.resume)
        assertFalse(v.state.value.confirmAbandon)
        assertNotNull("시도 행이 남는다", quiz.attempt(id))
        assertEquals("답 행이 남는다", 1, quiz.answers(id).size)
    }

    @Test fun `포기 finish가 실패하면 resume은 그대로이고 abandonFailed이다`() = runBlocking {
        content.items = many(2, JlptLevel.N4)
        quiz.startAttempt("exam-n4", 1L, content.items.map { it.id }, 10L)
        val v = vm()
        quiz.failFinish = java.io.IOException("x")
        v.requestAbandon(); v.confirmAbandon()
        assertTrue(v.state.value.abandonFailed)
        assertNotNull(v.state.value.resume)
        assertFalse(v.state.value.confirmAbandon)
    }

    @Test fun `모의고사 카드는 청해를 뺀 문항 수와 시간이고 모자라면 hasShortfall이다`() {
        content.items = many(3)
        content.templates = mapOf(JlptLevel.N5 to template(count = 2, minutes = 20))
        assertEquals(ExamCardUi(JlptLevel.N5, 2, 20 * 60_000L, false), vm().state.value.exam)
        content.items = many(1)
        assertEquals(ExamCardUi(JlptLevel.N5, 1, 20 * 60_000L, true), vm().state.value.exam)
        content.templates = emptyMap()
        assertNull(vm().state.value.exam)
    }

    @Test fun `한 회 문항 수는 최소 10과 유형 문항 수이고 지시한 유형만 담는다`() = runBlocking {
        content.items = many(15) + many(4, type = "usage")
        val v = vm()
        var started = 0L
        v.startPractice(Section.VOCAB, "kanji-reading") { started = it }
        val a = quiz.attempt(started)!!
        assertEquals(PRACTICE_SIZE, a.questionOrder.size)
        assertNull(a.examId)
        assertTrue(a.questionOrder.all { it.contains("kan") })
        v.reload() // 성공한 시작은 화면이 돌아올 때(reload)까지 다시 시작하지 못한다
        v.startPractice(Section.VOCAB, "usage") { started = it }
        assertEquals(4, quiz.attempt(started)!!.questionOrder.size)
    }

    @Test fun `유형 행을 더블탭해도 시도는 하나이고 onStarted도 한 번이다`() = runBlocking {
        content.items = many(3)
        val v = vm()
        var started = 0
        v.startPractice(Section.VOCAB, "kanji-reading") { started++ }
        v.startPractice(Section.VOCAB, "kanji-reading") { started++ }
        assertEquals(1, started)
        assertEquals(1, quiz.attempts.size)
        v.reload()
        v.startPractice(Section.VOCAB, "kanji-reading") { started++ }
        assertEquals(2, quiz.attempts.size)
    }

    @Test fun `init과 ON_START의 reload가 겹쳐도 문항 풀은 급수마다 한 번만 읽는다`() = runBlocking {
        content.items = many(3)
        content.poolGate = kotlinx.coroutines.CompletableDeferred()
        val v = vm()
        v.reload()
        content.poolGate!!.complete(Unit)
        assertEquals(JlptLevel.entries.size, content.poolCalls)
        assertEquals(PracticeHomeStatus.LOADED, v.state.value.status)
    }

    @Test fun `시작이 실패하면 startFailed이고 다시 시도는 같은 유형을 시작한다`() = runBlocking {
        content.items = many(3)
        val v = vm()
        quiz.failStart = java.io.IOException("x")
        var started = 0L
        v.startPractice(Section.VOCAB, "kanji-reading") { started = it }
        assertTrue(v.state.value.startFailed)
        assertEquals(0L, started)
        quiz.failStart = null
        v.retryStart()
        assertFalse(v.state.value.startFailed)
        assertEquals(1L, started)
    }
}
