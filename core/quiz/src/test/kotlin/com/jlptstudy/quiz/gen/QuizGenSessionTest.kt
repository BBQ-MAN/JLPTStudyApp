package com.jlptstudy.quiz.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 오라클: 생성기 함수를 부르지 않고 은행(DB 표) 값만으로 "정답이 정확히 하나"인지 본다. */
internal fun assertSafe(b: QuizBank, q: GeneratedQuestion) {
    val tag = "${q.id} ${q.choices}"
    val a = b.vocab.first { it.id == q.vocabId }
    assertEquals(tag, 4, q.choices.size)
    q.choices.forEach { assertTrue("빈 값 $tag", it.isNotBlank()) }
    assertEquals(tag, 4, q.choices.map { Kana.hira(it) }.toSet().size)          // 보이는 글자 서로 다름
    assertEquals(tag, q.id.let { QuizGen.parseId(it)!!.slot }, q.answerIndex)
    assertEquals(tag, listOf(q.answerIndex), q.why.indices.filter { q.why[it] == null })
    assertEquals(tag, listOf(q.vocabId), listOf(a.id))
    assertTrue(tag, a.id.startsWith("jm:"))
    when (q.type) {
        GenType.KANJI_READING -> {
            assertEquals(tag, a.reading, q.choices[q.answerIndex])
            val correct = q.choices.count { (a.written!! to Kana.hira(it)) in b.forms }
            assertEquals("정답이 둘 이상: $tag", 1, correct)
            q.choices.forEachIndexed { i, c -> if (i != q.answerIndex) assertFalse(tag, b.vocab.any { it.written == a.written && Kana.hira(it.reading) == Kana.hira(c) }) }
        }
        GenType.ORTHOGRAPHY -> {
            assertEquals(tag, a.written, q.choices[q.answerIndex])
            assertEquals("정답이 둘 이상: $tag", 1, q.choices.count { (it to Kana.hira(a.reading)) in b.forms })
            q.choices.forEach { c -> assertTrue("한자 없는 표기 $tag", c.any { it in '\u4E00'..'\u9FFF' }) }
            q.choices.forEachIndexed { i, c -> if (i != q.answerIndex) assertFalse(tag, b.vocab.any { it.written == c && Kana.hira(it.reading) == Kana.hira(a.reading) }) }
        }
        GenType.MEANING_JA_KO, GenType.MEANING_KO_JA -> {
            val ja = q.type == GenType.MEANING_JA_KO
            fun disp(v: QuizVocab) = if (ja) v.meaningKo else (v.written ?: v.reading)
            assertEquals(tag, disp(a), q.choices[q.answerIndex])
            assertTrue(tag, !a.meaningKo.isNullOrBlank())
            q.why.forEachIndexed { i, d ->
                if (d == null) return@forEachIndexed
                val s = b.vocab.first { it.id == d.sourceVocabId }
                assertEquals(tag, disp(s), q.choices[i])
                assertTrue("충돌 $tag", s.id !in b.conflicts[a.id].orEmpty() && a.id !in b.conflicts[s.id].orEmpty())
                assertEquals(tag, a.level, s.level)
                assertEquals(tag, posGroup(a.pos), posGroup(s.pos))
                assertTrue("미검수 $tag", !s.meaningKo.isNullOrBlank())
                assertTrue(tag, s.meaningKo != a.meaningKo && (s.written ?: s.reading) != (a.written ?: a.reading))
            }
        }
    }
}

class QuizGenSessionTest {
    private val bank = TestData.bank

    @Test fun sessionsSeeds1to500() {
        for (type in GenType.entries) {
            val slotTotals = IntArray(4); var total = 0
            for (seed in 1L..500L) {
                val s = QuizGen.buildSession(bank, type, seed, 10)
                assertTrue("$type $seed 문항 수 ${s.size}", s.size in 1..10)
                assertEquals(s.map { it.id }, QuizGen.buildSession(bank, type, seed, 10).map { it.id })       // 같은 시드 → 같은 목록
                assertEquals(s.size, s.map { it.vocabId }.toSet().size)                                       // 한 세션에 같은 단어 두 번 없음
                val perSlot = IntArray(4)
                for (q in s) {
                    assertSafe(bank, q)
                    assertEquals(type, q.type)
                    perSlot[QuizGen.parseId(q.id)!!.slot]++
                    assertEquals(q, (QuizGen.regenerate(bank, q.id) as GenResult.Ok).q)
                }
                val m = s.size
                assertTrue("$type $seed 한 자리 최대 ${perSlot.max()} (m=$m)", perSlot.max() <= (m + 3) / 4)
                if (m == 10) assertTrue("$type $seed 한 자리 ${perSlot.max()} > 3", perSlot.max() <= 3)
                perSlot.forEachIndexed { i, n -> slotTotals[i] += n }; total += m
            }
            slotTotals.forEach { assertTrue("$type 전체 집계 ${slotTotals.toList()}", it.toDouble() / total < 0.40) }
        }
    }

    @Test fun everyEligibleWordEitherSafeOrSkippedAcrossSeeds() {
        for (type in GenType.entries) for (id in QuizGen.eligible(bank, type)) for (seed in 1L..40L) {
            when (val r = QuizGen.generate(bank, type, id, seed, (seed % 4).toInt())) {
                is GenResult.Ok -> assertSafe(bank, r.q)
                is GenResult.Skip -> assertEquals("$type $id", SkipReason.TOO_FEW_DISTRACTORS, r.reason)
            }
        }
    }

    @Test fun sessionSizeAndShortSessions() {
        assertEquals(emptyList<GeneratedQuestion>(), QuizGen.buildSession(bank, GenType.KANJI_READING, 1L, 0))
        assertEquals(3, QuizGen.buildSession(bank, GenType.KANJI_READING, 1L, 3).size)
        // m <= 4면 자리가 모두 다르다.
        for (seed in 1L..100L) {
            val s = QuizGen.buildSession(bank, GenType.MEANING_JA_KO, seed, 4)
            assertEquals(s.size, s.map { it.answerIndex }.toSet().size)
        }
        // 후보가 모자라면 짧은 세션(m < size)이고 자리 분배는 ⌈m/4⌉를 지킨다.
        val few = bank(TestData.vocab.filter { it.meaningKo != null && posGroup(it.pos) == "adj-i" } + TestData.vocab.filter { it.pos == "v5u" || it.pos == "n" }.take(12))
        for (seed in 1L..100L) for (type in GenType.entries) {
            val s = QuizGen.buildSession(few, type, seed, 10); val m = s.size
            val per = IntArray(4).also { a -> s.forEach { a[it.answerIndex]++ } }
            assertTrue(per.max() <= (m + 3) / 4)
        }
    }

    @Test fun differentSessionSeedsDiffer() {
        val ids = (1L..30L).map { QuizGen.buildSession(bank, GenType.KANJI_READING, it, 10).map { q -> q.id } }
        assertTrue(ids.toSet().size > 25)
    }

    @Test fun sessionNeverContainsExcludedAnswers() {
        for (seed in 1L..200L) {
            QuizGen.buildSession(bank, GenType.MEANING_JA_KO, seed, 10).forEach { assertTrue(bank.vocab.first { v -> v.id == it.vocabId }.meaningKo != null) }
        }
    }
}
