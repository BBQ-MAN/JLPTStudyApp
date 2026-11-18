package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.QuizMode
import com.jlptstudy.app.ui.dictionary.DictShots
import com.jlptstudy.quiz.Score
import com.jlptstudy.quiz.Section
import com.jlptstudy.quiz.SectionScore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 자동 생성 연습 스크린샷(QG4, ui-design 12절 이름·픽스처). 합성 문항. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class GeneratedScreenshotTest : DictShots() {
    // N4 선택: 한자 읽기 7개(비활성, 대상 단어 부족) · 표기 245개 · 뜻 고르기 63개 · 단어 고르기 6개(비활성, 검수된 뜻 부족)
    private val home = PracticeHomeUiState(
        PracticeHomeStatus.LOADED, JlptLevel.N4, listOf(JlptLevel.N5, JlptLevel.N4), emptyList(), null,
        generated = listOf(
            GeneratedTypeUi("kanji-reading", 7, false), GeneratedTypeUi("orthography", 245, true),
            GeneratedTypeUi("meaning-ja-ko", 63, true), GeneratedTypeUi("meaning-ko-ja", 6, false),
        ),
    )

    @Config(qualifiers = "w360dp-h900dp-xhdpi")
    @Test fun practice_home_generated() = shots("practice_home_generated") { PracticeHomeScreen(home) }

    @Config(qualifiers = "w360dp-h1300dp-xhdpi")
    @Test fun practice_home_generated_font200() = shots("practice_home_generated", fontScale = 2f, lightOnly = true) { PracticeHomeScreen(home) }

    @Test fun quiz_generated_kanji_reading() = shots("quiz_generated_kanji_reading") {
        QuizScreen(QuizUiState(QuizStatus.ANSWERING, index = 2, total = 10, question = genKanjiQ))
    }

    @Config(qualifiers = "w360dp-h1100dp-xhdpi")
    @Test fun quiz_generated_meaning_ja_ko() = shots("quiz_generated_meaning_ja_ko") {
        QuizScreen(QuizUiState(QuizStatus.FEEDBACK, index = 2, total = 10, question = genJaKoQ, chosenIndex = 1))
    }

    private fun item(i: Int, chosen: Int?) = QuizReviewItemUi(i, genKanjiQ, chosen, chosen == genKanjiQ.answerIndex, false)

    @Test fun quiz_result_generated() = shots("quiz_result_generated") {
        QuizResultScreen(
            QuizResultUiState(
                QuizResultStatus.LOADED, QuizMode.PRACTICE, JlptLevel.N5, "kanji-reading",
                Score(listOf(SectionScore(Section.VOCAB, 7, 10)), emptyList(), 7, 10, 1),
                items = listOf(item(1, 2), item(4, null), item(8, 3)),
            ),
        )
    }
}
