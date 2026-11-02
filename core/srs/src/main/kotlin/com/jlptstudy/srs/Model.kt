package com.jlptstudy.srs

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 평가 값과 버튼의 대응은 여기 한 곳에서 정한다(py-fsrs Rating 1..4). */
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

enum class Phase(val value: Int) { NEW(0), LEARNING(1), REVIEW(2), RELEARNING(3) }

@Serializable
data class CardState(
    val cardId: String,
    val phase: Phase,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val dueAtEpochMs: Long,
    val lastReviewAtEpochMs: Long?,
    val reps: Int,
    val lapses: Int,
    val schedulerVersion: String,
) {
    companion object {
        fun new(cardId: String, createdAt: Instant, schedulerVersion: String): CardState = CardState(
            cardId = cardId, phase = Phase.NEW, step = null, stability = null, difficulty = null,
            dueAtEpochMs = createdAt.toEpochMilli(), lastReviewAtEpochMs = null, reps = 0, lapses = 0,
            schedulerVersion = schedulerVersion,
        )
    }
}

/** 학습일 경계(architecture.md 3절). 지역 시각에서 경계분을 빼서 날짜를 정한다. 경계 계산은 이 클래스에만 둔다. */
data class StudyDayRule(val zone: ZoneId, val boundaryMinutes: Int = 240) {
    init {
        require(boundaryMinutes in 0..720) { "boundaryMinutes must be in 0..720: $boundaryMinutes" }
    }

    fun studyDayOf(at: Instant): Long =
        at.atZone(zone).toLocalDateTime().minusMinutes(boundaryMinutes.toLong()).toLocalDate().toEpochDay()

    fun startOfStudyDay(studyDay: Long): Instant =
        LocalDate.ofEpochDay(studyDay).atStartOfDay().plusMinutes(boundaryMinutes.toLong()).atZone(zone).toInstant()
}

data class SrsConfig(
    val parameters: List<Double> = Fsrs6.DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val maximumIntervalDays: Int = 36500,
    val enableFuzz: Boolean = true,
    val studyDay: StudyDayRule,
)

/** 복습 상태 결과: scheduledDays >= 1, stepDuration == null / 학습·재학습 결과: scheduledDays == 0, stepDuration = 단계 간격. */
data class IntervalPreview(val dueAtEpochMs: Long, val scheduledDays: Int, val stepDuration: Duration?)

data class ReviewRecord(
    val cardId: String,
    val reviewedAtEpochMs: Long,
    val rating: Rating,
    val phaseBefore: Phase,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val before: CardState,
    val after: CardState,
    val schedulerVersion: String,
)

data class ScheduleResult(val state: CardState, val record: ReviewRecord)

interface Scheduler {
    val version: String
    fun schedule(card: CardState, rating: Rating, reviewedAt: Instant, config: SrsConfig): ScheduleResult
    fun preview(card: CardState, reviewedAt: Instant, config: SrsConfig): Map<Rating, IntervalPreview>
}
