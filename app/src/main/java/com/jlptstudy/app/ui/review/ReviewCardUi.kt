package com.jlptstudy.app.ui.review

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.ui.common.RubySegment

// architecture.md 7절.
enum class VocabDirection { JA_TO_KO, KO_TO_JA }

data class WordRef(val vocabId: String, val written: String?, val reading: String, val meaningKo: String)

data class MeaningUi(
    val textKo: String, val koSource: String, val koMethod: String, val reviewStatus: String,
    val reviewedAt: String?, val reviewerKind: String?,
)

sealed interface ReviewCardUi {
    val cardId: String

    data class Vocab(
        override val cardId: String, val direction: VocabDirection, val level: JlptLevel,
        val written: String?, val reading: String, val ruby: List<RubySegment>,
        val pos: List<String>, // pos 코드 그대로. 한국어 이름표 표는 카드 화면 마일스톤(M4)이 더한다.
        val meanings: List<MeaningUi>,
    ) : ReviewCardUi

    data class Kanji(
        override val cardId: String, val level: JlptLevel, val literal: String, val strokeCount: Int,
        val on: List<String>, val kun: List<String>, val koreanReadings: List<String>, val words: List<WordRef>,
    ) : ReviewCardUi

    /** 문법 카드(ui-design 6.9.1). example = grammar_example ord 0의 ja(`[[…]]` → emphasis), summaryKo = 설명 첫 문단. */
    data class Grammar(
        override val cardId: String, val level: JlptLevel, val title: String, val example: List<RubySegment>,
        val exampleKo: String, val meaningKo: String, val summaryKo: String, val provenance: MeaningUi, val needsReview: Boolean,
    ) : ReviewCardUi
}
