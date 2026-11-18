package com.jlptstudy.app.ui.listening

import com.jlptstudy.app.JlptLevel
import com.jlptstudy.app.data.attribution.AttributionData
import com.jlptstudy.app.data.attribution.AttributionRepository
import com.jlptstudy.app.data.attribution.LinkUi
import com.jlptstudy.app.data.attribution.NoticeUi
import com.jlptstudy.app.data.content.ListeningLine
import com.jlptstudy.app.data.content.QuizContent
import com.jlptstudy.app.speech.SpeakerState
import com.jlptstudy.app.ui.common.Furigana
import com.jlptstudy.app.ui.practice.ListeningLineUi
import com.jlptstudy.app.ui.practice.QuizQuestionUi
import com.jlptstudy.app.ui.practice.provenance
import com.jlptstudy.quiz.Section

// 합성 청해 문항(실제 콘텐츠가 아니다).
val sampleLines = listOf(
    ListeningLine("N", "{男|おとこ}の{人|ひと}と{女|おんな}の{人|ひと}が{話|はな}しています。", null),
    ListeningLine("M", "{今日|きょう}は{何|なに}をしますか。", null),
    ListeningLine("F", "{図書館|としょかん}へ{行|い}きます。", "としょかんへいきます。"),
)

fun ls(
    id: String, type: String = "task", question: String? = "{女|おんな}の{人|ひと}はこのあと{何|なに}をしますか。", lines: List<ListeningLine> = sampleLines,
    choices: List<String> = listOf("本を借りる", "本を返す", "勉強する", "帰る"), answer: Int = 1, level: JlptLevel = JlptLevel.N5,
) = QuizContent(
    id, level, Section.LISTENING, type, question.orEmpty(), null, null, lines, choices, answer, "시험 해설 첫 문단\n\n시험 해설 둘째 문단",
    listOf("틀린 이유 가", null, "틀린 이유 다", "틀린 이유 라"), emptyList(), provenance, false,
)

fun listeningQuestionUi(c: QuizContent = ls("ls-n5-0001"), answer: Int = 1): QuizQuestionUi {
    val speech = ScriptSpeech.lines(c.lines!!)
    return QuizQuestionUi(
        c.id, c.section, c.type, c.target, Furigana.parse(c.stem), null, c.choices.map(Furigana::parse), answer, c.explanationKo, c.whyWrongKo,
        c.targets, c.provenance, c.needsReview,
        c.lines!!.mapIndexed { i, l -> ListeningLineUi(speech[i].voice, Furigana.parse(l.text), speech[i].text) },
    )
}

fun noticeN12() = NoticeUi("N-12", "음성 안내", "이 앱의 청해 음성은 기기의 음성 합성 엔진이 만든 것으로 실제 시험 녹음이 아닙니다. 엔진에 따라 억양이 다를 수 있습니다.")

fun attributionWith(vararg notices: NoticeUi) = object : AttributionRepository {
    override suspend fun load() = AttributionData("v", "sample", LinkUi("x", "u"), null, emptyList(), null, emptyList(), notices.toList(), null, emptyMap())
    override suspend fun openSourceLicenses() = emptyList<com.jlptstudy.app.data.attribution.LicenseUi>()
}

val speakerStates = listOf(
    SpeakerState.Initializing, SpeakerState.Ready, SpeakerState.Playing("ls-n5-0001"), SpeakerState.MissingJapaneseData, SpeakerState.NoEngine, SpeakerState.Error,
)
