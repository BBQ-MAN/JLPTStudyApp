package com.jlptstudy.app.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.ui.theme.JlptTheme
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
class HomeScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private val events = mutableListOf<String>()
    private val actions get() = HomeActions(
        onStart = { events += "start" }, onPickLevels = { events += "pick" }, onChangeLimit = { events += "limit" },
        onMoreNew = { events += "more" }, onDismissNotice = { events += "dismiss" }, onNoticeMore = { events += "notice" },
        onAbout = { events += "about" }, onDictionary = { events += "dict" },
    )

    private fun show(s: HomeUiState) = rule.setContent { JlptTheme { HomeScreen(s, actions) } }

    @Test fun `NO_LEVEL_SELECTED에는 급수 고르기 행동이 있다`() {
        show(HomeUiState(status = HomeStatus.NO_LEVEL_SELECTED))
        rule.onNodeWithText("학습할 급수를 골라 주세요").assertIsDisplayed()
        rule.onNodeWithText("급수 고르기").performClick()
        assertEquals(listOf("pick"), events)
    }

    @Test fun `READY 요약 카드의 숫자와 버튼`() {
        show(HomeUiState(status = HomeStatus.READY, dueCount = 12, newTodayCount = 7, targetLevels = setOf(com.jlptstudy.app.JlptLevel.N5, com.jlptstudy.app.JlptLevel.N4)))
        rule.onNodeWithText("복습 12장 · 새 카드 7장").assertIsDisplayed()
        rule.onNodeWithText("학습 급수 N5·N4").assertIsDisplayed()
        rule.onNodeWithText("학습 시작").performClick(); rule.onNodeWithText("바꾸기").performClick()
        assertEquals(listOf("start", "pick"), events)
    }

    @Test fun `NEW_LIMIT_REACHED 행동은 새 카드 5장 더와 한도 바꾸기`() {
        show(HomeUiState(status = HomeStatus.NEW_LIMIT_REACHED, newLimit = 10))
        rule.onNodeWithText("오늘 새 카드 10장을 모두 학습했어요").assertIsDisplayed()
        rule.onNodeWithText("새 카드 5장 더").performClick(); rule.onNodeWithText("한도 바꾸기").performClick()
        assertEquals(listOf("more", "limit"), events)
    }

    @Test fun `샘플 띠와 첫 안내 카드는 다른 상태와 함께 나오고 닫기 자세히가 동작한다`() {
        show(HomeUiState(status = HomeStatus.ALL_DONE, isSampleContent = true, showFirstNotice = true, firstNotice = NoticeUi("firstNotice", "샘플 콘텐츠 안내", "본문")))
        rule.onNodeWithTag("sample_banner").assertIsDisplayed()
        rule.onNodeWithText("샘플 콘텐츠 안내").assertIsDisplayed()
        rule.onNodeWithText("자세히").performClick(); rule.onNodeWithText("닫기").performClick()
        assertEquals(listOf("notice", "dismiss"), events)
    }

    @Test fun `firstNotice가 null이면 첫 안내 카드가 없고 닫힌 뒤에도 없다`() {
        show(HomeUiState(status = HomeStatus.READY, showFirstNotice = true, firstNotice = null))
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasTestTag("first_notice")).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasTestTag("sample_banner")).fetchSemanticsNodes().size)
    }

    @Test fun `상단 메뉴 정보`() {
        show(HomeUiState(status = HomeStatus.READY))
        rule.onNodeWithContentDescription("더보기").performClick()
        rule.onNodeWithText("정보").performClick()
        assertEquals(listOf("about"), events)
    }
}
