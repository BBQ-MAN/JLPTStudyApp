package com.jlptstudy.app.ui.practice

/**
 * 결과 화면 비율(ui-design 6.10 S15): correct×100÷total을 0.5 올림으로 정수화한다.
 * 100은 전부 맞았을 때만, 0은 하나도 못 맞혔을 때만 나온다(그 사이는 1..99로 눌러 둔다). total이 0 이하면 0.
 */
fun percent(correct: Int, total: Int): Int {
    if (total <= 0) return 0
    val rounded = (correct * 200 + total) / (2 * total)
    return when {
        correct >= total -> 100
        correct <= 0 -> 0
        else -> rounded.coerceIn(1, 99)
    }
}
