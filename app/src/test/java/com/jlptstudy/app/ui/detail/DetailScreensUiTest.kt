package com.jlptstudy.app.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.dictionary.fiveMeanings
import com.jlptstudy.app.ui.dictionary.sampleWordRefs
import com.jlptstudy.app.ui.theme.JlptTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class DetailScreensUiTest {
    @get:Rule val rule = createComposeRule()

    private val kanji = KanjiDetailUiState(
        DetailStatus.LOADED, "直", JlptLevel.N5, 8, listOf("チョク"), listOf("ただ(ちに)"), listOf("직"), sampleWordRefs, strokes = null,
    )

    @Test fun `strokes가 null이면 필순 절이 그려지지 않고 있으면 그려진다`() {
        var s by androidx.compose.runtime.mutableStateOf(kanji)
        rule.setContent { JlptTheme { KanjiDetailScreen(s) } }
        rule.onNodeWithText("필순").assertDoesNotExistCompat()
        s = kanji.copy(strokes = listOf("M0,0"))
        rule.onNodeWithText("필순").assertIsDisplayed()
    }

    @Test fun `한국어 음이 없으면 그 행만 빠지고 음독 훈독은 남는다`() {
        rule.setContent { JlptTheme { KanjiDetailScreen(kanji.copy(koreanReadings = emptyList())) } }
        rule.onAllNodesWithText("한국어 음").assertCountEquals(0)
        rule.onNodeWithText("음독").assertIsDisplayed()
    }

    @Test fun `단어 상세의 뜻마다 ProvenanceLabel 칩이 붙는다`() {
        val s = VocabDetailUiState(
            DetailStatus.LOADED, "食べる", "たべる", Furigana.fromHeadword("食べる", "たべる"), JlptLevel.N5, listOf("1단 동사"), fiveMeanings, listOf("食"),
        )
        rule.setContent { JlptTheme { VocabDetailScreen(s) } }
        fiveMeanings.map(ProvenanceLabel::of).toSet().also { assertEquals(5, it.size) }
            .forEach { rule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
    }

    @Test fun `RETIRED는 문구와 뒤로 행동을 보인다`() {
        var back = 0
        rule.setContent { JlptTheme { VocabDetailScreen(VocabDetailUiState(DetailStatus.RETIRED), DetailActions(onBack = { back++ })) } }
        rule.onNodeWithText("더 이상 볼 수 없는 항목이에요").assertIsDisplayed()
        rule.onNodeWithText("뒤로").performClick()
        assertEquals(1, back)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
}
