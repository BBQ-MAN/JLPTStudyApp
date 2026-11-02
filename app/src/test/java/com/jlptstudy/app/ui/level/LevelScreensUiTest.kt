package com.jlptstudy.app.ui.level

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.settings.SettingsActions
import com.jlptstudy.app.ui.settings.SettingsScreen
import com.jlptstudy.app.ui.settings.SettingsUiState
import com.jlptstudy.app.ui.theme.JlptTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal val levelOptions = listOf(
    LevelOption(JlptLevel.N5, 120, 80, true), LevelOption(JlptLevel.N4, 0, 0, false),
    LevelOption(JlptLevel.N3, 0, 0, false), LevelOption(JlptLevel.N2, 0, 0, false), LevelOption(JlptLevel.N1, 0, 0, false),
)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LevelScreensUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `콘텐츠에 없는 급수 행은 선택할 수 없고 콘텐츠 준비 중이 보인다`() {
        var toggled: JlptLevel? = null
        rule.setContent {
            JlptTheme { LevelSelectScreen(LevelUiState(LevelStatus.READY, levelOptions, setOf(JlptLevel.N5)), { toggled = it }, {}, {}, {}, {}) }
        }
        rule.onNodeWithTag("level_row_N5").assertIsEnabled()
        rule.onNodeWithTag("level_row_N4").assertIsNotEnabled()
        rule.onNodeWithTag("level_row_N4").performClick()
        assertEquals(null, toggled)
        onAllNodesWithTextCount("콘텐츠 준비 중", 4)
        rule.onNodeWithTag("level_row_N5").performClick()
        assertEquals(JlptLevel.N5, toggled)
        rule.onNodeWithText("단어 120 · 한자 80").assertIsDisplayed()
        rule.onNodeWithTag("level_row_N4").fetchSemanticsNode().config[SemanticsProperties.StateDescription].let {
            assertEquals("콘텐츠 준비 중, 선택할 수 없음", it)
        }
        rule.onNodeWithText("시작").assertIsEnabled()
    }

    @Test fun `선택이 없으면 시작이 비활성이고 안내가 보인다`() {
        rule.setContent { JlptTheme { LevelSelectScreen(LevelUiState(LevelStatus.READY, levelOptions, emptySet()), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("급수를 하나 이상 고르세요").assertIsDisplayed()
        rule.onNodeWithText("시작").assertIsNotEnabled()
    }

    @Test fun `개수를 세는 동안 시작은 비활성이다`() {
        rule.setContent { JlptTheme { LevelSelectScreen(LevelUiState(LevelStatus.PREPARING), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("시작").assertIsNotEnabled()
    }

    @Test fun `NO_CONTENT는 앱 정보 열기 행동을 보인다`() {
        rule.setContent { JlptTheme { LevelSelectScreen(LevelUiState(LevelStatus.NO_CONTENT), {}, {}, {}, {}, {}) } }
        rule.onNodeWithText("학습할 콘텐츠가 없습니다").assertIsDisplayed()
        rule.onNodeWithText("앱 정보 열기").assertIsDisplayed()
    }

    @Test fun `설정에서 급수 0개 상태의 시트는 저장이 비활성이다`() {
        rule.setContent {
            JlptTheme {
                SettingsScreen(
                    SettingsUiState(loading = false, targetLevels = emptySet(), levelOptions = levelOptions),
                    SnackbarHostState(), openLevelPicker = true, actions = SettingsActions(),
                )
            }
        }
        rule.onNodeWithTag("level_picker_sheet").assertIsDisplayed()
        rule.onNodeWithText("저장").assertIsNotEnabled()
        rule.onNodeWithText("고르지 않은 급수의 복습은 멈춥니다. 학습 기록은 남습니다.").assertIsDisplayed()
        rule.onNodeWithTag("level_row_N5").performClick()
        rule.onNodeWithText("저장").assertIsEnabled()
    }

    @Test fun `시트 저장은 고른 급수를 넘긴다`() {
        var saved: Set<JlptLevel>? = null
        rule.setContent {
            JlptTheme {
                SettingsScreen(
                    SettingsUiState(loading = false, targetLevels = setOf(JlptLevel.N5), levelOptions = levelOptions),
                    SnackbarHostState(), openLevelPicker = true, actions = SettingsActions(onLevels = { saved = it }),
                )
            }
        }
        rule.onNodeWithText("저장").assertIsEnabled().performClick()
        assertEquals(setOf(JlptLevel.N5), saved)
    }

    @Test fun `시트는 기본으로 닫혀 있다`() {
        rule.setContent { JlptTheme { SettingsScreen(SettingsUiState(loading = false, levelOptions = levelOptions), SnackbarHostState()) } }
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasTestTag("level_picker_sheet")).fetchSemanticsNodes().size)
        rule.onNodeWithTag("row_levels").assertIsDisplayed()
    }

    @Test fun `카드 종류를 모두 끄려 하면 안내가 보인다`() {
        rule.setContent { JlptTheme { SettingsScreen(SettingsUiState(loading = false, levelOptions = levelOptions, kindsWarning = true), SnackbarHostState()) } }
        rule.onNodeWithTag("kinds_warning").performScrollTo().assertIsDisplayed()
    }

    private fun onAllNodesWithTextCount(text: String, n: Int) =
        assertEquals(n, rule.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size)

}
