package com.jlptstudy.app.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import com.jlptstudy.app.AppContainer
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.study.RoomStudyRepository
import com.jlptstudy.app.data.study.StudyDatabase
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.data.study.UserSettings
import com.jlptstudy.app.ui.review.FakeContent
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.review.vocab
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 시작 라우트(S01/홈)와 홈·설정에서의 이동. 실제 AppRoot·NavHost, 저장소만 메모리/Room. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class OnboardingNavTest {
    @get:Rule val rule = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var nav: NavHostController
    private var db: StudyDatabase? = null
    private val generation = androidx.compose.runtime.mutableIntStateOf(0)

    @After fun tearDown() { db?.close() }

    private val content get() = FakeContent((1..20).map { vocab("w$it", "語", "ご") } + (1..3).map { vocab("n4_$it", "語", "ご", level = JlptLevel.N4) },
        metaLevels = setOf(JlptLevel.N5))

    private fun container(study: StudyRepository) = AppContainer(scope = scope, openContent = { content }, openStudy = { study }).also { it.start() }

    private fun show(container: AppContainer) {
        rule.setContent { androidx.compose.runtime.key(generation.intValue) { nav = rememberNavController(); JlptTheme { AppRoot(container, nav) } } }
    }

    /** Room 쓰기 뒤 viewModelScope(Main) 재개는 Robolectric 메인 루퍼를 직접 비워야 돈다. */
    private fun awaitUntil(cond: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!cond()) {
            check(System.nanoTime() < end) { "조건이 5초 안에 충족되지 않았다" }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.waitForIdle()
            Thread.sleep(20)
        }
    }
    private fun awaitText(text: String) = rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitTag(tag: String) = rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onAllNodesWithTag(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag))

    private fun roomStudy(): StudyRepository = runBlocking {
        db?.close()
        RoomStudyRepository(StudyDatabase.open(ApplicationProvider.getApplicationContext()).also { db = it }.studyDao())
    }

    @Test fun `onboarding_done 0이면 S01로 시작하고 시작을 누르면 저장하고 다음 실행은 홈으로 시작한다`() {
        val study = roomStudy()
        show(container(study))
        awaitText("어느 급수를 공부할까요?")
        assertTrue(nav.currentDestination!!.hasRoute(LevelSelect::class))
        rule.onAllNodesWithText("홈").assertNone() // 첫 실행 화면에는 하단 바가 없다
        awaitText("단어 20 · 한자 0") // N5 개수(levelCounts)
        rule.onNodeWithTag("level_row_N5").assertIsDisplayed()
        rule.onNodeWithContentDescription("하루 새 카드 늘리기").performClick() // 10 -> 15
        assertEquals(10, runBlocking { study.settings().first() }.newCardsPerDay) // "시작" 전에는 저장하지 않는다(ui-design 6.8)
        rule.onNodeWithText("시작").performClick()
        awaitUntil { nav.currentDestination?.hasRoute(Home::class) == true }
        val saved = runBlocking { study.settings().first() }
        assertEquals(setOf(JlptLevel.N5), saved.targetLevels); assertEquals(15, saved.newCardsPerDay); assertTrue(saved.onboardingDone)

        // 앱 재생성: 새 컨테이너는 온보딩이 필요 없다고 판단한다(아래 시험이 홈 시작 화면을 확인)
        val second = container(roomStudy())
        awaitUntil { second.state.value is com.jlptstudy.app.AppState.Ready }
        assertEquals(com.jlptstudy.app.AppState.Ready(needsOnboarding = false), second.state.value)
    }

    @Test fun `시작 저장 뒤 같은 컨테이너로 다시 열면 홈으로 시작한다`() {
        val c = container(roomStudy())
        show(c)
        awaitText("어느 급수를 공부할까요?")
        awaitText("단어 20 · 한자 0")
        rule.onNodeWithText("시작").performClick()
        awaitUntil { nav.currentDestination?.hasRoute(Home::class) == true }
        awaitUntil { c.state.value == com.jlptstudy.app.AppState.Ready(needsOnboarding = false) }
        rule.runOnIdle { generation.intValue++ } // Activity 재생성: 같은 컨테이너, 새 NavController
        awaitUntil { nav.currentDestination?.hasRoute(Home::class) == true }
    }

    @Test fun `저장된 온보딩이 있으면 홈으로 시작한다`() {
        val study = roomStudy()
        runBlocking { study.updateSettings { it.copy(onboardingDone = true) } }
        show(container(study))
        awaitText("오늘의 학습")
        assertTrue(nav.currentDestination!!.hasRoute(Home::class))
    }

    @Test fun `NO_LEVEL_SELECTED의 급수 고르기는 설정 탭을 열고 LevelPicker 시트가 열려 있다`() {
        show(container(MemStudy(UserSettings(targetLevels = emptySet(), onboardingDone = true))))
        awaitText("학습할 급수를 골라 주세요")
        rule.onNodeWithText("급수 고르기").performClick()
        awaitTag("level_picker_sheet")
        assertTrue(nav.currentDestination!!.hasRoute(Settings::class))
        assertTrue(nav.currentBackStackEntry!!.toRoute<Settings>().openLevelPicker)
        rule.onNodeWithText("저장").assertIsNotEnabled() // 급수 0개: 저장 비활성
    }

    @Test fun `설정 탭을 직접 누르면 시트가 닫혀 있고 홈 탭을 오간 뒤에도 그렇다`() {
        show(container(MemStudy(UserSettings(targetLevels = emptySet(), onboardingDone = true))))
        awaitText("학습할 급수를 골라 주세요")
        rule.onNodeWithText("설정").performClick()
        awaitTag("settings_screen")
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("level_picker_sheet").fetchSemanticsNodes().isEmpty())
        assertEquals(false, nav.currentBackStackEntry!!.toRoute<Settings>().openLevelPicker)
    }

    @Test fun `시트를 연 설정을 떠났다가 설정 탭을 누르면 시트는 닫혀 있다`() {
        show(container(MemStudy(UserSettings(targetLevels = emptySet(), onboardingDone = true))))
        awaitText("학습할 급수를 골라 주세요")
        rule.onNodeWithText("급수 고르기").performClick()
        awaitTag("level_picker_sheet")
        nav.navigate(Home) // 시트가 열린 채 홈 탭으로(시스템 뒤로·알림 등)
        awaitUntil { nav.currentDestination?.hasRoute(Home::class) == true }
        rule.onNodeWithText("설정").performClick()
        awaitTag("settings_screen")
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("level_picker_sheet").fetchSemanticsNodes().isEmpty())
    }

    @Test fun `설정 탭의 출처와 라이선스 한 번과 홈 상단 메뉴 정보는 About으로 간다`() {
        show(container(MemStudy(UserSettings(onboardingDone = true))))
        awaitText("오늘의 학습")
        rule.onNodeWithContentDescription("더보기").performClick()
        rule.onNodeWithText("정보").performClick()
        awaitUntil { nav.currentDestination?.hasRoute(About::class) == true }
        rule.onNodeWithTag("about_screen").assertIsDisplayed()
        assertEquals(null, nav.currentBackStackEntry!!.toRoute<About>().anchor)

        rule.runOnUiThread { nav.popBackStack() }
        awaitUntil { nav.currentDestination?.hasRoute(Home::class) == true }
        rule.onNodeWithText("설정").performClick()
        awaitText("출처와 라이선스")
        rule.onNodeWithTag("row_about").performScrollTo().performClick()
        awaitUntil { nav.currentDestination?.hasRoute(About::class) == true }
    }

    @Test fun `홈 학습 시작은 Review 라우트로 간다`() {
        show(container(MemStudy(UserSettings(onboardingDone = true))))
        awaitText("학습 시작")
        rule.onNodeWithText("학습 시작").performClick()
        awaitUntil { nav.currentDestination?.hasRoute(Review::class) == true }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertNone() =
    assertTrue("노드가 없어야 한다", fetchSemanticsNodes().isEmpty())

