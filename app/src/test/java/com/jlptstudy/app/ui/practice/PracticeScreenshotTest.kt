package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.dictionary.DictShots
import com.jlptstudy.quiz.Section
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S12·S13 스크린샷(ui-design 12절 이름, 합성 픽스처). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PracticeScreenshotTest : DictShots() {
    private val home = PracticeHomeUiState(
        PracticeHomeStatus.LOADED, JlptLevel.N5, listOf(JlptLevel.N5, JlptLevel.N4, JlptLevel.N3),
        listOf(
            PracticeTypeUi(Section.VOCAB, "kanji-reading", 24), PracticeTypeUi(Section.VOCAB, "orthography", 18),
            PracticeTypeUi(Section.VOCAB, "context", 20), PracticeTypeUi(Section.GRAMMAR, "grammar-form", 30),
            PracticeTypeUi(Section.GRAMMAR, "sentence-order", 12), PracticeTypeUi(Section.READING, "short", 8),
        ),
        ExamCardUi(JlptLevel.N5, 45, 75 * 60_000L, false),
    )
    private val kanjiQ = qc(
        "q-n5-vocab-0001", stem = "毎日、[[{駅|えき}]]まで歩きます。", target = "駅", choices = listOf("えき", "ひろ", "たき", "だい"), answer = 0,
        why = listOf(null, "「ひろ」は「広」の読みで、この字の読みではありません。", "「たき」は「滝」の読みです。", "「だい」は「台」の読みです。"),
    )

    @Config(qualifiers = "w360dp-h900dp-xhdpi")
    @Test fun practice_home() = shots("practice_home") { PracticeHomeScreen(home) }

    @Config(qualifiers = "w360dp-h900dp-xhdpi")
    @Test fun practice_home_resume() = shots("practice_home_resume") {
        PracticeHomeScreen(home.copy(resume = ResumeExamUi(7, JlptLevel.N4, 12, 40, 1_325_000L)))
    }

    @Test fun practice_choice() = shots("practice_choice") {
        QuizScreen(QuizUiState(QuizStatus.ANSWERING, index = 2, total = 10, question = quizQuestion(kanjiQ)))
    }

    @Config(qualifiers = "w360dp-h1100dp-xhdpi")
    @Test fun practice_feedback() = shots("practice_feedback") {
        QuizScreen(QuizUiState(QuizStatus.FEEDBACK, index = 2, total = 10, question = quizQuestion(kanjiQ), chosenIndex = 1))
    }

    @Config(qualifiers = "w360dp-h2400dp-xhdpi")
    @Test fun practice_feedback_font200() = shots("practice_feedback", fontScale = 2f) {
        QuizScreen(QuizUiState(QuizStatus.FEEDBACK, index = 2, total = 10, question = quizQuestion(kanjiQ), chosenIndex = 1))
    }
}
