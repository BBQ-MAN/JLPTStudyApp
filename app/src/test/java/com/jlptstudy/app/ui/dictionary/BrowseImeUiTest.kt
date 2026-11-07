package com.jlptstudy.app.ui.dictionary

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.dp
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.theme.JlptTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ui-design 6.5: 키보드(IME) 인셋을 흉내 내면 결과 목록·빈 결과 StatusScreen이 키보드 위 남은 높이로 줄어든다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BrowseImeUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val avail = setOf(JlptLevel.N5, JlptLevel.N4)
    private val imeDp = 280

    private fun findComposeView(v: View): View? =
        if (v.javaClass.simpleName == "AndroidComposeView") v
        else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { findComposeView(g.getChildAt(it)) } }

    private fun openIme() {
        val px = (imeDp * rule.activity.resources.displayMetrics.density).toInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
            .setVisible(WindowInsetsCompat.Type.ime(), true).build()
        rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(findComposeView(rule.activity.window.decorView)!!, insets) }
        rule.waitForIdle()
    }

    @Test fun `키보드가 열려도 목록 마지막 행까지 스크롤로 닿고 키보드 위에 있다`() {
        val items = (1..40).map { BrowseItem.Word("w$it", "語$it", "ご", "뜻 $it", JlptLevel.N5) }
        rule.setContent { JlptTheme { BrowseScreen(BrowseUiState(BrowseStatus.RESULTS, items = items, availableLevels = avail)) } }
        openIme()
        rule.onNodeWithTag("word_list").performScrollToIndex(items.lastIndex)
        rule.waitForIdle()
        val rootBottom = rule.onRoot().getBoundsInRoot().bottom
        val last = rule.onNodeWithText("뜻 40").assertIsDisplayed().getBoundsInRoot()
        assertTrue("마지막 행 ${last.bottom} 은 키보드 위(${rootBottom - imeDp.dp})", last.bottom <= rootBottom - imeDp.dp)
    }

    @Test fun `키보드가 열려도 빈 결과의 필터 지우기 버튼이 키보드 위에 보인다`() {
        rule.setContent { JlptTheme { BrowseScreen(BrowseUiState(BrowseStatus.NO_CONTENT_FOR_FILTER, availableLevels = avail)) } }
        openIme()
        val rootBottom = rule.onRoot().getBoundsInRoot().bottom
        val button = rule.onNodeWithText("필터 지우기").assertIsDisplayed().getBoundsInRoot()
        assertTrue("버튼 ${button.bottom} 은 키보드 위(${rootBottom - imeDp.dp})", button.bottom <= rootBottom - imeDp.dp)
    }
}
