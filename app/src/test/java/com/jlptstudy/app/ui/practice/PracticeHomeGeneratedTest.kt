package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.quiz.gen.GenType
import kotlinx.coroutines.CompletableDeferred
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

/** S12 "자동 생성 연습"의 상태·시작(quizgen.md 6.3, ui-design 6.10). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PracticeHomeGeneratedTest {
    private val content = FakeQuizContent()
    private val quiz = FakeQuiz()
    private val gen = FakeQuizGen()
    private val clock = TickClock()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(vararg target: JlptLevel) =
        PracticeHomeViewModel(content, quiz, MemStudy(UserSettings(onboardingDone = true, targetLevels = target.toSet())), clock, gen)

    private fun fixed(n: Int, level: JlptLevel = JlptLevel.N5) = (1..n).map { qc("q-${level.name.lowercase()}-vocab-$it", level) }

    @Test fun `generated는 고른 급수의 GenType 순서 4행이고 단어 10개부터 활성이다`() {
        content.items = fixed(3)
        gen.targets = mapOf(JlptLevel.N5 to listOf(502, 9, 10, 0))
        val s = vm().state.value
        assertEquals(
            listOf(GeneratedTypeUi("kanji-reading", 502, true), GeneratedTypeUi("orthography", 9, false), GeneratedTypeUi("meaning-ja-ko", 10, true), GeneratedTypeUi("meaning-ko-ja", 0, false)),
            s.generated,
        )
        assertEquals(GenType.entries.map { it.code }, s.generated.map { it.type })
        assertEquals(10, GEN_MIN_TARGETS)
    }

    @Test fun `levels는 고정 문항 급수와 활성 유형이 있는 급수의 합집합이고 활성이 없는 급수는 들어오지 않는다`() {
        content.items = fixed(3)
        gen.targets = mapOf(JlptLevel.N5 to listOf(5, 5, 5, 5), JlptLevel.N4 to listOf(7, 245, 63, 6), JlptLevel.N3 to listOf(3, 3, 3, 3))
        val v = vm()
        assertEquals(listOf(JlptLevel.N5, JlptLevel.N4), v.state.value.levels)
        v.selectLevel(JlptLevel.N4)
        val s = v.state.value
        assertEquals(JlptLevel.N4, s.level)
        assertEquals(listOf(7, 245, 63, 6), s.generated.map { it.targets })
        assertEquals(listOf(false, true, true, false), s.generated.map { it.enabled })
        assertTrue("N4는 고정 문항이 없다", s.types.isEmpty())
        assertEquals("활성 행이 있으면 EMPTY가 아니다", PracticeHomeStatus.LOADED, s.status)
        v.selectLevel(JlptLevel.N3) // levels에 없는 급수는 무시
        assertEquals(JlptLevel.N4, v.state.value.level)
    }

    @Test fun `고정 문항이 없고 활성 유형도 없으면 EMPTY이고 생성 영역은 숨는다`() {
        gen.targets = mapOf(JlptLevel.N5 to listOf(0, 0, 0, 0))
        val s = vm().state.value
        assertEquals(PracticeHomeStatus.EMPTY, s.status)
        assertTrue("대상이 전부 0이면(생성 표가 없는 DB) 영역을 숨긴다", s.generated.isEmpty())
    }

    @Test fun `고정 문항만 있는 급수에서 생성 대상이 전부 0이면 generated는 비어 있다`() {
        content.items = fixed(2)
        val s = vm().state.value
        assertEquals(PracticeHomeStatus.LOADED, s.status)
        assertTrue(s.generated.isEmpty())
    }

    @Test fun `생성 가능 수를 읽지 못해도 고정 문항 화면은 그대로이다`() {
        content.items = fixed(2)
        gen.failAvailability = java.io.IOException("x")
        val s = vm().state.value
        assertEquals(PracticeHomeStatus.LOADED, s.status)
        assertTrue(s.generated.isEmpty())
        assertEquals(1, s.types.size)
    }

    @Test fun `gen 없이 만든 화면은 영역이 없다(기존 호출)`() {
        content.items = fixed(2)
        val s = PracticeHomeViewModel(content, quiz, MemStudy(), clock).state.value
        assertTrue(s.generated.isEmpty())
    }

    @Test fun `startGenerated는 gq ID 목록으로 시도 하나를 만들고 onStarted를 부른다`() = runBlocking {
        content.items = fixed(2)
        gen.targets = mapOf(JlptLevel.N5 to listOf(50, 50, 50, 50))
        val v = vm()
        var started = 0L
        v.startGenerated("meaning-ja-ko") { started = it }
        assertEquals(1, quiz.startCalls)
        val a = quiz.attempt(started)!!
        assertNull(a.examId)
        assertEquals(gen.ids, a.questionOrder)
        assertTrue(a.questionOrder.all { it.startsWith("gq:") })
        assertEquals(clock.ms, a.seed)
        assertEquals(listOf(Triple(JlptLevel.N5, GenType.MEANING_JA_KO, clock.ms)), gen.sessions)
    }

    @Test fun `연달아 두 번 눌러도 시도는 하나이고 reload 뒤에는 다시 시작한다`() = runBlocking {
        content.items = fixed(2)
        gen.targets = mapOf(JlptLevel.N5 to listOf(50, 50, 50, 50))
        gen.sessionGate = CompletableDeferred()
        val v = vm()
        var started = 0
        v.startGenerated("kanji-reading") { started++ }
        v.startGenerated("orthography") { started++ } // 잠금 중에는 다른 유형 행도 무시
        gen.sessionGate!!.complete(Unit)
        assertEquals(1, started)
        assertEquals(1, quiz.attempts.size)
        assertEquals(1, gen.sessions.size)
        v.reload()
        v.startGenerated("kanji-reading") { started++ }
        assertEquals(2, quiz.attempts.size)
    }

    @Test fun `newSession이 빈 목록이면 시도를 만들지 않고 startFailed이다`() = runBlocking {
        content.items = fixed(2)
        gen.targets = mapOf(JlptLevel.N5 to listOf(50, 50, 50, 50))
        gen.ids = emptyList()
        val v = vm()
        var started = 0
        v.startGenerated("kanji-reading") { started++ }
        assertTrue(v.state.value.startFailed)
        assertEquals(0, started)
        assertEquals(0, quiz.startCalls)
        // 잠금이 풀려 다시 시도할 수 있다
        gen.ids = listOf("gq:kanji-reading:0:a:jm:1")
        v.retryStart()
        assertFalse(v.state.value.startFailed)
        assertEquals(1, started)
    }

    @Test fun `startAttempt가 실패하면 startFailed이고 모르는 유형은 무시한다`() = runBlocking {
        content.items = fixed(2)
        gen.targets = mapOf(JlptLevel.N5 to listOf(50, 50, 50, 50))
        val v = vm()
        v.startGenerated("모름") { error("불리면 안 된다") }
        assertFalse(v.state.value.startFailed)
        quiz.failStart = java.io.IOException("x")
        v.startGenerated("kanji-reading") { error("불리면 안 된다") }
        assertTrue(v.state.value.startFailed)
    }
}
