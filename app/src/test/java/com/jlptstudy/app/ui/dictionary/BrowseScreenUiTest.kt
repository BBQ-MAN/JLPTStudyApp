package com.jlptstudy.app.ui.dictionary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jlptstudy.app.JlptLevel
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
class BrowseScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private val events = mutableListOf<String>()
    private val actions = BrowseActions(
        onQuery = { events += "q:$it" }, onTab = { events += "tab:$it" }, onToggleLevel = { events += "lv:$it" },
        onClearLevels = { events += "clear" }, onWord = { events += "w:$it" }, onKanji = { events += "k:$it" },
    )
    private val avail = setOf(JlptLevel.N5, JlptLevel.N4)

    @Test fun `행을 누르면 단어 상세 한자 칸을 누르면 한자 상세로 간다`() {
        var s by androidx.compose.runtime.mutableStateOf(BrowseUiState(BrowseStatus.RESULTS, items = sampleWords, availableLevels = avail))
        rule.setContent { JlptTheme { BrowseScreen(s, actions) } }
        rule.onNodeWithText("食べる").performClick()
        s = BrowseUiState(BrowseStatus.RESULTS, tab = BrowseTab.KANJI, items = sampleKanji, availableLevels = avail)
        rule.onNodeWithText("直").performClick()
        assertEquals(listOf("w:w1", "k:直"), events)
    }

    @Test fun `검색 입력과 급수 칩과 빈 결과 행동이 ViewModel로 전달된다`() {
        rule.setContent { JlptTheme { BrowseScreen(BrowseUiState(BrowseStatus.NO_RESULT, availableLevels = avail), actions) } }
        rule.onNodeWithTag("search_field").performTextInput("y")
        rule.onNodeWithTag("level_chip_N4").performClick()
        rule.onNodeWithText("급수 필터를 넓히거나 다른 표기로 찾아 보세요.").assertIsDisplayed()
        rule.onNodeWithText("필터 지우기").performClick()
        assertEquals(listOf("q:y", "lv:N4", "q:", "clear"), events)
    }

    @Test fun `밖에서 검색어가 지워지면 검색창 글자도 비워진다`() {
        var s by androidx.compose.runtime.mutableStateOf(BrowseUiState(BrowseStatus.NO_RESULT, query = "없는말", availableLevels = avail))
        rule.setContent { JlptTheme { BrowseScreen(s, actions) } }
        rule.onNodeWithTag("search_field").assertTextContains("없는말")
        s = s.copy(query = "")
        rule.onNodeWithText("없는말").assertDoesNotExist()
    }

    @Test fun `필터 결과 없음에는 급수 필터 풀기 행동이 있다`() {
        rule.setContent { JlptTheme { BrowseScreen(BrowseUiState(BrowseStatus.NO_CONTENT_FOR_FILTER, availableLevels = avail), actions) } }
        rule.onNodeWithText("필터 지우기").performClick()
        assertEquals(listOf("clear"), events)
    }
}
