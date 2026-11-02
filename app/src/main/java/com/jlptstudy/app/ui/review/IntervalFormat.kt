package com.jlptstudy.app.ui.review

import com.jlptstudy.srs.IntervalPreview

/** 간격 미리보기 글자(ui-design 5.3 표가 정본). 반올림은 0.5 올림, 1행만 반올림 전 값으로 판정한다. */
object IntervalFormat {
    const val UNDER_MINUTE = "1분 미만"

    fun format(preview: IntervalPreview): String {
        val step = preview.stepDuration
        if (step != null) {
            val ms = step.toMillis()
            if (ms < 60_000) return UNDER_MINUTE
            val minutes = roundDiv(ms, 60_000)
            if (minutes < 60) return "${minutes}분"
            val hours = roundDiv(ms, 3_600_000)
            if (hours < 24) return "${hours}시간"
            return "${roundDiv(ms, 86_400_000)}일"
        }
        val n = preview.scheduledDays
        return when {
            n < 30 -> "${n}일"
            n < 365 -> "${oneDecimal(n, 30)}개월"
            else -> "${oneDecimal(n, 365)}년"
        }
    }

    private fun roundDiv(a: Long, b: Long): Long = (a * 2 + b) / (b * 2)

    /** n ÷ d를 소수 첫째 자리로 반올림(.0은 생략). 정수 연산이라 부동소수점 경계 오차가 없다. */
    private fun oneDecimal(n: Int, d: Int): String {
        val tenths = (n * 20 + d) / (d * 2)
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }
}
