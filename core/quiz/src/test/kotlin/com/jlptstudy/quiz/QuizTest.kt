package com.jlptstudy.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleTest {
    private val q = QuizQuestion("q-n5-vocab-0001", Section.VOCAB, "kanji-reading", listOf("a", "b", "c", "d"), 1)

    @Test fun answerFollowsChoiceAndMappingRoundTrips() {
        for (seed in 0L until 50L) {
            val s = Quiz.shuffle(q, seed)
            assertEquals(q.choices[q.answerIndex], s.choices[s.answerIndex])
            for (shown in 0..3) {
                assertEquals(shown, s.toShown(s.toOriginal(shown)))
                assertEquals(q.choices[s.toOriginal(shown)], s.choices[shown])
            }
            assertEquals(listOf(0, 1, 2, 3), s.originalIndexOf.sorted())
        }
    }

    @Test fun sameSeedSameOrder() {
        assertEquals(Quiz.shuffle(q, 7L), Quiz.shuffle(q, 7L))
    }

    @Test fun someSeedsChangeOrder() {
        assertTrue((1000L until 1100L).any { Quiz.shuffle(q, it).originalIndexOf != listOf(0, 1, 2, 3) })
    }

    @Test fun fixedValue() {
        // java.util.Random(42L * 31 + "q-n5-vocab-0001".hashCode()) + Collections.shuffle([0,1,2,3]); 사양상 고정이라 JVM 판이 바뀌어도 같다.
        assertEquals(listOf(2, 3, 0, 1), Quiz.shuffle(q, 42L).originalIndexOf)
        assertEquals(3, Quiz.shuffle(q, 42L).answerIndex)
    }
}

class ValidationTest {
    private fun bad(block: () -> Unit) {
        try { block(); throw AssertionError("expected IllegalArgumentException") } catch (_: IllegalArgumentException) {}
    }

    @Test fun rejectsBadQuestions() {
        bad { QuizQuestion("x", Section.VOCAB, "t", listOf("a", "b", "c"), 0) }
        bad { QuizQuestion("x", Section.VOCAB, "t", listOf("a", "b", "c", "d", "e"), 0) }
        bad { QuizQuestion("x", Section.VOCAB, "t", listOf("a", "b", "c", "d"), 4) }
        bad { QuizQuestion("x", Section.VOCAB, "t", listOf("a", "b", "c", "d"), -1) }
    }

    @Test fun sectionOf() {
        Section.entries.forEach { assertEquals(it, Section.of(it.code)) }
        bad { Section.of("foo") }
    }
}

class BuildSetTest {
    private val pool = buildList {
        repeat(10) { add(PoolItem("v-kr-%02d".format(it), Section.VOCAB, "kanji-reading")) }
        repeat(3) { add(PoolItem("v-ctx-%02d".format(it), Section.VOCAB, "context")) }
        repeat(5) { add(PoolItem("g-form-%02d".format(it), Section.GRAMMAR, "grammar-form")) }
        repeat(5) { add(PoolItem("l-task-%02d".format(it), Section.LISTENING, "task")) }
    }.shuffled(java.util.Random(1))
    private val template = ExamTemplate(5, listOf(
        ExamSection("vocab", "언어지식(문자·어휘)", 20, listOf(
            ExamPart(Section.VOCAB, "kanji-reading", 4), ExamPart(Section.VOCAB, "context", 5))),
        ExamSection("grammar-reading", "언어지식(문법)·독해", 40, listOf(ExamPart(Section.GRAMMAR, "grammar-form", 2))),
        ExamSection("listening", "청해", 30, listOf(ExamPart(Section.LISTENING, "task", 9))),
    ))
    private val byId = pool.associateBy { it.id }

    @Test fun followsPartOrderAndFills() {
        val set = Quiz.buildSet(pool, template, 99L)
        val keys = set.questionIds.map { byId.getValue(it).let { p -> p.section to p.type } }
        assertEquals(List(4) { Section.VOCAB to "kanji-reading" } + List(3) { Section.VOCAB to "context" } +
            List(2) { Section.GRAMMAR to "grammar-form" } + List(5) { Section.LISTENING to "task" }, keys)
        assertEquals(set.questionIds.size, set.questionIds.toSet().size)
        assertEquals(listOf(Shortfall(Section.VOCAB, "context", 5, 3), Shortfall(Section.LISTENING, "task", 9, 5)), set.shortfalls)
    }

