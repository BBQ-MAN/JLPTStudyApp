package com.jlptstudy.app.ui.about

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jlptstudy.app.R
import com.jlptstudy.app.data.attribution.CreditUi
import com.jlptstudy.app.data.attribution.LinkUi
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.data.attribution.SourceUi
import com.jlptstudy.app.ui.theme.JlptTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AboutScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private fun source(id: String, name: String, licenseUrl: String = "https://example.test/lic/$id") = SourceUi(
        id, name, "2026-01-01", "Attribution text for $id", LinkUi("CC BY 4.0", licenseUrl), null, "변경 없음", emptyList(),
        listOf(CreditUi("추가 표시 $id", null)),
    )

    private fun longState(sources: List<SourceUi> = listOf(source("S-01", "자료 하나"), source("S-03", "자료 필순"))) = AboutUiState(
        AboutStatus.LOADED, "v1", LinkUi("CC BY-SA 4.0", "https://example.test/db"),
        notices = (1..12).map { NoticeUi("N-$it", "고지 제목 $it", "고지 본문 $it ".repeat(20)) },
        sources = sources,
    )

    @Test fun `anchor가 자료 ID면 그 카드가 보이도록 스크롤되어 있다`() {
        rule.setContent { JlptTheme { AboutScreen(longState(), anchor = "S-03") } }
        rule.onNodeWithText("자료 필순").assertIsDisplayed()
        rule.onNodeWithText("고지 제목 1").assertDoesNotExistCompat()
    }

    @Test fun `anchor가 고지 ID여도 그 카드로 간다`() {
        rule.setContent { JlptTheme { AboutScreen(longState(), anchor = "N-9") } }
        rule.onNodeWithText("고지 제목 9").assertIsDisplayed()
    }

    @Test fun `anchor가 null이면 맨 위에서 시작한다`() {
        rule.setContent { JlptTheme { AboutScreen(longState(), anchor = null) } }
        rule.onNodeWithText("고지 제목 1").assertIsDisplayed()
        rule.onNodeWithText("자료 필순").assertDoesNotExist()
    }

    @Test fun `없는 anchor도 맨 위에서 시작한다`() {
        rule.setContent { JlptTheme { AboutScreen(longState(), anchor = "S-99") } }
        rule.onNodeWithText("고지 제목 1").assertIsDisplayed()
    }

    @Test fun `링크를 열 Activity가 없으면 앱이 죽지 않고 안내 문구가 보인다`() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true) // 처리할 Activity가 없으면 startActivity가 던진다
        rule.setContent { JlptTheme { AboutScreen(longState(listOf(source("S-01", "자료 하나", "https://example.test/only-here"))), anchor = "S-01") } }
        rule.onNodeWithText("https://example.test/only-here").performClick()
        rule.onNodeWithText("링크를 열 앱이 없습니다").assertIsDisplayed()
        rule.onNodeWithText("주소 복사").assertIsDisplayed()
    }

    @Test fun `링크는 ACTION_VIEW 인텐트로 연다`() {
        rule.setContent { JlptTheme { AboutScreen(longState(listOf(source("S-01", "자료 하나", "https://example.test/only-here"))), anchor = "S-01") } }
        rule.onNodeWithText("https://example.test/only-here").performClick()
        val started: Intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://example.test/only-here", started.dataString)
    }

    @Test fun `FAILED는 attribution_fallback 문단을 그대로 보인다`() {
        rule.setContent { JlptTheme { AboutScreen(AboutUiState(AboutStatus.FAILED)) } }
        rule.onNodeWithTag("about_fallback").assertTextEquals(RuntimeEnvironment.getApplication().getString(R.string.attribution_fallback))
        rule.onNodeWithText("출처 정보를 읽지 못했습니다").assertIsDisplayed()
    }

    @Test fun `집계 줄은 reviewCounts가 있을 때 N-10 카드 아래에만 나온다`() {
        val withCounts = longState().copy(reviewCounts = com.jlptstudy.app.data.content.ReviewCounts(28, 1, 2, 3, 20, 2))
        rule.setContent { JlptTheme { AboutScreen(withCounts, anchor = "N-10") } }
        rule.onNodeWithTag("review_counts").assertTextEquals("한국어 뜻 28개 — 사람 검수 1 · AI 검수 2 · 표본 검수 묶음 3 · 검수 전 20 · 검수자 미기록 2")
    }
}

internal fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
