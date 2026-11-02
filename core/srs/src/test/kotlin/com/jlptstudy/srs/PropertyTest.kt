package com.jlptstudy.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PropertyTest {
    private val rule = StudyDayRule(SEOUL)
    private val start = Instant.parse("2025-01-10T03:00:00Z") // 서울 12:00

    /** 여러 상태(새 카드, 학습 1단계, 복습 여러 시점, 재학습)를 만든다. */
    private fun sampleStates(config: SrsConfig): List<Pair<CardState, Instant>> {
        val results = reviewAtDue(
            "prop", start,
            listOf(Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.AGAIN, Rating.GOOD, Rating.HARD, Rating.EASY),
            config,
        )
        val states = listOf(CardState.new("prop", start, scheduler.version) to start) +
            results.map { it.state to Instant.ofEpochMilli(it.state.dueAtEpochMs) }
        // 연체·조기 시점도 넣는다.
        val review = results[2].state
        val due = Instant.ofEpochMilli(review.dueAtEpochMs)
        return states + listOf(review to due.plus(Duration.ofDays(30)), review to due.minus(Duration.ofDays(1)))
    }

    @Test
    fun ratingOrder_againLessThanHard_hardUpToGoodUpToEasy() {
        val config = SrsConfig(enableFuzz = false, studyDay = rule)
        val phases = mutableSetOf<Phase>()
        for ((card, at) in sampleStates(config)) {
            phases += card.phase
            val p = scheduler.preview(card, at, config).mapValues { it.value.length(at) }
            val msg = "phase=${card.phase} step=${card.step} at=$at"
            assertTrue(msg, p.getValue(Rating.AGAIN) < p.getValue(Rating.HARD))
            assertTrue(msg, p.getValue(Rating.HARD) <= p.getValue(Rating.GOOD))
            assertTrue(msg, p.getValue(Rating.GOOD) <= p.getValue(Rating.EASY))
        }
        assertEquals(Phase.entries.toSet(), phases)
    }

    @Test
    fun consecutiveGood_intervalNeverShrinks() {
        val config = SrsConfig(enableFuzz = false, studyDay = rule)
        val results = reviewAtDue("good", start, List(20) { Rating.GOOD }, config)
        val lengths = results.map { it.state.dueAtEpochMs - it.record.reviewedAtEpochMs }
        val days = results.map { it.record.scheduledDays }
        assertEquals(lengths.sorted(), lengths)
        assertEquals(days.sorted(), days)
    }

    @Test
    fun dueAfterReview_andIntervalWithinMaximum_withFuzz() {
        for (max in listOf(36500, 30)) {
            val config = SrsConfig(maximumIntervalDays = max, studyDay = rule)
            for ((card, at) in sampleStates(config)) for (rating in Rating.entries) {
                val r = scheduler.schedule(card, rating, at, config)
                assertTrue(r.state.dueAtEpochMs > at.toEpochMilli())
                assertTrue("${r.record.scheduledDays} > $max", r.record.scheduledDays <= max)
            }
            // GOOD만 계속하면 상한에 닿아도 넘지 않는다.
            val long = reviewAtDue("max", start, List(30) { Rating.GOOD }, config)
            assertTrue(long.all { it.record.scheduledDays <= max })
        }
    }

    @Test
    fun newCardHard_isMeanOfFirstTwoSteps() {
        val config = SrsConfig(studyDay = rule)
        val card = CardState.new("hard", start, scheduler.version)
        val p = scheduler.preview(card, start, config).getValue(Rating.HARD)
        assertEquals(Duration.ofMinutes(5).plusSeconds(30), p.stepDuration)
        assertEquals(0, p.scheduledDays)
        assertEquals(start.plusSeconds(330).toEpochMilli(), p.dueAtEpochMs)
        val r = scheduler.schedule(card, Rating.HARD, start, config)
        assertEquals(start.plusSeconds(330).toEpochMilli(), r.state.dueAtEpochMs)
        assertEquals(Phase.LEARNING, r.state.phase)
        assertEquals(0, r.state.step)
    }

    @Test
    fun singleLearningStepHard_isOneAndHalfTimes() {
        val config = SrsConfig(learningSteps = listOf(Duration.ofMinutes(10)), studyDay = rule)
        val card = CardState.new("hard1", start, scheduler.version)
        val p = scheduler.preview(card, start, config).getValue(Rating.HARD)
        assertEquals(Duration.ofMinutes(15), p.stepDuration)
        assertEquals(start.plus(Duration.ofMinutes(15)).toEpochMilli(), p.dueAtEpochMs)
    }

    @Test
    fun reviewResult_hasDaysAndNoStep_learningResult_hasStepAndZeroDays() {
        val config = SrsConfig(studyDay = rule)
        val p = scheduler.preview(CardState.new("x", start, scheduler.version), start, config)
        assertNull(p.getValue(Rating.EASY).stepDuration)
        assertTrue(p.getValue(Rating.EASY).scheduledDays >= 1)
        assertEquals(Duration.ofMinutes(1), p.getValue(Rating.AGAIN).stepDuration)
        assertEquals(Duration.ofMinutes(10), p.getValue(Rating.GOOD).stepDuration)
    }

    @Test
    fun reviewAgain_countsLapse_andEntersRelearning() {
        val config = SrsConfig(studyDay = rule)
        val results = reviewAtDue("lapse", start, listOf(Rating.GOOD, Rating.GOOD, Rating.AGAIN), config)
        val last = results.last()
        assertEquals(Phase.REVIEW, last.record.phaseBefore)
        assertEquals(Phase.RELEARNING, last.state.phase)
        assertEquals(1, last.state.lapses)
        assertEquals(3, last.state.reps)
        assertEquals(last.record.reviewedAtEpochMs + Duration.ofMinutes(10).toMillis(), last.state.dueAtEpochMs)
    }
}
