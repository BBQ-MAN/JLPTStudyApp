package com.jlptstudy.app.ui.practice

import androidx.lifecycle.SavedStateHandle
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.toQuestion
import com.jlptstudy.app.nav.GrammarDetail
import com.jlptstudy.app.nav.KanjiDetail
import com.jlptstudy.app.nav.VocabDetail
import com.jlptstudy.app.nav.toRoute
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.quiz.AnswerInput
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
class QuizResultViewModelTest {
    private val seed = 42L
    private val quiz = FakeQuiz()
    private val clock = TickClock()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun qs(vararg targets: List<String>) = targets.mapIndexed { i, t ->
        qc("q-n5-vocab-%04d".format(i + 1), target = "語$i", choices = listOf("a$i", "b", "c", "d"), answer = i % 4, targets = t)
    }

    private fun attemptOf(content: FakeQuizContent, examId: String? = null, order: List<String> = content.items.map { it.id }): Long =
        runBlocking { quiz.startAttempt(examId, seed, order, clock.ms) }

    @Test fun `점수는 남은 문항의 AnswerInput으로 Quiz score를 부른 값과 같고 items는 보인 순서·섞은 뒤 색인이다`() {
        val content = FakeQuizContent(qs(emptyList(), emptyList(), emptyList()), mapOf(JlptLevel.N5 to template(count = 3)))
        val id = attemptOf(content, "exam-n5")
        // 1번 정답(원래 색인 0), 2번 오답, 3번은 답하지 않음
        quiz.answer(id, content.items[0].id, content.items[0].answerIndex, true, scriptShown = false)
        quiz.answer(id, content.items[1].id, (content.items[1].answerIndex + 1) % 4, false)
        val v = QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle())
        val s = v.state.value
        assertEquals(QuizResultStatus.LOADED, s.status)
        assertEquals(QuizMode.EXAM, s.mode)
        assertEquals(JlptLevel.N5, s.level)

        val expected = Quiz.score(
            content.items.mapIndexed { i, q ->
                AnswerInput(q.id, q.section, q.type, q.answerIndex, quiz.answerRows.firstOrNull { it.questionId == q.id }?.chosenIndex)
            },
            template(count = 3),
        )
        assertEquals(expected, s.score)
        assertEquals(1, s.score!!.correct)
        assertEquals(3, s.score!!.total)
        assertEquals(1, s.score!!.unanswered)
        assertEquals("언어지식(문자·어휘)", s.score!!.examSections.single().titleKo)

