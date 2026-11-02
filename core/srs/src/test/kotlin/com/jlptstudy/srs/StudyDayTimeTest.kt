package com.jlptstudy.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StudyDayTimeTest {
    private val newYork = ZoneId.of("America/New_York")
    private val losAngeles = ZoneId.of("America/Los_Angeles")

    private fun local(text: String, zone: ZoneId): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()
    private fun day(text: String): Long = LocalDate.parse(text).toEpochDay()

    @Test
    fun invariant_holdsAcrossDstTransitions_everyMinute() {
        for (transition in listOf("2025-03-09", "2025-11-02")) for (boundary in listOf(0, 120, 240)) {
            val rule = StudyDayRule(newYork, boundary)
            val date = LocalDate.parse(transition)
            var t = date.minusDays(3).atStartOfDay(newYork).toInstant()
            val end = date.plusDays(4).atStartOfDay(newYork).toInstant()
            var count = 0
            while (t < end) {
                val d = rule.studyDayOf(t)
                val msg = "zone=$newYork boundary=$boundary t=$t day=$d"
                assertTrue(msg, rule.startOfStudyDay(d) <= t)
                assertTrue(msg, t < rule.startOfStudyDay(d + 1))
                t = t.plusSeconds(60)
                count++
            }
            assertTrue(count >= 7 * 1440 - 60)
        }
    }

    @Test
    fun boundary_0359_belongsToPreviousDay_0400_toSameDay() {
        val rule = StudyDayRule(SEOUL, 240)
        assertEquals(day("2025-01-09"), rule.studyDayOf(local("2025-01-10T03:59:00", SEOUL)))
        assertEquals(day("2025-01-09"), rule.studyDayOf(local("2025-01-10T03:59:59.999", SEOUL)))
        assertEquals(day("2025-01-10"), rule.studyDayOf(local("2025-01-10T04:00:00", SEOUL)))
        assertEquals(day("2025-01-10"), rule.studyDayOf(local("2025-01-10T04:01:00", SEOUL)))
        assertEquals(local("2025-01-10T04:00:00", SEOUL), rule.startOfStudyDay(day("2025-01-10")))
    }

    @Test
    fun boundaryOutOfRange_isRejected() {
        runCatching { StudyDayRule(SEOUL, 721) }.let { assertTrue(it.isFailure) }
        runCatching { StudyDayRule(SEOUL, -1) }.let { assertTrue(it.isFailure) }
    }

    @Test
    fun zoneChange_seoulToLosAngeles_usesCurrentZoneForElapsedDaysAndDue() {
        val seoul = SrsConfig(enableFuzz = false, studyDay = StudyDayRule(SEOUL))
        val la = SrsConfig(enableFuzz = false, studyDay = StudyDayRule(losAngeles))
        // 서울에서 학습 → 복습 상태로 만든다(마지막 복습 2025-01-10T00:00Z = 서울 09:00).
        val lastReview = Instant.parse("2025-01-10T00:00:00Z")
        val first = scheduler.schedule(CardState.new("tz", lastReview, scheduler.version), Rating.EASY, lastReview, seoul).state
        // 2025-01-10T14:00Z: 서울 23:00(같은 학습일) / LA 06:00(LA 기준으로 마지막 복습은 전날 16:00)
        val now = Instant.parse("2025-01-10T14:00:00Z")
        assertEquals(0, scheduler.schedule(first, Rating.GOOD, now, seoul).record.elapsedDays)
        val inLa = scheduler.schedule(first, Rating.GOOD, now, la)
        assertEquals(1, inLa.record.elapsedDays)
        // 새 시간대의 학습일 시작(LA 04:00)이 기한이 된다.
        val due = Instant.ofEpochMilli(inLa.state.dueAtEpochMs).atZone(losAngeles).toLocalTime()
        assertEquals("04:00", due.toString())
        // 시계가 뒤로 가도(복습 시각 < 마지막 복습) 경과 일수는 음수가 되지 않는다.
        assertEquals(0, scheduler.schedule(first, Rating.GOOD, lastReview.minus(Duration.ofDays(3)), la).record.elapsedDays)
    }

    @Test
    fun overdueAndEarlyReview_elapsedDaysAreStudyDayDifferences_andStabilityReacts() {
        val config = SrsConfig(enableFuzz = false, studyDay = StudyDayRule(SEOUL))
        val start = local("2025-01-10T12:00:00", SEOUL)
        val review = reviewAtDue("od", start, listOf(Rating.GOOD, Rating.GOOD, Rating.GOOD), config).last().state
        assertEquals(Phase.REVIEW, review.phase)
        val due = Instant.ofEpochMilli(review.dueAtEpochMs)
        val lastDay = config.studyDay.studyDayOf(Instant.ofEpochMilli(review.lastReviewAtEpochMs!!))
        val scheduled = (config.studyDay.studyDayOf(due) - lastDay).toInt()

        val onTime = scheduler.schedule(review, Rating.GOOD, due, config)
        val overdue = scheduler.schedule(review, Rating.GOOD, due.plus(Duration.ofDays(10)), config)
        val early = scheduler.schedule(review, Rating.GOOD, Instant.ofEpochMilli(review.lastReviewAtEpochMs!!).plus(Duration.ofDays(1)), config)

        assertEquals(scheduled, onTime.record.elapsedDays)
        assertEquals(scheduled + 10, overdue.record.elapsedDays)
        assertEquals(1, early.record.elapsedDays)
        // FSRS: 늦게 맞힐수록(회상 확률이 낮을수록) 안정성이 더 크게 오르고, 일찍 맞히면 덜 오른다.
        assertTrue(overdue.state.stability!! > onTime.state.stability!!)
        assertTrue(early.state.stability!! < onTime.state.stability!!)
        assertTrue(early.state.stability!! >= review.stability!!)
    }

    @Test
    fun sameStudyDayAcrossMidnight_hasZeroElapsedDays_andUsesShortTermFormula() {
        val config = SrsConfig(enableFuzz = false, studyDay = StudyDayRule(SEOUL, 240))
        val evening = local("2025-01-10T22:00:00", SEOUL)
        val review = scheduler.schedule(CardState.new("same", evening, scheduler.version), Rating.EASY, evening, config).state
        val afterMidnight = local("2025-01-11T02:00:00", SEOUL) // 달력은 다음 날, 학습일은 같은 날
        val r = scheduler.schedule(review, Rating.GOOD, afterMidnight, config)
        assertEquals(0, r.record.elapsedDays)
        // 같은 날 공식(short-term)은 GOOD에서 안정성을 줄이지 않고 값이 크게 뛰지 않는다.
        assertTrue(r.state.stability!! >= review.stability!!)
        assertTrue(r.state.stability!! < review.stability!! * 2)
        // 04:00 이후는 다음 학습일이라 경과 1일.
        val nextDay = local("2025-01-11T04:00:00", SEOUL)
        assertEquals(1, scheduler.schedule(review, Rating.GOOD, nextDay, config).record.elapsedDays)
    }

    @Test
    fun reviewDue_isStartOfStudyDay() {
        val rule = StudyDayRule(SEOUL, 240)
        val config = SrsConfig(enableFuzz = false, studyDay = rule)
        val at = local("2025-01-10T23:30:00", SEOUL)
        val r = scheduler.schedule(CardState.new("due", at, scheduler.version), Rating.EASY, at, config)
        assertEquals(rule.startOfStudyDay(rule.studyDayOf(at) + r.record.scheduledDays).toEpochMilli(), r.state.dueAtEpochMs)
        assertEquals(0, r.record.elapsedDays)
    }
}