    @Test fun deterministicAndIndependentOfPoolOrder() {
        val a = Quiz.buildSet(pool, template, 5L)
        assertEquals(a, Quiz.buildSet(pool, template, 5L))
        assertEquals(a, Quiz.buildSet(pool.reversed(), template, 5L))
        assertTrue((0L until 50L).map { Quiz.buildSet(pool, template, it).questionIds }.toSet().size > 1)
    }

    @Test fun excludedSkipsPartsAndShortfalls() {
        val set = Quiz.buildSet(pool, template, 3L, excluded = setOf(Section.LISTENING))
        assertTrue(set.questionIds.none { byId.getValue(it).section == Section.LISTENING })
        assertEquals(listOf(Shortfall(Section.VOCAB, "context", 5, 3)), set.shortfalls)
    }

    @Test fun idAppearsOnceEvenIfTwoPartsShareKey() {
        val dup = ExamTemplate(5, listOf(ExamSection("s", "", 0, listOf(
            ExamPart(Section.VOCAB, "context", 2), ExamPart(Section.VOCAB, "context", 2)))))
        val set = Quiz.buildSet(pool, dup, 1L)
        assertEquals(3, set.questionIds.size)
        assertEquals(3, set.questionIds.toSet().size)
        assertEquals(listOf(Shortfall(Section.VOCAB, "context", 2, 1)), set.shortfalls)
    }

    @Test fun emptyPoolNoException() {
        val set = Quiz.buildSet(emptyList(), template, 1L, setOf(Section.LISTENING))
        assertEquals(emptyList<String>(), set.questionIds)
        assertEquals(3, set.shortfalls.size)
        assertTrue(set.shortfalls.all { it.available == 0 })
    }
}

class ScoreTest {
    private fun a(id: String, s: Section, type: String, chosen: Int?, answer: Int = 0) = AnswerInput(id, s, type, answer, chosen)

    private val n5 = ExamTemplate(5, listOf(
        ExamSection("vocab", "언어지식(문자·어휘)", 20, listOf(ExamPart(Section.VOCAB, "kanji-reading", 7))),
        ExamSection("grammar-reading", "언어지식(문법)·독해", 40, listOf(
            ExamPart(Section.GRAMMAR, "grammar-form", 9), ExamPart(Section.GRAMMAR, "sentence-order", 4),
            ExamPart(Section.READING, "short", 2))),
        ExamSection("listening", "청해", 30, listOf(ExamPart(Section.LISTENING, "task", 7))),
    ))

    private val answers = listOf(
        a("r1", Section.READING, "short", 0),
        a("v1", Section.VOCAB, "kanji-reading", 0),
        a("v2", Section.VOCAB, "kanji-reading", 2),
        a("v3", Section.VOCAB, "orthography", 0), // 템플릿 part에 없음
        a("g1", Section.GRAMMAR, "grammar-form", null),
        a("g2", Section.GRAMMAR, "sentence-order", 3, answer = 3),
    )

    @Test fun sectionsInDeclarationOrderAndUnanswered() {
        val s = Quiz.score(answers)
        assertEquals(listOf(
            SectionScore(Section.VOCAB, 2, 3), SectionScore(Section.GRAMMAR, 1, 2), SectionScore(Section.READING, 1, 1)), s.sections)
        assertEquals(4, s.correct)
        assertEquals(6, s.total)
        assertEquals(1, s.unanswered)
        assertEquals(emptyList<ExamSectionScore>(), s.examSections)
    }

    @Test fun examSectionsMergeGrammarReadingAndDropUnmatched() {
        val s = Quiz.score(answers, n5)
        assertEquals(listOf(
            ExamSectionScore("vocab", "언어지식(문자·어휘)", 1, 2),
            ExamSectionScore("grammar-reading", "언어지식(문법)·독해", 2, 3)), s.examSections)
        assertTrue(s.sections.any { it.section == Section.VOCAB && it.total == 3 }) // v3는 sections에 남는다
        assertEquals(6, s.total)
    }

    @Test fun empty() {
        assertEquals(Score(emptyList(), emptyList(), 0, 0, 0), Quiz.score(emptyList(), n5))
    }

    @Test fun noScaledScoreFields() {
        fun fields(c: Class<*>) = c.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(setOf("sections", "examSections", "correct", "total", "unanswered"), fields(Score::class.java))
        assertEquals(setOf("section", "correct", "total"), fields(SectionScore::class.java))
        assertEquals(setOf("sectionId", "titleKo", "correct", "total"), fields(ExamSectionScore::class.java))
        assertNotEquals(0, Score::class.java.declaredFields.size)
    }
}
