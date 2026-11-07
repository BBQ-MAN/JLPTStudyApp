package com.jlptstudy.app.ui.review

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.jlptstudy.app.ui.theme.JlptTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ui-design 4.3 "카드 세션 알림"·11.1: review_card 한 노드가 Polite이고, 앞면·뒷면·다음 카드·되돌리기 때 같은 노드의 text만 바뀐다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ReviewLiveRegionTest {
    @get:Rule val rule = createComposeRule()

    private val word = vocab("a", "食べる", "たべる")
    private val kanji = kanji("直")
    private val grammar = grammarCard("n5-1")
    private var state by mutableStateOf(ReviewUiState(status = ReviewStatus.READY, card = word, revealed = false, remainingReview = 1, remainingNew = 2))

    private fun go(card: ReviewCardUi, revealed: Boolean, left: Int = 3) {
        state = state.copy(card = card, revealed = revealed, remainingReview = left, remainingNew = 0)
        rule.waitForIdle()
    }

    private fun node() = rule.onNodeWithTag("review_card").fetchSemanticsNode()
    private fun text() = node().config[SemanticsProperties.Text].single().text

    @Test fun `review_card는 앞면 뒷면 다음 카드 되돌리기 내내 같은 노드이고 Polite이며 text만 바뀐다`() {
        rule.setContent { JlptTheme { ReviewScreen(state, ReviewPrefs(), SnackbarHostState(), {}, {}, {}, {}, {}, {}) } }
        val id = node().id
        val seen = mutableListOf<String>()
        fun check(startsWith: String, vararg contains: String) {
            val n = node()
            assertEquals("같은 시맨틱 노드", id, n.id)
            assertEquals(LiveRegionMode.Polite, n.config[SemanticsProperties.LiveRegion])
            val t = text()
            assertTrue("'$t' 는 '$startsWith'로 시작", t.startsWith(startsWith))
            contains.forEach { assertTrue("'$t' 에 '$it'", t.contains(it)) }
            assertTrue("문구가 바뀌어야 알림이 나간다: $t", seen.lastOrNull() != t)
            seen += t
        }
        check("단어 카드, 앞면. 食べる", "남은 3장")                         // 세션 첫 카드 앞면
        go(word, true);   check("뒷면. 읽기 たべる. 품사 명사. 뜻 뜻 a. ")             // 뒤집기
        go(kanji, false, 2); check("한자 카드, 앞면. 直", "남은 2장")          // 평가 뒤 다음 카드
        go(kanji, true, 2);  check("뒷면. 直. 한국어 음 직. 음독 チョク. 훈독 ただ(ちに). 9획. 쓰인 단어 直接 직접")
        go(grammar, false, 1); check("문법 카드, 앞면. 〜てください", "남은 1장") // 문법 카드
        go(grammar, true, 1);  check("뒷면. 뜻 시험 대응 표현. 예문 뜻 시험 예문 뜻")
        go(word, true);   check("뒷면. 읽기 たべる")                        // 되돌리기: 되살린 카드의 뒷면
        go(word, false);  check("단어 카드, 앞면. 食べる")
    }

    private fun backNode() = rule.onNodeWithTag("review_card").fetchSemanticsNode()

    @Test fun `뒷면 카드도 앞면처럼 하나의 낭독 노드이고 칩과 더 보기는 사용자 지정 동작으로 낸다`() {
        val many = vocab("m", "食べる", "たべる", meanings = List(5) { meaning("뜻 ${it + 1}") })
        val opened = mutableListOf<String>()
        state = state.copy(card = many, revealed = true, remainingReview = 1, remainingNew = 0)
        rule.setContent { JlptTheme { ReviewScreen(state, ReviewPrefs(), SnackbarHostState(), {}, {}, {}, {}, {}, {}, onChipClick = { opened += it.textKo }) } }
        val n = backNode()
        assertTrue("뒷면은 하위 시맨틱을 지우는 단일 노드", n.config.isClearingSemantics)
        assertEquals("병합 트리에 하위 노드가 없다", 0, n.children.size)
        val labels = n.config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("1번 뜻 만든 방법 자세히 보기", "2번 뜻 만든 방법 자세히 보기", "3번 뜻 만든 방법 자세히 보기", "뜻 2개 더"), labels)
        n.config[SemanticsActions.CustomActions][1].action()
        assertEquals(listOf("뜻 2"), opened)
        n.config[SemanticsActions.CustomActions].last().action() // 더 보기 -> 5개 모두
        rule.waitForIdle()
        assertEquals(5, backNode().config[SemanticsActions.CustomActions].size)
    }

    @Test fun `앞면 노드는 사용자 지정 동작이 없고 문법 뒷면은 칩 동작을 낸다`() {
        state = state.copy(card = grammar, revealed = false, remainingReview = 1, remainingNew = 0)
        rule.setContent { JlptTheme { ReviewScreen(state, ReviewPrefs(), SnackbarHostState(), {}, {}, {}, {}, {}, {}, onChipClick = {}, onGrammarDetail = {}) } }
        assertTrue(SemanticsActions.CustomActions !in backNode().config)
        go(grammar, true, 1)
        assertEquals(listOf("만든 방법 자세히 보기"), backNode().config[SemanticsActions.CustomActions].map { it.label })
    }

    @Test fun `뒷면 낭독문은 화면에 보이는 정보를 모두 담고 일본어에만 JpSpan이 걸린다`() {
        val k = backText(kanji).text
        listOf("한국어 음 직", "음독 チョク", "훈독 ただ(ちに)", "9획", "쓰인 단어 直接 직접").forEach { assertTrue("'$k' 에 '$it'", k.contains(it)) }

        val many = vocab("m", "食べる", "たべる", meanings = List(5) { meaning("뜻 ${it + 1}") }, pos = listOf("v1", "vt"))
        val three = backText(many).text
        listOf("품사 1단 동사, 타동사", "1번 뜻 뜻 1", "2번 뜻 뜻 2", "3번 뜻 뜻 3", "뜻 2개 더").forEach { assertTrue("'$three' 에 '$it'", three.contains(it)) }
        assertTrue("접힌 뜻은 읽지 않는다: $three", !three.contains("뜻 4"))
        val all = backText(many, allMeanings = true).text
        assertTrue("펼치면 5개 모두: $all", all.contains("5번 뜻 뜻 5") && !all.contains("개 더"))

        val ko = vocab("r", "食べる", "たべる", direction = VocabDirection.KO_TO_JA).let { backText(it).text }
        assertTrue("떠올리기 카드는 일본어 표기가 정답이다: $ko", ko.startsWith("뒷면. 일본어 食べる. 읽기 たべる"))

        val spans = backText(kanji).spanStyles
        assertTrue("일본어 구간에 JpSpan", spans.any { it.item == com.jlptstudy.app.ui.theme.JpSpan })
    }

    @Test fun `더 보기를 펼치면 같은 노드의 낭독문이 바뀌어 다시 읽힌다`() {
        val many = vocab("m", "食べる", "たべる", meanings = List(5) { meaning("뜻 ${it + 1}") })
        state = state.copy(card = many, revealed = true, remainingReview = 1, remainingNew = 0)
        rule.setContent { JlptTheme { ReviewScreen(state, ReviewPrefs(), SnackbarHostState(), {}, {}, {}, {}, {}, {}, onChipClick = {}) } }
        val before = text()
        assertTrue(before.contains("뜻 2개 더") && !before.contains("뜻 4"))
        backNode().config[SemanticsActions.CustomActions].last().action()
        rule.waitForIdle()
        val after = text()
        assertTrue("펼친 뒤: $after", after.contains("5번 뜻 뜻 5") && before != after)
    }
}
