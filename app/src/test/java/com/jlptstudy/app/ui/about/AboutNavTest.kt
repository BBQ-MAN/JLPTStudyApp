package com.jlptstudy.app.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jlptstudy.app.AppContainer
import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.FakeStudyRepository
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.attribution.AssetAttributionRepository
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.VocabDetail
import com.jlptstudy.app.nav.About
import com.jlptstudy.app.nav.AppRoot
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.review.MeaningUi
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AboutNavTest {
    @get:Rule val rule = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var nav: NavHostController

    private fun show(container: AppContainer) {
        container.start()
        rule.setContent { nav = rememberNavController(); JlptTheme { AppRoot(container, nav) } }
    }

    private fun awaitText(text: String) = rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private val meaning = MeaningUi("뜻 문장", "authored", "llm:model-x@2026-11-01", "unreviewed", null, null)
    private val content = object : ContentRepository by FakeContentRepository {
        override suspend fun vocabDetail(id: String) = VocabDetail(
            "食べる", "たべる", Furigana.fromHeadword("食べる", "たべる"), JlptLevel.N5, listOf("v1"), listOf(meaning), emptyList(),
        )
    }

    @Test fun `출처 칩을 누르면 하단 시트가 열리고 출처와 라이선스 보기는 About으로 간다`() {
        val container = AppContainer(
            scope = scope, openContent = { content }, openStudy = { FakeStudyRepository() },
            attributionRepository = AboutFixtures.repo(),
        )
        show(container)
        awaitText("오늘의 학습")
        rule.runOnUiThread { nav.navigate(com.jlptstudy.app.nav.VocabDetail("w1")) }
        awaitText(ProvenanceLabel.of(meaning))
        rule.onNodeWithText(ProvenanceLabel.of(meaning)).performClick()
        awaitText("이 뜻은 어떻게 만들었나요")
        rule.onNodeWithText("AI(model-x)가 작성했습니다.").assertIsDisplayed()
        rule.onNodeWithText("출처와 라이선스 보기").performClick()
        rule.waitUntil(10_000) { nav.currentDestination?.hasRoute(About::class) == true }
        rule.onNodeWithTag("about_screen").assertIsDisplayed()
    }

    @Test fun `콘텐츠 DB 실패 S90의 출처 보기는 LOADED로 자료 카드를 보이고 집계 줄이 없다`() {
        val container = AppContainer(
            scope = scope, openContent = { throw IOException("content.db") }, openStudy = { FakeStudyRepository() },
            attributionRepository = AssetAttributionRepository.create(RuntimeEnvironment.getApplication()),
        )
        show(container)
        awaitText("학습 콘텐츠를 열지 못했습니다")
        rule.onNodeWithText("출처 보기").performClick()
        val sourceName = AboutFixtures.bundled["sources"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content
        awaitText("JLPT 공식 자료가 아닙니다")
        rule.onNodeWithTag("about_list").performScrollToNode(androidx.compose.ui.test.hasText(sourceName))
        rule.onNodeWithText(sourceName).assertIsDisplayed()
        assertTrue(nav.currentDestination!!.hasRoute(About::class))
        assertEquals(0, rule.onAllNodesWithTag("review_counts").fetchSemanticsNodes().size)
    }
}
