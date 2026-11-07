package com.jlptstudy.app.ui.listening

import androidx.lifecycle.ViewModelStore
import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.speech.FakeSpeaker
import com.jlptstudy.app.speech.SpeakerState
import com.jlptstudy.app.speech.SpeechLine
import com.jlptstudy.app.speech.Voice
import com.jlptstudy.app.ui.practice.FakeQuiz
import com.jlptstudy.app.ui.practice.FakeQuizContent
import com.jlptstudy.app.ui.practice.LinkGrammar
import com.jlptstudy.app.ui.practice.QuizResultStatus
import com.jlptstudy.app.ui.practice.QuizResultViewModel
import com.jlptstudy.app.ui.practice.TickClock
import com.jlptstudy.app.ui.review.MemStudy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListeningViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val content = FakeQuizContent(listOf(ls("ls-n5-0001"), ls("ls-n5-0002", type = "quick-response", question = null)))
    private val quiz = FakeQuiz()
    private val clock = TickClock()
    private val made = mutableListOf<FakeSpeaker>()
    private val study = MemStudy(UserSettings(onboardingDone = true))
    private var touch = false

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    @After fun tearDown() = Dispatchers.resetMain()

    private fun speakerFactory() = FakeSpeaker().also(made::add)

    private fun vm(
        attribution: AttributionRepository = attributionWith(), initial: SpeakerState? = null, repo: FakeQuizContent = content,
    ): ListeningViewModel {
        val id = runBlocking { quiz.startAttempt(null, 42L, repo.items.map { it.id }, clock.ms) }
        return ListeningViewModel(id, QuizSessionLoader(quiz, repo), quiz, study, attribution, clock, { speakerFactory().also { s -> initial?.let { s.mutable.value = it } } }) { touch }
    }

    private fun ListeningViewModel.answer(shown: Int = 0) { choose(shown); clock.ms += 400 }

    @Test fun `ViewModel마다 새 Speaker를 만들고 onCleared는 자기 인스턴스만 shutdown`() {
        val factory = { FakeSpeaker().also(made::add) }
        val loader = QuizSessionLoader(quiz, content)
        fun make() = ListeningViewModel(1L, loader, quiz, study, attributionWith(), clock, factory)
        val s1 = ViewModelStore().also { it.put("a", make()) }
        val s2 = ViewModelStore().also { it.put("a", make()) }
        assertEquals(2, made.size)
        assertNotSame(made[0], made[1])
        s1.clear()
        assertEquals(listOf(1, 0), made.map { it.shutdownCount })
        assertEquals(SpeakerState.Ready, made[1].state.value)
        s2.clear()
        assertEquals(listOf(1, 1), made.map { it.shutdownCount })
    }

    @Test fun `화면이 열려도 speak가 불리지 않고 첫 문항은 ANSWERING이다`() {
        val v = vm()
        val s = v.state.value
        assertEquals(ListeningStatus.ANSWERING, s.status)
        assertEquals(2, s.total)
        assertTrue(made.single().spoken.isEmpty())
        assertEquals(3, s.question!!.lines!!.size)
        assertFalse(s.scriptOpen)
        assertFalse(s.scriptSeen)
        assertEquals(1.0f, s.rate)
    }

    @Test fun `speak에는 ttsText 우선 base 연결 문자열과 화자별 Voice가 넘어가고 question은 넘어가지 않는다`() {
        val v = vm()
        v.play()
        val call = made.single().spoken.single()
        assertEquals("ls-n5-0001", call.utteranceId)
        assertEquals(
            listOf(
                SpeechLine("男の人と女の人が話しています。", Voice.NARRATOR), SpeechLine("今日は何をしますか。", Voice.MALE),
                SpeechLine("としょかんへいきます。", Voice.FEMALE),
            ),
            call.lines,
        )
        assertTrue(call.lines.none { "何をしますか" in it.text && "女の人はこのあと" in it.text })
        assertEquals(1.0f, call.rate)
        assertEquals(SpeakerState.Playing("ls-n5-0001"), v.state.value.speaker)
    }

    @Test fun `속도 토글이 0 8배면 speak의 rate가 0 8f이고 값은 저장하지 않는다`() {
        val v = vm()
        v.toggleRate()
        assertEquals(0.8f, v.state.value.rate)
        v.play()
        assertEquals(0.8f, made.single().spoken.single().rate)
        v.toggleRate()
        assertEquals(1.0f, v.state.value.rate)
        assertEquals(1.0f, vm().state.value.rate) // 다시 열면 1.0
    }

    @Test fun `TalkBack이 켜져 있으면 재생 버튼 뒤 1000ms가 지나야 speak가 불리고 그 사이 화면은 재생 중이다`() {
        touch = true
        val v = vm()
        v.play()
        assertTrue(made.single().spoken.isEmpty())
        assertEquals(SpeakerState.Playing("ls-n5-0001"), v.state.value.speaker)
        scheduler.advanceTimeBy(999); scheduler.runCurrent()
        assertTrue(made.single().spoken.isEmpty())
        scheduler.advanceTimeBy(1); scheduler.runCurrent()
        assertEquals(1, made.single().spoken.size)
    }

    @Test fun `TalkBack 지연 중 정지하면 speak가 불리지 않는다`() {
        touch = true
        val v = vm()
        v.play()
        v.stopPlayback()
        scheduler.advanceTimeBy(2_000); scheduler.runCurrent()
        assertTrue(made.single().spoken.isEmpty())
        assertEquals(SpeakerState.Ready, v.state.value.speaker)
    }

    @Test fun `TalkBack이 꺼져 있으면 즉시 speak한다`() {
        touch = false
        vm().play()
        assertEquals(1, made.single().spoken.size)
    }

    @Test fun `Playing 동안 stop하면 Ready로 돌아온다`() {
        val v = vm()
        v.play()
        assertTrue(v.state.value.speaker is SpeakerState.Playing)
        v.stopPlayback()
        assertEquals(SpeakerState.Ready, v.state.value.speaker)
        v.onStop() // 앱이 백그라운드로: 이미 멈춰 있어도 stop은 불린다
        assertTrue(made.single().stopCount >= 2)
    }

    @Test fun `재생 중 답을 고르면 재생이 멈춘다`() {
        val v = vm()
        v.play()
        val before = made.single().stopCount
        v.choose(0)
        assertEquals(before + 1, made.single().stopCount)
        assertEquals(SpeakerState.Ready, v.state.value.speaker)
    }

    @Test fun `Error에서 다시 시도하면 지금 인스턴스를 shutdown하고 팩토리가 새 인스턴스를 만든다`() {
        val v = vm()
        made[0].mutable.value = SpeakerState.Error
        assertEquals(SpeakerState.Error, v.state.value.speaker)
        v.retrySpeaker()
        assertEquals(1, made[0].shutdownCount)
        assertEquals(2, made.size)
        assertEquals(SpeakerState.Ready, v.state.value.speaker)
        made[1].mutable.value = SpeakerState.NoEngine
        assertEquals(SpeakerState.NoEngine, v.state.value.speaker) // 새 인스턴스를 본다
        made[0].mutable.value = SpeakerState.Error // 옛 인스턴스의 변화는 무시
        assertEquals(SpeakerState.NoEngine, v.state.value.speaker)
    }

    @Test fun `ON_START에서 MissingJapaneseData NoEngine Error면 다시 만들고 Ready면 만들지 않는다`() {
        for (bad in listOf(SpeakerState.MissingJapaneseData, SpeakerState.NoEngine, SpeakerState.Error)) {
            made.clear()
            val v = vm()
            made[0].mutable.value = bad
            v.onStart()
            assertEquals("$bad", 2, made.size)
            assertEquals("$bad", 1, made[0].shutdownCount)
        }
        made.clear()
        val v = vm()
        v.onStart()
        assertEquals(1, made.size)
        made[0].mutable.value = SpeakerState.Initializing
        v.onStart()
        assertEquals("준비 중에는 다시 만들지 않는다", 1, made.size)
    }

    @Test fun `답하기 전에 대본을 펼쳤다 접고 답해도 script_shown은 1이다`() {
        val v = vm()
        v.toggleScript(); v.toggleScript()
        assertFalse(v.state.value.scriptOpen)
        assertTrue(v.state.value.scriptSeen)
        v.answer()
        assertTrue(quiz.answerRows.single().scriptShown)
    }

    @Test fun `한 번도 펼치지 않고 답하면 script_shown은 0이고 답한 뒤 펼친 것은 기록하지 않는다`() {
        val v = vm()
        v.answer()
        assertFalse(quiz.answerRows.single().scriptShown)
        v.toggleScript()
        assertFalse(v.state.value.scriptSeen)
        assertFalse(quiz.answerRows.single().scriptShown)
    }

    @Test fun `다음 문항으로 가면 scriptSeen이 false로 돌아간다`() {
        val v = vm()
        v.toggleScript(); v.answer()
        v.next()
        val s = v.state.value
        assertEquals(1, s.index)
        assertFalse(s.scriptSeen)
        assertFalse(s.scriptOpen)
        v.answer(2)
        assertEquals(listOf(true, false), quiz.answerRows.map { it.scriptShown })
    }

    @Test fun `listening_script_first가 켜져 있으면 대본이 처음부터 펼쳐지고 답은 대본으로 푼 것이다`() {
        study.settingsFlow.value = UserSettings(onboardingDone = true, listeningScriptFirst = true)
        val v = vm()
        assertTrue(v.state.value.scriptOpen)
        assertTrue(v.state.value.scriptSeen)
        v.answer()
        assertTrue(quiz.answerRows.single().scriptShown)
        v.next()
        assertTrue("다음 문항도 처음부터 펼친다", v.state.value.scriptOpen)
    }

    @Test fun `NoEngine이면 답하기 전에도 대본이 펼쳐지고 script_shown은 1이다 - 나중에 NoEngine이 되어도 같다`() {
        val a = vm(initial = SpeakerState.NoEngine)
        assertTrue(a.state.value.scriptOpen)
        a.answer()
        assertTrue(quiz.answerRows.single().scriptShown)
        quiz.answerRows.clear(); made.clear()
        val b = vm()
        assertFalse(b.state.value.scriptOpen)
        made[0].mutable.value = SpeakerState.NoEngine
        assertTrue(b.state.value.scriptOpen)
        assertTrue(b.state.value.scriptSeen)
    }

    @Test fun `MissingJapaneseData는 대본을 펼치지 않는다 - 대본 보기 버튼이 펼친다`() {
        val v = vm(initial = SpeakerState.MissingJapaneseData)
        assertFalse(v.state.value.scriptOpen)
        v.openScript()
        assertTrue(v.state.value.scriptOpen)
        v.openScript()
        assertTrue("버튼은 펼치기만 한다", v.state.value.scriptOpen)
        assertTrue(v.state.value.scriptSeen)
    }

    @Test fun `question이 NULL인 문항은 stem이 빈 목록이다`() {
        val v = vm()
        v.answer(); v.next()
        assertTrue(v.state.value.question!!.stem.isEmpty())
        assertTrue(v.state.value.question!!.lines!!.isNotEmpty())
    }

    @Test fun `N-12가 있으면 audioNotice가 채워지고 없거나 읽기 실패면 null이다`() {
        assertNull(vm().state.value.audioNotice)
        assertEquals("N-12", vm(attributionWith(noticeN12())).state.value.audioNotice!!.id)
        val failing = object : AttributionRepository {
            override suspend fun load() = throw java.io.IOException("x")
            override suspend fun openSourceLicenses() = throw java.io.IOException("x")
        }
        assertNull(vm(failing).state.value.audioNotice)
    }

    @Test fun `설치나 설정 화면을 열지 못하면 externalOpenFailed이고 Speaker 상태가 바뀌면 지운다`() {
        val v = vm(initial = SpeakerState.MissingJapaneseData)
        v.externalOpened(false)
        assertTrue(v.state.value.externalOpenFailed)
        v.onStart()
        assertFalse(v.state.value.externalOpenFailed)
    }

    @Test fun `마지막 문항 뒤 다음이면 finish 후 SUBMITTED이고 결과의 scriptShown이 답할 때의 scriptOpen과 같다`() {
        val v = vm()
        val id = v.state.value.attemptId
        v.toggleScript(); v.answer(0) // 대본을 펼쳐 푼 문항
        v.next()
        v.answer(1) // 펼치지 않은 문항
        assertEquals(ListeningStatus.FEEDBACK, v.state.value.status)
        v.next()
        assertEquals(ListeningStatus.SUBMITTED, v.state.value.status)
        assertEquals(listOf(id), quiz.finishCalls.map { it.first })
        val r = QuizResultViewModel(id, QuizSessionLoader(quiz, content), FakeContentRepository, LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle()).state.value
        assertEquals(QuizResultStatus.LOADED, r.status)
        assertEquals(QuizMode.LISTENING, r.mode)
        assertEquals(listOf(true, false), r.items.map { it.scriptShown })
        assertEquals(1, r.scriptShownCount)
    }

    @Test fun `다음은 답 직후 300ms 안에는 무시한다`() {
        val v = vm()
        v.choose(0)
        v.next()
        assertEquals(0, v.state.value.index)
        clock.ms += 400
        v.next()
        assertEquals(1, v.state.value.index)
    }

    @Test fun `saveAnswer 예외면 saveFailed이고 다시 시도는 같은 답과 script_shown을 저장한다`() {
        val v = vm()
        v.toggleScript()
        quiz.failSave = java.io.IOException("x")
        v.choose(1)
        assertTrue(v.state.value.saveFailed)
        assertEquals(ListeningStatus.ANSWERING, v.state.value.status)
        quiz.failSave = null
        v.retry()
        assertEquals(ListeningStatus.FEEDBACK, v.state.value.status)
        assertTrue(quiz.answerRows.single().scriptShown)
    }

    @Test fun `뒤로 나가면 정지하고 finish 후 onDone을 한 번만 부른다`() {
        val v = vm()
        v.play()
        var done = 0
        v.exit { done++ }
        v.exit { done++ }
        assertEquals(1, done)
        assertEquals(1, quiz.finishCalls.size)
        assertEquals(SpeakerState.Ready, v.state.value.speaker)
        assertTrue(made.single().stopCount >= 1)
    }

    @Test fun `남은 문항이 없으면 EMPTY이다`() {
        val v = vm(repo = FakeQuizContent(emptyList()))
        assertEquals(ListeningStatus.EMPTY, v.state.value.status)
    }
}
