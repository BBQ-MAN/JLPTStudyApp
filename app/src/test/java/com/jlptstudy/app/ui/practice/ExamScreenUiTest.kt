package com.jlptstudy.app.ui.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
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
class ExamScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private fun desc(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    @Test fun `번호 줄은 답함을 체크로, 현재를 굵은 테두리 막대로 구분하고 칩을 누르면 그 문항으로 간다`() {
        var state by mutableStateOf(
            QuizUiState(
                QuizStatus.ANSWERING, QuizMode.EXAM, index = 1, total = 5, question = quizQuestion(), answeredIndices = setOf(0, 1),
                sectionStarts = listOf(0, 3), sectionTitles = listOf("언어지식(문자·어휘)", "언어지식(문법)·독해"), limitMs = 60 * 60_000L,
            ),
        )
        val visited = mutableListOf<Int>()
        rule.setContent {
            JlptTheme { QuizScreen(state, actions = QuizActions(onGoTo = { visited += it; state = state.copy(index = it) })) }
        }
        // 답함 = check 아이콘(색만으로 구분하지 않음)
        rule.onNodeWithTag("exam_chip_check_1", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exam_chip_check_2", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exam_chip_check_3", useUnmergedTree = true).assertDoesNotExist()
        // 현재 = 굵은 테두리 + 아래 막대
        rule.onNodeWithTag("exam_chip_current_2", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exam_chip_current_1", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("1번 문항, 답함", desc("exam_chip_1"))
        assertEquals("2번 문항, 답함, 현재 문항", desc("exam_chip_2"))
        assertEquals("3번 문항, 답하지 않음", desc("exam_chip_3"))

        rule.onNodeWithTag("exam_chip_3").performClick()
        rule.waitForIdle()
        assertEquals(listOf(2), visited)
        rule.onNodeWithTag("exam_chip_current_3", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exam_chip_current_2", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("3번 문항, 답하지 않음, 현재 문항", desc("exam_chip_3"))
        rule.onNodeWithText("언어지식(문자·어휘)").assertExists() // 현재 영역 이름
    }

    @Test fun `타이머는 경과를 분 초로 읽고 기준을 넘으면 지남을 덧붙이며 첫 문항에서 이전은 비활성이다`() {
        rule.setContent {
            JlptTheme {
                QuizScreen(QuizUiState(QuizStatus.ANSWERING, QuizMode.EXAM, index = 0, total = 3, question = quizQuestion(), elapsedMs = 61 * 60_000L + 5_000, limitMs = 60 * 60_000L))
            }
        }
        assertEquals("경과 61분 5초, 기준 60분 지남", desc("exam_timer"))
        rule.onNodeWithTag("exam_prev").assertIsNotEnabled()
    }

    @Test fun `시작 화면은 문항이 모자라면 시작이 비활성이다`() {
        rule.setContent {
            JlptTheme { ExamStartScreen(ExamStartUiState(ExamStartStatus.LOADED, JlptLevel.N5, listOf(ExamSectionLine("언어지식(문자·어휘)", 1, 20)), 1, 20 * 60_000L, hasShortfall = true)) }
        }
        rule.onNodeWithTag("exam_start", useUnmergedTree = true).assertIsNotEnabled()
        rule.onNodeWithText("언어지식(문자·어휘)").assertExists()
        rule.onNodeWithText("1문항 · 기준 20분").assertExists()
        // 비활성 이유가 버튼 낭독에 들어 있다(ui-design S12·S14 TalkBack)
        val start = rule.onNodeWithTag("exam_start").fetchSemanticsNode().config
        assertEquals("모의고사 시작", desc("exam_start"))
        assertEquals("문항이 모자라 아직 볼 수 없습니다", start.getOrNull(SemanticsProperties.StateDescription))
    }
}
