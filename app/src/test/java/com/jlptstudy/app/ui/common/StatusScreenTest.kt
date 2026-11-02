package com.jlptstudy.app.ui.common

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import com.jlptstudy.app.ui.theme.JlptTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `제목은 heading이고 liveRegion으로 알리며 열리면 포커스를 받는다`() {
        rule.setContent { JlptTheme { StatusScreen(title = "제목", body = "본문") } }
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        rule.onNode(heading).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        rule.onNode(heading).assertIsFocused()
    }
}
