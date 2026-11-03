package com.jlptstudy.app.ui.grammar

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.content.ConnectionUi
import com.jlptstudy.app.data.content.ContrastUi
import com.jlptstudy.app.data.content.ExampleUi
import com.jlptstudy.app.data.content.GrammarDetailUi
import com.jlptstudy.app.data.content.GrammarRow
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.review.MeaningUi

// 합성 픽스처(시험용 문구). 실제 학습 콘텐츠가 아니다.
val sampleGrammarRows = listOf(
    GrammarRow("gr-n5-0001", JlptLevel.N5, "〜てください", "시험용 대응 표현 하나"),
    GrammarRow("gr-n5-0002", JlptLevel.N5, "〜なさい", "시험용 대응 표현 둘, 길게 이어지는 시험용 문장으로 두 줄을 넘기면 말줄임이 되는지 확인하는 줄입니다. 한 번 더 이어 붙입니다."),
    GrammarRow("gr-n4-0001", JlptLevel.N4, "〜たほうがいい", "시험용 대응 표현 셋"),
)

private fun ex(ja: String, ko: String) = ExampleUi(Furigana.parse(ja), ko, "authored")

val sampleGrammarDetail = GrammarDetailUi(
    id = "gr-n5-0001", level = JlptLevel.N5, title = "〜てください", meaningKo = "시험용 대응 표현 (시험 ひらがな 포함)",
    explanationKo = "시험용 설명 첫 문단입니다. 일본어 구간 「ください」가 섞여 있습니다.\n\n시험용 설명 둘째 문단입니다.",
    connections = listOf(
        ConnectionUi("동사", Furigana.parse("て形"), Furigana.parse("{食|た}べて")),
        ConnectionUi("い형용사", Furigana.parse("〜くて"), Furigana.parse("{高|たか}くて")),
    ),
    examples = listOf(
        ex("これを[[{見|み}てください]]。", "시험 번역 1"),
        ex("{名前|なまえ}を[[{書|か}いてください]]。", "시험 번역 2"),
        ex("{少|すこ}し[[{待|ま}ってください]]。", "시험 번역 3"),
    ),
    contrasts = listOf(
        ContrastUi(
            "gr-n5-0002", "〜なさい",
            ex("これを[[{見|み}てください]]。", "시험 번역 가"), ex("これを[[{見|み}なさい]]。", "시험 번역 나"),
        ),
    ),
    provenance = MeaningUi("시험용 설명 첫 문단입니다.", "authored", "llm:test-model@2026-11-03", "unreviewed", null, null),
    needsReview = true,
)

val grammarDetailState = GrammarDetailUiState(com.jlptstudy.app.ui.detail.DetailStatus.LOADED, sampleGrammarDetail)
val grammarListState = GrammarListUiState(
    GrammarListStatus.LOADED, setOf(JlptLevel.N5, JlptLevel.N4), sampleGrammarRows, setOf(JlptLevel.N5, JlptLevel.N4),
)
