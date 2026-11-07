package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.ui.dictionary.DictShots
import com.jlptstudy.quiz.ExamSectionScore
import com.jlptstudy.quiz.Score
import com.jlptstudy.quiz.Section
import com.jlptstudy.quiz.SectionScore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S15 스크린샷(ui-design 12절 이름, 합성 픽스처·고정 값). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class ResultScreenshotTest : DictShots() {
    private val kanjiQ = qc(
        "q-n5-vocab-0001", stem = "毎日、[[{駅|えき}]]まで歩きます。", target = "駅", choices = listOf("えき", "ひろ", "たき", "だい"), answer = 0,
        why = listOf(null, "「ひろ」は「広」の読みで、この字の読みではありません。", "「たき」は「滝」の読みです。", "「だい」は「台」の読みです。"),
    )

    // 모의고사 한 시도: 번호·유형이 섞이게 영역과 유형을 돌려 가며 만든다
    private val kinds = listOf(Section.VOCAB to "kanji-reading", Section.VOCAB to "context", Section.GRAMMAR to "grammar-form", Section.READING to "short")
    private fun item(i: Int, chosen: Int?, correct: Boolean, script: Boolean = false) = QuizReviewItemUi(
        i, quizQuestion(qc("q-n5-vocab-%04d".format(i + 1), section = kinds[i % 4].first, type = kinds[i % 4].second)), chosen, correct, script,
    )

    private val mixed = listOf(item(0, 0, true), item(1, 2, false), item(2, null, false), item(3, 0, true), item(4, 1, false, script = true), item(5, 0, true))

    private val exam = QuizResultUiState(
        QuizResultStatus.LOADED, QuizMode.EXAM, JlptLevel.N5,
        score = Score(
            listOf(SectionScore(Section.VOCAB, 20, 25), SectionScore(Section.GRAMMAR, 8, 12), SectionScore(Section.READING, 6, 8)),
            listOf(ExamSectionScore("vocab", "언어지식(문자·어휘)", 20, 25), ExamSectionScore("grammar-reading", "언어지식(문법)·독해", 14, 20)),
            34, 45, 3,
        ),
        elapsedMs = 41 * 60_000L + 12_000, limitMs = 60 * 60_000L, items = mixed,
    )
    private val practice = QuizResultUiState(
        QuizResultStatus.LOADED, QuizMode.PRACTICE, JlptLevel.N5, "kanji-reading", Score(listOf(SectionScore(Section.VOCAB, 7, 10)), emptyList(), 7, 10, 1),
        items = listOf(item(1, 2, false), item(4, null, false), item(8, 3, false)),
    )

    @Test fun quiz_result() = shots("quiz_result") { QuizResultScreen(exam) }

    @Config(qualifiers = "w360dp-h2800dp-xhdpi")
    @Test fun quiz_result_font200() = shots("quiz_result", fontScale = 2f) { QuizResultScreen(exam) }

    @Test fun quiz_result_practice() = shots("quiz_result_practice") { QuizResultScreen(practice) }

    @Test fun quiz_result_all_correct() = shots("quiz_result_all_correct") {
        QuizResultScreen(
            practice.copy(score = Score(listOf(SectionScore(Section.VOCAB, 10, 10)), emptyList(), 10, 10, 0), items = listOf(item(0, 0, true))),
        )
    }

    @Test fun quiz_result_retired_some() = shots("quiz_result_retired_some") { QuizResultScreen(
            practice.copy(
                score = Score(listOf(SectionScore(Section.VOCAB, 5, 8)), emptyList(), 5, 8, 1), retiredCount = 2, filter = ResultFilter.ALL,
                items = listOf(item(0, 0, true), item(1, 2, false), item(2, 0, true), item(3, null, false), item(4, 0, true), item(5, 1, false), item(6, 0, true), item(7, 0, true)),
            ),
        ) }

    @Test fun quiz_result_empty() = shots("quiz_result_empty") {
        QuizResultScreen(QuizResultUiState(QuizResultStatus.EMPTY, QuizMode.EXAM, JlptLevel.N5, retiredCount = 45))
    }

    private val review = QuizReviewItemUi(1, quizQuestion(kanjiQ), 1, false, false)
    private val links = mapOf(
        kanjiQ.id to listOf(TargetLink(TargetKind.VOCAB, "jm:1", "駅"), TargetLink(TargetKind.KANJI, "駅", "駅"), TargetLink(TargetKind.GRAMMAR, "gr-n5-0001", "〜てから")),
    )

    @Config(qualifiers = "w360dp-h1200dp-xhdpi")
    @Test fun quiz_result_explanation() = shots("quiz_result_explanation") {
        QuizResultScreen(practice.copy(items = listOf(item(0, 0, true), review, item(2, 1, false)), reviewIndex = 1, filter = ResultFilter.ALL, targetLinks = links))
    }

    @Config(qualifiers = "w360dp-h3000dp-xhdpi")
    @Test fun quiz_result_explanation_font200() = shots("quiz_result_explanation", fontScale = 2f) {
        QuizResultScreen(practice.copy(items = listOf(item(0, 0, true), review, item(2, 1, false)), reviewIndex = 1, filter = ResultFilter.ALL, targetLinks = links))
    }

    @Config(qualifiers = "w360dp-h1200dp-xhdpi")
    @Test fun quiz_result_explanation_unanswered() = shots("quiz_result_explanation_unanswered") {
        QuizResultScreen(practice.copy(items = listOf(QuizReviewItemUi(0, quizQuestion(kanjiQ), null, false, false)), reviewIndex = 0))
    }
}
