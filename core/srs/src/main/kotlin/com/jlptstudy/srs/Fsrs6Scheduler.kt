/*
 * FSRS-6 스케줄러. py-fsrs v6.3.2 fsrs/scheduler.py를 Kotlin으로 옮겼다.
 *   저장소: https://github.com/open-spaced-repetition/py-fsrs
 *   커밋: 9446cb06605c597a063aeee49f7d188d42e34dc2 (태그 v6.3.2)
 * py-fsrs와 의도적으로 다른 점(architecture.md 2.2): 경과 일수는 학습일 차이, 복습 상태 기한은 학습일 시작,
 * 퍼즈 난수는 카드 ID·복습 횟수 시드, UTC 강제는 Instant 타입으로 대신한다.
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

import java.time.Duration
import java.time.Instant
import java.util.Random
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object Fsrs6 {
    /** py-fsrs v6.3.2 DEFAULT_PARAMETERS(마지막 값 = FSRS_DEFAULT_DECAY). */
    val DEFAULT_PARAMETERS: List<Double> = listOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )
    internal const val STABILITY_MIN = 0.001
    internal const val MIN_DIFFICULTY = 1.0
    internal const val MAX_DIFFICULTY = 10.0
}

class Fsrs6Scheduler : Scheduler {
    override val version: String = "fsrs6/py-fsrs-6.3.2"

    override fun schedule(card: CardState, rating: Rating, reviewedAt: Instant, config: SrsConfig): ScheduleResult {
        val o = compute(card, rating, reviewedAt, config)
        return ScheduleResult(
            o.state,
            ReviewRecord(
                cardId = card.cardId, reviewedAtEpochMs = o.state.lastReviewAtEpochMs!!, rating = rating,
                phaseBefore = card.phase, elapsedDays = o.elapsedDays, scheduledDays = o.scheduledDays,
                before = card, after = o.state, schedulerVersion = version,
            ),
        )
    }

    override fun preview(card: CardState, reviewedAt: Instant, config: SrsConfig): Map<Rating, IntervalPreview> =
        Rating.entries.associateWith { rating ->
            compute(card, rating, reviewedAt, config).let { IntervalPreview(it.state.dueAtEpochMs, it.scheduledDays, it.step) }
        }

    private class Outcome(val state: CardState, val elapsedDays: Int, val scheduledDays: Int, val step: Duration?)

    /** schedule·preview가 함께 쓰는 유일한 계산. 같은 입력이면 같은 시드라 결과가 같다. */
    private fun compute(card: CardState, rating: Rating, reviewedAt: Instant, config: SrsConfig): Outcome {
        val m = Formulas(config)
        val at = Instant.ofEpochMilli(reviewedAt.toEpochMilli()) // 저장 정밀도(ms)로 맞춰 dueAt − reviewedAt == 단계 간격
        val rule = config.studyDay
        val today = rule.studyDayOf(at)
        // 학습일 차이(py-fsrs는 실시간 차 내림). 시계가 뒤로 가도 음수가 되지 않게 0으로 막는다.
        val elapsed: Int? = card.lastReviewAtEpochMs?.let {
            max(0L, today - rule.studyDayOf(Instant.ofEpochMilli(it))).toInt()
        }
        val r = rating.value
        val oldS = card.stability
        val oldD = card.difficulty
        val retrievability = if (elapsed == null || oldS == null) 0.0 else m.retrievability(elapsed, oldS)

        val s: Double
        val d: Double
        if (oldS == null || oldD == null) {
            s = m.initialStability(r)
            d = m.clampD(m.initialDifficulty(r))
        } else {
            s = if (elapsed != null && elapsed < 1) m.shortTermStability(oldS, r) else m.nextStability(oldD, oldS, retrievability, r)
            d = m.nextDifficulty(oldD, r)
        }

        var phase: Phase
        var step: Int?
        var stepDuration: Duration? = null
        var toReview = false
        var lapses = card.lapses
        when (card.phase) {
            Phase.NEW, Phase.LEARNING, Phase.RELEARNING -> {
                val steps = if (card.phase == Phase.RELEARNING) config.relearningSteps else config.learningSteps
                phase = if (card.phase == Phase.NEW) Phase.LEARNING else card.phase
                val cur = card.step ?: 0
                step = cur
                if (steps.isEmpty() || (cur >= steps.size && rating != Rating.AGAIN)) {
                    toReview = true
                } else when (rating) {
                    Rating.AGAIN -> { step = 0; stepDuration = steps[0] }
                    Rating.HARD -> stepDuration = when {
                        cur == 0 && steps.size == 1 -> steps[0].multipliedBy(3).dividedBy(2)
                        cur == 0 -> steps[0].plus(steps[1]).dividedBy(2)
                        else -> steps[cur]
                    }
                    Rating.GOOD -> if (cur + 1 == steps.size) toReview = true else { step = cur + 1; stepDuration = steps[cur + 1] }
                    Rating.EASY -> toReview = true
                }
            }
            Phase.REVIEW -> {
                phase = Phase.REVIEW
                step = null
                if (rating == Rating.AGAIN) lapses++
                if (rating == Rating.AGAIN && config.relearningSteps.isNotEmpty()) {
                    phase = Phase.RELEARNING
                    step = 0
                    stepDuration = config.relearningSteps[0]
                } else {
                    toReview = true
                }
            }
        }

        val scheduledDays: Int
        val dueAt: Instant
        if (toReview) {
            phase = Phase.REVIEW
            step = null
            stepDuration = null
            val ivl = m.nextInterval(s)
            scheduledDays = if (config.enableFuzz) m.fuzz(ivl, Random(card.cardId.hashCode().toLong() * 31 + card.reps)) else ivl
            dueAt = rule.startOfStudyDay(today + scheduledDays)
        } else {
            scheduledDays = 0
            dueAt = at.plus(stepDuration!!)
        }

        val state = CardState(
            cardId = card.cardId, phase = phase, step = step, stability = s, difficulty = d,
            dueAtEpochMs = dueAt.toEpochMilli(), lastReviewAtEpochMs = at.toEpochMilli(),
            reps = card.reps + 1, lapses = lapses, schedulerVersion = version,
        )
        return Outcome(state, elapsed ?: 0, scheduledDays, stepDuration)
    }

