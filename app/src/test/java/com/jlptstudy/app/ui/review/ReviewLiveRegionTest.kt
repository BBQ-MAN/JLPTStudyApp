package com.jlptstudy.app.ui.review

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
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
        go(word, true);   check("뒷면. 읽기 たべる. 뜻 뜻 a. ")             // 뒤집기
        go(kanji, false, 2); check("한자 카드, 앞면. 直", "남은 2장")          // 평가 뒤 다음 카드
        go(kanji, true, 2);  check("뒷면. 直")
        go(grammar, false, 1); check("문법 카드, 앞면. 〜てください", "남은 1장") // 문법 카드
        go(grammar, true, 1);  check("뒷면. 뜻 시험 대응 표현. 예문 뜻 시험 예문 뜻")
        go(word, true);   check("뒷면. 읽기 たべる")                        // 되돌리기: 되살린 카드의 뒷면
        go(word, false);  check("단어 카드, 앞면. 食べる")
    }
}
