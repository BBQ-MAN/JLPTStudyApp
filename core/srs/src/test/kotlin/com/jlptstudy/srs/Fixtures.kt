package com.jlptstudy.srs

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
internal val scheduler = Fsrs6Scheduler()

internal fun ms(iso: String): Long = Instant.parse(iso).toEpochMilli()

/** 새 카드에서 평가를 차례로 적용한다. 각 평가는 직전 결과의 dueAt에 한다. */
internal fun reviewAtDue(
    cardId: String, start: Instant, ratings: List<Rating>, config: SrsConfig,
): List<ScheduleResult> {
    var card = CardState.new(cardId, start, scheduler.version)
    var at = start
    return ratings.map { rating ->
        scheduler.schedule(card, rating, at, config).also {
            card = it.state
            at = Instant.ofEpochMilli(it.state.dueAtEpochMs)
        }
    }
}

internal fun IntervalPreview.length(reviewedAt: Instant): Duration =
    Duration.ofMillis(dueAtEpochMs - reviewedAt.toEpochMilli())
