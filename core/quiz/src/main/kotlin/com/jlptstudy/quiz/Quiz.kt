package com.jlptstudy.quiz

import java.util.Collections
import java.util.Random

enum class Section(val code: String) {
    VOCAB("vocab"), GRAMMAR("grammar"), READING("reading"), LISTENING("listening");

    companion object {
        fun of(code: String): Section = entries.firstOrNull { it.code == code } ?: throw IllegalArgumentException("unknown section: $code")
    }
}

/** 섞기 전 문항. choices.size == 4, answerIndex in 0..3(원래 색인). 어기면 생성자에서 IllegalArgumentException. */
data class QuizQuestion(
    val id: String, val section: Section, val type: String, val choices: List<String>, val answerIndex: Int,
    /** true: 자동 생성 문항 — 정답 자리를 생성기가 정했으므로 [Quiz.shuffle]이 순서를 바꾸지 않는다. */
    val fixedOrder: Boolean = false,
) {
    init {
        require(choices.size == 4) { "$id: choices.size ${choices.size} != 4" }
        require(answerIndex in 0..3) { "$id: answerIndex $answerIndex" }
    }
}

/** 보이는 순서. originalIndexOf[shown] = 원래 색인. answerIndex는 섞은 뒤 색인. */
data class ShuffledQuestion(
    val id: String, val section: Section, val type: String,
    val choices: List<String>, val answerIndex: Int, val originalIndexOf: List<Int>,
) {
    fun toOriginal(shownIndex: Int): Int = originalIndexOf[shownIndex]
    fun toShown(originalIndex: Int): Int = originalIndexOf.indexOf(originalIndex)
}

data class ExamPart(val section: Section, val type: String, val count: Int)
data class ExamSection(val id: String, val titleKo: String, val minutes: Int, val parts: List<ExamPart>)
data class ExamTemplate(val level: Int, val sections: List<ExamSection>)
data class PoolItem(val id: String, val section: Section, val type: String)
data class Shortfall(val section: Section, val type: String, val required: Int, val available: Int)
data class BuiltSet(val questionIds: List<String>, val shortfalls: List<Shortfall>)

/** 채점 입력. 색인은 모두 원래 색인. chosenIndex == null이면 미응답(오답으로 센다). */
data class AnswerInput(val questionId: String, val section: Section, val type: String, val answerIndex: Int, val chosenIndex: Int?)
data class SectionScore(val section: Section, val correct: Int, val total: Int)
/** 모의고사 영역 점수. 키는 템플릿 sections[].id. */
data class ExamSectionScore(val sectionId: String, val titleKo: String, val correct: Int, val total: Int)
// 척도 점수·합격 판정·예상 점수 필드를 두지 않는다(architecture 9.2.5).
data class Score(
    val sections: List<SectionScore>, val examSections: List<ExamSectionScore>,
    val correct: Int, val total: Int, val unanswered: Int,
)

object Quiz {
    fun shuffle(question: QuizQuestion, attemptSeed: Long): ShuffledQuestion {
        val order = MutableList(question.choices.size) { it }
        if (!question.fixedOrder) Collections.shuffle(order, Random(attemptSeed * 31 + question.id.hashCode()))
        return ShuffledQuestion(
            question.id, question.section, question.type,
            order.map { question.choices[it] }, order.indexOf(question.answerIndex), order.toList(),
        )
    }

    fun buildSet(pool: List<PoolItem>, template: ExamTemplate, seed: Long, excluded: Set<Section> = emptySet()): BuiltSet {
        val random = Random(seed)
        val picked = LinkedHashSet<String>()
        val shortfalls = mutableListOf<Shortfall>()
        for (part in template.sections.flatMap { it.parts }) {
            if (part.section in excluded) continue
            val candidates = pool.filter { it.section == part.section && it.type == part.type }
                .map { it.id }.distinct().sorted().toMutableList()
            Collections.shuffle(candidates, random)
            val available = candidates.filterNot { it in picked }
            picked += available.take(part.count)
            if (available.size < part.count) shortfalls += Shortfall(part.section, part.type, part.count, available.size)
        }
        return BuiltSet(picked.toList(), shortfalls)
    }

    fun score(answers: List<AnswerInput>, template: ExamTemplate? = null): Score {
        fun AnswerInput.ok() = chosenIndex == answerIndex
        val sections = Section.entries.mapNotNull { s ->
            val xs = answers.filter { it.section == s }
            if (xs.isEmpty()) null else SectionScore(s, xs.count { it.ok() }, xs.size)
        }
        val examSections = template?.sections.orEmpty().let { ess ->
            // 같은 (section, type)이 여러 영역에 있으면 템플릿 앞 영역 하나에만 센다. -1(어느 part에도 없음)은 빠진다.
            val bySection = answers.groupBy { a -> ess.indexOfFirst { e -> e.parts.any { it.section == a.section && it.type == a.type } } }
            ess.mapIndexedNotNull { i, es ->
                bySection[i]?.let { xs -> ExamSectionScore(es.id, es.titleKo, xs.count { it.ok() }, xs.size) }
            }
        }
        return Score(sections, examSections, answers.count { it.ok() }, answers.size, answers.count { it.chosenIndex == null })
    }
}
