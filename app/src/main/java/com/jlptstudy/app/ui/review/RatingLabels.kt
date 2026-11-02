package com.jlptstudy.app.ui.review

import com.jlptstudy.srs.Rating

/** 평가 버튼 이름표와 낭독 틀의 유일한 정의(ui-design 5.3). 조사를 붙이지 않는다("다시으로" 방지). */
object RatingLabels {
    fun name(rating: Rating): String = when (rating) {
        Rating.AGAIN -> "다시"
        Rating.HARD -> "어려움"
        Rating.GOOD -> "알맞음"
        Rating.EASY -> "쉬움"
    }

    /** `interval`은 IntervalFormat.format의 결과. */
    fun onClickLabel(rating: Rating, interval: String): String =
        if (interval == IntervalFormat.UNDER_MINUTE) "평가: ${name(rating)}, 다음 복습 1분 안에"
        else "평가: ${name(rating)}, 다음 복습 $interval 후"
}
