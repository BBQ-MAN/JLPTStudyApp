package com.jlptstudy.srs

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DeterminismTest {
    private val config = SrsConfig(studyDay = StudyDayRule(SEOUL)) // 퍼즈 켬(기본값)
    private val start = Instant.parse("2025-01-10T03:00:00Z")
    private val ratings = listOf(
        Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.AGAIN, Rating.GOOD, Rating.EASY, Rating.HARD,
    )

    @Test
    fun sameInputTwice_sameResult_withFuzz() {
        assertEquals(reviewAtDue("det", start, ratings, config), reviewAtDue("det", start, ratings, config))
    }

    @Test
    fun fuzz_isApplied_andStaysInPyFsrsRange() {
        // 같은 상태, 다른 카드 ID → 시드가 달라 간격이 퍼진다. 퍼즈 끈 값 근처(±delta)를 벗어나지 않는다.
        val base = reviewAtDue("f", start, List(4) { Rating.GOOD }, config.copy(enableFuzz = false)).last()
        val plain = scheduler.schedule(base.state, Rating.GOOD, Instant.ofEpochMilli(base.state.dueAtEpochMs), config.copy(enableFuzz = false))
        val ivl = plain.record.scheduledDays
        assertTrue("interval $ivl too small to fuzz", ivl >= 20)
        val fuzzed = (0 until 50).map { i ->
            scheduler.schedule(base.state.copy(cardId = "f$i"), Rating.GOOD, Instant.ofEpochMilli(base.state.dueAtEpochMs), config).record.scheduledDays
        }
        assertTrue(fuzzed.toSet().size > 1)
        val delta = 1.0 + 0.15 * 4.5 + 0.1 * 13 + 0.05 * (ivl - 20)
        assertTrue(fuzzed.all { it >= Math.rint(ivl - delta) && it <= Math.rint(ivl + delta) + 1 })
    }

    @Test
    fun preview_equalsSchedule_forEveryRating() {
        val states = listOf(CardState.new("pv", start, scheduler.version) to start) +
            reviewAtDue("pv", start, ratings, config).map { it.state to Instant.ofEpochMilli(it.state.dueAtEpochMs) }
        for ((card, at) in states) {
            val preview = scheduler.preview(card, at, config)
            assertEquals(Rating.entries.toSet(), preview.keys)
            for (rating in Rating.entries) {
                val r = scheduler.schedule(card, rating, at, config)
                val step = if (r.record.scheduledDays == 0) Duration.ofMillis(r.state.dueAtEpochMs - r.record.reviewedAtEpochMs) else null
                assertEquals("phase=${card.phase} $rating", IntervalPreview(r.state.dueAtEpochMs, r.record.scheduledDays, step), preview.getValue(rating))
            }
        }
    }

    @Test
    fun cardStateJson_roundTrip() {
        val states = listOf(CardState.new("vr:jm:1358280", start, scheduler.version)) +
            reviewAtDue("kr:水", start, ratings, config).map { it.state }
        for (s in states) assertEquals(s, Json.decodeFromString<CardState>(Json.encodeToString(s)))
    }

    @Test
    fun record_carriesBeforeAfterAndVersion() {
        val card = CardState.new("rec", start, scheduler.version)
        val r = scheduler.schedule(card, Rating.GOOD, start, config)
        assertEquals(card, r.record.before)
        assertEquals(r.state, r.record.after)
        assertEquals(Phase.NEW, r.record.phaseBefore)
        assertEquals("fsrs6/py-fsrs-6.3.2", r.record.schedulerVersion)
        assertEquals("fsrs6/py-fsrs-6.3.2", r.state.schedulerVersion)
        assertEquals(start.toEpochMilli(), r.state.lastReviewAtEpochMs)
        assertEquals(1, r.state.reps)
    }
}
