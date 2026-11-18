package com.jlptstudy.app.ui.grammar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jlptstudy.app.AppContainer
import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.FakeStudyRepository
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.nav.AppRoot
import com.jlptstudy.app.nav.Grammar
import com.jlptstudy.app.ui.detail.DetailStatus
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GrammarScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private val events = mutableListOf<String>()
    private val listActions = GrammarListActions(
        onToggleLevel = { events += "lv:$it" }, onClearLevels = { events += "clear" }, onOpen = { events += "open:$it" }, onNotice = { events += "notice" },
    )

    @Test fun `문법 탭이 내비게이션에 보이고 누르면 S10으로 가며 0행이면 EMPTY이다`() {
        lateinit var nav: NavHostController
        val container = AppContainer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            openContent = { FakeContentRepository }, openStudy = { FakeStudyRepository() },
        )
        container.start()
        rule.setContent { nav = rememberNavController(); JlptTheme { AppRoot(container, nav) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("문법").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("문법").assertIsDisplayed()
        rule.onNodeWithText("문법").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("이 급수의 문법은 아직 준비 중입니다").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(nav.currentDestination!!.hasRoute(Grammar::class))
        rule.onNodeWithTag("grammar_list_screen").assertIsDisplayed()
        rule.onNodeWithText("다른 급수를 골라 보세요.").assertIsDisplayed()
        rule.onNodeWithText("필터 지우기").assertIsDisplayed()
    }

    @Test fun `목록 행 급수 칩 안내 링크가 동작한다`() {
        rule.setContent { JlptTheme { GrammarListScreen(grammarListState, listActions) } }
        rule.onNodeWithText("N5 문법 · 비공식").assertIsDisplayed()
        rule.onNodeWithText("〜てください").performClick()
        rule.onNodeWithTag("grammar_level_chip_N4").performClick()
        rule.onNodeWithText("자세히").performClick()
        assertEquals(listOf("open:gr-n5-0001", "lv:N4", "notice"), events)
    }

    @Test fun `상세는 검토 중 칩과 출처 칩과 접속 표와 비슷한 문법을 보인다`() {
        rule.setContent { JlptTheme { GrammarDetailScreen(grammarDetailState, GrammarDetailActions(onGrammar = { events += "g:$it" }, onNotice = { events += "notice" })) } }
        rule.onNodeWithText("설명 검토 중").assertIsDisplayed()
        rule.onNodeWithText("AI 작성 · 검수 전").assertIsDisplayed()
        rule.onNodeWithText("N5 · 비공식").assertIsDisplayed()
        rule.onNodeWithTag("grammar_connections").assertIsDisplayed()
        rule.onNodeWithText("품사").assertIsDisplayed() // 넓은 화면은 표(열 머리)
        rule.onNodeWithText("비슷한 문법").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("〜なさい").performScrollTo().performClick()
        assertEquals(listOf("g:gr-n5-0002"), events)
    }

    @Test fun `needsReview 0이면 검토 중 칩이 없고 contrasts가 비면 절이 없다`() {
        val d = sampleGrammarDetail.copy(needsReview = false, contrasts = emptyList())
        rule.setContent { JlptTheme { GrammarDetailScreen(GrammarDetailUiState(DetailStatus.LOADED, d)) } }
        rule.onNodeWithText("N5 · 비공식").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("설명 검토 중").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("비슷한 문법").fetchSemanticsNodes().size)
    }

    @Test fun `글꼴 200퍼센트면 접속 표가 세로 카드로 바뀐다`() {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 2f)) { JlptTheme { GrammarDetailScreen(grammarDetailState) } }
        }
        rule.onNodeWithTag("grammar_connections").performScrollTo()
        assertEquals("열 머리는 없고 카드마다 라벨이 있다", 2, rule.onAllNodesWithText("품사").fetchSemanticsNodes().size)
    }

    @Test fun `RETIRED 상세는 뒤로 버튼을 준다`() {
        rule.setContent { JlptTheme { GrammarDetailScreen(GrammarDetailUiState(DetailStatus.RETIRED), GrammarDetailActions(onBack = { events += "back" })) } }
        rule.onNodeWithText("더 이상 볼 수 없는 항목이에요").assertIsDisplayed()
        rule.onNodeWithText("뒤로").performClick()
        assertEquals(listOf("back"), events)
    }
}
