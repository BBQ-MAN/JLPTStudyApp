package com.jlptstudy.app.ui.review

/** 사전 자료의 품사 코드의 한국어 이름표(고정 표, architecture.md 4.2 pos). 표에 없는 코드는 코드 그대로 보인다. */
object PosLabels {
    private val names = mapOf(
        "n" to "명사", "pn" to "대명사", "num" to "수사", "ctr" to "조수사", "pref" to "접두사", "suf" to "접미사",
        "adj-i" to "い형용사", "adj-na" to "な형용사", "adj-no" to "の형용사", "adj-pn" to "연체사", "adj-pre-noun" to "연체사",
        "adv" to "부사", "adv-to" to "と부사", "prt" to "조사", "conj" to "접속사", "int" to "감탄사",
        "exp" to "표현", "aux" to "조동사", "aux-v" to "조동사", "cop" to "계사",
        "vs" to "する동사", "vk" to "来る동사", "vt" to "타동사", "vi" to "자동사",
        "v1" to "1단 동사", "v5" to "5단 동사", "v5u" to "5단 동사", "v5k" to "5단 동사", "v5g" to "5단 동사",
        "v5s" to "5단 동사", "v5t" to "5단 동사", "v5n" to "5단 동사", "v5b" to "5단 동사", "v5m" to "5단 동사",
        "v5r" to "5단 동사", "v5aru" to "5단 동사", "v5k-s" to "5단 동사", "v5r-i" to "5단 동사", "v5u-s" to "5단 동사",
        "adj-ix" to "い형용사", "adj-f" to "명사 수식어", "adj-t" to "たる형용사", "vs-i" to "する동사", "vs-s" to "する동사",
        "v1-s" to "1단 동사", "vz" to "ずる동사", "n-pr" to "고유명사", "n-pref" to "접두 명사", "unc" to "분류 없음",
        "n-adv" to "부사적 명사", "n-t" to "시간 명사", "n-suf" to "접미 명사",
    )

    fun of(code: String): String = names[code] ?: code
}
