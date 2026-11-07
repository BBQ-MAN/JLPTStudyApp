package com.jlptstudy.quiz.gen

import com.jlptstudy.quiz.QuizQuestion
import com.jlptstudy.quiz.Section
import java.text.Normalizer

enum class GenType(val code: String) {
    KANJI_READING("kanji-reading"), ORTHOGRAPHY("orthography"), MEANING_JA_KO("meaning-ja-ko"), MEANING_KO_JA("meaning-ko-ja");

    companion object { fun of(code: String): GenType? = entries.firstOrNull { it.code == code } }
}

/** 앱 Room 엔티티와 분리한 입력. meaningKo는 ord 0 뜻이 reviewed일 때만 값(어댑터가 거른다 — 코어는 검수 상태를 모른다). */
data class QuizVocab(val id: String, val written: String?, val reading: String, val level: Int, val pos: String, val meaningKo: String?)
data class QuizKanji(val literal: String, val level: Int, val on: List<String>, val kun: List<String>)

/**
 * 한 급수의 생성 입력. [forms]는 jm_form (written, hira(reading)), [conflicts]는 vocab_conflict.
 * 파이프라인은 모든 jm: 어휘에 forms 자기 쌍(표기가 있을 때)과 conflicts 행을 하나씩 만든다. 그 행이 없으면 배제 데이터가
 * 로드되지 않은 것으로 보고 해당 어휘를 대상에서 뺀다(QuizGen.eligible).
 */
class QuizBank(
    val level: Int,
    val vocab: List<QuizVocab>,
    val kanji: List<QuizKanji>,
    val forms: Set<Pair<String, String>>,
    val conflicts: Map<String, Set<String>>,
) {
    /** 대상 어휘: 이 급수의 jm: 어휘만(Q-09), id 순. */
    internal val jm: List<QuizVocab> by lazy { vocab.filter { it.level == level && it.id.startsWith("jm:") }.distinctBy { it.id }.sortedBy { it.id } }
    internal val byId: Map<String, QuizVocab> by lazy { jm.associateBy { it.id } }
    internal val formKeys: Set<Pair<String, String>> by lazy { forms.mapTo(HashSet()) { norm(it.first) to rk(it.second) } }

    /** 표기 W의 가능한 읽기 전부: jm_form + 같은 표기의 은행 어휘(forms가 불완전해도 은행은 안다). */
    internal val readingsOfWritten: Map<String, Set<String>> by lazy {
        val m = HashMap<String, MutableSet<String>>()
        for ((w, r) in forms) m.getOrPut(norm(w)) { HashSet() } += rk(r)
        for (v in jm) v.written?.let { m.getOrPut(norm(it)) { HashSet() } += rk(v.reading) }
        m
    }

    /** 읽기 R의 가능한 표기 전부. */
    internal val writtensOfReading: Map<String, Set<String>> by lazy {
        val m = HashMap<String, MutableSet<String>>()
        for ((w, r) in forms) m.getOrPut(rk(r)) { HashSet() } += norm(w)
        for (v in jm) v.written?.let { m.getOrPut(rk(v.reading)) { HashSet() } += norm(it) }
        m
    }

    internal val bankKanji: Map<String, QuizKanji> by lazy { kanji.filter { it.level >= level }.sortedBy { it.literal }.associateBy { it.literal } }
}

enum class DistractorKind { LONG_VOWEL, VOICING, SOKUON, YOON, OTHER_READING, KANJI_SWAP, OTHER_WRITTEN, OTHER_MEANING, OTHER_WORD }
data class Distractor(val text: String, val kind: DistractorKind, val sourceVocabId: String? = null, val swap: Pair<String, String>? = null)

/** choices는 보이는 순서(fixedOrder). why[i] == null ⇔ i == answerIndex. text는 4.2~4.4의 문자열 그대로(루비 표기는 어댑터가 덧씌운다). */
data class GeneratedQuestion(
    val id: String, val type: GenType, val vocabId: String, val stem: String, val target: String?,
    val choices: List<String>, val answerIndex: Int, val why: List<Distractor?>,
) {
    init {
        require(choices.size == 4 && why.size == 4 && answerIndex in 0..3) { "$id: bad shape" }
        require(why.indices.all { (why[it] == null) == (it == answerIndex) }) { "$id: why/answerIndex mismatch" }
    }

    fun toQuizQuestion(): QuizQuestion = QuizQuestion(id, Section.VOCAB, type.code, choices, answerIndex, fixedOrder = true)
}

enum class SkipReason { NOT_TARGET, UNREVIEWED, TOO_FEW_DISTRACTORS, UNKNOWN_VOCAB }
sealed interface GenResult {
    data class Ok(val q: GeneratedQuestion) : GenResult
    data class Skip(val reason: SkipReason) : GenResult
}

data class GenId(val type: GenType, val slot: Int, val seed: Long, val vocabId: String)

/** 비교 키: NFC(눈에 같은 글자가 다른 코드열로 둘 보이는 것을 막는다). */
internal fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

/** 읽기 비교 키: NFC 뒤 hira. */
internal fun rk(s: String): String = Kana.hira(norm(s))
