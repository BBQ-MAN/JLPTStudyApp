package com.jlptstudy.quiz.gen

import com.jlptstudy.quiz.Quiz
import com.jlptstudy.quiz.QuizQuestion
import com.jlptstudy.quiz.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedOrderTest {
    @Test fun fixedOrderKeepsOriginalOrder() {
        val q = QuizQuestion("gq:x", Section.VOCAB, "kanji-reading", listOf("a", "b", "c", "d"), 2, fixedOrder = true)
        for (seed in 0L until 200L) {
            val s = Quiz.shuffle(q, seed)
            assertEquals(listOf(0, 1, 2, 3), s.originalIndexOf)
            assertEquals(2, s.answerIndex)
            assertEquals(q.choices, s.choices)
        }
    }

    @Test fun defaultIsNotFixedAndStillShuffles() {
        val q = QuizQuestion("q-n5-vocab-0001", Section.VOCAB, "kanji-reading", listOf("a", "b", "c", "d"), 1)
        assertFalse(q.fixedOrder)
        assertTrue((1000L until 1100L).any { Quiz.shuffle(q, it).originalIndexOf != listOf(0, 1, 2, 3) })
    }

    @Test fun generatedQuestionIsFixedOrder() {
        val g = (QuizGen.generate(TestData.bank, GenType.KANJI_READING, TestData.id("学生", "がくせい"), 7L, 3) as GenResult.Ok).q
        val qq = g.toQuizQuestion()
        assertTrue(qq.fixedOrder)
        assertEquals(Section.VOCAB, qq.section)
        assertEquals("kanji-reading", qq.type)
        assertEquals(g.id, qq.id)
        assertEquals(3, Quiz.shuffle(qq, 99L).answerIndex)
    }
}
