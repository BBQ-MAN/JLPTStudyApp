package com.jlptstudy.app.ui.listening

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.jlptstudy.app.AppContainer
import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.FakeStudyRepository
import com.jlptstudy.app.R
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.nav.AppRoot
import com.jlptstudy.app.nav.Listening
import com.jlptstudy.app.speech.FakeSpeaker
import com.jlptstudy.app.speech.SpeakerState
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.practice.FakeQuiz
import com.jlptstudy.app.ui.practice.FakeQuizContent
import com.jlptstudy.app.ui.practice.TickClock
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class ListeningWorldContent(q: FakeQuizContent) : ContentRepository by FakeContentRepository, QuizContentRepository by q
private class ListeningWorldStudy(q: FakeQuiz) : StudyRepository by FakeStudyRepository(), QuizRepository by q

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class ListeningScreenUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val events = mutableListOf<String>()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private val ready = ListeningUiState(
        ListeningStatus.ANSWERING, attemptId = 1, index = 0, total = 3, question = listeningQuestionUi(), speaker = SpeakerState.Ready,
    )

    private fun desc(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    @Test fun `S12에 청해 영역이 있고 유형 행을 누르면 청해 문항 화면으로 간다`() {
        lateinit var nav: NavHostController
        val items = FakeQuizContent(listOf(ls("ls-n5-0001"), ls("ls-n5-0002"), com.jlptstudy.app.ui.practice.qc("q-n5-vocab-0001")))
        val container = AppContainer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            openContent = { ListeningWorldContent(items) }, openStudy = { ListeningWorldStudy(FakeQuiz()) },
            attributionRepository = attributionWith(noticeN12()), speakerFactory = { FakeSpeaker() },
        )
        container.start()
        rule.setContent { nav = rememberNavController(); JlptTheme { AppRoot(container, nav) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("연습").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("연습").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("practice_type_listening_task").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("청해").assertIsDisplayed()
        rule.onNodeWithTag("practice_type_listening_task").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("listening_screen").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(nav.currentDestination!!.hasRoute(Listening::class))
        // N-12가 있는 픽스처면 S16 아래에 고지 줄이 보인다
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("listening_audio_notice").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("listening_audio_notice").assertIsDisplayed()
        rule.onNodeWithTag("listening_audio_notice").assertTextContains("이 앱의 청해 음성은 기기의 음성 합성 엔진이 만든 것으로 실제 시험 녹음이 아닙니다.")
        // 자동 재생 없음
        rule.onNodeWithTag("listening_primary").assertIsEnabled()
        assertEquals("재생", desc("listening_primary"))
    }

    @Test fun `N-12가 없으면 고지 줄이 없다`() {
        rule.setContent { JlptTheme { ListeningScreen(ready) } }
        assertEquals(0, rule.onAllNodesWithTag("listening_audio_notice").fetchSemanticsNodes().size)
    }

    @Test fun `N-12가 있으면 고지 줄이 보이고 자세히가 알려진다`() {
        rule.setContent { JlptTheme { ListeningScreen(ready.copy(audioNotice = noticeN12()), actions = ListeningActions(onNotice = { events += "notice" })) } }
        rule.onNodeWithTag("listening_audio_notice").assertIsDisplayed()
        rule.onNodeWithText("자세히").performClick()
        assertEquals(listOf("notice"), events)
    }

    @Test fun `질문이 없는 문항은 질문 줄 노드가 없고 있는 문항은 있다`() {
        val noQ = listeningQuestionUi(ls("ls-n5-0002", question = null))
        var state by mutableStateOf(ready.copy(question = noQ))
        rule.setContent { JlptTheme { ListeningScreen(state) } }
        assertTrue(noQ.stem.isEmpty())
        assertEquals(0, rule.onAllNodesWithTag("listening_question").fetchSemanticsNodes().size)
        rule.onNodeWithTag("listening_playback").assertIsDisplayed()
        state = ready
        rule.waitForIdle()
        rule.onNodeWithTag("listening_question").assertIsDisplayed()
    }

    @Test fun `해설 카드에 listening 행 P로 만든 ProvenanceLabel 문구의 칩이 있다`() {
        val q = listeningQuestionUi()
        rule.setContent {
            JlptTheme { ListeningScreen(ready.copy(status = ListeningStatus.FEEDBACK, chosenIndex = 0, question = q)) }
        }
        rule.onNodeWithText(ProvenanceLabel.of(q.provenance!!), substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("listening_next").assertIsDisplayed()
    }

    @Test fun `Playing 동안 주 버튼은 정지이고 Ready면 재생이며 Error면 다시 시도이다`() {
        var state by mutableStateOf(ready.copy(speaker = SpeakerState.Playing("ls-n5-0001")))
        rule.setContent { JlptTheme { ListeningScreen(state, actions = ListeningActions(onStop = { events += "stop" }, onPlay = { events += "play" }, onRetrySpeaker = { events += "retry" })) } }
        assertEquals("정지", desc("listening_primary"))
        rule.onNodeWithText("재생 중").assertIsDisplayed()
        rule.onNodeWithTag("listening_primary").performClick()
        state = ready; rule.waitForIdle()
        assertEquals("재생", desc("listening_primary"))
        rule.onNodeWithTag("listening_primary").performClick()
        state = ready.copy(speaker = SpeakerState.Error); rule.waitForIdle()
        assertEquals("다시 시도", desc("listening_primary"))
        rule.onNodeWithTag("listening_primary").performClick()
        assertEquals(listOf("stop", "play", "retry"), events)
    }

    @Test fun `MissingJapaneseData면 설치 안내와 대본 보기 버튼이 보이고 주 버튼은 비활성이다`() {
        rule.setContent {
            JlptTheme {
                ListeningScreen(
                    ready.copy(speaker = SpeakerState.MissingJapaneseData),
                    actions = ListeningActions(onInstallVoice = { events += "install" }, onOpenScript = { events += "script" }),
                )
            }
        }
        rule.onNodeWithText(context.getString(R.string.listening_side_missing)).assertIsDisplayed()
        rule.onNodeWithTag("listening_primary").assertIsNotEnabled()
        rule.onNodeWithTag("listening_install").performClick()
        rule.onNodeWithTag("listening_script_button").performClick()
        assertEquals(listOf("install", "script"), events)
        assertEquals(0, rule.onAllNodesWithTag("listening_external_failed").fetchSemanticsNodes().size)
    }

    @Test fun `NoEngine이면 대본 보기와 음성 설정 열기가 보이고 열지 못하면 안내 문구가 나온다`() {
        var state by mutableStateOf(ready.copy(speaker = SpeakerState.NoEngine, scriptOpen = true))
        rule.setContent { JlptTheme { ListeningScreen(state, actions = ListeningActions(onOpenVoiceSettings = { events += "settings" })) } }
        rule.onNodeWithTag("listening_open_settings").performClick()
        rule.onNodeWithTag("listening_script_line_0").assertIsDisplayed()
        state = state.copy(externalOpenFailed = true); rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.listening_settings_failed)).assertIsDisplayed()
        assertEquals(listOf("settings"), events)
    }

    @Test fun `대본 머리 라벨이 접힘 펼침을 알리고 펼치면 화자 라벨과 본문이 보이며 ttsText는 보이지 않는다`() {
        var state by mutableStateOf(ready)
        rule.setContent { JlptTheme { ListeningScreen(state, actions = ListeningActions(onToggleScript = { events += "toggle"; state = state.copy(scriptOpen = !state.scriptOpen) })) } }
        rule.onNodeWithTag("listening_script_toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "접힘"))
        assertEquals(0, rule.onAllNodesWithTag("listening_script_line_0").fetchSemanticsNodes().size)
        rule.onNodeWithTag("listening_script_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("listening_script_toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "펼침"))
        // 줄 노드는 "{화자}, {본문}"으로 낭독된다(자식 글자는 숨김)
        rule.onNodeWithTag("listening_script_line_0").assertIsDisplayed().assertTextContains("내레이션, 男の人と女の人が話しています。")
        rule.onNodeWithTag("listening_script_line_1").assertTextContains("남자, 今日は何をしますか。")
        rule.onNodeWithTag("listening_script_line_2").assertTextContains("여자, 図書館へ行きます。")
        assertEquals(0, rule.onAllNodesWithText("としょかんへいきます。").fetchSemanticsNodes().size)
    }

    // --- 라이프사이클: 화면을 떠나거나 ON_STOP이면 Speaker.stop ---

    private fun route(speaker: FakeSpeaker): ListeningViewModel {
        val content = FakeQuizContent(listOf(ls("ls-n5-0001")))
        val quiz = FakeQuiz()
        val id = runBlocking { quiz.startAttempt(null, 1L, listOf("ls-n5-0001"), 1L) }
        return ListeningViewModel(id, QuizSessionLoader(quiz, content), quiz, MemStudy(), attributionWith(), TickClock(), { speaker })
    }

    @Test fun `재생 중 앱이 백그라운드(ON_STOP)로 가면 stop이 불린다`() {
        val sp = FakeSpeaker()
        val vm = route(sp)
        rule.setContent { JlptTheme { ListeningRoute(vm, onExit = {}, onResult = {}, onNotice = {}) } }
        rule.onNodeWithTag("listening_primary").performClick()
        assertTrue(sp.state.value is SpeakerState.Playing)
        val before = sp.stopCount
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(sp.stopCount > before)
        assertEquals(SpeakerState.Ready, sp.state.value)
    }

    @Test fun `재생 중 뒤로 가면 stop이 불리고 onExit이 한 번 불린다`() {
        val sp = FakeSpeaker()
        val vm = route(sp)
        var exits = 0
        rule.setContent { JlptTheme { ListeningRoute(vm, onExit = { exits++ }, onResult = {}, onNotice = {}) } }
        rule.onNodeWithTag("listening_primary").performClick()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { exits > 0 }
        assertEquals(1, exits)
        assertEquals(SpeakerState.Ready, sp.state.value)
        assertTrue(sp.stopCount >= 1)
    }

    @Test fun `재생 중 화면이 컴포지션에서 빠지면 stop이 불린다`() {
        val sp = FakeSpeaker()
        val vm = route(sp)
        var show by mutableStateOf(true)
        rule.setContent { JlptTheme { if (show) ListeningRoute(vm, onExit = {}, onResult = {}, onNotice = {}) } }
        rule.onNodeWithTag("listening_primary").performClick()
        assertTrue(sp.state.value is SpeakerState.Playing)
        show = false
        rule.waitForIdle()
        assertEquals(SpeakerState.Ready, sp.state.value)
    }
}
