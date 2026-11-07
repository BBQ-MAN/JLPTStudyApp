package com.jlptstudy.app.ui.listening

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.practice.FakeQuizContent
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.settings.SettingsActions
import com.jlptstudy.app.ui.settings.SettingsScreen
import com.jlptstudy.app.ui.settings.SettingsUiState
import com.jlptstudy.app.ui.settings.SettingsViewModel
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class WithListening(q: QuizContentRepository) : ContentRepository by FakeContentRepository, QuizContentRepository by q

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xhdpi")
class ListeningSettingsTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private val noListening = FakeQuizContent(listOf(com.jlptstudy.app.ui.practice.qc("q-n5-vocab-0001")))
    private val withListening = FakeQuizContent(listOf(ls("ls-n5-0001")))

    @Test fun `청해 콘텐츠가 번들에 있을 때만 listeningAvailable이고 스위치 저장이 설정에 반영된다`() = runBlocking {
        val study = MemStudy(UserSettings(onboardingDone = true))
        val on = SettingsViewModel(study, WithListening(withListening))
        assertTrue(withTimeout(5_000) { on.state.first { !it.loading } }.listeningAvailable)
        on.setListeningScriptFirst(true)
        withTimeout(5_000) { on.state.first { it.listeningScriptFirst } }
        assertTrue(study.settingsFlow.value.listeningScriptFirst)
        val off = SettingsViewModel(MemStudy(), WithListening(noListening))
        assertFalse(withTimeout(5_000) { off.state.first { !it.loading } }.listeningAvailable)
        assertFalse(SettingsViewModel(MemStudy(), FakeContentRepository).let { withTimeout(5_000) { it.state.first { s -> !s.loading } } }.listeningAvailable)
    }

    @Test fun `설정 화면은 listeningAvailable일 때만 청해 대본 먼저 보기 행을 보인다`() {
        val events = mutableListOf<Boolean>()
        var state = SettingsUiState(loading = false, listeningAvailable = true)
        rule.setContent { JlptTheme { SettingsScreen(state, SnackbarHostState(), actions = SettingsActions(onListeningScriptFirst = { events += it })) } }
        rule.onNodeWithText("청해 대본 먼저 보기").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("청해 대본 먼저 보기").performClick()
        assertEquals(listOf(true), events)
    }

    @Test fun `청해가 없으면 행이 없다`() {
        rule.setContent { JlptTheme { SettingsScreen(SettingsUiState(loading = false), SnackbarHostState()) } }
        assertEquals(0, rule.onAllNodesWithText("청해 대본 먼저 보기").fetchSemanticsNodes().size)
    }
}
