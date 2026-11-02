package com.jlptstudy.app.ui.common

import com.jlptstudy.app.ui.review.MeaningUi

/** 출처·검수 칩 문구(ui-design 5.7): 앞 부분(생성) + " · " + 뒷 부분(검수). 기록 값만으로 정한다. */
object ProvenanceLabel {
    fun of(m: MeaningUi): String = "${creation(m)} · ${review(m)}"

    /** translate 아이콘이면 true, edit_note면 false. "만든 방법 미기록"은 edit_note. */
    fun isTranslated(m: MeaningUi): Boolean = known(m) && m.koSource == "jmdict-translated"

    /** 하단 시트의 생성 방법 문장(6.6 표의 키 7개). {model}은 ko_method의 모델 부분, 비었으면 -nomodel 키. 키가 없으면 빈 문자열. */
    fun sheetText(meaning: MeaningUi, texts: Map<String, String>): String {
        val model = if (meaning.koMethod.startsWith("llm:")) meaning.koMethod.removePrefix("llm:").substringBefore('@').trim() else ""
        val translated = meaning.koSource == "jmdict-translated"
        val key = when {
            meaning.koMethod.startsWith("llm:") ->
                (if (translated) "translated-llm" else "authored-llm") + if (model.isEmpty()) "-nomodel" else ""
            meaning.koMethod == "human" -> if (translated) "translated-human" else "authored-human"
            else -> "unknown"
        }
        return texts[key].orEmpty().replace("{model}", model)
    }

    /** 시트의 검수 줄: 날짜가 있으면 "{날짜} {검수 문구}". */
    fun reviewLine(m: MeaningUi): String = listOfNotNull(m.reviewedAt?.takeIf { it.isNotBlank() }, review(m)).joinToString(" ")

    private fun known(m: MeaningUi) = m.koMethod.startsWith("llm:") || m.koMethod == "human"

    private fun creation(m: MeaningUi): String {
        if (!known(m)) return "만든 방법 미기록"
        val translated = m.koSource == "jmdict-translated"
        return when {
            m.koMethod.startsWith("llm:") -> if (translated) "AI 번역" else "AI 작성"
            else -> if (translated) "사람 번역" else "직접 작성"
        }
    }

    private fun review(m: MeaningUi): String = when (m.reviewStatus) {
        "unreviewed" -> "검수 전"
        "sampled" -> "표본 검수한 묶음"
        "reviewed" -> when (m.reviewerKind) {
            "human" -> "사람 검수 완료"
            "agent" -> "AI 검수"
            else -> "검수자 미기록"
        }
        else -> "검수 상태 미기록"
    }
}