        assertEquals(content.items.map { it.id }, s.items.map { it.question.id })
        s.items.forEachIndexed { i, item ->
            val q = content.items[i]
            val sh = Quiz.shuffle(q.toQuestion(), seed)
            val row = quiz.answerRows.firstOrNull { it.questionId == q.id }
            assertEquals(row?.let { sh.toShown(it.chosenIndex) }, item.chosenIndex)
            assertEquals(row?.correct ?: false, item.correct)
            assertEquals(i, item.index)
        }
        assertNull(s.items[2].chosenIndex)
        assertEquals(0L, s.elapsedMs) // EXAM만 참고 표시
        assertEquals(20 * 60_000L, s.limitMs)
    }

    @Test fun `연습은 sections를 쓰고 시간은 보이지 않으며 유형과 급수를 낸다`() {
        val content = FakeQuizContent(qs(emptyList(), emptyList()))
        val id = attemptOf(content)
        quiz.answer(id, content.items[0].id, content.items[0].answerIndex, true)
        val s = QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle()).state.value
        assertEquals(QuizMode.PRACTICE, s.mode)
        assertEquals("kanji-reading", s.type)
        assertEquals(Section.VOCAB, s.score!!.sections.single().section)
        assertTrue(s.score!!.examSections.isEmpty())
        assertNull(s.elapsedMs)
        assertNull(s.limitMs)
    }

    @Test fun `빠진 문항은 retiredCount로 세고 남은 문항이 0이면 EMPTY다`() {
        val content = FakeQuizContent(qs(emptyList(), emptyList()))
        val id = attemptOf(content, order = listOf(content.items[0].id, "q-n5-vocab-9999", content.items[1].id))
        val s = QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle()).state.value
        assertEquals(1, s.retiredCount)
        assertEquals(2, s.items.size)
        assertEquals(2, s.score!!.total) // 분모는 남은 문항 기준

        val none = attemptOf(content, order = listOf("q-n5-vocab-9998", "q-n5-vocab-9999"))
        val e = QuizResultViewModel(none, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle()).state.value
        assertEquals(QuizResultStatus.EMPTY, e.status)
        assertEquals(2, e.retiredCount)
        assertTrue(e.items.isEmpty())
        assertNull(e.score)
    }

    @Test fun `시도가 없으면 EMPTY다`() {
        val s = QuizResultViewModel(99, QuizSessionLoader(quiz, FakeQuizContent()), LinkContent(), LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle()).state.value
        assertEquals(QuizResultStatus.EMPTY, s.status)
    }

    @Test fun `청해 답의 script_shown 수를 센다`() {
        val content = FakeQuizContent(qs(emptyList(), emptyList(), emptyList()))
        val id = attemptOf(content)
        quiz.answer(id, content.items[0].id, 0, true, scriptShown = true)
        quiz.answer(id, content.items[1].id, 1, false, scriptShown = true)
        quiz.answer(id, content.items[2].id, 2, false)
        val s = QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle()).state.value
        assertEquals(2, s.scriptShownCount)
        assertTrue(s.items[0].scriptShown)
        assertFalse(s.items[2].scriptShown)
    }

    @Test fun `틀림과 답하지 않음만 WRONG 필터에 남고 해설 이동은 그 안에서만 한다`() {
        val content = FakeQuizContent(qs(emptyList(), emptyList(), emptyList(), emptyList()))
        val id = attemptOf(content)
        quiz.answer(id, content.items[0].id, content.items[0].answerIndex, true)
        quiz.answer(id, content.items[1].id, (content.items[1].answerIndex + 1) % 4, false)
        // 2번(index 2)은 답하지 않음, 3번 정답
        quiz.answer(id, content.items[3].id, content.items[3].answerIndex, true)
        val v = QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle())
        assertEquals(listOf(1, 2), v.state.value.visibleItems.map { it.index })
        v.openReview(1)
        v.stepReview(-1)
        assertEquals(1, v.state.value.reviewIndex)
        v.stepReview(1)
        assertEquals(2, v.state.value.reviewIndex)
        v.stepReview(1)
        assertEquals(2, v.state.value.reviewIndex)
        v.closeReview()
        assertNull(v.state.value.reviewIndex)
        v.setFilter(ResultFilter.ALL)
        assertEquals(listOf(0, 1, 2, 3), v.state.value.visibleItems.map { it.index })
    }

    @Test fun `reviewIndex는 SavedStateHandle로 복원된다`() {
        val content = FakeQuizContent(qs(emptyList(), emptyList()))
        val id = attemptOf(content)
        val handle = SavedStateHandle()
        val first = QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), handle)
        first.openReview(1)
        val restored = QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), handle)
        assertEquals(1, restored.state.value.reviewIndex)
        restored.closeReview()
        assertNull(handle.get<Int>("reviewIndex"))
        // 범위 밖 값은 무시한다
        val bad = QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), SavedStateHandle(mapOf("reviewIndex" to 9)))
        assertNull(bad.state.value.reviewIndex)
    }

    @Test fun `targets의 jm·av·한자·gr ID는 각 상세 라우트로 이어지고 번들에 없는 ID는 빠진다`() {
        val targets = listOf("jm:1358280", "av-n5-0001", "駅", "gr-n5-0001", "jm:9999999", "gr-n5-9999", "鬱", "x", "駅")
        val content = FakeQuizContent(qs(targets))
        val id = attemptOf(content)
        val v = QuizResultViewModel(
            id, QuizSessionLoader(quiz, content), LinkContent(setOf("jm:1358280", "av-n5-0001"), setOf("駅")), LinkGrammar(setOf("gr-n5-0001")), MemStudy(), androidx.lifecycle.SavedStateHandle(),
        )
        assertTrue("해설을 열기 전에는 해소하지 않는다", v.state.value.targetLinks.isEmpty())
        v.openReview(0)
        val links = v.state.value.targetLinks.getValue(content.items[0].id)
        assertEquals(listOf("jm:1358280", "av-n5-0001", "駅", "gr-n5-0001"), links.map { it.id })
        assertEquals(listOf(TargetKind.VOCAB, TargetKind.VOCAB, TargetKind.KANJI, TargetKind.GRAMMAR), links.map { it.kind })
        assertEquals(
            listOf(VocabDetail("jm:1358280"), VocabDetail("av-n5-0001"), KanjiDetail("駅"), GrammarDetail("gr-n5-0001")),
            links.map { it.toRoute() },
        )
    }

    @Test fun `읽기 예외는 그 링크만 숨긴다`() {
        val content = FakeQuizContent(qs(listOf("jm:1", "駅")))
        val id = attemptOf(content)
        var fail = true
        val ok = LinkContent(vocab = setOf("jm:1"), kanji = setOf("駅"))
        val flaky = object : ContentRepository by ok {
            override suspend fun vocabDetail(id: String) = if (fail) throw java.io.IOException("x") else ok.vocabDetail(id)
        }
        val v = QuizResultViewModel(id, QuizSessionLoader(quiz, content), flaky, LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle())
        v.openReview(0)
        assertEquals(listOf("駅"), v.state.value.targetLinks.getValue(content.items[0].id).map { it.id })
        // 일시적 실패는 캐시하지 않는다: 다시 열면 복구된 링크가 나온다
        fail = false
        v.closeReview()
        v.openReview(0)
        assertEquals(listOf("jm:1", "駅"), v.state.value.targetLinks.getValue(content.items[0].id).map { it.id })
    }
}
