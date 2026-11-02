package com.jlptstudy.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class SessionPlannerTest {
    private val rule = StudyDayRule(SEOUL, 240)
    private val now = LocalDateTime.parse("2025-01-10T20:00:00").atZone(SEOUL).toInstant()
    private val endOfToday = rule.startOfStudyDay(rule.studyDayOf(now) + 1) // 2025-01-11 04:00 서울

    private fun card(id: String, phase: Phase, due: Instant) = CardState(
        cardId = id, phase = phase, step = if (phase == Phase.REVIEW) null else 0, stability = 1.0, difficulty = 5.0,
        dueAtEpochMs = due.toEpochMilli(), lastReviewAtEpochMs = due.minus(Duration.ofDays(1)).toEpochMilli(),
        reps = 1, lapses = 0, schedulerVersion = scheduler.version,
    )

    private fun plan(existing: List<CardState>, newIds: List<String> = emptyList(), at: Instant = now, pinned: String? = null) =
        SessionPlanner.plan(existing, newIds, at, learnAheadMinutes = 20, rule = rule, pinned = pinned)

    @Test
    fun reviewDue_meansBeforeEndOfTodaysStudyDay() {
        val existing = listOf(
            card("vr:a", Phase.REVIEW, endOfToday.minusMillis(1)), // 오늘 학습일 안(내일 새벽 3:59:59.999)
            card("vr:b", Phase.REVIEW, endOfToday), // 다음 학습일 시작 → 아님
            card("vr:c", Phase.REVIEW, now.minus(Duration.ofDays(5))), // 연체
        )
        assertEquals(listOf("vr:c", "vr:a"), plan(existing))
    }

    @Test
    fun learningDue_meansWithinLearnAhead() {
        val existing = listOf(
            card("vr:in", Phase.LEARNING, now.plus(Duration.ofMinutes(20))),
            card("vr:out", Phase.LEARNING, now.plus(Duration.ofMinutes(20)).plusMillis(1)),
            card("vr:rl", Phase.RELEARNING, now.plus(Duration.ofMinutes(5))),
        )
        assertEquals(listOf("vr:rl", "vr:in"), plan(existing))
    }

    @Test
    fun order_dueAscendingThenNewCardsInGivenOrder() {
        val existing = listOf(
            card("vr:late", Phase.REVIEW, now.minus(Duration.ofHours(1))),
            card("vr:learn", Phase.LEARNING, now.plus(Duration.ofMinutes(3))),
            card("vr:early", Phase.REVIEW, now.minus(Duration.ofDays(2))),
        )
        assertEquals(
            listOf("vr:early", "vr:late", "vr:n2", "vr:n1", "vr:learn"),
            plan(existing, listOf("vr:n2", "vr:n1")),
        )
    }

    @Test
    fun learnAheadCardWaitsBehindNewCards_untilItsDueTime() {
        val existing = listOf(card("vr:l", Phase.LEARNING, now.plus(Duration.ofMinutes(10))))
        assertEquals(listOf("vr:n1", "vr:l"), plan(existing, listOf("vr:n1")))
        assertEquals(listOf("vr:l", "vr:n1"), plan(existing, listOf("vr:n1"), at = now.plus(Duration.ofMinutes(10))))
    }

    @Test
    fun againGoesToRelearning_andReturnsWhenNowMoves() {
        val config = SrsConfig(enableFuzz = false, studyDay = rule)
        val review = scheduler.schedule(CardState.new("vr:x", now.minus(Duration.ofDays(3)), scheduler.version), Rating.EASY, now.minus(Duration.ofDays(3)), config).state
        val failed = scheduler.schedule(review, Rating.AGAIN, now, config).state
        assertEquals(Phase.RELEARNING, failed.phase)
        val other = card("vr:y", Phase.REVIEW, now.minus(Duration.ofHours(1)))
        // 10분 뒤 기한이라 learnAhead(20분) 안 → 바로 다음 plan에도 들어간다.
        assertEquals(listOf("vr:y", "vr:x"), plan(listOf(failed, other)))
        // y를 끝내고 now를 옮겨도 x가 남는다.
        val doneY = other.copy(phase = Phase.REVIEW, dueAtEpochMs = now.plus(Duration.ofDays(5)).toEpochMilli())
        assertEquals(listOf("vr:x"), plan(listOf(failed, doneY), at = now.plus(Duration.ofMinutes(2))))
    }

    @Test
    fun onlyLearningBeyondLearnAhead_meansEmpty() {
        val existing = listOf(
            card("vr:wait", Phase.LEARNING, now.plus(Duration.ofMinutes(30))),
            card("vr:future", Phase.REVIEW, now.plus(Duration.ofDays(3))),
        )
        assertTrue(plan(existing).isEmpty())
    }

    @Test
    fun pinned_comesFirst_withoutDuplicates() {
        val existing = listOf(
            card("vr:a", Phase.REVIEW, now.minus(Duration.ofDays(2))),
            card("vr:b", Phase.REVIEW, now.minus(Duration.ofDays(1))),
        )
        assertEquals(listOf("vr:b", "vr:a", "vr:n"), plan(existing, listOf("vr:n"), pinned = "vr:b"))
        assertEquals(listOf("vr:n", "vr:a", "vr:b"), plan(existing, listOf("vr:n"), pinned = "vr:n"))
        // 되돌린 새 카드처럼 기한 목록에 없는 카드도 맨 앞에 온다.
        assertEquals(listOf("vr:z", "vr:a", "vr:b"), plan(existing, pinned = "vr:z"))
    }

    @Test
    fun newCardAlreadyStarted_isNotRepeatedAsNew() {
        val config = SrsConfig(enableFuzz = false, studyDay = rule)
        val easy = scheduler.schedule(CardState.new("vr:n", now, scheduler.version), Rating.EASY, now, config).state
        assertTrue(plan(listOf(easy), listOf("vr:n")).isEmpty())
    }

    // selectNew

    @Test
    fun selectNew_countIsLimitMinusIntroducedToday_neverNegative() {
        val candidates = (1..10).map { "vr:jm:$it" }
        assertEquals(candidates.take(3), SessionPlanner.selectNew(candidates, emptySet(), listOf("vr:jm:100", "vr:jm:101"), 5))
        assertEquals(emptyList<String>(), SessionPlanner.selectNew(candidates, emptySet(), (100..107).map { "vr:jm:$it" }, 5))
        assertEquals(candidates, SessionPlanner.selectNew(candidates, emptySet(), emptyList(), 50))
    }

    @Test
    fun selectNew_skipsStartedCards() {
        val candidates = listOf("vr:jm:1", "vr:jm:2", "kr:水", "vr:jm:3")
        assertEquals(listOf("vr:jm:2", "vr:jm:3"), SessionPlanner.selectNew(candidates, setOf("vr:jm:1", "kr:水"), emptyList(), 10))
    }

    @Test
    fun selectNew_skipsOtherKindsOfContentIntroducedToday() {
        val candidates = listOf("vp:jm:1", "vr:jm:2", "vp:jm:2")
        // vr:jm:1을 오늘 도입했으면 vp:jm:1은 오늘 빼고, 남은 한도에서 jm:2는 vr만.
        assertEquals(listOf("vr:jm:2"), SessionPlanner.selectNew(candidates, setOf("vr:jm:1"), listOf("vr:jm:1"), 10))
    }

    @Test
    fun selectNew_sameContentTwoKinds_keepsOnlyTheFirst_andPreservesOrder() {
        val candidates = listOf("vr:jm:5", "kr:水", "vp:jm:5", "vr:jm:2", "vp:jm:2", "kr:火")
        assertEquals(listOf("vr:jm:5", "kr:水", "vr:jm:2", "kr:火"), SessionPlanner.selectNew(candidates, emptySet(), emptyList(), 10))
        assertEquals(listOf("vr:jm:5", "kr:水"), SessionPlanner.selectNew(candidates, emptySet(), emptyList(), 2))
    }
}
