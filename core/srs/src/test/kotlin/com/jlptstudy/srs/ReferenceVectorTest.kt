/*
 * 참조 벡터 출처: py-fsrs v6.3.2
 *   저장소: https://github.com/open-spaced-repetition/py-fsrs
 *   커밋: 9446cb06605c597a063aeee49f7d188d42e34dc2 (태그 v6.3.2)
 *   파일: tests/test_basic.py — TestPyFSRS.test_review_card, TestPyFSRS.test_memo_state (기대값을 그대로 옮김)
 *
 * MIT License
 *
 * Copyright (c) 2022 Open Spaced Repetition
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.jlptstudy.srs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * 비교 조건(architecture.md 2.2): 시간대 UTC, 경계 0분.
 * 의도적 차이: 복습 상태 dueAt은 py-fsrs의 `review + interval`이 아니라 그 학습일 시작(00:00Z)이다.
 * 그래서 dueAt 시각 대신 scheduledDays와 "py-fsrs 방식 기한의 학습일 == 이 앱 dueAt의 학습일"을 비교한다.
 */
class ReferenceVectorTest {
    private val scheduler = Fsrs6Scheduler()
    private val rule = StudyDayRule(ZoneOffset.UTC, boundaryMinutes = 0)

    // test_basic.py TEST_RATINGS_1
    private val testRatings1 = listOf(
        Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD,
        Rating.AGAIN, Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD,
    )

    // datetime(2022, 11, 29, 12, 30, 0, 0, timezone.utc)
    private val start = Instant.parse("2022-11-29T12:30:00Z")

    @Test
    fun reviewCard_scheduledDaysMatchPyFsrs() {
        val config = SrsConfig(enableFuzz = false, studyDay = rule)
        var card = CardState.new("ref-card", start, scheduler.version)
        var reviewedAt = start
        val scheduled = mutableListOf<Int>()
        for (rating in testRatings1) {
            val result = scheduler.schedule(card, rating, reviewedAt, config)
            val days = result.record.scheduledDays
            scheduled += days
            val pyFsrsDue = reviewedAt.plus(Duration.ofDays(days.toLong()))
            assertEquals(
                "study day of py-fsrs style due vs app dueAt at review #${scheduled.size}",
                rule.studyDayOf(pyFsrsDue),
                rule.studyDayOf(Instant.ofEpochMilli(result.state.dueAtEpochMs)),
            )
            card = result.state
            reviewedAt = Instant.ofEpochMilli(result.state.dueAtEpochMs)
        }
        assertEquals(listOf(0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21), scheduled)
    }

    @Test
    fun memoState_matchesPyFsrs() {
        // py-fsrs test_memo_state는 Scheduler() 기본값(퍼즈 켬)이다. 퍼즈는 기한만 바꾸고 stability·difficulty는 바꾸지 않는다.
        val config = SrsConfig(studyDay = rule)
        val ratings = listOf(Rating.AGAIN, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD, Rating.GOOD)
        val ivlHistory = listOf(0L, 0L, 1L, 3L, 8L, 21L)
        var card = CardState.new("memo-card", start, scheduler.version)
        var reviewedAt = start
        for ((rating, ivl) in ratings.zip(ivlHistory)) {
            reviewedAt = reviewedAt.plus(Duration.ofDays(ivl))
            card = scheduler.schedule(card, rating, reviewedAt, config).state
        }
        assertEquals(53.62691, card.stability!!, 1e-4)
        assertEquals(6.3574867, card.difficulty!!, 1e-4)
    }
}
