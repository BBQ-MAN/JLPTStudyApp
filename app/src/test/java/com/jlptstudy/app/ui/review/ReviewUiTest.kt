package com.jlptstudy.app.ui.review

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.intl.LocaleList
import com.jlptstudy.app.ui.theme.JlptTheme
import com.jlptstudy.srs.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ReviewUiTest {
    @get:Rule val rule = createComposeRule()

    private fun show(card: ReviewCardUi, prefs: ReviewPrefs = ReviewPrefs(), revealedFlag: () -> Boolean) {
        rule.setContent {
            JlptTheme {
                val state = ReviewUiState(
                    status = ReviewStatus.READY, card = card, revealed = revealedFlag(), remainingReview = 1, remainingNew = 2,
                )
                ReviewScreen(state, prefs, SnackbarHostState(), {}, {}, {}, {}, {}, {})
            }
        }
    }

    private fun assertSameHeadword(card: ReviewCardUi, prefs: ReviewPrefs = ReviewPrefs()) {
        var revealed by mutableStateOf(false)
        show(card, prefs) { revealed }
        val front = rule.onNodeWithTag("review_headword", useUnmergedTree = true).getBoundsInRoot()
        revealed = true
        rule.waitForIdle()
        val back = rule.onNodeWithTag("review_headword", useUnmergedTree = true).getBoundsInRoot()
        assertEquals("top", front.top, back.top)
        assertEquals("left", front.left, back.left)
        assertEquals("width(글자 크기)", front.right - front.left, back.right - back.left)
        assertEquals("height(글자 크기)", front.bottom - front.top, back.bottom - back.top)
    }

    @Test fun `단어 카드 표제어는 앞뒤 같은 위치와 크기`() = assertSameHeadword(vocab("a", "食べる", "たべる"))
    @Test fun `읽기가 표기보다 넓은 단어도 같다`() = assertSameHeadword(vocab("a", "今日", "きょう"))
    @Test fun `후리가나가 켜진 앞면이어도 같다`() = assertSameHeadword(vocab("a", "食べる", "たべる"), ReviewPrefs(frontFurigana = true))
    @Test fun `후리가나를 꺼도 줄 높이가 남아 같다`() = assertSameHeadword(vocab("a", "食べる", "たべる"), ReviewPrefs(bodyFurigana = false))
    @Test fun `가나 전용 단어도 같다`() = assertSameHeadword(vocab("a", null, "ありがとう"))
    @Test fun `긴 가타카나도 같다`() = assertSameHeadword(vocab("a", "コミュニケーション", "コミュニケーション"))
    @Test fun `한자 카드 한자는 앞뒤 같다`() = assertSameHeadword(kanji("直"))
    @Test fun `회상 카드 한국어 뜻은 앞뒤 같다`() =
        assertSameHeadword(vocab("a", "食べる", "たべる", direction = VocabDirection.KO_TO_JA))

    @Test fun `앞면 노드는 표기 구간에 ja-JP 로캘을 건 AnnotatedString이고 contentDescription이 없다`() {
        show(vocab("a", "直接", "ちょくせつ")) { false }
        val config = rule.onNodeWithTag("review_card").fetchSemanticsNode().config
        val text = config[SemanticsProperties.Text].single()
        val start = text.text.indexOf("直接")
        assertTrue(start >= 0)
        assertTrue(text.spanStyles.any { it.start <= start && it.end >= start + 2 && it.item.localeList == LocaleList("ja-JP") })
        assertTrue(text.text.contains("남은 3장"))
        assertFalse(config.contains(SemanticsProperties.ContentDescription))
    }

    @Test fun `한자 앞면도 한자에 ja-JP 로캘을 건다`() {
        show(kanji("直")) { false }
        val text = rule.onNodeWithTag("review_card").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val start = text.text.indexOf("直")
        assertTrue(text.spanStyles.any { it.start <= start && it.end > start && it.item.localeList == LocaleList("ja-JP") })
    }

    @Test fun `평가 버튼은 onClickLabel에 간격을 담고 누르면 평가가 전달된다`() {
        var rated: Rating? = null
        rule.setContent {
            JlptTheme {
                val previews = Rating.entries.associateWith { com.jlptstudy.srs.IntervalPreview(0, 3, null) }
                val state = ReviewUiState(status = ReviewStatus.READY, card = vocab("a", "食べる", "たべる"), revealed = true, previews = previews)
                ReviewScreen(state, ReviewPrefs(), SnackbarHostState(), {}, {}, {}, {}, { rated = it }, {})
            }
        }
        rule.onNodeWithTag("rate_GOOD").performClick()
        assertEquals(Rating.GOOD, rated)
        val click = rule.onNodeWithTag("rate_AGAIN").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label
        assertEquals("평가: 다시, 다음 복습 3일 후", click)
    }

    @Test fun `S04는 남은 새 카드가 있을 때만 안내와 더 학습 버튼을 보인다`() {
        var left by mutableStateOf(3)
        var more = 0
        rule.setContent {
            JlptTheme {
                DoneScreen(ReviewUiState(status = ReviewStatus.READY, finished = true, reviewedInSession = 4, newInSession = 2, newCardsLeftAfterSession = left), {}, { more++ })
            }
        }
        rule.onNodeWithText("새 카드 3장 더 학습").performClick(); assertEquals(1, more) // 남은 수가 5보다 적으면 그 수만큼
        rule.onNodeWithText("새 카드 3장을 더 할 수 있어요", substring = true).assertExists()
        rule.onNodeWithText("4장 복습 · 새 카드 2장", substring = true).assertExists()
        left = 8
        rule.waitForIdle()
        rule.onNodeWithText("새 카드 5장 더 학습").assertExists()
        left = 0
        rule.waitForIdle()
        rule.onNodeWithText("더 학습", substring = true).assertDoesNotExist()
        rule.onNodeWithText("더 할 수 있어요", substring = true).assertDoesNotExist()
        rule.onNodeWithText("홈으로").assertExists()
    }
}