    /** py-fsrs Scheduler의 계산식(_initial_*, _next_*, _short_term_stability, _get_fuzzed_interval). */
    private class Formulas(private val c: SrsConfig) {
        private val w = c.parameters
        private val decay: Double
        private val factor: Double

        init {
            require(w.size == 21) { "Expected 21 parameters, got ${w.size}." }
            decay = -w[20]
            factor = 0.9.pow(1 / decay) - 1
        }

        fun clampD(d: Double) = d.coerceIn(Fsrs6.MIN_DIFFICULTY, Fsrs6.MAX_DIFFICULTY)
        private fun clampS(s: Double) = max(s, Fsrs6.STABILITY_MIN)

        fun retrievability(elapsedDays: Int, s: Double) = (1 + factor * elapsedDays / s).pow(decay)

        fun initialStability(r: Int) = clampS(w[r - 1])
        fun initialDifficulty(r: Int) = w[4] - exp(w[5] * (r - 1)) + 1

        // Python round()는 짝수 쪽 반올림이라 Math.rint로 맞춘다.
        fun nextInterval(s: Double): Int {
            val ivl = (s / factor) * (c.desiredRetention.pow(1 / decay) - 1)
            return min(max(Math.rint(ivl), 1.0), c.maximumIntervalDays.toDouble()).toInt()
        }

        fun shortTermStability(s: Double, r: Int): Double {
            var inc = exp(w[17] * (r - 3 + w[18])) * s.pow(-w[19])
            if (r != Rating.AGAIN.value) inc = max(inc, 1.0)
            return clampS(s * inc)
        }

        fun nextDifficulty(d: Double, r: Int): Double {
            val delta = -(w[6] * (r - 3))
            val damped = d + (10.0 - d) * delta / 9.0
            return clampD(w[7] * initialDifficulty(Rating.EASY.value) + (1 - w[7]) * damped)
        }

        fun nextStability(d: Double, s: Double, rr: Double, r: Int): Double = clampS(
            if (r == Rating.AGAIN.value) {
                min(
                    w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - rr) * w[14]),
                    s / exp(w[17] * w[18]),
                )
            } else {
                val hardPenalty = if (r == Rating.HARD.value) w[15] else 1.0
                val easyBonus = if (r == Rating.EASY.value) w[16] else 1.0
                s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp((1 - rr) * w[10]) - 1) * hardPenalty * easyBonus)
            },
        )

        fun fuzz(ivl: Int, random: Random): Int {
            if (ivl < 2.5) return ivl
            var delta = 1.0
            for ((start, end, f) in FUZZ_RANGES) delta += f * max(min(ivl.toDouble(), end) - start, 0.0)
            var lo = Math.rint(ivl - delta).toInt()
            val hi = min(Math.rint(ivl + delta).toInt(), c.maximumIntervalDays)
            lo = min(max(2, lo), hi)
            val fuzzed = random.nextDouble() * (hi - lo + 1) + lo
            return min(Math.rint(fuzzed).toInt(), c.maximumIntervalDays)
        }
    }

    private companion object {
        val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )
    }
}
