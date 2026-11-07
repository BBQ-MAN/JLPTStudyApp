package com.jlptstudy.app.ui.practice

import com.jlptstudy.quiz.Section

/** 유형 코드 → 화면 이름·지시문(ui-design 6.10). 독해·청해에 같은 코드 `integrated`가 있어 (section, type) 쌍으로 찾는다. */
object QuizTypeLabels {
    /** 영역 안 표시 순서 = architecture.md 9.2.2 표 순서. */
    private val types: Map<Section, List<Pair<String, String>>> = mapOf(
        Section.VOCAB to listOf(
            "kanji-reading" to "한자 읽기", "orthography" to "표기", "word-formation" to "단어 형성",
            "context" to "문맥 규정", "paraphrase" to "유의 표현", "usage" to "용법",
        ),
        Section.GRAMMAR to listOf("grammar-form" to "문법 형식 판단", "sentence-order" to "문장 배열", "text-grammar" to "글의 문법"),
        Section.READING to listOf(
            "short" to "단문", "mid" to "중문", "long" to "장문", "integrated" to "통합 이해", "thematic" to "주장 이해", "info-search" to "정보 검색",
        ),
        Section.LISTENING to listOf(
            "task" to "과제 이해", "point" to "포인트 이해", "summary" to "개요 이해", "utterance" to "발화 표현",
            "quick-response" to "즉시 응답", "integrated" to "통합 이해",
        ),
    )

    fun sectionName(s: Section): String = when (s) {
        Section.VOCAB -> "문자·어휘"
        Section.GRAMMAR -> "문법"
        Section.READING -> "독해"
        Section.LISTENING -> "청해"
    }

    /** 모르는 코드는 코드 그대로(빈 이름표로 화면이 깨지지 않게). */
    fun name(section: Section, type: String): String = types[section]?.firstOrNull { it.first == type }?.second ?: type

    /** 표 순서. 모르는 코드는 맨 뒤. */
    fun order(section: Section, type: String): Int = types[section]?.indexOfFirst { it.first == type }?.takeIf { it >= 0 } ?: Int.MAX_VALUE

    /** 후리가나를 언제나 끄는 유형(정답이 드러난다, ui-design 5.2). */
    fun alwaysNoRuby(section: Section, type: String) = section == Section.VOCAB && (type == "kanji-reading" || type == "orthography")

    const val TARGET = "{대상}"

    /** 지시문. `{대상}` 자리에 문항 target을 넣는다(일본어 구간 처리는 화면). */
    fun instruction(section: Section, type: String): String = when (section) {
        Section.VOCAB -> when (type) {
            "kanji-reading" -> "밑줄 친 {대상}의 읽기로 알맞은 것을 고르세요."
            "orthography" -> "밑줄 친 {대상}의 알맞은 표기를 고르세요."
            "word-formation" -> "빈칸에 붙여 말을 만들 때 알맞은 것을 고르세요."
            "context" -> "빈칸에 들어갈 말로 알맞은 것을 고르세요."
            "paraphrase" -> "밑줄 친 말 {대상} — 뜻이 가장 가까운 것을 고르세요."
            "usage" -> "{대상} — 이 말이 바르게 쓰인 문장을 고르세요."
            else -> GENERIC
        }
        Section.GRAMMAR -> when (type) {
            "grammar-form" -> "빈칸에 들어갈 알맞은 표현을 고르세요."
            "sentence-order" -> "네 조각을 바른 순서로 놓았을 때 굵은 테두리 칸에 들어갈 것을 고르세요."
            "text-grammar" -> "글의 흐름에 맞게 빈칸에 들어갈 것을 고르세요."
            else -> GENERIC
        }
        Section.READING -> "글을 읽고 질문에 알맞은 답을 고르세요."
        Section.LISTENING -> when (type) {
            "task" -> "음성을 듣고, 이 사람이 이제 무엇을 해야 하는지 알맞은 것을 고르세요."
            "point", "integrated" -> "음성을 듣고 질문에 알맞은 답을 고르세요."
            "summary" -> "음성을 듣고 이야기의 중심 내용으로 알맞은 것을 고르세요."
            "utterance" -> "상황 설명을 듣고 이 사람이 할 말로 알맞은 것을 고르세요."
            "quick-response" -> "말을 듣고 알맞은 대답을 고르세요."
            else -> GENERIC
        }
    }

    private const val GENERIC = "알맞은 것을 고르세요."
}
