package com.jlptstudy.app.ui.review

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.theme.JlptTheme
import com.jlptstudy.app.ui.settings.SettingsScreen
import com.jlptstudy.app.ui.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ui-design 6.9.1 문법 카드 앞·뒷면. 문구는 시험 픽스처다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GrammarCardUiTest {
    @get:Rule val rule = createComposeRule()

    private val noRuby = ReviewPrefs(bodyFurigana = false)
    private val card = grammarCard("n5-1", title = "〜てください", example = "これを[[見てください]]。", needsReview = true)
    private val rubyCard = grammarCard("n5-2", example = "これを[[{見|み}てください]]。")

    private var detailId: String? = null

    private fun show(c: ReviewCardUi, prefs: ReviewPrefs = noRuby, revealed: () -> Boolean) {
        rule.setContent {
            JlptTheme {
                val state = ReviewUiState(status = ReviewStatus.READY, card = c, revealed = revealed(), remainingReview = 1, remainingNew = 2)
                ReviewScreen(state, prefs, SnackbarHostState(), {}, {}, {}, {}, {}, {}, onGrammarDetail = { detailId = it })
            }
        }
    }

    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    @Test fun `앞면 노드는 문법 카드 문구이고 형식과 예문에 ja-JP 로캘이 있으며 contentDescription이 없다`() {
        show(card) { false }
        val config = node("review_card").fetchSemanticsNode().config
        val text = config[SemanticsProperties.Text].single()
        assertEquals("문법 카드, 앞면. 〜てください. 예문 これを見てください。. 남은 3장", text.text)
        val ja = LocaleList("ja-JP")
        for (part in listOf("〜てください", "これを見てください。")) {
            val s = text.text.indexOf(part)
            assertTrue(part, text.spanStyles.any { it.start <= s && it.end >= s + part.length && it.item.localeList == ja })
        }
        assertFalse(config.contains(SemanticsProperties.ContentDescription))
        rule.onNodeWithText("시험 예문 뜻").assertDoesNotExist() // 예문 번역은 앞면에 없다
    }

    @Test fun `후리가나가 켜져 있으면 앞면 낭독에 읽기가 붙는다`() {
        show(rubyCard, ReviewPrefs(bodyFurigana = true)) { false }
        val text = node("review_card").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertEquals("문법 카드, 앞면. 〜てください. 예문 これを見てください。, 읽기 これをみてください。. 남은 3장", text)
    }

    @Test fun `뒷면 노드는 뜻 예문 뜻 요약 칩 문구 순이다`() {
        show(card) { true }
        val text = node("review_card").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertEquals("뒷면. 뜻 시험 대응 표현. 예문 뜻 시험 예문 뜻. 시험 요약 첫 문단. ${ProvenanceLabel.of(card.provenance)}. 설명 검토 중", text)
        rule.onNodeWithText("설명 검토 중").assertExists()
    }

    private fun assertStable(c: ReviewCardUi) {
        var revealed by mutableStateOf(false)
        show(c) { revealed }
        val front = node("review_headword").getBoundsInRoot(); val frontEx = node("review_example").getBoundsInRoot()
        revealed = true
        rule.waitForIdle()
        val back = node("review_headword").getBoundsInRoot(); val backEx = node("review_example").getBoundsInRoot()
        assertEquals("top", front.top, back.top); assertEquals("width", front.right - front.left, back.right - back.left)
        assertEquals("height(글자 크기)", front.bottom - front.top, back.bottom - back.top)
        assertEquals("예문 top", frontEx.top, backEx.top)
    }

    @Test fun `문법 형식은 앞뒤 같은 y와 글자 크기다`() = assertStable(card)
    @Test fun `긴 문법 형식도 앞뒤 같다`() = assertStable(grammarCard("n5-3", title = "〜なければならない"))

    @Test fun `emphasis 구간에만 밑줄과 굵기 500이 있다`() {
        show(card) { false }
        val medium = mutableListOf<String>(); val normal = mutableListOf<String>()
        val texts = rule.onAllNodes(hasAnyAncestor(hasTestTag("review_example")) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        for (n in texts.fetchSemanticsNodes()) {
            val out = mutableListOf<TextLayoutResult>()
            n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
            val input = out.single().layoutInput
            (if (input.style.fontWeight == FontWeight.Medium) medium else normal) += input.text.text
        }
        // 줄바꿈 단위로 글자마다 나뉘어 그려진다: 이어 붙인 글자와 밑줄 조각 수로 확인한다.
        assertEquals("見てください", medium.joinToString(""))
        assertEquals("これを。", normal.joinToString(""))
        rule.onAllNodesWithTag("ruby_underline", useUnmergedTree = true).assertCountEquals(6)
    }

    @Test fun `요약이 4줄을 넘으면 자세히 버튼이 문법 ID로 상세를 연다`() {
        show(grammarCard("n5-9", summaryKo = "아주 긴 시험 요약 문장입니다. ".repeat(40))) { true }
        node("review_grammar_detail").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("n5-9", detailId)
    }

    @Test fun `요약이 짧으면 자세히 버튼이 없다`() {
        show(card) { true }
        rule.onAllNodesWithTag("review_grammar_detail", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun `설정의 문법 스위치는 번들에 문법이 있을 때만 보인다`() {
        var available by mutableStateOf(true)
        rule.setContent { JlptTheme { SettingsScreen(SettingsUiState(loading = false, grammarAvailable = available), SnackbarHostState()) } }
        rule.onNodeWithText("문법").assertExists()
        available = false
        rule.waitForIdle()
        rule.onNodeWithText("문법").assertDoesNotExist()
    }
}
