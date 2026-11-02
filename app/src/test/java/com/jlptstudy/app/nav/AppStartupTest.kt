package com.jlptstudy.app.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jlptstudy.app.AppContainer
import com.jlptstudy.app.AppState
import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.FakeStudyRepository
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class AppStartupTest {
    @get:Rule val rule = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var nav: NavHostController

    private fun show(container: AppContainer) {
        rule.setContent { nav = rememberNavController(); JlptTheme { AppRoot(container, nav) } }
    }

    private fun awaitText(text: String) = rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun `Opening 동안 NavHost가 없고 300ms 뒤에 로딩 문구가 보인다`() {
        val gate = CompletableDeferred<Unit>()
        val container = AppContainer(
            scope = scope,
            openContent = { gate.await(); FakeContentRepository },
            openStudy = { FakeStudyRepository() },
        )
        container.start()
        rule.mainClock.autoAdvance = false
        show(container)
        rule.mainClock.advanceTimeBy(100)
        assertEquals(AppState.Opening, container.state.value)
        assertNull("NavHost가 없다", nav.currentBackStackEntry)
        rule.onNodeWithTag("opening_blank").assertIsDisplayed()
        rule.onAllNodesWithText("학습 콘텐츠를 준비하고 있습니다").assertCountEquals0()

        rule.mainClock.advanceTimeBy(150) // 250 ms
        rule.onAllNodesWithText("학습 콘텐츠를 준비하고 있습니다").assertCountEquals0()
        rule.mainClock.advanceTimeBy(100) // 350 ms
        rule.onNodeWithText("학습 콘텐츠를 준비하고 있습니다").assertIsDisplayed()
        rule.onNodeWithTag("status_screen").assertIsDisplayed()
        assertNull("여전히 NavHost가 없다", nav.currentBackStackEntry)

        gate.complete(Unit)
        rule.mainClock.autoAdvance = true
        awaitText("홈")
        assertTrue(container.state.value is AppState.Ready)
        assertTrue(nav.currentDestination!!.hasRoute(Home::class))
    }

    @Test fun `콘텐츠 DB 열기가 실패하면 ContentDbFailed이고 출처 보기는 About으로 간다`() {
        // 번들 에셋(M3부터 있음)은 정상으로 열리므로, 열기 실패는 던지는 openContent로 만든다.
        val container = AppContainer(scope = scope, openContent = { throw IOException("content.db") }, openStudy = { FakeStudyRepository() })
        container.start()
        show(container)
        awaitText("학습 콘텐츠를 열지 못했습니다")
        assertEquals(AppState.ContentDbFailed, container.state.value)
        rule.onNodeWithText("다시 시도").assertIsDisplayed()
        rule.onNodeWithText("앱 정보 열기").assertIsDisplayed()
        rule.onNodeWithText("출처 보기").assertIsDisplayed()
        rule.onAllNodesWithText("사전").assertCountEquals0() // 탭 목적지는 그리지 않는다
        assertTrue(nav.currentDestination!!.hasRoute(AppError::class))

        rule.onNodeWithText("출처 보기").performClick()
        rule.waitForIdle()
        assertTrue(nav.currentDestination!!.hasRoute(About::class))
        rule.onNodeWithTag("about_screen").assertIsDisplayed()
        rule.onAllNodesWithText("사전").assertCountEquals0()
    }

    @Test fun `앱 정보를 열 수 없으면 스낵바로 알린다`() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true) // 처리할 앱이 없으면 ActivityNotFoundException
        val container = AppContainer(scope = scope, openContent = { throw IOException("x") }, openStudy = { FakeStudyRepository() })
        container.start()
        show(container)
        awaitText("앱 정보 열기")
        rule.onNodeWithText("앱 정보 열기").performClick()
        awaitText("앱 정보를 열 수 없습니다")
        rule.onNodeWithText("앱 정보를 열 수 없습니다").assertIsDisplayed()
    }

    @Test fun `학습 기록 DB 열기 실패는 RecordDbFailed이고 기록 삭제 버튼이 없다`() {
        val container = AppContainer(
            scope = scope,
            openContent = { FakeContentRepository },
            openStudy = { throw IOException("corrupt") },
        )
        container.start()
        show(container)
        awaitText("학습 기록을 열지 못했습니다")
        assertEquals(AppState.RecordDbFailed, container.state.value)
        rule.onNodeWithText("다시 시도").assertIsDisplayed()
        rule.onNodeWithText("앱 정보 열기").assertIsDisplayed()
        rule.onAllNodesWithText("출처 보기").assertCountEquals0()
        rule.onAllNodesWithText("삭제", substring = true).assertCountEquals0()
        rule.onAllNodesWithText("초기화", substring = true).assertCountEquals0()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() =
    assertTrue("노드가 없어야 한다", fetchSemanticsNodes().isEmpty())
