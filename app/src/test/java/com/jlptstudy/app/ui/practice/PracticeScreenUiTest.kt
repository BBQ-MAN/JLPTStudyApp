package com.jlptstudy.app.ui.practice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.jlptstudy.app.AppContainer
import com.jlptstudy.app.FakeContentRepository
import com.jlptstudy.app.FakeStudyRepository
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.data.content.ContentRepository
import com.jlptstudy.app.data.content.QuizContentRepository
import com.jlptstudy.app.data.study.QuizRepository
import com.jlptstudy.app.data.study.StudyRepository
import com.jlptstudy.app.nav.AppRoot
import com.jlptstudy.app.nav.Practice
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.theme.JlptTheme
import com.jlptstudy.app.ui.theme.JpSpan
import com.jlptstudy.quiz.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class QuizWorldContent(q: FakeQuizContent) : ContentRepository by FakeContentRepository, QuizContentRepository by q
private class QuizWorldStudy(q: FakeQuiz) : StudyRepository by FakeStudyRepository(), QuizRepository by q

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PracticeScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private val events = mutableListOf<String>()
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun spans(a: AnnotatedString?) = a?.spanStyles.orEmpty()

    @Test fun `연습 탭이 보이고 누르면 S12로 가며 attribution이 깨져도 N-11 줄이 리소스 문구로 보인다`() {
        lateinit var nav: NavHostController
        val items = FakeQuizContent(listOf(qc("q-n5-vocab-0001"), qc("q-n5-vocab-0002")))
        val container = AppContainer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            openContent = { QuizWorldContent(items) }, openStudy = { QuizWorldStudy(FakeQuiz()) },
            // 기본 attributionRepository는 load()가 예외를 던진다(없거나 깨진 attribution.json과 같다)
        )
        container.start()
        rule.setContent { nav = rememberNavController(); JlptTheme { AppRoot(container, nav) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("연습").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("연습").assertIsDisplayed()
        rule.onNodeWithText("연습").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("practice_type_vocab_kanji-reading").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(nav.currentDestination!!.hasRoute(Practice::class))
        rule.onNodeWithTag("practice_home_screen").assertIsDisplayed()
        val notice = rule.onNodeWithTag("practice_notice").performScrollTo()
        notice.assertIsDisplayed()
        val text = notice.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals(context.getString(R.string.practice_notice), text)
    }

    @Test fun `PracticeHomeUiState에는 notice 필드가 없다`() {
        assertTrue(PracticeHomeUiState::class.java.declaredFields.none { it.name.contains("notice", ignoreCase = true) })
    }

    @Test fun `S12는 유형 행과 모의고사 카드와 N-11 줄을 보이고 행을 누르면 시작한다`() {
        val s = PracticeHomeUiState(
            PracticeHomeStatus.LOADED, JlptLevel.N5, listOf(JlptLevel.N5, JlptLevel.N4),
            listOf(PracticeTypeUi(Section.VOCAB, "kanji-reading", 12), PracticeTypeUi(Section.GRAMMAR, "grammar-form", 3)),
            ExamCardUi(JlptLevel.N5, 40, 60 * 60_000L, false),
        )
        rule.setContent {
            JlptTheme {
                PracticeHomeScreen(s, actions = PracticeHomeActions(
                    onSelectLevel = { events += "lv:$it" }, onStartType = { sec, t -> events += "start:${sec.code}/$t" },
                    onStartExam = { events += "exam:$it" }, onNotice = { events += "notice" },
                ))
            }
        }
        rule.onNodeWithText("N5 모의고사").assertIsDisplayed()
        rule.onNodeWithText("40문항 · 기준 시간 60분").assertIsDisplayed()
        rule.onNodeWithText("청해 영역은 포함하지 않습니다.").assertIsDisplayed()
        rule.onNodeWithTag("practice_level_chip_N4").performClick()
        rule.onNodeWithTag("practice_type_vocab_kanji-reading").performClick()
        rule.onNodeWithTag("practice_exam_start").performClick()
        rule.onNodeWithTag("practice_notice").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("자세히").performScrollTo().performClick()
        assertEquals(listOf("lv:N4", "start:vocab/kanji-reading", "exam:N5", "notice"), events)
        assertEquals(0, rule.onAllNodesWithText("청해").fetchSemanticsNodes().size)
    }

    @Test fun `이어서 풀기 카드의 포기하기는 대화상자를 거치고 모자라면 시작이 비활성이다`() {
        val resume = ResumeExamUi(7, JlptLevel.N4, 3, 40, 125_000L)
        val base = PracticeHomeUiState(
            PracticeHomeStatus.LOADED, JlptLevel.N5, listOf(JlptLevel.N5), listOf(PracticeTypeUi(Section.VOCAB, "usage", 1)),
            ExamCardUi(JlptLevel.N5, 10, 20 * 60_000L, true), resume,
        )
        var state by mutableStateOf(base)
        rule.setContent {
            JlptTheme {
                PracticeHomeScreen(state, actions = PracticeHomeActions(
                    onResume = { events += "resume:$it" }, onRequestAbandon = { events += "ask"; state = state.copy(confirmAbandon = true) },
                    onConfirmAbandon = { events += "abandon" }, onCancelAbandon = { events += "cancel"; state = state.copy(confirmAbandon = false) },
                ))
            }
        }
        rule.onNodeWithText("N4 모의고사 · 3/40 문항 답함 · 경과 02:05").assertIsDisplayed()
        rule.onNodeWithTag("practice_exam_start").assertIsNotEnabledCompat()
        // TalkBack: "모의고사 시작, 문항이 모자라 아직 볼 수 없습니다, 사용 중지됨"(ui-design S12) — 이유가 버튼 낭독에 들어 있다
        val start = rule.onNodeWithTag("practice_exam_start").fetchSemanticsNode().config
        assertEquals("모의고사 시작", start[SemanticsProperties.ContentDescription].joinToString())
        assertEquals("문항이 모자라 아직 볼 수 없습니다", start[SemanticsProperties.StateDescription])
        rule.onNodeWithText("이어서 풀기").performClick()
        rule.onNodeWithTag("practice_abandon").performClick()
        rule.onNodeWithText("모의고사를 포기할까요?").assertIsDisplayed()
        rule.onNodeWithText("이 모의고사는 결과 없이 끝납니다. 고른 답은 기록에 남습니다.").assertIsDisplayed()
        rule.onNodeWithText("취소").performClick()
        assertEquals(listOf("resume:7", "ask", "cancel"), events)
        rule.onNodeWithTag("practice_abandon").performClick()
        rule.onNodeWithTag("practice_abandon_confirm").performClick()
        assertEquals("abandon", events.last())
        state = base.copy(confirmReplace = resume)
        assertEquals(
            "N4 모의고사, 3/40 문항 답함, 경과 2분 5초. 새로 시작하면 풀던 모의고사는 결과 없이 끝납니다(고른 답은 기록에 남습니다).",
            rule.onNodeWithTag("practice_replace_body").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(),
        )
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsNotEnabledCompat() {
        assertTrue(fetchSemanticsNode().config.contains(SemanticsProperties.Disabled))
    }

    private fun question(stem: String = "これは[[{漢字|かんじ}]]です。") = quizQuestion(qc("q-n5-vocab-0001", stem = stem))

    @Test fun `지문에서는 emphasis 구간에만 밑줄이 있다`() {
        rule.setContent { JlptTheme { QuizScreen(QuizUiState(QuizStatus.ANSWERING, total = 10, question = question())) } }
        assertEquals(1, rule.onAllNodesWithTag("ruby_underline", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `대상 구간이 없으면 밑줄이 없다`() {
        rule.setContent { JlptTheme { QuizScreen(QuizUiState(QuizStatus.ANSWERING, total = 10, question = question("これは{漢字|かんじ}です。"))) } }
        assertEquals(0, rule.onAllNodesWithTag("ruby_underline", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `지시문의 대상 구간에 ja-JP가 걸린다`() {
        rule.setContent { JlptTheme { QuizScreen(QuizUiState(QuizStatus.ANSWERING, total = 10, question = question())) } }
        val t = rule.onNodeWithTag("quiz_instruction").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals("밑줄 친 漢字의 읽기로 알맞은 것을 고르세요.", t.text)
        val ja = spans(t).filter { it.item.localeList == JpSpan.localeList }
        assertEquals(1, ja.size)
        assertEquals("漢字", t.text.substring(ja[0].start, ja[0].end))
    }

    @Test fun `FEEDBACK의 해설 카드에 출처 칩이 있다`() {
        val q = question()
        rule.setContent { JlptTheme { QuizScreen(QuizUiState(QuizStatus.FEEDBACK, total = 10, question = q, chosenIndex = 1)) } }
        rule.onNodeWithText(ProvenanceLabel.of(q.provenance)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("해설 검토 중").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("quiz_next").assertIsDisplayed()
    }

    @Test fun `선택지 노드는 ja-JP 낭독 문구와 RadioButton Selected OnClick을 함께 가지며 contentDescription이 없다`() {
        val items = FakeQuizContent(listOf(qc("q-n5-vocab-0001")))
        val quiz = FakeQuiz()
        val id = kotlinx.coroutines.runBlocking { quiz.startAttempt(null, 5L, listOf("q-n5-vocab-0001"), 1L) }
        val vm = QuizViewModel(id, QuizSessionLoader(quiz, items), quiz, MemStudy(), TickClock())
        rule.setContent { JlptTheme { QuizRoute(vm, onExit = {}, onResult = {}) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("quiz_choice_1").fetchSemanticsNodes().isNotEmpty() }
        (1..4).forEach { n ->
            val c = rule.onNodeWithTag("quiz_choice_$n").fetchSemanticsNode().config
            val t = c[SemanticsProperties.Text].single()
            assertTrue("선택지 $n 일본어 구간", spans(t).any { it.item.localeList == JpSpan.localeList })
            assertNull(c.getOrNull(SemanticsProperties.ContentDescription))
            assertEquals(Role.RadioButton, c[SemanticsProperties.Role])
            assertEquals(false, c[SemanticsProperties.Selected])
            assertTrue(c.contains(SemanticsActions.OnClick))
        }
        rule.onNodeWithTag("quiz_choice_2").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("quiz_next").fetchSemanticsNodes().isNotEmpty() }
        (1..4).forEach { n ->
            assertEquals("선택지 $n", n == 2, rule.onNodeWithTag("quiz_choice_$n").fetchSemanticsNode().config[SemanticsProperties.Selected])
        }
        assertEquals(1, quiz.answerRows.size)
        // 정답/오답 문구가 같은 노드의 낭독에 합쳐진다
        val spoken = rule.onNodeWithTag("quiz_choice_2").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertTrue(spoken, spoken.endsWith("정답") || spoken.endsWith("오답 · 내가 고른 답"))
        assertFalse(rule.onNodeWithTag("quiz_choice_2").fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription))
        assertNotNull(rule.onNodeWithTag("quiz_feedback").fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test fun `다음 문항으로 바뀌면 접근성 포커스가 진행 표시로 옮겨진다`() {
        var state by mutableStateOf(QuizUiState(QuizStatus.FEEDBACK, index = 0, total = 10, question = question(), chosenIndex = 1))
        rule.setContent { JlptTheme { QuizScreen(state) } }
        rule.onNodeWithTag("quiz_progress").assertIsFocused()
        rule.onNodeWithTag("quiz_choice_1").performClick() // 채점 뒤 선택지를 눌러도 포커스를 가져가거나 값을 바꾸지 않는다
        state = QuizUiState(QuizStatus.ANSWERING, index = 1, total = 10, question = quizQuestion(qc("q-n5-vocab-0002")))
        rule.waitForIdle()
        rule.onNodeWithTag("quiz_progress").assertIsFocused()
        assertEquals("2/10", rule.onNodeWithTag("quiz_progress").fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
    }

    @Test fun `채점 뒤 선택지는 비활성으로 읽히지 않고 역할과 선택만 남는다`() {
        rule.setContent { JlptTheme { QuizScreen(QuizUiState(QuizStatus.FEEDBACK, total = 10, question = question(), chosenIndex = 2)) } }
        (1..4).forEach { n ->
            val node = rule.onNodeWithTag("quiz_choice_$n")
            node.assertIsEnabled()
            val c = node.fetchSemanticsNode().config
            assertEquals(Role.RadioButton, c[SemanticsProperties.Role])
            assertEquals(n == 3, c[SemanticsProperties.Selected])
            assertFalse(c.contains(SemanticsActions.OnClick))
        }
    }

    @Test fun `유형 행과 자세히 버튼과 경과 시간은 동작 이름과 목적지와 분 초로 읽힌다`() {
        val s = PracticeHomeUiState(
            PracticeHomeStatus.LOADED, JlptLevel.N5, listOf(JlptLevel.N5), listOf(PracticeTypeUi(Section.VOCAB, "kanji-reading", 12)),
            resume = ResumeExamUi(7, JlptLevel.N4, 3, 40, 22 * 60_000L + 5_000L),
        )
        rule.setContent { JlptTheme { PracticeHomeScreen(s) } }
        val row = rule.onNodeWithTag("practice_type_vocab_kanji-reading").fetchSemanticsNode().config
        assertEquals(context.getString(R.string.practice_type_start), row[SemanticsActions.OnClick].label)
        val more = rule.onNodeWithText("자세히").performScrollTo().fetchSemanticsNode().config
        assertEquals(context.getString(R.string.practice_notice_more_a11y), more[SemanticsProperties.ContentDescription].single())
        rule.onNodeWithText("N4 모의고사 · 3/40 문항 답함 · 경과 22:05").assertIsDisplayed()
        val spoken = rule.onNodeWithText("N4 모의고사 · 3/40 문항 답함 · 경과 22:05").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(spoken, spoken.endsWith("경과 22분 5초"))
    }

    @Test fun `빈칸 토큰은 글자가 아니라 칸으로 그려진다`() {
        val q = quizQuestion(qc("q-n5-grammar-0001", section = Section.GRAMMAR, type = "sentence-order", target = null, stem = "これは＿＿＿★＿＿＿です。"))
        rule.setContent { JlptTheme { QuizScreen(QuizUiState(QuizStatus.ANSWERING, total = 10, question = q)) } }
        rule.onNodeWithContentDescription("이 칸").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("＿", substring = true).fetchSemanticsNodes().size)
    }
}
