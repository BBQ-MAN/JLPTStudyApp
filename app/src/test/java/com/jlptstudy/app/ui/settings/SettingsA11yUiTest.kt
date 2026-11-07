package com.jlptstudy.app.ui.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.study.RoomStudyRepository
import com.jlptstudy.app.data.study.StudyDatabase
import com.jlptstudy.app.ui.level.LevelSelectScreen
import com.jlptstudy.app.ui.level.LevelStatus
import com.jlptstudy.app.ui.level.LevelUiState
import com.jlptstudy.app.ui.level.levelOptions
import com.jlptstudy.app.ui.review.FakeContent
import com.jlptstudy.app.ui.theme.JlptTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ui-design 6.8·11.1: NumberStepper(S01·S08)와 하루 시작 시각 시트. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SettingsA11yUiTest {
    @get:Rule val rule = createComposeRule()
    private var db: StudyDatabase? = null
    @After fun tearDown() { db?.close() }

    private val dec = "하루 새 카드 줄이기"
    private val inc = "하루 새 카드 늘리기"

    private fun stateDesc(label: String) = rule.onNodeWithContentDescription(label).fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    /** 5에서 −, 50에서 +가 비활성이고, +를 누르면 5 늘며 두 버튼이 "{n}장"을 알린다. 값 노드에 liveRegion·진행률이 없다. */
    private fun checkStepper(rule: ComposeContentTestRule, value: () -> Int, set: (Int) -> Unit) {
        set(5); rule.waitForIdle()
        rule.onNodeWithContentDescription(dec).assertIsNotEnabled()
        rule.onNodeWithContentDescription(inc).assertIsEnabled()
        set(50); rule.waitForIdle()
        rule.onNodeWithContentDescription(inc).assertIsNotEnabled()
        rule.onNodeWithContentDescription(dec).assertIsEnabled()
        set(10); rule.waitForIdle()
        rule.onNodeWithContentDescription(inc).performClick(); rule.waitForIdle()
        assertEquals(15, value())
        assertEquals("15장", stateDesc(dec)); assertEquals("15장", stateDesc(inc))
        val valueNode = rule.onNodeWithText("15장").fetchSemanticsNode()
        for (n in listOf(valueNode, valueNode.parent!!)) {
            assertFalse("값 노드에 liveRegion 없음", n.config.contains(SemanticsProperties.LiveRegion))
            assertFalse("진행률 없음", n.config.contains(SemanticsProperties.ProgressBarRangeInfo))
        }
        rule.onNodeWithContentDescription(dec).performClick(); rule.waitForIdle()
        assertEquals(10, value())
    }

    @Test fun `S08 스테퍼는 범위 끝에서 비활성이고 값을 5씩 바꾸며 stateDescription을 알린다`() {
        var n by mutableStateOf(10)
        rule.setContent {
            JlptTheme {
                SettingsScreen(
                    SettingsUiState(loading = false, newCardsPerDay = n, levelOptions = levelOptions), SnackbarHostState(),
                    actions = SettingsActions(onNewCards = { n = it }),
                )
            }
        }
        checkStepper(rule, { n }, { n = it })
    }

    @Test fun `S01 스테퍼도 같다`() {
        var n by mutableStateOf(10)
        rule.setContent {
            JlptTheme { LevelSelectScreen(LevelUiState(LevelStatus.READY, levelOptions, setOf(JlptLevel.N5), newCardsPerDay = n), {}, { n = it }, {}, {}, {}) }
        }
        checkStepper(rule, { n }, { n = it })
    }

    @Test fun `S08은 누를 때마다 new_cards_per_day를 저장하고 시작 시각을 고르면 360분이 저장된다`() = runBlocking {
        val study = RoomStudyRepository(StudyDatabase.open(ApplicationProvider.getApplicationContext()).also { db = it }.studyDao())
        val vm = SettingsViewModel(study, FakeContent(emptyList()))
        rule.setContent { JlptTheme { SettingsRoute(vm, openLevelPicker = false, onAbout = {}, onLevelNotice = {}) } }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("row_day_start")).fetchSemanticsNodes().isNotEmpty() }
        fun saved() = runBlocking { study.settings().first() }
        rule.onNodeWithContentDescription(inc).performClick()
        rule.waitUntil(5_000) { saved().newCardsPerDay == 15 }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("15장").fetchSemanticsNodes().isNotEmpty() } // 저장값이 화면에 돌아온 뒤 누른다
        rule.onNodeWithContentDescription(dec).performClick()
        rule.waitUntil(5_000) { saved().newCardsPerDay == 10 }

        rule.onNodeWithTag("row_day_start").performScrollTo().performClick()
        rule.onNodeWithTag("day_start_6").performClick()
        rule.waitUntil(5_000) { saved().dayBoundaryMinutes == 360 }
    }

    @Test fun `하루 시작 시각 행은 시트를 열고 라디오 13개 중 현재 값만 선택이며 고르면 닫고 행으로 포커스가 돌아온다`() {
        var picked: Int? = null
        var hour by mutableStateOf(4)
        var input: InputModeManager? = null
        rule.setContent {
            input = LocalInputModeManager.current
            JlptTheme {
                SettingsScreen(
                    SettingsUiState(loading = false, dayBoundaryHour = hour, levelOptions = levelOptions), SnackbarHostState(),
                    actions = SettingsActions(onDayHour = { picked = it; hour = it }),
                )
            }
        }
        // 터치 모드에서는 clickable이 입력 포커스를 받지 않는다: 키보드·D-pad 사용자 경로를 확인한다.
        rule.runOnUiThread { input!!.requestInputMode(InputMode.Keyboard) }
        val row = rule.onNodeWithTag("row_day_start").performScrollTo()
        row.fetchSemanticsNode().config.let {
            assertEquals("4시", it[SemanticsProperties.StateDescription])
            assertEquals("바꾸기", it[SemanticsActions.OnClick].label)
        }
        row.performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("day_start_sheet").assertExists()
        val radios = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and hasAnyAncestor(hasTestTag("day_start_sheet"))).fetchSemanticsNodes() // 테마 분할 버튼도 RadioButton이라 시트 안만 센다
        assertEquals(13, radios.size)
        val selected = radios.filter { (SemanticsProperties.Selected in it.config && it.config[SemanticsProperties.Selected]) }
        assertEquals(1, selected.size)
        assertEquals("4시", selected.single().config[SemanticsProperties.ContentDescription].single())
        val first = rule.onNodeWithTag("day_start_0").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        val last = rule.onNodeWithTag("day_start_12").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertEquals("00:00" to "12:00", first to last)

        rule.onNodeWithTag("day_start_6").performClick()
        rule.waitForIdle()
        assertEquals(6, picked)
        assertEquals(0, rule.onAllNodes(hasTestTag("day_start_sheet")).fetchSemanticsNodes().size)
        rule.onNodeWithTag("row_day_start").assertIsFocused()
        assertEquals("6시", rule.onNodeWithTag("row_day_start").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }
}
