package com.jlptstudy.app.ui.practice

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.R
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.data.QuizSessionLoader
import com.jlptstudy.app.ui.common.ProvenanceLabel
import com.jlptstudy.app.ui.review.MemStudy
import com.jlptstudy.app.ui.theme.JlptTheme
import com.jlptstudy.quiz.ExamSectionScore
import com.jlptstudy.quiz.Score
import com.jlptstudy.quiz.Section
import com.jlptstudy.quiz.SectionScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class QuizResultUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun desc(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private fun rows(): List<String> = (0 until 40).filter { rule.onAllNodesWithTag("result_row_$it").fetchSemanticsNodes().isNotEmpty() }.map { "$it" }

    private fun item(i: Int, chosen: Int?, correct: Boolean, needsReview: Boolean = true, script: Boolean = false) =
        QuizReviewItemUi(i, quizQuestion(qc("q-n5-vocab-%04d".format(i + 1), needsReview = needsReview)), chosen, correct, script)

    private fun score(c: Int, t: Int, un: Int = 0, exam: List<ExamSectionScore> = emptyList(), sections: List<SectionScore> = listOf(SectionScore(Section.VOCAB, c, t))) =
        Score(sections, exam, c, t, un)

    private fun state(
        items: List<QuizReviewItemUi>, mode: QuizMode = QuizMode.PRACTICE, score: Score = score(2, 4), filter: ResultFilter = ResultFilter.WRONG,
        retired: Int = 0, reviewIndex: Int? = null, links: Map<String, List<TargetLink>> = emptyMap(),
    ) = QuizResultUiState(
        QuizResultStatus.LOADED, mode, JlptLevel.N5, "kanji-reading", score, filter = filter, items = items, retiredCount = retired,
        reviewIndex = reviewIndex, targetLinks = links,
    )

    private val four = listOf(item(0, 0, true), item(1, 1, false), item(2, null, false), item(3, 0, true))

    // --- 목록 ---

    @Config(qualifiers = "w360dp-h1600dp-xhdpi")
    @Test fun `WRONG은 틀렸거나 미응답인 항목만, ALL은 전부 보인다`() {
        val vm = world(four)
        rule.setContent { JlptTheme { QuizResultRoute(vm, {}, {}, {}) } }
        assertEquals(listOf("1", "2"), rows())
        rule.onNodeWithTag("result_filter_all").performClick()
        assertEquals(listOf("0", "1", "2", "3"), rows())
        rule.onNodeWithTag("result_filter_wrong").performClick()
        assertEquals(listOf("1", "2"), rows())
    }

    @Test fun `틀린 문항이 없으면 안내를 보이고 전체 칩으로 볼 수 있다`() {
        rule.setContent { JlptTheme { QuizResultScreen(state(listOf(item(0, 0, true)), score = score(1, 1))) } }
        rule.onNodeWithTag("result_none_wrong").assertIsDisplayed()
        rule.onNodeWithText("틀린 문항이 없습니다").assertIsDisplayed()
        assertEquals(emptyList<String>(), rows())
    }

    @Test fun `항목을 누르면 같은 라우트 안에서 해설이 보이고 시스템 뒤로는 reviewIndex를 null로 하고 스크롤 위치가 남는다`() {
        val many = List(30) { item(it, null, false) }
        val vm = world(many)
        rule.setContent { JlptTheme { QuizResultRoute(vm, {}, {}, {}) } }
        rule.onNodeWithTag("result_list").performScrollToIndex(24)
        rule.onNodeWithTag("result_row_20").assertIsDisplayed().performClick()
        assertEquals(20, vm.state.value.reviewIndex)
        rule.onNodeWithTag("result_review").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTag("result_list").fetchSemanticsNodes().size)
        rule.onNodeWithText("21번 문항").assertIsDisplayed()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertNull(vm.state.value.reviewIndex)
        rule.onNodeWithTag("result_list").assertIsDisplayed()
        rule.onNodeWithTag("result_row_20").assertIsDisplayed() // 같은 스크롤 위치
    }

    @Test fun `이전 문항과 다음 문항은 현재 필터 안에서 움직이고 끝에서 비활성이다`() {
        val vm = world(four)
        rule.setContent { JlptTheme { QuizResultRoute(vm, {}, {}, {}) } }
        rule.onNodeWithTag("result_row_1").performClick()
        rule.onNodeWithTag("result_prev").assertIsNotEnabled()
        rule.onNodeWithTag("result_next").performClick()
        assertEquals(2, vm.state.value.reviewIndex)
        rule.onNodeWithTag("result_next").assertIsNotEnabled()
    }

    @Test fun `EXAM 영역 줄은 examSections 템플릿 이름이고 PRACTICE는 sections가 둘 이상일 때만 보인다`() {
        val exam = score(
            3, 6, exam = listOf(ExamSectionScore("vocab", "언어지식(문자·어휘)", 1, 3), ExamSectionScore("gr-read", "언어지식(문법)·독해", 2, 3)),
            sections = listOf(SectionScore(Section.VOCAB, 1, 3), SectionScore(Section.GRAMMAR, 1, 2), SectionScore(Section.READING, 1, 1)),
        )
        rule.setContent { JlptTheme { QuizResultScreen(state(four, QuizMode.EXAM, exam).copy(elapsedMs = 61 * 60_000L + 5_000, limitMs = 60 * 60_000L)) } }
        rule.onNodeWithText("언어지식(문자·어휘) 1/3 (33%)", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("언어지식(문법)·독해 2/3 (67%)", useUnmergedTree = true).assertExists()
        assertEquals("언어지식(문법)·독해, 2/3, 67퍼센트", desc("result_section_1"))
        assertEquals(0, rule.onAllNodesWithTag("result_section_2").fetchSemanticsNodes().size)
        rule.onNodeWithText("청해 영역은 포함하지 않았습니다.").assertExists()
        rule.onNodeWithText("답하지 않은 문항 0개").assertDoesNotExist()
        rule.onNodeWithText("걸린 시간 61분 5초 · 기준 60분 지남").assertExists()
    }

    @Test fun `PRACTICE 영역 줄은 sections 이름이고 한 영역이면 줄이 없다`() {
        val two = score(2, 4, sections = listOf(SectionScore(Section.VOCAB, 1, 2), SectionScore(Section.GRAMMAR, 1, 2)))
        rule.setContent { JlptTheme { QuizResultScreen(state(four, score = two)) } }
        rule.onNodeWithText("문자·어휘 1/2 (50%)", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("문법 1/2 (50%)", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("청해 영역은 포함하지 않았습니다.").assertDoesNotExist()
    }

    @Test fun `연습의 한 영역은 영역 줄과 시간 줄이 없다`() {
        rule.setContent { JlptTheme { QuizResultScreen(state(four)) } }
        assertEquals(0, rule.onAllNodesWithTag("result_section_0").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithTag("result_time").fetchSemanticsNodes().size)
        assertEquals("정답 2개, 전체 4개, 50퍼센트", desc("result_total"))
        rule.onNodeWithText("N5 한자 읽기 연습 결과").assertExists()
    }

    @Test fun `retiredCount가 양수면 빠진 문항 안내가 보이고 0이면 없다`() {
        rule.setContent { JlptTheme { QuizResultScreen(state(four, retired = 3)) } }
        rule.onNodeWithText("콘텐츠 업데이트로 빠진 문항 3개는 채점에서 뺐습니다").assertIsDisplayed()
    }

    @Test fun `빠진 문항이 없으면 안내가 없다`() {
        rule.setContent { JlptTheme { QuizResultScreen(state(four)) } }
        assertEquals(0, rule.onAllNodesWithTag("result_retired").fetchSemanticsNodes().size)
    }

    @Test fun `EMPTY는 합계 자리에 안내만 보이고 고지 카드는 남는다`() {
        rule.setContent { JlptTheme { QuizResultScreen(QuizResultUiState(QuizResultStatus.EMPTY, QuizMode.EXAM, JlptLevel.N5, retiredCount = 2)) } }
        rule.onNodeWithText("채점할 문항이 없습니다").assertIsDisplayed()
        rule.onNodeWithTag("result_notice").assertIsDisplayed()
        assertEquals(emptyList<String>(), rows())
        rule.onNodeWithText("연습으로").assertIsDisplayed()
    }

    @Test fun `고지 카드는 고정 문구이고 행 노드는 번호 유형 결과 정답 내 답을 읽는다`() {
        rule.setContent { JlptTheme { QuizResultScreen(state(listOf(item(0, 1, false, script = true), item(1, null, false)), filter = ResultFilter.ALL)) } }
        val text = rule.onNodeWithTag("result_notice_text").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals(context.getString(R.string.result_notice), text)
        assertTrue(text.startsWith("원점수입니다. 실제 시험의 척도 점수나 합격 여부와 다르"))
        assertEquals("1번, 한자 읽기, 오답, 정답 1번, 내 답 2번, 대본으로 풂", desc("result_row_0"))
        assertEquals("2번, 한자 읽기, 답하지 않음, 정답 1번", desc("result_row_1"))
    }

    // --- 해설 ---

    @Test fun `해설 카드에 question 행 P로 만든 출처 칩이 있고 needs_review면 해설 검토 중 칩이 있다`() {
        val review = item(0, 1, false, needsReview = true)
        rule.setContent { JlptTheme { QuizResultScreen(state(listOf(review), reviewIndex = 0)) } }
        rule.onNodeWithText(ProvenanceLabel.of(review.question.provenance)).assertExists()
        rule.onNodeWithText("해설 검토 중").assertExists()
        rule.onNodeWithTag("result_review_unanswered", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `needs_review가 0이면 검토 중 칩이 없고 답하지 않음은 선택지 아래에 보인다`() {
        rule.setContent { JlptTheme { QuizResultScreen(state(listOf(item(0, null, false, needsReview = false)), reviewIndex = 0)) } }
        rule.onNodeWithText("해설 검토 중").assertDoesNotExist()
        rule.onNodeWithTag("result_review_unanswered").assertExists()
    }

    @Test fun `관련 링크는 눌리면 콜백으로 전달되고 링크가 없으면 줄째 숨는다`() {
        val review = item(0, 1, false)
        val got = mutableListOf<TargetLink>()
        val link = TargetLink(TargetKind.KANJI, "駅", "駅")
        rule.setContent {
            JlptTheme {
                QuizResultScreen(state(listOf(review), reviewIndex = 0, links = mapOf(review.question.id to listOf(link))), QuizResultActions(onTarget = { got += it }))
            }
        }
        rule.onNodeWithText("관련 단어·한자·문법 보기").assertExists()
        rule.onNodeWithTag("result_link_駅").performScrollTo().performClick()
        assertEquals(listOf(link), got)
    }

    @Test fun `링크가 없으면 관련 단어·한자·문법 보기 줄이 없다`() {
        rule.setContent { JlptTheme { QuizResultScreen(state(listOf(item(0, 1, false)), reviewIndex = 0)) } }
        assertEquals(0, rule.onAllNodesWithTag("result_links").fetchSemanticsNodes().size)
    }

    // 합성 시도를 저장소·콘텐츠 가짜로 만들어 실제 ViewModel을 붙인다
    private fun world(items: List<QuizReviewItemUi>): QuizResultViewModel {
        val content = FakeQuizContent(items.map { qc(it.question.id) })
        val quiz = FakeQuiz()
        val id = runBlocking { quiz.startAttempt(null, 42L, content.items.map { it.id }, 1L) }
        items.forEach { it.chosenIndex?.let { c -> quiz.answer(id, it.question.id, c, it.correct) } }
        return QuizResultViewModel(id, QuizSessionLoader(quiz, content), LinkContent(), LinkGrammar(), MemStudy(), androidx.lifecycle.SavedStateHandle())
    }
}
