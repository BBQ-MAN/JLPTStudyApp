package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.quiz.Quiz
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuizViewModelTest {
    private val content = FakeQuizContent(listOf(qc("q-n5-vocab-0001"), qc("q-n5-vocab-0002", target = "別", choices = listOf("a", "b", "c", "d"), answer = 2)))
    private val quiz = FakeQuiz()
    private val clock = TickClock()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(): QuizViewModel {
        val id = runBlocking { quiz.startAttempt(null, 42L, content.items.map { it.id }, clock.ms) }
        return QuizViewModel(id, QuizSessionLoader(quiz, content), quiz, MemStudy(), clock)
    }

    @Test fun `지시문의 대상은 문항 target이고 첫 문항이 ANSWERING이다`() {
        val v = vm()
        val s = v.state.value
        assertEquals(QuizStatus.ANSWERING, s.status)
        assertEquals("漢字", s.question!!.target)
        assertEquals(content.items[0].target, s.question!!.target)
        assertEquals(2, s.total)
        assertTrue(QuizTypeLabels.instruction(Section.VOCAB, "kanji-reading").contains(QuizTypeLabels.TARGET))
    }

    @Test fun `답을 고르면 saveAnswer가 끝난 뒤 FEEDBACK이고 원래 색인으로 저장한다`() {
        val v = vm()
        val q = v.state.value.question!!
        val shown = q.answerIndex
        v.choose(shown)
        val s = v.state.value
        assertEquals(QuizStatus.FEEDBACK, s.status)
        assertEquals(shown, s.chosenIndex)
        val row = quiz.answerRows.single()
        assertTrue(row.correct)
        assertEquals(content.items[0].answerIndex, row.chosenIndex) // 섞기 전 색인
        assertEquals(setOf(0), s.answeredIndices)
        v.choose((shown + 1) % 4)
        assertEquals("한 번 고르면 바꿀 수 없다", 1, quiz.saveCalls)
    }

    @Test fun `틀린 답은 correct false이고 선택지는 섞은 순서의 해설 이유와 짝이 맞는다`() {
        val v = vm()
        val q = v.state.value.question!!
        val wrong = (0..3).first { it != q.answerIndex }
        v.choose(wrong)
        assertFalse(quiz.answerRows.single().correct)
        // 섞은 뒤 색인의 이유 = 원래 색인의 이유
        val sh = Quiz.shuffle(content.items[0].toQuestion(), 42L)
        assertEquals(content.items[0].whyWrongKo[sh.toOriginal(wrong)], q.whyWrongKo[wrong])
        assertNull(q.whyWrongKo[q.answerIndex])
    }

    @Test fun `saveAnswer 예외면 saveFailed이고 다음으로 가지 않으며 다시 시도는 같은 답을 저장한다`() {
        val v = vm()
        quiz.failSave = java.io.IOException("x")
        v.choose(1)
        var s = v.state.value
        assertTrue(s.saveFailed)
        assertEquals(QuizStatus.ANSWERING, s.status)
        assertEquals(1, s.chosenIndex)
        clock.ms += 1_000
        v.next()
        assertEquals(0, v.state.value.index)
        quiz.failSave = null
        v.retry()
        s = v.state.value
        assertFalse(s.saveFailed)
        assertEquals(QuizStatus.FEEDBACK, s.status)
        assertEquals(2, quiz.saveCalls)
        assertEquals(Quiz.shuffle(content.items[0].toQuestion(), 42L).toOriginal(1), quiz.answerRows.single().chosenIndex)
    }

    @Test fun `답 직후 300ms는 다음을 무시하고 이후 다음 문항으로 가며 마지막은 finish 뒤 SUBMITTED이다`() {
        val v = vm()
        v.choose(0)
        v.next()
        assertEquals("가드", 0, v.state.value.index)
        clock.ms += 400
        v.next()
        assertEquals(1, v.state.value.index)
        assertEquals(QuizStatus.ANSWERING, v.state.value.status)
        assertNull(v.state.value.chosenIndex)
        v.choose(0)
        clock.ms += 400
        assertTrue(quiz.finishCalls.isEmpty())
        v.next()
        assertEquals(QuizStatus.SUBMITTED, v.state.value.status)
        assertEquals(1, quiz.finishCalls.size)
    }

    @Test fun `마지막 finish가 실패하면 saveFailed이고 다시 시도는 finish를 다시 부른다`() {
        val v = vm()
        v.choose(0); clock.ms += 400; v.next(); v.choose(0); clock.ms += 400
        quiz.failFinish = java.io.IOException("x")
        v.next()
        assertTrue(v.state.value.saveFailed)
        assertEquals(QuizStatus.FEEDBACK, v.state.value.status)
        quiz.failFinish = null
        v.retry()
        assertEquals(QuizStatus.SUBMITTED, v.state.value.status)
    }

    @Test fun `뒤로 닫기는 확인 없이 finish하고 S12로 가며 모의고사 미완료에 잡히지 않는다`() {
        val v = vm()
        v.choose(0)
        var left = false
        v.exit { left = true }
        assertTrue(left)
        assertEquals(1, quiz.finishCalls.size)
        assertEquals(v.state.value.attemptId, quiz.finishCalls.single().first)
        assertEquals(1, quiz.answerRows.size) // 답 행은 남는다
        assertNull(runBlocking { quiz.unfinishedExam() })
        assertTrue(runBlocking { quiz.attempt(1) }!!.finishedAtEpochMs != null)
    }

    @Test fun `닫기와 뒤로를 연달아 눌러도 onDone과 finish는 한 번이다`() {
        val v = vm()
        var left = 0
        v.exit { left++ }
        v.exit { left++ }
        assertEquals(1, left)
        assertEquals(1, quiz.finishCalls.size)
    }

    @Test fun `저장이 실패한 뒤에도 다른 선택지로 바꿀 수 없다`() {
        val v = vm()
        quiz.failSave = java.io.IOException("x")
        v.choose(1)
        v.choose(2)
        assertEquals(1, v.state.value.chosenIndex)
        assertEquals(1, quiz.saveCalls)
    }

    @Test fun `시험 시도를 열면 EXAM 모드로 ANSWERING이고 템플릿이 없어도 풀 수 있다`() {
        val id = runBlocking { quiz.startAttempt("exam-n5", 1L, content.items.map { it.id }, clock.ms) }
        val s = QuizViewModel(id, QuizSessionLoader(quiz, content), quiz, MemStudy(), clock).state.value
        assertEquals(QuizStatus.ANSWERING, s.status)
        assertEquals(com.jlptstudy.app.data.QuizMode.EXAM, s.mode)
        assertNull(s.limitMs)
    }

    @Test fun `finish가 실패해도 닫기는 나간다`() {
        val v = vm()
        quiz.failFinish = java.io.IOException("x")
        var left = false
        v.exit { left = true }
        assertTrue(left)
    }

    @Test fun `시도가 없거나 문항이 모두 빠지면 EMPTY이다`() {
        content.items = emptyList()
        assertEquals(QuizStatus.EMPTY, vm().state.value.status)
        val v = QuizViewModel(99, QuizSessionLoader(quiz, content), quiz, MemStudy(), clock)
        assertEquals(QuizStatus.EMPTY, v.state.value.status)
    }

    @Test fun `이미 답한 문항으로 다시 열리면 FEEDBACK으로 잇는다`() = runBlocking {
        val first = vm()
        first.choose(first.state.value.question!!.answerIndex)
        val again = QuizViewModel(first.state.value.attemptId, QuizSessionLoader(quiz, content), quiz, MemStudy(), clock)
        assertEquals(QuizStatus.FEEDBACK, again.state.value.status)
        assertEquals(first.state.value.chosenIndex, again.state.value.chosenIndex)
        assertEquals(JlptLevel.N5, content.items[0].level)
    }
}
