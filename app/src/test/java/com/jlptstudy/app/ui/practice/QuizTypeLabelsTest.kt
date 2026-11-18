package com.jlptstudy.app.ui.practice

import com.jlptstudy.app.ui.common.RubySegment
import com.jlptstudy.quiz.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun `자동 생성 뜻 유형 두 개는 이름과 지시문이 있고 표 순서는 기존 유형 뒤이다`() {
        assertEquals("뜻 고르기", QuizTypeLabels.name(Section.VOCAB, "meaning-ja-ko"))
        assertEquals("단어 고르기", QuizTypeLabels.name(Section.VOCAB, "meaning-ko-ja"))
        assertEquals("밑줄 친 {대상}의 뜻으로 알맞은 것을 고르세요.", QuizTypeLabels.instruction(Section.VOCAB, "meaning-ja-ko"))
        assertEquals("이 뜻을 가진 일본어를 고르세요.", QuizTypeLabels.instruction(Section.VOCAB, "meaning-ko-ja"))
        assertTrue(QuizTypeLabels.instruction(Section.VOCAB, "meaning-ja-ko").contains(QuizTypeLabels.TARGET))
        assertFalse("단어 고르기는 대상이 없다", QuizTypeLabels.instruction(Section.VOCAB, "meaning-ko-ja").contains(QuizTypeLabels.TARGET))
        assertTrue(QuizTypeLabels.order(Section.VOCAB, "meaning-ja-ko") > QuizTypeLabels.order(Section.VOCAB, "usage"))
        assertTrue(QuizTypeLabels.order(Section.VOCAB, "meaning-ko-ja") > QuizTypeLabels.order(Section.VOCAB, "meaning-ja-ko"))
    }

    @Test fun `한국어 선택지와 한국어 지문 유형`() {
        assertTrue(QuizTypeLabels.koreanChoices("meaning-ja-ko"))
        assertFalse(QuizTypeLabels.koreanChoices("meaning-ko-ja"))
        assertTrue(QuizTypeLabels.koreanStem("meaning-ko-ja"))
        assertFalse(QuizTypeLabels.koreanStem("meaning-ja-ko"))
        listOf("kanji-reading", "orthography", "usage").forEach {
            assertFalse(QuizTypeLabels.koreanChoices(it)); assertFalse(QuizTypeLabels.koreanStem(it))
        }
    }
}
