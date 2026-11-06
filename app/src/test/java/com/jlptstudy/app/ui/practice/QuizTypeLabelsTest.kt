package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.quiz.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTypeLabelsTest {
    @Test fun `독해와 청해의 통합 이해는 쌍으로 찾는다`() {
        assertEquals("통합 이해", QuizTypeLabels.name(Section.READING, "integrated"))
        assertEquals("통합 이해", QuizTypeLabels.name(Section.LISTENING, "integrated"))
        assertEquals("한자 읽기", QuizTypeLabels.name(Section.VOCAB, "kanji-reading"))
        assertEquals("모름", QuizTypeLabels.name(Section.VOCAB, "모름"))
    }

    @Test fun `대상이 있는 유형만 지시문에 대상 자리가 있다`() {
        val withTarget = setOf("kanji-reading", "orthography", "paraphrase", "usage")
        listOf("kanji-reading", "orthography", "word-formation", "context", "paraphrase", "usage").forEach {
            assertEquals(it, it in withTarget, QuizTypeLabels.instruction(Section.VOCAB, it).contains(QuizTypeLabels.TARGET))
        }
    }

    @Test fun `빈칸 토큰을 칸으로 나눈다`() {
        val parts = QuizStem.split(listOf(RubySegment("a＿＿b＿★＿"), RubySegment("終", "しゅう")))
        assertEquals(
            listOf("a", "B", "b", "S", "終"),
            parts.map { when (it) { is StemPart.Text -> it.segment.base; StemPart.Blank -> "B"; StemPart.StarBlank -> "S" } },
        )
        assertTrue(QuizStem.split(listOf(RubySegment("ただの文"))).single() is StemPart.Text)
    }
}
