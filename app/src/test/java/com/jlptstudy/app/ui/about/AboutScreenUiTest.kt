package com.jlptstudy.app.ui.about

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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

    // 보이는 URL 글자에는 줄바꿈용 안 보이는 글자(U+2060·U+200B)가 들어 있지만 접근성 트리의 Text는 원래 url 그대로다
    private fun urlText(url: String) = SemanticsMatcher("낭독 URL = $url") { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } == url
    }

    @Test fun `줄바꿈 도우미는 슬래시와 점 앞에서 끊지 않고 슬래시 뒤에서 끊게 하며 원래 글자는 그대로다`() {
        val shown = breakFriendly("https://a.org/x/y.html")
        assertEquals("https://a.org/x/y.html", shown.replace("\u2060", "").replace("\u200B", ""))
        assertEquals("https://a\u2060.org\u2060/\u200Bx\u2060/\u200By\u2060.html", shown)
    }

    @Test fun `링크를 열 Activity가 없으면 앱이 죽지 않고 안내 문구가 보인다`() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true) // 처리할 Activity가 없으면 startActivity가 던진다
        rule.setContent { JlptTheme { AboutScreen(longState(listOf(source("S-01", "자료 하나", "https://example.test/only-here"))), anchor = "S-01") } }
        rule.onNode(urlText("https://example.test/only-here")).performClick()
        rule.onNodeWithText("링크를 열 앱이 없습니다").assertIsDisplayed()
        rule.onNodeWithText("주소 복사").assertIsDisplayed()
    }

    @Test fun `링크는 ACTION_VIEW 인텐트로 연다`() {
        rule.setContent { JlptTheme { AboutScreen(longState(listOf(source("S-01", "자료 하나", "https://example.test/only-here"))), anchor = "S-01") } }
        rule.onNode(urlText("https://example.test/only-here")).performClick()
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

    @Test fun `복사 버튼은 URL마다 이름이 다르고 누르면 클립보드에 원문이 들어가며 복사했다고 알린다`() {
        val a = "https://example.test/a"; val b = "https://example.test/b"
        rule.setContent { JlptTheme { AboutScreen(longState(listOf(source("S-01", "자료 하나", a).copy(links = listOf(b)))), anchor = "S-01") } }
        rule.onNode(urlText(a)).assertExists() // 낭독 문구에 안 보이는 글자가 없다
        rule.onNodeWithContentDescription("주소 복사: $a").assertExists()
        rule.onNodeWithContentDescription("주소 복사: $b").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("주소를 복사했습니다").assertIsDisplayed()
        val clip = RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager::class.java).primaryClip
        assertEquals(b, clip?.getItemAt(0)?.text?.toString())
    }
}

internal fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
