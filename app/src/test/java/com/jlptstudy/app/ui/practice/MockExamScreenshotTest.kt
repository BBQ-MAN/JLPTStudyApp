package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.ui.dictionary.DictShots
import com.jlptstudy.quiz.Section
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S14 모의고사 스크린샷(ui-design 12절 이름, 합성 픽스처·고정 경과 시간). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class MockExamScreenshotTest : DictShots() {
    private val titles = listOf("언어지식(문자·어휘)", "언어지식(문법)·독해")
    private val intro = ExamStartUiState(
        ExamStartStatus.LOADED, JlptLevel.N5, listOf(ExamSectionLine(titles[0], 25, 20), ExamSectionLine(titles[1], 20, 40)), 45, 60 * 60_000L,
    )
    private val q = qc(
        "q-n5-vocab-0001", stem = "毎日、[[{駅|えき}]]まで歩きます。", target = "駅", choices = listOf("えき", "ひろ", "たき", "だい"), answer = 0,
    )
    private val nav = QuizUiState(
        QuizStatus.ANSWERING, QuizMode.EXAM, attemptId = 7, index = 23, total = 45, question = quizQuestion(q), chosenIndex = 1,
        answeredIndices = (0..20).toSet() - setOf(4, 9) + 23, sectionStarts = listOf(0, 25), sectionTitles = titles,
        elapsedMs = 754_000L, limitMs = 60 * 60_000L, unansweredCount = 23,
    )

    @Test fun mock_exam_intro() = shots("mock_exam_intro") { ExamStartScreen(intro) }

    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun mock_exam_intro_font200() = shots("mock_exam_intro", fontScale = 2f) { ExamStartScreen(intro) }

    @Test fun mock_exam_intro_shortfall() = shots("mock_exam_intro_shortfall") {
        ExamStartScreen(intro.copy(sections = listOf(ExamSectionLine(titles[0], 25, 20), ExamSectionLine(titles[1], 12, 40)), total = 37, hasShortfall = true))
    }

    @Test fun mock_exam_intro_failed() = shots("mock_exam_intro_failed") { ExamStartScreen(ExamStartUiState(ExamStartStatus.FAILED, JlptLevel.N5)) }

    @Test fun mock_exam_nav() = shots("mock_exam_nav") { QuizScreen(nav) }

    @Config(qualifiers = "w360dp-h1400dp-xhdpi")
    @Test fun mock_exam_nav_font200() = shots("mock_exam_nav", fontScale = 2f) { QuizScreen(nav) }

    @Test fun mock_exam_over_time() = shots("mock_exam_over_time") { QuizScreen(nav.copy(elapsedMs = 61 * 60_000L + 5_000)) }

    @Test fun mock_exam_confirm_exit() = shots("mock_exam_confirm_exit", dialog = true) { QuizScreen(nav.copy(status = QuizStatus.CONFIRM_EXIT)) }

    @Test fun mock_exam_confirm_submit() = shots("mock_exam_confirm_submit", dialog = true) { QuizScreen(nav.copy(status = QuizStatus.CONFIRM_SUBMIT)) }

    @Test fun mock_exam_resume_unavailable() = shots("mock_exam_resume_unavailable") {
        QuizScreen(QuizUiState(QuizStatus.EMPTY, QuizMode.EXAM, attemptId = 7))
    }

    @Config(qualifiers = "w360dp-h900dp-xhdpi")
    @Test fun practice_home_confirm_replace() = shots("practice_home_confirm_replace", dialog = true) {
        val r = ResumeExamUi(7, JlptLevel.N4, 12, 40, 1_325_000L)
        PracticeHomeScreen(
            PracticeHomeUiState(
                PracticeHomeStatus.LOADED, JlptLevel.N5, listOf(JlptLevel.N5, JlptLevel.N4),
                listOf(PracticeTypeUi(Section.VOCAB, "kanji-reading", 24)), ExamCardUi(JlptLevel.N5, 45, 60 * 60_000L, false), resume = r, confirmReplace = r,
            ),
        )
    }
}
